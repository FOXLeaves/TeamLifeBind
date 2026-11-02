package com.teamlifebind.forge;

import com.teamlifebind.common.GunCombatState;
import com.teamlifebind.common.GunCombatState.Shot;
import com.teamlifebind.common.GunCombatState.Snapshot;
import com.teamlifebind.common.GunCombatState.WeaponRef;
import com.teamlifebind.common.GunMath;
import com.teamlifebind.common.GunProfile;
import com.teamlifebind.forge.mixin.GunDamageAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Original server-authoritative weapons. Native damage preserves the existing match rules. */
final class TeamGunController {
    private static final String SERIAL = "tlb_gun_serial";
    private static final String AMMO = "tlb_gun_ammo";
    private static final String LOCALE = "tlb_gun_locale";
    private static final int MAX_ROCKETS = 32;
    private static final long INPUT_TIMEOUT_TICKS = 20L;
    private final TeamLifeBindForgeManager host;
    private final GunCombatState state = new GunCombatState(true);
    private final Map<UUID, UUID> reloadRequests = new HashMap<>();
    private final Map<UUID, UUID> heldSerials = new HashMap<>();
    private final Map<UUID, Input> inputs = new HashMap<>();
    // Preserve a press/release pair arriving between server ticks without inventing held input.
    private final Map<UUID, Input> pendingTriggers = new HashMap<>();
    private final Map<UUID, VisualSync> visualSync = new HashMap<>();
    private final Map<UUID, SoundState> soundStates = new HashMap<>();
    private final Map<UUID, Long> weaponSequences = new HashMap<>();
    private final List<Rocket> rockets = new ArrayList<>();
    private int particleBudget;

    TeamGunController(TeamLifeBindForgeManager host) { this.host = host; }

    static GunProfile profile(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof TeamGunItem gun ? gun.profile() : null;
    }

    // A catalogue entry is never an issued weapon: no serial, ammo state or registration.
    ItemStack menuPreview(GunProfile profile, ServerPlayer viewer) {
        ItemStack stack = new ItemStack(TeamLifeBindForge.gunItem(profile));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(host.text(viewer, "item." + profile.id() + ".native_name")));
        List<Component> lore = new ArrayList<>();
        for (String line : (profile.usesHeat() ? host.text(viewer, "item." + profile.id() + ".native_lore")
                : host.text(viewer, "item." + profile.id() + ".native_lore", "R")).split("\n")) {
            lore.add(Component.literal(line));
        }
        lore.add(Component.literal(host.text(viewer, "dev.menu.gun.test")));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    ItemStack create(GunProfile profile) {
        ItemStack stack = new ItemStack(TeamLifeBindForge.gunItem(profile));
        UUID serial = UUID.randomUUID();
        if (!state.registerWeapon(serial, profile, state.capacity(profile))) return ItemStack.EMPTY;
        CompoundTag data = new CompoundTag();
        data.putString(SERIAL, serial.toString());
        data.putInt(AMMO, state.capacity(profile));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        refreshLore(stack, null);
        return stack;
    }

    private WeaponRef reference(ItemStack stack) {
        GunProfile profile = profile(stack);
        CustomData custom = stack == null ? null : stack.get(DataComponents.CUSTOM_DATA);
        if (profile == null || custom == null) return null;
        CompoundTag data = custom.copyTag();
        try {
            return new WeaponRef(UUID.fromString(data.getString(SERIAL).orElse("")), profile,
                    Math.max(0, Math.min(state.capacity(profile), data.getInt(AMMO).orElse(0))));
        } catch (IllegalArgumentException invalid) { return null; }
    }

    boolean beginUse(ServerPlayer player) {
        if (!host.gunUserAllowed(player)) { message(player, "gun.unavailable"); return false; }
        WeaponRef weapon = reference(player.getMainHandItem());
        if (weapon == null) { message(player, "gun.invalid"); return false; }
        if (!state.registerWeapon(weapon.serial(), weapon.profile(), weapon.savedAmmo())) {
            message(player, "gun.limit_reached"); return false;
        }
        host.gunCancelUtilities(player);
        return true;
    }

