package com.teamlifebind.neoforge;

import com.teamlifebind.common.DeathResult;
import com.teamlifebind.common.GameOptions;
import com.teamlifebind.common.HealthPreset;
import com.teamlifebind.common.StartResult;
import com.teamlifebind.common.TeamLifeBindEngine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class TeamLifeBindNeoForgeManager {

    private static final long TEAM_WIPE_GUARD_MS = 2_000L;
    private static final int RANDOM_SPAWN_RADIUS = 750;
    private static final int MIN_TEAM_DISTANCE = 280;
    private static final int MIN_TEAM_DISTANCE_FLOOR = 64;
    private static final int SPAWN_SEARCH_ATTEMPTS = 256;
    private static final int TEAM_SPAWN_SPREAD = 16;

    private static final int READY_COUNTDOWN_SECONDS = 10;
    private static final int LOBBY_PLATFORM_Y = 100;
    private static final int MATCH_CENTER_RADIUS = 6500;
    private static final int MATCH_CENTER_MIN_DISTANCE = 1800;
    private static final ResourceKey<Level> LOBBY_OVERWORLD_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.parse(TeamLifeBindNeoForge.MOD_ID + ":lobby_overworld"));
    private static final ResourceKey<Level> LOBBY_NETHER_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.parse(TeamLifeBindNeoForge.MOD_ID + ":lobby_nether"));
    private static final ResourceKey<Level> LOBBY_END_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.parse(TeamLifeBindNeoForge.MOD_ID + ":lobby_end"));
    private static final ResourceKey<Level> BATTLE_OVERWORLD_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.parse(TeamLifeBindNeoForge.MOD_ID + ":battle_overworld"));
    private static final ResourceKey<Level> BATTLE_NETHER_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.parse(TeamLifeBindNeoForge.MOD_ID + ":battle_nether"));
    private static final ResourceKey<Level> BATTLE_END_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.parse(TeamLifeBindNeoForge.MOD_ID + ":battle_end"));
    private static final Set<ResourceKey<Level>> BATTLE_DIMENSIONS = Set.of(BATTLE_OVERWORLD_KEY, BATTLE_NETHER_KEY, BATTLE_END_KEY);

    private final TeamLifeBindEngine engine = new TeamLifeBindEngine();
    private final Map<Integer, SpawnPoint> teamSpawns = new HashMap<>();
    private final Map<Integer, SpawnPoint> teamBeds = new HashMap<>();
    private final Map<Integer, Long> teamWipeGuardUntil = new HashMap<>();
    private final Set<UUID> noRespawnLockedPlayers = new HashSet<>();
    private final Set<ResourceKey<Level>> noRespawnDimensions = new HashSet<>(Set.of(Level.END, BATTLE_END_KEY));
    private final List<PendingBedPlacement> pendingBedPlacements = new ArrayList<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Set<UUID> dimensionRedirectGuard = new HashSet<>();
    private final Random random = new Random();

    private int teamCount = 2;
    private HealthPreset healthPreset = HealthPreset.ONE_HEART;
    private boolean noRespawnEnabled = true;

    private int countdownRemainingSeconds = -1;
    private int countdownTickBudget = 0;
    private BlockPos activeMatchCenter;
    private BlockPos previousMatchCenter;

    public void onServerStarted(MinecraftServer server) {
        prepareLobbyPlatform(resolveLobbyLevel(server));
        teleportAllToLobby(server);
    }

    public void onPlayerJoin(ServerPlayer player) {
        readyPlayers.remove(player.getUUID());
        if (engine.isRunning()) {
            return;
        }

        MinecraftServer server = serverOf(player);
        prepareLobbyPlatform(resolveLobbyLevel(server));
        teleportToLobby(player);
        player.sendSystemMessage(Component.literal("[TLB] \u5f53\u524d\u5728\u5927\u5385\uff0c\u4f7f\u7528 /tlb ready \u51c6\u5907\u5f00\u59cb\u3002"));
    }

    public void onPlayerLeave(UUID playerId, MinecraftServer server) {
        readyPlayers.remove(playerId);
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
    }

    public int getTeamCount() {
        return teamCount;
    }

    public void setHealthPreset(HealthPreset preset) {
        this.healthPreset = preset;
    }

    public HealthPreset getHealthPreset() {
        return healthPreset;
    }

    public String noRespawnStatus() {
        List<String> blocked = noRespawnDimensions.stream().map(key -> key.identifier().toString()).sorted().toList();
        return "[TLB] norespawn=" + noRespawnEnabled + " | blocked=" + blocked;
    }

    public void setNoRespawnEnabled(boolean enabled) {
        this.noRespawnEnabled = enabled;
        if (!enabled) {
            noRespawnLockedPlayers.clear();
        }
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
            player.sendSystemMessage(Component.literal("[TLB] \u6bd4\u8d5b\u8fdb\u884c\u4e2d\uff0c\u4e0d\u80fd\u51c6\u5907\u3002"));
            return;
        }

        if (!readyPlayers.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("[TLB] \u4f60\u5df2\u7ecf\u662f\u51c6\u5907\u72b6\u6001\u3002"));
            return;
        }

        MinecraftServer server = serverOf(player);
        server.getPlayerList().broadcastSystemMessage(Component.literal("[TLB] " + player.getName().getString() + " \u5df2\u51c6\u5907\u3002"), false);
        evaluateReadyCountdown(server);
    }

    public void unready(ServerPlayer player) {
        if (!readyPlayers.remove(player.getUUID())) {
            player.sendSystemMessage(Component.literal("[TLB] \u4f60\u76ee\u524d\u4e0d\u662f\u51c6\u5907\u72b6\u6001\u3002"));
            return;
        }

        MinecraftServer server = serverOf(player);
        server.getPlayerList().broadcastSystemMessage(Component.literal("[TLB] " + player.getName().getString() + " \u53d6\u6d88\u51c6\u5907\u3002"), false);
        evaluateReadyCountdown(server);
    }

    public String start(MinecraftServer server) {
        if (engine.isRunning()) {
            return "[TLB] Match already running.";
        }

        ServerLevel matchLevel = resolveBattleOverworld(server);
        if (matchLevel == null) {
            return "[TLB] Missing match dimension: teamlifebind:battle_overworld";
        }

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (players.size() < teamCount) {
            return "[TLB] Need at least " + teamCount + " players online.";
        }

        cancelCountdown();
        readyPlayers.clear();
        clearRoundState();

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
            teleport(player, randomized);
            applyHealthPreset(player);
            player.sendSystemMessage(Component.literal("[TLB] Team #" + team + " | health=" + healthPreset.name()));
        }

        server.getPlayerList().broadcastSystemMessage(Component.literal("[TLB] Match started. Team count: " + teamCount + ". PvP allowed."), false);
        server.getPlayerList().broadcastSystemMessage(Component.literal("[TLB] Craft Team Bed: 6 wool + 3 planks (bottom row)."), false);
        return "[TLB] Match started.";
    }

    public String stop(MinecraftServer server) {
        if (!engine.isRunning()) {
            cancelCountdown();
            readyPlayers.clear();
            teleportAllToLobby(server);
            return "[TLB] No running match.";
        }

        stopInternal(server, false, null);
        return "[TLB] Match stopped.";
    }

    public String status(MinecraftServer server) {
        if (!engine.isRunning()) {
            return "[TLB] Status: idle | ready=" + readyPlayers.size() + "/" + server.getPlayerList().getPlayers().size();
        }

        List<Integer> beds = new ArrayList<>(teamBeds.keySet());
        Collections.sort(beds);
        return "[TLB] Status: running | teams="
            + teamCount
            + " | health="
            + healthPreset.name()
            + " | teamBeds="
            + beds
            + " | norespawn="
            + noRespawnEnabled;
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
            player.sendSystemMessage(Component.literal("[TLB] Beds do not set personal spawn points."));
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
            player.sendSystemMessage(Component.literal("[TLB] Normal beds are disabled and explode instantly."));
            denyUse(event);
            return;
        }

        if (!engine.isRunning()) {
            player.sendSystemMessage(Component.literal("[TLB] Team bed can only be placed while a match is running."));
            denyUse(event);
            return;
        }

        Integer team = engine.teamForPlayer(player.getUUID());
        if (team == null) {
            player.sendSystemMessage(Component.literal("[TLB] You are not assigned to a team."));
            denyUse(event);
            return;
        }

        pendingBedPlacements.add(new PendingBedPlacement(player.getUUID(), team, level.dimension(), placePos.immutable(), level.getGameTime() + 5L));
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
                teamBeds.put(pending.team(), bedFoot);
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(pending.ownerId());
                if (owner != null) {
                    owner.sendSystemMessage(Component.literal("[TLB] Team #" + pending.team() + " bed spawn updated."));
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
        removeTeamBedAt(normalized);
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

        stopInternal(killer.level().getServer(), true, "first dragon kill");
    }

    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!engine.isRunning()) {
            teleportToLobby(player);
            return;
        }

        if (isRespawnLocked(player)) {
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("[TLB] You died in a no-respawn dimension and are now eliminated."));
            return;
        }

        Integer team = engine.teamForPlayer(player.getUUID());
        if (team == null) {
            return;
        }

        SpawnPoint spawn = resolveRespawn(team, player.level().getServer());
        if (spawn != null) {
            teleport(player, spawn);
        }
    }

    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        processPendingBedPlacements(server);
        processReadyCountdown(server);
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
            iterator.remove();
        }
    }

    private void processReadyCountdown(MinecraftServer server) {
        if (countdownRemainingSeconds < 0) {
            return;
        }

        String reason = readyFailureReason(server);
        if (reason != null) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(reason), false);
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
            server.getPlayerList().broadcastSystemMessage(Component.literal("[TLB] \u6bd4\u8d5b\u5c06\u5728 " + countdownRemainingSeconds + " \u79d2\u540e\u5f00\u59cb\u3002"), false);
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
                server.getPlayerList().broadcastSystemMessage(Component.literal(reason), false);
            }
            cancelCountdown();
            return;
        }

        if (countdownRemainingSeconds >= 0) {
            return;
        }

        countdownRemainingSeconds = READY_COUNTDOWN_SECONDS;
        countdownTickBudget = 0;
        server.getPlayerList().broadcastSystemMessage(Component.literal("[TLB] \u5168\u5458\u51c6\u5907\u5b8c\u6210\uff0c\u5012\u8ba1\u65f6\u5f00\u59cb\u3002"), false);
    }

    private String readyFailureReason(MinecraftServer server) {
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        if (online.size() < teamCount) {
            return "[TLB] \u5728\u7ebf\u4eba\u6570\u4e0d\u8db3\uff0c\u5012\u8ba1\u65f6\u53d6\u6d88\u3002";
        }

        for (ServerPlayer player : online) {
            if (!readyPlayers.contains(player.getUUID())) {
                return "[TLB] \u6709\u73a9\u5bb6\u672a\u51c6\u5907\uff0c\u5012\u8ba1\u65f6\u53d6\u6d88\u3002";
            }
        }
        return null;
    }

    private void cancelCountdown() {
        countdownRemainingSeconds = -1;
        countdownTickBudget = 0;
    }

    private void handlePlayerDeath(ServerPlayer deadPlayer) {
        DeathResult result = engine.onPlayerDeath(deadPlayer.getUUID());
        if (!result.triggered()) {
            return;
        }
        if (!acquireTeamWipeGuard(result.team())) {
            return;
        }

        MinecraftServer server = deadPlayer.level().getServer();
        server.getPlayerList().broadcastSystemMessage(Component.literal("[TLB] Team #" + result.team() + " triggered life-bind wipe."), false);

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
                server.getPlayerList().broadcastSystemMessage(Component.literal("[TLB] Team #" + winner + " wins (" + reason + ")."), false);
            }
        }

        engine.stop();
        cancelCountdown();
        readyPlayers.clear();
        clearRoundState();
        prepareLobbyPlatform(resolveLobbyLevel(server));
        teleportAllToLobby(server);
        resetBattleDimensionData(server);
    }

    private void clearRoundState() {
        teamSpawns.clear();
        teamBeds.clear();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        pendingBedPlacements.clear();
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
                TeamLifeBindNeoForge.LOGGER.warn("Failed to reset battle dimension folder {}", folder, ex);
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
                level.setBlockAndUpdate(new BlockPos(x, LOBBY_PLATFORM_Y, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, LOBBY_PLATFORM_Y + 1, z), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, LOBBY_PLATFORM_Y + 2, z), Blocks.AIR.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(new BlockPos(0, LOBBY_PLATFORM_Y + 1, 0), Blocks.TORCH.defaultBlockState());
    }

    private void teleportAllToLobby(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            teleportToLobby(player);
        }
    }

    private void teleportToLobby(ServerPlayer player) {
        MinecraftServer server = serverOf(player);
        ServerLevel lobby = resolveLobbyLevel(server);
        teleport(player, new SpawnPoint(lobby.dimension(), new BlockPos(0, LOBBY_PLATFORM_Y + 1, 0)));
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

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, chosen.getX(), chosen.getZ()) + 1;
        return new BlockPos(chosen.getX(), y, chosen.getZ());
    }

    private ServerLevel resolveLobbyLevel(MinecraftServer server) {
        ServerLevel lobby = server.getLevel(LOBBY_OVERWORLD_KEY);
        return lobby != null ? lobby : server.overworld();
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

        if (from.equals(LOBBY_OVERWORLD_KEY) && to.equals(Level.NETHER)) {
            return LOBBY_NETHER_KEY;
        }
        if (from.equals(LOBBY_NETHER_KEY) && to.equals(Level.OVERWORLD)) {
            return LOBBY_OVERWORLD_KEY;
        }
        if (from.equals(LOBBY_OVERWORLD_KEY) && to.equals(Level.END)) {
            return LOBBY_END_KEY;
        }
        if (from.equals(LOBBY_END_KEY) && to.equals(Level.OVERWORLD)) {
            return LOBBY_OVERWORLD_KEY;
        }
        return null;
    }

    private BlockPos portalTargetPos(ResourceKey<Level> from, ResourceKey<Level> target, BlockPos sourcePos, ServerLevel targetLevel) {
        int x = sourcePos.getX();
        int z = sourcePos.getZ();

        if ((from.equals(BATTLE_OVERWORLD_KEY) || from.equals(LOBBY_OVERWORLD_KEY)) && (target.equals(BATTLE_NETHER_KEY) || target.equals(LOBBY_NETHER_KEY))) {
            x = sourcePos.getX() / 8;
            z = sourcePos.getZ() / 8;
        } else if ((from.equals(BATTLE_NETHER_KEY) || from.equals(LOBBY_NETHER_KEY)) && (target.equals(BATTLE_OVERWORLD_KEY) || target.equals(LOBBY_OVERWORLD_KEY))) {
            x = sourcePos.getX() * 8;
            z = sourcePos.getZ() * 8;
        } else if (target.equals(BATTLE_END_KEY) || target.equals(LOBBY_END_KEY)) {
            x = 0;
            z = 0;
        }

        int y = targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        return new BlockPos(x, y, z);
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
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, flat.getX(), flat.getZ()) + 1;
            return new BlockPos(flat.getX(), y, flat.getZ());
        }
        return null;
    }

    private BlockPos fallbackRandomTeamBase(ServerLevel level, BlockPos center) {
        BlockPos flat = randomPointInRadius(center, RANDOM_SPAWN_RADIUS);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, flat.getX(), flat.getZ()) + 1;
        return new BlockPos(flat.getX(), y, flat.getZ());
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

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, offset.getX(), offset.getZ()) + 1;
        return new SpawnPoint(base.worldKey(), new BlockPos(offset.getX(), y, offset.getZ()));
    }

    private SpawnPoint resolveRespawn(int team, MinecraftServer server) {
        SpawnPoint bed = teamBeds.get(team);
        if (bed != null) {
            ServerLevel bedLevel = server.getLevel(bed.worldKey());
            if (bedLevel != null && bedLevel.getBlockState(bed.pos()).is(BlockTags.BEDS)) {
                return new SpawnPoint(bed.worldKey(), bed.pos().above());
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

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.pos().getX(), spawn.pos().getZ()) + 1;
        return new SpawnPoint(spawn.worldKey(), new BlockPos(spawn.pos().getX(), y, spawn.pos().getZ()));
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
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    private record SpawnPoint(ResourceKey<Level> worldKey, BlockPos pos) {
    }

    private record PendingBedPlacement(UUID ownerId, int team, ResourceKey<Level> worldKey, BlockPos approxPos, long expiresAtTick) {
    }
}

