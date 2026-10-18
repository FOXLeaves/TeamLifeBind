package com.teamlifebind.paper;

import com.teamlifebind.common.GunCombatState;
import com.teamlifebind.common.GunCombatState.Shot;
import com.teamlifebind.common.GunCombatState.Snapshot;
import com.teamlifebind.common.GunCombatState.WeaponRef;
import com.teamlifebind.common.GunMath;
import com.teamlifebind.common.GunProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Paper gun adapter; normal acquisition is by supply drop, with admin developer test grants. */
final class TeamGunController {
    private static final NamespacedKey PROFILE = new NamespacedKey("teamlifebind", "gun_profile");
    private static final NamespacedKey SERIAL = new NamespacedKey("teamlifebind", "gun_serial");
    private static final NamespacedKey AMMO = new NamespacedKey("teamlifebind", "gun_ammo");
    private static final NamespacedKey LOCALE = new NamespacedKey("teamlifebind", "gun_locale");
    private static final int MAX_ROCKETS = 32;
    private final TeamLifeBindPaperPlugin plugin;
    private final GunCombatState state = new GunCombatState();
    private final Map<UUID, UUID> reloadRequests = new HashMap<>();
    private final Map<UUID, UUID> heldSerials = new HashMap<>();
    private final Map<UUID, UUID> authorizedUse = new HashMap<>();
    private final List<Rocket> rockets = new ArrayList<>();
    private int particleBudget;

    TeamGunController(TeamLifeBindPaperPlugin plugin) { this.plugin = plugin; }

    static GunProfile profile(ItemStack stack) {
        if (stack == null || stack.getType() != Material.IRON_HORSE_ARMOR || !stack.hasItemMeta()) return null;
        return GunProfile.byId(stack.getItemMeta().getPersistentDataContainer().get(PROFILE, PersistentDataType.STRING));
    }

