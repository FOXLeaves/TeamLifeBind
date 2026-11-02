package com.teamlifebind.fabric;

import com.teamlifebind.common.PlayMode;
import com.teamlifebind.common.PveWaveState;
import com.teamlifebind.common.PveWaves;
import com.teamlifebind.common.SpellCatalogue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/** Real encounter entities are run-owned; missing entities are replaced, never credited as kills. */
final class TeamPveController {
    private static final String OWNED = "tlb_pve_encounter";
    private final TeamLifeBindFabricManager host;
    private final PveWaveState state = new PveWaveState();
    private final TeamRogueController rogue;
    private final Map<UUID, Mob> enemies = new HashMap<>();
    private final Map<UUID, Double> attackScales = new HashMap<>();
    private final Map<UUID, ServerBossEvent> bossBars = new HashMap<>();
    private UUID bossId;
    private TeamCampaignController.Anchor center;
    private ServerLevel encounterWorld;
    private boolean savedMobGriefing;
    private long completedAt = -1L;
    private boolean failurePresented;

    TeamPveController(TeamLifeBindFabricManager host) { this.host = host; rogue = new TeamRogueController(host, state); }
    PveWaveState state() { return state; }
    TeamRogueController rogue() { return rogue; }
    void start() {
        clear();
        center = host.campaignCenter();
        if (center == null) return;
        encounterWorld = host.utilityServer().getLevel(center.dimension());
        if (encounterWorld == null) return;
        savedMobGriefing = encounterWorld.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
        encounterWorld.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING, false, host.utilityServer());
        state.start(host.campaignAssignments().size(), host.utilityTick(), host.pveDifficulty());
        if (host.playMode() == PlayMode.RG) rogue.start(host.campaignAssignments().size()); else previewNext();
    }
    void clear() {
        rogue.clear();
        clearBossBars(); bossId = null;
        for (Mob enemy : enemies.values()) if (!enemy.isRemoved()) enemy.discard();
        if (encounterWorld != null) encounterWorld.getGameRules().set(
            net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING, savedMobGriefing, host.utilityServer());
        encounterWorld = null;
        enemies.clear(); attackScales.clear(); state.clear(); center = null; completedAt = -1L; failurePresented = false;
    }
    void onLoad(Entity entity) {
        if (entity.entityTags().contains(OWNED) && !enemies.containsKey(entity.getUUID())) entity.discard();
    }
    void died(Entity entity) {
        attackScales.remove(entity.getUUID());
        if (enemies.remove(entity.getUUID()) != null) state.defeated(state.epoch(), entity.getUUID(), host.utilityTick());
        if (entity.getUUID().equals(bossId)) { bossId = null; clearBossBars(); }
    }
    String hud(ServerPlayer viewer) {
        if (!host.playMode().encounter() || !host.campaignRunning()) return "";
        if (rogue.state().waitingForChoice()) return rogue.choiceHud(viewer);
        if (state.completed()) return host.text(viewer, "play.pve.extract", Math.max(0L, (completedAt + 400L - host.utilityTick() + 19L) / 20L));
        if (state.phase() == PveWaveState.Phase.WAITING) return host.text(viewer, "play.pve.hud.wait",
            host.text(viewer, stageKey(Math.min(PveWaves.TOTAL_WAVES, state.wave() + 1))), Math.max(0L, (state.nextWaveTick() - host.utilityTick() + 19L) / 20L));
        if (host.playMode() == PlayMode.RG) return host.text(viewer, "rg.hud.fight", host.text(viewer, state.difficulty().languageKey()), state.wave(), PveWaves.TOTAL_WAVES, state.remaining(), rogue.state().totalRelics());
        return host.text(viewer, "play.pve.hud.fight", host.text(viewer, state.difficulty().languageKey()), state.wave(), PveWaves.TOTAL_WAVES, state.remaining());
    }
    double projectileDamageScale(Entity shooter) {
        return shooter == null ? 1D : attackScales.getOrDefault(shooter.getUUID(), 1D);
    }
    void starter(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        give(player, new ItemStack(Items.IRON_SWORD));
        give(player, new ItemStack(Items.BOW));
        give(player, new ItemStack(Items.ARROW, 48));
        give(player, new ItemStack(Items.COOKED_BEEF, 16));
        give(player, host.magic().createCompanion(TeamMagicItem.Kind.MANA_POTION, player));
        host.magic().giveStarter(player);
    }
    void tick(MinecraftServer server) {
        if (!host.campaignRunning() || !host.playMode().encounter() || center == null || host.utilityTick() % 10L != 0L) return;
        rogue.tick();
        updateBossBars(server);
        if (state.completed()) {
            completePresentation(host.utilityTick());
            if (host.utilityTick() - completedAt >= 400L) { host.campaignFinish(1, false); return; }
        }
        List<ServerPlayer> fighters = server.getPlayerList().getPlayers().stream().filter(host::utilityParticipant).toList();
        if (fighters.isEmpty()) return;
        ServerLevel world = server.getLevel(center.dimension());
        if (world == null || !state.completed() && world.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) { fail(); return; }
        if (rogue.state().waitingForChoice()) return;
        long tick = host.utilityTick();
        for (Mob enemy : List.copyOf(enemies.values())) {
            if (enemy.isDeadOrDying()) { died(enemy); continue; }
            if (enemy.isRemoved()) {
                state.missing(state.epoch(), enemy.getUUID()); enemies.remove(enemy.getUUID()); attackScales.remove(enemy.getUUID()); continue;
            }
            List<ServerPlayer> nearby = fighters.stream().filter(player -> player.level() == world).toList();
            if (!nearby.isEmpty()) {
                ServerPlayer target = nearby.stream().min(java.util.Comparator.comparingDouble(enemy::distanceToSqr)).orElseThrow();
                if (enemy.distanceToSqr(target) > 96.0D * 96.0D) {
                    spawnPos(world, target.blockPosition(), enemies.size(), enemy);
                }
                enemy.setTarget(target);
            }
        }
        PveWaveState.WaveStart started = host.playMode() == PlayMode.RG && state.phase() == PveWaveState.Phase.WAITING
            ? state.beginWave(tick, rogue.state().plan()) : state.beginWave(tick);
        if (started != null) {
            announce("play.pve.wave", started.wave().number(), PveWaves.TOTAL_WAVES, started.wave().spawns().size());
            announceStage();
        }
        if (state.phase() == PveWaveState.Phase.SPAWNING) {
            for (PveWaves.Spawn spawn : state.pendingSpawns()) {
                if (!spawn(world, spawn, fighters)) { state.abort(); break; }
            }
            if (!state.failed()) state.finishSpawning(state.epoch(), tick);
        }
        if (state.failed()) { fail(); return; }
        PveWaveState.Reward reward;
        while ((reward = state.claimReward()) != null) {
            reward(reward, fighters);
            if (host.playMode() == PlayMode.RG) rogue.afterWave(reward.wave());
            else if (!reward.completed()) previewNext();
        }
        updateBossBars(server);
        if (state.completed()) {
            completePresentation(tick);
        }
    }
    private boolean spawn(ServerLevel world, PveWaves.Spawn spec, List<ServerPlayer> fighters) {
        EntityType<?> type = switch (spec.kind()) {
            case ZOMBIE -> EntityTypes.ZOMBIE; case SKELETON -> EntityTypes.SKELETON;
            case SPIDER -> EntityTypes.SPIDER; case HUSK -> EntityTypes.HUSK; case STRAY -> EntityTypes.STRAY;
            case VINDICATOR -> EntityTypes.VINDICATOR; case RAVAGER -> EntityTypes.RAVAGER;
        };
        if (!(type.create(world, EntitySpawnReason.EVENT) instanceof Mob mob)) return false;
        if (spawnPos(world, center.pos(), spec.slot(), mob) == null) { mob.discard(); return false; }
        if (mob.getAttribute(Attributes.MAX_HEALTH) != null) {
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(mob.getMaxHealth() * spec.healthMultiplier());
            mob.setHealth(mob.getMaxHealth());
        }
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(
            mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * spec.damageMultiplier());
        mob.setPersistenceRequired(); mob.setCanPickUpLoot(false); mob.addTag(OWNED);
        if (host.playMode() == PlayMode.RG && mob.getAttribute(Attributes.MOVEMENT_SPEED) != null)
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * rogue.state().selected().threat().speed());
        if (spec.kind() == PveWaves.Kind.ZOMBIE || spec.kind() == PveWaves.Kind.HUSK
            || spec.kind() == PveWaves.Kind.SKELETON || spec.kind() == PveWaves.Kind.STRAY) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        }
        if (spec.kind() == PveWaves.Kind.SKELETON || spec.kind() == PveWaves.Kind.STRAY) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        if (spec.kind() == PveWaves.Kind.VINDICATOR) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        if (spec.boss()) { mob.setCustomName(Component.literal(host.text("play.pve.boss." + state.wave()))); mob.setCustomNameVisible(true); }
        if (!state.registerSpawn(state.epoch(), spec.slot(), mob.getUUID())) { mob.discard(); return false; }
        enemies.put(mob.getUUID(), mob);
        attackScales.put(mob.getUUID(), spec.damageMultiplier());
        if (spec.boss()) bossId = mob.getUUID();
        if (!world.addFreshEntity(mob)) { enemies.remove(mob.getUUID()); attackScales.remove(mob.getUUID()); mob.discard(); return false; }
        fighters.stream().filter(player -> player.level() == world).findFirst().ifPresent(mob::setTarget);
        return true;
    }
    private BlockPos spawnPos(ServerLevel world, BlockPos origin, int slot, Mob mob) {
        net.minecraft.world.phys.Vec3 previous = mob.position();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = (slot * 2.399963229728653D) + attempt * 0.43D;
            int radius = 14 + attempt % 10;
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos pos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (world.getBlockState(pos.below()).isSolid() && world.getFluidState(pos).isEmpty()
                && world.getBlockState(pos).isAir() && world.getBlockState(pos.above()).isAir()) {
                mob.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                if (world.noCollision(mob, mob.getBoundingBox())) return pos;
            }
        }
        mob.setPos(previous);
        return null;
    }
    private void reward(PveWaveState.Reward reward, List<ServerPlayer> fighters) {
        for (ServerPlayer player : fighters) {
            give(player, new ItemStack(Items.COOKED_BEEF, reward.boss() ? 12 : 4));
            give(player, new ItemStack(Items.ARROW, reward.boss() ? 32 : 12));
            give(player, host.magic().createCompanion(reward.boss() ? TeamMagicItem.Kind.GREATER_MANA_POTION : TeamMagicItem.Kind.MANA_POTION, player));
            if (reward.wave() % 2 == 0 || reward.boss()) give(player, host.magic().createCompanion(TeamMagicItem.Kind.MANA_APPLE, player));
            List<com.teamlifebind.common.SpellDefinition> books = host.playMode() == PlayMode.RG
                ? SpellCatalogue.tier(rogue.state().rewardTier(reward))
                : SpellCatalogue.all().stream().filter(spell -> spell.tier() <= reward.tier() && (reward.boss() || spell.tier() <= 2)).toList();
            if (!books.isEmpty()) give(player, host.magic().createBook(books.get(Math.floorMod(player.getUUID().hashCode() + reward.wave(), books.size())), player));
            player.giveExperiencePoints(10 + reward.wave() * 5);
            int provisions = rogue.state().level(com.teamlifebind.common.RogueRun.Relic.PROVISIONER);
            if (provisions > 0) {
                give(player, new ItemStack(Items.COOKED_BEEF, 4 * provisions));
                for (int count = 0; count < provisions; count++) give(player, host.magic().createCompanion(TeamMagicItem.Kind.MANA_POTION, player));
            }
        }
        announce("play.pve.reward", reward.wave());
    }
    private static void give(ServerPlayer player, ItemStack item) {
        if (!player.getInventory().add(item)) player.drop(item, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }
    private void announce(String key, Object... args) {
        MinecraftServer server = host.utilityServer();
        if (server != null) for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (host.campaignAssignments().containsKey(player.getUUID())) player.sendSystemMessage(Component.literal(host.text(player, key, args)));
    }
    private void fail() {
        presentFailure(); host.campaignFinish(null, true);
    }
    void presentFailure() {
        if (failurePresented || state.completed() || state.phase() == PveWaveState.Phase.IDLE) return;
        failurePresented = true; state.abort(host.utilityTick()); announce("play.pve.failed"); announceSummary();
    }
    private void completePresentation(long tick) {
        if (completedAt >= 0L) return;
        completedAt = tick; announce("play.pve.victory"); announceSummary();
    }
    private void announceSummary() {
        var summary = state.summary(host.utilityTick());
        for (ServerPlayer player : host.utilityServer().getPlayerList().getPlayers())
            if (host.campaignAssignments().containsKey(player.getUUID())) player.sendSystemMessage(Component.literal(host.text(player,
                "play.pve.summary", host.text(player, summary.difficulty().languageKey()), summary.clearedWaves(), PveWaves.TOTAL_WAVES,
                summary.defeats(), summary.elapsedLabel())));
        if (host.playMode() == PlayMode.RG) announce("rg.result", Long.toUnsignedString(rogue.state().seed(), 16), rogue.state().totalRelics());
    }
    private String stageKey(int wave) {
        var route = rogue.state().selected();
        return host.playMode() == PlayMode.RG && route != null ? route.theme().key() : PveWaves.nameKey(wave);
    }
    private void announceStage() {
        for (ServerPlayer player : host.utilityServer().getPlayerList().getPlayers())
            if (host.campaignAssignments().containsKey(player.getUUID())) player.sendSystemMessage(Component.literal(host.text(player,
                "play.pve.stage.start", host.text(player, stageKey(state.wave())), host.text(player, state.difficulty().languageKey()))));
    }
    private void previewNext() {
        int next = Math.min(PveWaves.TOTAL_WAVES, state.wave() + 1);
        for (ServerPlayer player : host.utilityServer().getPlayerList().getPlayers())
            if (host.campaignAssignments().containsKey(player.getUUID())) player.sendSystemMessage(Component.literal(host.text(player,
                "play.pve.preview", next, host.text(player, PveWaves.nameKey(next)), Math.max(0L, (state.nextWaveTick() - host.utilityTick() + 19L) / 20L))));
    }
    private void updateBossBars(MinecraftServer server) {
        Mob boss = bossId == null ? null : enemies.get(bossId);
        if (boss == null || boss.isRemoved() || boss.isDeadOrDying() || boss.getMaxHealth() <= 0 || state.phase() != PveWaveState.Phase.FIGHTING) {
            clearBossBars(); return;
        }
        List<ServerPlayer> viewers = server.getPlayerList().getPlayers().stream()
            .filter(player -> player.level() == boss.level() && host.campaignAssignments().containsKey(player.getUUID())).toList();
        java.util.Set<UUID> visible = new java.util.HashSet<>();
        for (ServerPlayer viewer : viewers) {
            visible.add(viewer.getUUID());
            ServerBossEvent bar = bossBars.computeIfAbsent(viewer.getUUID(), id -> new ServerBossEvent(UUID.randomUUID(), Component.empty(),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
            bar.setName(Component.literal(host.text(viewer, "play.pve.boss.bar", host.text(viewer, "play.pve.boss." + state.wave()),
                host.text(viewer, state.difficulty().languageKey()), (int) Math.ceil(boss.getHealth()), (int) Math.ceil(boss.getMaxHealth()))));
            bar.setProgress(Math.max(0F, Math.min(1F, boss.getHealth() / boss.getMaxHealth())));
            if (!bar.getPlayers().contains(viewer)) { bar.removeAllPlayers(); bar.addPlayer(viewer); }
        }
        bossBars.entrySet().removeIf(entry -> { if (visible.contains(entry.getKey())) return false; entry.getValue().removeAllPlayers(); return true; });
    }
    private void clearBossBars() { bossBars.values().forEach(ServerBossEvent::removeAllPlayers); bossBars.clear(); }
}