    // R sends only reload intent; the held gun, native eligibility and ammo are checked here.
    boolean requestReload(ServerPlayer player) {
        if (profile(player.getMainHandItem()) == null) return false;
        if (!host.gunUserAllowed(player)) { message(player, "gun.unavailable"); return true; }
        WeaponRef weapon = reference(player.getMainHandItem());
        if (weapon == null) { message(player, "gun.invalid"); return true; }
        if (weapon.profile().usesHeat()) { message(player, "gun.reload.not_needed"); return true; }
        host.gunCancelUtilities(player);
        if (reloadRequests.containsKey(player.getUUID()) || reloadRequests.size() < GunCombatState.MAX_PLAYERS)
            reloadRequests.put(player.getUUID(), weapon.serial());
        return true;
    }

    boolean acceptInput(ServerPlayer player, GunInputPayload payload) {
        MinecraftServer server = host.utilityServer();
        if (server == null || server.getPlayerList().getPlayer(player.getUUID()) != player) return false;
        UUID playerId = player.getUUID();
        if (GunInputPayload.CANCEL_SERIAL.equals(payload.serial()) && !payload.firing() && !payload.aiming()) {
            cancel(playerId);
            return true;
        }
        WeaponRef weapon = reference(player.getMainHandItem());
        if (!host.gunUserAllowed(player) || weapon == null || !weapon.serial().equals(payload.serial())) {
            cancel(playerId);
            if (weapon != null && weapon.serial().equals(payload.serial())) TeamGunNetwork.sendVisual(player,
                    new GunVisualPayload(weapon.serial(), weaponSequences.getOrDefault(weapon.serial(), 0L), weapon.savedAmmo(), 0, 0.0F, GunCombatState.Reason.NO_WEAPON.ordinal(), false));
            return false;
        }
        if (!state.registerWeapon(weapon.serial(), weapon.profile(), weapon.savedAmmo())) return false;
        if (!payload.firing()) state.releaseTrigger(playerId, weapon.serial());
        if (!payload.firing() && !payload.aiming()) {
            inputs.remove(playerId);
            return true;
        }
        if (!inputs.containsKey(playerId) && inputs.size() >= GunCombatState.MAX_PLAYERS) return false;
        Input previous = inputs.get(playerId);
        boolean pressed = payload.firing() && (previous == null || !previous.firing() || !previous.serial().equals(weapon.serial()));
        if (pressed && !pendingTriggers.containsKey(playerId) && pendingTriggers.size() >= GunCombatState.MAX_PLAYERS) return false;
        host.gunCancelUtilities(player);
        Input input = new Input(weapon.serial(), payload.firing(), payload.aiming(), host.utilityTick());
        if (pressed) pendingTriggers.put(playerId, input);
        inputs.put(playerId, input);
        return true;
    }

    void cancel(UUID playerId) {
        state.cancelPlayer(playerId);
        reloadRequests.remove(playerId);
        UUID oldSerial = heldSerials.remove(playerId);
        inputs.remove(playerId);
        pendingTriggers.remove(playerId);
        visualSync.remove(playerId);
        MinecraftServer server = host.utilityServer();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        SoundState previousSound = soundStates.remove(playerId);
        if (player != null && previousSound != null && previousSound.turning()) TeamGunSounds.play(player, TeamGunSounds.SPIN_STOP);
        if (player != null && player.isUsingItem() && profile(player.getUseItem()) != null) player.stopUsingItem();
        if (player != null && oldSerial != null) TeamGunNetwork.sendVisual(player, new GunVisualPayload(oldSerial,
                weaponSequences.getOrDefault(oldSerial, 0L), 0, 0, 0.0F, GunCombatState.Reason.NO_WEAPON.ordinal(), false));
    }

    void clear() {
        for (UUID playerId : List.copyOf(heldSerials.keySet())) cancel(playerId);
        state.clear();
        reloadRequests.clear();
        heldSerials.clear();
        inputs.clear();
        pendingTriggers.clear();
        visualSync.clear();
        soundStates.clear();
        weaponSequences.clear();
        rockets.clear();
    }