    /** A catalogue icon, without a weapon serial or any combat-state allocation. */
    ItemStack preview(GunProfile profile, Player viewer) {
        ItemStack stack = new ItemStack(Material.IRON_HORSE_ARMOR, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.setMaxStackSize(1);
        meta.setItemModel(new NamespacedKey("teamlifebind", profile.id()));
        meta.getPersistentDataContainer().set(PROFILE, PersistentDataType.STRING, profile.id());
        stack.setItemMeta(meta);
        refreshLore(stack, viewer);
        meta = stack.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore());
        lore.add(ChatColor.YELLOW + plugin.text(viewer, "dev.menu.gun.test"));
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    ItemStack create(GunProfile profile) {
        UUID serial = UUID.randomUUID();
        if (!state.registerWeapon(serial, profile, profile.capacity())) return null;
        // Both server APIs delegate this fixed component syntax to the native item parser.
        ItemStack stack = Bukkit.getItemFactory().createItemStack("minecraft:iron_horse_armor[minecraft:consumable={consume_seconds:3600.0,animation:\"none\",sound:\"minecraft:intentionally_empty\",has_consume_particles:false}]");
        ItemMeta meta = stack.getItemMeta();
        meta.setMaxStackSize(1);
        meta.setItemModel(new NamespacedKey("teamlifebind", profile.id()));
        var data = meta.getPersistentDataContainer();
        data.set(PROFILE, PersistentDataType.STRING, profile.id());
        data.set(SERIAL, PersistentDataType.STRING, serial.toString());
        data.set(AMMO, PersistentDataType.INTEGER, profile.capacity());
        stack.setItemMeta(meta);
        refreshLore(stack, null);
        return stack;
    }

    private WeaponRef reference(ItemStack stack) {
        GunProfile profile = profile(stack);
        if (profile == null || stack.getAmount() != 1) return null;
        var data = stack.getItemMeta().getPersistentDataContainer();
        String serial = data.get(SERIAL, PersistentDataType.STRING);
        Integer ammo = data.get(AMMO, PersistentDataType.INTEGER);
        if (serial == null || ammo == null || ammo < 0 || ammo > profile.capacity()) return null;
        try { return new WeaponRef(UUID.fromString(serial), profile, ammo); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    boolean beginUse(Player player) {
        if (!plugin.isUtilityParticipant(player)) { message(player, "gun.match_only"); return false; }
        WeaponRef weapon = reference(player.getInventory().getItemInMainHand());
        if (weapon == null) { message(player, "gun.invalid"); return false; }
        if (!state.registerWeapon(weapon.serial(), weapon.profile(), weapon.savedAmmo())) {
            message(player, "gun.limit_reached"); return false;
        }
        if (!authorizedUse.containsKey(player.getUniqueId()) && authorizedUse.size() >= GunCombatState.MAX_PLAYERS) {
            message(player, "gun.limit_reached"); return false;
        }
        authorizedUse.put(player.getUniqueId(), weapon.serial());
        plugin.utilities().interrupt(player);
        return true;
    }

    // F is consumed for any held gun, including outside a match.
    boolean requestReload(Player player) {
        if (!isHoldingGun(player)) return false;
        if (!plugin.isUtilityParticipant(player)) { message(player, "gun.match_only"); return true; }
        WeaponRef weapon = reference(player.getInventory().getItemInMainHand());
        if (weapon == null) { message(player, "gun.invalid"); return true; }
        plugin.utilities().interrupt(player);
        if (reloadRequests.containsKey(player.getUniqueId()) || reloadRequests.size() < GunCombatState.MAX_PLAYERS)
            reloadRequests.put(player.getUniqueId(), weapon.serial());
        return true;
    }

    boolean isHoldingGun(Player player) { return profile(player.getInventory().getItemInMainHand()) != null; }

    void cancel(UUID player) {
        state.cancelPlayer(player);
        reloadRequests.remove(player);
        heldSerials.remove(player);
        authorizedUse.remove(player);
    }

    void clear() {
        state.clear(); reloadRequests.clear(); heldSerials.clear(); authorizedUse.clear(); rockets.clear();
    }

    void tick(long tick) {
        particleBudget = 512;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            ItemStack stack = player.getInventory().getItemInMainHand();
            WeaponRef weapon = plugin.isUtilityParticipant(player) ? reference(stack) : null;
            if (weapon == null) {
                if (heldSerials.containsKey(id)) cancel(id);
                reloadRequests.remove(id);
                continue;
            }
            UUID previous = heldSerials.put(id, weapon.serial());
            if (previous != null && !previous.equals(weapon.serial())) {
                state.cancelPlayer(id);
                if (!weapon.serial().equals(authorizedUse.get(id))) authorizedUse.remove(id);
            }
            WeaponRef using = reference(player.getItemInUse());
            if (using == null) authorizedUse.remove(id);
            boolean trigger = using != null && weapon.serial().equals(using.serial()) && weapon.serial().equals(authorizedUse.get(id))
                && (!weapon.profile().isLauncher() || rockets.size() < MAX_ROCKETS);
            Snapshot before = state.snapshot(weapon.serial(), tick);
            var update = state.advance(id, weapon, trigger, weapon.serial().equals(reloadRequests.remove(id)), player.isSneaking(), tick);
            if (update.snapshot() != null && update.snapshot().capacity() == weapon.profile().capacity()
                && update.reason() != GunCombatState.Reason.INVALID_WEAPON && update.reason() != GunCombatState.Reason.LIMIT_REACHED) {
                persist(stack, weapon.serial(), update.snapshot());
                reloadSound(player, before, update.snapshot(), update.shots().size());
            }
            refreshLore(stack, player);
            for (Shot shot : update.shots()) shoot(player, shot);
            if (tick % 10L == 0L && !plugin.utilities().hasHud(player) && !plugin.campaigns().hasHud(player)) {
                showHud(player, weapon, update.snapshot());
            }
        }
        tickRockets();
    }

    private void reloadSound(Player player, Snapshot before, Snapshot after, int fired) {
        if (before.reloadRemainingTicks() == 0 && after.reloadRemainingTicks() > 0) {
            player.playSound(player.getLocation(), "teamlifebind:gun.reload_start", 0.65F, 1.0F);
        } else if (before.reason() == GunCombatState.Reason.RELOADING
            && after.reloadRemainingTicks() == 0 && after.ammo() + fired == after.capacity()) {
            player.playSound(player.getLocation(), "teamlifebind:gun.reload_end", 0.65F, 1.0F);
        }
    }

    private void persist(ItemStack stack, UUID serial, Snapshot snapshot) {
        if (snapshot == null) return;
        ItemMeta meta = stack.getItemMeta();
        var data = meta.getPersistentDataContainer();
        if (!serial.toString().equals(data.get(SERIAL, PersistentDataType.STRING))) return;
        if (!Integer.valueOf(snapshot.ammo()).equals(data.get(AMMO, PersistentDataType.INTEGER))) {
            data.set(AMMO, PersistentDataType.INTEGER, snapshot.ammo());
            stack.setItemMeta(meta);
        }
    }

    boolean refreshLore(ItemStack stack, Player viewer) {
        GunProfile profile = profile(stack);
        if (profile == null) return false;
        ItemMeta meta = stack.getItemMeta();
        // Names identify locale without depending on private plugin language selection.
        String name = plugin.text(viewer, "item." + profile.id() + ".name");
        if (name.equals(meta.getPersistentDataContainer().get(LOCALE, PersistentDataType.STRING))) return true;
        meta.setDisplayName(ChatColor.AQUA + name);
        meta.setLore(java.util.Arrays.stream(plugin.text(viewer, "item." + profile.id() + ".lore", "F").split("\\n"))
            .map(line -> ChatColor.GRAY + line).toList());
        meta.getPersistentDataContainer().set(LOCALE, PersistentDataType.STRING, name);
        stack.setItemMeta(meta);
        return true;
    }

    private void showHud(Player player, WeaponRef weapon, Snapshot snapshot) {
        if (snapshot == null) return;
        String name = plugin.text(player, "item." + weapon.profile().id() + ".name");
        if (snapshot.reloadRemainingTicks() > 0) message(player, "gun.hud.reload", name, (snapshot.reloadRemainingTicks() + 19) / 20);
        else if (snapshot.overheated()) message(player, "gun.hud.overheated", name, Math.round(snapshot.heat()));
        else message(player, "gun.hud.ready", name, snapshot.ammo(), snapshot.capacity());
    }

    void sidebar(Player player, List<String> lines) {
        WeaponRef weapon = reference(player.getInventory().getItemInMainHand());
        if (weapon == null || !plugin.isUtilityParticipant(player) || lines.size() >= 15) return;
        Snapshot snapshot = state.snapshot(weapon.serial(), plugin.utilityTick());
        if (snapshot != null) lines.add(ChatColor.AQUA + plugin.text(player, "gun.hud.ready",
            plugin.text(player, "item." + weapon.profile().id() + ".name"), snapshot.ammo(), snapshot.capacity()));
    }

    private void shoot(Player shooter, Shot shot) {
        GunProfile profile = shot.profile();
        World world = shooter.getWorld();
        world.playSound(shooter.getLocation(), "teamlifebind:gun.fire." + profile.id(),
            profile.isLauncher() ? 1.2F : profile.usesHeat() ? 0.5F : 0.8F, 1.0F);
        Vector start = shooter.getEyeLocation().toVector();
        if (profile.isLauncher()) {
            if (rockets.size() < MAX_ROCKETS) rockets.add(new Rocket(shooter.getUniqueId(), world, shot, start, scatter(shooter, shot, 0).multiply(profile.rocketSpeed())));
            return;
        }
        Map<Entity, Impact> damage = new LinkedHashMap<>();
        for (int pellet = 0; pellet < profile.pellets(); pellet++) {
            Vector end = start.clone().add(scatter(shooter, shot, pellet).multiply(profile.range()));
            Collision collision = collision(world, shooter, start, end);
            trail(world, start, collision.position());
            if (collision.entity() != null) addBullet(damage, shooter, collision.entity(), profile, start.distance(collision.position()), head(collision.entity(), collision.position()));
        }
        applyDamage(shooter, damage);
    }

    private Vector scatter(Player player, Shot shot, int pellet) {
        Vector forward = player.getEyeLocation().getDirection();
        var direction = GunMath.scatter(new GunMath.Vec3(forward.getX(), forward.getY(), forward.getZ()),
            shot.weaponSerial(), shot.sequence(), pellet, shot.profile().spreadDegrees(shot.aimed()));
        return new Vector(direction.x(), direction.y(), direction.z());
    }

    private Collision collision(World world, Player shooter, Vector start, Vector intended) {
        Vector delta = intended.clone().subtract(start);
        double length = delta.length();
        if (length < 1.0E-8) return new Collision(null, intended, false);
        Vector direction = delta.clone().normalize();
        var block = world.rayTraceBlocks(start.toLocation(world), direction, length, FluidCollisionMode.NEVER, true);
        Vector end = block == null ? intended : block.getHitPosition();
        double nearest = start.distance(end);
        Entity hit = null;
        Vector impact = end;
        for (Entity target : targets(world, shooter, BoundingBox.of(start, end).expand(1.0))) {
            RayTraceResult candidate = target.getBoundingBox().clone().expand(0.1).rayTrace(start, direction, nearest);
            if (candidate == null) continue;
            double distance = start.distance(candidate.getHitPosition());
            if (distance < nearest) { nearest = distance; hit = target; impact = candidate.getHitPosition(); }
        }
        return new Collision(hit, impact, block != null);
    }

    private List<Entity> targets(World world, Player shooter, BoundingBox region) {
        List<Entity> targets = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(region)) {
            if (entity == shooter || entity.isDead()) continue;
            if (entity instanceof Player player && !plugin.isUtilityParticipant(player)) continue;
            if (entity instanceof EnderDragon dragon) {
                for (ComplexEntityPart part : dragon.getParts()) if (part.getBoundingBox().overlaps(region)) targets.add(part);
            } else if (entity instanceof LivingEntity) targets.add(entity);
        }
        // Dragon parts may overlap the ray when their parent's bounds do not.
        for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
            if (dragon.isDead()) continue;
            for (ComplexEntityPart part : dragon.getParts()) {
                if (part.getBoundingBox().overlaps(region) && !targets.contains(part)) targets.add(part);
            }
        }
        return targets;
    }

