package com.teamlifebind.paper;

import com.teamlifebind.common.PlayMode;
import com.teamlifebind.common.PveWaves;
import com.teamlifebind.common.PveWaveState;
import com.teamlifebind.common.SpellCatalogue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Owns only the mobs it spawned; deaths of natural mobs never advance encounters. */
final class TeamPveController {
    private final TeamLifeBindPaperPlugin plugin;
    private final PveWaveState state = new PveWaveState();
    private final TeamRogueController rogue;
    private final Map<UUID, Mob> enemies = new HashMap<>();
    private final Map<UUID, Double> attackScales = new HashMap<>();
    private final java.util.Random random = new java.util.Random();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private UUID bossId;
    private World world;
    private Location anchor;
    private Boolean previousMobGriefing;
    private long extractionAt = -1;
    private boolean failurePresented;

    TeamPveController(TeamLifeBindPaperPlugin plugin) { this.plugin = plugin; rogue = new TeamRogueController(plugin, this); }
    PveWaveState state() { return state; }
    TeamRogueController rogue() { return rogue; }
    boolean inArena(Player player) { return world != null && player != null && player.getWorld() == world; }
    boolean owns(UUID id) { return enemies.containsKey(id); }

    void equip(Player player) {
        player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        player.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.IRON_PICKAXE));
        player.getInventory().setItem(2, new ItemStack(Material.BOW));
        player.getInventory().addItem(new ItemStack(Material.ARROW, 64), new ItemStack(Material.BREAD, 16));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        if (plugin.campaigns().rules().matchMode() != com.teamlifebind.common.MatchMode.RANDOM_GRIMOIRE) {
            var pool = SpellCatalogue.starterPool();
            give(player, plugin.magic().createBook(pool.get(random.nextInt(pool.size())), player));
        }
    }

    void start(World arena, Location spawn) {
        clear();
        world = arena;
        previousMobGriefing = world.getGameRuleValue(org.bukkit.GameRule.MOB_GRIEFING);
        world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, false);
        anchor = spawn == null ? arena.getSpawnLocation() : spawn.clone();
        state.start(Math.max(1, plugin.campaignAssignments().size()), plugin.utilityTick(), plugin.campaigns().rules().pveDifficulty());
        if (plugin.playMode() == PlayMode.RG) rogue.start(Math.max(1, plugin.campaignAssignments().size())); else previewNext();
    }

    void tick(long tick) {
        if (world == null || !plugin.isMatchRunning() || !plugin.playMode().encounter()) return;
        rogue.tick(tick);
        updateBossBars();
        List<Player> present = participants();
        if (state.completed()) {
            rewards(present, tick);
            if (extractionAt < 0) extractionAt = deadline(tick);
            if (tick >= extractionAt) plugin.finishPve(true);
            return;
        }
        if (world.getDifficulty() == org.bukkit.Difficulty.PEACEFUL) { fail(); return; }
        if (present.isEmpty()) {
            List<Player> online = plugin.campaignAssignments().keySet().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).toList();
            if (!online.isEmpty() && online.stream().allMatch(player -> plugin.isRespawnLocked(player.getUniqueId()))) fail();
            return;
        }
        if (rogue.state().waitingForChoice()) return;
        long epoch = state.epoch();
        for (var entry : List.copyOf(enemies.entrySet())) {
            Mob mob = entry.getValue();
            // Removal/unload is not death credit. The missing slot is recreated below.
            if (!mob.isValid() || mob.isDead()) {
                enemies.remove(entry.getKey());
                attackScales.remove(entry.getKey());
                state.missing(epoch, entry.getKey());
                continue;
            }
            if (tick % 20 == 0) {
                if (mob.getWorld() != world || mob.getLocation().distanceSquared(anchor) > 96 * 96) {
                    Location returned = safeSpawn(0, mob.getType() == EntityType.RAVAGER, mob.getType() == EntityType.SPIDER);
                    if (returned == null) { fail(); return; }
                    mob.teleport(returned);
                }
                Player target = present.stream().min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(mob.getLocation()))).orElse(present.getFirst());
                mob.setTarget(target);
            }
        }
        PveWaveState.WaveStart next = plugin.playMode() == PlayMode.RG && state.phase() == PveWaveState.Phase.WAITING
            ? state.beginWave(tick, rogue.state().plan()) : state.beginWave(tick);
        if (next != null) {
            broadcast("play.pve.wave", next.wave().number(), PveWaves.TOTAL_WAVES, next.wave().spawns().size());
            for (Player viewer : viewers()) viewer.sendMessage(ChatColor.GOLD + plugin.text(viewer, "play.pve.stage.start",
                plugin.text(viewer, stageKey(state.wave())), plugin.text(viewer, state.difficulty().languageKey())));
        }
        if (state.phase() == PveWaveState.Phase.SPAWNING) {
            for (PveWaves.Spawn planned : state.pendingSpawns()) if (!spawn(planned, state.epoch(), present)) { fail(); return; }
            if (!state.finishSpawning(state.epoch(), tick)) { fail(); return; }
        }
        rewards(present, tick);
        updateBossBars();
        if (state.failed()) fail();
    }

    private void rewards(List<Player> present, long tick) {
        PveWaveState.Reward reward;
        while ((reward = state.claimReward()) != null) {
            for (Player player : present) reward(player, reward);
            broadcast("play.pve.reward", reward.wave());
            if (plugin.playMode() == PlayMode.RG) rogue.afterWave(reward.wave());
            if (reward.completed()) {
                if (extractionAt < 0) extractionAt = deadline(tick);
                broadcast("play.pve.victory");
                announceSummary();
            } else if (plugin.playMode() != PlayMode.RG) previewNext();
        }
    }

    private boolean spawn(PveWaves.Spawn planned, long epoch, List<Player> present) {
        Location location = safeSpawn(planned.slot(), planned.boss(), planned.kind() == PveWaves.Kind.SPIDER);
        if (location == null) return false;
        if (!(world.spawnEntity(location, EntityType.valueOf(planned.kind().name())) instanceof Mob mob)) return false;
        if (!mob.isValid()) { mob.remove(); return false; }
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.addScoreboardTag("tlb_pve_encounter");
        if (mob instanceof org.bukkit.entity.Zombie zombie) zombie.setCanBreakDoors(false);
        var health = mob.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) { health.setBaseValue(Math.min(2048, health.getBaseValue() * planned.healthMultiplier())); mob.setHealth(health.getValue()); }
        var damage = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(Math.min(100, damage.getBaseValue() * planned.damageMultiplier()));
        var speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (plugin.playMode() == PlayMode.RG && speed != null) speed.setBaseValue(speed.getBaseValue() * rogue.state().selected().threat().speed());
        if (mob.getEquipment() != null) {
            mob.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            mob.getEquipment().setHelmetDropChance(0);
            mob.getEquipment().setItemInMainHandDropChance(0);
            if (planned.kind() == PveWaves.Kind.SKELETON || planned.kind() == PveWaves.Kind.STRAY) mob.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
            if (planned.kind() == PveWaves.Kind.VINDICATOR) mob.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        }
        if (planned.boss()) {
            mob.setCustomName(plugin.text("play.pve.boss." + state.wave()));
            mob.setCustomNameVisible(true);
            mob.setGlowing(true);
        }
        if (!state.registerSpawn(epoch, planned.slot(), mob.getUniqueId())) { mob.remove(); return false; }
        enemies.put(mob.getUniqueId(), mob);
        attackScales.put(mob.getUniqueId(), planned.damageMultiplier());
        if (planned.boss()) bossId = mob.getUniqueId();
        mob.setTarget(present.get(planned.slot() % present.size()));
        return true;
    }

    private Location safeSpawn(int slot, boolean boss, boolean spider) {
        double width = boss ? 1.1 : spider ? 0.8 : 0.4, height = boss ? 2.4 : 2;
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = ((slot + attempt) * 2.399963229728653) % (Math.PI * 2);
            double radius = 18 + (slot + attempt) % 5 * 3;
            Location location = plugin.utilitySafeSurface(world, (int) Math.floor(anchor.getX() + Math.cos(angle) * radius),
                (int) Math.floor(anchor.getZ() + Math.sin(angle) * radius));
            if (location == null || !location.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) continue;
            boolean clear = true;
            for (int x = (int) Math.floor(location.getX() - width); x <= Math.floor(location.getX() + width) && clear; x++)
                for (int z = (int) Math.floor(location.getZ() - width); z <= Math.floor(location.getZ() + width) && clear; z++)
                    for (int y = location.getBlockY(); y < Math.ceil(location.getY() + height); y++) {
                        var block = world.getBlockAt(x, y, z);
                        if (!block.isPassable() || block.isLiquid()) { clear = false; break; }
                    }
            if (clear && world.getNearbyEntities(location.clone().add(0, height / 2, 0), width, height / 2, width).stream().noneMatch(entity -> entity instanceof LivingEntity))
                return location;
        }
        return null;
    }

    void died(LivingEntity entity) {
        attackScales.remove(entity.getUniqueId());
        if (entity.getUniqueId().equals(bossId)) { bossId = null; clearBossBars(); }
        if (enemies.remove(entity.getUniqueId()) != null) {
            state.defeated(state.epoch(), entity.getUniqueId(), plugin.utilityTick());
            if (state.completed() && extractionAt < 0) extractionAt = deadline(plugin.utilityTick());
        }
    }

    private List<Player> participants() {
        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getWorld() == world && plugin.isUtilityParticipant(player)) result.add(player);
        return result;
    }
    void bowShot(LivingEntity shooter, org.bukkit.entity.Entity projectile) {
        Double scale = attackScales.get(shooter.getUniqueId());
        if (scale != null && projectile instanceof org.bukkit.entity.AbstractArrow arrow)
            arrow.setDamage(arrow.getDamage() * scale);
    }

    private void reward(Player player, PveWaveState.Reward reward) {
        give(player, new ItemStack(Material.BREAD, reward.boss() ? 12 : 6));
        give(player, new ItemStack(Material.ARROW, 16));
        give(player, plugin.magic().createCompanion(reward.boss() ? "greater_mana_potion" : "mana_potion", player));
        give(player, plugin.magic().createCompanion("mana_apple", player));
        var books = SpellCatalogue.tier(plugin.playMode() == PlayMode.RG ? rogue.state().rewardTier(reward) : reward.tier());
        if (!books.isEmpty()) give(player, plugin.magic().createBook(books.get(random.nextInt(books.size())), player));
        int provisions = rogue.state().level(com.teamlifebind.common.RogueRun.Relic.PROVISIONER);
        if (provisions > 0) {
            give(player, new ItemStack(Material.BREAD, 4 * provisions));
            for (int count = 0; count < provisions; count++) give(player, plugin.magic().createCompanion("mana_potion", player));
        }
    }

    void sidebar(Player viewer, List<String> lines) {
        if (world == null || viewer == null || !plugin.playMode().encounter()) return;
        if (rogue.state().waitingForChoice()) { lines.add(ChatColor.GOLD + rogue.choiceHud(viewer)); return; }
        if (plugin.playMode() == PlayMode.RG) lines.add(ChatColor.LIGHT_PURPLE + plugin.text(viewer, "rg.relics", rogue.state().totalRelics()));
        lines.add(ChatColor.GOLD + plugin.text(viewer, "play.pve.hud.fight", plugin.text(viewer, state.difficulty().languageKey()), Math.max(1, state.wave()), PveWaves.TOTAL_WAVES, state.remaining()));
        if (state.completed()) lines.add(ChatColor.GREEN + plugin.text(viewer, "play.pve.extract", Math.max(0, extractionAt - plugin.utilityTick() + 19) / 20));
        if (state.phase() == PveWaveState.Phase.WAITING)
            lines.add(ChatColor.AQUA + plugin.text(viewer, "play.pve.hud.wait", plugin.text(viewer, stageKey(Math.min(PveWaves.TOTAL_WAVES, state.wave() + 1))), Math.max(0, state.nextWaveTick() - plugin.utilityTick() + 19) / 20));
    }

    void clear() {
        rogue.clear();
        clearBossBars(); bossId = null;
        state.clear();
        for (Mob mob : List.copyOf(enemies.values())) mob.remove();
        enemies.clear();
        attackScales.clear();
        if (world != null && previousMobGriefing != null && Bukkit.getWorld(world.getUID()) != null)
            world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, previousMobGriefing);
        world = null; anchor = null; previousMobGriefing = null; extractionAt = -1; failurePresented = false;
    }
    private long deadline(long tick) { return tick > Long.MAX_VALUE - 400 ? Long.MAX_VALUE : tick + 400; }
    private void fail() { presentFailure(); plugin.finishPve(false); }
    void presentFailure() {
        if (failurePresented || state.completed() || state.phase() == PveWaveState.Phase.IDLE) return;
        failurePresented = true; state.abort(plugin.utilityTick()); broadcast("play.pve.failed"); announceSummary();
    }
    private List<Player> viewers() {
        return Bukkit.getOnlinePlayers().stream().filter(player -> plugin.campaignAssignments().containsKey(player.getUniqueId())).map(player -> (Player) player).toList();
    }
    private void announceSummary() {
        var summary = state.summary(plugin.utilityTick());
        for (Player viewer : viewers()) viewer.sendMessage(ChatColor.GOLD + plugin.text(viewer, "play.pve.summary",
            plugin.text(viewer, summary.difficulty().languageKey()), summary.clearedWaves(), PveWaves.TOTAL_WAVES, summary.defeats(), summary.elapsedLabel()));
        if (plugin.playMode() == PlayMode.RG) broadcast("rg.result", Long.toUnsignedString(rogue.state().seed(), 16), rogue.state().totalRelics());
    }
    private String stageKey(int wave) {
        var route = rogue.state().selected();
        return plugin.playMode() == PlayMode.RG && route != null ? route.theme().key() : PveWaves.nameKey(wave);
    }
    private void previewNext() {
        int next = Math.min(PveWaves.TOTAL_WAVES, state.wave() + 1);
        for (Player viewer : viewers()) viewer.sendMessage(ChatColor.GOLD + plugin.text(viewer, "play.pve.preview", next,
            plugin.text(viewer, PveWaves.nameKey(next)), Math.max(0L, (state.nextWaveTick() - plugin.utilityTick() + 19L) / 20L)));
    }
    private void updateBossBars() {
        Mob boss = bossId == null ? null : enemies.get(bossId);
        if (boss == null || !boss.isValid() || boss.isDead() || state.phase() != PveWaveState.Phase.FIGHTING) { clearBossBars(); return; }
        var attribute = boss.getAttribute(Attribute.MAX_HEALTH);
        double maximum = attribute == null ? boss.getHealth() : attribute.getValue();
        if (maximum <= 0) { clearBossBars(); return; }
        java.util.Set<UUID> visible = new java.util.HashSet<>();
        for (Player viewer : viewers()) if (viewer.getWorld() == boss.getWorld()) {
            visible.add(viewer.getUniqueId());
            BossBar bar = bossBars.computeIfAbsent(viewer.getUniqueId(), id -> Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID));
            String title = plugin.text(viewer, "play.pve.boss.bar", plugin.text(viewer, "play.pve.boss." + state.wave()),
                plugin.text(viewer, state.difficulty().languageKey()), (int) Math.ceil(boss.getHealth()), (int) Math.ceil(maximum));
            if (!bar.getTitle().equals(title)) bar.setTitle(title);
            // Boss progress travels as a float; compare at wire precision before asking Bukkit to broadcast it.
            float progress = (float) Math.max(0D, Math.min(1D, boss.getHealth() / maximum));
            if (Float.compare((float) bar.getProgress(), progress) != 0) bar.setProgress(progress);
            if (bar.getPlayers().stream().noneMatch(player -> player == viewer)) { bar.removeAll(); bar.addPlayer(viewer); }
        }
        bossBars.entrySet().removeIf(entry -> { if (visible.contains(entry.getKey())) return false; entry.getValue().removeAll(); return true; });
    }
    private void clearBossBars() { bossBars.values().forEach(BossBar::removeAll); bossBars.clear(); }
    private void broadcast(String key, Object... args) {
        for (Player player : Bukkit.getOnlinePlayers()) if (plugin.utilityTeam(player) != null) player.sendMessage(ChatColor.GOLD + plugin.text(player, key, args));
    }
    private void give(Player player, ItemStack stack) {
        if (stack != null) for (ItemStack rest : player.getInventory().addItem(stack).values()) player.getWorld().dropItemNaturally(player.getLocation(), rest);
    }
}
