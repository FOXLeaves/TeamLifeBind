package com.teamlifebind.fabric;

import com.teamlifebind.common.DeathResult;
import com.teamlifebind.common.GameOptions;
import com.teamlifebind.common.HealthPreset;
import com.teamlifebind.common.StartResult;
import com.teamlifebind.common.TeamLifeBindCombatMath;
import com.teamlifebind.common.TeamLifeBindEngine;
import com.teamlifebind.common.TeamLifeBindLanguage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TeamLifeBindFabricManager {

    private static final long TEAM_WIPE_GUARD_MS = 2_000L;
    private static final int RANDOM_SPAWN_RADIUS = 750;
    private static final int MIN_TEAM_DISTANCE = 280;
    private static final int MIN_TEAM_DISTANCE_FLOOR = 64;
    private static final int SPAWN_SEARCH_ATTEMPTS = 256;
    private static final int SAFE_SPAWN_SEARCH_RADIUS = 12;
    private static final int TEAM_SPAWN_SPREAD = 16;

    private static final int READY_COUNTDOWN_SECONDS = 10;
    private static final int RESPAWN_COUNTDOWN_SECONDS = 5;
    private static final int MATCH_OBSERVATION_SECONDS = 10;
    private static final int JOIN_SYNC_DELAY_TICKS = 5;
    private static final int JOIN_SYNC_RETRY_TICKS = 10;
    private static final int IMPORTANT_NOTICE_TICKS = 60;
    private static final int RESPAWN_COUNTDOWN_TICKS = RESPAWN_COUNTDOWN_SECONDS * 20;
    private static final int MATCH_OBSERVATION_TICKS = MATCH_OBSERVATION_SECONDS * 20;
    private static final long RESPAWN_INVULNERABILITY_TICKS = 100L;
    private static final int SIDEBAR_UPDATE_INTERVAL_TICKS = 20;
    private static final float HEALTH_SYNC_EPSILON = 0.01F;
    private static final int TEAM_REGEN_IDLE_TICKS = 60;
    private static final int TEAM_REGEN_INTERVAL_TICKS = 40;
    private static final float TEAM_REGEN_AMOUNT = 1.0F;
    private static final int EFFECT_DURATION_SYNC_TOLERANCE_TICKS = 10;
    private static final int TOTEM_REGENERATION_TICKS = 900;
    private static final int TOTEM_FIRE_RESISTANCE_TICKS = 800;
    private static final int TOTEM_ABSORPTION_TICKS = 100;
    private static final int END_SPAWN_MIN_RADIUS = 72;
    private static final int END_SPAWN_MAX_RADIUS = 128;
    private static final int FORCED_PLATFORM_RADIUS = 1;
    private static final int FORCED_PLATFORM_FOUNDATION_DEPTH = 2;
    private static final int FORCED_PLATFORM_CLEARANCE = 3;
    private static final int LOBBY_PLATFORM_Y = 100;
    private static final int LOBBY_MENU_SLOT = 6;
    private static final int LOBBY_GUIDE_SLOT = 7;
    private static final int LOBBY_RECIPES_SLOT = 8;
    private static final int LOBBY_MENU_SIZE = 27;
    private static final int LOBBY_MENU_PRIMARY_SLOT = 10;
    private static final int LOBBY_MENU_STATUS_SLOT = 13;
    private static final int LOBBY_MENU_HELP_SLOT = 16;
    private static final int LOBBY_MENU_HEALTH_SYNC_SLOT = 14;
    private static final int LOBBY_MENU_TAB_SLOT = 18;
    private static final int LOBBY_MENU_TEAMS_SLOT = 19;
    private static final int LOBBY_MENU_HEALTH_SLOT = 20;
    private static final int LOBBY_MENU_NORESPAWN_SLOT = 21;
    private static final int LOBBY_MENU_ANNOUNCE_TEAMS_SLOT = 22;
    private static final int LOBBY_MENU_SCOREBOARD_SLOT = 23;
    private static final int LOBBY_MENU_START_STOP_SLOT = 24;
    private static final int LOBBY_MENU_RELOAD_SLOT = 25;
    private static final int LOBBY_MENU_ADVANCEMENTS_SLOT = 26;
    private static final int MATCH_CENTER_RADIUS = 6500;
    private static final int MATCH_CENTER_MIN_DISTANCE = 1800;
    private static final String SIDEBAR_OBJECTIVE_NAME = "tlb_sidebar";
    private static final String SIDEBAR_ENTRY_PREFIX = "tlb_line_";
    private static final int SIDEBAR_MAX_LINES = 9;
    private static final String NAME_COLOR_TEAM_PREFIX = "tlb_name_";
    private static final String NAME_COLOR_TEAM_NEUTRAL = NAME_COLOR_TEAM_PREFIX + "neutral";
    private static final ChatFormatting[] TAB_TEAM_COLORS = {
            ChatFormatting.AQUA,
            ChatFormatting.YELLOW,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.GOLD,
            ChatFormatting.BLUE,
            ChatFormatting.WHITE,
            ChatFormatting.DARK_AQUA,
            ChatFormatting.GRAY
    };
    private static final String LOBBY_ITEM_TAG = "tlb_lobby_item";
    private static final String LOBBY_MENU_TAG = "menu";
    private static final String LOBBY_GUIDE_TAG = "guide";
    private static final String LOBBY_RECIPES_TAG = "recipes";
    private static final String LOBBY_MENU_ACTION_TAG = "tlb_menu_action";
    private static final String TEAM_BED_OWNER_TAG = "tlb_team_bed_owner";
    private static final String SUPPLY_BOAT_TAG = "tlb_supply_boat";
    private static final long SUPPLY_BOAT_DROP_CONFIRM_TICKS = 60L;
    private static final String MENU_ACTION_READY = "ready";
    private static final String MENU_ACTION_UNREADY = "unready";
    private static final String MENU_ACTION_STATUS = "status";
    private static final String MENU_ACTION_HELP = "help";
    private static final String MENU_ACTION_TEAMS = "teams";
    private static final String MENU_ACTION_HEALTH = "health";
    private static final String MENU_ACTION_HEALTH_SYNC = "healthsync";
    private static final String MENU_ACTION_NORESPAWN = "norespawn";
    private static final String MENU_ACTION_ANNOUNCE_TEAMS = "announce_teams";
    private static final String MENU_ACTION_SCOREBOARD = "scoreboard";
    private static final String MENU_ACTION_TAB = "tab";
    private static final String MENU_ACTION_ADVANCEMENTS = "advancements";
    private static final String MENU_ACTION_START = "start";
    private static final String MENU_ACTION_STOP = "stop";
    private static final String MENU_ACTION_RELOAD = "reload";
    private static final ResourceKey<Level> LOBBY_OVERWORLD_KEY = ResourceKey.create(Registries.DIMENSION, TeamLifeBindFabric.id("lobby_overworld"));
    private static final ResourceKey<Level> BATTLE_OVERWORLD_KEY = ResourceKey.create(Registries.DIMENSION, TeamLifeBindFabric.id("battle_overworld"));
    private static final ResourceKey<Level> BATTLE_NETHER_KEY = ResourceKey.create(Registries.DIMENSION, TeamLifeBindFabric.id("battle_nether"));
    private static final ResourceKey<Level> BATTLE_END_KEY = ResourceKey.create(Registries.DIMENSION, TeamLifeBindFabric.id("battle_end"));
    private static final BlockPos LOBBY_SPAWN_POS = new BlockPos(0, LOBBY_PLATFORM_Y + 1, 0);
    private static final Set<ResourceKey<Level>> BATTLE_DIMENSIONS = Set.of(BATTLE_OVERWORLD_KEY, BATTLE_NETHER_KEY, BATTLE_END_KEY);

    private final TeamLifeBindEngine engine = new TeamLifeBindEngine();
    private final Map<Integer, SpawnPoint> teamSpawns = new HashMap<>();
    private final Map<Integer, SpawnPoint> teamBeds = new HashMap<>();
    private final Map<SpawnPoint, Integer> placedTeamBedOwners = new HashMap<>();
    private final Map<Integer, Long> teamWipeGuardUntil = new HashMap<>();
    private final Set<UUID> noRespawnLockedPlayers = new HashSet<>();
    private final Set<ResourceKey<Level>> noRespawnDimensions = new HashSet<>(Set.of(Level.END, BATTLE_END_KEY));
    private final Map<UUID, TimedNotice> importantNotices = new HashMap<>();
    private final List<PendingBedPlacement> pendingBedPlacements = new ArrayList<>();
    private final List<PendingBrokenTeamBedDrop> pendingBrokenTeamBedDrops = new ArrayList<>();
    private final Map<UUID, PendingRespawn> pendingRespawns = new HashMap<>();
    private final Map<UUID, PendingMatchObservation> pendingMatchObservations = new HashMap<>();
    private final Map<UUID, Integer> pendingJoinSyncs = new HashMap<>();
    private final Map<UUID, InteractionHand> pendingMilkBucketUses = new HashMap<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Set<UUID> dimensionRedirectGuard = new HashSet<>();
    private final Set<UUID> sidebarInitialized = new HashSet<>();
    private final Map<UUID, Integer> sidebarLineCounts = new HashMap<>();
    private final Map<UUID, List<Component>> sidebarLines = new HashMap<>();
    private final Map<UUID, Set<String>> activeNameColorTeams = new HashMap<>();
    private final Map<UUID, Long> supplyBoatDropConfirmUntil = new HashMap<>();
    private final Map<UUID, Long> respawnInvulnerableUntil = new HashMap<>();
    private final Map<Integer, Float> teamSharedHealth = new HashMap<>();
    private final Map<Integer, Set<UUID>> observedTeamHealthPlayers = new HashMap<>();
    private final Map<Integer, Integer> teamSharedExperience = new HashMap<>();
    private final Map<Integer, Set<UUID>> observedTeamExperiencePlayers = new HashMap<>();
    private final Map<Integer, Integer> teamSharedFoodLevels = new HashMap<>();
    private final Map<Integer, Float> teamSharedSaturation = new HashMap<>();
    private final Map<Integer, Set<UUID>> observedTeamFoodPlayers = new HashMap<>();
    private final Map<Integer, Long> teamLastDamageTicks = new HashMap<>();
    private final Random random = new Random();

    private int teamCount = 2;
    private HealthPreset healthPreset = HealthPreset.ONE_HEART;
    private boolean announceTeamAssignment = true;
    private boolean healthSyncEnabled = true;
    private boolean noRespawnEnabled = true;
    private boolean scoreboardEnabled = true;
    private boolean tabEnabled = true;
    private boolean advancementsEnabled = false;
    private long battleSeed = new Random().nextLong();
    private int sidebarTickBudget = 0;
    private TeamLifeBindLanguage language;
    private MinecraftServer activeServer;

    private int countdownRemainingSeconds = -1;
    private int countdownTickBudget = 0;
    private BlockPos activeMatchCenter;
    private BlockPos previousMatchCenter;
    private long sharedStateTick = 0L;

    public void onServerStarted(MinecraftServer server) {
        this.activeServer = server;
        loadPersistentSettings();
        clearRoundState();
        resetBattleDimensionData(server);
        applyBattleSeed(server);
        reloadLanguage();
        applyAdvancementRule(server);
        ServerLevel lobby = lobbyWorldIfReady(server);
        if (lobby != null) {
            prepareLobbyPlatform(lobby);
            teleportAllToLobby(server);
        }
    }

    @SuppressWarnings("resource")
    public void onPlayerJoin(ServerPlayer player) {
        readyPlayers.remove(player.getUUID());
        importantNotices.remove(player.getUUID());
        supplyBoatDropConfirmUntil.remove(player.getUUID());
        respawnInvulnerableUntil.remove(player.getUUID());
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }
        pendingJoinSyncs.put(player.getUUID(), JOIN_SYNC_DELAY_TICKS);
    }

    public void onPlayerLeave(UUID playerId, MinecraftServer server) {
        readyPlayers.remove(playerId);
        importantNotices.remove(playerId);
        supplyBoatDropConfirmUntil.remove(playerId);
        respawnInvulnerableUntil.remove(playerId);
        sidebarInitialized.remove(playerId);
        sidebarLineCounts.remove(playerId);
        sidebarLines.remove(playerId);
        activeNameColorTeams.remove(playerId);
        pendingJoinSyncs.remove(playerId);
        pendingMilkBucketUses.remove(playerId);
        forgetSharedTeamHealthPlayer(playerId);
        forgetSharedTeamExperiencePlayer(playerId);
        forgetSharedTeamFoodPlayer(playerId);
        evaluateReadyCountdown(server);
    }

    @SuppressWarnings("resource")
    public void onPlayerWorldChange(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to) {
        if (dimensionRedirectGuard.remove(player.getUUID())) {
            return;
        }

        ResourceKey<Level> target = resolvePortalRedirect(from, to);
        if (target == null) {
            return;
        }

        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }
        ServerLevel targetWorld = server.getLevel(target);
        if (targetWorld == null) {
            return;
        }

        BlockPos source = player.blockPosition();
        BlockPos destination = portalTargetPos(from, target, source, targetWorld);
        dimensionRedirectGuard.add(player.getUUID());
        player.teleportTo(targetWorld, destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, Set.of(), player.getYRot(), player.getXRot(), false);
    }

    public void setTeamCount(int teamCount) {
        this.teamCount = Math.max(2, Math.min(32, teamCount));
        savePersistentSettings();
    }

    @SuppressWarnings("unused")
    public int getTeamCount() {
        return teamCount;
    }

    public void setHealthPreset(HealthPreset preset) {
        this.healthPreset = preset;
        savePersistentSettings();
    }

    @SuppressWarnings("unused")
    public HealthPreset getHealthPreset() {
        return healthPreset;
    }

    public String healthSyncStatus() {
        return text("command.healthsync.current", stateLabel(healthSyncEnabled));
    }

    public void setHealthSyncEnabled(boolean enabled) {
        this.healthSyncEnabled = enabled;
        clearSharedHealthTracking();
        savePersistentSettings();
    }

    public String noRespawnStatus() {
        List<String> blocked = noRespawnDimensions.stream().map(key -> key.identifier().toString()).sorted().toList();
        String blockedText = blocked.isEmpty() ? text("scoreboard.value.none") : String.join(", ", blocked);
        return text("norespawn.status", stateLabel(noRespawnEnabled), blockedText);
    }

    public void setNoRespawnEnabled(boolean enabled) {
        this.noRespawnEnabled = enabled;
        if (!enabled) {
            noRespawnLockedPlayers.clear();
        }
        savePersistentSettings();
    }

    @SuppressWarnings("unused")
    public boolean isAnnounceTeamAssignmentEnabled() {
        return announceTeamAssignment;
    }

    public void setAnnounceTeamAssignment(boolean enabled) {
        this.announceTeamAssignment = enabled;
        savePersistentSettings();
    }

    @SuppressWarnings("unused")
    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public void setScoreboardEnabled(boolean enabled) {
        this.scoreboardEnabled = enabled;
        savePersistentSettings();
        refreshScoreboardState(activeServer);
    }

    @SuppressWarnings("unused")
    public boolean isAdvancementsEnabled() {
        return advancementsEnabled;
    }

    @SuppressWarnings("unused")
    public boolean isTabEnabled() {
        return tabEnabled;
    }

    public void setTabEnabled(boolean enabled) {
        this.tabEnabled = enabled;
        savePersistentSettings();
        refreshTabState(activeServer);
    }

    public void setAdvancementsEnabled(boolean enabled) {
        this.advancementsEnabled = enabled;
        savePersistentSettings();
        applyAdvancementRule(activeServer);
    }

    public boolean addNoRespawnDimension(String rawDimension) {
        try {
            Identifier id = Identifier.parse(rawDimension);
            return noRespawnDimensions.add(ResourceKey.create(Registries.DIMENSION, id));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean removeNoRespawnDimension(String rawDimension) {
        try {
            Identifier id = Identifier.parse(rawDimension);
            return noRespawnDimensions.remove(ResourceKey.create(Registries.DIMENSION, id));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public void clearNoRespawnDimensions() {
        noRespawnDimensions.clear();
    }

    public void markReady(ServerPlayer player) {
        if (engine.isRunning()) {
            sendOverlay(player, text("ready.in_match"));
            return;
        }

        if (!readyPlayers.add(player.getUUID())) {
            sendOverlay(player, text("ready.already"));
            return;
        }

        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }

        broadcastOverlay(server, text("ready.marked", player.getName().getString()));
        evaluateReadyCountdown(server);
    }

    public void unready(ServerPlayer player) {
        if (!readyPlayers.remove(player.getUUID())) {
            sendOverlay(player, text("ready.not_marked"));
            return;
        }

        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }

        broadcastOverlay(server, text("ready.unmarked", player.getName().getString()));
        evaluateReadyCountdown(server);
    }

    //noinspection BooleanMethodIsAlwaysInverted
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean shouldCancelFriendlyFire(ServerPlayer attacker, ServerPlayer target) {
        if (attacker == null || target == null) {
            return false;
        }
        if (attacker.getUUID().equals(target.getUUID())) {
            return false;
        }
        if (target.level().dimension().equals(LOBBY_OVERWORLD_KEY)) {
            return !attacker.isSpectator() && !target.isSpectator();
        }
        if (!engine.isRunning()) {
            return false;
        }
        Integer attackerTeam = engine.teamForPlayer(attacker.getUUID());
        Integer targetTeam = engine.teamForPlayer(target.getUUID());
        return attackerTeam != null
                && attackerTeam.equals(targetTeam)
                && !attacker.isSpectator()
                && !target.isSpectator();
    }

    public void notifyFriendlyFireBlocked(ServerPlayer attacker) {
        if (attacker != null) {
            sendOverlay(attacker, text(attacker.level().dimension().equals(LOBBY_OVERWORLD_KEY) ? "lobby.pvp_denied" : "combat.friendly_fire_blocked"));
        }
    }

    //noinspection BooleanMethodIsAlwaysInverted
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean tryUseTeamTotem(ServerPlayer target, float finalDamage) {
        if (finalDamage <= 0.0F || !engine.isRunning() || !isSharedHealthParticipant(target)) {
            return false;
        }

        Integer team = engine.teamForPlayer(target.getUUID());
        if (team == null) {
            return false;
        }
        if ((target.getHealth() + target.getAbsorptionAmount()) - finalDamage > HEALTH_SYNC_EPSILON) {
            return false;
        }

        List<ServerPlayer> players = resolveTotemEligibleTeamPlayers(serverOf(target), team);
        if (players.isEmpty()) {
            return false;
        }

        ServerPlayer totemOwner = null;
        int totemSlot = -1;
        for (ServerPlayer player : players) {
            int candidateSlot = findTotemSlot(player);
            if (candidateSlot >= 0) {
                totemOwner = player;
                totemSlot = candidateSlot;
                break;
            }
        }
        if (totemOwner == null) {
            return false;
        }

        consumeInventoryItem(totemOwner, totemSlot);
        clearTeamSharedHealthTracking(team);
        teamLastDamageTicks.put(team, sharedStateTick);
        for (ServerPlayer player : players) {
            applyTeamTotemRescue(player);
        }
        return true;
    }

    public void trackMilkBucketUse(ServerPlayer player, InteractionHand hand) {
        if (player != null && hand != null) {
            pendingMilkBucketUses.put(player.getUUID(), hand);
        }
    }

    private List<ServerPlayer> resolveTotemEligibleTeamPlayers(MinecraftServer server, int team) {
        if (server == null) {
            return List.of();
        }
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isSharedHealthParticipant(player)) {
                continue;
            }
            Integer playerTeam = engine.teamForPlayer(player.getUUID());
            if (playerTeam != null && playerTeam == team) {
                players.add(player);
            }
        }
        return players;
    }

    private int findTotemSlot(ServerPlayer player) {
        if (player == null) {
            return -1;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                return slot;
            }
        }
        return -1;
    }

    private void consumeInventoryItem(ServerPlayer player, int slot) {
        if (player == null || slot < 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty()) {
            return;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            inventory.setItem(slot, ItemStack.EMPTY);
        }
    }

    private void applyTeamTotemRescue(ServerPlayer player) {
        if (player == null || !player.isAlive()) {
            return;
        }
        player.removeAllEffects();
        player.clearFire();
        player.setHealth(Math.min(player.getMaxHealth(), 1.0F));
        player.setAbsorptionAmount(0.0F);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, TOTEM_REGENERATION_TICKS, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, TOTEM_FIRE_RESISTANCE_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, TOTEM_ABSORPTION_TICKS, 1, false, true, true));
        ServerLevel world = player.level();
        world.broadcastEntityEvent(player, (byte) 35);
        world.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public String start(MinecraftServer server) {
        if (hasActiveMatchSession()) {
            return text("match.already_running");
        }

        ServerLevel matchWorld = resolveBattleOverworld(server);
        if (matchWorld == null) {
            return text("match.missing_dimension", BATTLE_OVERWORLD_KEY.identifier());
        }

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (players.size() < teamCount) {
            return text("match.need_players", teamCount);
        }

        cancelCountdown();
        readyPlayers.clear();
        clearRoundState();
        rotateBattleSeed(server);

        activeMatchCenter = selectMatchCenter(matchWorld);
        previousMatchCenter = activeMatchCenter;

        List<UUID> ids = players.stream().map(Player::getUUID).toList();
        StartResult result = engine.start(ids, new GameOptions(teamCount, healthPreset));

        Map<Integer, SpawnPoint> resolved = resolveTeamSpawns(matchWorld, activeMatchCenter, teamCount);
        teamSpawns.putAll(resolved);

        for (ServerPlayer player : players) {
            int team = result.teamByPlayer().get(player.getUUID());
            SpawnPoint base = teamSpawns.get(team);
            if (base == null) {
                continue;
            }
            SpawnPoint randomized = randomizeAround(base, server);
            preparePlayerForMatch(player);
            teleport(player, randomized);
            applyHealthPreset(player);
            sendOverlay(player, text("match.player_assignment", teamLabel(team), healthPresetLabel(healthPreset)));
            beginMatchObservation(player, randomized);
        }

        broadcastImportantNotice(server, text("match.notice.start.title"));
        if (announceTeamAssignment) {
            announceTeams(result.teamByPlayer(), server);
        }
        return text("match.started.mod", teamCount);
    }

    public String stop(MinecraftServer server) {
        if (!hasActiveMatchSession()) {
            cancelCountdown();
            readyPlayers.clear();
            teleportAllToLobby(server);
            return text("match.no_running");
        }

        stopInternal(server, false, null);
        return text("match.stopped");
    }

    public String status(MinecraftServer server) {
        if (!engine.isRunning()) {
            return text("match.status.idle.mod", readyPlayers.size(), server.getPlayerList().getPlayers().size());
        }
        List<Integer> beds = new ArrayList<>(teamBeds.keySet());
        Collections.sort(beds);
        return text("match.status.running", teamCount, healthPresetLabel(healthPreset), beds, stateLabel(noRespawnEnabled));
    }

    public InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hitResult) {
        ServerLevel world = player.level();
        BlockPos clickedPos = hitResult.getBlockPos();
        BlockState clickedState = world.getBlockState(clickedPos);

        if (clickedState.is(BlockTags.BEDS)) {
            sendPlayerMessage(player, Component.literal(text("bed.personal_spawn_disabled")), true);
            return InteractionResult.FAIL;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BedItem)) {
            return InteractionResult.PASS;
        }

        BlockPos placePos = clickedPos.relative(hitResult.getDirection());
        if (!stack.is(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            explode(world, placePos, player);
            return InteractionResult.FAIL;
        }

        if (!engine.isRunning()) {
            sendPlayerMessage(player, Component.literal(text("bed.place_only_in_match")), true);
            return InteractionResult.FAIL;
        }

        Integer team = engine.teamForPlayer(player.getUUID());
        if (team == null) {
            sendPlayerMessage(player, Component.literal(text("bed.not_on_team")), true);
            return InteractionResult.FAIL;
        }

        Integer ownerTeam = resolveTeamBedOwner(stack);
        if (ownerTeam == null || !ownerTeam.equals(team)) {
            if (!player.isCreative()) {
                stack.shrink(Math.min(2, stack.getCount()));
            }
            explode(world, placePos, player);
            sendPlayerMessage(player, Component.literal(text("bed.wrong_team", ownerTeam != null ? teamLabel(ownerTeam) : text("scoreboard.value.none"))), true);
            return InteractionResult.FAIL;
        }

        if (hasActiveTeamBedOrPendingPlacement(team, serverOf(player))) {
            sendPlayerMessage(player, Component.literal(text("bed.limit_reached")), true);
            return InteractionResult.FAIL;
        }

        if (!player.isCreative() && stack.getCount() > 1) {
            stack.setCount(1);
        }
        pendingBedPlacements.add(new PendingBedPlacement(player.getUUID(), ownerTeam, hand, world.dimension(), placePos, world.getGameTime() + 5));
        return InteractionResult.PASS;
    }

    public void onBlockBreak(ServerPlayer player, ServerLevel world, BlockPos pos, BlockState state) {
        if (!state.is(BlockTags.BEDS)) {
            return;
        }
        SpawnPoint normalized = normalizeBedFoot(world.dimension(), pos, state);
        Integer ownerTeam = placedTeamBedOwners.remove(normalized);
        if (ownerTeam != null) {
            if (!player.isCreative()) {
                pendingBrokenTeamBedDrops.add(new PendingBrokenTeamBedDrop(normalized, ownerTeam, world.getGameTime() + 1));
            }
            SpawnPoint activeBed = teamBeds.get(ownerTeam);
            if (normalized.equals(activeBed)) {
                teamBeds.remove(ownerTeam);
            }
            return;
        }
        removeTeamBedAt(normalized);
    }

    public void onLivingDeath(Entity entity, DamageSource source) {
        if (entity instanceof ServerPlayer deadPlayer) {
            lockNoRespawnIfBlocked(deadPlayer);
            handlePlayerDeath(deadPlayer);
            return;
        }

        if (!(entity instanceof EnderDragon)) {
            return;
        }

        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer killer)) {
            return;
        }

        Integer winner = engine.onObjectiveCompleted(killer.getUUID());
        if (winner == null) {
            return;
        }

        MinecraftServer server = serverOf(killer);
        if (server == null) {
            return;
        }
        stopInternal(server, true, text("match.win_reason.dragon", killer.getName().getString()));
    }

    public void onRespawn(ServerPlayer player) {
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }

        if (!engine.isRunning()) {
            teleportToLobby(player);
            return;
        }

        Integer team = engine.teamForPlayer(player.getUUID());
        if (isRespawnLocked(player)) {
            player.setGameMode(GameType.SPECTATOR);
            teleport(player, resolveSpectatorSpawn(team, server));
            setImportantNotice(player, text("player.eliminated.no_respawn"));
            return;
        }

        if (team == null) {
            moveUnassignedPlayerToSpectator(player, server);
            return;
        }

        queuePendingRespawn(player, team, server);
    }

    public void onEntityLoad(Entity entity, ServerLevel world) {
        if (!(entity instanceof ItemEntity itemEntity)) {
            return;
        }

        Entity owner = itemEntity.getOwner();
        if (owner instanceof ServerPlayer player && !player.isCreative() && isProtectedLobbyItem(itemEntity.getItem())) {
            if (!player.level().dimension().equals(LOBBY_OVERWORLD_KEY)) {
                return;
            }
            ItemStack droppedStack = itemEntity.getItem().copy();
            itemEntity.discard();
            giveLobbyItemBack(player, droppedStack);
            sendOverlay(player, text("lobby.item_locked"));
            return;
        }
        if (owner instanceof ServerPlayer player && !player.isCreative() && itemEntity.getItem().is(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            itemEntity.getItem().setCount(1);
            consumeAllTeamBedTokens(player, itemEntity.getItem());
            return;
        }

        if (!isDiscardProtectedBoatItem(itemEntity.getItem())) {
            return;
        }
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isAlive()) {
            if (isSupplyBoatItem(itemEntity.getItem())) {
                itemEntity.discard();
            }
            return;
        }
        if (player.isCreative()) {
            return;
        }

        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        long tick = overworld.getGameTime();
        Long expiresAt = supplyBoatDropConfirmUntil.get(player.getUUID());
        if (expiresAt != null && expiresAt >= tick) {
            supplyBoatDropConfirmUntil.remove(player.getUUID());
            itemEntity.discard();
            sendOverlay(player, text("boat.discarded"));
            return;
        }

        supplyBoatDropConfirmUntil.put(player.getUUID(), tick + SUPPLY_BOAT_DROP_CONFIRM_TICKS);
        ItemStack droppedStack = itemEntity.getItem().copy();
        itemEntity.discard();
        giveBoatBack(player, droppedStack);
        sendOverlay(player, text("boat.drop_confirm"));
    }

    public void onServerTick(MinecraftServer server) {
        processPendingJoinSyncs(server);
        processPendingBedPlacements(server);
        processPendingBrokenTeamBedDrops(server);
        processPendingRespawns(server);
        normalizeTeamBedInventories(server);
        processRespawnInvulnerability(server);
        processImportantNotices(server);
        processPendingMatchObservations(server);
        processPendingMilkBucketUses(server);
        processSharedTeamHealth(server);
        processReadyCountdown(server);
        processSidebars(server);
    }

    private void processPendingMilkBucketUses(MinecraftServer server) {
        if (server == null || pendingMilkBucketUses.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, InteractionHand>> iterator = pendingMilkBucketUses.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, InteractionHand> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            InteractionHand hand = entry.getValue();
            if (player.isUsingItem() && player.getItemInHand(hand).is(Items.MILK_BUCKET)) {
                continue;
            }
            if (player.getItemInHand(hand).is(Items.BUCKET)) {
                handleMilkBucketConsumed(player);
            }
            iterator.remove();
        }
    }

    public void handleMilkBucketConsumed(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Integer team = engine.teamForPlayer(player.getUUID());
        if (team == null) {
            clearPotionEffects(player);
            return;
        }
        for (ServerPlayer teammate : resolveTotemEligibleTeamPlayers(serverOf(player), team)) {
            clearPotionEffects(teammate);
        }
    }

    private void clearPotionEffects(ServerPlayer player) {
        if (player != null) {
            player.removeAllEffects();
        }
    }

    private void processSidebars(MinecraftServer server) {
        if (server == null) {
            return;
        }
        sidebarTickBudget++;
        if (sidebarTickBudget < SIDEBAR_UPDATE_INTERVAL_TICKS) {
            return;
        }
        sidebarTickBudget = 0;
        if (!scoreboardEnabled) {
            clearSidebars(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                applyNameColorTeams(player, server);
                applyTabListDisplay(player, server);
            }
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applySidebar(player, server);
        }
    }

    private void refreshScoreboardState(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (!scoreboardEnabled) {
            clearSidebars(server);
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applySidebar(player, server);
        }
    }

    private void clearSidebars(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clearSidebar(player);
        }
    }

    private void clearSidebar(ServerPlayer player) {
        if (player == null) {
            return;
        }
        sidebarLineCounts.remove(player.getUUID());
        sidebarLines.remove(player.getUUID());
        if (!sidebarInitialized.remove(player.getUUID())) {
            return;
        }
        player.connection.send(
                new ClientboundSetObjectivePacket(createSidebarObjective(), ClientboundSetObjectivePacket.METHOD_REMOVE)
        );
    }

    private void applySidebar(ServerPlayer player, MinecraftServer server) {
        if (player == null) {
            return;
        }

        boolean initialized = sidebarInitialized.add(player.getUUID());
        if (initialized) {
            Objective objective = createSidebarObjective();
            player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
        }

        List<Component> lines = buildSidebarLines(player, server);
        int used = Math.min(lines.size(), SIDEBAR_MAX_LINES);
        List<Component> displayedLines = new ArrayList<>(used);
        for (int index = 0; index < used; index++) {
            displayedLines.add(lines.get(index));
        }
        int previous = initialized ? 0 : sidebarLineCounts.getOrDefault(player.getUUID(), 0);
        List<Component> previousLines = initialized ? Collections.emptyList() : sidebarLines.getOrDefault(player.getUUID(), Collections.emptyList());
        for (int index = 0; index < used; index++) {
            if (previous != used || index >= previousLines.size() || !displayedLines.get(index).equals(previousLines.get(index))) {
                player.connection.send(
                        new ClientboundSetScorePacket(
                                sidebarEntry(index),
                                SIDEBAR_OBJECTIVE_NAME,
                                used - index,
                                Optional.of(displayedLines.get(index)),
                                Optional.empty()
                        )
                );
            }
        }
        for (int index = used; index < previous; index++) {
            player.connection.send(new ClientboundResetScorePacket(sidebarEntry(index), SIDEBAR_OBJECTIVE_NAME));
        }
        sidebarLineCounts.put(player.getUUID(), used);
        sidebarLines.put(player.getUUID(), displayedLines);
        applyNameColorTeams(player, server);
        applyTabListDisplay(player, server);
    }

    private void applyNameColorTeams(ServerPlayer viewer, MinecraftServer server) {
        if (viewer == null || server == null) {
            return;
        }
        if (!tabEnabled) {
            clearNameColorTeams(viewer);
            return;
        }

        Scoreboard scoreboard = new Scoreboard();
        PlayerTeam neutralTeam = createNameColorTeam(scoreboard, NAME_COLOR_TEAM_NEUTRAL, ChatFormatting.WHITE, Component.empty());
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            PlayerTeam bucket = resolveNameColorTeam(viewer, scoreboard, target, neutralTeam);
            scoreboard.addPlayerToTeam(target.getName().getString(), bucket);
        }

        clearNameColorTeams(viewer);
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            viewer.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        }
        Set<String> installedTeams = new HashSet<>();
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            installedTeams.add(team.getName());
        }
        activeNameColorTeams.put(viewer.getUUID(), installedTeams);
    }

    private PlayerTeam createNameColorTeam(Scoreboard scoreboard, String teamName, ChatFormatting color, Component prefix) {
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
        }
        team.setDisplayName(Component.literal(teamName));
        team.setColor(color);
        team.setPlayerPrefix(prefix == null ? Component.empty() : prefix);
        return team;
    }

    private PlayerTeam resolveNameColorTeam(ServerPlayer viewer, Scoreboard scoreboard, ServerPlayer target, PlayerTeam neutralTeam) {
        if (!engine.isRunning()) {
            return neutralTeam;
        }
        Integer targetTeam = target == null ? null : engine.teamForPlayer(target.getUUID());
        if (targetTeam != null) {
            Integer viewerTeam = viewer != null ? engine.teamForPlayer(viewer.getUUID()) : null;
            ChatFormatting color = resolveTabTeamColor(viewerTeam, targetTeam);
            return createNameColorTeam(
                    scoreboard,
                    NAME_COLOR_TEAM_PREFIX + "team_" + targetTeam,
                    color,
                    buildTabTeamPrefix(targetTeam, color)
            );
        }
        return neutralTeam;
    }

    private void applyTabListDisplay(ServerPlayer player, MinecraftServer server) {
        if (player == null || server == null) {
            return;
        }
        if (!tabEnabled) {
            clearTabListDisplay(player);
            return;
        }
        player.connection.send(new ClientboundTabListPacket(buildTabListHeader(), buildTabListFooter(player, server)));
    }

    private Component buildTabListHeader() {
        MutableComponent header = Component.literal(text("scoreboard.title")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        header.append(Component.literal("\n"));
        header.append(
                Component.literal(text("scoreboard.line.state", text(engine.isRunning() ? "scoreboard.state.running" : "scoreboard.state.lobby")))
                        .withStyle(ChatFormatting.YELLOW)
        );
        return header;
    }

    private Component buildTabListFooter(ServerPlayer viewer, MinecraftServer server) {
        MutableComponent footer = Component.empty();
        if (!engine.isRunning()) {
            appendTabSection(footer, text("scoreboard.line.ready", readyPlayers.size(), server.getPlayerList().getPlayers().size()), ChatFormatting.GREEN);
            appendTabSeparator(footer);
            appendTabSection(footer, text("scoreboard.line.teams", teamCount), ChatFormatting.AQUA);
            footer.append(Component.literal("\n"));
            appendTabSection(footer, text("scoreboard.line.health", healthPresetLabel(healthPreset)), ChatFormatting.RED);
            appendTabSeparator(footer);
            appendTabSection(footer, text("scoreboard.line.norespawn", stateLabel(noRespawnEnabled)), ChatFormatting.LIGHT_PURPLE);
            return footer;
        }

        Integer playerTeam = engine.teamForPlayer(viewer.getUUID());
        appendTabSection(
                footer,
                text("scoreboard.line.team", playerTeam != null ? teamLabel(playerTeam) : text("scoreboard.value.none")),
                ChatFormatting.GREEN
        );
        appendTabSeparator(footer);
        appendTabSection(
                footer,
                text("scoreboard.line.team_bed", playerTeam != null ? bedStatusText(teamBeds.containsKey(playerTeam)) : text("scoreboard.value.none")),
                ChatFormatting.AQUA
        );
        footer.append(Component.literal("\n"));
        appendTabSection(footer, text("scoreboard.line.dimension", dimensionLabel(viewer)), ChatFormatting.DARK_AQUA);
        appendTabSeparator(footer);
        appendTabSection(footer, text("scoreboard.line.norespawn", stateLabel(noRespawnEnabled)), ChatFormatting.LIGHT_PURPLE);
        return footer;
    }

    private void appendTabSection(MutableComponent target, String content, ChatFormatting color) {
        target.append(Component.literal(content).withStyle(color));
    }

    private void appendTabSeparator(MutableComponent target) {
        target.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
    }

    private void clearTabListDisplay(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.connection.send(new ClientboundTabListPacket(Component.empty(), Component.empty()));
    }

    private void clearNameColorTeams(ServerPlayer viewer) {
        if (viewer == null) {
            return;
        }
        Set<String> knownTeams = activeNameColorTeams.remove(viewer.getUUID());
        if (knownTeams == null || knownTeams.isEmpty()) {
            return;
        }
        for (String teamName : knownTeams) {
            viewer.connection.send(
                    ClientboundSetPlayerTeamPacket.createRemovePacket(createNameColorTeam(new Scoreboard(), teamName, ChatFormatting.WHITE, Component.empty()))
            );
        }
    }

    private void refreshTabState(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (tabEnabled) {
                applyNameColorTeams(player, server);
                applyTabListDisplay(player, server);
            } else {
                clearNameColorTeams(player);
                clearTabListDisplay(player);
            }
        }
    }

    private ChatFormatting resolveTabTeamColor(Integer viewerTeam, int targetTeam) {
        if (viewerTeam != null) {
            return viewerTeam == targetTeam ? ChatFormatting.GREEN : ChatFormatting.RED;
        }
        return TAB_TEAM_COLORS[Math.floorMod(targetTeam - 1, TAB_TEAM_COLORS.length)];
    }

    private Component buildTabTeamPrefix(int team, ChatFormatting color) {
        MutableComponent prefix = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY);
        prefix.append(Component.literal("T" + team).withStyle(color));
        prefix.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
        return prefix;
    }

    private Objective createSidebarObjective() {
        return new Objective(
                new Scoreboard(),
                SIDEBAR_OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal(text("scoreboard.title")),
                ObjectiveCriteria.DUMMY.getDefaultRenderType(),
                false,
                null
        );
    }

    private String sidebarEntry(int index) {
        return SIDEBAR_ENTRY_PREFIX + index;
    }

    private List<Component> buildSidebarLines(ServerPlayer viewer, MinecraftServer server) {
        List<Component> lines = new ArrayList<>();
        if (!engine.isRunning()) {
            lines.add(styledText(ChatFormatting.YELLOW, "scoreboard.line.state", text("scoreboard.state.lobby")));
            lines.add(styledText(ChatFormatting.GREEN, "scoreboard.line.ready", readyPlayers.size(), server.getPlayerList().getPlayers().size()));
            if (countdownRemainingSeconds >= 0) {
                lines.add(styledText(ChatFormatting.GOLD, "scoreboard.line.countdown", countdownRemainingSeconds));
            }
            lines.add(styledText(ChatFormatting.AQUA, "scoreboard.line.teams", teamCount));
            lines.add(styledText(ChatFormatting.RED, "scoreboard.line.health", healthPresetLabel(healthPreset)));
            lines.add(styledText(ChatFormatting.LIGHT_PURPLE, "scoreboard.line.norespawn", stateLabel(noRespawnEnabled)));
            return lines;
        }

        Integer playerTeam = engine.teamForPlayer(viewer.getUUID());
        lines.add(styledText(ChatFormatting.YELLOW, "scoreboard.line.state", text("scoreboard.state.running")));
        lines.add(styledText(ChatFormatting.AQUA, "scoreboard.line.team", playerTeam != null ? teamLabel(playerTeam) : text("scoreboard.value.none")));
        lines.add(
                styledText(
                        ChatFormatting.GREEN,
                        "scoreboard.line.team_bed",
                        playerTeam != null ? bedStatusText(teamBeds.containsKey(playerTeam)) : text("scoreboard.value.none")
                )
        );
        lines.add(styledText(ChatFormatting.GOLD, "scoreboard.line.respawn", respawnStatusText(viewer, server)));
        Integer observationSeconds = pendingMatchObservationSeconds(viewer);
        if (observationSeconds != null) {
            lines.add(styledText(ChatFormatting.YELLOW, "scoreboard.line.observe", text("scoreboard.observe.countdown", observationSeconds)));
        }
        lines.add(styledText(ChatFormatting.RED, "scoreboard.line.health", healthPresetLabel(healthPreset)));
        lines.add(styledText(ChatFormatting.BLUE, "scoreboard.line.teams", teamCount));
        lines.add(styledText(ChatFormatting.RED, "scoreboard.line.beds", teamBeds.size()));
        if (observationSeconds == null) {
            lines.add(styledText(ChatFormatting.DARK_AQUA, "scoreboard.line.dimension", dimensionLabel(viewer)));
        }
        lines.add(styledText(ChatFormatting.LIGHT_PURPLE, "scoreboard.line.norespawn", stateLabel(noRespawnEnabled)));
        return lines;
    }

    private Component styledText(ChatFormatting color, String key, Object... args) {
        return Component.literal(text(key, args)).withStyle(color);
    }

    private void processPendingBedPlacements(MinecraftServer server) {
        if (pendingBedPlacements.isEmpty()) {
            return;
        }

        long tick = server.overworld().getGameTime();
        Iterator<PendingBedPlacement> iterator = pendingBedPlacements.iterator();
        while (iterator.hasNext()) {
            PendingBedPlacement pending = iterator.next();
            ServerLevel world = server.getLevel(pending.worldKey());
            if (world == null) {
                iterator.remove();
                continue;
            }

            if (tick > pending.expiresAtTick()) {
                iterator.remove();
                continue;
            }

            BlockState state = world.getBlockState(pending.approxPos());
            if (!state.is(BlockTags.BEDS)) {
                continue;
            }

            SpawnPoint bed = normalizeBedFoot(pending.worldKey(), pending.approxPos(), state);
            placedTeamBedOwners.put(bed, pending.team());
            teamBeds.put(pending.team(), bed);

            ServerPlayer owner = server.getPlayerList().getPlayer(pending.ownerId());
            if (owner != null) {
                consumeResidualPlacedTeamBedToken(owner, pending.hand());
                sendOverlay(owner, text("bed.updated", teamLabel(pending.team())));
            }
            iterator.remove();
        }
    }

    private void processPendingBrokenTeamBedDrops(MinecraftServer server) {
        if (pendingBrokenTeamBedDrops.isEmpty()) {
            return;
        }

        long tick = server.overworld().getGameTime();
        Iterator<PendingBrokenTeamBedDrop> iterator = pendingBrokenTeamBedDrops.iterator();
        while (iterator.hasNext()) {
            PendingBrokenTeamBedDrop pending = iterator.next();
            if (tick < pending.processAtTick()) {
                continue;
            }

            ServerLevel world = server.getLevel(pending.spawnPoint().worldKey());
            if (world == null) {
                iterator.remove();
                continue;
            }

            AABB searchBox = new AABB(pending.spawnPoint().pos()).inflate(1.5D);
            world.getEntitiesOfClass(
                    ItemEntity.class,
                    searchBox,
                    entity -> entity.isAlive() && entity.getItem().is(Items.WHITE_BED)
            ).stream().findFirst().ifPresent(ItemEntity::discard);

            world.addFreshEntity(new ItemEntity(
                    world,
                    pending.spawnPoint().pos().getX() + 0.5D,
                    pending.spawnPoint().pos().getY() + 0.5D,
                    pending.spawnPoint().pos().getZ() + 0.5D,
                    createOwnedTeamBedItem(pending.ownerTeam())
            ));
            iterator.remove();
        }
    }

    private void processPendingRespawns(MinecraftServer server) {
        if (pendingRespawns.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, PendingRespawn>> iterator = pendingRespawns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingRespawn> entry = iterator.next();
            PendingRespawn pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            int remainingTicks = pending.remainingTicks() - 1;
            if (remainingTicks <= 0) {
                if (player != null) {
                    completePendingRespawn(player, pending.team(), server);
                }
                iterator.remove();
                continue;
            }

            int secondsLeft = (remainingTicks + 19) / 20;
            if (player != null) {
                if (!player.isSpectator()) {
                    player.setGameMode(GameType.SPECTATOR);
                }
                if (secondsLeft != pending.lastShownSeconds()) {
                    sendOverlay(player, text("respawn.wait", secondsLeft));
                }
            }
            entry.setValue(new PendingRespawn(pending.team(), remainingTicks, secondsLeft));
        }
    }

    private void processPendingMatchObservations(MinecraftServer server) {
        if (pendingMatchObservations.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, PendingMatchObservation>> iterator = pendingMatchObservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingMatchObservation> entry = iterator.next();
            PendingMatchObservation pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            int remainingTicks = pending.remainingTicks() - 1;
            if (player != null) {
                enforceObservationLock(player, pending.spawnPoint());
                sendOverlay(player, text("match.observe.wait", Math.max(1, (remainingTicks + 19) / 20)));
            }
            if (remainingTicks <= 0) {
                if (player != null) {
                    completeMatchObservation(player);
                }
                iterator.remove();
                continue;
            }

            int secondsLeft = (remainingTicks + 19) / 20;
            entry.setValue(new PendingMatchObservation(pending.spawnPoint(), remainingTicks, secondsLeft));
        }
    }

    private void processSharedTeamHealth(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (!engine.isRunning()) {
            clearSharedTeamState();
            return;
        }

        sharedStateTick++;
        Map<Integer, List<ServerPlayer>> playersByTeam = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isSharedHealthParticipant(player)) {
                continue;
            }
            Integer team = engine.teamForPlayer(player.getUUID());
            if (team == null) {
                continue;
            }
            playersByTeam.computeIfAbsent(team, ignored -> new ArrayList<>()).add(player);
        }

        teamSharedHealth.keySet().retainAll(playersByTeam.keySet());
        observedTeamHealthPlayers.keySet().retainAll(playersByTeam.keySet());
        teamSharedExperience.keySet().retainAll(playersByTeam.keySet());
        observedTeamExperiencePlayers.keySet().retainAll(playersByTeam.keySet());
        teamSharedFoodLevels.keySet().retainAll(playersByTeam.keySet());
        teamSharedSaturation.keySet().retainAll(playersByTeam.keySet());
        observedTeamFoodPlayers.keySet().retainAll(playersByTeam.keySet());
        teamLastDamageTicks.keySet().retainAll(playersByTeam.keySet());

        for (Map.Entry<Integer, List<ServerPlayer>> entry : playersByTeam.entrySet()) {
            int team = entry.getKey();
            List<ServerPlayer> players = entry.getValue();
            Set<UUID> currentPlayers = new HashSet<>();
            for (ServerPlayer player : players) {
                currentPlayers.add(player.getUUID());
            }

            TeamLifeBindCombatMath.ExperienceState experienceState = processSharedTeamExperience(team, players, currentPlayers);
            applySharedMaxHealth(players, experienceState.level());
            processSharedTeamFood(team, players, currentPlayers);
            applySharedPotionEffects(players);
            if (healthSyncEnabled) {
                processSharedTeamHealthForTeam(team, players, currentPlayers);
            } else {
                clearTeamSharedHealthTracking(team);
                teamLastDamageTicks.remove(team);
            }
        }
    }

    private void clearSharedTeamState() {
        clearSharedHealthTracking();
        teamSharedExperience.clear();
        observedTeamExperiencePlayers.clear();
        teamSharedFoodLevels.clear();
        teamSharedSaturation.clear();
        observedTeamFoodPlayers.clear();
    }

    private void clearSharedHealthTracking() {
        teamSharedHealth.clear();
        observedTeamHealthPlayers.clear();
        teamLastDamageTicks.clear();
    }

    private TeamLifeBindCombatMath.ExperienceState processSharedTeamExperience(int team, List<ServerPlayer> players, Set<UUID> currentPlayers) {
        Integer baselineValue = teamSharedExperience.get(team);
        Set<UUID> observedPlayers = observedTeamExperiencePlayers.computeIfAbsent(team, HashSet::new);
        if (baselineValue == null) {
            int initialExperience = resolveInitialSharedExperience(players);
            teamSharedExperience.put(team, initialExperience);
            applySharedExperience(players, initialExperience);
            observedPlayers.clear();
            observedPlayers.addAll(currentPlayers);
            return TeamLifeBindCombatMath.resolveExperienceState(initialExperience);
        }

        int baseline = Math.max(0, baselineValue);
        int delta = 0;
        boolean changed = false;
        for (ServerPlayer player : players) {
            UUID playerId = player.getUUID();
            if (!observedPlayers.contains(playerId)) {
                applySharedExperience(player, baseline);
                continue;
            }

            int currentExperience = Math.max(0, player.totalExperience);
            if (currentExperience == baseline) {
                continue;
            }
            delta += currentExperience - baseline;
            changed = true;
        }

        int targetExperience = changed ? Math.max(0, baseline + delta) : baseline;
        teamSharedExperience.put(team, targetExperience);
        applySharedExperience(players, targetExperience);
        observedPlayers.clear();
        observedPlayers.addAll(currentPlayers);
        return TeamLifeBindCombatMath.resolveExperienceState(targetExperience);
    }

    private int resolveInitialSharedExperience(List<ServerPlayer> players) {
        int initialExperience = 0;
        for (ServerPlayer player : players) {
            initialExperience = Math.max(initialExperience, Math.max(0, player.totalExperience));
        }
        return initialExperience;
    }

    private void applySharedExperience(List<ServerPlayer> players, int totalExperience) {
        for (ServerPlayer player : players) {
            applySharedExperience(player, totalExperience);
        }
    }

    private void applySharedExperience(ServerPlayer player, int totalExperience) {
        if (player == null) {
            return;
        }
        TeamLifeBindCombatMath.ExperienceState state = TeamLifeBindCombatMath.resolveExperienceState(totalExperience);
        if (player.totalExperience == state.totalExperience()
                && player.experienceLevel == state.level()
                && Math.abs(player.experienceProgress - state.progress()) <= 0.0001F) {
            return;
        }
        player.totalExperience = state.totalExperience();
        player.setExperienceLevels(state.level());
        player.setExperiencePoints(state.intoCurrentLevel());
        player.experienceProgress = state.progress();
    }

    private void processSharedTeamFood(int team, List<ServerPlayer> players, Set<UUID> currentPlayers) {
        Integer baselineFoodValue = teamSharedFoodLevels.get(team);
        Float baselineSaturationValue = teamSharedSaturation.get(team);
        Set<UUID> observedPlayers = observedTeamFoodPlayers.computeIfAbsent(team, HashSet::new);
        if (baselineFoodValue == null || baselineSaturationValue == null) {
            int initialFood = resolveInitialSharedFood(players);
            float initialSaturation = resolveInitialSharedSaturation(players, initialFood);
            teamSharedFoodLevels.put(team, initialFood);
            teamSharedSaturation.put(team, initialSaturation);
            applySharedFood(players, initialFood, initialSaturation);
            observedPlayers.clear();
            observedPlayers.addAll(currentPlayers);
            return;
        }

        int baselineFood = clampFoodLevel(baselineFoodValue);
        float baselineSaturation = clampSaturation(baselineFood, baselineSaturationValue);
        int positiveFoodDelta = 0;
        int negativeFoodDelta = 0;
        float positiveSaturationDelta = 0.0F;
        float negativeSaturationDelta = 0.0F;
        boolean changed = false;

        for (ServerPlayer player : players) {
            UUID playerId = player.getUUID();
            if (!observedPlayers.contains(playerId)) {
                applySharedFood(player, baselineFood, baselineSaturation);
                continue;
            }

            int currentFood = clampFoodLevel(player.getFoodData().getFoodLevel());
            if (currentFood != baselineFood) {
                int foodDelta = currentFood - baselineFood;
                positiveFoodDelta = Math.max(positiveFoodDelta, foodDelta);
                negativeFoodDelta = Math.min(negativeFoodDelta, foodDelta);
                changed = true;
            }

            float currentSaturation = clampSaturation(currentFood, player.getFoodData().getSaturationLevel());
            if (Math.abs(currentSaturation - baselineSaturation) > 0.001F) {
                float saturationDelta = currentSaturation - baselineSaturation;
                positiveSaturationDelta = Math.max(positiveSaturationDelta, saturationDelta);
                negativeSaturationDelta = Math.min(negativeSaturationDelta, saturationDelta);
                changed = true;
            }
        }

        int targetFood = changed ? clampFoodLevel(baselineFood + positiveFoodDelta + negativeFoodDelta) : baselineFood;
        float targetSaturation = changed
                ? clampSaturation(targetFood, baselineSaturation + positiveSaturationDelta + negativeSaturationDelta)
                : clampSaturation(targetFood, baselineSaturation);
        teamSharedFoodLevels.put(team, targetFood);
        teamSharedSaturation.put(team, targetSaturation);
        applySharedFood(players, targetFood, targetSaturation);
        observedPlayers.clear();
        observedPlayers.addAll(currentPlayers);
    }

    private int resolveInitialSharedFood(List<ServerPlayer> players) {
        int initialFood = 20;
        for (ServerPlayer player : players) {
            initialFood = Math.min(initialFood, clampFoodLevel(player.getFoodData().getFoodLevel()));
        }
        return initialFood;
    }

    private float resolveInitialSharedSaturation(List<ServerPlayer> players, int sharedFoodLevel) {
        float initialSaturation = sharedFoodLevel;
        for (ServerPlayer player : players) {
            initialSaturation = Math.min(initialSaturation, clampSaturation(sharedFoodLevel, player.getFoodData().getSaturationLevel()));
        }
        return clampSaturation(sharedFoodLevel, initialSaturation);
    }

    private int clampFoodLevel(int foodLevel) {
        return Math.max(0, Math.min(foodLevel, 20));
    }

    private float clampSaturation(int foodLevel, float saturation) {
        return Math.max(0.0F, Math.min(saturation, foodLevel));
    }

    private void applySharedFood(List<ServerPlayer> players, int foodLevel, float saturation) {
        for (ServerPlayer player : players) {
            applySharedFood(player, foodLevel, saturation);
        }
    }

    private void applySharedFood(ServerPlayer player, int foodLevel, float saturation) {
        if (player == null) {
            return;
        }
        int clampedFood = clampFoodLevel(foodLevel);
        float clampedSaturation = clampSaturation(clampedFood, saturation);
        if (player.getFoodData().getFoodLevel() != clampedFood) {
            player.getFoodData().setFoodLevel(clampedFood);
        }
        if (Math.abs(player.getFoodData().getSaturationLevel() - clampedSaturation) > 0.001F) {
            player.getFoodData().setSaturation(clampedSaturation);
        }
    }

    private void applySharedMaxHealth(List<ServerPlayer> players, int sharedLevel) {
        double targetMaxHealth = TeamLifeBindCombatMath.sharedMaxHealth(healthPreset, sharedLevel);
        for (ServerPlayer player : players) {
            if (player == null) {
                continue;
            }
            AttributeInstance maxHealthAttribute = maxHealthAttribute(player);
            if (maxHealthAttribute != null
                    && Math.abs(maxHealthAttribute.getBaseValue() - targetMaxHealth) > 0.001D) {
                maxHealthAttribute.setBaseValue(targetMaxHealth);
            }
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    private void applySharedPotionEffects(List<ServerPlayer> players) {
        Map<String, MobEffectInstance> sharedEffects = collectSharedPotionEffects(players);
        if (sharedEffects.isEmpty()) {
            return;
        }
        for (ServerPlayer player : players) {
            Map<String, MobEffectInstance> currentEffects = new HashMap<>();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                String effectKey = statusEffectKey(effect);
                if (effectKey != null) {
                    currentEffects.put(effectKey, effect);
                }
            }
            for (Map.Entry<String, MobEffectInstance> entry : sharedEffects.entrySet()) {
                MobEffectInstance current = currentEffects.get(entry.getKey());
                MobEffectInstance shared = entry.getValue();
                if (!shouldRefreshStatusEffect(current, shared)) {
                    continue;
                }
                player.addEffect(new MobEffectInstance(shared));
            }
        }
    }

    private Map<String, MobEffectInstance> collectSharedPotionEffects(List<ServerPlayer> players) {
        Map<String, MobEffectInstance> effects = new HashMap<>();
        for (ServerPlayer player : players) {
            for (MobEffectInstance effect : player.getActiveEffects()) {
                String effectKey = statusEffectKey(effect);
                if (effectKey == null) {
                    continue;
                }
                MobEffectInstance current = effects.get(effectKey);
                if (current == null
                        || effect.getAmplifier() > current.getAmplifier()
                        || (effect.getAmplifier() == current.getAmplifier() && effect.getDuration() > current.getDuration())) {
                    effects.put(effectKey, new MobEffectInstance(effect));
                }
            }
        }
        return effects;
    }

    private boolean shouldRefreshStatusEffect(MobEffectInstance current, MobEffectInstance shared) {
        if (shared == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        return current.getAmplifier() != shared.getAmplifier()
                || current.isAmbient() != shared.isAmbient()
                || current.isVisible() != shared.isVisible()
                || current.showIcon() != shared.showIcon()
                || current.getDuration() + EFFECT_DURATION_SYNC_TOLERANCE_TICKS < shared.getDuration();
    }

    private void processSharedTeamHealthForTeam(int team, List<ServerPlayer> players, Set<UUID> currentPlayers) {
        Float baselineValue = teamSharedHealth.get(team);
        Set<UUID> observedPlayers = observedTeamHealthPlayers.computeIfAbsent(team, HashSet::new);
        if (baselineValue == null) {
            float initialHealth = resolveInitialSharedHealth(players);
            teamSharedHealth.put(team, initialHealth);
            applySharedTeamHealth(players, initialHealth);
            observedPlayers.clear();
            observedPlayers.addAll(currentPlayers);
            return;
        }

        float baseline = clampSharedHealth(baselineValue, players);
        float delta = 0.0F;
        boolean changed = false;
        for (ServerPlayer player : players) {
            UUID playerId = player.getUUID();
            if (!observedPlayers.contains(playerId)) {
                float clampedBaseline = clampPlayerHealth(player, baseline);
                if (Math.abs(player.getHealth() - clampedBaseline) > HEALTH_SYNC_EPSILON) {
                    player.setHealth(clampedBaseline);
                }
                continue;
            }

            float currentHealth = clampPlayerHealth(player, player.getHealth());
            if (Math.abs(currentHealth - baseline) <= HEALTH_SYNC_EPSILON) {
                continue;
            }

            float playerDelta = currentHealth - baseline;
            if (playerDelta < 0.0F) {
                double armor = player.getAttribute(Attributes.ARMOR) != null ? player.getAttributeValue(Attributes.ARMOR) : 0.0D;
                double toughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS) != null ? player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) : 0.0D;
                playerDelta = -TeamLifeBindCombatMath.applyExtraArmorReduction(-playerDelta, armor, toughness);
                teamLastDamageTicks.put(team, sharedStateTick);
            }
            delta += playerDelta;
            changed = true;
        }

        float targetHealth = changed ? clampSharedHealth(baseline + delta, players) : baseline;
        targetHealth = applySharedBonusRegen(team, players, targetHealth);
        teamSharedHealth.put(team, targetHealth);
        applySharedTeamHealth(players, targetHealth);
        observedPlayers.clear();
        observedPlayers.addAll(currentPlayers);
    }

    //noinspection BooleanMethodIsAlwaysInverted
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isSharedHealthParticipant(ServerPlayer player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && !pendingRespawns.containsKey(player.getUUID());
    }

    private float resolveInitialSharedHealth(List<ServerPlayer> players) {
        float initialHealth = resolveSharedHealthCap(players);
        for (ServerPlayer player : players) {
            initialHealth = Math.min(initialHealth, clampPlayerHealth(player, player.getHealth()));
        }
        return clampSharedHealth(initialHealth, players);
    }

    private float clampSharedHealth(float health, List<ServerPlayer> players) {
        return Math.max(0.0F, Math.min(health, resolveSharedHealthCap(players)));
    }

    private float clampPlayerHealth(ServerPlayer player, float health) {
        return Math.max(0.0F, Math.min(health, player.getMaxHealth()));
    }

    private float resolveSharedHealthCap(List<ServerPlayer> players) {
        float maxHealth = (float) TeamLifeBindCombatMath.SHARED_MAX_HEALTH_CAP;
        for (ServerPlayer player : players) {
            maxHealth = Math.min(maxHealth, player.getMaxHealth());
        }
        return maxHealth;
    }

    private float applySharedBonusRegen(int team, List<ServerPlayer> players, float targetHealth) {
        if (players.isEmpty() || targetHealth >= resolveSharedHealthCap(players) - HEALTH_SYNC_EPSILON) {
            return targetHealth;
        }
        if (teamSharedFoodLevels.getOrDefault(team, 20) < 18) {
            return targetHealth;
        }
        long lastDamageTick = teamLastDamageTicks.getOrDefault(team, Long.MIN_VALUE / 4L);
        if ((sharedStateTick - lastDamageTick) < TEAM_REGEN_IDLE_TICKS) {
            return targetHealth;
        }
        if (((sharedStateTick + team) % TEAM_REGEN_INTERVAL_TICKS) != 0L) {
            return targetHealth;
        }
        return clampSharedHealth(targetHealth + TEAM_REGEN_AMOUNT, players);
    }

    private void applySharedTeamHealth(List<ServerPlayer> players, float targetHealth) {
        for (ServerPlayer player : players) {
            float clampedHealth = clampPlayerHealth(player, targetHealth);
            if (Math.abs(player.getHealth() - clampedHealth) <= HEALTH_SYNC_EPSILON) {
                continue;
            }
            player.setHealth(clampedHealth);
        }
    }

    private void forgetSharedTeamHealthPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Iterator<Map.Entry<Integer, Set<UUID>>> iterator = observedTeamHealthPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Set<UUID>> entry = iterator.next();
            entry.getValue().remove(playerId);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private void forgetSharedTeamExperiencePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Iterator<Map.Entry<Integer, Set<UUID>>> iterator = observedTeamExperiencePlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Set<UUID>> entry = iterator.next();
            entry.getValue().remove(playerId);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private void forgetSharedTeamFoodPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Iterator<Map.Entry<Integer, Set<UUID>>> iterator = observedTeamFoodPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Set<UUID>> entry = iterator.next();
            entry.getValue().remove(playerId);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private void clearTeamSharedHealthTracking(Integer team) {
        if (team == null) {
            return;
        }
        teamSharedHealth.remove(team);
        observedTeamHealthPlayers.remove(team);
    }

    private void processImportantNotices(MinecraftServer server) {
        if (importantNotices.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, TimedNotice>> iterator = importantNotices.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TimedNotice> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }

            TimedNotice notice = entry.getValue();
            sendOverlay(player, notice.message());
            if (notice.remainingTicks() <= 1) {
                iterator.remove();
            } else {
                entry.setValue(new TimedNotice(notice.message(), notice.remainingTicks() - 1));
            }
        }
    }

    private void processReadyCountdown(MinecraftServer server) {
        if (countdownRemainingSeconds < 0) {
            return;
        }

        String reason = readyFailureReason(server);
        if (reason != null) {
            broadcastOverlay(server, reason);
            cancelCountdown();
            return;
        }

        if (++countdownTickBudget < 20) {
            return;
        }
        countdownTickBudget = 0;

        if (countdownRemainingSeconds <= 0) {
            cancelCountdown();
            start(server);
            return;
        }

        if (countdownRemainingSeconds <= 5 || countdownRemainingSeconds % 5 == 0) {
            broadcastOverlay(server, text("ready.countdown_tick", countdownRemainingSeconds));
        }
        if (countdownRemainingSeconds <= 5) {
            playReadyCountdownSound(server, countdownRemainingSeconds);
        }
        countdownRemainingSeconds--;
    }

    private void evaluateReadyCountdown(MinecraftServer server) {
        if (server == null || engine.isRunning()) {
            cancelCountdown();
            return;
        }

        String reason = readyFailureReason(server);
        if (reason != null) {
            if (countdownRemainingSeconds >= 0) {
                broadcastOverlay(server, reason);
            }
            cancelCountdown();
            return;
        }

        if (countdownRemainingSeconds >= 0) {
            return;
        }

        countdownRemainingSeconds = READY_COUNTDOWN_SECONDS;
        countdownTickBudget = 0;
        broadcastOverlay(server, text("ready.countdown_started"));
    }

    private String readyFailureReason(MinecraftServer server) {
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        if (online.size() < teamCount) {
            return text("ready.countdown_cancel_players");
        }

        for (ServerPlayer player : online) {
            if (!readyPlayers.contains(player.getUUID())) {
                return text("ready.countdown_cancel_unready");
            }
        }
        return null;
    }

    private void cancelCountdown() {
        countdownRemainingSeconds = -1;
        countdownTickBudget = 0;
    }

    private void handlePlayerDeath(ServerPlayer deadPlayer) {
        pendingMatchObservations.remove(deadPlayer.getUUID());
        DeathResult result = engine.onPlayerDeath(deadPlayer.getUUID());
        if (!result.triggered()) {
            return;
        }
        if (!acquireTeamWipeGuard(result.team())) {
            return;
        }

        MinecraftServer server = serverOf(deadPlayer);
        if (server == null) {
            return;
        }

        notifyTeamWiped(server, result.team(), result.victims());

        for (UUID victimId : result.victims()) {
            if (victimId.equals(deadPlayer.getUUID())) {
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(victimId);
            if (target == null || target.isDeadOrDying()) {
                continue;
            }
            target.setHealth(0.0F);
        }
    }

    private void stopInternal(MinecraftServer server, boolean byWin, String reason) {
        if (byWin) {
            Integer winner = engine.winnerTeam();
            if (winner != null) {
                broadcastImportantNotice(server, text("match.win", teamLabel(winner), reason));
            }
        }

        engine.stop();
        cancelCountdown();
        readyPlayers.clear();
        clearRoundState();
        ServerLevel lobby = lobbyWorldIfReady(server);
        if (lobby != null) {
            prepareLobbyPlatform(lobby);
        }
        teleportAllToLobby(server);
        resetBattleDimensionData(server);
    }

    private void clearRoundState() {
        teamSpawns.clear();
        teamBeds.clear();
        placedTeamBedOwners.clear();
        pendingBedPlacements.clear();
        pendingBrokenTeamBedDrops.clear();
        pendingRespawns.clear();
        pendingMatchObservations.clear();
        clearSharedTeamState();
        supplyBoatDropConfirmUntil.clear();
        importantNotices.clear();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        activeMatchCenter = null;
    }

    private void resetBattleDimensionData(MinecraftServer server) {
        for (ResourceKey<Level> key : BATTLE_DIMENSIONS) {
            Path folder = server.getWorldPath(LevelResource.ROOT)
                    .resolve("dimensions")
                    .resolve(key.identifier().getNamespace())
                    .resolve(key.identifier().getPath());
            try {
                deleteDirectory(folder);
            } catch (IOException ex) {
                TeamLifeBindFabric.LOGGER.warn("Failed to reset battle dimension folder {}", folder, ex);
            }
        }
    }

    private void deleteDirectory(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, @Nullable IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean acquireTeamWipeGuard(int team) {
        long now = System.currentTimeMillis();
        Long guardUntil = teamWipeGuardUntil.get(team);
        if (guardUntil != null && guardUntil > now) {
            return false;
        }
        teamWipeGuardUntil.put(team, now + TEAM_WIPE_GUARD_MS);
        return true;
    }

    private void lockNoRespawnIfBlocked(ServerPlayer deadPlayer) {
        if (!engine.isRunning() || !noRespawnEnabled) {
            return;
        }
        if (noRespawnDimensions.contains(deadPlayer.level().dimension())) {
            noRespawnLockedPlayers.add(deadPlayer.getUUID());
        }
    }

    private boolean isRespawnLocked(ServerPlayer player) {
        return noRespawnEnabled && noRespawnLockedPlayers.contains(player.getUUID());
    }

    private void prepareLobbyPlatform(ServerLevel world) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                world.setBlock(new BlockPos(x, LOBBY_PLATFORM_Y, z), Blocks.GLASS.defaultBlockState(), 3);
                world.setBlock(new BlockPos(x, LOBBY_PLATFORM_Y + 1, z), Blocks.AIR.defaultBlockState(), 3);
                world.setBlock(new BlockPos(x, LOBBY_PLATFORM_Y + 2, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        world.setBlock(new BlockPos(0, LOBBY_PLATFORM_Y, 0), Blocks.SEA_LANTERN.defaultBlockState(), 3);
    }

    private void teleportAllToLobby(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            teleportToLobby(player);
        }
    }

    private void teleportToLobby(ServerPlayer player) {
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }
        ServerLevel lobby = lobbyWorldIfReady(server);
        if (lobby == null) {
            pendingJoinSyncs.put(player.getUUID(), JOIN_SYNC_RETRY_TICKS);
            return;
        }
        prepareLobbyPlatform(lobby);
        preparePlayerForLobby(player);
        teleport(player, new SpawnPoint(lobby.dimension(), LOBBY_SPAWN_POS));
    }

    private boolean hasActiveMatchSession() {
        return engine.isRunning() || countdownRemainingSeconds >= 0;
    }

    private void syncJoinedPlayer(ServerPlayer player, MinecraftServer server) {
        if (!engine.isRunning()) {
            ServerLevel lobby = lobbyWorldIfReady(server);
            if (lobby == null) {
                pendingJoinSyncs.put(player.getUUID(), JOIN_SYNC_RETRY_TICKS);
                return;
            }
            prepareLobbyPlatform(lobby);
            teleportToLobby(player);
            sendPlayerMessage(player, Component.literal(text("lobby.guide")), false);
            return;
        }

        Integer team = engine.teamForPlayer(player.getUUID());
        if (team == null) {
            moveUnassignedPlayerToSpectator(player, server);
            return;
        }

        if (isRespawnLocked(player)) {
            player.setGameMode(GameType.SPECTATOR);
            if (!BATTLE_DIMENSIONS.contains(player.level().dimension())) {
                teleport(player, resolveSpectatorSpawn(team, server));
            }
            return;
        }

        PendingRespawn pending = pendingRespawns.get(player.getUUID());
        if (pending != null) {
            player.setGameMode(GameType.SPECTATOR);
            if (!BATTLE_DIMENSIONS.contains(player.level().dimension())) {
                teleport(player, resolveSpectatorSpawn(pending.team(), server));
            }
            sendOverlay(player, text("respawn.wait", Math.max(1, (pending.remainingTicks() + 19) / 20)));
            return;
        }

        PendingMatchObservation observation = pendingMatchObservations.get(player.getUUID());
        if (observation != null) {
            player.setGameMode(GameType.SURVIVAL);
            enforceObservationLock(player, observation.spawnPoint());
            sendOverlay(player, text("match.observe.wait", Math.max(1, (observation.remainingTicks() + 19) / 20)));
            return;
        }

        player.setGameMode(GameType.SURVIVAL);
        if (!BATTLE_DIMENSIONS.contains(player.level().dimension())) {
            SpawnPoint respawn = resolveRespawn(team, server);
            if (respawn != null) {
                teleport(player, respawn);
            }
        }
    }

    private void moveUnassignedPlayerToSpectator(ServerPlayer player, MinecraftServer server) {
        preparePlayerForSpectator(player);
        teleport(player, resolveSpectatorSpawn(server));
        sendPlayerMessage(player, Component.literal(text("player.unassigned_spectator")), false);
    }

    private SpawnPoint resolveSpectatorSpawn(Integer team, MinecraftServer server) {
        if (team != null) {
            SpawnPoint respawn = resolveRespawn(team, server);
            if (respawn != null) {
                return new SpawnPoint(respawn.worldKey(), respawn.pos().above(8));
            }
        }
        return resolveSpectatorSpawn(server);
    }

    private SpawnPoint resolveSpectatorSpawn(MinecraftServer server) {
        SpawnPoint teamSpawn = teamSpawns.values().stream().findFirst().orElse(null);
        if (teamSpawn != null) {
            return new SpawnPoint(teamSpawn.worldKey(), teamSpawn.pos().above(8));
        }

        ServerLevel battleWorld = resolveBattleOverworld(server);
        if (battleWorld != null) {
            BlockPos center = activeMatchCenter != null ? activeMatchCenter : battleWorld.getRespawnData().pos();
            return new SpawnPoint(battleWorld.dimension(), center.above(8));
        }

        ServerLevel lobby = resolveLobbyWorld(server);
        return new SpawnPoint(lobby.dimension(), LOBBY_SPAWN_POS);
    }

    private void processPendingJoinSyncs(MinecraftServer server) {
        if (server == null || pendingJoinSyncs.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Integer>> iterator = pendingJoinSyncs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remainingTicks = entry.getValue() - 1;
            if (remainingTicks > 0) {
                entry.setValue(remainingTicks);
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }

            if (!engine.isRunning() && lobbyWorldIfReady(server) == null) {
                entry.setValue(JOIN_SYNC_RETRY_TICKS);
                continue;
            }

            syncJoinedPlayer(player, server);
            iterator.remove();
        }
    }

    private BlockPos selectMatchCenter(ServerLevel world) {
        BlockPos worldSpawn = world.getRespawnData().pos();
        BlockPos chosen = null;

        for (int attempt = 0; attempt < 128; attempt++) {
            BlockPos candidate = randomPointInRadius(worldSpawn, MATCH_CENTER_RADIUS);
            if (previousMatchCenter != null) {
                long dx = (long) candidate.getX() - previousMatchCenter.getX();
                long dz = (long) candidate.getZ() - previousMatchCenter.getZ();
                long distSq = (dx * dx) + (dz * dz);
                if (distSq < (long) MATCH_CENTER_MIN_DISTANCE * MATCH_CENTER_MIN_DISTANCE) {
                    continue;
                }
            }
            chosen = candidate;
            break;
        }

        if (chosen == null) {
            chosen = randomPointInRadius(worldSpawn, MATCH_CENTER_RADIUS);
        }

        return safeSurface(world, chosen.getX(), chosen.getZ());
    }

    private ServerLevel resolveLobbyWorld(MinecraftServer server) {
        ServerLevel lobby = server.getLevel(LOBBY_OVERWORLD_KEY);
        return lobby != null ? lobby : server.overworld();
    }

    private ServerLevel lobbyWorldIfReady(MinecraftServer server) {
        return server == null ? null : server.getLevel(LOBBY_OVERWORLD_KEY);
    }

    private ServerLevel resolveBattleOverworld(MinecraftServer server) {
        return server.getLevel(BATTLE_OVERWORLD_KEY);
    }

    private ResourceKey<Level> resolvePortalRedirect(ResourceKey<Level> from, ResourceKey<Level> to) {
        if (engine.isRunning()) {
            if (from.equals(BATTLE_OVERWORLD_KEY) && to.equals(Level.NETHER)) {
                return BATTLE_NETHER_KEY;
            }
            if (from.equals(BATTLE_NETHER_KEY) && to.equals(Level.OVERWORLD)) {
                return BATTLE_OVERWORLD_KEY;
            }
            if (from.equals(BATTLE_OVERWORLD_KEY) && to.equals(Level.END)) {
                return BATTLE_END_KEY;
            }
            if (from.equals(BATTLE_END_KEY) && to.equals(Level.OVERWORLD)) {
                return BATTLE_OVERWORLD_KEY;
            }
            return null;
        }

        if (from.equals(LOBBY_OVERWORLD_KEY) && (to.equals(Level.NETHER) || to.equals(Level.END))) {
            return LOBBY_OVERWORLD_KEY;
        }
        return null;
    }

    private BlockPos portalTargetPos(ResourceKey<Level> from, ResourceKey<Level> target, BlockPos sourcePos, ServerLevel targetWorld) {
        if (target.equals(LOBBY_OVERWORLD_KEY)) {
            return LOBBY_SPAWN_POS;
        }
        if (target.equals(BATTLE_END_KEY)) {
            return randomEndSpawn(targetWorld);
        }

        int x = sourcePos.getX();
        int z = sourcePos.getZ();

        if ((from.equals(BATTLE_OVERWORLD_KEY) || from.equals(LOBBY_OVERWORLD_KEY)) && target.equals(BATTLE_NETHER_KEY)) {
            x = sourcePos.getX() / 8;
            z = sourcePos.getZ() / 8;
        } else if (from.equals(BATTLE_NETHER_KEY) && target.equals(BATTLE_OVERWORLD_KEY)) {
            x = sourcePos.getX() * 8;
            z = sourcePos.getZ() * 8;
        }

        return safeSurface(targetWorld, x, z);
    }

    private Map<Integer, SpawnPoint> resolveTeamSpawns(ServerLevel world, BlockPos center, int teams) {
        Map<Integer, SpawnPoint> resolved = new HashMap<>();
        List<BlockPos> chosen = new ArrayList<>();

        for (int team = 1; team <= teams; team++) {
            BlockPos candidate = null;
            int minDistance = MIN_TEAM_DISTANCE;
            for (int relax = 0; relax < 8 && candidate == null; relax++) {
                candidate = findRandomTeamBase(world, center, chosen, minDistance);
                minDistance = Math.max(MIN_TEAM_DISTANCE_FLOOR, (int) Math.floor(minDistance * 0.85D));
            }
            if (candidate == null) {
                candidate = fallbackRandomTeamBase(world, center);
            }
            chosen.add(candidate);
            resolved.put(team, new SpawnPoint(world.dimension(), candidate));
        }
        return resolved;
    }

    private BlockPos findRandomTeamBase(ServerLevel world, BlockPos center, List<BlockPos> chosen, int minDistance) {
        for (int attempt = 0; attempt < SPAWN_SEARCH_ATTEMPTS; attempt++) {
            BlockPos flat = randomPointInRadius(center, RANDOM_SPAWN_RADIUS);
            if (!isFarEnough(flat, chosen, minDistance)) {
                continue;
            }
            return safeSurface(world, flat.getX(), flat.getZ());
        }
        return null;
    }

    private BlockPos fallbackRandomTeamBase(ServerLevel world, BlockPos center) {
        BlockPos flat = randomPointInRadius(center, RANDOM_SPAWN_RADIUS);
        return safeSurface(world, flat.getX(), flat.getZ());
    }

    private BlockPos randomPointInRadius(BlockPos center, int radius) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
        return new BlockPos(x, center.getY(), z);
    }

    private boolean isFarEnough(BlockPos candidate, List<BlockPos> chosen, int minDistance) {
        long minDistanceSq = (long) minDistance * minDistance;
        for (BlockPos existing : chosen) {
            long dx = candidate.getX() - existing.getX();
            long dz = candidate.getZ() - existing.getZ();
            long distSq = (dx * dx) + (dz * dz);
            if (distSq < minDistanceSq) {
                return false;
            }
        }
        return true;
    }

    private SpawnPoint randomizeAround(SpawnPoint base, MinecraftServer server) {
        int dx = random.nextInt((TEAM_SPAWN_SPREAD * 2) + 1) - TEAM_SPAWN_SPREAD;
        int dz = random.nextInt((TEAM_SPAWN_SPREAD * 2) + 1) - TEAM_SPAWN_SPREAD;
        BlockPos offset = base.pos().offset(dx, 0, dz);

        ServerLevel world = server.getLevel(base.worldKey());
        if (world == null) {
            return base;
        }

        return new SpawnPoint(base.worldKey(), safeSurface(world, offset.getX(), offset.getZ()));
    }

    private SpawnPoint resolveRespawn(int team, MinecraftServer server) {
        SpawnPoint bed = teamBeds.get(team);
        if (bed != null) {
            ServerLevel bedWorld = server.getLevel(bed.worldKey());
            if (bedWorld != null && bedWorld.getBlockState(bed.pos()).is(BlockTags.BEDS)) {
                return new SpawnPoint(bed.worldKey(), safeSurface(bedWorld, bed.pos().getX(), bed.pos().getZ()));
            }
            teamBeds.remove(team);
        }

        SpawnPoint spawn = teamSpawns.get(team);
        if (spawn == null) {
            return null;
        }
        ServerLevel world = server.getLevel(spawn.worldKey());
        if (world == null) {
            return null;
        }
        return new SpawnPoint(spawn.worldKey(), safeSurface(world, spawn.pos().getX(), spawn.pos().getZ()));
    }

    private boolean hasActiveTeamBedOrPendingPlacement(int team, MinecraftServer server) {
        SpawnPoint existing = teamBeds.get(team);
        if (existing != null) {
            if (server != null) {
                ServerLevel bedWorld = server.getLevel(existing.worldKey());
                if (bedWorld != null && bedWorld.getBlockState(existing.pos()).is(BlockTags.BEDS)) {
                    return true;
                }
            }
            removeTeamBedAt(existing);
        }

        for (PendingBedPlacement pending : pendingBedPlacements) {
            if (pending.team() == team) {
                return true;
            }
        }
        return false;
    }

    private void queuePendingRespawn(ServerPlayer player, int team, MinecraftServer server) {
        if (player == null || server == null) {
            return;
        }
        pendingMatchObservations.remove(player.getUUID());
        pendingRespawns.put(player.getUUID(), new PendingRespawn(team, RESPAWN_COUNTDOWN_TICKS, RESPAWN_COUNTDOWN_SECONDS));
        player.setGameMode(GameType.SPECTATOR);
        teleport(player, resolveSpectatorSpawn(team, server));
        sendOverlay(player, text("respawn.wait", RESPAWN_COUNTDOWN_SECONDS));
    }

    private void beginMatchObservation(ServerPlayer player, SpawnPoint spawnPoint) {
        if (player == null || spawnPoint == null) {
            return;
        }
        pendingMatchObservations.put(player.getUUID(), new PendingMatchObservation(spawnPoint, MATCH_OBSERVATION_TICKS, MATCH_OBSERVATION_SECONDS));
    }

    private void completeMatchObservation(ServerPlayer player) {
        if (player == null || !engine.isRunning()) {
            return;
        }
        playSound(player, "minecraft:entity.player.levelup", 1.2F);
        sendOverlay(player, text("match.observe.ready"));
    }

    private void completePendingRespawn(ServerPlayer player, int team, MinecraftServer server) {
        if (player == null || server == null || !engine.isRunning()) {
            return;
        }
        SpawnPoint respawn = resolveRespawn(team, server);
        if (respawn == null) {
            return;
        }
        teleport(player, respawn);
        applyHealthPreset(player);
        ensureSupplyBoat(player);
        player.setGameMode(GameType.SURVIVAL);
        grantRespawnInvulnerability(player);
        sendOverlay(player, text("respawn.ready"));
    }

    private Integer pendingRespawnSeconds(ServerPlayer player, MinecraftServer server) {
        if (player == null || server == null) {
            return null;
        }
        PendingRespawn pending = pendingRespawns.get(player.getUUID());
        if (pending == null) {
            return null;
        }
        return Math.max(1, (pending.remainingTicks() + 19) / 20);
    }

    private Integer pendingMatchObservationSeconds(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        PendingMatchObservation pending = pendingMatchObservations.get(player.getUUID());
        if (pending == null) {
            return null;
        }
        return Math.max(1, (pending.remainingTicks() + 19) / 20);
    }

    private MinecraftServer serverOf(ServerPlayer player) {
        return player.createCommandSourceStack().getServer();
    }

    @SuppressWarnings("resource")
    private void teleport(ServerPlayer player, SpawnPoint point) {
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }

        ServerLevel targetWorld = server.getLevel(point.worldKey());
        if (targetWorld == null) {
            return;
        }

        player.teleportTo(targetWorld, point.pos().getX() + 0.5D, point.pos().getY(), point.pos().getZ() + 0.5D, Set.of(), player.getYRot(), player.getXRot(), false);
    }

    private void enforceObservationLock(ServerPlayer player, SpawnPoint spawnPoint) {
        if (player == null || spawnPoint == null) {
            return;
        }

        double targetX = spawnPoint.pos().getX() + 0.5D;
        double targetY = spawnPoint.pos().getY();
        double targetZ = spawnPoint.pos().getZ() + 0.5D;
        boolean moved = Math.abs(player.getX() - targetX) > 1.0E-4D
                || Math.abs(player.getY() - targetY) > 1.0E-4D
                || Math.abs(player.getZ() - targetZ) > 1.0E-4D;
        if (!player.level().dimension().equals(spawnPoint.worldKey()) || moved) {
            teleport(player, spawnPoint);
        }
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    private BlockPos randomEndSpawn(ServerLevel world) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int radius = END_SPAWN_MIN_RADIUS + random.nextInt((END_SPAWN_MAX_RADIUS - END_SPAWN_MIN_RADIUS) + 1);
        int x = (int) Math.round(Math.cos(angle) * radius);
        int z = (int) Math.round(Math.sin(angle) * radius);
        return safeSurface(world, x, z);
    }

    private BlockPos safeSurface(ServerLevel world, int x, int z) {
        BlockPos direct = findSafeSurfaceAt(world, x, z);
        if (direct != null) {
            return direct;
        }

        for (int radius = 1; radius <= SAFE_SPAWN_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    BlockPos candidate = findSafeSurfaceAt(world, x + dx, z + dz);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        }

        if (world.dimension().equals(Level.NETHER) || world.dimension().equals(BATTLE_NETHER_KEY)) {
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
            return new BlockPos(x, y, z);
        }
        return createFallbackSpawnPlatform(world, x, z);
    }

    private BlockPos findSafeSurfaceAt(ServerLevel world, int x, int z) {
        int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int offset = -3; offset <= 3; offset++) {
            BlockPos candidate = new BlockPos(x, topY + 1 + offset, z);
            if (isSafeSpawnLocation(world, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isSafeSpawnLocation(ServerLevel world, BlockPos candidate) {
        BlockPos groundPos = candidate.below();
        BlockState ground = world.getBlockState(groundPos);
        BlockState feet = world.getBlockState(candidate);
        BlockState head = world.getBlockState(candidate.above());
        if (ground.isAir() || isHazardousGround(ground) || ground.getCollisionShape(world, groundPos).isEmpty() || !isAllowedSpawnGround(world, ground)) {
            return false;
        }
        if (!feet.isAir() || !head.isAir()) {
            return false;
        }
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty();
    }

    private boolean isAllowedSpawnGround(ServerLevel world, BlockState ground) {
        ResourceKey<Level> worldKey = world.dimension();
        if (worldKey.equals(Level.END) || worldKey.equals(BATTLE_END_KEY)) {
            return ground.is(Blocks.END_STONE);
        }
        if (worldKey.equals(Level.OVERWORLD) || worldKey.equals(LOBBY_OVERWORLD_KEY) || worldKey.equals(BATTLE_OVERWORLD_KEY)) {
            return ground.is(Blocks.GRASS_BLOCK);
        }
        return !ground.is(BlockTags.LEAVES);
    }

    private BlockPos createFallbackSpawnPlatform(ServerLevel world, int x, int z) {
        int feetY = Math.max(world.getSeaLevel() + 2, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1);
        int platformY = feetY - 1;
        BlockState topState = fallbackPlatformTopState(world);
        BlockState foundationState = topState.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : topState;

        for (int dx = -FORCED_PLATFORM_RADIUS; dx <= FORCED_PLATFORM_RADIUS; dx++) {
            for (int dz = -FORCED_PLATFORM_RADIUS; dz <= FORCED_PLATFORM_RADIUS; dz++) {
                for (int depth = 1; depth <= FORCED_PLATFORM_FOUNDATION_DEPTH; depth++) {
                    world.setBlock(new BlockPos(x + dx, platformY - depth, z + dz), foundationState, Block.UPDATE_ALL);
                }
                world.setBlock(new BlockPos(x + dx, platformY, z + dz), topState, Block.UPDATE_ALL);
                for (int clearance = 1; clearance <= FORCED_PLATFORM_CLEARANCE; clearance++) {
                    world.setBlock(new BlockPos(x + dx, platformY + clearance, z + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        return new BlockPos(x, platformY + 1, z);
    }

    private BlockState fallbackPlatformTopState(ServerLevel world) {
        ResourceKey<Level> worldKey = world.dimension();
        if (worldKey.equals(Level.END) || worldKey.equals(BATTLE_END_KEY)) {
            return Blocks.END_STONE.defaultBlockState();
        }
        if (worldKey.equals(Level.OVERWORLD) || worldKey.equals(LOBBY_OVERWORLD_KEY) || worldKey.equals(BATTLE_OVERWORLD_KEY)) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private boolean isHazardousGround(BlockState state) {
        return state.is(Blocks.WATER)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.COBWEB)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(BlockTags.LEAVES)
                || !state.getFluidState().isEmpty();
    }

    private void sendOverlay(ServerPlayer player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        sendPlayerMessage(player, Component.literal(message), true);
    }

    private void sendPlayerMessage(ServerPlayer player, Component message, boolean overlay) {
        if (player == null || message == null) {
            return;
        }
        player.sendSystemMessage(message, overlay);
    }

    private void broadcastOverlay(MinecraftServer server, String message) {
        if (server == null || message == null || message.isBlank()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendOverlay(player, message);
        }
    }

    private void playReadyCountdownSound(MinecraftServer server, int secondsRemaining) {
        if (server == null) {
            return;
        }
        float pitch = 1.0F + ((5 - secondsRemaining) * 0.1F);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            playSound(player, "minecraft:block.note_block.hat", pitch);
        }
    }

    private void playSound(ServerPlayer player, String soundId, float pitch) {
        if (player == null || soundId == null || soundId.isBlank()) {
            return;
        }
        var sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(soundId));
        if (sound == null) {
            return;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.9F, pitch);
    }

    private void setImportantNotice(ServerPlayer player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        importantNotices.put(player.getUUID(), new TimedNotice(message, IMPORTANT_NOTICE_TICKS));
        sendOverlay(player, message);
    }

    private void broadcastImportantNotice(MinecraftServer server, String message) {
        if (server == null || message == null || message.isBlank()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            setImportantNotice(player, message);
        }
    }

    private void notifyTeamWiped(MinecraftServer server, int team, List<UUID> victims) {
        if (server == null || victims == null || victims.isEmpty()) {
            return;
        }
        String message = text("match.life_bind_wipe", teamLabel(team));
        for (UUID victimId : victims) {
            ServerPlayer player = server.getPlayerList().getPlayer(victimId);
            if (player == null) {
                continue;
            }
            setImportantNotice(player, message);
        }
    }

    String text(String key, Object... args) {
        TeamLifeBindLanguage current = language;
        return current != null ? current.text(key, args) : key;
    }

    private String teamLabel(int team) {
        TeamLifeBindLanguage current = language;
        return current != null ? current.teamLabel(team) : "Team #" + team;
    }

    private String healthPresetLabel(HealthPreset preset) {
        TeamLifeBindLanguage current = language;
        return current != null ? current.healthPresetName(preset) : preset.name();
    }

    private int stepTeamCount(int current, boolean decrement) {
        if (decrement) {
            return Math.max(2, current - 1);
        }
        return Math.min(32, current + 1);
    }

    private HealthPreset stepHealthPreset(HealthPreset current, boolean decrement) {
        HealthPreset[] presets = HealthPreset.values();
        int offset = decrement ? -1 : 1;
        return presets[Math.floorMod(current.ordinal() + offset, presets.length)];
    }

    private String stateLabel(boolean enabled) {
        TeamLifeBindLanguage current = language;
        return current != null ? current.state(enabled) : Boolean.toString(enabled);
    }

    private String bedStatusText(boolean placed) {
        return text(placed ? "scoreboard.bed.present" : "scoreboard.bed.missing");
    }

    private String respawnStatusText(ServerPlayer player, MinecraftServer server) {
        Integer countdown = pendingRespawnSeconds(player, server);
        if (countdown != null) {
            return text("scoreboard.respawn.countdown", countdown);
        }
        if (isRespawnLocked(player)) {
            return text("scoreboard.respawn.locked");
        }
        return text("scoreboard.respawn.ready");
    }

    private String dimensionLabel(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel world)) {
            return text("scoreboard.dimension.unknown");
        }
        ResourceKey<Level> worldKey = world.dimension();
        if (worldKey.equals(Level.NETHER) || worldKey.equals(BATTLE_NETHER_KEY)) {
            return text("scoreboard.dimension.nether");
        }
        if (worldKey.equals(Level.END) || worldKey.equals(BATTLE_END_KEY)) {
            return text("scoreboard.dimension.end");
        }
        if (worldKey.equals(Level.OVERWORLD) || worldKey.equals(LOBBY_OVERWORLD_KEY) || worldKey.equals(BATTLE_OVERWORLD_KEY)) {
            return text("scoreboard.dimension.overworld");
        }
        return text("scoreboard.dimension.unknown");
    }

    public void openLobbyMenu(ServerPlayer player) {
        if (player == null) {
            return;
        }
        SimpleContainer inventory = new SimpleContainer(LOBBY_MENU_SIZE);
        populateLobbyMenuInventory(inventory, player);
        player.openMenu(
                new SimpleMenuProvider(
                        (syncId, playerInventory, ignored) -> new LobbyMenuScreenHandler(syncId, playerInventory, inventory),
                        Component.literal(text("menu.inventory.title")).withStyle(ChatFormatting.DARK_AQUA)
                )
        );
    }

    public boolean isLobbyMenuItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        return LOBBY_MENU_TAG.equals(customData.copyTag().getString(LOBBY_ITEM_TAG).orElse(""));
    }

    private boolean isProtectedLobbyItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        return !customData.copyTag().getString(LOBBY_ITEM_TAG).orElse("").isBlank();
    }

    void bindCraftedTeamBed(ItemStack stack, UUID crafterId) {
        Integer team = crafterId == null ? null : engine.teamForPlayer(crafterId);
        if (team == null) {
            clearTeamBedOwner(stack);
            stack.setCount(2);
            return;
        }
        setTeamBedOwner(stack, team);
        stack.setCount(2);
    }

    private void preparePlayerForLobby(ServerPlayer player) {
        resetPlayerState(player, true);
        giveLobbyItems(player);
    }

    private void preparePlayerForMatch(ServerPlayer player) {
        resetPlayerState(player, false);
        giveMatchStarterItems(player);
    }

    private void preparePlayerForSpectator(ServerPlayer player) {
        resetPlayerState(player, false);
        player.setGameMode(GameType.SPECTATOR);
    }

    private void resetPlayerState(ServerPlayer player, boolean lobbyState) {
        player.closeContainer();
        clearRespawnInvulnerability(player);
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.removeAllEffects();
        player.setExperiencePoints(0);
        player.setExperienceLevels(0);
        player.experienceProgress = 0.0F;
        player.setRemainingFireTicks(0);
        player.fallDistance = 0.0F;
        AttributeInstance maxHealthAttribute = maxHealthAttribute(player);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(20.0D);
        }
        player.setHealth(20.0F);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        player.setGameMode(lobbyState ? GameType.ADVENTURE : GameType.SURVIVAL);
    }

    private void giveLobbyItems(ServerPlayer player) {
        player.getInventory().setItem(LOBBY_MENU_SLOT, createLobbyMenuItem());
        player.getInventory().setItem(LOBBY_GUIDE_SLOT, createWrittenBookItem("item.guide.name", "book.guide.title", "book.guide.author", "book.guide.page1", "book.guide.page2", LOBBY_GUIDE_TAG));
        player.getInventory().setItem(LOBBY_RECIPES_SLOT, createWrittenBookItem("item.recipes.name", "book.recipes.title", "book.recipes.author", "book.recipes.page1", "book.recipes.page2", LOBBY_RECIPES_TAG));
    }

    private void giveMatchStarterItems(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
        inventory.setItem(1, new ItemStack(Items.WOODEN_PICKAXE));
        inventory.setItem(2, new ItemStack(Items.WOODEN_AXE));
        inventory.setItem(3, createSupplyBoatItem());
    }

    private ItemStack createLobbyMenuItem() {
        ItemStack item = new ItemStack(Items.COMPASS);
        item.set(DataComponents.CUSTOM_NAME, Component.literal(text("item.menu.name")).withStyle(ChatFormatting.AQUA));
        item.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(text("item.menu.lore")).withStyle(ChatFormatting.GRAY))));
        return markAsLobbyItem(item, LOBBY_MENU_TAG);
    }

    private ItemStack createSupplyBoatItem() {
        ItemStack item = new ItemStack(Items.OAK_BOAT);
        CustomData customData = item.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();
        tag.putBoolean(SUPPLY_BOAT_TAG, true);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return item;
    }

    private boolean isSupplyBoatItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.OAK_BOAT)) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().getBoolean(SUPPLY_BOAT_TAG).orElse(false);
    }

    private boolean isDiscardProtectedBoatItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.endsWith("_boat") || path.endsWith("_raft");
    }

    private void ensureSupplyBoat(ServerPlayer player) {
        if (player == null || !engine.isRunning()) {
            return;
        }
        supplyBoatDropConfirmUntil.remove(player.getUUID());
        if (playerHasSupplyBoat(player)) {
            return;
        }
        giveSupplyBoatBack(player);
    }

    private boolean playerHasSupplyBoat(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isSupplyBoatItem(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private void giveSupplyBoatBack(ServerPlayer player) {
        giveBoatBack(player, createSupplyBoatItem());
    }

    private void giveBoatBack(ServerPlayer player, ItemStack stack) {
        if (player == null) {
            return;
        }
        player.getInventory().add(stack.copy());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    private ItemStack createOwnedTeamBedItem(int ownerTeam) {
        ItemStack item = new ItemStack(TeamLifeBindFabric.TEAM_BED_ITEM);
        setTeamBedOwner(item, ownerTeam);
        return item;
    }

    private void normalizeTeamBedInventories(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            normalizeTeamBedInventory(player);
        }
    }

    private void normalizeTeamBedInventory(ServerPlayer player) {
        if (player == null || player.isCreative()) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(TeamLifeBindFabric.TEAM_BED_ITEM) && stack.getCount() == 1) {
                stack.setCount(2);
            }
        }
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    private void consumeResidualDroppedTeamBedToken(ServerPlayer player, ItemStack droppedStack) {
        if (player == null || player.isCreative() || droppedStack == null || !droppedStack.is(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            return;
        }
        Integer ownerTeam = resolveTeamBedOwner(droppedStack);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isResidualDroppedTeamBedStack(stack, ownerTeam)) {
                continue;
            }
            if (stack.getCount() > 1) {
                stack.shrink(1);
            } else {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
            return;
        }
    }

    private void consumeAllTeamBedTokens(ServerPlayer player, ItemStack droppedStack) {
        if (player == null || player.isCreative() || droppedStack == null || !droppedStack.is(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            return;
        }
        Integer ownerTeam = resolveTeamBedOwner(droppedStack);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(TeamLifeBindFabric.TEAM_BED_ITEM)) {
                continue;
            }
            Integer stackOwnerTeam = resolveTeamBedOwner(stack);
            if (sameTeamBedOwner(stackOwnerTeam, ownerTeam)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    private boolean isResidualDroppedTeamBedStack(ItemStack stack, Integer ownerTeam) {
        return stack != null
                && !stack.isEmpty()
                && stack.is(TeamLifeBindFabric.TEAM_BED_ITEM)
                && stack.getCount() == 1
                && sameTeamBedOwner(resolveTeamBedOwner(stack), ownerTeam);
    }

    private boolean sameTeamBedOwner(Integer left, Integer right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private boolean isPendingTeamBedPlacementStack(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }
        for (PendingBedPlacement pending : pendingBedPlacements) {
            if (!pending.ownerId().equals(player.getUUID())) {
                continue;
            }
            if (stack == player.getItemInHand(pending.hand())) {
                return true;
            }
        }
        return false;
    }

    private void consumeResidualPlacedTeamBedToken(ServerPlayer player, InteractionHand hand) {
        if (player == null || player.isCreative()) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            return;
        }
        stack.shrink(1);
    }

    @SuppressWarnings("resource")
    private void grantRespawnInvulnerability(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        player.setInvulnerable(true);
        respawnInvulnerableUntil.put(player.getUUID(), overworld.getGameTime() + RESPAWN_INVULNERABILITY_TICKS);
    }

    private void processRespawnInvulnerability(MinecraftServer server) {
        if (server == null || respawnInvulnerableUntil.isEmpty()) {
            return;
        }
        long tick = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Long>> iterator = respawnInvulnerableUntil.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() > tick) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.setInvulnerable(false);
            }
            iterator.remove();
        }
    }

    private void clearRespawnInvulnerability(ServerPlayer player) {
        if (player == null) {
            return;
        }
        respawnInvulnerableUntil.remove(player.getUUID());
        player.setInvulnerable(false);
    }

    private ItemStack createWrittenBookItem(String nameKey, String titleKey, String authorKey, String pageOneKey, String pageTwoKey, String lobbyItemTag) {
        ItemStack item = new ItemStack(Items.WRITTEN_BOOK);
        item.set(DataComponents.CUSTOM_NAME, Component.literal(text(nameKey)).withStyle(ChatFormatting.GOLD));
        item.set(
                DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(
                        Filterable.passThrough(text(titleKey)),
                        text(authorKey),
                        0,
                        List.of(Filterable.passThrough(Component.literal(text(pageOneKey))), Filterable.passThrough(Component.literal(text(pageTwoKey)))),
                        true
                )
        );
        return markAsLobbyItem(item, lobbyItemTag);
    }

    private ItemStack markAsLobbyItem(ItemStack item, String lobbyItemTag) {
        CustomData customData = item.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();
        tag.putString(LOBBY_ITEM_TAG, lobbyItemTag);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return item;
    }

    private void giveLobbyItemBack(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        player.getInventory().add(stack.copy());
        player.inventoryMenu.broadcastChanges();
    }


    private void populateLobbyMenuInventory(SimpleContainer inventory, ServerPlayer player) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, ItemStack.EMPTY);
        }

        boolean ready = readyPlayers.contains(player.getUUID());
        boolean canManage = canManageMatch(player);

        inventory.setItem(
                LOBBY_MENU_PRIMARY_SLOT,
                createMenuButton(
                        ready ? MENU_ACTION_UNREADY : MENU_ACTION_READY,
                        ready ? Items.RED_WOOL : Items.LIME_WOOL,
                        ready ? text("menu.button.unready") : text("menu.button.ready"),
                        List.of(Component.literal(text("menu.info.status", text(engine.isRunning() ? "scoreboard.state.running" : "scoreboard.state.lobby"))).withStyle(ChatFormatting.GRAY))
                )
        );
        inventory.setItem(
                LOBBY_MENU_STATUS_SLOT,
                createMenuButton(
                        MENU_ACTION_STATUS,
                        Items.CLOCK,
                        text("menu.button.status"),
                        List.of(
                                Component.literal(text("menu.info.status", text(engine.isRunning() ? "scoreboard.state.running" : "scoreboard.state.lobby"))).withStyle(ChatFormatting.YELLOW),
                                Component.literal(text("menu.info.team", resolvePlayerTeamLabel(player))).withStyle(ChatFormatting.AQUA)
                        )
                )
        );
        inventory.setItem(
                LOBBY_MENU_HELP_SLOT,
                createMenuButton(MENU_ACTION_HELP, Items.BOOK, text("menu.button.help"), List.of(Component.literal(text("menu.info.tip")).withStyle(ChatFormatting.GRAY)))
        );

        if (canManage) {
            inventory.setItem(
                    LOBBY_MENU_HEALTH_SYNC_SLOT,
                    createMenuButton(
                            MENU_ACTION_HEALTH_SYNC,
                            healthSyncEnabled ? Items.LIME_DYE : Items.GRAY_DYE,
                            text("menu.button.health_sync"),
                            List.of(
                                    Component.literal(text("menu.value.toggle", stateLabel(healthSyncEnabled))).withStyle(ChatFormatting.YELLOW),
                                    Component.literal(text("menu.action.toggle")).withStyle(ChatFormatting.GRAY)
                            )
                    )
            );
            inventory.setItem(
                    LOBBY_MENU_TAB_SLOT,
                    createMenuButton(
                            MENU_ACTION_TAB,
                            tabEnabled ? Items.COMPASS : Items.GRAY_DYE,
                            text("menu.button.tab"),
                            List.of(
                                    Component.literal(text("menu.value.toggle", stateLabel(tabEnabled))).withStyle(ChatFormatting.YELLOW),
                                    Component.literal(text("menu.action.toggle")).withStyle(ChatFormatting.GRAY)
                            )
                    )
            );
            inventory.setItem(
                    LOBBY_MENU_TEAMS_SLOT,
                    createMenuButton(
                            MENU_ACTION_TEAMS,
                            Items.CYAN_WOOL,
                            text("menu.button.teams"),
                            List.of(
                                    Component.literal(text("menu.value.team_count", teamCount)).withStyle(ChatFormatting.YELLOW),
                                    Component.literal(text("menu.action.adjust_number")).withStyle(ChatFormatting.GRAY)
                            )
                    )
            );
            inventory.setItem(
                    LOBBY_MENU_HEALTH_SLOT,
                    createMenuButton(
                            MENU_ACTION_HEALTH,
                            Items.GOLDEN_APPLE,
                            text("menu.button.health"),
                            List.of(
                                    Component.literal(text("menu.value.health", healthPresetLabel(healthPreset))).withStyle(ChatFormatting.YELLOW),
                                    Component.literal(text("menu.action.adjust_cycle")).withStyle(ChatFormatting.GRAY)
                            )
                    )
            );
            inventory.setItem(
                    LOBBY_MENU_NORESPAWN_SLOT,
                    createMenuButton(
                            MENU_ACTION_NORESPAWN,
                            noRespawnEnabled ? Items.EMERALD_BLOCK : Items.REDSTONE_BLOCK,
                            text("menu.button.norespawn"),
                            List.of(
                                    Component.literal(text("menu.value.toggle", stateLabel(noRespawnEnabled))).withStyle(ChatFormatting.YELLOW),
                                    Component.literal(text("menu.action.toggle")).withStyle(ChatFormatting.GRAY)
                            )
                    )
            );
            inventory.setItem(
                    LOBBY_MENU_ANNOUNCE_TEAMS_SLOT,
                    createMenuButton(
                            MENU_ACTION_ANNOUNCE_TEAMS,
                            announceTeamAssignment ? Items.NAME_TAG : Items.PAPER,
                            text("menu.button.announce_teams"),
                            List.of(
                                    Component.literal(text("menu.value.toggle", stateLabel(announceTeamAssignment))).withStyle(ChatFormatting.YELLOW),
                                    Component.literal(text("menu.action.toggle")).withStyle(ChatFormatting.GRAY)
                            )
                    )
            );
            inventory.setItem(
                    LOBBY_MENU_SCOREBOARD_SLOT,
                    createMenuButton(
                            MENU_ACTION_SCOREBOARD,
                            scoreboardEnabled ? Items.MAP : Items.GRAY_DYE,
                            text("menu.button.scoreboard"),
                            List.of(
                                    Component.literal(text("menu.value.toggle", stateLabel(scoreboardEnabled))).withStyle(ChatFormatting.YELLOW),
                                    Component.literal(text("menu.action.toggle")).withStyle(ChatFormatting.GRAY)
                            )
                    )
            );
            inventory.setItem(
                    LOBBY_MENU_START_STOP_SLOT,
                    createMenuButton(
                            hasActiveMatchSession() ? MENU_ACTION_STOP : MENU_ACTION_START,
                            hasActiveMatchSession() ? Items.BARRIER : Items.DIAMOND_SWORD,
                            hasActiveMatchSession() ? text("menu.button.stop") : text("menu.button.start"),
                            List.of(
                                    Component.literal(text("menu.info.status", text(engine.isRunning() ? "scoreboard.state.running" : "scoreboard.state.lobby"))).withStyle(ChatFormatting.GRAY)
                            )
                    )
            );
            inventory.setItem(
                    LOBBY_MENU_RELOAD_SLOT,
                    createMenuButton(
                            MENU_ACTION_RELOAD,
                            Items.REPEATER,
                            text("menu.button.reload"),
                            List.of(Component.literal(text("command.reload.success")).withStyle(ChatFormatting.GRAY))
                    )
            );
            inventory.setItem(
                    LOBBY_MENU_ADVANCEMENTS_SLOT,
                    createMenuButton(
                            MENU_ACTION_ADVANCEMENTS,
                            advancementsEnabled ? Items.EXPERIENCE_BOTTLE : Items.GLASS_BOTTLE,
                            text("menu.button.advancements"),
                            List.of(
                                    Component.literal(text("menu.value.toggle", stateLabel(advancementsEnabled))).withStyle(ChatFormatting.YELLOW),
                                    Component.literal(text("menu.action.toggle")).withStyle(ChatFormatting.GRAY)
                            )
                    )
            );
        }
    }

    private ItemStack createMenuButton(String action, net.minecraft.world.item.Item item, String label, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(label).withStyle(ChatFormatting.GOLD));
        if (lore != null && !lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(LOBBY_MENU_ACTION_TAG, action);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private String resolveMenuAction(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        String action = customData.copyTag().getString(LOBBY_MENU_ACTION_TAG).orElse("");
        return action.isBlank() ? null : action;
    }

    private void setTeamBedOwner(ItemStack stack, int ownerTeam) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();
        tag.putInt(TEAM_BED_OWNER_TAG, ownerTeam);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        updateTeamBedDisplay(stack, ownerTeam);
    }

    private void clearTeamBedOwner(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        CompoundTag tag = customData.copyTag();
        tag.remove(TEAM_BED_OWNER_TAG);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        updateTeamBedDisplay(stack, null);
    }

    private Integer resolveTeamBedOwner(ItemStack stack) {
        if (stack == null || !stack.is(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            return null;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        return customData.copyTag().getInt(TEAM_BED_OWNER_TAG).orElse(null);
    }

    private void updateTeamBedDisplay(ItemStack stack, Integer ownerTeam) {
        if (stack == null || !stack.is(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            return;
        }
        MutableComponent displayName = Component.translatable("item.teamlifebind.team_bed");
        if (ownerTeam != null) {
            displayName = displayName.append(Component.literal(" [" + teamLabel(ownerTeam) + "]"));
        }
        stack.set(DataComponents.CUSTOM_NAME, displayName.withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.team_bed.lore").withStyle(ChatFormatting.GRAY))));
    }

    private void handleLobbyMenuAction(ServerPlayer player, String action, SimpleContainer inventory, boolean decrement) {
        if (player == null || action == null) {
            return;
        }

        switch (action) {
            case MENU_ACTION_READY -> markReady(player);
            case MENU_ACTION_UNREADY -> unready(player);
            case MENU_ACTION_STATUS -> sendPlayerMessage(player, Component.literal(status(serverOf(player))), false);
            case MENU_ACTION_HELP -> sendCommandHelp(player);
            case MENU_ACTION_TEAMS -> {
                if (!canManageMatch(player)) {
                    sendPlayerMessage(player, Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED), false);
                } else if (hasActiveMatchSession()) {
                    sendPlayerMessage(player, Component.literal(text("command.team_count.running")).withStyle(ChatFormatting.RED), false);
                } else {
                    int updatedCount = stepTeamCount(teamCount, decrement);
                    setTeamCount(updatedCount);
                    sendPlayerMessage(player, Component.literal(text("command.team_count.updated", updatedCount)).withStyle(ChatFormatting.GREEN), false);
                }
            }
            case MENU_ACTION_HEALTH -> {
                if (!canManageMatch(player)) {
                    sendPlayerMessage(player, Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED), false);
                } else if (hasActiveMatchSession()) {
                    sendPlayerMessage(player, Component.literal(text("command.health.running")).withStyle(ChatFormatting.RED), false);
                } else {
                    HealthPreset next = stepHealthPreset(healthPreset, decrement);
                    setHealthPreset(next);
                    sendPlayerMessage(player, Component.literal(text("command.health.updated", healthPresetLabel(next))).withStyle(ChatFormatting.GREEN), false);
                }
            }
            case MENU_ACTION_HEALTH_SYNC -> {
                if (!canManageMatch(player)) {
                    sendPlayerMessage(player, Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED), false);
                } else {
                    boolean enabled = !healthSyncEnabled;
                    setHealthSyncEnabled(enabled);
                    sendPlayerMessage(
                            player,
                            Component.literal(text(enabled ? "command.healthsync.enabled" : "command.healthsync.disabled")).withStyle(ChatFormatting.GREEN),
                            false
                    );
                }
            }
            case MENU_ACTION_NORESPAWN -> {
                if (!canManageMatch(player)) {
                    sendPlayerMessage(player, Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED), false);
                } else {
                    boolean enabled = !noRespawnEnabled;
                    setNoRespawnEnabled(enabled);
                    sendPlayerMessage(
                            player,
                            Component.literal(text(enabled ? "command.norespawn.enabled" : "command.norespawn.disabled")).withStyle(ChatFormatting.GREEN),
                            false
                    );
                }
            }
            case MENU_ACTION_ANNOUNCE_TEAMS -> {
                if (!canManageMatch(player)) {
                    sendPlayerMessage(player, Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED), false);
                } else {
                    boolean enabled = !announceTeamAssignment;
                    setAnnounceTeamAssignment(enabled);
                    sendPlayerMessage(
                            player,
                            Component.literal(text("config.announce_team_assignment.updated", stateLabel(enabled))).withStyle(ChatFormatting.GREEN),
                            false
                    );
                }
            }
            case MENU_ACTION_SCOREBOARD -> {
                if (!canManageMatch(player)) {
                    sendPlayerMessage(player, Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED), false);
                } else {
                    boolean enabled = !scoreboardEnabled;
                    setScoreboardEnabled(enabled);
                    sendPlayerMessage(player, Component.literal(text("config.scoreboard.updated", stateLabel(enabled))).withStyle(ChatFormatting.GREEN), false);
                }
            }
            case MENU_ACTION_ADVANCEMENTS -> {
                if (!canManageMatch(player)) {
                    sendPlayerMessage(player, Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED), false);
                } else {
                    boolean enabled = !advancementsEnabled;
                    setAdvancementsEnabled(enabled);
                    sendPlayerMessage(player, Component.literal(text("config.advancements.updated", stateLabel(enabled))).withStyle(ChatFormatting.GREEN), false);
                }
            }
            case MENU_ACTION_TAB -> {
                if (!canManageMatch(player)) {
                    sendPlayerMessage(player, Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED), false);
                } else {
                    boolean enabled = !tabEnabled;
                    setTabEnabled(enabled);
                    sendPlayerMessage(player, Component.literal(text("config.tab.updated", stateLabel(enabled))).withStyle(ChatFormatting.GREEN), false);
                }
            }
            case MENU_ACTION_START -> {
                if (!canManageMatch(player)) {
                    sendPlayerMessage(player, Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED), false);
                } else {
                    sendPlayerMessage(player, Component.literal(start(serverOf(player))), false);
                }
            }
            case MENU_ACTION_STOP -> {
                if (!canManageMatch(player)) {
                    sendPlayerMessage(player, Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED), false);
                } else {
                    sendPlayerMessage(player, Component.literal(stop(serverOf(player))), false);
                }
            }
            case MENU_ACTION_RELOAD -> {
                if (!canManageMatch(player)) {
                    sendPlayerMessage(player, Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED), false);
                } else {
                    loadPersistentSettings();
                    reloadLanguage();
                    applyAdvancementRule(activeServer);
                    refreshScoreboardState(activeServer);
                    refreshTabState(activeServer);
                    sendPlayerMessage(player, Component.literal(text("command.reload.success")).withStyle(ChatFormatting.GREEN), false);
                }
            }
            default -> {
                return;
            }
        }

        if (player.containerMenu instanceof LobbyMenuScreenHandler handler) {
            populateLobbyMenuInventory(inventory, player);
            handler.broadcastChanges();
        }
    }

    private void sendCommandHelp(ServerPlayer player) {
        if (player == null) {
            return;
        }
        for (String key : List.of(
                "command.help.title",
                "command.help.help",
                "command.help.menu",
                "command.help.ready",
                "command.help.unready",
                "command.help.start",
                "command.help.stop",
                "command.help.status",
                "command.help.teams",
                "command.help.health",
                "command.help.healthsync",
                "command.help.healthsync_toggle",
                "command.help.norespawn",
                "command.help.norespawn_toggle",
                "command.help.norespawn_add",
                "command.help.norespawn_remove",
                "command.help.norespawn_clear"
        )) {
            sendPlayerMessage(player, Component.literal(text(key)), false);
        }
    }

    private boolean canManageMatch(ServerPlayer player) {
        return player != null && player.canUseGameMasterBlocks();
    }

    private void reloadLanguage() {
        language = TeamLifeBindLanguage.load(
                FabricLoader.getInstance().getConfigDir().resolve("teamlifebind"),
                TeamLifeBindFabric.LOGGER::warn
        );
    }

    private void applyAdvancementRule(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ResourceKey<Level> key : Set.of(LOBBY_OVERWORLD_KEY, BATTLE_OVERWORLD_KEY, BATTLE_NETHER_KEY, BATTLE_END_KEY, Level.OVERWORLD, Level.NETHER, Level.END)) {
            ServerLevel world = server.getLevel(key);
            if (world != null) {
                world.getGameRules().set(GameRules.SHOW_ADVANCEMENT_MESSAGES, advancementsEnabled, server);
                world.getGameRules().set(GameRules.IMMEDIATE_RESPAWN, true, server);
            }
        }
    }

    private void loadPersistentSettings() {
        Properties properties = new Properties();
        Path path = settingsFilePath();
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                try (InputStream input = Files.newInputStream(path)) {
                    properties.load(input);
                }
            }
        } catch (IOException ex) {
            TeamLifeBindFabric.LOGGER.warn("Failed to load settings from {}", path, ex);
        }

        teamCount = Math.max(2, Math.min(32, parseTeamCount(properties.getProperty("team-count"))));
        healthPreset = HealthPreset.fromString(properties.getProperty("health-preset", HealthPreset.ONE_HEART.name()));
        announceTeamAssignment = Boolean.parseBoolean(properties.getProperty("announce-team-assignment", "true"));
        healthSyncEnabled = Boolean.parseBoolean(properties.getProperty("health-sync-enabled", "true"));
        noRespawnEnabled = Boolean.parseBoolean(properties.getProperty("no-respawn-enabled", "true"));
        scoreboardEnabled = Boolean.parseBoolean(properties.getProperty("scoreboard-enabled", "true"));
        tabEnabled = Boolean.parseBoolean(properties.getProperty("tab-enabled", "true"));
        advancementsEnabled = Boolean.parseBoolean(properties.getProperty("advancements-enabled", "false"));
        battleSeed = parseLong(properties.getProperty("battle-seed"), battleSeed);
        savePersistentSettings();
    }

    private void savePersistentSettings() {
        Path path = settingsFilePath();
        Properties properties = new Properties();
        properties.setProperty("team-count", String.valueOf(teamCount));
        properties.setProperty("health-preset", healthPreset.name());
        properties.setProperty("announce-team-assignment", String.valueOf(announceTeamAssignment));
        properties.setProperty("health-sync-enabled", String.valueOf(healthSyncEnabled));
        properties.setProperty("no-respawn-enabled", String.valueOf(noRespawnEnabled));
        properties.setProperty("scoreboard-enabled", String.valueOf(scoreboardEnabled));
        properties.setProperty("tab-enabled", String.valueOf(tabEnabled));
        properties.setProperty("advancements-enabled", String.valueOf(advancementsEnabled));
        properties.setProperty("battle-seed", String.valueOf(battleSeed));
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "TeamLifeBind gameplay settings");
            }
        } catch (IOException ex) {
            TeamLifeBindFabric.LOGGER.warn("Failed to save settings to {}", path, ex);
        }
    }

    private Path settingsFilePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("teamlifebind").resolve("settings.properties");
    }

    private int parseTeamCount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 2;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return 2;
        }
    }

    private long parseLong(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Nullable
    private AttributeInstance maxHealthAttribute(ServerPlayer player) {
        return player == null ? null : player.getAttribute(Attributes.MAX_HEALTH);
    }

    @Nullable
    private String statusEffectKey(MobEffectInstance effect) {
        if (effect == null) {
            return null;
        }
        Identifier effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        return effectId != null ? effectId.toString() : null;
    }

    private void rotateBattleSeed(MinecraftServer server) {
        battleSeed = random.nextLong();
        savePersistentSettings();
        applyBattleSeed(server);
    }

    private void applyBattleSeed(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ResourceKey<Level> key : BATTLE_DIMENSIONS) {
            ServerLevel world = server.getLevel(key);
            if (world == null) {
                continue;
            }
            ChunkGenerator generator = world.getChunkSource().getGenerator();
            if (generator instanceof RoundSeededNoiseChunkGenerator seededGenerator) {
                seededGenerator.setRoundSeed(battleSeed, world.registryAccess());
            }
        }
    }

    private String resolvePlayerTeamLabel(ServerPlayer player) {
        Integer team = player != null ? engine.teamForPlayer(player.getUUID()) : null;
        return team != null ? teamLabel(team) : text("menu.info.no_team");
    }

    private void announceTeams(Map<UUID, Integer> assignments, MinecraftServer server) {
        if (assignments == null || assignments.isEmpty() || server == null) {
            return;
        }
        Map<Integer, List<String>> byTeam = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : assignments.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            byTeam.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(player.getName().getString());
        }

        List<Integer> teams = new ArrayList<>(byTeam.keySet());
        Collections.sort(teams);
        for (int team : teams) {
            Component message = Component.literal(text("match.team_announcement", teamLabel(team), String.join(", ", byTeam.get(team)))).withStyle(ChatFormatting.AQUA);
            for (Map.Entry<UUID, Integer> entry : assignments.entrySet()) {
                if (!entry.getValue().equals(team)) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    sendPlayerMessage(player, message, false);
                }
            }
        }
    }

    private void applyHealthPreset(ServerPlayer player) {
        double targetMaxHealth = resolvePlayerSharedMaxHealth(player);
        AttributeInstance maxHealthAttribute = maxHealthAttribute(player);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(targetMaxHealth);
        }
        player.setHealth(Math.min(player.getMaxHealth(), (float) targetMaxHealth));
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
    }

    private double resolvePlayerSharedMaxHealth(ServerPlayer player) {
        if (player == null) {
            return healthPreset.maxHealth();
        }
        Integer team = engine.teamForPlayer(player.getUUID());
        if (team == null) {
            return healthPreset.maxHealth();
        }
        int totalExperience = teamSharedExperience.getOrDefault(team, player.totalExperience);
        int sharedLevel = TeamLifeBindCombatMath.resolveExperienceState(totalExperience).level();
        return TeamLifeBindCombatMath.sharedMaxHealth(healthPreset, sharedLevel);
    }

    private SpawnPoint normalizeBedFoot(ResourceKey<Level> worldKey, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BedBlock)) {
            return new SpawnPoint(worldKey, pos.immutable());
        }
        BedPart part = state.getValue(BedBlock.PART);
        if (part == BedPart.HEAD) {
            Direction facing = state.getValue(BedBlock.FACING);
            return new SpawnPoint(worldKey, pos.relative(facing.getOpposite()).immutable());
        }
        return new SpawnPoint(worldKey, pos.immutable());
    }

    private void removeTeamBedAt(SpawnPoint target) {
        Integer matched = null;
        for (Map.Entry<Integer, SpawnPoint> entry : teamBeds.entrySet()) {
            if (entry.getValue().equals(target)) {
                matched = entry.getKey();
                break;
            }
        }
        if (matched != null) {
            teamBeds.remove(matched);
        }
    }

    private void explode(ServerLevel world, BlockPos pos, ServerPlayer source) {
        world.explode(source, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 3.0F, Level.ExplosionInteraction.NONE);
    }

    private final class LobbyMenuScreenHandler extends ChestMenu {

        private final SimpleContainer menuInventory;

        private LobbyMenuScreenHandler(int syncId, Inventory playerInventory, SimpleContainer menuInventory) {
            super(MenuType.GENERIC_9x3, syncId, playerInventory, menuInventory, 3);
            this.menuInventory = menuInventory;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (actionType != ContainerInput.PICKUP) {
                return;
            }
            if (slotIndex < 0 || slotIndex >= menuInventory.getContainerSize()) {
                return;
            }

            String action = resolveMenuAction(menuInventory.getItem(slotIndex));
            if (action != null) {
                handleLobbyMenuAction(serverPlayer, action, menuInventory, button == 1);
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    private record SpawnPoint(ResourceKey<Level> worldKey, BlockPos pos) {
    }

    private record TimedNotice(String message, int remainingTicks) {
    }

    private record PendingRespawn(int team, int remainingTicks, int lastShownSeconds) {
    }

    private record PendingMatchObservation(SpawnPoint spawnPoint, int remainingTicks, int lastShownSeconds) {
    }

    private record PendingBedPlacement(UUID ownerId, int team, InteractionHand hand, ResourceKey<Level> worldKey, BlockPos approxPos, long expiresAtTick) {
    }

    private record PendingBrokenTeamBedDrop(SpawnPoint spawnPoint, int ownerTeam, long processAtTick) {
    }

}