    private static Entity owner(Entity entity) { return entity instanceof EnderDragonPart part ? part.getParent() : entity; }

    private boolean friendly(Player shooter, Entity entity) {
        return entity instanceof Player target && plugin.shouldCancelFriendlyFire(shooter, target);
    }

    private static boolean head(Entity entity, Vector impact) {
        if (entity instanceof EnderDragonPart part) return ServerGunDamageCompat.isHead(part);
        BoundingBox box = entity.getBoundingBox();
        return impact.getY() >= box.getMaxY() - box.getHeight() * 0.28;
    }

    private static double maxHealth(Entity entity) {
        if (entity instanceof LivingEntity living && living.getAttribute(Attribute.MAX_HEALTH) != null) return living.getAttribute(Attribute.MAX_HEALTH).getValue();
        return 20.0;
    }

    private void addBullet(Map<Entity, Impact> damage, Player shooter, Entity target, GunProfile profile, double distance, boolean head) {
        Entity owner = owner(target);
        if (friendly(shooter, owner)) return;
        add(damage, owner, target, GunMath.bulletDamage(profile, owner instanceof Player, maxHealth(owner), distance, head));
    }

    private static void add(Map<Entity, Impact> damage, Entity owner, Entity target, double amount) {
        if (!(amount > 0.0) || !Double.isFinite(amount)) return;
        Impact previous = damage.get(owner);
        boolean dragon = owner instanceof EnderDragon;
        boolean unscaled = target instanceof EnderDragonPart part && ServerGunDamageCompat.isUnscaled(part);
        boolean body = dragon && !unscaled;
        Entity representative = previous == null ? target : previous.target();
        if (dragon && unscaled && (previous == null || !(previous.target() instanceof EnderDragonPart part)
            || !ServerGunDamageCompat.isUnscaled(part))) representative = target;
        damage.put(owner, new Impact(representative,
            (body ? 0.0 : amount) + (previous == null ? 0.0 : previous.damage()),
            (body ? amount : 0.0) + (previous == null ? 0.0 : previous.dragonBodyDamage())));
    }

