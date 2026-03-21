package com.teamlifebind.fabric;

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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreResetS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.rule.GameRules;

final class TeamLifeBindFabricManager {

    private static final long TEAM_WIPE_GUARD_MS = 2_000L;
    private static final int RANDOM_SPAWN_RADIUS = 750;
    private static final int MIN_TEAM_DISTANCE = 280;
    private static final int MIN_TEAM_DISTANCE_FLOOR = 64;
    private static final int SPAWN_SEARCH_ATTEMPTS = 256;
    private static final int SAFE_SPAWN_SEARCH_RADIUS = 12;
    private static final int TEAM_SPAWN_SPREAD = 16;

    private static final int READY_COUNTDOWN_SECONDS = 10;
    private static final int IMPORTANT_NOTICE_TICKS = 60;
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
    private static final int LOBBY_MENU_TEAMS_SLOT = 19;
    private static final int LOBBY_MENU_HEALTH_SLOT = 20;
    private static final int LOBBY_MENU_NORESPAWN_SLOT = 21;
    private static final int LOBBY_MENU_ADVANCEMENTS_SLOT = 22;
    private static final int LOBBY_MENU_TAB_SLOT = 23;
    private static final int LOBBY_MENU_START_STOP_SLOT = 24;
    private static final int LOBBY_MENU_RELOAD_SLOT = 25;
    private static final int MATCH_CENTER_RADIUS = 6500;
    private static final int MATCH_CENTER_MIN_DISTANCE = 1800;
    private static final String SIDEBAR_OBJECTIVE_NAME = "tlb_sidebar";
    private static final String SIDEBAR_ENTRY_PREFIX = "tlb_line_";
    private static final int SIDEBAR_MAX_LINES = 8;
    private static final String NAME_COLOR_TEAM_PREFIX = "tlb_name_";
    private static final String NAME_COLOR_TEAM_NEUTRAL = NAME_COLOR_TEAM_PREFIX + "neutral";
    private static final Formatting[] TAB_TEAM_COLORS = {
        Formatting.AQUA,
        Formatting.YELLOW,
        Formatting.LIGHT_PURPLE,
        Formatting.GOLD,
        Formatting.BLUE,
        Formatting.WHITE,
        Formatting.DARK_AQUA,
        Formatting.GRAY
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
    private static final String MENU_ACTION_TAB = "tab";
    private static final String MENU_ACTION_ADVANCEMENTS = "advancements";
    private static final String MENU_ACTION_START = "start";
    private static final String MENU_ACTION_STOP = "stop";
    private static final String MENU_ACTION_RELOAD = "reload";
    private static final RegistryKey<World> LOBBY_OVERWORLD_KEY = RegistryKey.of(RegistryKeys.WORLD, TeamLifeBindFabric.id("lobby_overworld"));
    private static final RegistryKey<World> BATTLE_OVERWORLD_KEY = RegistryKey.of(RegistryKeys.WORLD, TeamLifeBindFabric.id("battle_overworld"));
    private static final RegistryKey<World> BATTLE_NETHER_KEY = RegistryKey.of(RegistryKeys.WORLD, TeamLifeBindFabric.id("battle_nether"));
    private static final RegistryKey<World> BATTLE_END_KEY = RegistryKey.of(RegistryKeys.WORLD, TeamLifeBindFabric.id("battle_end"));
    private static final BlockPos LOBBY_SPAWN_POS = new BlockPos(0, LOBBY_PLATFORM_Y + 1, 0);
    private static final Set<RegistryKey<World>> BATTLE_DIMENSIONS = Set.of(BATTLE_OVERWORLD_KEY, BATTLE_NETHER_KEY, BATTLE_END_KEY);

    private final TeamLifeBindEngine engine = new TeamLifeBindEngine();
    private final Map<Integer, SpawnPoint> teamSpawns = new HashMap<>();
    private final Map<Integer, SpawnPoint> teamBeds = new HashMap<>();
    private final Map<SpawnPoint, Integer> placedTeamBedOwners = new HashMap<>();
    private final Map<Integer, Long> teamWipeGuardUntil = new HashMap<>();
    private final Set<UUID> noRespawnLockedPlayers = new HashSet<>();
    private final Set<RegistryKey<World>> noRespawnDimensions = new HashSet<>(Set.of(World.END, BATTLE_END_KEY));
    private final Map<UUID, TimedNotice> importantNotices = new HashMap<>();
    private final List<PendingBedPlacement> pendingBedPlacements = new ArrayList<>();
    private final List<PendingBrokenTeamBedDrop> pendingBrokenTeamBedDrops = new ArrayList<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Set<UUID> dimensionRedirectGuard = new HashSet<>();
    private final Set<UUID> sidebarInitialized = new HashSet<>();
    private final Map<UUID, Integer> sidebarLineCounts = new HashMap<>();
    private final Map<UUID, Long> supplyBoatDropConfirmUntil = new HashMap<>();
    private final Map<UUID, Long> respawnInvulnerableUntil = new HashMap<>();
    private final Random random = new Random();

    private int teamCount = 2;
    private HealthPreset healthPreset = HealthPreset.ONE_HEART;
    private boolean noRespawnEnabled = true;
    private boolean tabEnabled = true;
    private boolean advancementsEnabled = false;
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
        reloadLanguage();
        applyAdvancementRule(server);
        ServerWorld lobby = resolveLobbyWorld(server);
        prepareLobbyPlatform(lobby);
        teleportAllToLobby(server);
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        readyPlayers.remove(player.getUuid());
        importantNotices.remove(player.getUuid());
        supplyBoatDropConfirmUntil.remove(player.getUuid());
        respawnInvulnerableUntil.remove(player.getUuid());
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }
        syncJoinedPlayer(player, server);
    }

    public void onPlayerLeave(UUID playerId, MinecraftServer server) {
        readyPlayers.remove(playerId);
        importantNotices.remove(playerId);
        supplyBoatDropConfirmUntil.remove(playerId);
        respawnInvulnerableUntil.remove(playerId);
        sidebarInitialized.remove(playerId);
        sidebarLineCounts.remove(playerId);
        evaluateReadyCountdown(server);
    }

    public void onPlayerWorldChange(ServerPlayerEntity player, RegistryKey<World> from, RegistryKey<World> to) {
        if (dimensionRedirectGuard.remove(player.getUuid())) {
            return;
        }

        RegistryKey<World> target = resolvePortalRedirect(from, to);
        if (target == null) {
            return;
        }

        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }
        ServerWorld targetWorld = server.getWorld(target);
        if (targetWorld == null) {
            return;
        }

        BlockPos source = player.getBlockPos();
        BlockPos destination = portalTargetPos(from, target, source, targetWorld);
        dimensionRedirectGuard.add(player.getUuid());
        player.teleport(targetWorld, destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, Set.of(), player.getYaw(), player.getPitch(), false);
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
        List<String> blocked = noRespawnDimensions.stream().map(key -> key.getValue().toString()).sorted().toList();
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
            Identifier id = Identifier.of(rawDimension);
            return noRespawnDimensions.add(RegistryKey.of(RegistryKeys.WORLD, id));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean removeNoRespawnDimension(String rawDimension) {
        try {
            Identifier id = Identifier.of(rawDimension);
            return noRespawnDimensions.remove(RegistryKey.of(RegistryKeys.WORLD, id));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public void clearNoRespawnDimensions() {
        noRespawnDimensions.clear();
    }

    public void markReady(ServerPlayerEntity player) {
        if (engine.isRunning()) {
            sendOverlay(player, text("ready.in_match"));
            return;
        }

        if (!readyPlayers.add(player.getUuid())) {
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

    public void unready(ServerPlayerEntity player) {
        if (!readyPlayers.remove(player.getUuid())) {
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

    public boolean shouldCancelFriendlyFire(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        if (attacker == null || target == null) {
            return false;
        }
        if (attacker.getUuid().equals(target.getUuid())) {
            return false;
        }
        if (target.getEntityWorld().getRegistryKey().equals(LOBBY_OVERWORLD_KEY)) {
            return !attacker.isSpectator() && !target.isSpectator();
        }
        if (!engine.isRunning()) {
            return false;
        }
        Integer attackerTeam = engine.teamForPlayer(attacker.getUuid());
        Integer targetTeam = engine.teamForPlayer(target.getUuid());
        return attackerTeam != null
            && attackerTeam.equals(targetTeam)
            && !attacker.isSpectator()
            && !target.isSpectator();
    }

    public void notifyFriendlyFireBlocked(ServerPlayerEntity attacker) {
        if (attacker != null) {
            sendOverlay(attacker, text(attacker.getEntityWorld().getRegistryKey().equals(LOBBY_OVERWORLD_KEY) ? "lobby.pvp_denied" : "combat.friendly_fire_blocked"));
        }
    }

    public String start(MinecraftServer server) {
        if (hasActiveMatchSession()) {
            return text("match.already_running");
        }

        ServerWorld matchWorld = resolveBattleOverworld(server);
        if (matchWorld == null) {
            return text("match.missing_dimension", BATTLE_OVERWORLD_KEY.getValue());
        }

        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        if (players.size() < teamCount) {
            return text("match.need_players", teamCount);
        }

        cancelCountdown();
        readyPlayers.clear();
        clearRoundState();

        activeMatchCenter = selectMatchCenter(matchWorld);
        previousMatchCenter = activeMatchCenter;

        List<UUID> ids = players.stream().map(PlayerEntity::getUuid).toList();
        StartResult result = engine.start(ids, new GameOptions(teamCount, healthPreset));

        Map<Integer, SpawnPoint> resolved = resolveTeamSpawns(matchWorld, activeMatchCenter, teamCount);
        teamSpawns.putAll(resolved);

        for (ServerPlayerEntity player : players) {
            int team = result.teamByPlayer().get(player.getUuid());
            SpawnPoint base = teamSpawns.get(team);
            if (base == null) {
                continue;
            }
            SpawnPoint randomized = randomizeAround(base, server);
            preparePlayerForMatch(player);
            teleport(player, randomized);
            applyHealthPreset(player);
            sendOverlay(player, text("match.player_assignment", teamLabel(team), healthPresetLabel(healthPreset)));
        }

        broadcastImportantNotice(server, text("match.notice.start.title"));
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
            return text("match.status.idle.mod", readyPlayers.size(), server.getPlayerManager().getPlayerList().size());
        }
        List<Integer> beds = new ArrayList<>(teamBeds.keySet());
        Collections.sort(beds);
        return text("match.status.running", teamCount, healthPresetLabel(healthPreset), beds, stateLabel(noRespawnEnabled));
    }

    public ActionResult onUseBlock(ServerPlayerEntity player, Hand hand, BlockHitResult hitResult) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        BlockPos clickedPos = hitResult.getBlockPos();
        BlockState clickedState = world.getBlockState(clickedPos);

        if (clickedState.isIn(BlockTags.BEDS)) {
            player.sendMessage(Text.literal(text("bed.personal_spawn_disabled")), true);
            return ActionResult.FAIL;
        }

        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof BedItem)) {
            return ActionResult.PASS;
        }

        BlockPos placePos = clickedPos.offset(hitResult.getSide());
        if (!stack.isOf(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            if (!player.isCreative()) {
                stack.decrement(1);
            }
            explode(world, placePos, player);
            return ActionResult.FAIL;
        }

        if (!engine.isRunning()) {
            player.sendMessage(Text.literal(text("bed.place_only_in_match")), true);
            return ActionResult.FAIL;
        }

        Integer team = engine.teamForPlayer(player.getUuid());
        if (team == null) {
            player.sendMessage(Text.literal(text("bed.not_on_team")), true);
            return ActionResult.FAIL;
        }

        Integer ownerTeam = resolveTeamBedOwner(stack);
        if (ownerTeam == null || !ownerTeam.equals(team)) {
            if (!player.isCreative()) {
                stack.decrement(Math.min(2, stack.getCount()));
            }
            explode(world, placePos, player);
            player.sendMessage(Text.literal(text("bed.wrong_team", ownerTeam != null ? teamLabel(ownerTeam) : text("scoreboard.value.none"))), true);
            return ActionResult.FAIL;
        }

        if (!player.isCreative() && stack.getCount() > 1) {
            stack.setCount(1);
        }
        pendingBedPlacements.add(new PendingBedPlacement(player.getUuid(), ownerTeam, hand, world.getRegistryKey(), placePos, world.getTime() + 5));
        return ActionResult.PASS;
    }

    public void onBlockBreak(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
        if (!state.isIn(BlockTags.BEDS)) {
            return;
        }
        SpawnPoint normalized = normalizeBedFoot(world.getRegistryKey(), pos, state);
        Integer ownerTeam = placedTeamBedOwners.remove(normalized);
        if (ownerTeam != null) {
            if (!player.isCreative()) {
                pendingBrokenTeamBedDrops.add(new PendingBrokenTeamBedDrop(normalized, ownerTeam, world.getTime() + 1));
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
        if (entity instanceof ServerPlayerEntity deadPlayer) {
            lockNoRespawnIfBlocked(deadPlayer);
            handlePlayerDeath(deadPlayer);
            return;
        }

        if (!(entity instanceof EnderDragonEntity)) {
            return;
        }

        Entity attacker = source.getAttacker();
        if (!(attacker instanceof ServerPlayerEntity killer)) {
            return;
        }

        Integer winner = engine.onObjectiveCompleted(killer.getUuid());
        if (winner == null) {
            return;
        }

        MinecraftServer server = serverOf(killer);
        if (server == null) {
            return;
        }
        stopInternal(server, true, text("match.win_reason.dragon", killer.getName().getString()));
    }

    public void onRespawn(ServerPlayerEntity player) {
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }

        if (!engine.isRunning()) {
            teleportToLobby(player);
            return;
        }

        if (isRespawnLocked(player)) {
            player.changeGameMode(GameMode.SPECTATOR);
            teleport(player, resolveSpectatorSpawn(server));
            setImportantNotice(player, text("player.eliminated.no_respawn"), IMPORTANT_NOTICE_TICKS);
            return;
        }

        Integer team = engine.teamForPlayer(player.getUuid());
        if (team == null) {
            moveUnassignedPlayerToSpectator(player, server, true);
            return;
        }

        SpawnPoint respawn = resolveRespawn(team, server);
        if (respawn == null) {
            return;
        }
        teleport(player, respawn);
        applyHealthPreset(player);
        ensureSupplyBoat(player);
        player.changeGameMode(GameMode.SURVIVAL);
        grantRespawnInvulnerability(player);
    }

    public void onEntityLoad(Entity entity, ServerWorld world) {
        if (!(entity instanceof ItemEntity itemEntity)) {
            return;
        }

        Entity owner = itemEntity.getOwner();
        if (owner instanceof ServerPlayerEntity player && !player.isCreative() && itemEntity.getStack().isOf(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            itemEntity.getStack().setCount(1);
            consumeResidualDroppedTeamBedToken(player, itemEntity.getStack());
            return;
        }

        if (!isDiscardProtectedBoatItem(itemEntity.getStack())) {
            return;
        }
        if (!(owner instanceof ServerPlayerEntity player)) {
            return;
        }
        if (!player.isAlive()) {
            if (isSupplyBoatItem(itemEntity.getStack())) {
                itemEntity.discard();
            }
            return;
        }
        if (player.isCreative()) {
            return;
        }

        long tick = world.getServer().getOverworld().getTime();
        Long expiresAt = supplyBoatDropConfirmUntil.get(player.getUuid());
        if (expiresAt != null && expiresAt >= tick) {
            supplyBoatDropConfirmUntil.remove(player.getUuid());
            itemEntity.discard();
            sendOverlay(player, text("boat.discarded"));
            return;
        }

        supplyBoatDropConfirmUntil.put(player.getUuid(), tick + SUPPLY_BOAT_DROP_CONFIRM_TICKS);
        ItemStack droppedStack = itemEntity.getStack().copy();
        itemEntity.discard();
        giveBoatBack(player, droppedStack);
        sendOverlay(player, text("boat.drop_confirm"));
    }

    public void onServerTick(MinecraftServer server) {
        processPendingBedPlacements(server);
        processPendingBrokenTeamBedDrops(server);
        normalizeTeamBedInventories(server);
        processRespawnInvulnerability(server);
        processImportantNotices(server);
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
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            applySidebar(player, server);
        }
    }

    private void applySidebar(ServerPlayerEntity player, MinecraftServer server) {
        if (player == null) {
            return;
        }

        if (sidebarInitialized.add(player.getUuid())) {
            ScoreboardObjective objective = createSidebarObjective();
            player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(objective, ScoreboardObjectiveUpdateS2CPacket.ADD_MODE));
            player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, objective));
        }

        List<String> lines = buildSidebarLines(player, server);
        int used = Math.min(lines.size(), SIDEBAR_MAX_LINES);
        int previous = sidebarLineCounts.getOrDefault(player.getUuid(), 0);
        for (int index = 0; index < used; index++) {
            player.networkHandler.sendPacket(
                new ScoreboardScoreUpdateS2CPacket(
                    sidebarEntry(index),
                    SIDEBAR_OBJECTIVE_NAME,
                    used - index,
                    Optional.of(Text.literal(lines.get(index))),
                    Optional.empty()
                )
            );
        }
        for (int index = used; index < previous; index++) {
            player.networkHandler.sendPacket(new ScoreboardScoreResetS2CPacket(sidebarEntry(index), SIDEBAR_OBJECTIVE_NAME));
        }
        sidebarLineCounts.put(player.getUuid(), used);
        applyNameColorTeams(player, server);
        applyTabListDisplay(player, server);
    }

    private void applyNameColorTeams(ServerPlayerEntity viewer, MinecraftServer server) {
        if (viewer == null || server == null) {
            return;
        }
        if (!tabEnabled) {
            clearNameColorTeams(viewer);
            return;
        }

        Scoreboard scoreboard = new Scoreboard();
        Team neutralTeam = createNameColorTeam(scoreboard, NAME_COLOR_TEAM_NEUTRAL, Formatting.WHITE, Text.empty());
        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            Team bucket = resolveNameColorTeam(viewer, scoreboard, target, neutralTeam);
            scoreboard.addScoreHolderToTeam(target.getName().getString(), bucket);
        }

        clearNameColorTeams(viewer);
        for (Team team : scoreboard.getTeams()) {
            viewer.networkHandler.sendPacket(TeamS2CPacket.updateTeam(team, true));
        }
    }

    private Team createNameColorTeam(Scoreboard scoreboard, String teamName, Formatting color, Text prefix) {
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.addTeam(teamName);
        }
        team.setDisplayName(Text.literal(teamName));
        team.setColor(color);
        team.setPrefix(prefix == null ? Text.empty() : prefix);
        return team;
    }

    private Team resolveNameColorTeam(ServerPlayerEntity viewer, Scoreboard scoreboard, ServerPlayerEntity target, Team neutralTeam) {
        if (!engine.isRunning()) {
            return neutralTeam;
        }
        Integer targetTeam = target == null ? null : engine.teamForPlayer(target.getUuid());
        if (targetTeam != null) {
            Integer viewerTeam = viewer != null ? engine.teamForPlayer(viewer.getUuid()) : null;
            Formatting color = resolveTabTeamColor(viewerTeam, targetTeam);
            return createNameColorTeam(
                scoreboard,
                NAME_COLOR_TEAM_PREFIX + "team_" + targetTeam,
                color,
                buildTabTeamPrefix(targetTeam, color)
            );
        }
        return neutralTeam;
    }

    private void applyTabListDisplay(ServerPlayerEntity player, MinecraftServer server) {
        if (player == null || server == null) {
            return;
        }
        if (!tabEnabled) {
            clearTabListDisplay(player);
            return;
        }
        player.networkHandler.sendPacket(new PlayerListHeaderS2CPacket(buildTabListHeader(), buildTabListFooter(player, server)));
    }

    private Text buildTabListHeader() {
        MutableText header = Text.literal(text("scoreboard.title")).formatted(Formatting.GOLD, Formatting.BOLD);
        header.append(Text.literal("\n"));
        header.append(
            Text.literal(text("scoreboard.line.state", text(engine.isRunning() ? "scoreboard.state.running" : "scoreboard.state.lobby")))
                .formatted(Formatting.YELLOW)
        );
        return header;
    }

    private Text buildTabListFooter(ServerPlayerEntity viewer, MinecraftServer server) {
        MutableText footer = Text.empty();
        if (!engine.isRunning()) {
            appendTabSection(footer, text("scoreboard.line.ready", readyPlayers.size(), server.getPlayerManager().getPlayerList().size()), Formatting.GREEN);
            appendTabSeparator(footer);
            appendTabSection(footer, text("scoreboard.line.teams", teamCount), Formatting.AQUA);
            footer.append(Text.literal("\n"));
            appendTabSection(footer, text("scoreboard.line.health", healthPresetLabel(healthPreset)), Formatting.RED);
            appendTabSeparator(footer);
            appendTabSection(footer, text("scoreboard.line.norespawn", stateLabel(noRespawnEnabled)), Formatting.LIGHT_PURPLE);
            return footer;
        }

        Integer playerTeam = engine.teamForPlayer(viewer.getUuid());
        appendTabSection(
            footer,
            text("scoreboard.line.team", playerTeam != null ? teamLabel(playerTeam) : text("scoreboard.value.none")),
            Formatting.GREEN
        );
        appendTabSeparator(footer);
        appendTabSection(
            footer,
            text("scoreboard.line.team_bed", playerTeam != null ? bedStatusText(teamBeds.containsKey(playerTeam)) : text("scoreboard.value.none")),
            Formatting.AQUA
        );
        footer.append(Text.literal("\n"));
        appendTabSection(footer, text("scoreboard.line.dimension", dimensionLabel(viewer)), Formatting.DARK_AQUA);
        appendTabSeparator(footer);
        appendTabSection(footer, text("scoreboard.line.norespawn", stateLabel(noRespawnEnabled)), Formatting.LIGHT_PURPLE);
        return footer;
    }

    private void appendTabSection(MutableText target, String content, Formatting color) {
        target.append(Text.literal(content).formatted(color));
    }

    private void appendTabSeparator(MutableText target) {
        target.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY));
    }

    private void clearTabListDisplay(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        player.networkHandler.sendPacket(new PlayerListHeaderS2CPacket(Text.empty(), Text.empty()));
    }

    private void clearNameColorTeams(ServerPlayerEntity viewer) {
        if (viewer == null) {
            return;
        }
        viewer.networkHandler.sendPacket(TeamS2CPacket.updateRemovedTeam(createNameColorTeam(new Scoreboard(), NAME_COLOR_TEAM_NEUTRAL, Formatting.WHITE, Text.empty())));
        for (int team = 1; team <= 32; team++) {
            viewer.networkHandler.sendPacket(
                TeamS2CPacket.updateRemovedTeam(
                    createNameColorTeam(new Scoreboard(), NAME_COLOR_TEAM_PREFIX + "team_" + team, Formatting.WHITE, Text.empty())
                )
            );
        }
    }

    private void refreshTabState(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (tabEnabled) {
                applyNameColorTeams(player, server);
                applyTabListDisplay(player, server);
            } else {
                clearNameColorTeams(player);
                clearTabListDisplay(player);
            }
        }
    }

    private Formatting resolveTabTeamColor(Integer viewerTeam, int targetTeam) {
        if (viewerTeam != null) {
            return viewerTeam == targetTeam ? Formatting.GREEN : Formatting.RED;
        }
        return TAB_TEAM_COLORS[Math.floorMod(targetTeam - 1, TAB_TEAM_COLORS.length)];
    }

    private Text buildTabTeamPrefix(int team, Formatting color) {
        MutableText prefix = Text.literal("[").formatted(Formatting.DARK_GRAY);
        prefix.append(Text.literal("T" + team).formatted(color));
        prefix.append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
        return prefix;
    }

    private ScoreboardObjective createSidebarObjective() {
        return new ScoreboardObjective(
            new Scoreboard(),
            SIDEBAR_OBJECTIVE_NAME,
            ScoreboardCriterion.DUMMY,
            Text.literal(text("scoreboard.title")),
            ScoreboardCriterion.DUMMY.getDefaultRenderType(),
            false,
            null
        );
    }

    private String sidebarEntry(int index) {
        return SIDEBAR_ENTRY_PREFIX + index;
    }

    private List<String> buildSidebarLines(ServerPlayerEntity viewer, MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        if (!engine.isRunning()) {
            lines.add(text("scoreboard.line.state", text("scoreboard.state.lobby")));
            lines.add(text("scoreboard.line.ready", readyPlayers.size(), server.getPlayerManager().getPlayerList().size()));
            lines.add(text("scoreboard.line.teams", teamCount));
            lines.add(text("scoreboard.line.health", healthPresetLabel(healthPreset)));
            lines.add(text("scoreboard.line.norespawn", stateLabel(noRespawnEnabled)));
            return lines;
        }

        Integer playerTeam = engine.teamForPlayer(viewer.getUuid());
        lines.add(text("scoreboard.line.state", text("scoreboard.state.running")));
        lines.add(text("scoreboard.line.team", playerTeam != null ? teamLabel(playerTeam) : text("scoreboard.value.none")));
        lines.add(text("scoreboard.line.team_bed", playerTeam != null ? bedStatusText(teamBeds.containsKey(playerTeam)) : text("scoreboard.value.none")));
        lines.add(text("scoreboard.line.respawn", respawnStatusText(viewer, server)));
        lines.add(text("scoreboard.line.teams", teamCount));
        lines.add(text("scoreboard.line.beds", teamBeds.size()));
        lines.add(text("scoreboard.line.dimension", dimensionLabel(viewer)));
        lines.add(text("scoreboard.line.norespawn", stateLabel(noRespawnEnabled)));
        return lines;
    }

    private void processPendingBedPlacements(MinecraftServer server) {
        if (pendingBedPlacements.isEmpty()) {
            return;
        }

        long tick = server.getOverworld().getTime();
        Iterator<PendingBedPlacement> iterator = pendingBedPlacements.iterator();
        while (iterator.hasNext()) {
            PendingBedPlacement pending = iterator.next();
            ServerWorld world = server.getWorld(pending.worldKey());
            if (world == null) {
                iterator.remove();
                continue;
            }

            if (tick > pending.expiresAtTick()) {
                iterator.remove();
                continue;
            }

            BlockState state = world.getBlockState(pending.approxPos());
            if (!state.isIn(BlockTags.BEDS)) {
                continue;
            }

            SpawnPoint bed = normalizeBedFoot(pending.worldKey(), pending.approxPos(), state);
            placedTeamBedOwners.put(bed, pending.team());
            teamBeds.put(pending.team(), bed);

            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(pending.ownerId());
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

        long tick = server.getOverworld().getTime();
        Iterator<PendingBrokenTeamBedDrop> iterator = pendingBrokenTeamBedDrops.iterator();
        while (iterator.hasNext()) {
            PendingBrokenTeamBedDrop pending = iterator.next();
            if (tick < pending.processAtTick()) {
                continue;
            }

            ServerWorld world = server.getWorld(pending.spawnPoint().worldKey());
            if (world == null) {
                iterator.remove();
                continue;
            }

            Box searchBox = new Box(pending.spawnPoint().pos()).expand(1.5D);
            ItemEntity vanillaDrop = world.getEntitiesByClass(
                ItemEntity.class,
                searchBox,
                entity -> entity.isAlive() && entity.getStack().isOf(Items.WHITE_BED)
            ).stream().findFirst().orElse(null);
            if (vanillaDrop != null) {
                vanillaDrop.discard();
            }

            world.spawnEntity(new ItemEntity(
                world,
                pending.spawnPoint().pos().getX() + 0.5D,
                pending.spawnPoint().pos().getY() + 0.5D,
                pending.spawnPoint().pos().getZ() + 0.5D,
                createOwnedTeamBedItem(pending.ownerTeam())
            ));
            iterator.remove();
        }
    }

    private void processImportantNotices(MinecraftServer server) {
        if (importantNotices.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, TimedNotice>> iterator = importantNotices.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TimedNotice> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
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
        List<ServerPlayerEntity> online = server.getPlayerManager().getPlayerList();
        if (online.size() < teamCount) {
            return text("ready.countdown_cancel_players");
        }

        for (ServerPlayerEntity player : online) {
            if (!readyPlayers.contains(player.getUuid())) {
                return text("ready.countdown_cancel_unready");
            }
        }
        return null;
    }

    private void cancelCountdown() {
        countdownRemainingSeconds = -1;
        countdownTickBudget = 0;
    }

    private void handlePlayerDeath(ServerPlayerEntity deadPlayer) {
        DeathResult result = engine.onPlayerDeath(deadPlayer.getUuid());
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
            if (victimId.equals(deadPlayer.getUuid())) {
                continue;
            }
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(victimId);
            if (target == null || target.isDead()) {
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
        prepareLobbyPlatform(resolveLobbyWorld(server));
        teleportAllToLobby(server);
        resetBattleDimensionData(server);
    }

    private void clearRoundState() {
        teamSpawns.clear();
        teamBeds.clear();
        placedTeamBedOwners.clear();
        pendingBedPlacements.clear();
        pendingBrokenTeamBedDrops.clear();
        supplyBoatDropConfirmUntil.clear();
        importantNotices.clear();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        activeMatchCenter = null;
    }

    private void resetBattleDimensionData(MinecraftServer server) {
        for (RegistryKey<World> key : BATTLE_DIMENSIONS) {
            Path folder = server.getSavePath(WorldSavePath.ROOT)
                .resolve("dimensions")
                .resolve(key.getValue().getNamespace())
                .resolve(key.getValue().getPath());
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

    private void lockNoRespawnIfBlocked(ServerPlayerEntity deadPlayer) {
        if (!engine.isRunning() || !noRespawnEnabled) {
            return;
        }
        if (noRespawnDimensions.contains(deadPlayer.getEntityWorld().getRegistryKey())) {
            noRespawnLockedPlayers.add(deadPlayer.getUuid());
        }
    }

    private boolean isRespawnLocked(ServerPlayerEntity player) {
        return noRespawnEnabled && noRespawnLockedPlayers.contains(player.getUuid());
    }

    private void prepareLobbyPlatform(ServerWorld world) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                world.setBlockState(new BlockPos(x, LOBBY_PLATFORM_Y, z), Blocks.GLASS.getDefaultState(), 3);
                world.setBlockState(new BlockPos(x, LOBBY_PLATFORM_Y + 1, z), Blocks.AIR.getDefaultState(), 3);
                world.setBlockState(new BlockPos(x, LOBBY_PLATFORM_Y + 2, z), Blocks.AIR.getDefaultState(), 3);
            }
        }
        world.setBlockState(new BlockPos(0, LOBBY_PLATFORM_Y, 0), Blocks.SEA_LANTERN.getDefaultState(), 3);
    }

    private void teleportAllToLobby(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            teleportToLobby(player);
        }
    }

    private void teleportToLobby(ServerPlayerEntity player) {
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }
        ServerWorld lobby = resolveLobbyWorld(server);
        preparePlayerForLobby(player);
        teleport(player, new SpawnPoint(lobby.getRegistryKey(), LOBBY_SPAWN_POS));
    }

    private boolean hasActiveMatchSession() {
        return engine.isRunning() || countdownRemainingSeconds >= 0;
    }

    private void syncJoinedPlayer(ServerPlayerEntity player, MinecraftServer server) {
        if (!engine.isRunning()) {
            prepareLobbyPlatform(resolveLobbyWorld(server));
            teleportToLobby(player);
            player.sendMessage(Text.literal(text("lobby.guide")), false);
            return;
        }

        Integer team = engine.teamForPlayer(player.getUuid());
        if (team == null) {
            moveUnassignedPlayerToSpectator(player, server, true);
            return;
        }

        if (isRespawnLocked(player)) {
            player.changeGameMode(GameMode.SPECTATOR);
            if (!BATTLE_DIMENSIONS.contains(player.getEntityWorld().getRegistryKey())) {
                teleport(player, resolveSpectatorSpawn(server));
            }
            return;
        }

        player.changeGameMode(GameMode.SURVIVAL);
        if (!BATTLE_DIMENSIONS.contains(player.getEntityWorld().getRegistryKey())) {
            SpawnPoint respawn = resolveRespawn(team, server);
            if (respawn != null) {
                teleport(player, respawn);
            }
        }
    }

    private void moveUnassignedPlayerToSpectator(ServerPlayerEntity player, MinecraftServer server, boolean notify) {
        preparePlayerForSpectator(player);
        teleport(player, resolveSpectatorSpawn(server));
        if (notify) {
            player.sendMessage(Text.literal(text("player.unassigned_spectator")), false);
        }
    }

    private SpawnPoint resolveSpectatorSpawn(MinecraftServer server) {
        SpawnPoint teamSpawn = teamSpawns.values().stream().findFirst().orElse(null);
        if (teamSpawn != null) {
            return new SpawnPoint(teamSpawn.worldKey(), teamSpawn.pos().up(8));
        }

        ServerWorld battleWorld = resolveBattleOverworld(server);
        if (battleWorld != null) {
            BlockPos center = activeMatchCenter != null ? activeMatchCenter : battleWorld.getSpawnPoint().getPos();
            return new SpawnPoint(battleWorld.getRegistryKey(), center.up(8));
        }

        ServerWorld lobby = resolveLobbyWorld(server);
        return new SpawnPoint(lobby.getRegistryKey(), LOBBY_SPAWN_POS);
    }

    private BlockPos selectMatchCenter(ServerWorld world) {
        BlockPos worldSpawn = world.getSpawnPoint().getPos();
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

    private ServerWorld resolveLobbyWorld(MinecraftServer server) {
        ServerWorld lobby = server.getWorld(LOBBY_OVERWORLD_KEY);
        return lobby != null ? lobby : server.getOverworld();
    }

    private ServerWorld resolveBattleOverworld(MinecraftServer server) {
        return server.getWorld(BATTLE_OVERWORLD_KEY);
    }

    private RegistryKey<World> resolvePortalRedirect(RegistryKey<World> from, RegistryKey<World> to) {
        if (engine.isRunning()) {
            if (from.equals(BATTLE_OVERWORLD_KEY) && to.equals(World.NETHER)) {
                return BATTLE_NETHER_KEY;
            }
            if (from.equals(BATTLE_NETHER_KEY) && to.equals(World.OVERWORLD)) {
                return BATTLE_OVERWORLD_KEY;
            }
            if (from.equals(BATTLE_OVERWORLD_KEY) && to.equals(World.END)) {
                return BATTLE_END_KEY;
            }
            if (from.equals(BATTLE_END_KEY) && to.equals(World.OVERWORLD)) {
                return BATTLE_OVERWORLD_KEY;
            }
            return null;
        }

        if (from.equals(LOBBY_OVERWORLD_KEY) && (to.equals(World.NETHER) || to.equals(World.END))) {
            return LOBBY_OVERWORLD_KEY;
        }
        return null;
    }

    private BlockPos portalTargetPos(RegistryKey<World> from, RegistryKey<World> target, BlockPos sourcePos, ServerWorld targetWorld) {
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

    private Map<Integer, SpawnPoint> resolveTeamSpawns(ServerWorld world, BlockPos center, int teams) {
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
            resolved.put(team, new SpawnPoint(world.getRegistryKey(), candidate));
        }
        return resolved;
    }

    private BlockPos findRandomTeamBase(ServerWorld world, BlockPos center, List<BlockPos> chosen, int minDistance) {
        for (int attempt = 0; attempt < SPAWN_SEARCH_ATTEMPTS; attempt++) {
            BlockPos flat = randomPointInRadius(center, RANDOM_SPAWN_RADIUS);
            if (!isFarEnough(flat, chosen, minDistance)) {
                continue;
            }
            return safeSurface(world, flat.getX(), flat.getZ());
        }
        return null;
    }

    private BlockPos fallbackRandomTeamBase(ServerWorld world, BlockPos center) {
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
        BlockPos offset = base.pos().add(dx, 0, dz);

        ServerWorld world = server.getWorld(base.worldKey());
        if (world == null) {
            return base;
        }

        return new SpawnPoint(base.worldKey(), safeSurface(world, offset.getX(), offset.getZ()));
    }

    private SpawnPoint resolveRespawn(int team, MinecraftServer server) {
        SpawnPoint bed = teamBeds.get(team);
        if (bed != null) {
            ServerWorld bedWorld = server.getWorld(bed.worldKey());
            if (bedWorld != null && bedWorld.getBlockState(bed.pos()).isIn(BlockTags.BEDS)) {
                return new SpawnPoint(bed.worldKey(), safeSurface(bedWorld, bed.pos().getX(), bed.pos().getZ()));
            }
            teamBeds.remove(team);
        }

        SpawnPoint spawn = teamSpawns.get(team);
        if (spawn == null) {
            return null;
        }
        ServerWorld world = server.getWorld(spawn.worldKey());
        if (world == null) {
            return null;
        }
        return new SpawnPoint(spawn.worldKey(), safeSurface(world, spawn.pos().getX(), spawn.pos().getZ()));
    }

    private MinecraftServer serverOf(ServerPlayerEntity player) {
        return player.getCommandSource().getServer();
    }

    private void teleport(ServerPlayerEntity player, SpawnPoint point) {
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }

        ServerWorld targetWorld = server.getWorld(point.worldKey());
        if (targetWorld == null) {
            return;
        }

        player.teleport(targetWorld, point.pos().getX() + 0.5D, point.pos().getY(), point.pos().getZ() + 0.5D, Set.of(), player.getYaw(), player.getPitch(), false);
    }

    private BlockPos randomEndSpawn(ServerWorld world) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int radius = END_SPAWN_MIN_RADIUS + random.nextInt((END_SPAWN_MAX_RADIUS - END_SPAWN_MIN_RADIUS) + 1);
        int x = (int) Math.round(Math.cos(angle) * radius);
        int z = (int) Math.round(Math.sin(angle) * radius);
        return safeSurface(world, x, z);
    }

    private BlockPos safeSurface(ServerWorld world, int x, int z) {
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

        if (world.getRegistryKey().equals(World.NETHER) || world.getRegistryKey().equals(BATTLE_NETHER_KEY)) {
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
            return new BlockPos(x, y, z);
        }
        return createFallbackSpawnPlatform(world, x, z);
    }

    private BlockPos findSafeSurfaceAt(ServerWorld world, int x, int z) {
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int offset = -3; offset <= 3; offset++) {
            BlockPos candidate = new BlockPos(x, topY + 1 + offset, z);
            if (isSafeSpawnLocation(world, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isSafeSpawnLocation(ServerWorld world, BlockPos candidate) {
        BlockPos groundPos = candidate.down();
        BlockState ground = world.getBlockState(groundPos);
        BlockState feet = world.getBlockState(candidate);
        BlockState head = world.getBlockState(candidate.up());
        if (ground.isAir() || isHazardousGround(ground) || !ground.blocksMovement() || !isAllowedSpawnGround(world, ground)) {
            return false;
        }
        if (!feet.isAir() || !head.isAir()) {
            return false;
        }
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty();
    }

    private boolean isAllowedSpawnGround(ServerWorld world, BlockState ground) {
        RegistryKey<World> worldKey = world.getRegistryKey();
        if (worldKey.equals(World.END) || worldKey.equals(BATTLE_END_KEY)) {
            return ground.isOf(Blocks.END_STONE);
        }
        if (worldKey.equals(World.OVERWORLD) || worldKey.equals(LOBBY_OVERWORLD_KEY) || worldKey.equals(BATTLE_OVERWORLD_KEY)) {
            return ground.isOf(Blocks.GRASS_BLOCK);
        }
        return !ground.isIn(BlockTags.LEAVES);
    }

    private BlockPos createFallbackSpawnPlatform(ServerWorld world, int x, int z) {
        int feetY = Math.max(world.getSeaLevel() + 2, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) + 1);
        int platformY = feetY - 1;
        BlockState topState = fallbackPlatformTopState(world);
        BlockState foundationState = topState.isOf(Blocks.GRASS_BLOCK) ? Blocks.DIRT.getDefaultState() : topState;

        for (int dx = -FORCED_PLATFORM_RADIUS; dx <= FORCED_PLATFORM_RADIUS; dx++) {
            for (int dz = -FORCED_PLATFORM_RADIUS; dz <= FORCED_PLATFORM_RADIUS; dz++) {
                for (int depth = 1; depth <= FORCED_PLATFORM_FOUNDATION_DEPTH; depth++) {
                    world.setBlockState(new BlockPos(x + dx, platformY - depth, z + dz), foundationState, Block.NOTIFY_ALL);
                }
                world.setBlockState(new BlockPos(x + dx, platformY, z + dz), topState, Block.NOTIFY_ALL);
                for (int clearance = 1; clearance <= FORCED_PLATFORM_CLEARANCE; clearance++) {
                    world.setBlockState(new BlockPos(x + dx, platformY + clearance, z + dz), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }

        return new BlockPos(x, platformY + 1, z);
    }

    private BlockState fallbackPlatformTopState(ServerWorld world) {
        RegistryKey<World> worldKey = world.getRegistryKey();
        if (worldKey.equals(World.END) || worldKey.equals(BATTLE_END_KEY)) {
            return Blocks.END_STONE.getDefaultState();
        }
        if (worldKey.equals(World.OVERWORLD) || worldKey.equals(LOBBY_OVERWORLD_KEY) || worldKey.equals(BATTLE_OVERWORLD_KEY)) {
            return Blocks.GRASS_BLOCK.getDefaultState();
        }
        return Blocks.STONE.getDefaultState();
    }

    private boolean isHazardousGround(BlockState state) {
        return state.isOf(Blocks.WATER)
            || state.isOf(Blocks.LAVA)
            || state.isOf(Blocks.MAGMA_BLOCK)
            || state.isOf(Blocks.CACTUS)
            || state.isOf(Blocks.CAMPFIRE)
            || state.isOf(Blocks.SOUL_CAMPFIRE)
            || state.isOf(Blocks.SWEET_BERRY_BUSH)
            || state.isOf(Blocks.POWDER_SNOW)
            || state.isOf(Blocks.COBWEB)
            || state.isOf(Blocks.FIRE)
            || state.isOf(Blocks.SOUL_FIRE)
            || state.isIn(BlockTags.LEAVES)
            || !state.getFluidState().isEmpty();
    }

    private void sendOverlay(ServerPlayerEntity player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        player.sendMessage(Text.literal(message), true);
    }

    private void broadcastOverlay(MinecraftServer server, String message) {
        if (server == null || message == null || message.isBlank()) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendOverlay(player, message);
        }
    }

    private void setImportantNotice(ServerPlayerEntity player, String message, int ticks) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        importantNotices.put(player.getUuid(), new TimedNotice(message, Math.max(1, ticks)));
        sendOverlay(player, message);
    }

    private void broadcastImportantNotice(MinecraftServer server, String message) {
        if (server == null || message == null || message.isBlank()) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            setImportantNotice(player, message, IMPORTANT_NOTICE_TICKS);
        }
    }

    private void notifyTeamWiped(MinecraftServer server, int team, List<UUID> victims) {
        if (server == null || victims == null || victims.isEmpty()) {
            return;
        }
        String message = text("match.life_bind_wipe", teamLabel(team));
        for (UUID victimId : victims) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(victimId);
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

    private String respawnStatusText(ServerPlayerEntity player, MinecraftServer server) {
        if (isRespawnLocked(player)) {
            return text("scoreboard.respawn.locked");
        }
        return text("scoreboard.respawn.ready");
    }

    private String dimensionLabel(ServerPlayerEntity player) {
        if (player == null || !(player.getEntityWorld() instanceof ServerWorld world)) {
            return text("scoreboard.dimension.unknown");
        }
        RegistryKey<World> worldKey = world.getRegistryKey();
        if (worldKey.equals(World.NETHER) || worldKey.equals(BATTLE_NETHER_KEY)) {
            return text("scoreboard.dimension.nether");
        }
        if (worldKey.equals(World.END) || worldKey.equals(BATTLE_END_KEY)) {
            return text("scoreboard.dimension.end");
        }
        if (worldKey.equals(World.OVERWORLD) || worldKey.equals(LOBBY_OVERWORLD_KEY) || worldKey.equals(BATTLE_OVERWORLD_KEY)) {
            return text("scoreboard.dimension.overworld");
        }
        return text("scoreboard.dimension.unknown");
    }

    public void openLobbyMenu(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        SimpleInventory inventory = new SimpleInventory(LOBBY_MENU_SIZE);
        populateLobbyMenuInventory(inventory, player);
        player.openHandledScreen(
            new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, ignored) -> new LobbyMenuScreenHandler(syncId, playerInventory, inventory),
                Text.literal(text("menu.inventory.title")).formatted(Formatting.DARK_AQUA)
            )
        );
    }

    public boolean isLobbyMenuItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        return LOBBY_MENU_TAG.equals(customData.copyNbt().getString(LOBBY_ITEM_TAG).orElse(""));
    }

    void bindCraftedTeamBed(ItemStack stack, UUID crafterId) {
        Integer team = crafterId == null ? null : engine.teamForPlayer(crafterId);
        if (team == null) {
            clearTeamBedOwner(stack);
            return;
        }
        setTeamBedOwner(stack, team);
    }

    private void preparePlayerForLobby(ServerPlayerEntity player) {
        resetPlayerState(player, true);
        giveLobbyItems(player);
    }

    private void preparePlayerForMatch(ServerPlayerEntity player) {
        resetPlayerState(player, false);
        giveMatchStarterItems(player);
    }

    private void preparePlayerForSpectator(ServerPlayerEntity player) {
        resetPlayerState(player, false);
        player.changeGameMode(GameMode.SPECTATOR);
    }

    private void resetPlayerState(ServerPlayerEntity player, boolean lobbyState) {
        player.closeHandledScreen();
        clearRespawnInvulnerability(player);
        player.getInventory().clear();
        player.getInventory().setSelectedSlot(0);
        player.clearStatusEffects();
        player.setExperiencePoints(0);
        player.setExperienceLevel(0);
        player.experienceProgress = 0.0F;
        player.setFireTicks(0);
        player.fallDistance = 0.0F;
        if (player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH) != null) {
            player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(20.0D);
        }
        player.setHealth(20.0F);
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0F);
        player.changeGameMode(lobbyState ? GameMode.ADVENTURE : GameMode.SURVIVAL);
    }

    private void giveLobbyItems(ServerPlayerEntity player) {
        player.getInventory().setStack(LOBBY_MENU_SLOT, createLobbyMenuItem());
        player.getInventory().setStack(LOBBY_GUIDE_SLOT, createWrittenBookItem("item.guide.name", "book.guide.title", "book.guide.author", "book.guide.page1", "book.guide.page2"));
        player.getInventory().setStack(LOBBY_RECIPES_SLOT, createWrittenBookItem("item.recipes.name", "book.recipes.title", "book.recipes.author", "book.recipes.page1", "book.recipes.page2"));
    }

    private void giveMatchStarterItems(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        player.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        player.equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        inventory.setStack(0, new ItemStack(Items.WOODEN_SWORD));
        inventory.setStack(1, new ItemStack(Items.WOODEN_PICKAXE));
        inventory.setStack(2, new ItemStack(Items.WOODEN_AXE));
        inventory.setStack(3, createSupplyBoatItem());
    }

    private ItemStack createLobbyMenuItem() {
        ItemStack item = new ItemStack(Items.COMPASS);
        item.set(DataComponentTypes.CUSTOM_NAME, Text.literal(text("item.menu.name")).formatted(Formatting.AQUA));
        NbtCompound tag = new NbtCompound();
        tag.putString(LOBBY_ITEM_TAG, LOBBY_MENU_TAG);
        item.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
        return item;
    }

    private ItemStack createSupplyBoatItem() {
        ItemStack item = new ItemStack(Items.OAK_BOAT);
        NbtComponent customData = item.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound tag = customData != null ? customData.copyNbt() : new NbtCompound();
        tag.putBoolean(SUPPLY_BOAT_TAG, true);
        item.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
        return item;
    }

    private boolean isSupplyBoatItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.OAK_BOAT)) {
            return false;
        }
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData != null && customData.copyNbt().getBoolean(SUPPLY_BOAT_TAG).orElse(false);
    }

    private boolean isDiscardProtectedBoatItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return path.endsWith("_boat") || path.endsWith("_raft");
    }

    private void ensureSupplyBoat(ServerPlayerEntity player) {
        if (player == null || !engine.isRunning()) {
            return;
        }
        supplyBoatDropConfirmUntil.remove(player.getUuid());
        if (playerHasSupplyBoat(player)) {
            return;
        }
        giveSupplyBoatBack(player);
    }

    private boolean playerHasSupplyBoat(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (isSupplyBoatItem(inventory.getStack(slot))) {
                return true;
            }
        }
        return false;
    }

    private void giveSupplyBoatBack(ServerPlayerEntity player) {
        giveBoatBack(player, createSupplyBoatItem());
    }

    private void giveBoatBack(ServerPlayerEntity player, ItemStack stack) {
        if (player == null) {
            return;
        }
        player.getInventory().insertStack(stack.copy());
        player.currentScreenHandler.sendContentUpdates();
        player.playerScreenHandler.sendContentUpdates();
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
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            normalizeTeamBedInventory(player);
        }
    }

    private void normalizeTeamBedInventory(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isOf(TeamLifeBindFabric.TEAM_BED_ITEM) || stack.getCount() >= 2 || isPendingTeamBedPlacementStack(player, stack)) {
                continue;
            }
            stack.setCount(2);
        }
    }

    private void consumeResidualDroppedTeamBedToken(ServerPlayerEntity player, ItemStack droppedStack) {
        if (player == null || player.isCreative() || droppedStack == null || !droppedStack.isOf(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            return;
        }
        Integer ownerTeam = resolveTeamBedOwner(droppedStack);
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!isResidualDroppedTeamBedStack(stack, ownerTeam)) {
                continue;
            }
            inventory.setStack(slot, ItemStack.EMPTY);
            player.currentScreenHandler.sendContentUpdates();
            player.playerScreenHandler.sendContentUpdates();
            return;
        }
    }

    private boolean isResidualDroppedTeamBedStack(ItemStack stack, Integer ownerTeam) {
        return stack != null
            && !stack.isEmpty()
            && stack.isOf(TeamLifeBindFabric.TEAM_BED_ITEM)
            && stack.getCount() == 1
            && sameTeamBedOwner(resolveTeamBedOwner(stack), ownerTeam);
    }

    private boolean sameTeamBedOwner(Integer left, Integer right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private boolean isPendingTeamBedPlacementStack(ServerPlayerEntity player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }
        for (PendingBedPlacement pending : pendingBedPlacements) {
            if (!pending.ownerId().equals(player.getUuid())) {
                continue;
            }
            if (stack == player.getStackInHand(pending.hand())) {
                return true;
            }
        }
        return false;
    }

    private void consumeResidualPlacedTeamBedToken(ServerPlayerEntity player, Hand hand) {
        if (player == null || player.isCreative()) {
            return;
        }
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            return;
        }
        stack.decrement(1);
    }

    private void grantRespawnInvulnerability(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }
        player.setInvulnerable(true);
        respawnInvulnerableUntil.put(player.getUuid(), server.getOverworld().getTime() + RESPAWN_INVULNERABILITY_TICKS);
    }

    private void processRespawnInvulnerability(MinecraftServer server) {
        if (server == null || respawnInvulnerableUntil.isEmpty()) {
            return;
        }
        long tick = server.getOverworld().getTime();
        Iterator<Map.Entry<UUID, Long>> iterator = respawnInvulnerableUntil.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() > tick) {
                continue;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                player.setInvulnerable(false);
            }
            iterator.remove();
        }
    }

    private void clearRespawnInvulnerability(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        respawnInvulnerableUntil.remove(player.getUuid());
        player.setInvulnerable(false);
    }

    private ItemStack createWrittenBookItem(String nameKey, String titleKey, String authorKey, String pageOneKey, String pageTwoKey) {
        ItemStack item = new ItemStack(Items.WRITTEN_BOOK);
        item.set(DataComponentTypes.CUSTOM_NAME, Text.literal(text(nameKey)).formatted(Formatting.GOLD));
        item.set(
            DataComponentTypes.WRITTEN_BOOK_CONTENT,
            new WrittenBookContentComponent(
                RawFilteredPair.of(text(titleKey)),
                text(authorKey),
                0,
                List.of(RawFilteredPair.of(Text.literal(text(pageOneKey))), RawFilteredPair.of(Text.literal(text(pageTwoKey)))),
                true
            )
        );
        return item;
    }

    private MutableText clickableButton(String label, String command, Formatting color) {
        return Text.literal(label).styled(style -> style.withColor(color).withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private void populateLobbyMenuInventory(SimpleInventory inventory, ServerPlayerEntity player) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.setStack(slot, ItemStack.EMPTY);
        }

        boolean ready = readyPlayers.contains(player.getUuid());
        boolean canManage = canManageMatch(player);

        inventory.setStack(
            LOBBY_MENU_PRIMARY_SLOT,
            createMenuButton(
                ready ? MENU_ACTION_UNREADY : MENU_ACTION_READY,
                ready ? Items.RED_WOOL : Items.LIME_WOOL,
                ready ? text("menu.button.unready") : text("menu.button.ready"),
                ready ? Formatting.YELLOW : Formatting.GREEN
            )
        );
        inventory.setStack(
            LOBBY_MENU_STATUS_SLOT,
            createMenuButton(MENU_ACTION_STATUS, Items.CLOCK, text("menu.button.status"), Formatting.YELLOW)
        );
        inventory.setStack(
            LOBBY_MENU_HELP_SLOT,
            createMenuButton(MENU_ACTION_HELP, Items.BOOK, text("menu.button.help"), Formatting.AQUA)
        );

        if (canManage) {
            inventory.setStack(
                LOBBY_MENU_TEAMS_SLOT,
                createMenuButton(MENU_ACTION_TEAMS, Items.CYAN_WOOL, text("menu.button.teams") + " " + teamCount, Formatting.AQUA)
            );
            inventory.setStack(
                LOBBY_MENU_HEALTH_SLOT,
                createMenuButton(MENU_ACTION_HEALTH, Items.GOLDEN_APPLE, text("menu.button.health") + " " + healthPresetLabel(healthPreset), Formatting.GOLD)
            );
            inventory.setStack(
                LOBBY_MENU_NORESPAWN_SLOT,
                createMenuButton(
                    MENU_ACTION_NORESPAWN,
                    noRespawnEnabled ? Items.EMERALD_BLOCK : Items.REDSTONE_BLOCK,
                    text("menu.button.norespawn") + " " + stateLabel(noRespawnEnabled),
                    noRespawnEnabled ? Formatting.GREEN : Formatting.RED
                )
            );
            inventory.setStack(
                LOBBY_MENU_ADVANCEMENTS_SLOT,
                createMenuButton(
                    MENU_ACTION_ADVANCEMENTS,
                    advancementsEnabled ? Items.EXPERIENCE_BOTTLE : Items.GLASS_BOTTLE,
                    text("menu.button.advancements") + " " + stateLabel(advancementsEnabled),
                    advancementsEnabled ? Formatting.GREEN : Formatting.RED
                )
            );
            inventory.setStack(
                LOBBY_MENU_TAB_SLOT,
                createMenuButton(
                    MENU_ACTION_TAB,
                    tabEnabled ? Items.COMPASS : Items.GRAY_DYE,
                    text("menu.button.tab") + " " + stateLabel(tabEnabled),
                    tabEnabled ? Formatting.GREEN : Formatting.RED
                )
            );
            inventory.setStack(
                LOBBY_MENU_START_STOP_SLOT,
                createMenuButton(
                    hasActiveMatchSession() ? MENU_ACTION_STOP : MENU_ACTION_START,
                    hasActiveMatchSession() ? Items.BARRIER : Items.DIAMOND_SWORD,
                    hasActiveMatchSession() ? text("menu.button.stop") : text("menu.button.start"),
                    hasActiveMatchSession() ? Formatting.RED : Formatting.LIGHT_PURPLE
                )
            );
            inventory.setStack(
                LOBBY_MENU_RELOAD_SLOT,
                createMenuButton(MENU_ACTION_RELOAD, Items.REPEATER, text("menu.button.reload"), Formatting.GOLD)
            );
        }
    }

    private ItemStack createMenuButton(String action, net.minecraft.item.Item item, String label, Formatting color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(label).formatted(color));
        NbtCompound tag = new NbtCompound();
        tag.putString(LOBBY_MENU_ACTION_TAG, action);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
        return stack;
    }

    private String resolveMenuAction(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        String action = customData.copyNbt().getString(LOBBY_MENU_ACTION_TAG).orElse("");
        return action == null || action.isBlank() ? null : action;
    }

    private void setTeamBedOwner(ItemStack stack, int ownerTeam) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound tag = customData != null ? customData.copyNbt() : new NbtCompound();
        tag.putInt(TEAM_BED_OWNER_TAG, ownerTeam);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
        updateTeamBedDisplay(stack, ownerTeam);
    }

    private void clearTeamBedOwner(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        NbtCompound tag = customData.copyNbt();
        tag.remove(TEAM_BED_OWNER_TAG);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
        updateTeamBedDisplay(stack, null);
    }

    private Integer resolveTeamBedOwner(ItemStack stack) {
        if (stack == null || !stack.isOf(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            return null;
        }
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        return customData.copyNbt().getInt(TEAM_BED_OWNER_TAG).orElse(null);
    }

    private void updateTeamBedDisplay(ItemStack stack, Integer ownerTeam) {
        if (stack == null || !stack.isOf(TeamLifeBindFabric.TEAM_BED_ITEM)) {
            return;
        }
        String displayName = text("item.team_bed.name");
        if (ownerTeam != null) {
            displayName += " [" + teamLabel(ownerTeam) + "]";
        }
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName).formatted(Formatting.GOLD));
    }

    private void handleLobbyMenuAction(ServerPlayerEntity player, String action, SimpleInventory inventory, boolean decrement) {
        if (player == null || action == null) {
            return;
        }

        switch (action) {
            case MENU_ACTION_READY -> markReady(player);
            case MENU_ACTION_UNREADY -> unready(player);
            case MENU_ACTION_STATUS -> player.sendMessage(Text.literal(status(serverOf(player))), false);
            case MENU_ACTION_HELP -> sendCommandHelp(player);
            case MENU_ACTION_TEAMS -> {
                if (!canManageMatch(player)) {
                    player.sendMessage(Text.literal(text("command.no_permission")).formatted(Formatting.RED), false);
                } else if (hasActiveMatchSession()) {
                    player.sendMessage(Text.literal(text("command.team_count.running")).formatted(Formatting.RED), false);
                } else {
                    int updatedCount = stepTeamCount(teamCount, decrement);
                    setTeamCount(updatedCount);
                    player.sendMessage(Text.literal(text("command.team_count.updated", updatedCount)).formatted(Formatting.GREEN), false);
                }
            }
            case MENU_ACTION_HEALTH -> {
                if (!canManageMatch(player)) {
                    player.sendMessage(Text.literal(text("command.no_permission")).formatted(Formatting.RED), false);
                } else if (hasActiveMatchSession()) {
                    player.sendMessage(Text.literal(text("command.health.running")).formatted(Formatting.RED), false);
                } else {
                    HealthPreset next = stepHealthPreset(healthPreset, decrement);
                    setHealthPreset(next);
                    player.sendMessage(Text.literal(text("command.health.updated", healthPresetLabel(next))).formatted(Formatting.GREEN), false);
                }
            }
            case MENU_ACTION_NORESPAWN -> {
                if (!canManageMatch(player)) {
                    player.sendMessage(Text.literal(text("command.no_permission")).formatted(Formatting.RED), false);
                } else {
                    boolean enabled = !noRespawnEnabled;
                    setNoRespawnEnabled(enabled);
                    player.sendMessage(
                        Text.literal(text(enabled ? "command.norespawn.enabled" : "command.norespawn.disabled")).formatted(Formatting.GREEN),
                        false
                    );
                }
            }
            case MENU_ACTION_ADVANCEMENTS -> {
                if (!canManageMatch(player)) {
                    player.sendMessage(Text.literal(text("command.no_permission")).formatted(Formatting.RED), false);
                } else {
                    boolean enabled = !advancementsEnabled;
                    setAdvancementsEnabled(enabled);
                    player.sendMessage(Text.literal(text("config.advancements.updated", stateLabel(enabled))).formatted(Formatting.GREEN), false);
                }
            }
            case MENU_ACTION_TAB -> {
                if (!canManageMatch(player)) {
                    player.sendMessage(Text.literal(text("command.no_permission")).formatted(Formatting.RED), false);
                } else {
                    boolean enabled = !tabEnabled;
                    setTabEnabled(enabled);
                    player.sendMessage(Text.literal(text("config.tab.updated", stateLabel(enabled))).formatted(Formatting.GREEN), false);
                }
            }
            case MENU_ACTION_START -> {
                if (!canManageMatch(player)) {
                    player.sendMessage(Text.literal(text("command.no_permission")).formatted(Formatting.RED), false);
                } else {
                    player.sendMessage(Text.literal(start(serverOf(player))), false);
                }
            }
            case MENU_ACTION_STOP -> {
                if (!canManageMatch(player)) {
                    player.sendMessage(Text.literal(text("command.no_permission")).formatted(Formatting.RED), false);
                } else {
                    player.sendMessage(Text.literal(stop(serverOf(player))), false);
                }
            }
            case MENU_ACTION_RELOAD -> {
                if (!canManageMatch(player)) {
                    player.sendMessage(Text.literal(text("command.no_permission")).formatted(Formatting.RED), false);
                } else {
                    loadPersistentSettings();
                    reloadLanguage();
                    applyAdvancementRule(activeServer);
                    refreshTabState(activeServer);
                    player.sendMessage(Text.literal(text("command.reload.success")).formatted(Formatting.GREEN), false);
                }
            }
            default -> {
                return;
            }
        }

        if (player.currentScreenHandler instanceof LobbyMenuScreenHandler handler && player.currentScreenHandler == handler) {
            populateLobbyMenuInventory(inventory, player);
            handler.sendContentUpdates();
        }
    }

    private void sendCommandHelp(ServerPlayerEntity player) {
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
            player.sendMessage(Text.literal(text(key)), false);
        }
    }

    private boolean canManageMatch(ServerPlayerEntity player) {
        return player != null && player.isCreativeLevelTwoOp();
    }

    private void reloadLanguage() {
        language = TeamLifeBindLanguage.load(
            FabricLoader.getInstance().getConfigDir().resolve("teamlifebind"),
            message -> TeamLifeBindFabric.LOGGER.warn(message)
        );
    }

    private void applyAdvancementRule(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (RegistryKey<World> key : Set.of(LOBBY_OVERWORLD_KEY, BATTLE_OVERWORLD_KEY, BATTLE_NETHER_KEY, BATTLE_END_KEY, World.OVERWORLD, World.NETHER, World.END)) {
            ServerWorld world = server.getWorld(key);
            if (world != null) {
                world.getGameRules().setValue(GameRules.ANNOUNCE_ADVANCEMENTS, advancementsEnabled, server);
                world.getGameRules().setValue(GameRules.DO_IMMEDIATE_RESPAWN, true, server);
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

        teamCount = Math.max(2, Math.min(32, parseInt(properties.getProperty("team-count"), 2)));
        healthPreset = HealthPreset.fromString(properties.getProperty("health-preset", HealthPreset.ONE_HEART.name()));
        noRespawnEnabled = Boolean.parseBoolean(properties.getProperty("no-respawn-enabled", "true"));
        tabEnabled = Boolean.parseBoolean(properties.getProperty("tab-enabled", "true"));
        advancementsEnabled = Boolean.parseBoolean(properties.getProperty("advancements-enabled", "false"));
        savePersistentSettings();
    }

    private void savePersistentSettings() {
        Path path = settingsFilePath();
        Properties properties = new Properties();
        properties.setProperty("team-count", String.valueOf(teamCount));
        properties.setProperty("health-preset", healthPreset.name());
        properties.setProperty("no-respawn-enabled", String.valueOf(noRespawnEnabled));
        properties.setProperty("tab-enabled", String.valueOf(tabEnabled));
        properties.setProperty("advancements-enabled", String.valueOf(advancementsEnabled));
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

    private String resolvePlayerTeamLabel(ServerPlayerEntity player) {
        Integer team = player != null ? engine.teamForPlayer(player.getUuid()) : null;
        return team != null ? teamLabel(team) : text("menu.info.no_team");
    }

    private void applyHealthPreset(ServerPlayerEntity player) {
        if (player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH) != null) {
            player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(healthPreset.maxHealth());
        }
        player.setHealth((float) Math.min(player.getMaxHealth(), healthPreset.maxHealth()));
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0F);
    }

    private SpawnPoint normalizeBedFoot(RegistryKey<World> worldKey, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BedBlock)) {
            return new SpawnPoint(worldKey, pos.toImmutable());
        }
        BedPart part = state.get(BedBlock.PART);
        if (part == BedPart.HEAD) {
            Direction facing = state.get(BedBlock.FACING);
            return new SpawnPoint(worldKey, pos.offset(facing.getOpposite()).toImmutable());
        }
        return new SpawnPoint(worldKey, pos.toImmutable());
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

    private void explode(ServerWorld world, BlockPos pos, ServerPlayerEntity source) {
        world.createExplosion(source, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 3.0F, World.ExplosionSourceType.NONE);
    }

    private final class LobbyMenuScreenHandler extends GenericContainerScreenHandler {

        private final SimpleInventory menuInventory;

        private LobbyMenuScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory menuInventory) {
            super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, menuInventory, 3);
            this.menuInventory = menuInventory;
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            if (actionType != SlotActionType.PICKUP) {
                return;
            }
            if (slotIndex < 0 || slotIndex >= menuInventory.size()) {
                return;
            }

            String action = resolveMenuAction(menuInventory.getStack(slotIndex));
            if (action != null) {
                handleLobbyMenuAction(serverPlayer, action, menuInventory, button == 1);
            }
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }
    }

    private record SpawnPoint(RegistryKey<World> worldKey, BlockPos pos) {
    }

    private record TimedNotice(String message, int remainingTicks) {
    }

    private record PendingBedPlacement(UUID ownerId, int team, Hand hand, RegistryKey<World> worldKey, BlockPos approxPos, long expiresAtTick) {
    }

    private record PendingBrokenTeamBedDrop(SpawnPoint spawnPoint, int ownerTeam, long processAtTick) {
    }

}
