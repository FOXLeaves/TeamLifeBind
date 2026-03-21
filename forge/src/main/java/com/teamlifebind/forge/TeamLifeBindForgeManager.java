package com.teamlifebind.forge;

import com.teamlifebind.common.DeathResult;
import com.teamlifebind.common.GameOptions;
import com.teamlifebind.common.HealthPreset;
import com.teamlifebind.common.StartResult;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.loading.FMLPaths;

final class TeamLifeBindForgeManager {

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
    private static final String MENU_ACTION_NORESPAWN = "norespawn";
    private static final String MENU_ACTION_ANNOUNCE_TEAMS = "announce_teams";
    private static final String MENU_ACTION_SCOREBOARD = "scoreboard";
    private static final String MENU_ACTION_TAB = "tab";
    private static final String MENU_ACTION_ADVANCEMENTS = "advancements";
    private static final String MENU_ACTION_START = "start";
    private static final String MENU_ACTION_STOP = "stop";
    private static final String MENU_ACTION_RELOAD = "reload";
    private static final ResourceKey<Level> LOBBY_OVERWORLD_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.parse(TeamLifeBindForge.MOD_ID + ":lobby_overworld"));
    private static final ResourceKey<Level> BATTLE_OVERWORLD_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.parse(TeamLifeBindForge.MOD_ID + ":battle_overworld"));
    private static final ResourceKey<Level> BATTLE_NETHER_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.parse(TeamLifeBindForge.MOD_ID + ":battle_nether"));
    private static final ResourceKey<Level> BATTLE_END_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.parse(TeamLifeBindForge.MOD_ID + ":battle_end"));
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
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Set<UUID> dimensionRedirectGuard = new HashSet<>();
    private final Set<UUID> sidebarInitialized = new HashSet<>();
    private final Map<UUID, Integer> sidebarLineCounts = new HashMap<>();
    private final Map<UUID, List<Component>> sidebarLines = new HashMap<>();
    private final Map<UUID, Set<String>> activeNameColorTeams = new HashMap<>();
    private final Map<UUID, Long> supplyBoatDropConfirmUntil = new HashMap<>();
    private final Map<UUID, Long> respawnInvulnerableUntil = new HashMap<>();
    private final Random random = new Random();

    private int teamCount = 2;
    private HealthPreset healthPreset = HealthPreset.ONE_HEART;
    private boolean announceTeamAssignment = true;
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

    public void onServerStarted(MinecraftServer server) {
        this.activeServer = server;
        loadPersistentSettings();
        applyBattleSeed(server);
        reloadLanguage();
        applyAdvancementRule(server);
        ServerLevel lobby = lobbyLevelIfReady(server);
        if (lobby != null) {
            prepareLobbyPlatform(lobby);
            teleportAllToLobby(server);
        }
    }

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
        evaluateReadyCountdown(server);
    }

    public void onPlayerChangedDimension(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to) {
        if (dimensionRedirectGuard.remove(player.getUUID())) {
            return;
        }

        ResourceKey<Level> target = resolvePortalRedirect(from, to);
        if (target == null) {
            return;
        }

        MinecraftServer server = serverOf(player);
        ServerLevel targetLevel = server.getLevel(target);
        if (targetLevel == null) {
            return;
        }

        BlockPos source = player.blockPosition();
        BlockPos destination = portalTargetPos(from, target, source, targetLevel);
        dimensionRedirectGuard.add(player.getUUID());
        player.teleportTo(targetLevel, destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, Set.of(), player.getYRot(), player.getXRot(), false);
    }

    public void setTeamCount(int teamCount) {
        this.teamCount = Math.max(2, Math.min(32, teamCount));
        savePersistentSettings();
    }

    public int getTeamCount() {
        return teamCount;
    }

    public void setHealthPreset(HealthPreset preset) {
        this.healthPreset = preset;
        savePersistentSettings();
    }

    public HealthPreset getHealthPreset() {
        return healthPreset;
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

    public boolean isAnnounceTeamAssignmentEnabled() {
        return announceTeamAssignment;
    }

    public void setAnnounceTeamAssignment(boolean enabled) {
        this.announceTeamAssignment = enabled;
        savePersistentSettings();
    }

    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public void setScoreboardEnabled(boolean enabled) {
        this.scoreboardEnabled = enabled;
        savePersistentSettings();
        refreshScoreboardState(activeServer);
    }

    public boolean isAdvancementsEnabled() {
        return advancementsEnabled;
    }

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
        broadcastOverlay(server, text("ready.marked", player.getName().getString()));
        evaluateReadyCountdown(server);
    }

    public void unready(ServerPlayer player) {
        if (!readyPlayers.remove(player.getUUID())) {
            sendOverlay(player, text("ready.not_marked"));
            return;
        }

        MinecraftServer server = serverOf(player);
        broadcastOverlay(server, text("ready.unmarked", player.getName().getString()));
        evaluateReadyCountdown(server);
    }

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

    public String start(MinecraftServer server) {
        if (hasActiveMatchSession()) {
            return text("match.already_running");
        }

        ServerLevel matchLevel = resolveBattleOverworld(server);
        if (matchLevel == null) {
            return text("match.missing_dimension", TeamLifeBindForge.MOD_ID + ":battle_overworld");
        }

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (players.size() < teamCount) {
            return text("match.need_players", teamCount);
        }

        cancelCountdown();
        readyPlayers.clear();
        clearRoundState();
        rotateBattleSeed(server);

        activeMatchCenter = selectMatchCenter(matchLevel);
        previousMatchCenter = activeMatchCenter;

        List<UUID> ids = players.stream().map(ServerPlayer::getUUID).toList();
        StartResult result = engine.start(ids, new GameOptions(teamCount, healthPreset));

        Map<Integer, SpawnPoint> resolved = resolveTeamSpawns(matchLevel, activeMatchCenter, teamCount);
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

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event, Item teamBedItem) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos clickedPos = event.getPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        if (clickedState.is(BlockTags.BEDS)) {
            player.sendSystemMessage(Component.literal(text("bed.personal_spawn_disabled")));
            denyUse(event);
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BedItem)) {
            return;
        }

        Direction face = event.getFace();
        if (face == null) {
            return;
        }

        BlockPos placePos = clickedPos.relative(face);
        if (!stack.is(teamBedItem)) {
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            explode(level, placePos, player);
            denyUse(event);
            return;
        }

        if (!engine.isRunning()) {
            player.sendSystemMessage(Component.literal(text("bed.place_only_in_match")));
            denyUse(event);
            return;
        }

        Integer team = engine.teamForPlayer(player.getUUID());
        if (team == null) {
            player.sendSystemMessage(Component.literal(text("bed.not_on_team")));
            denyUse(event);
            return;
        }

        Integer ownerTeam = resolveTeamBedOwner(stack);
        if (ownerTeam == null || !ownerTeam.equals(team)) {
            if (!player.isCreative()) {
                stack.shrink(Math.min(2, stack.getCount()));
            }
            explode(level, placePos, player);
            player.sendSystemMessage(Component.literal(text("bed.wrong_team", ownerTeam != null ? teamLabel(ownerTeam) : text("scoreboard.value.none"))));
            denyUse(event);
            return;
        }

        if (hasActiveTeamBedOrPendingPlacement(team, serverOf(player))) {
            player.sendSystemMessage(Component.literal(text("bed.limit_reached")));
            denyUse(event);
            return;
        }

        if (!player.isCreative() && stack.getCount() > 1) {
            stack.setCount(1);
        }
        pendingBedPlacements.add(new PendingBedPlacement(player.getUUID(), ownerTeam, event.getHand(), level.dimension(), placePos.immutable(), level.getGameTime() + 5L));
    }

    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(BlockTags.BEDS)) {
            return;
        }

        SpawnPoint bedFoot = normalizeBedFoot(level.dimension(), event.getPos(), state);
        for (PendingBedPlacement pending : pendingBedPlacements) {
            if (pending.worldKey().equals(bedFoot.worldKey()) && pending.approxPos().distManhattan(bedFoot.pos()) <= 1) {
                placedTeamBedOwners.put(bedFoot, pending.team());
                teamBeds.put(pending.team(), bedFoot);
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(pending.ownerId());
                if (owner != null) {
                    sendOverlay(owner, text("bed.updated", teamLabel(pending.team())));
                }
                break;
            }
        }
    }

    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockState state = event.getState();
        if (!state.is(BlockTags.BEDS)) {
            return;
        }
        SpawnPoint normalized = normalizeBedFoot(level.dimension(), event.getPos(), state);
        Integer ownerTeam = placedTeamBedOwners.remove(normalized);
        if (ownerTeam != null) {
            if (event.getPlayer() instanceof ServerPlayer player && !player.isCreative()) {
                pendingBrokenTeamBedDrops.add(new PendingBrokenTeamBedDrop(normalized, ownerTeam, level.getGameTime() + 1L));
            }
            SpawnPoint activeBed = teamBeds.get(ownerTeam);
            if (normalized.equals(activeBed)) {
                teamBeds.remove(ownerTeam);
            }
            return;
        }
        removeTeamBedAt(normalized);
    }

    public boolean onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker) || !(event.getTarget() instanceof ServerPlayer target)) {
            return true;
        }
        if (!shouldCancelFriendlyFire(attacker, target)) {
            return true;
        }
        notifyFriendlyFireBlocked(attacker);
        return false;
    }

    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        LivingEntity dead = event.getEntity();
        if (dead instanceof ServerPlayer deadPlayer) {
            lockNoRespawnIfBlocked(deadPlayer);
            handlePlayerDeath(deadPlayer);
            return;
        }

        if (!(dead instanceof EnderDragon)) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof ServerPlayer killer)) {
            return;
        }

        Integer winner = engine.onObjectiveCompleted(killer.getUUID());
        if (winner == null) {
            return;
        }

        stopInternal(killer.level().getServer(), true, text("match.win_reason.dragon", killer.getName().getString()));
    }

    public void onLivingDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(drop -> isSupplyBoatItem(drop.getItem()));
    }

    public void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        ItemEntity itemEntity = event.getEntity();
        if (itemEntity == null) {
            return;
        }
        if (itemEntity.getItem().is(TeamLifeBindForge.TEAM_BED_ITEM.get())) {
            itemEntity.getItem().setCount(1);
            consumeResidualDroppedTeamBedToken(player, itemEntity.getItem());
            return;
        }
        if (!isDiscardProtectedBoatItem(itemEntity.getItem())) {
            return;
        }

        long tick = player.level().getGameTime();
        Long expiresAt = supplyBoatDropConfirmUntil.get(player.getUUID());
        ItemStack droppedStack = itemEntity.getItem().copy();
        itemEntity.discard();
        if (expiresAt != null && expiresAt >= tick) {
            supplyBoatDropConfirmUntil.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(text("boat.discarded")).withStyle(ChatFormatting.GRAY));
            return;
        }

        supplyBoatDropConfirmUntil.put(player.getUUID(), tick + SUPPLY_BOAT_DROP_CONFIRM_TICKS);
        giveBoatBack(player, droppedStack);
        player.sendSystemMessage(Component.literal(text("boat.drop_confirm")).withStyle(ChatFormatting.YELLOW));
    }

    public boolean onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer target)) {
            return true;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return true;
        }
        if (!shouldCancelFriendlyFire(attacker, target)) {
            return true;
        }
        notifyFriendlyFireBlocked(attacker);
        return false;
    }

    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!engine.isRunning()) {
            teleportToLobby(player);
            return;
        }

        Integer team = engine.teamForPlayer(player.getUUID());
        if (isRespawnLocked(player)) {
            player.setGameMode(GameType.SPECTATOR);
            teleport(player, resolveSpectatorSpawn(team, player.level().getServer()));
            setImportantNotice(player, text("player.eliminated.no_respawn"), IMPORTANT_NOTICE_TICKS);
            return;
        }

        if (team == null) {
            moveUnassignedPlayerToSpectator(player, player.level().getServer(), true);
            return;
        }

        queuePendingRespawn(player, team, player.level().getServer());
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.server();
        if (server == null) {
            return;
        }

        processPendingJoinSyncs(server);
        processPendingBedPlacements(server);
        processPendingBrokenTeamBedDrops(server);
        processPendingRespawns(server);
        normalizeTeamBedInventories(server);
        processRespawnInvulnerability(server);
        processImportantNotices(server);
        processPendingMatchObservations(server);
        processReadyCountdown(server);
        processSidebars(server);
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
        player.connection.send(new ClientboundSetObjectivePacket(createSidebarObjective(), ClientboundSetObjectivePacket.METHOD_REMOVE));
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
            if (tick > pending.expiresAtTick()) {
                iterator.remove();
                continue;
            }

            ServerLevel world = server.getLevel(pending.worldKey());
            if (world == null) {
                iterator.remove();
                continue;
            }

            if (!world.getBlockState(pending.approxPos()).is(BlockTags.BEDS)) {
                continue;
            }

            SpawnPoint bed = normalizeBedFoot(pending.worldKey(), pending.approxPos(), world.getBlockState(pending.approxPos()));
            teamBeds.put(pending.team(), bed);
            ServerPlayer owner = server.getPlayerList().getPlayer(pending.ownerId());
            if (owner != null) {
                consumeResidualPlacedTeamBedToken(owner, pending.hand());
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

            ServerLevel level = server.getLevel(pending.spawnPoint().worldKey());
            if (level == null) {
                iterator.remove();
                continue;
            }

            AABB searchBox = new AABB(pending.spawnPoint().pos()).inflate(1.5D);
            ItemEntity vanillaDrop = level.getEntitiesOfClass(
                ItemEntity.class,
                searchBox,
                entity -> entity.isAlive() && entity.getItem().is(Items.WHITE_BED)
            ).stream().findFirst().orElse(null);
            if (vanillaDrop != null) {
                vanillaDrop.discard();
            }

            level.addFreshEntity(new ItemEntity(
                level,
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

        MinecraftServer server = deadPlayer.level().getServer();
        notifyTeamWiped(server, result.team(), result.victims());

        for (UUID victimId : result.victims()) {
            if (victimId.equals(deadPlayer.getUUID())) {
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(victimId);
            if (target == null || !target.isAlive()) {
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
        ServerLevel lobby = lobbyLevelIfReady(server);
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
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        supplyBoatDropConfirmUntil.clear();
        pendingBedPlacements.clear();
        pendingBrokenTeamBedDrops.clear();
        pendingRespawns.clear();
        pendingMatchObservations.clear();
        importantNotices.clear();
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
                TeamLifeBindForge.LOGGER.warn("Failed to reset battle dimension folder {}", folder, ex);
            }
        }
    }

    private void deleteDirectory(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
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

    private void prepareLobbyPlatform(ServerLevel level) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                level.setBlockAndUpdate(new BlockPos(x, LOBBY_PLATFORM_Y, z), Blocks.GLASS.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, LOBBY_PLATFORM_Y + 1, z), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, LOBBY_PLATFORM_Y + 2, z), Blocks.AIR.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(new BlockPos(0, LOBBY_PLATFORM_Y, 0), Blocks.SEA_LANTERN.defaultBlockState());
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
        ServerLevel lobby = lobbyLevelIfReady(server);
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
            ServerLevel lobby = lobbyLevelIfReady(server);
            if (lobby == null) {
                pendingJoinSyncs.put(player.getUUID(), JOIN_SYNC_RETRY_TICKS);
                return;
            }
            prepareLobbyPlatform(lobby);
            teleportToLobby(player);
            player.sendSystemMessage(Component.literal(text("lobby.guide")));
            return;
        }

        Integer team = engine.teamForPlayer(player.getUUID());
        if (team == null) {
            moveUnassignedPlayerToSpectator(player, server, true);
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

    private void moveUnassignedPlayerToSpectator(ServerPlayer player, MinecraftServer server, boolean notify) {
        preparePlayerForSpectator(player);
        teleport(player, resolveSpectatorSpawn(server));
        if (notify) {
            player.sendSystemMessage(Component.literal(text("player.unassigned_spectator")));
        }
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

        ServerLevel battleLevel = resolveBattleOverworld(server);
        if (battleLevel != null) {
            BlockPos center = activeMatchCenter != null ? activeMatchCenter : battleLevel.getRespawnData().pos();
            return new SpawnPoint(battleLevel.dimension(), center.above(8));
        }

        ServerLevel lobbyLevel = resolveLobbyLevel(server);
        return new SpawnPoint(lobbyLevel.dimension(), LOBBY_SPAWN_POS);
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

            if (!engine.isRunning() && lobbyLevelIfReady(server) == null) {
                entry.setValue(JOIN_SYNC_RETRY_TICKS);
                continue;
            }

            syncJoinedPlayer(player, server);
            iterator.remove();
        }
    }

    private BlockPos selectMatchCenter(ServerLevel level) {
        BlockPos worldSpawn = level.getRespawnData().pos();
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

        return safeSurface(level, chosen.getX(), chosen.getZ());
    }

    private ServerLevel resolveLobbyLevel(MinecraftServer server) {
        ServerLevel lobby = server.getLevel(LOBBY_OVERWORLD_KEY);
        return lobby != null ? lobby : server.overworld();
    }

    private ServerLevel lobbyLevelIfReady(MinecraftServer server) {
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

    private BlockPos portalTargetPos(ResourceKey<Level> from, ResourceKey<Level> target, BlockPos sourcePos, ServerLevel targetLevel) {
        if (target.equals(LOBBY_OVERWORLD_KEY)) {
            return LOBBY_SPAWN_POS;
        }
        if (target.equals(BATTLE_END_KEY)) {
            return randomEndSpawn(targetLevel);
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

        return safeSurface(targetLevel, x, z);
    }

    private Map<Integer, SpawnPoint> resolveTeamSpawns(ServerLevel level, BlockPos center, int teams) {
        Map<Integer, SpawnPoint> resolved = new HashMap<>();
        List<BlockPos> chosen = new ArrayList<>();

        for (int team = 1; team <= teams; team++) {
            BlockPos candidate = null;
            int minDistance = MIN_TEAM_DISTANCE;
            for (int relax = 0; relax < 8 && candidate == null; relax++) {
                candidate = findRandomTeamBase(level, center, chosen, minDistance);
                minDistance = Math.max(MIN_TEAM_DISTANCE_FLOOR, (int) Math.floor(minDistance * 0.85D));
            }
            if (candidate == null) {
                candidate = fallbackRandomTeamBase(level, center);
            }
            chosen.add(candidate);
            resolved.put(team, new SpawnPoint(level.dimension(), candidate));
        }
        return resolved;
    }

    private BlockPos findRandomTeamBase(ServerLevel level, BlockPos center, List<BlockPos> chosen, int minDistance) {
        for (int attempt = 0; attempt < SPAWN_SEARCH_ATTEMPTS; attempt++) {
            BlockPos flat = randomPointInRadius(center, RANDOM_SPAWN_RADIUS);
            if (!isFarEnough(flat, chosen, minDistance)) {
                continue;
            }
            return safeSurface(level, flat.getX(), flat.getZ());
        }
        return null;
    }

    private BlockPos fallbackRandomTeamBase(ServerLevel level, BlockPos center) {
        BlockPos flat = randomPointInRadius(center, RANDOM_SPAWN_RADIUS);
        return safeSurface(level, flat.getX(), flat.getZ());
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

        ServerLevel level = server.getLevel(base.worldKey());
        if (level == null) {
            return base;
        }

        return new SpawnPoint(base.worldKey(), safeSurface(level, offset.getX(), offset.getZ()));
    }

    private SpawnPoint resolveRespawn(int team, MinecraftServer server) {
        SpawnPoint bed = teamBeds.get(team);
        if (bed != null) {
            ServerLevel bedLevel = server.getLevel(bed.worldKey());
            if (bedLevel != null && bedLevel.getBlockState(bed.pos()).is(BlockTags.BEDS)) {
                return new SpawnPoint(bed.worldKey(), safeSurface(bedLevel, bed.pos().getX(), bed.pos().getZ()));
            }
            teamBeds.remove(team);
        }

        SpawnPoint spawn = teamSpawns.get(team);
        if (spawn == null) {
            return null;
        }

        ServerLevel level = server.getLevel(spawn.worldKey());
        if (level == null) {
            return null;
        }

        return new SpawnPoint(spawn.worldKey(), safeSurface(level, spawn.pos().getX(), spawn.pos().getZ()));
    }

    private boolean hasActiveTeamBedOrPendingPlacement(int team, MinecraftServer server) {
        SpawnPoint existing = teamBeds.get(team);
        if (existing != null) {
            if (server != null) {
                ServerLevel bedLevel = server.getLevel(existing.worldKey());
                if (bedLevel != null && bedLevel.getBlockState(existing.pos()).is(BlockTags.BEDS)) {
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
        playSound(player, "minecraft:entity.player.levelup", 0.9F, 1.2F);
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
        return player.level().getServer();
    }

    private void teleport(ServerPlayer player, SpawnPoint spawnPoint) {
        ServerLevel level = serverOf(player).getLevel(spawnPoint.worldKey());
        if (level == null) {
            return;
        }

        player.teleportTo(level, spawnPoint.pos().getX() + 0.5D, spawnPoint.pos().getY(), spawnPoint.pos().getZ() + 0.5D, Set.of(), player.getYRot(), player.getXRot(), false);
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

    private BlockPos randomEndSpawn(ServerLevel level) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int radius = END_SPAWN_MIN_RADIUS + random.nextInt((END_SPAWN_MAX_RADIUS - END_SPAWN_MIN_RADIUS) + 1);
        int x = (int) Math.round(Math.cos(angle) * radius);
        int z = (int) Math.round(Math.sin(angle) * radius);
        return safeSurface(level, x, z);
    }

    private BlockPos safeSurface(ServerLevel level, int x, int z) {
        BlockPos direct = findSafeSurfaceAt(level, x, z);
        if (direct != null) {
            return direct;
        }

        for (int radius = 1; radius <= SAFE_SPAWN_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    BlockPos candidate = findSafeSurfaceAt(level, x + dx, z + dz);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        }

        if (level.dimension().equals(Level.NETHER) || level.dimension().equals(BATTLE_NETHER_KEY)) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
            return new BlockPos(x, y, z);
        }
        return createFallbackSpawnPlatform(level, x, z);
    }

    private BlockPos findSafeSurfaceAt(ServerLevel level, int x, int z) {
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int offset = -3; offset <= 3; offset++) {
            BlockPos candidate = new BlockPos(x, topY + 1 + offset, z);
            if (isSafeSpawnLocation(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isSafeSpawnLocation(ServerLevel level, BlockPos candidate) {
        BlockPos groundPos = candidate.below();
        BlockState ground = level.getBlockState(groundPos);
        BlockState feet = level.getBlockState(candidate);
        BlockState head = level.getBlockState(candidate.above());
        if (ground.isAir() || isHazardousGround(ground) || ground.getCollisionShape(level, groundPos).isEmpty() || !isAllowedSpawnGround(level, ground)) {
            return false;
        }
        if (!feet.isAir() || !head.isAir()) {
            return false;
        }
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty();
    }

    private boolean isAllowedSpawnGround(ServerLevel level, BlockState ground) {
        ResourceKey<Level> worldKey = level.dimension();
        if (worldKey.equals(Level.END) || worldKey.equals(BATTLE_END_KEY)) {
            return ground.is(Blocks.END_STONE);
        }
        if (worldKey.equals(Level.OVERWORLD) || worldKey.equals(LOBBY_OVERWORLD_KEY) || worldKey.equals(BATTLE_OVERWORLD_KEY)) {
            return ground.is(Blocks.GRASS_BLOCK);
        }
        return !ground.is(BlockTags.LEAVES);
    }

    private BlockPos createFallbackSpawnPlatform(ServerLevel level, int x, int z) {
        int feetY = Math.max(level.getSeaLevel() + 2, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1);
        int platformY = feetY - 1;
        BlockState topState = fallbackPlatformTopState(level);
        BlockState foundationState = topState.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : topState;

        for (int dx = -FORCED_PLATFORM_RADIUS; dx <= FORCED_PLATFORM_RADIUS; dx++) {
            for (int dz = -FORCED_PLATFORM_RADIUS; dz <= FORCED_PLATFORM_RADIUS; dz++) {
                for (int depth = 1; depth <= FORCED_PLATFORM_FOUNDATION_DEPTH; depth++) {
                    level.setBlockAndUpdate(new BlockPos(x + dx, platformY - depth, z + dz), foundationState);
                }
                level.setBlockAndUpdate(new BlockPos(x + dx, platformY, z + dz), topState);
                for (int clearance = 1; clearance <= FORCED_PLATFORM_CLEARANCE; clearance++) {
                    level.setBlockAndUpdate(new BlockPos(x + dx, platformY + clearance, z + dz), Blocks.AIR.defaultBlockState());
                }
            }
        }

        return new BlockPos(x, platformY + 1, z);
    }

    private BlockState fallbackPlatformTopState(ServerLevel level) {
        ResourceKey<Level> worldKey = level.dimension();
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
        player.displayClientMessage(Component.literal(message), true);
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
            playSound(player, "minecraft:block.note_block.hat", 0.9F, pitch);
        }
    }

    private void playSound(ServerPlayer player, String soundId, float volume, float pitch) {
        if (player == null || soundId == null || soundId.isBlank()) {
            return;
        }
        var sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(soundId));
        if (sound == null) {
            return;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private void setImportantNotice(ServerPlayer player, String message, int ticks) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        importantNotices.put(player.getUUID(), new TimedNotice(message, Math.max(1, ticks)));
        sendOverlay(player, message);
    }

    private void broadcastImportantNotice(MinecraftServer server, String message) {
        if (server == null || message == null || message.isBlank()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            setImportantNotice(player, message, IMPORTANT_NOTICE_TICKS);
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
            setImportantNotice(player, message, IMPORTANT_NOTICE_TICKS);
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
        if (player == null || player.level() == null) {
            return text("scoreboard.dimension.unknown");
        }
        ResourceKey<Level> worldKey = player.level().dimension();
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
        SimpleContainer menuInventory = new SimpleContainer(LOBBY_MENU_SIZE);
        populateLobbyMenuInventory(menuInventory, player);
        player.openMenu(
            new SimpleMenuProvider(
                (containerId, playerInventory, ignored) -> new LobbyMenu(containerId, playerInventory, menuInventory),
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

    void bindCraftedTeamBed(ItemStack stack, UUID crafterId) {
        Integer team = crafterId == null ? null : engine.teamForPlayer(crafterId);
        if (team == null) {
            clearTeamBedOwner(stack);
            return;
        }
        setTeamBedOwner(stack, team);
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
        player.clearFire();
        player.fallDistance = 0.0F;
        if (player.getAttribute(Attributes.MAX_HEALTH) != null) {
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
        }
        player.setHealth(20.0F);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        player.setGameMode(lobbyState ? GameType.ADVENTURE : GameType.SURVIVAL);
    }

    private void giveLobbyItems(ServerPlayer player) {
        player.getInventory().setItem(LOBBY_MENU_SLOT, createLobbyMenuItem());
        player.getInventory().setItem(LOBBY_GUIDE_SLOT, createWrittenBookItem("item.guide.name", "book.guide.title", "book.guide.author", "book.guide.page1", "book.guide.page2"));
        player.getInventory().setItem(LOBBY_RECIPES_SLOT, createWrittenBookItem("item.recipes.name", "book.recipes.title", "book.recipes.author", "book.recipes.page1", "book.recipes.page2"));
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
        CompoundTag tag = new CompoundTag();
        tag.putString(LOBBY_ITEM_TAG, LOBBY_MENU_TAG);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return item;
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
    }

    private ItemStack createOwnedTeamBedItem(int ownerTeam) {
        ItemStack item = new ItemStack(TeamLifeBindForge.TEAM_BED_ITEM.get());
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
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(TeamLifeBindForge.TEAM_BED_ITEM.get()) || stack.getCount() >= 2 || isPendingTeamBedPlacementStack(player, stack)) {
                continue;
            }
            stack.setCount(2);
        }
    }

    private void consumeResidualDroppedTeamBedToken(ServerPlayer player, ItemStack droppedStack) {
        if (player == null || player.isCreative() || droppedStack == null || !droppedStack.is(TeamLifeBindForge.TEAM_BED_ITEM.get())) {
            return;
        }
        Integer ownerTeam = resolveTeamBedOwner(droppedStack);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isResidualDroppedTeamBedStack(stack, ownerTeam)) {
                continue;
            }
            inventory.setItem(slot, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
            return;
        }
    }

    private boolean isResidualDroppedTeamBedStack(ItemStack stack, Integer ownerTeam) {
        return stack != null
            && !stack.isEmpty()
            && stack.is(TeamLifeBindForge.TEAM_BED_ITEM.get())
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
        if (!stack.is(TeamLifeBindForge.TEAM_BED_ITEM.get())) {
            return;
        }
        stack.shrink(1);
    }

    private void grantRespawnInvulnerability(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }
        player.setInvulnerable(true);
        respawnInvulnerableUntil.put(player.getUUID(), server.overworld().getGameTime() + RESPAWN_INVULNERABILITY_TICKS);
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

    private ItemStack createWrittenBookItem(String nameKey, String titleKey, String authorKey, String pageOneKey, String pageTwoKey) {
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
        return item;
    }

    private MutableComponent clickableButton(String label, String command, ChatFormatting color) {
        return Component.literal(label).withStyle(style -> style.withColor(color).withClickEvent(new ClickEvent.RunCommand(command)));
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

    private ItemStack createMenuButton(String action, Item item, String label, List<Component> lore) {
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
        return action == null || action.isBlank() ? null : action;
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
        if (stack == null || !stack.is(TeamLifeBindForge.TEAM_BED_ITEM.get())) {
            return null;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        return customData.copyTag().getInt(TEAM_BED_OWNER_TAG).orElse(null);
    }

    private void updateTeamBedDisplay(ItemStack stack, Integer ownerTeam) {
        if (stack == null || !stack.is(TeamLifeBindForge.TEAM_BED_ITEM.get())) {
            return;
        }
        String displayName = text("item.team_bed.name");
        if (ownerTeam != null) {
            displayName += " [" + teamLabel(ownerTeam) + "]";
        }
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName).withStyle(ChatFormatting.GOLD));
    }

    private void handleLobbyMenuAction(ServerPlayer player, String action, SimpleContainer inventory, boolean decrement) {
        if (player == null || action == null) {
            return;
        }

        switch (action) {
            case MENU_ACTION_READY -> markReady(player);
            case MENU_ACTION_UNREADY -> unready(player);
            case MENU_ACTION_STATUS -> player.sendSystemMessage(Component.literal(status(serverOf(player))));
            case MENU_ACTION_HELP -> sendCommandHelp(player);
            case MENU_ACTION_TEAMS -> {
                if (!canManageMatch(player)) {
                    player.sendSystemMessage(Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED));
                } else if (hasActiveMatchSession()) {
                    player.sendSystemMessage(Component.literal(text("command.team_count.running")).withStyle(ChatFormatting.RED));
                } else {
                    int updatedCount = stepTeamCount(teamCount, decrement);
                    setTeamCount(updatedCount);
                    player.sendSystemMessage(Component.literal(text("command.team_count.updated", updatedCount)).withStyle(ChatFormatting.GREEN));
                }
            }
            case MENU_ACTION_HEALTH -> {
                if (!canManageMatch(player)) {
                    player.sendSystemMessage(Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED));
                } else if (hasActiveMatchSession()) {
                    player.sendSystemMessage(Component.literal(text("command.health.running")).withStyle(ChatFormatting.RED));
                } else {
                    HealthPreset next = stepHealthPreset(healthPreset, decrement);
                    setHealthPreset(next);
                    player.sendSystemMessage(Component.literal(text("command.health.updated", healthPresetLabel(next))).withStyle(ChatFormatting.GREEN));
                }
            }
            case MENU_ACTION_NORESPAWN -> {
                if (!canManageMatch(player)) {
                    player.sendSystemMessage(Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED));
                } else {
                    boolean enabled = !noRespawnEnabled;
                    setNoRespawnEnabled(enabled);
                    player.sendSystemMessage(
                        Component.literal(text(enabled ? "command.norespawn.enabled" : "command.norespawn.disabled")).withStyle(ChatFormatting.GREEN)
                    );
                }
            }
            case MENU_ACTION_ANNOUNCE_TEAMS -> {
                if (!canManageMatch(player)) {
                    player.sendSystemMessage(Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED));
                } else {
                    boolean enabled = !announceTeamAssignment;
                    setAnnounceTeamAssignment(enabled);
                    player.sendSystemMessage(Component.literal(text("config.announce_team_assignment.updated", stateLabel(enabled))).withStyle(ChatFormatting.GREEN));
                }
            }
            case MENU_ACTION_SCOREBOARD -> {
                if (!canManageMatch(player)) {
                    player.sendSystemMessage(Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED));
                } else {
                    boolean enabled = !scoreboardEnabled;
                    setScoreboardEnabled(enabled);
                    player.sendSystemMessage(Component.literal(text("config.scoreboard.updated", stateLabel(enabled))).withStyle(ChatFormatting.GREEN));
                }
            }
            case MENU_ACTION_ADVANCEMENTS -> {
                if (!canManageMatch(player)) {
                    player.sendSystemMessage(Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED));
                } else {
                    boolean enabled = !advancementsEnabled;
                    setAdvancementsEnabled(enabled);
                    player.sendSystemMessage(Component.literal(text("config.advancements.updated", stateLabel(enabled))).withStyle(ChatFormatting.GREEN));
                }
            }
            case MENU_ACTION_TAB -> {
                if (!canManageMatch(player)) {
                    player.sendSystemMessage(Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED));
                } else {
                    boolean enabled = !tabEnabled;
                    setTabEnabled(enabled);
                    player.sendSystemMessage(Component.literal(text("config.tab.updated", stateLabel(enabled))).withStyle(ChatFormatting.GREEN));
                }
            }
            case MENU_ACTION_START -> {
                if (!canManageMatch(player)) {
                    player.sendSystemMessage(Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED));
                } else {
                    player.sendSystemMessage(Component.literal(start(serverOf(player))));
                }
            }
            case MENU_ACTION_STOP -> {
                if (!canManageMatch(player)) {
                    player.sendSystemMessage(Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED));
                } else {
                    player.sendSystemMessage(Component.literal(stop(serverOf(player))));
                }
            }
            case MENU_ACTION_RELOAD -> {
                if (!canManageMatch(player)) {
                    player.sendSystemMessage(Component.literal(text("command.no_permission")).withStyle(ChatFormatting.RED));
                } else {
                    loadPersistentSettings();
                    reloadLanguage();
                    applyAdvancementRule(activeServer);
                    refreshScoreboardState(activeServer);
                    refreshTabState(activeServer);
                    player.sendSystemMessage(Component.literal(text("command.reload.success")).withStyle(ChatFormatting.GREEN));
                }
            }
            default -> {
                return;
            }
        }

        if (player.containerMenu instanceof LobbyMenu menu) {
            populateLobbyMenuInventory(inventory, player);
            menu.broadcastChanges();
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
            "command.help.norespawn",
            "command.help.norespawn_toggle",
            "command.help.norespawn_add",
            "command.help.norespawn_remove",
            "command.help.norespawn_clear"
        )) {
            player.sendSystemMessage(Component.literal(text(key)));
        }
    }

    private boolean canManageMatch(ServerPlayer player) {
        return player != null && player.canUseGameMasterBlocks();
    }

    private void reloadLanguage() {
        language = TeamLifeBindLanguage.load(
            FMLPaths.CONFIGDIR.get().resolve("teamlifebind"),
            message -> TeamLifeBindForge.LOGGER.warn(message)
        );
    }

    private void applyAdvancementRule(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ResourceKey<Level> key : Set.of(LOBBY_OVERWORLD_KEY, BATTLE_OVERWORLD_KEY, BATTLE_NETHER_KEY, BATTLE_END_KEY, Level.OVERWORLD, Level.NETHER, Level.END)) {
            ServerLevel level = server.getLevel(key);
            if (level != null) {
                setBooleanGameRule(level, server, advancementsEnabled, "SHOW_ADVANCEMENT_MESSAGES", "RULE_SHOW_ADVANCEMENT_MESSAGES");
                setBooleanGameRule(level, server, true, "RULE_DO_IMMEDIATE_RESPAWN", "IMMEDIATE_RESPAWN");
            }
        }
    }

    private void setBooleanGameRule(ServerLevel level, MinecraftServer server, boolean enabled, String... fieldNames) {
        if (level == null || server == null) {
            return;
        }
        Object gameRules = level.getGameRules();
        for (String fieldName : fieldNames) {
            try {
                Object value = GameRules.class.getField(fieldName).get(null);
                if (value == null) {
                    continue;
                }
                for (java.lang.reflect.Method method : gameRules.getClass().getMethods()) {
                    if (!method.getName().equals("set") || method.getParameterCount() != 3) {
                        continue;
                    }
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (!parameterTypes[0].isInstance(value) || parameterTypes[1] != boolean.class || !parameterTypes[2].isInstance(server)) {
                        continue;
                    }
                    method.invoke(gameRules, value, enabled, server);
                    return;
                }
            } catch (ReflectiveOperationException ignored) {
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
            TeamLifeBindForge.LOGGER.warn("Failed to load settings from {}", path, ex);
        }

        teamCount = Math.max(2, Math.min(32, parseInt(properties.getProperty("team-count"), 2)));
        healthPreset = HealthPreset.fromString(properties.getProperty("health-preset", HealthPreset.ONE_HEART.name()));
        announceTeamAssignment = Boolean.parseBoolean(properties.getProperty("announce-team-assignment", "true"));
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
            TeamLifeBindForge.LOGGER.warn("Failed to save settings to {}", path, ex);
        }
    }

    private Path settingsFilePath() {
        return FMLPaths.CONFIGDIR.get().resolve("teamlifebind").resolve("settings.properties");
    }

    private int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
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
            ServerLevel level = server.getLevel(key);
            if (level == null) {
                continue;
            }
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            if (generator instanceof RoundSeededNoiseChunkGenerator seededGenerator) {
                seededGenerator.setRoundSeed(battleSeed, level.registryAccess());
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
            byTeam.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>()).add(player.getName().getString());
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
                    player.sendSystemMessage(message);
                }
            }
        }
    }

    private void applyHealthPreset(ServerPlayer player) {
        if (player.getAttribute(Attributes.MAX_HEALTH) != null) {
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(healthPreset.maxHealth());
        }
        player.setHealth((float) Math.min(player.getMaxHealth(), healthPreset.maxHealth()));
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
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

    private void explode(ServerLevel level, BlockPos pos, ServerPlayer source) {
        level.explode(source, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 3.0F, Level.ExplosionInteraction.NONE);
    }

    private void denyUse(PlayerInteractEvent.RightClickBlock event) {
        event.setUseBlock(Result.DENY);
        event.setUseItem(Result.DENY);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    private final class LobbyMenu extends ChestMenu {

        private final SimpleContainer menuInventory;

        private LobbyMenu(int containerId, Inventory playerInventory, SimpleContainer menuInventory) {
            super(MenuType.GENERIC_9x3, containerId, playerInventory, menuInventory, 3);
            this.menuInventory = menuInventory;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (clickType != ClickType.PICKUP) {
                return;
            }
            if (slotId < 0 || slotId >= menuInventory.getContainerSize()) {
                return;
            }

            String action = resolveMenuAction(menuInventory.getItem(slotId));
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

