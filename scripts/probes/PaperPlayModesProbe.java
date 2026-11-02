import com.teamlifebind.common.*;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Real Paper entities, registered players, menus and survival files; no graphical client. */
public final class PaperPlayModesProbe extends JavaPlugin {
    private JavaPlugin target;
    private TeamLifeBindEngine engine;
    private Object campaign, magic, profiles, pve, originalMagic;
    private MagicState mana;
    private Field magicField, tickField, activeWorldField;
    private long originalTick, clock;
    private int assertions, bosses;
    private final Map<UUID, Integer> bossPackets = new HashMap<>();
    private final List<ServerPlayer> players = new ArrayList<>();
    private List<ServerPlayer> nativePlayers;
    private Map<UUID, ServerPlayer> byId;
    private World arena, survival;
    private String config;
    private final Map<Path, byte[]> files = new HashMap<>();
    private final Map<World, Set<UUID>> entities = new HashMap<>();

    @Override public void onEnable() { Bukkit.getScheduler().runTaskLater(this, this::run, 20); }
    @SuppressWarnings("unchecked") private void run() {
        Throwable problem = null;
        try {
            check(Bukkit.isPrimaryThread() && Bukkit.getOnlinePlayers().isEmpty(), "isolated main thread without connected users");
            check(getDataFolder().toPath().toAbsolutePath().toString().contains(".tools"), "prepared isolated fixture");
            target = (JavaPlugin) Bukkit.getPluginManager().getPlugin("TeamLifeBind");
            check(target != null && target.isEnabled(), "production plugin enabled");
            config = target.getConfig().saveToString();
            engine = (TeamLifeBindEngine) field(target, "engine").get(target);
            check(!engine.isRunning(), "idle fixture");
            tickField = field(target, "sharedStateTick"); originalTick = tickField.getLong(target); clock = originalTick;
            activeWorldField = field(target, "activeMatchWorldName");
            campaign = call(target, "campaigns"); profiles = field(target, "survivalProfiles").get(target); pve = call(target, "pve");
            magicField = field(target, "magicController"); originalMagic = magicField.get(target);
            var constructor = originalMagic.getClass().getDeclaredConstructor(target.getClass()); constructor.setAccessible(true);
            magic = constructor.newInstance(target); magicField.set(target, magic); mana = (MagicState) call(magic, "state");
            remember(target.getDataFolder().toPath().resolve("magic-players.properties"));
            arena = Bukkit.getWorlds().getFirst();
            check(arena.getWorldFolder().toPath().toAbsolutePath().toString().contains(".tools"), "isolated arena");
            survival = Bukkit.getWorld("tlb_survival");
            if (survival == null) survival = new WorldCreator("tlb_survival").type(WorldType.FLAT).generateStructures(false).createWorld();
            check(survival != null, "persistent survival world available");
            entities.put(arena, ids(arena)); entities.put(survival, ids(survival));
            var server = ((org.bukkit.craftbukkit.CraftServer) Bukkit.getServer()).getServer();
            nativePlayers = (List<ServerPlayer>) inherited(server.getPlayerList().getClass(), "players").get(server.getPlayerList());
            byId = (Map<UUID, ServerPlayer>) inherited(server.getPlayerList().getClass(), "playersByUUID").get(server.getPlayerList());
            Player player = create(survival, "ModesProbe");
            modes(player);
            survival(player);
            encounters(player);
            roguelike(player);
            rogueCooperation(player);
        } catch (Throwable error) { problem = unwrap(error); }
        finally {
            try {
                if (pve != null) call(pve, "clear");
                if (engine != null) engine.stop();
                if (campaign != null) call(campaign, "clear");
                if (activeWorldField != null) activeWorldField.set(target, null);
                for (ServerPlayer handle : players) {
                    Player player = handle.getBukkitEntity();
                    if (profiles != null) call(profiles, "forget", player.getUniqueId());
                    if (nativePlayers != null) nativePlayers.remove(handle);
                    if (byId != null) byId.remove(handle.getUUID());
                    ((org.bukkit.craftbukkit.CraftWorld) player.getWorld()).getHandle().removePlayerImmediately(handle, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
                for (var entry : entities.entrySet()) for (Entity entity : entry.getKey().getEntities())
                    if (!(entity instanceof Player) && !entry.getValue().contains(entity.getUniqueId())) entity.remove();
                if (magicField != null) magicField.set(target, originalMagic);
                if (tickField != null) tickField.setLong(target, originalTick);
                if (config != null) target.getConfig().loadFromString(config);
                for (var entry : files.entrySet()) if (entry.getValue() == null) Files.deleteIfExists(entry.getKey()); else Files.write(entry.getKey(), entry.getValue());
                check(Bukkit.getOnlinePlayers().isEmpty(), "native player list restored");
            } catch (Throwable error) { if (problem == null) problem = unwrap(error); else problem.addSuppressed(unwrap(error)); }
        }
        if (problem == null) getLogger().info("TLB_PAPER_PLAY_MODES_PROBE PASS assertions=" + assertions + " bosses=" + bosses + " actualEntities=true actualPlayer=true realSurvivalFiles=true clientRendered=false");
        else getLogger().log(Level.SEVERE, "TLB_PAPER_PLAY_MODES_PROBE FAIL assertions=" + assertions, problem);
    }

    private void modes(Player player) throws Exception {
        target.getConfig().set("gameplay.play-mode", null);
        target.getConfig().set("gameplay.pve-difficulty", null);
        check(((CampaignRules) call(campaign, "configuredRules")).pveDifficulty() == PveDifficulty.NORMAL, "legacy Paper settings default to standard PVE");
        check(((CampaignRules) call(campaign, "configuredRules")).playMode() == PlayMode.SUR, "old configuration without top mode now defaults to survival");
        check((Integer) call(target, "sessionTeamCount") == 1 && (Boolean) call(target, "hasEnoughOnlinePlayers", List.of(player)), "survival admits one player in one cooperative team");
        var executor = target.getCommand("tlb").getExecutor();
        executor.onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"pve", "HaRd"});
        check(((CampaignRules) call(campaign, "configuredRules")).pveDifficulty() == PveDifficulty.HARD, "real Paper difficulty command and saved config select hard");
        target.reloadConfig();
        check(((CampaignRules) call(campaign, "configuredRules")).pveDifficulty() == PveDifficulty.HARD, "difficulty persists through actual configuration reload");
        executor.onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"pve", "invalid"});
        check(((CampaignRules) call(campaign, "configuredRules")).pveDifficulty() == PveDifficulty.HARD, "invalid difficulty preserves prior config");
        call(campaign, "openMenu", player);
        check(player.getOpenInventory().getTopInventory().getItem(6).getType() == Material.DIAMOND_SWORD, "Paper menu contains a selectable difficulty option");
        var click = new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(), org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            6, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        call(campaign, "menuClick", click);
        check(click.isCancelled() && ((CampaignRules) call(campaign, "configuredRules")).pveDifficulty() == PveDifficulty.EASY, "menu cycles difficulty without taking its preview item");
        player.closeInventory();
        check(executor.onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"mode", "pvp"}), "real pvp command handled");
        check(((CampaignRules) call(campaign, "configuredRules")).playMode() == PlayMode.PVP, "pvp selection retained");
        check(!(Boolean) call(target, "hasEnoughOnlinePlayers", List.of(player)), "pvp still needs configured team count");
        executor.onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"mode", "pve"});
        executor.onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"mode", "RANDOM_GRIMOIRE"});
        CampaignRules modified = (CampaignRules) call(campaign, "configuredRules");
        check(modified.pveDifficulty() == PveDifficulty.EASY, "mode and variant commands retain chosen PVE difficulty");
        check(modified.playMode() == PlayMode.PVE && modified.matchMode() == MatchMode.RANDOM_GRIMOIRE, "old match variant does not erase the selected top-level mode");
        var completions = target.getCommand("tlb").getTabCompleter().onTabComplete(player, target.getCommand("tlb"), "tlb", new String[]{"mode", ""});
        check(completions.containsAll(List.of("sur", "pve", "pvp", "DRAGON_HUNT", "LIMITED_LIVES", "RANDOM_GRIMOIRE")), "real completion includes all new and old modes");
        target.getConfig().set("gameplay.play-mode", "SUR"); target.getConfig().set("gameplay.match-mode", "DRAGON_HUNT");
        target.getConfig().set("gameplay.border-enabled", false); target.getConfig().set("gameplay.control-point-enabled", false);
    }

    private void survival(Player player) throws Exception {
        Path profile = target.getDataFolder().toPath().resolve("survival-playerdata/" + player.getUniqueId() + ".yml"); remember(profile);
        player.getInventory().clear(); player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 17));
        player.getInventory().setItem(1, (ItemStack) call(magic, "createBook", SpellCatalogue.byId("fire_bolt"), player));
        player.setTotalExperience(99); player.setLevel(7); player.setExp(0.4f);
        player.teleport(new Location(survival, 80.5, survival.getHighestBlockYAt(80, 80) + 1, 80.5));
        check((Boolean) call(profiles, "save", player) && Files.isRegularFile(profile), "real survival inventory is saved atomically");
        player.getInventory().clear(); player.setLevel(0); player.setExp(0);
        var profileConstructor = profiles.getClass().getDeclaredConstructor(target.getClass()); profileConstructor.setAccessible(true);
        Object reloadedProfiles = profileConstructor.newInstance(target);
        Location restored = (Location) call(reloadedProfiles, "restore", player);
        check(restored != null && restored.getWorld() == survival && restored.getBlockX() == 80, "saved survival location restored");
        check(player.getInventory().getItem(0).getType() == Material.DIAMOND && player.getInventory().getItem(0).getAmount() == 17, "survival backpack restored");
        check(player.getInventory().getItem(1).hasItemMeta() && player.getLevel() == 7 && Math.abs(player.getExp() - 0.4) < 0.001, "magic item metadata and experience restored");
        check(!Files.exists(profile) && call(profiles, "restore", player) == null, "restored snapshot cannot be replayed");
        check((Boolean) call(profiles, "save", player), "second survival checkpoint saved");
        call(target, "invalidateSurvival", player);
        check(!Files.exists(profile), "survival death invalidation removes the checkpoint");

        Field directory = field(profiles, "directory"); Path normal = (Path) directory.get(profiles);
        Path blocked = target.getDataFolder().toPath().resolve("modes-save-failure.fixture"); remember(blocked); Files.writeString(blocked, "not a directory");
        directory.set(profiles, blocked);
        try {
            check(!(Boolean) call(profiles, "save", player), "real file failure is reported");
            call(target, "teleportToLobby", player);
            check(player.getWorld() == survival && player.getInventory().getItem(0).getAmount() == 17, "failed survival save does not clear inventory or teleport to lobby");
        } finally { directory.set(profiles, normal); }

        engine.start(List.of(player.getUniqueId()), new GameOptions(1, HealthPreset.ONE_HEART));
        activeWorldField.set(target, survival.getName());
        call(campaign, "startRound");
        mana.learn(player.getUniqueId(), "fire_bolt"); call(magic, "startSurvivalSession");
        mana.increaseMaxMana(player.getUniqueId(), clock); mana.increaseMaxMana(player.getUniqueId(), clock);
        call(magic, "disconnect", player); call(magic, "login", player);
        check(mana.maxMana(player.getUniqueId()) == 120 && mana.learned(player.getUniqueId()).contains("fire_bolt"), "same survival session reconnect keeps growth and exploration learning");
        Player joined = create(survival, "ModesJoin");
        call(target, "handlePlayerJoin", joined);
        check(engine.teamForPlayer(joined.getUniqueId()) == 1 && ((RoundCampaignState) call(campaign, "state")).teamForPlayer(joined.getUniqueId()) == 1, "new survival player joins the live cooperative team");
        check((Boolean) call(target, "shouldCancelFriendlyFire", player, joined), "survival blocks player friendly fire");
        call(magic, "resetRound");
        check(mana.maxMana(player.getUniqueId()) == 100 && mana.learned(player.getUniqueId()).contains("fire_bolt"), "ending survival session resets cap without erasing exploration spells");
        var magicConstructor = magic.getClass().getDeclaredConstructor(target.getClass()); magicConstructor.setAccessible(true);
        Object reloadedMagic = magicConstructor.newInstance(target); call(reloadedMagic, "login", player);
        MagicState reloadedMana = (MagicState) call(reloadedMagic, "state");
        check(reloadedMana.maxMana(player.getUniqueId()) == 100 && reloadedMana.learned(player.getUniqueId()).contains("fire_bolt"), "fresh magic controller reload retains learning and baseline cap");
        check((Boolean) call(profiles, "save", player) && Files.exists(profile), "checkpoint before offline life-bind death exists");
        call(target, "campaignQueueRespawn", player);
        check(!Files.exists(profile), "offline life-bind respawn invalidates old survival inventory checkpoint");
        ((Map<?, ?>) field(target, "pendingRespawns").get(target)).clear();
        check((Boolean) call(profiles, "save", player) && Files.exists(profile), "checkpoint before permanent lock exists");
        call(target, "campaignLockTeam", 1);
        check(!Files.exists(profile), "permanent lock invalidates old survival inventory checkpoint");
        ((Set<?>) field(target, "noRespawnLockedPlayers").get(target)).clear();
        Boolean keepInventory = survival.getGameRuleValue(GameRule.KEEP_INVENTORY);
        Path tombstone = profile.resolveSibling(player.getUniqueId() + ".dead"); remember(tombstone);
        try {
            survival.setGameRule(GameRule.KEEP_INVENTORY, false);
            player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 19)); player.setLevel(8);
            check((Boolean) call(profiles, "save", player), "checkpoint for absent teammate exists");
            byId.remove(((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle().getUUID());
            try { call(target, "campaignPrepareOfflineWipe", 1); }
            finally { byId.put(player.getUniqueId(), ((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle()); }
            check(Files.isRegularFile(tombstone) && !Files.exists(profile), "real offline team-wipe path persists death marker before removing checkpoint");
            Object afterRestart = profileConstructor.newInstance(target);
            check((Boolean) call(afterRestart, "applyOfflineDeath", player) && !player.getInventory().contains(Material.DIAMOND) && player.getLevel() == 0,
                "new helper after restart applies offline death to original playerdata inventory");
            player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 2));
            check((Boolean) call(afterRestart, "applyOfflineDeath", player) && player.getInventory().contains(Material.DIAMOND) && !Files.exists(tombstone),
                "consumed offline death marker cannot clear a second inventory");
            survival.setGameRule(GameRule.KEEP_INVENTORY, true); player.setLevel(9);
            check((Boolean) call(profiles, "save", player) && (Boolean) call(profiles, "markOfflineDeath", player.getUniqueId(), false), "keepInventory offline death marker is saved using checkpoint world rule");
            check((Boolean) call(profileConstructor.newInstance(target), "applyOfflineDeath", player)
                && player.getInventory().getItem(0).getAmount() == 2 && player.getLevel() == 9 && !Files.exists(tombstone),
                "offline death after restart honors keepInventory without restoring a stale snapshot");
        } finally { survival.setGameRule(GameRule.KEEP_INVENTORY, keepInventory); }
        engine.stop(); call(campaign, "clear"); activeWorldField.set(target, null);
    }

    @SuppressWarnings("unchecked") private void encounters(Player player) throws Exception {
        target.getConfig().set("gameplay.play-mode", "PVE");
        target.getConfig().set("gameplay.pve-difficulty", "HARD");
        player.teleport(arena.getSpawnLocation()); player.setGameMode(GameMode.SURVIVAL);
        engine.start(List.of(player.getUniqueId()), new GameOptions(1, HealthPreset.ONE_HEART)); activeWorldField.set(target, arena.getName());
        Map<Integer, Location> spawns = (Map<Integer, Location>) field(target, "matchTeamSpawns").get(target); spawns.put(1, player.getLocation());
        call(campaign, "startRound"); call(magic, "startRound");
        Player newcomer = create(arena, "PveNew");
        call(target, "handlePlayerJoin", newcomer);
        check(engine.teamForPlayer(newcomer.getUniqueId()) == null && newcomer.getGameMode() == GameMode.SPECTATOR, "mid-run new pve arrivals spectate");
        call(pve, "equip", player);
        check(player.getInventory().contains(Material.IRON_SWORD) && player.getInventory().contains(Material.BOW) && player.getInventory().contains(Material.BREAD), "pve has playable initial equipment");
        Boolean grief = arena.getGameRuleValue(GameRule.MOB_GRIEFING);
        call(pve, "start", arena, player.getLocation());
        check(Boolean.FALSE.equals(arena.getGameRuleValue(GameRule.MOB_GRIEFING)), "pve monsters cannot grief the arena");
        PveWaveState progress = (PveWaveState) call(pve, "state");
        target.getCommand("tlb").getExecutor().onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"pve", "easy"});
        check(progress.difficulty() == PveDifficulty.HARD && "HARD".equals(target.getConfig().getString("gameplay.pve-difficulty")), "running Paper PVE rejects difficulty changes");
        List<String> preview = new ArrayList<>(); call(pve, "sidebar", player, preview);
        String previewName = (String) call(target, "text", player, PveWaves.nameKey(1), new Object[]{});
        check(preview.stream().anyMatch(line -> line.contains(previewName)), "Paper sidebar previews the named next stage");
        Map<UUID, Mob> enemies = (Map<UUID, Mob>) field(pve, "enemies").get(pve);
        clock += PveWaveState.INITIAL_DELAY_TICKS; tickField.setLong(target, clock); call(pve, "tick", clock);
        check(progress.wave() == 1 && enemies.size() == 4, "first actual encounter contains its four registered mobs");
        check(enemies.values().stream().allMatch(mob -> Math.abs(mob.getAttribute(Attribute.MAX_HEALTH).getValue() - 27D) < .01D), "hard tuning changes real Paper zombie health to 27");
        int remaining = progress.remaining();
        Mob natural = (Mob) arena.spawnEntity(player.getLocation().clone().add(3, 0, 0), EntityType.ZOMBIE); natural.setHealth(0);
        check(progress.remaining() == remaining, "natural mob deaths never give encounter kill credit");
        Mob removed = enemies.values().iterator().next(); UUID removedId = removed.getUniqueId(); removed.remove();
        clock++; tickField.setLong(target, clock); call(pve, "tick", clock);
        check(progress.remaining() == remaining && enemies.size() == remaining && !enemies.containsKey(removedId), "removed or unloaded encounter mob is replaced without free progress");
        for (int wave = 1; wave <= PveWaves.TOTAL_WAVES; wave++) {
            check(progress.wave() == wave && !enemies.isEmpty(), "wave " + wave + " is actually populated");
            if (wave == 3) verifyRangedDamage(enemies.values().stream().filter(mob -> mob.getType() == EntityType.SKELETON).findFirst().orElseThrow(), player);
            if (wave == 5 || wave == 10) {
                Mob boss = enemies.values().stream().filter(Entity::isGlowing).findFirst().orElseThrow();
                verifyBossBar(boss, player, newcomer);
                check(boss.getType() == EntityType.RAVAGER && boss.getAttribute(Attribute.MAX_HEALTH).getValue() >= 300, "actual scaled boss on wave " + wave); bosses++;
            }
            // The production twenty-second rest naturally clears corpses. This synchronous probe
            // advances the controller clock, so remove bodies after their real death events fired.
            for (Mob mob : List.copyOf(enemies.values())) { mob.setAI(false); mob.setHealth(0); mob.remove(); }
            check(progress.remaining() == 0 && enemies.isEmpty(), "real death events clear wave " + wave);
            check(((Map<?, ?>) field(pve, "bossBars").get(pve)).isEmpty(), "boss death immediately clears the displayed Paper bar");
            check(((Map<?, ?>) field(pve, "attackScales").get(pve)).isEmpty(), "Paper deaths release projectile scaling records");
            clock++; tickField.setLong(target, clock); call(pve, "tick", clock);
            if (wave != PveWaves.TOTAL_WAVES) {
                check(progress.phase() == PveWaveState.Phase.WAITING && player.getInventory().contains(Material.APPLE), "cleared wave grants real items and waits");
                clock = progress.nextWaveTick(); tickField.setLong(target, clock); call(pve, "tick", clock);
            }
        }
        check(engine.isRunning() && progress.completed(), "final wave keeps the victory showcase session alive");
        var result = progress.summary(clock);
        check(result.defeats() == 78 && result.clearedWaves() == 10 && result.difficulty() == PveDifficulty.HARD, "Paper result totals exclude natural deaths and missing replacements");
        ItemStack[] rewardInventory = Arrays.stream(player.getInventory().getContents()).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        clock++; tickField.setLong(target, clock); call(pve, "tick", clock);
        check(Arrays.equals(rewardInventory, player.getInventory().getContents()), "victory showcase cannot repeat final rewards");
        check(progress.summary(clock + 400).equals(result), "Paper result duration excludes extraction time");
        clock += 400; tickField.setLong(target, clock); call(pve, "tick", clock);
        check(!engine.isRunning() && enemies.isEmpty() && Objects.equals(grief, arena.getGameRuleValue(GameRule.MOB_GRIEFING)), "extraction after twenty seconds stops the run and restores world griefing");
        check(Bukkit.getWorld("tlb_survival") == survival && Files.isDirectory(survival.getWorldFolder().toPath()), "cooperative survival world survives another mode's cleanup");
    }

    @SuppressWarnings("unchecked") private void roguelike(Player player) throws Exception {
        var executor = target.getCommand("tlb").getExecutor();
        executor.onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"mode", "rg"});
        check(((CampaignRules) call(campaign, "configuredRules")).playMode() == PlayMode.RG, "Paper mode command accepts RG");
        target.getConfig().set("gameplay.pve-difficulty", "NORMAL");
        player.teleport(arena.getSpawnLocation()); player.setGameMode(GameMode.SURVIVAL);
        engine.start(List.of(player.getUniqueId()), new GameOptions(1, HealthPreset.ONE_ROW)); activeWorldField.set(target, arena.getName());
        ((Map<Integer, Location>) field(target, "matchTeamSpawns").get(target)).put(1, player.getLocation());
        call(campaign, "startRound"); call(magic, "startRound"); call(pve, "equip", player); call(pve, "start", arena, player.getLocation());
        Object routes = call(pve, "rogue"); RogueRun run = (RogueRun) call(routes, "state"); PveWaveState waves = (PveWaveState) call(pve, "state");
        check(run.waitingForChoice() && player.getOpenInventory().getTopInventory().getSize() == 27, "Paper RG opens its real route menu");
        run.start(coveringRogueSeed(), 1, PveDifficulty.NORMAL); executor.onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"rg"});
        long originalToken = run.offers().getFirst().token();
        Player outsider = Bukkit.getOnlinePlayers().stream().filter(viewer -> viewer != player).findFirst().orElseThrow();
        check(!(Boolean) call(routes, "chooseCurrent", outsider, 1) && run.totalRelics() == 0, "Paper outsider cannot choose a party route");
        clock += 1000; tickField.setLong(target, clock); call(pve, "tick", clock);
        Map<UUID, Mob> enemies = (Map<UUID, Mob>) field(pve, "enemies").get(pve);
        check(enemies.isEmpty() && waves.wave() == 0 && run.waitingForChoice(), "Paper RG waits indefinitely for an actual route selection");
        NamespacedKey foreign = new NamespacedKey("probe", "foreign_armor");
        player.getAttribute(Attribute.ARMOR).addModifier(new org.bukkit.attribute.AttributeModifier(foreign, 1.25D, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.ANY));
        int kills = 0;
        for (int floor = 1; floor <= 10; floor++) {
            executor.onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"rg"});
            var view = player.getOpenInventory();
            var shift = new org.bukkit.event.inventory.InventoryClickEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                10, org.bukkit.event.inventory.ClickType.SHIFT_LEFT, org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY);
            Bukkit.getPluginManager().callEvent(shift);
            check(shift.isCancelled() && run.waitingForChoice(), "real Paper route listener blocks shift-click item extraction");
            var choice = run.offers().stream().filter(route -> run.level(route.relic()) == 0).findFirst().orElse(run.offers().getFirst());
            var click = new org.bukkit.event.inventory.InventoryClickEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                7 + choice.slot() * 3, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
            Bukkit.getPluginManager().callEvent(click);
            check(click.isCancelled() && run.totalRelics() == floor && !run.waitingForChoice(), "Paper menu chooses exactly one route and relic");
            call(routes, "choose", player, choice.token(), choice.slot());
            check(run.totalRelics() == floor, "Paper replayed route cannot grant another relic");
            verifyRogueModifiers(player, run, true);
            if (run.level(RogueRun.Relic.MANA_SPRING) > 0) {
                clock = ((clock + 39) / 20) * 20; tickField.setLong(target, clock);
                mana.drainMana(player.getUniqueId(), 40, clock); int before = mana.mana(player.getUniqueId(), clock);
                call(routes, "tick", clock); int after = mana.mana(player.getUniqueId(), clock);
                check(after == before + 2 * run.level(RogueRun.Relic.MANA_SPRING), "Paper mana relic restores real mana");
                call(routes, "tick", clock); check(mana.mana(player.getUniqueId(), clock) == after, "Paper same-tick mana pulse is idempotent");
            }
            var plan = run.plan(); clock = waves.nextWaveTick(); tickField.setLong(target, clock); call(pve, "tick", clock);
            check(waves.phase() == PveWaveState.Phase.FIGHTING && enemies.size() == plan.spawns().size(), "Paper selected route creates all real enemies");
            check(plan.spawns().stream().map(PveWaves.Spawn::kind).sorted().toList().equals(enemies.values().stream()
                .map(mob -> PveWaves.Kind.valueOf(mob.getType().name())).sorted().toList()), "Paper enemy composition matches its selected theme");
            if (floor == 5 || floor == 10) check(!((Map<?, ?>) field(pve, "bossBars").get(pve)).isEmpty(), "Paper RG boss has a live boss bar");
            player.getInventory().clear();
            int tier = run.rewardTier(new PveWaveState.Reward(floor, plan.boss(), floor == 10, plan.rewardTier()));
            int provisions = run.level(RogueRun.Relic.PROVISIONER);
            for (Mob mob : List.copyOf(enemies.values())) { mob.setAI(false); mob.setHealth(0); mob.remove(); kills++; }
            clock++; tickField.setLong(target, clock); call(pve, "tick", clock);
            check(waves.summary(clock).defeats() == kills, "Paper RG summary counts its actual random population");
            List<SpellDefinition> books = new ArrayList<>();
            for (ItemStack stack : player.getInventory().getContents()) { var spell = (SpellDefinition) call(magic, "spell", stack); if (spell != null) books.add(spell); }
            check(books.size() == 1 && books.getFirst().tier() == tier, "Paper scholar modifies the actual reward book");
            int food = Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(stack -> stack.getType() == Material.BREAD).mapToInt(ItemStack::getAmount).sum();
            check(food == (plan.boss() ? 12 : 6) + 4 * provisions, "Paper provisioner increases the real food reward");
            if (floor < 10) check(run.waitingForChoice() && run.floor() == floor + 1, "Paper clear opens only the next floor choices");
        }
        check(run.completed() && run.relics().size() == 8 && run.totalRelics() == 10, "Paper full run exercises all eight relics");
        clock += 400; tickField.setLong(target, clock); call(pve, "tick", clock);
        check(!engine.isRunning() && !run.active() && run.relics().isEmpty(), "Paper victory cleans run-only growth");
        verifyRogueModifiers(player, run, false);
        check(player.getAttribute(Attribute.ARMOR).getModifiers().stream().anyMatch(mod -> mod.getKey().equals(foreign)), "Paper RG cleanup preserves foreign modifiers");
        // Model saved playerdata after an interrupted server process, then exercise the real login path.
        player.getAttribute(Attribute.ARMOR).addModifier(new org.bukkit.attribute.AttributeModifier(new NamespacedKey("teamlifebind", "rg_armor"), 6D,
            org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.ANY));
        call(target, "handlePlayerJoin", player); verifyRogueModifiers(player, run, false);
        check(player.getAttribute(Attribute.ARMOR).getModifiers().stream().anyMatch(mod -> mod.getKey().equals(foreign)), "login removes stale RG growth without removing other plugins' modifiers");
        for (var mod : List.copyOf(player.getAttribute(Attribute.ARMOR).getModifiers())) if (mod.getKey().equals(foreign)) player.getAttribute(Attribute.ARMOR).removeModifier(mod);
        player.teleport(arena.getSpawnLocation()); player.setGameMode(GameMode.SURVIVAL);
        engine.start(List.of(player.getUniqueId()), new GameOptions(1, HealthPreset.ONE_ROW)); activeWorldField.set(target, arena.getName());
        ((Map<Integer, Location>) field(target, "matchTeamSpawns").get(target)).put(1, player.getLocation());
        call(campaign, "startRound"); call(pve, "start", arena, player.getLocation());
        check(!(Boolean) call(routes, "choose", player, originalToken, 1) && run.totalRelics() == 0, "previous-run Paper menu token is rejected");
        call(routes, "chooseCurrent", player, 1);
        RoundCampaignState campaignState = (RoundCampaignState) call(campaign, "state");
        int credits = ((CampaignRules) call(campaign, "rules")).respawnCredits();
        for (int wipe = 1; wipe <= credits + 1; wipe++) {
            clock += 20; tickField.setLong(target, clock); call(campaign, "wipe", 1, false);
            if (wipe <= credits) check(engine.isRunning() && !campaignState.isEliminated(1) && campaignState.remainingRespawnCredits(1) == credits - wipe, "Paper RG spends its team respawn credits even with a separate round variant");
        }
        clock++; tickField.setLong(target, clock); call(campaign, "tick", clock);
        check(!engine.isRunning() && !run.active(), "Paper RG exhaustion ends the actual campaign");
        verifyRogueModifiers(player, run, false);
        check(run.offers().isEmpty(), "Paper failed-run cleanup clears route choices");
        getLogger().info("PLAY_RG_PAPER_PASS floors=10 relicKinds=8 realDeaths=" + kills + " realMenus=true orphanModifierLoginCleanup=true");
    }
    @SuppressWarnings("unchecked") private void rogueCooperation(Player player) throws Exception {
        Player second = create(arena, "RogueSecond"), third = create(arena, "RogueThird"), fourth = create(arena, "RogueFourth");
        player.teleport(arena.getSpawnLocation()); player.setGameMode(GameMode.SURVIVAL);
        engine.start(List.of(player.getUniqueId(), second.getUniqueId(), third.getUniqueId(), fourth.getUniqueId()), new GameOptions(1, HealthPreset.ONE_ROW));
        activeWorldField.set(target, arena.getName());
        ((Map<Integer, Location>) field(target, "matchTeamSpawns").get(target)).put(1, player.getLocation());
        call(campaign, "startRound"); call(magic, "startRound"); call(pve, "start", arena, player.getLocation());
        Object routes = call(pve, "rogue"); RogueRun run = (RogueRun) call(routes, "state");
        RogueBallot ballot = (RogueBallot) call(routes, "ballot"); PveWaveState waves = (PveWaveState) call(pve, "state");
        call(routes, "open", player); var originalView = player.getOpenInventory();
        ItemStack[] before = Arrays.stream(player.getInventory().getContents()).map(stack -> stack == null ? null : stack.clone()).toArray(ItemStack[]::new);
        clickRogueMenu(player, 22, org.bukkit.event.inventory.ClickType.LEFT);
        check(player.getOpenInventory().getTopInventory().getSize() == 45, "Paper route summary navigates to the full panel through a scheduled inventory click");
        for (int slot = 19; slot <= 26; slot++) check(player.getOpenInventory().getTopInventory().getItem(slot) != null, "Paper panel lists every owned and unowned relic");
        clickRogueMenu(player, 19, org.bukkit.event.inventory.ClickType.SHIFT_LEFT);
        clickRogueMenu(player, 19, org.bukkit.event.inventory.ClickType.LEFT);
        check(Arrays.equals(before, player.getInventory().getContents()), "Paper build panel cannot insert its icons into a player inventory");
        clickRogueMenu(player, 40, org.bukkit.event.inventory.ClickType.LEFT);
        check(player.getOpenInventory().getTopInventory().getSize() == 27, "Paper panel back button restores the route ballot");
        check((Boolean) call(routes, "chooseCurrent", player, 3) && ballot.participants() == 4 && run.totalRelics() == 0, "Paper first of four votes grants nothing yet");
        call(routes, "chooseCurrent", player, 2);
        check(ballot.submitted() == 1 && ballot.votesFor(3) == 0, "Paper revote replaces rather than duplicates the vote");
        check(player.getOpenInventory().getTopInventory().getItem(13).getItemMeta().getLore().stream()
            .anyMatch(line -> line.equals(callText(player, "rg.vote.yours"))), "Paper open menu updates its own-vote marker");
        call(routes, "chooseCurrent", second, 1); call(routes, "chooseCurrent", third, 1);
        UUID returningId = third.getUniqueId();
        ServerPlayer oldHandle = ((org.bukkit.craftbukkit.entity.CraftPlayer) third).getHandle();
        nativePlayers.remove(oldHandle); byId.remove(returningId);
        ((org.bukkit.craftbukkit.CraftWorld) third.getWorld()).getHandle().removePlayerImmediately(oldHandle, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        clock = ((clock + 39) / 20) * 20; tickField.setLong(target, clock); call(routes, "tick", clock);
        check(ballot.participants() == 3 && ballot.submitted() == 2 && ballot.voteOf(returningId) == 0 && run.waitingForChoice(), "Paper disconnect drops the departed vote");
        third = create(arena, "RogueThirdReturn", returningId); call(target, "handlePlayerJoin", third);
        clock += 20; tickField.setLong(target, clock); call(routes, "tick", clock);
        check(ballot.participants() == 4 && ballot.voteOf(returningId) == 0 && third.getOpenInventory().getTopInventory().getSize() == 27, "Paper real login reopens voting for a returning original participant");
        call(routes, "chooseCurrent", third, 2);
        Set<Integer> priorTasks = pendingTaskIds();
        call(routes, "chooseCurrent", fourth, 2);
        check(!run.waitingForChoice() && run.selected().slot() == 2 && run.totalRelics() == 1, "Paper majority resolves one party relic");
        check(waves.nextWaveTick() >= clock + 200, "Paper majority keeps the preparation countdown");
        var executor = target.getCommand("tlb").getExecutor();
        executor.onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"rg"});
        runNewMenuTasks(priorTasks);
        check(player.getOpenInventory().getTopInventory().getSize() == 45, "delayed closing of the old vote cannot close a newly opened build panel");
        clock = waves.nextWaveTick(); tickField.setLong(target, clock); call(pve, "tick", clock);
        executor.onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"rg"});
        check(player.getOpenInventory().getTopInventory().getItem(4).getItemMeta().getDisplayName().contains(
            (String) call(target, "text", player, "rg.details.fighting", new Object[]{waves.remaining()})), "Paper combat panel displays the real enemy count");
        clickRogueMenu(player, 40, org.bukkit.event.inventory.ClickType.LEFT);
        check(!(Boolean) call(routes, "isMenu", player.getOpenInventory().getTopInventory()), "Paper combat panel closes through its actual button");
        call(pve, "clear"); call(pve, "start", arena, player.getLocation());
        var oldClick = new org.bukkit.event.inventory.InventoryClickEvent(originalView, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            10, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(oldClick);
        check(oldClick.isCancelled() && run.totalRelics() == 0 && ballot.submitted() == 0, "Paper stale encounter inventory cannot vote into a fresh ballot");
        third.setGameMode(GameMode.SPECTATOR); fourth.setGameMode(GameMode.SPECTATOR);
        check((Boolean) call(routes, "open", third) && third.getOpenInventory().getTopInventory().getSize() == 45, "Paper original spectator can still inspect the build");
        check(!(Boolean) call(routes, "chooseCurrent", third, 1), "Paper spectator cannot vote");
        call(routes, "chooseCurrent", player, 3); long voteAt = clock;
        clock = voteAt + 380; tickField.setLong(target, clock); call(routes, "tick", clock);
        check(run.waitingForChoice() && ballot.secondsRemaining(clock) == 1, "Paper ballot waits until its twenty-second deadline");
        call(routes, "chooseCurrent", player, 3);
        clock = voteAt + 400; tickField.setLong(target, clock); call(routes, "tick", clock);
        check(run.selected().slot() == 3 && run.totalRelics() == 1, "Paper timeout resolves despite abstention and repeated clicks");
        executor.onCommand(player, target.getCommand("tlb"), "tlb", new String[]{"rg"});
        call(target, "finishPve", false);
        check(!run.active() && ballot.participants() == 0 && ballot.submitted() == 0 && !(Boolean) call(routes, "isMenu", player.getOpenInventory().getTopInventory()), "Paper stop clears the ballot and build panel");
        getLogger().info("PLAY_RG_COOP_PAPER_PASS players=4 majority=true revote=true reconnect=true timeout=true livePanel=true readOnly=true deferredMenus=true");
    }
    private String callText(Player player, String key) {
        try { return (String) call(target, "text", player, key, new Object[0]); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private Set<Integer> pendingTaskIds() {
        return Bukkit.getScheduler().getPendingTasks().stream().map(org.bukkit.scheduler.BukkitTask::getTaskId).collect(java.util.stream.Collectors.toSet());
    }
    private void runNewMenuTasks(Set<Integer> previous) {
        // The probe itself runs on the server thread. Dispatch only newly queued menu callbacks,
        // leaving normal world/engine jobs alone; production callbacks also guard against replay.
        for (var task : Bukkit.getScheduler().getPendingTasks()) if (task.getOwner() == target && !previous.contains(task.getTaskId())) ((Runnable) task).run();
    }
    private void clickRogueMenu(Player player, int slot, org.bukkit.event.inventory.ClickType clickType) {
        Set<Integer> previous = pendingTaskIds();
        var click = new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(), org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            slot, clickType, clickType == org.bukkit.event.inventory.ClickType.SHIFT_LEFT ? org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY : org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click); check(click.isCancelled(), "RG inventory interaction is cancelled by the real listener");
        runNewMenuTasks(previous);
    }
    private void verifyRogueModifiers(Player player, RogueRun run, boolean active) {
        var relics = new RogueRun.Relic[]{RogueRun.Relic.BLADE, RogueRun.Relic.ARMOR, RogueRun.Relic.STRIDE, RogueRun.Relic.FURY, RogueRun.Relic.POISE};
        var attributes = new Attribute[]{Attribute.ATTACK_DAMAGE, Attribute.ARMOR, Attribute.MOVEMENT_SPEED, Attribute.ATTACK_SPEED, Attribute.KNOCKBACK_RESISTANCE};
        double[] perRank = {.12D, 2D, .06D, .1D, .1D};
        for (int index = 0; index < relics.length; index++) {
            NamespacedKey key = new NamespacedKey("teamlifebind", "rg_" + relics[index].name().toLowerCase(Locale.ROOT));
            var mod = player.getAttribute(attributes[index]).getModifiers().stream().filter(value -> value.getKey().equals(key)).findFirst().orElse(null);
            double expected = active ? run.level(relics[index]) * perRank[index] : 0D;
            check(expected == 0D ? mod == null : mod != null && Math.abs(mod.getAmount() - expected) < 1E-9, "Paper relic modifier matches the current run or was removed");
        }
    }
    private static long coveringRogueSeed() {
        for (long seed = 0; seed < 1000; seed++) {
            RogueRun sample = new RogueRun(); sample.start(seed, 1, PveDifficulty.NORMAL);
            for (int floor = 1; floor <= 10; floor++) {
                var choice = sample.offers().stream().filter(route -> sample.level(route.relic()) == 0).findFirst().orElse(sample.offers().getFirst());
                sample.choose(choice.token(), choice.slot()); sample.advanceAfterWave(floor);
            }
            if (sample.relics().size() == 8) return seed;
        }
        throw new AssertionError("No fixture seed covers all relics");
    }

    private void verifyRangedDamage(Mob shooter, Player targetPlayer) throws Exception {
        Map<UUID, Double> baseDamage = new HashMap<>();
        org.bukkit.event.Listener observer = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void shot(org.bukkit.event.entity.EntityShootBowEvent event) {
                if (event.getProjectile() instanceof AbstractArrow arrow) baseDamage.put(arrow.getUniqueId(), arrow.getDamage());
            }
        };
        Bukkit.getPluginManager().registerEvents(observer, this);
        Mob natural = null;
        try {
            var handle = (net.minecraft.world.entity.monster.skeleton.AbstractSkeleton) ((org.bukkit.craftbukkit.entity.CraftLivingEntity) shooter).getHandle();
            handle.performRangedAttack(((org.bukkit.craftbukkit.entity.CraftPlayer) targetPlayer).getHandle(), 1F);
            AbstractArrow shot = arena.getEntitiesByClass(AbstractArrow.class).stream().filter(arrow -> baseDamage.containsKey(arrow.getUniqueId())).findFirst().orElseThrow();
            check(shot.getShooter() == shooter && baseDamage.get(shot.getUniqueId()) > 0, "actual Paper skeleton fires a real arrow through the bow event");
            double multiplier = PveWaves.wave(3, 1, PveDifficulty.HARD).spawns().stream()
                .filter(spawn -> spawn.kind() == PveWaves.Kind.SKELETON).findFirst().orElseThrow().damageMultiplier();
            check(Math.abs(shot.getDamage() - baseDamage.get(shot.getUniqueId()) * multiplier) < 1E-9, "real Paper bow event scales ranged damage by captured encounter strength");
            natural = (Mob) arena.spawnEntity(targetPlayer.getLocation().clone().add(4, 0, 0), EntityType.SKELETON);
            natural.setAI(false); natural.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
            var nativeNatural = (net.minecraft.world.entity.monster.skeleton.AbstractSkeleton) ((org.bukkit.craftbukkit.entity.CraftLivingEntity) natural).getHandle();
            nativeNatural.performRangedAttack(((org.bukkit.craftbukkit.entity.CraftPlayer) targetPlayer).getHandle(), 1F);
            AbstractArrow naturalArrow = arena.getEntitiesByClass(AbstractArrow.class).stream()
                .filter(arrow -> baseDamage.containsKey(arrow.getUniqueId()) && arrow != shot).findFirst().orElseThrow();
            check(naturalArrow.getDamage() == baseDamage.get(naturalArrow.getUniqueId()), "natural skeleton arrows retain vanilla damage");
            double before = naturalArrow.getDamage(); call(pve, "bowShot", targetPlayer, naturalArrow);
            check(naturalArrow.getDamage() == before, "player arrows are not affected by PVE difficulty");
        } finally {
            org.bukkit.event.HandlerList.unregisterAll(observer);
            arena.getEntitiesByClass(AbstractArrow.class).stream().filter(arrow -> baseDamage.containsKey(arrow.getUniqueId())).forEach(Entity::remove);
            if (natural != null) natural.remove();
        }
    }

    @SuppressWarnings("unchecked") private void verifyBossBar(Mob boss, Player viewer, Player outsider) throws Exception {
        Map<UUID, org.bukkit.boss.BossBar> bars = (Map<UUID, org.bukkit.boss.BossBar>) field(pve, "bossBars").get(pve);
        check(bars.size() == 1 && !bars.containsKey(outsider.getUniqueId()), "Paper boss bars only include assigned viewers");
        var first = bars.get(viewer.getUniqueId());
        check(first != null && first.getPlayers().contains(viewer) && first.getProgress() == 1D, "real Paper boss bar starts full");
        int sent = bossPackets.getOrDefault(viewer.getUniqueId(), 0);
        check(sent > 0 && bossPackets.getOrDefault(outsider.getUniqueId(), 0) == 0, "Paper boss add packets reach the assigned client only");
        call(pve, "updateBossBars");
        check(bossPackets.get(viewer.getUniqueId()) == sent, "unchanged Paper boss bars do not resend packets");
        boss.setHealth(boss.getAttribute(Attribute.MAX_HEALTH).getValue() / 2D); call(pve, "updateBossBars");
        check(Math.abs(first.getProgress() - .5D) < .001D, "Paper boss health changes update visible progress");
        check(bossPackets.get(viewer.getUniqueId()) > sent, "Paper boss damage sends a real client update");
        boss.setHealth(boss.getAttribute(Attribute.MAX_HEALTH).getValue() / 3D); call(pve, "updateBossBars");
        check(Math.abs(first.getProgress() - 1D / 3D) < .001D, "Paper boss progress supports fractional health");
        sent = bossPackets.get(viewer.getUniqueId()); call(pve, "updateBossBars");
        check(bossPackets.get(viewer.getUniqueId()) == sent, "Paper float rounding does not trigger repeated client updates");
        sent = bossPackets.get(viewer.getUniqueId());
        Location previous = viewer.getLocation(); viewer.teleport(survival.getSpawnLocation()); call(pve, "updateBossBars");
        check(bars.isEmpty() && first.getPlayers().isEmpty(), "Paper dimension exit removes boss viewers");
        check(bossPackets.get(viewer.getUniqueId()) > sent, "Paper dimension exit sends a client removal packet");
        viewer.teleport(previous); call(pve, "updateBossBars");
        check(bars.containsKey(viewer.getUniqueId()), "Paper return restores boss presentation");
    }

    private Player create(World world, String name) throws Exception {
        return create(world, name, UUID.randomUUID());
    }
    private Player create(World world, String name, UUID id) throws Exception {
        var server = ((org.bukkit.craftbukkit.CraftServer) Bukkit.getServer()).getServer();
        var level = ((org.bukkit.craftbukkit.CraftWorld) world).getHandle();
        ServerPlayer handle = new ServerPlayer(server, level, new GameProfile(id, name), ClientInformation.createDefault()) {
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message, boolean overlay) { }
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { }
            @Override public void sendOverlayMessage(net.minecraft.network.chat.Component message) { }
        };
        handle.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), handle, CommonListenerCookie.createInitial(handle.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundBossEventPacket) bossPackets.merge(handle.getUUID(), 1, Integer::sum);
            }
        };
        handle.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        Location at = world.getSpawnLocation(); handle.setPos(at.getX(), at.getY(), at.getZ());
        nativePlayers.add(handle); byId.put(handle.getUUID(), handle); level.addNewPlayer(handle); players.add(handle);
        Player result = handle.getBukkitEntity(); result.addAttachment(this, "teamlifebind.admin", true);
        result.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000); result.setHealth(1000); result.setGameMode(GameMode.SURVIVAL);
        return result;
    }
    private Set<UUID> ids(World world) { return new HashSet<>(world.getEntities().stream().map(Entity::getUniqueId).toList()); }
    private void remember(Path path) throws Exception { files.putIfAbsent(path, Files.exists(path) ? Files.readAllBytes(path) : null); }
    private void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static Field field(Object owner, String name) throws Exception { return inherited(owner.getClass(), name); }
    private static Field inherited(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) try { Field value = current.getDeclaredField(name); value.setAccessible(true); return value; } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    private static Throwable unwrap(Throwable value) { while (value instanceof InvocationTargetException wrapper && wrapper.getCause() != null) value = wrapper.getCause(); return value; }
    private static Object call(Object owner, String name, Object... args) throws Exception {
        for (Method method : owner.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            boolean matches = true;
            for (int index = 0; index < args.length; index++) {
                Class<?> type = method.getParameterTypes()[index];
                if (type.isPrimitive()) type = type == int.class ? Integer.class : type == long.class ? Long.class : type == boolean.class ? Boolean.class : type == double.class ? Double.class : type;
                if (args[index] != null && !type.isInstance(args[index])) matches = false;
            }
            if (matches) { method.setAccessible(true); return method.invoke(owner, args); }
        }
        throw new NoSuchMethodException(name);
    }
}