    private void applyDamage(Player shooter, Map<Entity, Impact> impacts) {
        for (var entry : impacts.entrySet()) {
            if (!plugin.isUtilityParticipant(shooter)) return;
            Entity owner = entry.getKey();
            if (owner.isDead() || owner.getWorld() != shooter.getWorld() || friendly(shooter, owner)) continue;
            if (owner instanceof Player player && !plugin.isUtilityParticipant(player)) continue;
            Impact impact = entry.getValue();
            if (!(owner instanceof LivingEntity living) || !(impact.target() instanceof Damageable target)) continue;
            int invulnerability = living.getNoDamageTicks();
            double lastDamage = living.getLastDamage();
            try {
                living.setNoDamageTicks(0);
                // Keep a single ordinary hit, with a body-only volley reduced once by vanilla.
                double amount = impact.amount();
                if (impact.target() instanceof EnderDragonPart part) ServerGunDamageCompat.damage(part, shooter, amount);
                else target.damage(amount, shooter);
            } finally {
                living.setNoDamageTicks(invulnerability);
                living.setLastDamage(lastDamage);
            }
        }
    }

    private void tickRockets() {
        for (Rocket rocket : List.copyOf(rockets)) {
            if (!rockets.contains(rocket)) continue;
            Player shooter = Bukkit.getPlayer(rocket.shooter);
            if (shooter == null || !plugin.isUtilityParticipant(shooter) || shooter.getWorld() != rocket.world) { rockets.remove(rocket); continue; }
            double remainingRange = Math.max(0.0, rocket.shot.profile().range() - rocket.distance);
            Vector step = rocket.velocity.clone();
            if (step.length() > remainingRange) step.normalize().multiply(remainingRange);
            Vector next = rocket.position.clone().add(step);
            Collision collision = collision(rocket.world, shooter, rocket.position, next);
            trail(rocket.world, rocket.position, collision.position());
            double distance = rocket.distance + rocket.position.distance(collision.position());
            if (collision.entity() != null || collision.block() || ++rocket.age >= rocket.shot.profile().rocketLifetimeTicks() || distance >= rocket.shot.profile().range()) {
                explode(shooter, rocket, collision, distance);
                rockets.remove(rocket);
            } else {
                rocket.position = next; rocket.distance = distance;
                rocket.velocity.setY(rocket.velocity.getY() - rocket.shot.profile().rocketGravity());
            }
        }
    }

