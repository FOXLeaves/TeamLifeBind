package com.teamlifebind.fabric;

import com.mojang.authlib.GameProfile;
import com.teamlifebind.common.CampaignRules;
import com.teamlifebind.common.MatchMode;
import com.teamlifebind.common.PlayMode;
import com.teamlifebind.common.PveDifficulty;
import net.minecraft.server.level.ServerBossEvent;
import com.teamlifebind.common.PveWaveState;
import com.teamlifebind.common.PveWaves;
import com.teamlifebind.common.RoundCampaignState;
import com.teamlifebind.common.TeamLifeBindEngine;
import com.teamlifebind.fabric.MagicSyncPayload;
import com.teamlifebind.fabric.TeamLifeBindFabric;
import com.teamlifebind.fabric.TeamLifeBindFabricManager;
import io.netty.channel.ChannelFutureListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;

/** Real manager, Brigadier, registry-coded inventory, native mobs and death events in a disposable fixture. */
public final class PlayModesProbe implements ModInitializer {
    private static final String MARKER = "TLB_PLAY_MODES_PROBE";
    private MinecraftServer server;
    private TeamLifeBindFabricManager manager;
    private TeamLifeBindEngine engine;
    private Object campaign, pve;
    private PveWaveState waves;
    private List<ServerPlayer> online;
    private Map<UUID, ServerPlayer> byId;
    private final List<ServerPlayer> probes = new ArrayList<>();
    private final Map<ServerLevel, Map<BlockPos, BlockState>> blocks = new LinkedHashMap<>();
    private final Map<ServerLevel, Map<ChunkPos, Visibility>> visibility = new LinkedHashMap<>();
    private final Map<ServerLevel, Set<UUID>> baselineEntities = new LinkedHashMap<>();
    private final List<Mob> encounterEntities = new ArrayList<>();
    private final Map<UUID, List<String>> messages = new HashMap<>();
    private final Map<UUID, Integer> bossPackets = new HashMap<>();
    private final Map<WorldVfxPayload.Kind, Integer> worldVfx = new java.util.EnumMap<>(WorldVfxPayload.Kind.class);
    private final Map<UUID, Map<WorldVfxPayload.Kind, Integer>> worldVfxByPlayer = new HashMap<>();
    private ServerPlayer player;
    private int waited, assertions, realDeaths, replacements;
    private long previousClock;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(value -> server = value);
        ServerTickEvents.END_SERVER_TICK.register(value -> { if (server != null && ++waited == 20) run(); });
    }

    @SuppressWarnings("unchecked") private void run() {
        boolean passed = false;
        try {
            check(server.getServerDirectory().toAbsolutePath().normalize().toString().contains(".tools"), "isolated server only");
            online = (List<ServerPlayer>) field(PlayerList.class, server.getPlayerList(), "players");
            byId = (Map<UUID, ServerPlayer>) field(PlayerList.class, server.getPlayerList(), "playersByUUID");
            check(online.isEmpty(), "fixture contains no human players");
            manager = (TeamLifeBindFabricManager) field(TeamLifeBindFabric.class, null, "MANAGER");
            engine = (TeamLifeBindEngine) field(manager.getClass(), manager, "engine");
            campaign = field(manager.getClass(), manager, "campaign");
            pve = field(manager.getClass(), manager, "pve");
            waves = (PveWaveState) call(pve, "state");
            previousClock = clock();
            for (ServerLevel level : server.getAllLevels()) {
                Set<UUID> ids = new HashSet<>();
                for (Entity entity : level.getAllEntities()) ids.add(entity.getUUID());
                baselineEntities.put(level, ids);
            }
            check(rules().playMode() == PlayMode.SUR, "missing play-mode configuration defaults to SUR");
            directSurvivalEntry();
            player = createPlayer(lobby(), "ModeSolo");
            captureLobbyMenu();
            difficultyOptions();
            survival();
            survivalDeathBoundaries(false);
            survivalDeathBoundaries(true);
            pvpCompatibility();
            dungeon();
            roguelike();
            rogueCooperation();
            for (WorldVfxPayload.Kind kind : List.of(WorldVfxPayload.Kind.MANA_GROWTH, WorldVfxPayload.Kind.BOSS,
                    WorldVfxPayload.Kind.VICTORY, WorldVfxPayload.Kind.RELIC, WorldVfxPayload.Kind.ENCOUNTER_SPAWN,
                    WorldVfxPayload.Kind.WAVE_CLEAR, WorldVfxPayload.Kind.ROUTE_LOCK, WorldVfxPayload.Kind.BORDER_WARNING))
                check(worldVfx.getOrDefault(kind, 0) > 0, "actual gameplay emits world VFX " + kind);
            utilityVisuals();
            worldVfxContracts();
            System.out.println("WORLD_VFX_GAMEPLAY=" + worldVfx);
            passed = true;
        } catch (Throwable error) {
            System.out.println(MARKER + " FAIL " + error); error.printStackTrace();
        } finally {
            try { cleanup(); }
            catch (Throwable error) { passed = false; System.out.println(MARKER + " FAIL cleanup " + error); error.printStackTrace(); }
        }
        if (passed) System.out.println(MARKER + " PASS assertions=" + assertions + " realDeaths=" + realDeaths
            + " replacements=" + replacements + " originalManager=true realCommands=true realMobs=true worldAndConfigRestoredByDriver=true");
    }

    private void directSurvivalEntry() throws Exception {
        ServerPlayer first = createPlayer(server.overworld(), "DirectSurvivor");
        first.getInventory().setItem(7, new ItemStack(Items.DIAMOND, 3));
        first.getFoodData().setFoodLevel(12);
        double x = first.getX(), y = first.getY(), z = first.getZ();
        manager.onPlayerJoin(first);
        for (int tick = 0; tick < 8; tick++) manager.onServerTick(server);
        check(engine.isRunning() && engine.teamForPlayer(first.getUUID()) == 1, "first login starts default cooperative survival without a command");
        check(first.level() == server.overworld() && first.getX() == x && first.getY() == y && first.getZ() == z,
            "direct survival stays at the vanilla spawn/playerdata location");
        check(first.getInventory().getItem(7).is(Items.DIAMOND) && first.getInventory().getItem(7).getCount() == 3,
            "direct survival keeps the existing inventory");
        check(first.getFoodData().getFoodLevel() == 12, "direct survival keeps the existing hunger");
        manager.stop(server);
        check(!engine.isRunning() && first.level() == server.overworld(), "explicit stop leaves the player in survival");
        manager.onPlayerJoin(first);
        for (int tick = 0; tick < 8; tick++) manager.onServerTick(server);
        check(!engine.isRunning() && first.level() == server.overworld(), "explicit stop remains paused on rejoin");
        manager.onPlayerLeave(first.getUUID(), server);
        removePlayer(first);
        System.out.println("DIRECT_SUR_PASS firstJoin=true noLobby=true livePlayerdata=true explicitStop=true");
    }

    private void difficultyOptions() throws Exception {
        check(rules().pveDifficulty() == PveDifficulty.NORMAL, "missing difficulty defaults to original standard tuning");
        command("tlb pve HARD");
        check(rules().pveDifficulty() == PveDifficulty.HARD, "real PVE option command accepts mixed case");
        java.util.Properties saved = new java.util.Properties();
        call(campaign, "save", new Class<?>[] {java.util.Properties.class}, saved);
        check("HARD".equals(saved.getProperty("pve-difficulty")), "difficulty is included in persistent native settings");
        TeamCampaignController reloaded = new TeamCampaignController(manager); reloaded.load(saved);
        check(reloaded.rules().pveDifficulty() == PveDifficulty.HARD, "new campaign controller reloads saved difficulty");
        command("tlb mode pvp"); command("tlb mode LIMITED_LIVES"); command("tlb border off");
        check(rules().pveDifficulty() == PveDifficulty.HARD, "unrelated menu options and legacy variants preserve difficulty");
        command("tlb pve invalid");
        check(rules().pveDifficulty() == PveDifficulty.HARD, "invalid difficulty cannot silently reset the configured option");
        manager.openCampaignMenu(player);
        check(player.containerMenu.getSlot(6).getItem().isEmpty(), "PVP hides the unrelated wave difficulty");
        player.containerMenu.clicked(4,0,net.minecraft.world.inventory.ContainerInput.PICKUP,player);
        check(java.util.stream.IntStream.of(10,12,14,16).allMatch(slot->!player.containerMenu.getSlot(slot).getItem().isEmpty()),"real campaign picker offers four modes");
        player.containerMenu.clicked(12,0,net.minecraft.world.inventory.ContainerInput.PICKUP,player);
        check(rules().playMode()==PlayMode.PVE&&player.containerMenu.getSlot(6).getItem().is(Items.DIAMOND_SWORD), "real PVE campaign menu exposes its difficulty");
        player.containerMenu.clicked(6, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        check(rules().pveDifficulty() == PveDifficulty.EASY, "actual menu click cycles difficulty and persists it");
        player.closeContainer();
        command("tlb mode DRAGON_HUNT"); command("tlb mode sur"); command("tlb pve normal");
    }

    private void survival() throws Exception {
        check(command("tlb mode SuR") == 1 && rules().playMode() == PlayMode.SUR, "real mode command accepts case-insensitive SUR");
        String started = manager.start(server);
        check(engine.isRunning(), "solo SUR starts through original manager: " + started);
        check(engine.configuredTeamCount() == 1 && engine.teamForPlayer(player.getUUID()) == 1, "solo uses one cooperative team");
        check(player.level() == server.overworld(), "SUR uses the persistent original overworld");
        check(field(manager.getClass(), manager, "pendingMatchObservations") instanceof Map<?, ?> observations && observations.isEmpty(),
            "SUR does not apply a temporary match observation lock");
        int liveProfiles = manager.magic().state().playerCount();
        for (int index = 0; index < 130; index++) {
            UUID visitor = UUID.randomUUID();
            check(manager.magic().state().learn(visitor, "fire_bolt").accepted(), "long SUR run admits transient profile " + index);
            manager.magic().disconnect(visitor);
            check(manager.magic().state().playerCount() == liveProfiles, "offline SUR profile releases live state capacity " + index);
        }
        check(manager.magic().state().playerCount() == liveProfiles, "130 transient SUR visitors do not exhaust live caster capacity");
        command("tlb mode pvp");
        check(rules().playMode() == PlayMode.SUR, "mode cannot change during a running survival session");

        ItemStack diamond = new ItemStack(Items.DIAMOND, 13);
        diamond.set(DataComponents.CUSTOM_NAME, Component.literal("Survival component sentinel"));
        CustomData.update(DataComponents.CUSTOM_DATA, diamond, tag -> tag.putString("tlb_mode_probe", "persistent"));
        ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE); armor.setDamageValue(17);
        ItemStack shield = new ItemStack(Items.SHIELD); shield.set(DataComponents.CUSTOM_NAME, Component.literal("Persistent shield"));
        player.getInventory().setItem(7, diamond.copy());
        player.getInventory().setSelectedSlot(7);
        player.setItemSlot(EquipmentSlot.CHEST, armor.copy());
        player.setItemSlot(EquipmentSlot.OFFHAND, shield.copy());
        player.experienceLevel = 20; player.totalExperience = 1234; player.experienceProgress = 0.375F;
        player.snapTo(player.getX() + 2.25D, player.getY() + 1D, player.getZ() + 3.25D, -65F, 15F);
        double x = player.getX(), y = player.getY(), z = player.getZ();
        BlockPos built = player.blockPosition().offset(2, 2, 0);
        change(server.overworld(), built, Blocks.OBSIDIAN.defaultBlockState());
        check(manager.magic().state().learn(player.getUUID(), "fire_bolt").accepted(), "SUR learns actual magic knowledge");
        for (int index = 0; index < 2; index++) {
            jumpTo(clock() + 10L);
            ItemStack apple = manager.magic().createCompanion(TeamMagicItem.Kind.MANA_APPLE, player);
            check(manager.magic().useItem(player, apple, TeamMagicItem.Kind.MANA_APPLE) && apple.isEmpty(), "SUR consumes a real mana apple");
        }
        check(manager.magic().state().maxMana(player.getUUID()) == 120, "SUR apple cap is 120 within this session");
        UUID returningId = player.getUUID();
        manager.onPlayerLeave(returningId, server);
        removePlayer(player);
        player = createPlayer(lobby(), "ModeSoloReturn", returningId);
        manager.onPlayerJoin(player);
        call(manager, "syncJoinedPlayer", new Class<?>[] {ServerPlayer.class, MinecraftServer.class}, player, server);
        check(manager.magic().state().maxMana(returningId) == 120, "same SUR session reconnect retains 120 apple cap");
        check(manager.magic().state().learned(returningId).contains("fire_bolt"), "same SUR reconnect retains learned magic");
        check(ItemStack.matches(diamond, player.getInventory().getItem(7)), "same SUR reconnect restores registered inventory snapshot");
        long seed = (Long) field(manager.getClass(), manager, "battleSeed");
        manager.stop(server);
        check(!engine.isRunning() && player.level() == server.overworld(), "SUR stop remains in survival");
        check(manager.magic().state().maxMana(player.getUUID()) == 100, "SUR stop clears apple growth to 100");
        var profile = server.getWorldPath(LevelResource.ROOT).resolve("data/teamlifebind-survival").resolve(player.getUUID() + ".dat");
        check(Files.isRegularFile(profile), "SUR stop writes a real per-world player snapshot");
        check(ItemStack.matches(diamond, player.getInventory().getItem(7)), "stopping SUR keeps the live survival inventory");
        manager.start(server);
        check(engine.isRunning() && player.level() == server.overworld(), "second SUR session starts in persistent overworld");
        check((Long) field(manager.getClass(), manager, "battleSeed") == seed, "SUR does not rotate the competition seed");
        check(ItemStack.matches(diamond, player.getInventory().getItem(7)), "main item count/name/custom components survive stop/start");
        check(ItemStack.matches(armor, player.getItemBySlot(EquipmentSlot.CHEST)), "armor durability survives");
        check(ItemStack.matches(shield, player.getItemBySlot(EquipmentSlot.OFFHAND)), "offhand components survive");
        check(player.getInventory().getSelectedSlot() == 7, "selected hotbar slot survives");
        check(player.experienceLevel == 20 && player.totalExperience == 1234 && player.experienceProgress == 0.375F, "XP state survives exactly");
        check(Math.abs(player.getX() - x) < .001D && Math.abs(player.getY() - y) < .001D && Math.abs(player.getZ() - z) < .001D,
            "survival position survives");
        check(player.getYRot() == -65F && player.getXRot() == 15F, "survival facing survives");
        check(server.overworld().getBlockState(built).is(Blocks.OBSIDIAN), "player construction is not regenerated at a new SUR session");
        check(manager.magic().state().learned(player.getUUID()).contains("fire_bolt"), "SUR learning survives stop/start");
        check(manager.magic().state().maxMana(player.getUUID()) == 100, "next SUR session cannot inherit apple growth");
        check(Files.isRegularFile(profile), "direct restart retains the checkpoint and keeps live playerdata authoritative");

        RoundCampaignState campaignState = (RoundCampaignState) field(campaign.getClass(), campaign, "state");
        check(campaignState.completeTask(1, RoundCampaignState.StageTask.CLAIM_SUPPLY), "existing SUR team completes a task");
        long cooldownAt = clock();
        check(campaignState.tryActivateSilence(player.getUUID(), cooldownAt), "existing team starts a real shared cooldown");
        ServerPlayer partner = createPlayer(lobby(), "ModePartner");
        manager.onPlayerJoin(partner);
        call(manager, "syncJoinedPlayer", new Class<?>[] {ServerPlayer.class, MinecraftServer.class}, partner, server);
        check(engine.teamForPlayer(partner.getUUID()) == 1 && partner.level() == server.overworld(), "new player joins a running SUR cooperative team");
        check(campaignState.teamForPlayer(partner.getUUID()) == 1, "campaign roster includes the late joiner");
        check(campaignState.completedTasks(1).contains(RoundCampaignState.StageTask.CLAIM_SUPPLY), "late join does not reset tasks");
        check(campaignState.silenceCooldownRemainingTicks(1, cooldownAt) == RoundCampaignState.SILENCE_COOLDOWN_TICKS,
            "late join does not reset cooldowns");
        check(manager.shouldCancelFriendlyFire(player, partner), "SUR manager blocks friendly fire");
        check(manager.magic().state().learned(player.getUUID()).contains("fire_bolt"), "late join does not reset existing knowledge");
        manager.stop(server);
        removePlayer(partner);
        System.out.println("PLAY_SUR_PASS solo=true snapshotComponents=true xp=true position=true blocks=true midJoin=true transientProfiles=130");
    }

    private void pvpCompatibility() throws Exception {
        command("tlb mode pvp");
        command("tlb border on");
        check(rules().playMode() == PlayMode.PVP, "real command selects PVP");
        manager.start(server);
        check(!engine.isRunning(), "PVP retains the two-player/team threshold");
        ServerPlayer rival = createPlayer(lobby(), "ModeRival");
        manager.start(server);
        check(engine.isRunning() && engine.configuredTeamCount() == 2, "two-player PVP starts with two teams");
        check(!engine.teamForPlayer(player.getUUID()).equals(engine.teamForPlayer(rival.getUUID())), "PVP keeps separate teams");
        check(!manager.shouldCancelFriendlyFire(player, rival), "opposing PVP teams may fight");
        ((Map<?,?>)field(manager.getClass(),manager,"pendingMatchObservations")).clear();
        check(manager.utilityParticipant(player)&&manager.utilityParticipant(rival),"border fixture completes initial player observation");
        TeamCampaignController borderController=(TeamCampaignController)campaign;
        long roundStart=(Long)field(borderController.getClass(),borderController,"startTick");
        int warning=visuals(player,WorldVfxPayload.Kind.BORDER_WARNING),rivalWarning=visuals(rival,WorldVfxPayload.Kind.BORDER_WARNING);
        // Co-located opponents must each receive one own warning, not each other's events.
        rival.setPos(player.position());
        jumpTo(roundStart+com.teamlifebind.common.RoundCampaignState.BORDER_WARNING_TICKS-20);borderController.tick(server);
        check(visuals(player,WorldVfxPayload.Kind.BORDER_WARNING)==warning,"border warning does not fire early");
        jumpTo(clock()+20);borderController.tick(server);
        check(visuals(player,WorldVfxPayload.Kind.BORDER_WARNING)==warning+1&&visuals(rival,WorldVfxPayload.Kind.BORDER_WARNING)==rivalWarning+1,
            "border warning is sent once to each participant privately");
        jumpTo(clock()+20);borderController.tick(server);
        check(visuals(player,WorldVfxPayload.Kind.BORDER_WARNING)==warning+1,"repeated ticks cannot repeat the border warning");
        command("tlb mode sur");
        check(rules().playMode() == PlayMode.PVP, "running PVP rejects mode changes");
        manager.stop(server);
        command("tlb border off");
        command("tlb mode LIMITED_LIVES");
        check(rules().playMode() == PlayMode.PVP && rules().matchMode() == MatchMode.LIMITED_LIVES, "legacy sub-mode command preserves PVP");
        command("tlb mode DRAGON_HUNT");
        check(rules().playMode() == PlayMode.PVP && rules().matchMode() == MatchMode.DRAGON_HUNT, "legacy dragon sub-mode remains available");
        removePlayer(rival);
        System.out.println("PLAY_PVP_PASS threshold=true teams=true combat=true legacyMode=true");
    }

    private void survivalDeathBoundaries(boolean naturalDeath) throws Exception {
        manager.start(server);
        check(engine.isRunning() && rules().playMode() == PlayMode.SUR, "offline death regression starts a real SUR round");
        TeamSurvivalProfiles profiles = (TeamSurvivalProfiles) field(manager.getClass(), manager, "survivalProfiles");
        ServerPlayer offline = createPlayer(lobby(), "ModeOfflineVictim");
        manager.onPlayerJoin(offline);
        call(manager, "syncJoinedPlayer", new Class<?>[] {ServerPlayer.class, MinecraftServer.class}, offline, server);
        UUID id = offline.getUUID();
        var directory = server.getWorldPath(LevelResource.ROOT).resolve("data/teamlifebind-survival");
        var snapshot = directory.resolve(id + ".dat");
        var marker = directory.resolve(id + ".dead");
        ItemStack stale = new ItemStack(Items.EMERALD, 23);
        stale.set(DataComponents.CUSTOM_NAME, Component.literal("Offline death stale inventory sentinel"));
        offline.getInventory().setItem(6, stale.copy());
        offline.totalExperience = 4321; offline.experienceLevel = 30; offline.experienceProgress = .25F;
        manager.onPlayerLeave(id, server);
        removePlayer(offline);
        check(Files.isRegularFile(snapshot), "offline player has a genuine survival snapshot before a team wipe");
        if (naturalDeath) {
            ServerLevel world = player.level();
            // Complete the normal client acknowledgement after the previous dimension transitions.
            player.hasChangedDimension();
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            check(player.hurtServer(world, world.damageSources().genericKill(), Float.MAX_VALUE), "native SUR player receives lethal damage");
            check(player.isDeadOrDying(), "native SUR player actually dies");
            manager.onLivingDeath(player, world.damageSources().genericKill());
        } else {
            check(Boolean.TRUE.equals(call(manager, "campaignTeamWipe", new Class<?>[] {int.class, boolean.class}, 1, false)),
                "real SUR team wipe includes an offline teammate");
        }
        check(Files.isRegularFile(marker) && !Files.exists(snapshot), "offline team wipe persists a death marker and invalidates its inventory snapshot");
        manager.stop(server);
        check(!engine.isRunning() && Files.isRegularFile(marker), "offline death marker survives the round boundary");
        // The real player list replaces a dead entity. Stopping SUR no longer resets that entity in a lobby.
        if (player.isDeadOrDying()) {
            UUID survivor = player.getUUID();
            manager.onPlayerLeave(survivor, server);
            removePlayer(player);
            player = createPlayer(server.overworld(), "ModeRespawn", survivor);
            manager.onRespawn(player);
            check(player.level() == server.overworld(), "idle SUR keeps the vanilla respawn dimension");
        }
        manager.start(server);
        check(engine.isRunning(), "SUR restarts while an offline death marker is pending");
        offline = createPlayer(server.overworld(), "ModeOfflineVictimReturn", id);
        // Simulate vanilla playerdata loaded after the old campaign roster was cleared.
        offline.getInventory().setItem(6, stale.copy());
        offline.totalExperience = 4321; offline.experienceLevel = 30; offline.experienceProgress = .25F;
        manager.onPlayerJoin(offline);
        call(manager, "syncJoinedPlayer", new Class<?>[] {ServerPlayer.class, MinecraftServer.class}, offline, server);
        check(!Files.exists(marker), "successful rejoin consumes the persistent offline death marker");
        check(!ItemStack.matches(stale, offline.getInventory().getItem(6)), "next-round rejoin cannot resurrect stale vanilla inventory");
        check(offline.totalExperience == 0 && offline.experienceLevel == 0 && offline.experienceProgress == 0F,
            "next-round rejoin cannot resurrect stale vanilla XP");

        boolean keepInventory = server.overworld().getGameRules().get(GameRules.KEEP_INVENTORY);
        try {
            server.overworld().getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
            offline.getInventory().setItem(6, stale.copy());
            offline.totalExperience = 321; offline.experienceLevel = 9; offline.experienceProgress = .5F;
            check(profiles.markOfflineDeath(server, id), "offline marker can be persisted for keepInventory semantics");
            check(new TeamSurvivalProfiles().applyOfflineDeath(offline), "a fresh helper applies the persisted marker");
            check(ItemStack.matches(stale, offline.getInventory().getItem(6)) && offline.totalExperience == 321,
                "offline death respects vanilla keepInventory when it is enabled");
        } finally { server.overworld().getGameRules().set(GameRules.KEEP_INVENTORY, keepInventory, server); }

        check(profiles.tryCapture(offline) && Files.isRegularFile(snapshot), "online pending-respawn regression starts with a valid snapshot");
        call(manager, "campaignPendingOfflineRespawn", new Class<?>[] {ServerPlayer.class}, offline);
        check(!Files.exists(snapshot) && offline.isSpectator(), "pending offline respawn invalidates the snapshot before spectator transition");
        @SuppressWarnings("unchecked") Map<UUID, ?> pending = (Map<UUID, ?>) field(manager.getClass(), manager, "pendingRespawns");
        pending.remove(id);
        offline.setGameMode(GameType.SURVIVAL);
        check(profiles.tryCapture(offline) && Files.isRegularFile(snapshot), "online lock regression starts with a valid snapshot");
        call(manager, "campaignLockPlayer", new Class<?>[] {ServerPlayer.class}, offline);
        check(!Files.exists(snapshot) && offline.isSpectator(), "permanent respawn lock invalidates the snapshot before spectator transition");
        manager.stop(server);
        removePlayer(offline);
        System.out.println("PLAY_SUR_DEATH_PASS naturalDeath=" + naturalDeath + " offlineWipe=true acrossRounds=true staleInventoryCleared=true staleXpCleared=true keepInventory=true pendingAndLockInvalidated=true");
    }

    private void dungeon() throws Exception {
        command("tlb mode pve"); command("tlb pve hard");
        manager.start(server);
        command("tlb pve easy");
        check(rules().pveDifficulty() == PveDifficulty.HARD && waves.difficulty() == PveDifficulty.HARD, "running PVE captures difficulty and rejects changes");
        check(engine.isRunning() && engine.configuredTeamCount() == 1, "solo PVE starts through original manager");
        ServerLevel arena = player.level();
        check(arena != server.overworld() && arena != lobby(), "PVE uses a separate competition dimension");
        check(!arena.getGameRules().get(GameRules.MOB_GRIEFING), "PVE disables destructive mob griefing");
        @SuppressWarnings("unchecked") Map<UUID, ?> observations = (Map<UUID, ?>) field(manager.getClass(), manager, "pendingMatchObservations");
        observations.clear();
        BlockPos base = player.blockPosition();
        int platformY = arena.getMaxY() - 12;
        for (int dx = -48; dx <= 48; dx++) for (int dz = -48; dz <= 48; dz++) {
            change(arena, new BlockPos(base.getX() + dx, platformY, base.getZ() + dz), Blocks.STONE.defaultBlockState());
        }
        int chunkX = base.getX() >> 4, chunkZ = base.getZ() >> 4;
        ensureVisible(arena, chunkX - 4, chunkX + 4, chunkZ - 4, chunkZ + 4);
        player.snapTo(base.getX() + .5D, platformY + 1D, base.getZ() + .5D);
        call(pve, "start");
        boolean originalMobGriefing = (Boolean) field(pve.getClass(), pve, "savedMobGriefing");
        check(waves.phase() == PveWaveState.Phase.WAITING, "PVE starts with an initial preparation period");
        check(manager.pveHud(player).contains(manager.text(player, PveWaves.nameKey(1))), "waiting HUD previews the next named stage");
        ServerPlayer spectator = createPlayer(lobby(), "ModeSpectator");
        manager.onPlayerJoin(spectator);
        call(manager, "syncJoinedPlayer", new Class<?>[] {ServerPlayer.class, MinecraftServer.class}, spectator, server);
        check(engine.teamForPlayer(spectator.getUUID()) == null && spectator.isSpectator(), "new mid-run PVE player spectates");

        Mob stranger = EntityTypes.ZOMBIE.create(arena, EntitySpawnReason.COMMAND);
        check(stranger != null, "real unrelated zombie is constructible");
        stranger.setPos(player.position().add(0D, 0D, 7D)); stranger.setNoAi(true);
        check(arena.addFreshEntity(stranger), "real unrelated zombie enters world");
        encounterEntities.add(stranger);
        int previous = waves.remaining();
        manager.onLivingDeath(stranger, arena.damageSources().generic());
        check(waves.remaining() == previous, "unregistered natural mob callback cannot advance a wave");

        List<Mob> finalEnemies = List.of();
        for (int wave = 1; wave <= PveWaves.TOTAL_WAVES; wave++) {
            int arrivals=visuals(player,WorldVfxPayload.Kind.ENCOUNTER_SPAWN),clears=visuals(player,WorldVfxPayload.Kind.WAVE_CLEAR);
            jumpTo(waves.nextWaveTick());
            call(pve, "tick", new Class<?>[] {MinecraftServer.class}, server);
            check(waves.wave() == wave && waves.phase() == PveWaveState.Phase.FIGHTING, "real encounter spawns for wave " + wave);
            Map<UUID, Mob> enemies = enemies();
            check(!enemies.isEmpty() && enemies.size() == waves.remaining(), "all wave slots hold real native mobs " + wave);
            long bosses = enemies.values().stream().filter(enemy -> enemy instanceof Ravager).count();
            check(bosses == (wave == 5 || wave == 10 ? 1L : 0L), "boss appears only at 5/10: " + wave);
            check(visuals(player,WorldVfxPayload.Kind.ENCOUNTER_SPAWN)==arrivals+3,"each wave caps ordinary arrival VFX at three: "+wave);
            if (wave == 1) check(enemies.values().stream().allMatch(mob -> Math.abs(mob.getMaxHealth() - 27.0F) < 0.01F), "hard difficulty changes actual first-wave max health to 27");
            if (wave == 5 || wave == 10) verifyBossBar(enemies.values().stream().filter(mob -> mob instanceof Ravager).findFirst().orElseThrow(), spectator);
            if (wave == 3) verifyRangedDamage(enemies.values().stream().filter(mob -> mob.getType() == EntityTypes.SKELETON).findFirst().orElseThrow());
            for (Mob mob : enemies.values()) {
                encounterEntities.add(mob);
                check(arena.getEntity(mob.getUUID()) == mob, "encounter entity is indexed in the real world");
                check(mob.entityTags().contains("tlb_pve_encounter"), "encounter entity has ownership marker");
                if (mob.getType() == EntityTypes.SKELETON || mob.getType() == EntityTypes.STRAY)
                    check(mob.getMainHandItem().is(Items.BOW), "ranged enemies have a bow");
            }
            if (wave == 1) {
                Mob removed = enemies.values().iterator().next();
                int remaining = waves.remaining();
                removed.discard();
                jumpTo(clock() + 10L);
                call(pve, "tick", new Class<?>[] {MinecraftServer.class}, server);
                check(waves.wave() == 1 && waves.remaining() == remaining && waves.phase() == PveWaveState.Phase.FIGHTING,
                    "a removed mob is replaced without kill credit or a free wave clear");
                check(!enemies().containsKey(removed.getUUID()) && enemies().size() == remaining, "missing slot receives a new real mob");
                check(visuals(player,WorldVfxPayload.Kind.ENCOUNTER_SPAWN)==arrivals+3,"replacement mob does not bypass the wave visual cap");
                replacements++;
            }
            List<Mob> active = List.copyOf(enemies().values());
            finalEnemies = active;
            for (Mob enemy : active) {
                check(enemy.hurtServer(arena, arena.damageSources().genericKill(), Float.MAX_VALUE), "real native damage accepts lethal hit");
                check(enemy.isDeadOrDying(), "native enemy actually died");
                manager.onLivingDeath(enemy, arena.damageSources().genericKill());
                realDeaths++;
            }
            check(waves.remaining() == 0, "real death callbacks clear exactly this wave " + wave);
            check(bossBars().isEmpty(), "death of the tracked boss removes its bar immediately");
            check(((Map<?, ?>) field(pve.getClass(), pve, "attackScales")).isEmpty(), "dead enemies retain no projectile strength records");
            check(waves.summary(clock()).defeats() == realDeaths, "team defeat total counts only actual accepted deaths");
            jumpTo(clock() + 10L);
            call(pve, "tick", new Class<?>[] {MinecraftServer.class}, server);
            check(visuals(player,WorldVfxPayload.Kind.WAVE_CLEAR)==clears+(wave<PveWaves.TOTAL_WAVES?1:0),
                "intermission shows one clear effect; final wave keeps its separate victory: "+wave);
            if (wave < PveWaves.TOTAL_WAVES) {
                check(waves.phase() == PveWaveState.Phase.WAITING && waves.wave() == wave, "wave clear enters intermission " + wave);
                check(waves.claimReward() == null, "controller has consumed the wave reward exactly once");
                List<ItemStack> after = inventory(player);
                int xp = player.totalExperience;
                for (Mob enemy : active) manager.onLivingDeath(enemy, arena.damageSources().genericKill());
                jumpTo(clock() + 10L);
                call(pve, "tick", new Class<?>[] {MinecraftServer.class}, server);
                check(matches(after, inventory(player)) && xp == player.totalExperience, "duplicate deaths/ticks cannot repeat rewards " + wave);
                check(visuals(player,WorldVfxPayload.Kind.WAVE_CLEAR)==clears+1,"duplicate reward processing emits no extra clear effect");
            }
        }
        check(waves.completed() && engine.isRunning() && player.level() == arena,
            "tenth clear preserves the live arena for the final reward presentation");
        check(waves.claimReward() == null, "final reward is consumed exactly once");
        List<ItemStack> finalInventory = inventory(player);
        int finalXp = player.totalExperience;
        PveWaveState.Summary summary = waves.summary(clock());
        check(summary.clearedWaves() == 10 && summary.defeats() == 78 && summary.difficulty() == PveDifficulty.HARD, "completion summary records the captured difficulty and whole run");
        String expectedSummary = manager.text(player, "play.pve.summary", manager.text(player, summary.difficulty().languageKey()), 10, 10, 78, summary.elapsedLabel());
        check(messages.get(player.getUUID()).stream().filter(expectedSummary::equals).count() == 1L, "the real completion message is emitted once");
        var previousDifficulty = arena.getDifficulty();
        try {
            command("difficulty peaceful"); jumpTo(clock() + 10L);
            call(pve, "tick", new Class<?>[] {MinecraftServer.class}, server);
            check(engine.isRunning() && waves.completed(), "a completed run cannot turn into a failure when world difficulty changes during extraction");
        } finally { command("difficulty " + previousDifficulty.name().toLowerCase(java.util.Locale.ROOT)); }
        long completedAt = (Long) field(pve.getClass(), pve, "completedAt");
        check(completedAt >= 0L, "final reward starts the extraction clock");
        for (Mob enemy : finalEnemies) manager.onLivingDeath(enemy, arena.damageSources().genericKill());
        jumpTo(completedAt + 390L);
        call(pve, "tick", new Class<?>[] {MinecraftServer.class}, server);
        check(engine.isRunning() && waves.completed(), "reward presentation remains open until the full twenty seconds");
        check(waves.summary(clock()).equals(summary), "completion elapsed time stops before the twenty-second showcase");
        check(messages.get(player.getUUID()).stream().filter(expectedSummary::equals).count() == 1L, "repeated completed ticks do not duplicate the result");
        check(matches(finalInventory, inventory(player)) && finalXp == player.totalExperience,
            "duplicate final deaths and presentation ticks cannot repeat rewards");
        jumpTo(completedAt + 400L);
        call(pve, "tick", new Class<?>[] {MinecraftServer.class}, server);
        check(!engine.isRunning() && player.level() == lobby(), "completed run returns to lobby after twenty seconds");
        check(enemies().isEmpty() && waves.phase() == PveWaveState.Phase.IDLE, "completed encounter is cleared");
        check(arena.getGameRules().get(GameRules.MOB_GRIEFING) == originalMobGriefing, "completion restores mob griefing rule");
        for (Mob enemy : finalEnemies) check(enemy.isRemoved() || enemy.isDeadOrDying(), "final boss group is no longer alive");
        check(stranger.isAlive() && !stranger.isRemoved(), "PVE cleanup preserves unrelated native creatures");
        removePlayer(spectator);
        System.out.println("PLAY_PVE_PASS waves=10 realDeaths=" + realDeaths + " missingReplacements=" + replacements + " rewardOnce=true finalPresentationTicks=400 finalCleanup=true");
    }

    @SuppressWarnings("unchecked") private Map<UUID, ServerBossEvent> bossBars() throws Exception { return (Map<UUID, ServerBossEvent>) field(pve.getClass(), pve, "bossBars"); }
    @SuppressWarnings("unchecked") private void roguelike() throws Exception {
        command("tlb mode rg"); command("tlb pve normal"); manager.start(server);
        check(rules().playMode() == PlayMode.RG && engine.isRunning(), "RG starts through the real mode command and manager");
        ((Map<?, ?>) field(manager.getClass(), manager, "pendingMatchObservations")).clear();
        ServerLevel arena = player.level(); BlockPos base = player.blockPosition(); int height = arena.getMaxY() - 12;
        for (int x = -48; x <= 48; x++) for (int z = -48; z <= 48; z++) change(arena, new BlockPos(base.getX() + x, height, base.getZ() + z), Blocks.STONE.defaultBlockState());
        ensureVisible(arena, (base.getX() >> 4) - 4, (base.getX() >> 4) + 4, (base.getZ() >> 4) - 4, (base.getZ() >> 4) + 4);
        player.snapTo(base.getX() + .5D, height + 1D, base.getZ() + .5D); call(pve, "start");
        TeamRogueController routes = (TeamRogueController) call(pve, "rogue");
        var run = routes.state();
        check(run.waitingForChoice() && player.containerMenu != player.inventoryMenu, "RG opens a real route inventory for the participant");
        long seed = coveringRogueSeed(); run.start(seed, 1, PveDifficulty.NORMAL); command("tlb rg");
        var staleMenu = player.containerMenu;
        ServerPlayer outsider = createPlayer(arena, "RogueOutsider"); outsider.setGameMode(GameType.SPECTATOR);
        check(!manager.rogueCommand(outsider, 1) && run.totalRelics() == 0, "unassigned spectator cannot choose or gain a relic");
        jumpTo(clock() + 1000); call(pve, "tick", new Class<?>[]{MinecraftServer.class}, server);
        check(waves.wave() == 0 && enemies().isEmpty() && run.waitingForChoice(), "encounters wait for a route even after the old countdown elapsed");
        var foreign = Identifier.fromNamespaceAndPath("probe", "unrelated_armor");
        var armor = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        armor.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(foreign, 1.25D, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        int kills = 0;
        for (int floor = 1; floor <= 10; floor++) {
            command("tlb rg"); var menu = player.containerMenu;
            List<ItemStack> before = inventory(player);
            menu.clicked(10, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
            check(run.waitingForChoice() && matches(before, inventory(player)), "route icons cannot be shift-clicked into the inventory");
            var choice = run.offers().stream().filter(route -> run.level(route.relic()) == 0).findFirst().orElse(run.offers().getFirst());
            int locked=visuals(player,WorldVfxPayload.Kind.ROUTE_LOCK);
            var arenaPosition=player.position();var choiceAnchor=manager.campaignCenter();
            player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(choiceAnchor.pos()));outsider.setPos(player.position());
            menu.clicked(7 + choice.slot() * 3, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            check(!run.waitingForChoice() && run.totalRelics() == floor, "real menu awards one shared relic on floor " + floor);
            check(visuals(player,WorldVfxPayload.Kind.ROUTE_LOCK)==locked+1,"accepted route choice emits one star map");
            check(visuals(outsider,WorldVfxPayload.Kind.ROUTE_LOCK)==0,"unassigned spectators receive no route star map");
            menu.clicked(7 + choice.slot() * 3, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            check(run.totalRelics() == floor, "replayed native menu click grants no extra relic");
            check(visuals(player,WorldVfxPayload.Kind.ROUTE_LOCK)==locked+1,"replayed choice emits no extra star map");
            player.setPos(arenaPosition);
            verifyRogueModifiers(run, true);
            if (run.level(com.teamlifebind.common.RogueRun.Relic.MANA_SPRING) > 0) {
                jumpTo(((clock() + 39) / 20) * 20);
                manager.magic().state().drainMana(player.getUUID(), 40, clock());
                int mana = manager.magic().state().mana(player.getUUID(), clock()); routes.tick();
                int restored = manager.magic().state().mana(player.getUUID(), clock());
                check(restored == mana + 2 * run.level(com.teamlifebind.common.RogueRun.Relic.MANA_SPRING), "mana relic restores its actual extra mana");
                routes.tick(); check(manager.magic().state().mana(player.getUUID(), clock()) == restored, "same-tick relic update cannot farm mana");
            }
            var plan = run.plan(); jumpTo(waves.nextWaveTick()); call(pve, "tick", new Class<?>[]{MinecraftServer.class}, server);
            check(waves.phase() == PveWaveState.Phase.FIGHTING && enemies().size() == plan.spawns().size(), "chosen random route spawns its complete real encounter");
            check(plan.spawns().stream().map(PveWaves.Spawn::kind).sorted().toList().equals(enemies().values().stream()
                .map(mob -> PveWaves.Kind.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().toUpperCase(java.util.Locale.ROOT))).sorted().toList()), "actual enemies match the selected theme and threat count");
            if (floor == 5 || floor == 10) check(bossBars().size() == 1, "RG boss uses the live boss bar");
            player.getInventory().clearContent();
            int tier = run.rewardTier(new PveWaveState.Reward(floor, plan.boss(), floor == 10, plan.rewardTier()));
            int provisions = run.level(com.teamlifebind.common.RogueRun.Relic.PROVISIONER);
            for (Mob enemy : List.copyOf(enemies().values())) { enemy.hurtServer(arena, arena.damageSources().genericKill(), Float.MAX_VALUE); manager.onLivingDeath(enemy, arena.damageSources().genericKill()); kills++; }
            jumpTo(clock() + 10); call(pve, "tick", new Class<?>[]{MinecraftServer.class}, server);
            check(waves.summary(clock()).defeats() == kills, "RG result counts the actual random enemy population");
            var books = inventory(player).stream().map(TeamMagicController::spell).filter(java.util.Objects::nonNull).toList();
            check(books.size() == 1 && books.getFirst().tier() == tier, "scholar modifies the real awarded book tier");
            int food = inventory(player).stream().filter(stack -> stack.is(Items.COOKED_BEEF)).mapToInt(ItemStack::getCount).sum();
            check(food == (plan.boss() ? 12 : 4) + 4 * provisions, "provisioner adds the expected real reward food");
            if (floor < 10) check(run.waitingForChoice() && run.floor() == floor + 1, "clear opens the next route exactly once");
        }
        check(run.relics().size() == 8 && run.totalRelics() == 10 && run.completed(), "full RG run exercises all eight relics");
        long completedAt = (Long) field(pve.getClass(), pve, "completedAt"); jumpTo(completedAt + 400);
        call(pve, "tick", new Class<?>[]{MinecraftServer.class}, server);
        check(!engine.isRunning() && !run.active() && run.relics().isEmpty(), "RG completion returns to lobby and clears run growth");
        verifyRogueModifiers(run, false);
        check(armor.getModifier(foreign) != null, "RG cleanup preserves unrelated attribute modifiers"); armor.removeModifier(foreign);
        removePlayer(outsider);
        manager.start(server); ((Map<?, ?>) field(manager.getClass(), manager, "pendingMatchObservations")).clear();
        staleMenu.clicked(10, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        check(run.totalRelics() == 0, "previous-run menu cannot award a relic in a new run");
        routes.chooseCurrent(player, 1);
        var campaignState = (RoundCampaignState) field(campaign.getClass(), campaign, "state");
        int credits = rules().respawnCredits();
        String failure = manager.text(player, "play.pve.failed");
        long failuresBefore = messages.get(player.getUUID()).stream().filter(failure::equals).count();
        for (int wipe = 1; wipe <= credits + 1; wipe++) {
            jumpTo(clock() + 20); call(campaign, "wipe", new Class<?>[]{int.class, boolean.class}, 1, false);
            if (wipe <= credits) check(engine.isRunning() && !campaignState.isEliminated(1) && campaignState.remainingRespawnCredits(1) == credits - wipe, "RG ordinary wipes spend the captured team respawn budget");
        }
        jumpTo(clock() + 10); call(campaign, "tick", new Class<?>[]{MinecraftServer.class}, server);
        check(!engine.isRunning() && !run.active(), "exhausted RG team finishes through the real campaign failure path");
        check(messages.get(player.getUUID()).stream().filter(failure::equals).count() == failuresBefore + 1, "RG exhaustion presents failure exactly once instead of a competitive draw");
        verifyRogueModifiers(run, false);
        check(run.offers().isEmpty() && player.containerMenu == player.inventoryMenu, "failed run closes route UI and clears pending choices");
        System.out.println("PLAY_RG_PASS floors=10 relicKinds=8 realDeaths=" + kills + " realMenus=true transientAttributes=true rewardTiers=true");
    }
    @SuppressWarnings("unchecked") private void rogueCooperation() throws Exception {
        ServerPlayer second = createPlayer(lobby(), "RogueSecond");
        ServerPlayer third = createPlayer(lobby(), "RogueThird");
        ServerPlayer fourth = createPlayer(lobby(), "RogueFourth");
        manager.start(server);
        ((Map<?, ?>) field(manager.getClass(), manager, "pendingMatchObservations")).clear();
        // This start chooses a new random arena. Prepare it just as the solo fixtures do;
        // otherwise an ocean seed can abort spawning before the UI assertion is reached.
        TeamCampaignController.Anchor cooperativeCenter=manager.campaignCenter();
        ServerLevel cooperativeWorld=server.getLevel(cooperativeCenter.dimension());
        BlockPos base=cooperativeCenter.pos();int floor=cooperativeWorld.getMaxY()-12;
        for(int x=-48;x<=48;x++)for(int z=-48;z<=48;z++)
            change(cooperativeWorld,new BlockPos(base.getX()+x,floor,base.getZ()+z),Blocks.STONE.defaultBlockState());
        ensureVisible(cooperativeWorld,(base.getX()>>4)-4,(base.getX()>>4)+4,(base.getZ()>>4)-4,(base.getZ()>>4)+4);
        for(ServerPlayer member:List.of(player,second,third,fourth))member.setPos(base.getX()+.5,floor+1,base.getZ()+.5);
        TeamRogueController routes = (TeamRogueController) call(pve, "rogue");
        var run = routes.state(); var ballot = routes.ballot();
        routes.open(player);
        var originalMenu = player.containerMenu;
        List<ItemStack> inventoryBefore = inventory(player);
        originalMenu.clicked(22, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        check(player.containerMenu.getSlot(4).getItem().is(Items.COMPASS), "route summary opens the real full run panel");
        for (int slot = 19; slot <= 26; slot++) check(!player.containerMenu.getSlot(slot).getItem().isEmpty(), "run panel exposes every relic including unowned ranks");
        player.containerMenu.clicked(19, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
        player.containerMenu.clicked(19, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        check(matches(inventoryBefore, inventory(player)), "run panel icons never become inventory items");
        player.containerMenu.clicked(40, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        check(player.containerMenu.getSlot(4).getItem().is(Items.CLOCK), "panel back button returns to the current ballot");
        check(routes.chooseCurrent(player, 3) && ballot.participants() == 4 && run.totalRelics() == 0, "first of four votes does not grant a relic or start a wave");
        check(routes.chooseCurrent(player, 2) && ballot.submitted() == 1 && ballot.votesFor(3) == 0, "command vote changes replace the prior vote");
        check(player.containerMenu.getSlot(13).getItem().get(net.minecraft.core.component.DataComponents.LORE).lines().stream()
            .anyMatch(line -> line.getString().equals(manager.text(player, "rg.vote.yours"))), "open native menu refreshes its own-vote marker");
        routes.chooseCurrent(second, 1); routes.chooseCurrent(third, 1);
        UUID returningId = third.getUUID(); ServerLevel arena = player.level();
        removePlayer(third); jumpTo(((clock() + 39) / 20) * 20); routes.tick();
        check(ballot.participants() == 3 && ballot.submitted() == 2 && ballot.voteOf(returningId) == 0 && run.waitingForChoice(), "disconnect removes that player's vote without inventing a winner");
        third = createPlayer(arena, "RogueThirdReturn", returningId); manager.onPlayerJoin(third);
        ((Map<?, ?>) field(manager.getClass(), manager, "pendingMatchObservations")).clear();
        jumpTo(((clock() + 39) / 20) * 20); routes.tick();
        check(ballot.participants() == 4 && ballot.voteOf(returningId) == 0 && third.containerMenu != third.inventoryMenu, "reconnected participant gets the pending menu and a fresh vote");
        routes.chooseCurrent(third, 2); routes.chooseCurrent(fourth, 2);
        check(!run.waitingForChoice() && run.selected().slot() == 2 && run.totalRelics() == 1, "three of four matching votes resolve exactly one shared relic");
        long afterChoice = clock(); check(waves.nextWaveTick() >= afterChoice + 200, "resolved vote preserves the preparation delay");
        command("tlb rg"); var details = player.containerMenu;
        check(details.getSlot(4).getItem().is(Items.COMPASS) && details.getSlot(13).getItem().is(Items.MAP), "RG command shows current route and build after voting");
        jumpTo(waves.nextWaveTick()); call(pve, "tick", new Class<?>[]{MinecraftServer.class}, server);
        command("tlb rg"); details = player.containerMenu;
        String combatTitle = details.getSlot(4).getItem().getHoverName().getString();
        String expectedCombat = manager.text(player, "rg.details.fighting", waves.remaining());
        check(combatTitle.contains(expectedCombat), "combat panel reports the actual remaining encounter population: title=" + combatTitle + ", expected=" + expectedCombat + ", phase=" + waves.phase());
        details.clicked(40, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        check(player.containerMenu == player.inventoryMenu, "combat panel close button returns control to the player");
        // Start a fresh encounter through its production controller for deadline and stale-view checks.
        call(pve, "clear"); call(pve, "start");
        originalMenu.clicked(10, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        check(run.totalRelics() == 0 && ballot.submitted() == 0, "previous encounter inventory cannot cast into the new ballot");
        third.setGameMode(GameType.SPECTATOR); fourth.setGameMode(GameType.SPECTATOR);
        check(routes.open(third) && third.containerMenu.getSlot(4).getItem().is(Items.COMPASS), "original participants may inspect the build while spectating");
        check(!routes.chooseCurrent(third, 1), "spectating participants cannot vote");
        routes.chooseCurrent(player, 3); long voteAt = clock();
        jumpTo(voteAt + 380); routes.tick();
        check(run.waitingForChoice() && ballot.secondsRemaining(clock()) == 1, "one vote waits for the second active participant until the deadline");
        routes.chooseCurrent(player, 3);
        jumpTo(voteAt + 400); routes.tick();
        check(run.selected().slot() == 3 && run.totalRelics() == 1, "20-second timeout resolves an abstention and duplicate clicks cannot extend it");
        command("tlb rg"); var endedPanel = player.containerMenu;
        manager.stop(server);
        check(!run.active() && ballot.participants() == 0 && ballot.submitted() == 0 && player.containerMenu == player.inventoryMenu, "stop clears votes and closes build panels");
        check(!endedPanel.stillValid(player), "ended run panel is invalid after cleanup");
        removePlayer(second); removePlayer(third); removePlayer(fourth);
        System.out.println("PLAY_RG_COOP_PASS players=4 majority=true revote=true reconnect=true timeout=true livePanel=true readOnly=true");
    }
    private void verifyRogueModifiers(com.teamlifebind.common.RogueRun run, boolean active) {
        var relics = new com.teamlifebind.common.RogueRun.Relic[]{com.teamlifebind.common.RogueRun.Relic.BLADE, com.teamlifebind.common.RogueRun.Relic.ARMOR, com.teamlifebind.common.RogueRun.Relic.STRIDE, com.teamlifebind.common.RogueRun.Relic.FURY, com.teamlifebind.common.RogueRun.Relic.POISE};
        var attributes = List.of(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        double[] perRank = {.12D, 2D, .06D, .1D, .1D};
        for (int index = 0; index < relics.length; index++) {
            var modifier = player.getAttribute(attributes.get(index)).getModifier(Identifier.fromNamespaceAndPath("teamlifebind", "rg_" + relics[index].name().toLowerCase(java.util.Locale.ROOT)));
            double expected = active ? run.level(relics[index]) * perRank[index] : 0D;
            check(expected == 0D ? modifier == null : modifier != null && Math.abs(modifier.amount() - expected) < 1E-9, "real relic modifier has expected strength or is absent after cleanup");
        }
    }
    private static long coveringRogueSeed() {
        for (long seed = 0; seed < 1000; seed++) {
            var sample = new com.teamlifebind.common.RogueRun(); sample.start(seed, 1, PveDifficulty.NORMAL);
            for (int floor = 1; floor <= 10; floor++) {
                var choice = sample.offers().stream().filter(route -> sample.level(route.relic()) == 0).findFirst().orElse(sample.offers().getFirst());
                sample.choose(choice.token(), choice.slot()); sample.advanceAfterWave(floor);
            }
            if (sample.relics().size() == 8) return seed;
        }
        throw new AssertionError("No fixture seed covers all relics");
    }
    private void verifyRangedDamage(Mob shooter) throws Exception {
        ServerLevel arena = player.level();
        Set<UUID> prior = new HashSet<>(); arena.getAllEntities().forEach(entity -> prior.add(entity.getUUID()));
        ((net.minecraft.world.entity.monster.skeleton.AbstractSkeleton) shooter).performRangedAttack(player, 1F);
        var arrows = iterable(arena.getAllEntities()).stream().filter(entity -> !prior.contains(entity.getUUID()))
            .filter(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class::isInstance)
            .map(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class::cast).toList();
        check(arrows.size() == 1 && arrows.getFirst().getOwner() == shooter, "actual ranged attack creates an owned native arrow");
        var arrow = arrows.getFirst();
        try {
            double expected = PveWaves.wave(3, 1, PveDifficulty.HARD).spawns().stream()
                .filter(spawn -> spawn.kind() == PveWaves.Kind.SKELETON).findFirst().orElseThrow().damageMultiplier();
            check(Math.abs(TeamLifeBindFabric.pveProjectileDamageScale(shooter) - expected) < 1E-9, "projectile strength uses the captured wave and difficulty");
            arrow.getRandom().setSeed(3751L); arrow.setBaseDamageFromMob(1F);
            double scaled = (Double) field(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, arrow, "baseDamage");
            arrow.setOwner(player); arrow.getRandom().setSeed(3751L); arrow.setBaseDamageFromMob(1F);
            double vanilla = (Double) field(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, arrow, "baseDamage");
            check(vanilla > 0 && Math.abs(scaled - vanilla * expected) < 1E-9, "real arrow mixin scales vanilla damage rather than the unrelated melee attribute");
            check(TeamLifeBindFabric.pveProjectileDamageScale(player) == 1D && TeamLifeBindFabric.pveProjectileDamageScale(null) == 1D, "players and ownerless arrows remain unscaled");
            arrow.setOwner(shooter); arrow.getRandom().setSeed(3751L); arrow.setBaseDamageFromMob(1F);
            check(Math.abs((Double) field(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, arrow, "baseDamage") - scaled) < 1E-9, "reinitializing mob damage never compounds its previous multiplier");
        } finally { arrows.forEach(Entity::discard); }
    }
    private void verifyBossBar(Mob boss, ServerPlayer outsider) throws Exception {
        check(bossBars().size() == 1 && !bossBars().containsKey(outsider.getUUID()), "only assigned same-world viewers receive a boss bar");
        ServerBossEvent first = bossBars().get(player.getUUID());
        check(first != null && first.getPlayers().contains(player) && first.getProgress() == 1F, "production boss bar starts at full actual health");
        int sent = bossPackets.getOrDefault(player.getUUID(), 0);
        check(sent > 0 && bossPackets.getOrDefault(outsider.getUUID(), 0) == 0, "boss add packets reach the assigned client only");
        call(pve, "updateBossBars", new Class<?>[] {MinecraftServer.class}, server);
        check(bossPackets.get(player.getUUID()) == sent, "unchanged boss bars do not send redundant network updates");
        check(first.getName().getString().contains(manager.text(player, "play.pve.boss." + waves.wave())), "boss bar name is localized");
        boss.setHealth(boss.getMaxHealth() / 2F);
        call(pve, "updateBossBars", new Class<?>[] {MinecraftServer.class}, server);
        check(Math.abs(first.getProgress() - .5F) < .001F, "actual boss damage updates visible progress");
        check(bossPackets.get(player.getUUID()) > sent, "actual boss damage sends a client update packet");
        sent = bossPackets.get(player.getUUID());
        ServerLevel arena = player.level(); var pos = player.position();
        player.teleportTo(lobby(), 64.5, 100, 64.5, Set.of(), 0F, 0F, false);
        call(pve, "updateBossBars", new Class<?>[] {MinecraftServer.class}, server);
        check(first.getPlayers().isEmpty() && bossBars().isEmpty(), "dimension exit detaches existing boss packets");
        check(bossPackets.get(player.getUUID()) > sent, "dimension exit sends a client removal packet");
        player.teleportTo(arena, pos.x, pos.y, pos.z, Set.of(), 0F, 0F, false);
        call(pve, "updateBossBars", new Class<?>[] {MinecraftServer.class}, server);
        check(bossBars().containsKey(player.getUUID()), "returning to the boss dimension restores the current bar");
        ServerBossEvent rejoined = bossBars().get(player.getUUID());
        int index = online.indexOf(player); online.remove(player);
        call(pve, "updateBossBars", new Class<?>[] {MinecraftServer.class}, server);
        check(rejoined.getPlayers().isEmpty() && bossBars().isEmpty(), "disconnected viewers leave no retained boss bar");
        online.add(index, player);
        call(pve, "updateBossBars", new Class<?>[] {MinecraftServer.class}, server);
    }

    @SuppressWarnings("unchecked") private Map<UUID, Mob> enemies() throws Exception { return (Map<UUID, Mob>) field(pve.getClass(), pve, "enemies"); }
    private CampaignRules rules() throws Exception { return (CampaignRules) call(campaign, "rules"); }
    private ServerLevel lobby() { return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("teamlifebind", "lobby_overworld"))); }
    private long clock() throws Exception { return (Long) field(manager.getClass(), manager, "sharedStateTick"); }
    private void jumpTo(long tick) throws Exception {
        set(manager.getClass(), manager, "sharedStateTick", ((Math.max(clock(), tick) + 9L) / 10L) * 10L);
        // This fixture simulates many manager ticks inside one real world tick.
        ((Map<?,?>)field(WorldVfx.class, null, "BUDGETS")).clear();
    }

    private void worldVfxContracts() throws Exception {
        ((Map<?,?>)field(WorldVfx.class, null, "BUDGETS")).clear();
        for (WorldVfxPayload.Kind kind : WorldVfxPayload.Kind.values()) {
            var point = player.position();
            WorldVfxPayload event = new WorldVfxPayload(17L, player.getUUID(), player.level().dimension().identifier(), kind.ordinal(),
                point.x, point.y, point.z, point.x, point.y + 1, point.z, 1.2F);
            var bytes = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
            try { WorldVfxPayload.CODEC.encode(bytes, event); check(event.equals(WorldVfxPayload.CODEC.decode(bytes)), "world event registry codec roundtrip " + kind); }
            finally { bytes.release(); }
            int before = worldVfx.getOrDefault(kind, 0);
            WorldVfx.player(player, kind, 1.2F);
            check(worldVfx.getOrDefault(kind, 0) > before, "nearby world event transport " + kind);
        }
        int before = worldVfx.values().stream().mapToInt(Integer::intValue).sum();
        WorldVfx.at(player.level(), WorldVfxPayload.Kind.EXPLOSION, new net.minecraft.world.phys.Vec3(Double.NaN, 0, 0), 1);
        check(worldVfx.values().stream().mapToInt(Integer::intValue).sum() == before, "invalid cosmetic coordinates never reach clients or interrupt gameplay");
        Object addon = net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl.getAddon(player.connection);
        @SuppressWarnings("unchecked") Set<Identifier> channels = (Set<Identifier>) field(net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon.class, addon, "sendableChannels");
        channels.remove(WorldVfxPayload.TYPE.id());
        Identifier legacy=Identifier.fromNamespaceAndPath("teamlifebind","world_vfx");
        channels.add(legacy);
        check(!WorldVfxPayload.TYPE.id().equals(legacy),"expanded VFX kinds advertise a separate capability from legacy clients");
        WorldVfx.player(player, WorldVfxPayload.Kind.MANA, 1);
        check(worldVfx.values().stream().mapToInt(Integer::intValue).sum() == before, "clients without the optional VFX channel receive no unsupported payload");
        WorldVfx.player(player, WorldVfxPayload.Kind.TOTEM_RESCUE, 1);
        check(worldVfx.values().stream().mapToInt(Integer::intValue).sum() == before, "legacy-only clients never decode unknown VFX kind IDs");
        channels.remove(legacy);
        Identifier v2=Identifier.fromNamespaceAndPath("teamlifebind","world_vfx_v2");channels.add(v2);
        check(!WorldVfxPayload.TYPE.id().equals(v2),"progression effects use a distinct v3 capability");
        WorldVfx.player(player,WorldVfxPayload.Kind.SPELL_LEARN,1);
        check(worldVfx.values().stream().mapToInt(Integer::intValue).sum()==before,"v2-only clients receive no new kind IDs");
        channels.remove(v2);
        channels.add(WorldVfxPayload.TYPE.id());
    }

    private void utilityVisuals() throws Exception {
        if (engine.isRunning()) manager.stop(server);
        ServerPlayer utilityPlayer = createPlayer(lobby(), "VfxUtility");
        command("tlb mode sur"); ((TeamCampaignController)campaign).change(player, "objectives", "on");
        manager.setHealthSyncEnabled(false); manager.start(server);
        check(engine.isRunning(), "world VFX utility fixture starts real SUR");
        TeamUtilityController utilities = (TeamUtilityController) field(manager.getClass(), manager, "utilities");
        utilityPlayer.setHealth(utilityPlayer.getMaxHealth() * 0.5F);
        utilityPlayer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, utilities.item(utilityPlayer, false));
        int healing = worldVfx.getOrDefault(WorldVfxPayload.Kind.HEAL, 0);
        jumpTo(clock() + 10); utilities.use(utilityPlayer, net.minecraft.world.InteractionHand.MAIN_HAND);
        check(utilities.hasChannel(utilityPlayer), "actual bandage starts a channel: participant=" + manager.utilityParticipant(utilityPlayer)
            + ", team=" + manager.utilityTeam(utilityPlayer) + ", health=" + utilityPlayer.getHealth() + "/" + utilityPlayer.getMaxHealth()
            + ", canHeal=" + manager.utilityCanHeal(utilityPlayer) + ", item=" + utilityPlayer.getMainHandItem());
        jumpTo(clock() + com.teamlifebind.common.TeamUtilityState.BANDAGE_CHANNEL_TICKS + 10);
        utilities.tick(server);
        check(worldVfx.getOrDefault(WorldVfxPayload.Kind.HEAL, 0) > healing, "successful real bandage emits healing VFX");
        utilityPlayer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos drop = utilityPlayer.blockPosition().offset(1, 0, 0);
        change(utilityPlayer.level(), drop, Blocks.BARREL.defaultBlockState());
        int beacons = worldVfx.getOrDefault(WorldVfxPayload.Kind.BEACON, 0);
        utilities.registerSupply(utilityPlayer.level(), drop, 1);
        check(worldVfx.getOrDefault(WorldVfxPayload.Kind.BEACON, 0) > beacons, "registered air drop emits its beacon");
        int opened = worldVfx.getOrDefault(WorldVfxPayload.Kind.SUPPLY_OPEN, 0);
        utilities.useSupply(utilityPlayer, drop);
        check(utilities.hasChannel(utilityPlayer), "actual supply claim starts a channel");
        jumpTo(clock() + com.teamlifebind.common.TeamUtilityState.SUPPLY_CLAIM_TICKS + 10); utilities.tick(server);
        check(!utilities.locked(utilityPlayer.level(), drop) && worldVfx.getOrDefault(WorldVfxPayload.Kind.SUPPLY_OPEN, 0) > opened,
            "successful supply claim unlocks loot and emits its opening VFX");
        cooperativeVisuals(utilityPlayer, utilities);
        magicProgressVisuals(utilityPlayer);
        manager.stop(server); removePlayer(utilityPlayer);
    }
    private void magicProgressVisuals(ServerPlayer user) throws Exception {
        TeamMagicController magic=manager.magic();
        var spell=com.teamlifebind.common.SpellCatalogue.byId("fire_bolt");
        ItemStack book=magic.createBook(spell,user);
        int learned=visuals(user,WorldVfxPayload.Kind.SPELL_LEARN);
        user.setShiftKeyDown(false);jumpTo(clock()+10);magic.useItem(user,book,TeamMagicItem.Kind.SPELL_BOOK);
        check(!book.isEmpty()&&visuals(user,WorldVfxPayload.Kind.SPELL_LEARN)==learned,"viewing learning instructions emits no success effect");
        user.setShiftKeyDown(true);jumpTo(clock()+10);
        check(magic.useItem(user,book,TeamMagicItem.Kind.SPELL_BOOK)&&book.isEmpty(),"actual learning consumes the selected spell book");
        check(visuals(user,WorldVfxPayload.Kind.SPELL_LEARN)==learned+1,"successful learning emits the book effect");
        ItemStack duplicate=magic.createBook(spell,user);jumpTo(clock()+10);
        check(!magic.useItem(user,duplicate,TeamMagicItem.Kind.SPELL_BOOK)&&!duplicate.isEmpty()
            &&visuals(user,WorldVfxPayload.Kind.SPELL_LEARN)==learned+1,"duplicate learning neither consumes nor emits");
        user.setShiftKeyDown(false);jumpTo(clock()+10);
        int growth=visuals(user,WorldVfxPayload.Kind.MANA_GROWTH),maximum=magic.state().maxMana(user.getUUID());
        ItemStack apple=magic.createCompanion(TeamMagicItem.Kind.MANA_APPLE,user);
        check(magic.useItem(user,apple,TeamMagicItem.Kind.MANA_APPLE)&&magic.state().maxMana(user.getUUID())==maximum+10,
            "actual apple increases capacity before growth presentation");
        check(visuals(user,WorldVfxPayload.Kind.MANA_GROWTH)==growth+1,"growth effect accompanies a real capacity increase");
        for(int i=0;i<30&&magic.state().maxMana(user.getUUID())<300;i++)magic.state().increaseMaxMana(user.getUUID(),clock());
        jumpTo(clock()+10);ItemStack capped=magic.createCompanion(TeamMagicItem.Kind.MANA_APPLE,user);
        check(!magic.useItem(user,capped,TeamMagicItem.Kind.MANA_APPLE)&&!capped.isEmpty()
            &&visuals(user,WorldVfxPayload.Kind.MANA_GROWTH)==growth+1,"capacity cap emits no false growth effect");
        magic.state().drainMana(user.getUUID(),40,clock());jumpTo(clock()+1);
        int refill=visuals(user,WorldVfxPayload.Kind.MANA);
        check(magic.useItem(user,magic.createCompanion(TeamMagicItem.Kind.MANA_POTION,user),TeamMagicItem.Kind.MANA_POTION),"actual mana potion restores mana");
        check(visuals(user,WorldVfxPayload.Kind.MANA)==refill+1&&visuals(user,WorldVfxPayload.Kind.MANA_GROWTH)==growth+1,
            "a refill retains its own effect without claiming capacity growth");
    }
    private int visuals(ServerPlayer viewer, WorldVfxPayload.Kind kind) {
        return worldVfxByPlayer.getOrDefault(viewer.getUUID(), Map.of()).getOrDefault(kind, 0);
    }
    private void cooperativeVisuals(ServerPlayer user, TeamUtilityController utilities) throws Exception {
        ServerPlayer outsider=createPlayer(user.level(), "VfxOutsider");
        outsider.setPos(user.getX(),user.getY(),user.getZ());
        check(manager.utilityTeam(outsider)==null,"visual observer is outside the active team");
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        user.setXRot(40);user.setItemInHand(hand,utilities.item(user,true));
        int before=visuals(user,WorldVfxPayload.Kind.TEAM_SIGNAL);
        utilities.use(user,hand);
        check(visuals(user,WorldVfxPayload.Kind.TEAM_SIGNAL)>before,"successful whistle emits a team signal");
        check(visuals(outsider,WorldVfxPayload.Kind.TEAM_SIGNAL)==0,"whistle target is not broadcast to outsiders");
        before=visuals(user,WorldVfxPayload.Kind.TEAM_SIGNAL);utilities.use(user,hand);
        check(visuals(user,WorldVfxPayload.Kind.TEAM_SIGNAL)==before,"whistle cooldown emits no duplicate signal");
        TeamCampaignController controller=(TeamCampaignController)campaign;
        user.setItemInHand(hand,controller.item(user,false));jumpTo(clock()+10);
        controller.use(user,hand);
        check(visuals(user,WorldVfxPayload.Kind.SCOUT_PULSE)>0,"successful scout emits radar pulse");
        check(visuals(outsider,WorldVfxPayload.Kind.SCOUT_PULSE)==0,"scout pulse remains team-only");
        before=visuals(user,WorldVfxPayload.Kind.SCOUT_PULSE);user.setItemInHand(hand,controller.item(user,false));
        jumpTo(clock()+10);controller.use(user,hand);
        check(visuals(user,WorldVfxPayload.Kind.SCOUT_PULSE)==before&&!user.getMainHandItem().isEmpty(),"scout cooldown neither consumes nor emits");
        user.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Items.TOTEM_OF_UNDYING));
        before=visuals(user,WorldVfxPayload.Kind.TOTEM_RESCUE);
        check(manager.tryUseTeamTotem(user,user.getHealth()+user.getAbsorptionAmount()+100),"real lethal rescue consumes a team totem");
        check(visuals(user,WorldVfxPayload.Kind.TOTEM_RESCUE)>before,"successful totem rescue emits protective wings");

        // Queue a normal owned-bed interaction, then observe the real post-placement completion.
        ((Map<?,?>)field(manager.getClass(),manager,"teamBeds")).clear();
        ((Map<?,?>)field(manager.getClass(),manager,"placedTeamBedOwners")).clear();
        ItemStack bed=(ItemStack)call(manager,"createOwnedTeamBedItem",new Class<?>[]{int.class},manager.utilityTeam(user));
        user.setItemInHand(hand,bed);
        BlockPos foot=user.blockPosition().offset(3,0,0),support=foot.below();
        change(user.level(),support,Blocks.STONE.defaultBlockState());
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(support),net.minecraft.core.Direction.UP,support,false);
        check(manager.onUseBlock(user,hand,hit)==net.minecraft.world.InteractionResult.PASS,"owned team bed enters normal placement queue");
        BlockState bedState=((net.minecraft.world.item.BlockItem)TeamLifeBindFabric.TEAM_BED_ITEM).getBlock().defaultBlockState();
        var facing=bedState.getValue(net.minecraft.world.level.block.BedBlock.FACING);
        change(user.level(),foot,bedState.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        change(user.level(),foot.relative(facing),bedState.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        call(manager,"processPendingBedPlacements",new Class<?>[]{MinecraftServer.class},server);
        check(visuals(user,WorldVfxPayload.Kind.TEAM_ANCHOR)>0,"confirmed bed registration emits anchor gates");
        check(visuals(outsider,WorldVfxPayload.Kind.TEAM_ANCHOR)==0,"bed location remains team-only");

        TeamCampaignController.Anchor point=(TeamCampaignController.Anchor)field(controller.getClass(),controller,"controlPoint");
        check(point!=null&&rules().controlPointEnabled(),"objective fixture has a real enabled control point");
        user.setPos(point.pos().getX()+.5,point.pos().getY()+.2,point.pos().getZ()+.5);
        jumpTo(((clock()+39)/20)*20);controller.tick(server);
        check(visuals(user,WorldVfxPayload.Kind.CONTROL_POINT)>0,"active objective emits its persistent range ring");
        jumpTo(clock()+com.teamlifebind.common.RoundCampaignState.CONTROL_CAPTURE_TICKS+20);controller.tick(server);
        check(visuals(user,WorldVfxPayload.Kind.CONTROL_CAPTURE)>0,"completed capture emits ascending victory chevrons");
        before=visuals(user,WorldVfxPayload.Kind.CONTROL_CAPTURE);jumpTo(clock()+20);controller.tick(server);
        check(visuals(user,WorldVfxPayload.Kind.CONTROL_CAPTURE)==before,"capture reward VFX does not repeat every tick");
        removePlayer(outsider);
    }
    private int command(String text) throws Exception { return server.getCommands().getDispatcher().execute(text, player.createCommandSourceStack()); }

    /** Capture the actual populated lobby, including its legacy decorative border. */
    @SuppressWarnings("unchecked") private void captureLobbyMenu() throws Exception {
        Map<UUID, String> locales = (Map<UUID, String>) field(manager.getClass(), manager, "playerLanguageCodes");
        String previous = locales.put(player.getUUID(), "zh_cn");
        try {
            manager.openLobbyMenu(player);
            check(player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu, "actual lobby uses a chest protocol menu");
            var menu = (net.minecraft.world.inventory.ChestMenu) player.containerMenu;
            var result = new com.google.gson.JsonObject();
            result.addProperty("kind", "lobby"); result.addProperty("title", "TLB 控制面板"); result.addProperty("rows", menu.getRowCount());
            var entries = new com.google.gson.JsonArray();
            for (int slot = 0; slot < menu.getRowCount() * 9; slot++) {
                var item = menu.getSlot(slot).getItem(); if (item.isEmpty()) continue;
                var entry = new com.google.gson.JsonObject(); entry.addProperty("slot", slot);
                entry.addProperty("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                entry.addProperty("name", item.getHoverName().getString());
                var data = item.get(DataComponents.CUSTOM_DATA);
                entry.addProperty("action", data == null ? "" : data.copyTag().getStringOr("tlb_menu_action", ""));
                var lines = new com.google.gson.JsonArray(); var lore = item.get(DataComponents.LORE);
                if (lore != null) for (Component line : lore.lines()) lines.add(line.getString());
                entry.add("lore", lines); entries.add(entry);
            }
            result.add("entries", entries);
            var output = server.getServerDirectory().toAbsolutePath().normalize().getParent().getParent()
                .resolve("play-modes-regression/lobby-menu.json");
            Files.writeString(output, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
            check(entries.size() > 30, "capture includes real lobby decoration rather than an idealized sparse menu");
            System.out.println("NATIVE_LOBBY_CAPTURE_PASS slots=" + entries.size() + " source=productionServerMenu");
        } finally {
            player.closeContainer();
            if (previous == null) locales.remove(player.getUUID()); else locales.put(player.getUUID(), previous);
        }
    }

    @SuppressWarnings("unchecked") private ServerPlayer createPlayer(ServerLevel level, String name) throws Exception {
        return createPlayer(level, name, UUID.randomUUID());
    }
    @SuppressWarnings("unchecked") private ServerPlayer createPlayer(ServerLevel level, String name, UUID id) throws Exception {
        check(level != null, "native lobby exists");
        ServerPlayer value = new ServerPlayer(server, level, new GameProfile(id, name), ClientInformation.createDefault()) {
            @Override public PermissionSet permissions() { return PermissionSet.ALL_PERMISSIONS; }
            @Override public void sendSystemMessage(Component message) { messages.computeIfAbsent(getUUID(), ignored -> new ArrayList<>()).add(message.getString()); }
            @Override public void sendSystemMessage(Component message, boolean overlay) { if (!overlay) sendSystemMessage(message); }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) { }
            @Override public void send(Packet<?> packet, ChannelFutureListener listener) { }
            @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) { }
        };
        value.connection = new ServerGamePacketListenerImpl(server, connection, value, CommonListenerCookie.createInitial(value.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundBossEventPacket) bossPackets.merge(value.getUUID(), 1, Integer::sum);
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom && custom.payload() instanceof WorldVfxPayload visual) {
                    worldVfx.merge(WorldVfxPayload.Kind.values()[visual.kind()], 1, Integer::sum);
                    worldVfxByPlayer.computeIfAbsent(value.getUUID(),ignored->new java.util.EnumMap<>(WorldVfxPayload.Kind.class))
                        .merge(WorldVfxPayload.Kind.values()[visual.kind()],1,Integer::sum);
                }
            }
        };
        Object addon = net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl.getAddon(value.connection);
        Set<Identifier> channels = (Set<Identifier>) field(net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon.class, addon, "sendableChannels");
        channels.add(MagicSyncPayload.TYPE.id());
        channels.add(WorldVfxPayload.TYPE.id());
        value.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        value.setPos(64.5D, level.getMaxY() - 20D, 64.5D);
        value.setHealth(20F); value.setGameMode(GameType.SURVIVAL);
        online.add(value); byId.put(value.getUUID(), value); probes.add(value);
        level.addNewPlayer(value);
        return value;
    }

    private void removePlayer(ServerPlayer value) {
        online.remove(value); byId.remove(value.getUUID());
        value.level().removePlayerImmediately(value, Entity.RemovalReason.DISCARDED);
    }
    private void change(ServerLevel level, BlockPos at, BlockState state) {
        blocks.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).putIfAbsent(at.immutable(), level.getBlockState(at));
        level.setBlock(at, state, 3);
    }
    @SuppressWarnings("unchecked") private void ensureVisible(ServerLevel level, int minX, int maxX, int minZ, int maxZ) throws Exception {
        PersistentEntitySectionManager<Entity> index = (PersistentEntitySectionManager<Entity>) field(ServerLevel.class, level, "entityManager");
        var statuses = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<Visibility>) field(index.getClass(), index, "chunkVisibility");
        Map<ChunkPos, Visibility> saved = visibility.computeIfAbsent(level, ignored -> new HashMap<>());
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            ChunkPos chunk = new ChunkPos(x, z); level.getChunk(x, z);
            saved.putIfAbsent(chunk, statuses.getOrDefault(chunk.pack(), Visibility.HIDDEN));
            index.updateChunkStatus(chunk, Visibility.TICKING);
        }
    }

    private void cleanup() throws Exception {
        if (manager != null && engine != null && engine.isRunning()) manager.stop(server);
        for (Mob mob : encounterEntities) if (!mob.isRemoved()) mob.discard();
        for (ServerPlayer value : probes) {
            value.closeContainer();
            if (online != null) online.remove(value);
            if (byId != null) byId.remove(value.getUUID());
            value.level().removePlayerImmediately(value, Entity.RemovalReason.DISCARDED);
            Files.deleteIfExists(server.getWorldPath(LevelResource.ROOT).resolve("data/teamlifebind-survival").resolve(value.getUUID() + ".dat"));
            Files.deleteIfExists(server.getWorldPath(LevelResource.ROOT).resolve("data/teamlifebind-survival").resolve(value.getUUID() + ".dead"));
            Files.deleteIfExists(server.getWorldPath(LevelResource.ROOT).resolve("data/teamlifebind-survival").resolve(value.getUUID() + ".dead.tmp"));
            if (manager != null) {
                manager.magic().state().remove(value.getUUID());
                @SuppressWarnings("unchecked") Map<UUID, ?> saved = (Map<UUID, ?>) field(manager.magic().getClass(), manager.magic(), "exploration");
                saved.remove(value.getUUID());
            }
        }
        for (var world : baselineEntities.entrySet()) for (Entity entity : List.copyOf(iterable(world.getKey().getAllEntities())))
            if (!(entity instanceof ServerPlayer) && !world.getValue().contains(entity.getUUID())) entity.discard();
        for (var world : blocks.entrySet()) for (var entry : world.getValue().entrySet()) world.getKey().setBlock(entry.getKey(), entry.getValue(), 3);
        for (var world : visibility.entrySet()) {
            @SuppressWarnings("unchecked") PersistentEntitySectionManager<Entity> index = (PersistentEntitySectionManager<Entity>) field(ServerLevel.class, world.getKey(), "entityManager");
            for (var entry : world.getValue().entrySet()) index.updateChunkStatus(entry.getKey(), entry.getValue());
        }
        if (manager != null) set(manager.getClass(), manager, "sharedStateTick", previousClock);
    }
    private static <T> List<T> iterable(Iterable<T> source) { List<T> values = new ArrayList<>(); source.forEach(values::add); return values; }
    private static List<ItemStack> inventory(ServerPlayer player) { List<ItemStack> values = new ArrayList<>(); for (int i = 0; i < player.getInventory().getContainerSize(); i++) values.add(player.getInventory().getItem(i).copy()); return values; }
    private static boolean matches(List<ItemStack> left, List<ItemStack> right) { if (left.size() != right.size()) return false; for (int i = 0; i < left.size(); i++) if (!ItemStack.matches(left.get(i), right.get(i))) return false; return true; }
    private void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static Object field(Class<?> type, Object target, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
    private static Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception { Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args); }
}