    void tick(MinecraftServer server) {
        particleBudget = 512;
        long tick = host.utilityTick();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            ItemStack stack = player.getMainHandItem();
            WeaponRef weapon = host.gunUserAllowed(player) ? reference(stack) : null;
            if (weapon == null) {
                if (heldSerials.containsKey(playerId) || inputs.containsKey(playerId) || pendingTriggers.containsKey(playerId) || reloadRequests.containsKey(playerId)) cancel(playerId);
                continue;
            }
            UUID previous = heldSerials.get(playerId);
            Input input = inputs.get(playerId);
            Input press = pendingTriggers.get(playerId);
            if (previous != null && !previous.equals(weapon.serial())) {
                UUID requestedSerial = reloadRequests.get(playerId);
                cancel(playerId);
                if (weapon.serial().equals(requestedSerial)) reloadRequests.put(playerId, requestedSerial);
                if (input != null && input.serial().equals(weapon.serial())) inputs.put(playerId, input);
                if (press != null && press.serial().equals(weapon.serial())) pendingTriggers.put(playerId, press);
            }
            if (input != null && (!input.serial().equals(weapon.serial()) || tick < input.receivedTick()
                    || tick - input.receivedTick() >= INPUT_TIMEOUT_TICKS)) { cancel(playerId); input = null; }
            if (!heldSerials.containsKey(playerId) && heldSerials.size() >= GunCombatState.MAX_PLAYERS) continue;
            heldSerials.put(playerId, weapon.serial());
            press = pendingTriggers.remove(playerId);
            boolean pressed = press != null && press.serial().equals(weapon.serial()) && tick >= press.receivedTick()
                    && tick - press.receivedTick() < INPUT_TIMEOUT_TICKS;
            boolean trigger = ((input != null && input.firing()) || pressed)
                    && (!weapon.profile().isLauncher() || rockets.size() < MAX_ROCKETS);
            boolean aiming = (input != null && input.aiming()) || (pressed && press.aiming());
            GunCombatState.Update update = state.advance(playerId, weapon, trigger, weapon.serial().equals(reloadRequests.remove(playerId)), aiming, tick);
            if (pressed && (input == null || !input.firing())) state.releaseTrigger(playerId, weapon.serial());
            if (update.snapshot() != null && update.snapshot().capacity() == state.capacity(weapon.profile())
                    && update.reason() != GunCombatState.Reason.INVALID_WEAPON && update.reason() != GunCombatState.Reason.LIMIT_REACHED) {
                persist(stack, weapon.serial(), update.snapshot());
            }
            for (Shot shot : update.shots()) {
                weaponSequences.put(shot.weaponSerial(), shot.sequence());
                shoot(player, shot);
            }
            updateSounds(player, weapon, update, trigger, tick);
            syncVisual(player, weapon, update, tick);
            // Persistent ammo/reload/heat now uses the native HUD; action-bar interactions keep priority.
        }
        tickRockets(server);
    }

    private void syncVisual(ServerPlayer player, WeaponRef weapon, GunCombatState.Update update, long tick) {
        Snapshot snapshot = update.snapshot();
        if (snapshot == null) return;
        VisualSync previous = visualSync.get(player.getUUID());
        boolean changed = previous == null || !previous.serial().equals(weapon.serial())
                || previous.ammo() != snapshot.ammo() || previous.reloading() != (snapshot.reloadRemainingTicks() > 0L)
                || previous.reason() != update.reason() || previous.overheated() != snapshot.overheated();
        if (!changed && update.shots().isEmpty() && tick - previous.tick() < 5L) return;
        TeamGunNetwork.sendVisual(player, new GunVisualPayload(weapon.serial(), weaponSequences.getOrDefault(weapon.serial(), 0L),
                snapshot.ammo(), (int) Math.min(Integer.MAX_VALUE, snapshot.reloadRemainingTicks()), (float) snapshot.heat(),
                update.reason().ordinal(), !update.shots().isEmpty()));
        visualSync.put(player.getUUID(), new VisualSync(weapon.serial(), snapshot.ammo(), snapshot.reloadRemainingTicks() > 0L,
                snapshot.overheated(), update.reason(), tick));
    }

    /** Sound transitions are sampled every server tick, independently of the 5-tick visual heartbeat. */
    private void updateSounds(ServerPlayer player, WeaponRef weapon, GunCombatState.Update update, boolean trigger, long tick) {
        Snapshot snapshot = update.snapshot();
        if (snapshot == null || update.reason() == GunCombatState.Reason.DUPLICATE_TICK
                || update.reason() == GunCombatState.Reason.CLOCK_REWIND) return;
        SoundState previous = soundStates.get(player.getUUID());
        if (previous != null && !previous.serial().equals(weapon.serial())) previous = null;
        boolean reloading = snapshot.reloadRemainingTicks() > 0L;
        if (reloading && (previous == null || !previous.reloading())) TeamGunSounds.play(player, TeamGunSounds.RELOAD_START);
        if (previous != null && previous.reloading()) {
            if (weapon.profile() == GunProfile.BREACH_SHOTGUN && snapshot.ammo() + update.shots().size() > previous.ammo())
                TeamGunSounds.play(player, TeamGunSounds.SHELL_INSERT);
            // A completed automatic reload may fire immediately. A cancelled SPAS closing stage
            // has the same apparent ammo, so also require the previous completion deadline.
            if (!reloading && tick >= previous.reloadFinishTick()
                    && snapshot.ammo() + update.shots().size() == snapshot.capacity())
                TeamGunSounds.play(player, TeamGunSounds.RELOAD_END);
        }
        if (!update.shots().isEmpty() && (weapon.profile() == GunProfile.BREACH_SHOTGUN || weapon.profile() == GunProfile.MARKSMAN_RIFLE))
            TeamGunSounds.play(player, TeamGunSounds.BOLT);
        boolean turning = weapon.profile().usesHeat() && trigger && !snapshot.overheated()
                && (update.reason() == GunCombatState.Reason.WINDING_UP || update.reason() == GunCombatState.Reason.FIRED
                    || update.reason() == GunCombatState.Reason.COOLDOWN);
        if (turning && (previous == null || !previous.turning())) TeamGunSounds.play(player, TeamGunSounds.SPIN_START);
        if (!turning && previous != null && previous.turning()) TeamGunSounds.play(player, TeamGunSounds.SPIN_STOP);
        long remaining = snapshot.reloadRemainingTicks();
        long reloadFinishTick = tick > Long.MAX_VALUE - remaining ? Long.MAX_VALUE : tick + remaining;
        soundStates.put(player.getUUID(), new SoundState(weapon.serial(), snapshot.ammo(), reloading, turning, reloadFinishTick));
    }

    private record SoundState(UUID serial, int ammo, boolean reloading, boolean turning, long reloadFinishTick) {}
    private record Input(UUID serial, boolean firing, boolean aiming, long receivedTick) {}
    private record VisualSync(UUID serial, int ammo, boolean reloading, boolean overheated, GunCombatState.Reason reason, long tick) {}

    private void persist(ItemStack stack, UUID serial, Snapshot snapshot) {
        if (snapshot == null) return;
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return;
        CompoundTag data = custom.copyTag();
        if (!serial.toString().equals(data.getString(SERIAL).orElse(""))) return;
        if (data.getInt(AMMO).orElse(-1) != snapshot.ammo()) {
            data.putInt(AMMO, snapshot.ammo());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
    }

    boolean refreshLore(ItemStack stack, ServerPlayer viewer) {
        GunProfile profile = profile(stack);
        if (profile == null) return false;
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return false;
        CompoundTag data = custom.copyTag();
        String locale = host.utilityLocale(viewer) + "|tacz-controls-v4";
        if (locale.equals(data.getString(LOCALE).orElse("")) && stack.has(DataComponents.LORE)) return false;
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(host.text(viewer, "item." + profile.id() + ".native_name")));
        List<Component> lore = new ArrayList<>();
        for (String line : (profile.usesHeat() ? host.text(viewer, "item." + profile.id() + ".native_lore") : host.text(viewer, "item." + profile.id() + ".native_lore", "R")).split("\n")) {
            lore.add(Component.literal(line));
        }
        for (String line : host.text(viewer, "gun.controls.mod", "R").split("\n")) {
            lore.add(Component.literal(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        data.putString(LOCALE, locale);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return true;
    }

    String sidebar(ServerPlayer player) {
        WeaponRef weapon = reference(player.getMainHandItem());
        if (weapon == null || !host.gunUserAllowed(player)) return null;
        Snapshot snapshot = state.snapshot(weapon.serial(), host.utilityTick());
        if (snapshot == null) return null;
        return hud(player, weapon, snapshot);
    }

    private String hud(ServerPlayer player, WeaponRef weapon, Snapshot snapshot) {
        if (snapshot == null) return "";
        String name = host.text(player, "item." + weapon.profile().id() + ".native_name");
        if (snapshot.overheated()) return host.text(player, "gun.hud.overheated", name, Math.round(snapshot.heat()));
        if (weapon.profile().usesHeat()) return host.text(player, "gun.hud.feed_ready", name, Math.round(snapshot.heat()));
        if (snapshot.reloadRemainingTicks() > 0L) return host.text(player, "gun.hud.reload", name, (snapshot.reloadRemainingTicks() + 19L) / 20L);
        return host.text(player, "gun.hud.ready", name, snapshot.ammo(), snapshot.capacity());
    }

    private void shoot(ServerPlayer shooter, Shot shot) {
        ServerLevel level = shooter.level();
        GunProfile profile = shot.profile();
        TeamGunSounds.fire(shooter, profile);
        if (profile.isLauncher()) {
            if (rockets.size() >= MAX_ROCKETS) return;
            Vec3 direction = scatter(shooter.getLookAngle(), shot, 0);
            rockets.add(new Rocket(shooter.getUUID(), level, shot, shooter.getEyePosition(), direction.scale(profile.rocketSpeed())));
            return;
        }
        Map<Entity, Impact> damage = new LinkedHashMap<>();
        Vec3 start = shooter.getEyePosition();
        for (int pellet = 0; pellet < profile.pellets(); pellet++) {
            Vec3 end = start.add(scatter(shooter.getLookAngle(), shot, pellet).scale(profile.range()));
            Collision collision = collision(level, shooter, start, end);
            Vec3 impact = collision.position();
            trail(level, start, impact, profile.pellets() > 1 ? 2.0D : 1.5D);
            if (collision.entity() != null) addBullet(damage, shooter, collision.entity(), profile, start.distanceTo(impact), head(collision.entity(), impact));
        }
        applyDamage(shooter, damage);
    }

    private Vec3 scatter(Vec3 forward, Shot shot, int pellet) {
        GunMath.Vec3 direction = GunMath.scatter(new GunMath.Vec3(forward.x, forward.y, forward.z),
                shot.weaponSerial(), shot.sequence(), pellet, shot.profile().spreadDegrees(shot.aimed()));
        return new Vec3(direction.x(), direction.y(), direction.z());
    }

    private Collision collision(ServerLevel level, ServerPlayer shooter, Vec3 start, Vec3 intended) {
        HitResult block = level.clip(new ClipContext(start, intended, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 end = block.getType() == HitResult.Type.MISS ? intended : block.getLocation();
        AABB region = new AABB(start, end).inflate(1.0D);
        Entity nearest = null;
        Vec3 impact = end;
        double nearestDistance = start.distanceToSqr(end);
        for (Entity target : targets(level, shooter, region)) {
            Vec3 hit = target.getBoundingBox().inflate(0.1D).clip(start, end).orElse(null);
            if (hit == null) continue;
            double distance = start.distanceToSqr(hit);
            if (distance < nearestDistance) { nearest = target; impact = hit; nearestDistance = distance; }
        }
        return new Collision(nearest, impact, block.getType() != HitResult.Type.MISS);
    }

    private List<Entity> targets(ServerLevel level, ServerPlayer shooter, AABB region) {
        List<Entity> result = new ArrayList<>();
        for (Entity entity : level.getEntities(shooter, region, target -> target instanceof LivingEntity && target.isAlive() && !target.isSpectator())) {
            if (entity instanceof ServerPlayer player && !host.gunUserAllowed(player)) continue;
            if (!(entity instanceof EnderDragon)) result.add(entity);
        }
        for (Entity entityPart : level.dragonParts()) {
            if (entityPart instanceof EnderDragonPart part && part.parentMob.isAlive()
                    && !part.parentMob.isRemoved() && part.getBoundingBox().intersects(region)) result.add(part);
        }
        return result;
    }

    private static Entity owner(Entity target) { return target instanceof EnderDragonPart part ? part.parentMob : target; }
    private static boolean head(Entity target, Vec3 impact) {
        if (target instanceof EnderDragonPart part) return part == part.parentMob.head;
        AABB box = target.getBoundingBox();
        return impact.y >= box.maxY - (box.maxY - box.minY) * 0.28D;
    }

    private void addBullet(Map<Entity, Impact> damage, ServerPlayer shooter, Entity target, GunProfile profile, double distance, boolean head) {
        Entity owner = owner(target);
        if (owner instanceof ServerPlayer player && host.shouldCancelFriendlyFire(shooter, player)) return;
        double maxHealth = owner instanceof LivingEntity living ? living.getMaxHealth() : 20.0D;
        add(damage, owner, target, GunMath.bulletDamage(profile, owner instanceof ServerPlayer, maxHealth, distance, head));
    }

    private static boolean unscaledDragonPart(Entity target) {
        return target instanceof EnderDragonPart part && (part == part.parentMob.head || "neck".equals(part.name));
    }

    private static void add(Map<Entity, Impact> damage, Entity owner, Entity target, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        boolean body = target instanceof EnderDragonPart && !unscaledDragonPart(target);
        Impact previous = damage.get(owner);
        Entity representative = previous == null || unscaledDragonPart(target) && !unscaledDragonPart(previous.target())
                ? target : previous.target();
        damage.put(owner, new Impact(representative,
                (body ? 0.0D : amount) + (previous == null ? 0.0D : previous.damage()),
                (body ? amount : 0.0D) + (previous == null ? 0.0D : previous.dragonBodyDamage())));
    }

    private void applyDamage(ServerPlayer shooter, Map<Entity, Impact> impacts) {
        if (!host.gunUserAllowed(shooter)) return;
        for (Map.Entry<Entity, Impact> entry : impacts.entrySet()) {
            if (!host.gunUserAllowed(shooter)) return;
            Entity owner = entry.getKey();
            if (!owner.isAlive() || owner.level() != shooter.level()) continue;
            if (owner instanceof ServerPlayer player && (!host.gunUserAllowed(player) || host.shouldCancelFriendlyFire(shooter, player))) continue;
            host.recordCampaignAttack(shooter, owner);
            Impact impact = entry.getValue();
            if (owner instanceof LivingEntity living) {
                int invulnerability = living.damageCooldownTime;
                GunDamageAccessor cooldown = (GunDamageAccessor) living;
                float lastHurt = cooldown.tlb$getLastHurt();
                try {
                    living.damageCooldownTime = 0;
                    // One ordinary damage call per owner. Body-only hits retain vanilla part reduction;
                    // mixed head/neck hits include body reduction once before reaching an unscaled part.
                    double amount = impact.damage() + (unscaledDragonPart(impact.target())
                            ? GunMath.dragonBodyDamage(impact.dragonBodyDamage()) : impact.dragonBodyDamage());
                    impact.target().hurtServer(shooter.level(), shooter.damageSources().playerAttack(shooter), (float) amount);
                } finally {
                    living.damageCooldownTime = invulnerability;
                    cooldown.tlb$setLastHurt(lastHurt);
                }
            }
        }
    }

    private void tickRockets(MinecraftServer server) {
        for (Rocket rocket : List.copyOf(rockets)) {
            if (!rockets.contains(rocket)) continue;
            ServerPlayer shooter = server.getPlayerList().getPlayer(rocket.shooter);
            if (shooter == null || !host.gunUserAllowed(shooter) || shooter.level() != rocket.level) { rockets.remove(rocket); continue; }
            double remaining = Math.max(0.0D, rocket.shot.profile().range() - rocket.distance);
            double stepLength = rocket.velocity.length();
            Vec3 step = stepLength > remaining && stepLength > 0.0D ? rocket.velocity.scale(remaining / stepLength) : rocket.velocity;
            Vec3 next = rocket.position.add(step);
            Collision collision = collision(rocket.level, shooter, rocket.position, next);
            trail(rocket.level, rocket.position, collision.position(), 0.8D);
            double distance = rocket.distance + rocket.position.distanceTo(collision.position());
            if (collision.entity() != null || collision.block() || ++rocket.age >= rocket.shot.profile().rocketLifetimeTicks()
                    || distance >= rocket.shot.profile().range()) {
                explode(shooter, rocket, collision, distance);
                rockets.remove(rocket);
            } else {
                rocket.position = next;
                rocket.distance = distance;
                rocket.velocity = rocket.velocity.add(0.0D, -rocket.shot.profile().rocketGravity(), 0.0D);
            }
        }
    }

    private void explode(ServerPlayer shooter, Rocket rocket, Collision collision, double distance) {
        GunProfile profile = rocket.shot.profile();
        Vec3 center = collision.position();
        // Nudge the blast toward the incoming free-space side to keep wall occlusion meaningful.
        Vec3 blastOrigin = center.subtract(rocket.velocity.normalize().scale(0.05D));
        Map<Entity, Impact> damage = new LinkedHashMap<>();
        if (collision.entity() != null) addBullet(damage, shooter, collision.entity(), profile, distance, false);
        Set<Entity> included = new HashSet<>();
        for (Entity target : targets(rocket.level, shooter, new AABB(center, center).inflate(profile.blastRadius()))) {
            Entity owner = owner(target);
            if (included.contains(owner) || (owner instanceof ServerPlayer player && host.shouldCancelFriendlyFire(shooter, player))) continue;
            Vec3 closest = closest(target.getBoundingBox(), center);
            double radiusDistance = center.distanceTo(closest);
            if (radiusDistance >= profile.blastRadius()) continue;
            HitResult wall = rocket.level.clip(new ClipContext(blastOrigin, closest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            if (wall.getType() != HitResult.Type.MISS && blastOrigin.distanceToSqr(wall.getLocation()) + 0.01D < blastOrigin.distanceToSqr(closest)) continue;
            double maxHealth = owner instanceof LivingEntity living ? living.getMaxHealth() : 20.0D;
            add(damage, owner, target, GunMath.splashDamage(profile, owner instanceof ServerPlayer, maxHealth, radiusDistance));
            included.add(owner);
        }
        rocket.level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        rocket.level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.1F);
        applyDamage(shooter, damage);
    }

    private static Vec3 closest(AABB box, Vec3 point) {
        return new Vec3(Math.max(box.minX, Math.min(box.maxX, point.x)), Math.max(box.minY, Math.min(box.maxY, point.y)), Math.max(box.minZ, Math.min(box.maxZ, point.z)));
    }

    private void trail(ServerLevel level, Vec3 start, Vec3 end, double spacing) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length <= 0.0D) return;
        int steps = Math.min(16, Math.max(1, (int) Math.ceil(length / spacing)));
        for (int i = 1; i <= steps && particleBudget > 0; i++) {
            particleBudget--;
            Vec3 point = start.add(delta.scale((double) i / steps));
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void message(ServerPlayer player, String key, Object... args) { messageRaw(player, host.text(player, key, args)); }
    private void messageRaw(ServerPlayer player, String text) { if (!text.isEmpty()) player.sendSystemMessage(Component.literal(host.campaignOverlay(player, text)), true); }
    private record Collision(Entity entity, Vec3 position, boolean block) { }
    private record Impact(Entity target, double damage, double dragonBodyDamage) { }
    private static final class Rocket {
        final UUID shooter;
        final ServerLevel level;
        final Shot shot;
        Vec3 position;
        Vec3 velocity;
        double distance;
        int age;
        Rocket(UUID shooter, ServerLevel level, Shot shot, Vec3 position, Vec3 velocity) {
            this.shooter = shooter; this.level = level; this.shot = shot; this.position = position; this.velocity = velocity;
        }
    }
}