    private void explode(Player shooter, Rocket rocket, Collision collision, double distance) {
        GunProfile profile = rocket.shot.profile();
        Vector center = collision.position();
        Vector origin = center.clone().subtract(rocket.velocity.clone().normalize().multiply(0.05));
        Map<Entity, Impact> damage = new LinkedHashMap<>();
        if (collision.entity() != null) addBullet(damage, shooter, collision.entity(), profile, distance, false);
        Set<Entity> included = new HashSet<>();
        for (Entity target : targets(rocket.world, shooter, BoundingBox.of(center, center).expand(profile.blastRadius()))) {
            Entity owner = owner(target);
            if (included.contains(owner) || friendly(shooter, owner)) continue;
            Vector closest = closest(target.getBoundingBox(), center);
            double radiusDistance = center.distance(closest);
            if (radiusDistance >= profile.blastRadius()) continue;
            Vector path = closest.clone().subtract(origin);
            double length = path.length();
            RayTraceResult wall = length < 1.0E-8 ? null : rocket.world.rayTraceBlocks(origin.toLocation(rocket.world), path.clone().normalize(), length, FluidCollisionMode.NEVER, true);
            if (wall != null && origin.distanceSquared(wall.getHitPosition()) + 0.01 < length * length) continue;
            add(damage, owner, target, GunMath.splashDamage(profile, owner instanceof Player, maxHealth(owner), radiusDistance));
            included.add(owner);
        }
        rocket.world.spawnParticle(Particle.EXPLOSION, center.toLocation(rocket.world), 1);
        rocket.world.playSound(center.toLocation(rocket.world), Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.1F);
        applyDamage(shooter, damage);
    }

    private static Vector closest(BoundingBox box, Vector point) {
        return new Vector(Math.max(box.getMinX(), Math.min(box.getMaxX(), point.getX())),
            Math.max(box.getMinY(), Math.min(box.getMaxY(), point.getY())), Math.max(box.getMinZ(), Math.min(box.getMaxZ(), point.getZ())));
    }

    private void trail(World world, Vector start, Vector end) {
        Vector delta = end.clone().subtract(start);
        int steps = Math.min(16, Math.max(1, (int) Math.ceil(delta.length() / 1.5)));
        for (int i = 1; i <= steps && particleBudget > 0; i++) {
            particleBudget--;
            world.spawnParticle(Particle.CRIT, start.clone().add(delta.clone().multiply((double) i / steps)).toLocation(world), 1);
        }
    }

    private void message(Player player, String key, Object... args) { plugin.utilityActionBar(player, key, args); }
    private record Collision(Entity entity, Vector position, boolean block) { }
    private record Impact(Entity target, double damage, double dragonBodyDamage) {
        private double amount() {
            if (target instanceof EnderDragonPart part) return ServerGunDamageCompat.isUnscaled(part)
                ? damage + GunMath.dragonBodyDamage(dragonBodyDamage) : dragonBodyDamage;
            return target instanceof EnderDragon ? dragonBodyDamage : damage;
        }
    }
    private static final class Rocket {
        final UUID shooter;
        final World world;
        final Shot shot;
        Vector position, velocity;
        double distance;
        int age;
        Rocket(UUID shooter, World world, Shot shot, Vector position, Vector velocity) {
            this.shooter = shooter; this.world = world; this.shot = shot; this.position = position; this.velocity = velocity;
        }
    }
}
