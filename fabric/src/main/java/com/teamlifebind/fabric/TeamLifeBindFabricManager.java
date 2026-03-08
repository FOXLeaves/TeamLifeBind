package com.teamlifebind.fabric;

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
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

final class TeamLifeBindFabricManager {

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
    private static final RegistryKey<World> LOBBY_OVERWORLD_KEY = RegistryKey.of(RegistryKeys.WORLD, TeamLifeBindFabric.id("lobby_overworld"));
    private static final RegistryKey<World> LOBBY_NETHER_KEY = RegistryKey.of(RegistryKeys.WORLD, TeamLifeBindFabric.id("lobby_nether"));
    private static final RegistryKey<World> LOBBY_END_KEY = RegistryKey.of(RegistryKeys.WORLD, TeamLifeBindFabric.id("lobby_end"));
    private static final RegistryKey<World> BATTLE_OVERWORLD_KEY = RegistryKey.of(RegistryKeys.WORLD, TeamLifeBindFabric.id("battle_overworld"));
    private static final RegistryKey<World> BATTLE_NETHER_KEY = RegistryKey.of(RegistryKeys.WORLD, TeamLifeBindFabric.id("battle_nether"));
    private static final RegistryKey<World> BATTLE_END_KEY = RegistryKey.of(RegistryKeys.WORLD, TeamLifeBindFabric.id("battle_end"));
    private static final Set<RegistryKey<World>> BATTLE_DIMENSIONS = Set.of(BATTLE_OVERWORLD_KEY, BATTLE_NETHER_KEY, BATTLE_END_KEY);

    private final TeamLifeBindEngine engine = new TeamLifeBindEngine();
    private final Map<Integer, SpawnPoint> teamSpawns = new HashMap<>();
    private final Map<Integer, SpawnPoint> teamBeds = new HashMap<>();
    private final Map<Integer, Long> teamWipeGuardUntil = new HashMap<>();
    private final Set<UUID> noRespawnLockedPlayers = new HashSet<>();
    private final Set<RegistryKey<World>> noRespawnDimensions = new HashSet<>(Set.of(World.END, BATTLE_END_KEY));
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
        ServerWorld lobby = resolveLobbyWorld(server);
        prepareLobbyPlatform(lobby);
        teleportAllToLobby(server);
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        readyPlayers.remove(player.getUuid());
        if (engine.isRunning()) {
            return;
        }

        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }

        prepareLobbyPlatform(resolveLobbyWorld(server));
        teleportToLobby(player);
        player.sendMessage(Text.literal("[TLB] \u5f53\u524d\u5728\u5927\u5385\uff0c\u4f7f\u7528 /tlb ready \u51c6\u5907\u5f00\u59cb\u3002"), false);
    }

    public void onPlayerLeave(UUID playerId, MinecraftServer server) {
        readyPlayers.remove(playerId);
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
        List<String> blocked = noRespawnDimensions.stream().map(key -> key.getValue().toString()).sorted().toList();
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
            player.sendMessage(Text.literal("[TLB] \u6bd4\u8d5b\u8fdb\u884c\u4e2d\uff0c\u4e0d\u80fd\u51c6\u5907\u3002"), false);
            return;
        }

        if (!readyPlayers.add(player.getUuid())) {
            player.sendMessage(Text.literal("[TLB] \u4f60\u5df2\u7ecf\u662f\u51c6\u5907\u72b6\u6001\u3002"), false);
            return;
        }

        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }

        server.getPlayerManager().broadcast(Text.literal("[TLB] " + player.getName().getString() + " \u5df2\u51c6\u5907\u3002"), false);
        evaluateReadyCountdown(server);
    }

    public void unready(ServerPlayerEntity player) {
        if (!readyPlayers.remove(player.getUuid())) {
            player.sendMessage(Text.literal("[TLB] \u4f60\u76ee\u524d\u4e0d\u662f\u51c6\u5907\u72b6\u6001\u3002"), false);
            return;
        }

        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }

        server.getPlayerManager().broadcast(Text.literal("[TLB] " + player.getName().getString() + " \u53d6\u6d88\u51c6\u5907\u3002"), false);
        evaluateReadyCountdown(server);
    }

    public String start(MinecraftServer server) {
        if (engine.isRunning()) {
            return "[TLB] Match already running.";
        }

        ServerWorld matchWorld = resolveBattleOverworld(server);
        if (matchWorld == null) {
            return "[TLB] Missing match dimension: teamlifebind:battle_overworld";
        }

        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        if (players.size() < teamCount) {
            return "[TLB] Need at least " + teamCount + " players online.";
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
            teleport(player, randomized);
            applyHealthPreset(player);
            player.sendMessage(Text.literal("[TLB] Team #" + team + " | health=" + healthPreset.name()), false);
        }

        server.getPlayerManager().broadcast(Text.literal("[TLB] Match started. Team count: " + teamCount + ". PvP allowed."), false);
        server.getPlayerManager().broadcast(Text.literal("[TLB] Craft Team Bed: 6 wool + 3 planks (bottom row)."), false);
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
            return "[TLB] Status: idle | ready=" + readyPlayers.size() + "/" + server.getPlayerManager().getPlayerList().size();
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

    public ActionResult onUseBlock(ServerPlayerEntity player, Hand hand, BlockHitResult hitResult) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        BlockPos clickedPos = hitResult.getBlockPos();
        BlockState clickedState = world.getBlockState(clickedPos);

        if (clickedState.isIn(BlockTags.BEDS)) {
            player.sendMessage(Text.literal("[TLB] Beds do not set personal spawn points."), true);
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
            player.sendMessage(Text.literal("[TLB] Normal beds are disabled and explode instantly."), true);
            return ActionResult.FAIL;
        }

        if (!engine.isRunning()) {
            player.sendMessage(Text.literal("[TLB] Team bed can only be placed while a match is running."), true);
            return ActionResult.FAIL;
        }

        Integer team = engine.teamForPlayer(player.getUuid());
        if (team == null) {
            player.sendMessage(Text.literal("[TLB] You are not assigned to a team."), true);
            return ActionResult.FAIL;
        }

        pendingBedPlacements.add(new PendingBedPlacement(player.getUuid(), team, world.getRegistryKey(), placePos, world.getTime() + 5));
        return ActionResult.PASS;
    }

    public void onBlockBreak(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
        if (!state.isIn(BlockTags.BEDS)) {
            return;
        }
        SpawnPoint normalized = normalizeBedFoot(world.getRegistryKey(), pos, state);
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
        stopInternal(server, true, "first dragon kill");
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
            player.sendMessage(Text.literal("[TLB] You died in a no-respawn dimension and are now eliminated."), false);
            return;
        }

        Integer team = engine.teamForPlayer(player.getUuid());
        if (team == null) {
            return;
        }

        SpawnPoint respawn = resolveRespawn(team, server);
        if (respawn == null) {
            return;
        }
        teleport(player, respawn);
    }

    public void onServerTick(MinecraftServer server) {
        processPendingBedPlacements(server);
        processReadyCountdown(server);
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
            teamBeds.put(pending.team(), bed);

            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(pending.ownerId());
            if (owner != null) {
                owner.sendMessage(Text.literal("[TLB] Team #" + pending.team() + " bed spawn updated."), false);
            }
            iterator.remove();
        }
    }

    private void processReadyCountdown(MinecraftServer server) {
        if (countdownRemainingSeconds < 0) {
            return;
        }

        String reason = readyFailureReason(server);
        if (reason != null) {
            server.getPlayerManager().broadcast(Text.literal(reason), false);
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
            server.getPlayerManager().broadcast(Text.literal("[TLB] \u6bd4\u8d5b\u5c06\u5728 " + countdownRemainingSeconds + " \u79d2\u540e\u5f00\u59cb\u3002"), false);
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
                server.getPlayerManager().broadcast(Text.literal(reason), false);
            }
            cancelCountdown();
            return;
        }

        if (countdownRemainingSeconds >= 0) {
            return;
        }

        countdownRemainingSeconds = READY_COUNTDOWN_SECONDS;
        countdownTickBudget = 0;
        server.getPlayerManager().broadcast(Text.literal("[TLB] \u5168\u5458\u51c6\u5907\u5b8c\u6210\uff0c\u5012\u8ba1\u65f6\u5f00\u59cb\u3002"), false);
    }

    private String readyFailureReason(MinecraftServer server) {
        List<ServerPlayerEntity> online = server.getPlayerManager().getPlayerList();
        if (online.size() < teamCount) {
            return "[TLB] \u5728\u7ebf\u4eba\u6570\u4e0d\u8db3\uff0c\u5012\u8ba1\u65f6\u53d6\u6d88\u3002";
        }

        for (ServerPlayerEntity player : online) {
            if (!readyPlayers.contains(player.getUuid())) {
                return "[TLB] \u6709\u73a9\u5bb6\u672a\u51c6\u5907\uff0c\u5012\u8ba1\u65f6\u53d6\u6d88\u3002";
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

        server.getPlayerManager().broadcast(Text.literal("[TLB] Team #" + result.team() + " triggered life-bind wipe."), false);

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
                server.getPlayerManager().broadcast(Text.literal("[TLB] Team #" + winner + " wins (" + reason + ")."), false);
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
        pendingBedPlacements.clear();
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
                world.setBlockState(new BlockPos(x, LOBBY_PLATFORM_Y, z), Blocks.STONE.getDefaultState(), 3);
                world.setBlockState(new BlockPos(x, LOBBY_PLATFORM_Y + 1, z), Blocks.AIR.getDefaultState(), 3);
                world.setBlockState(new BlockPos(x, LOBBY_PLATFORM_Y + 2, z), Blocks.AIR.getDefaultState(), 3);
            }
        }
        world.setBlockState(new BlockPos(0, LOBBY_PLATFORM_Y + 1, 0), Blocks.TORCH.getDefaultState(), 3);
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
        teleport(player, new SpawnPoint(lobby.getRegistryKey(), new BlockPos(0, LOBBY_PLATFORM_Y + 1, 0)));
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

        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, chosen.getX(), chosen.getZ()) + 1;
        return new BlockPos(chosen.getX(), y, chosen.getZ());
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

        if (from.equals(LOBBY_OVERWORLD_KEY) && to.equals(World.NETHER)) {
            return LOBBY_NETHER_KEY;
        }
        if (from.equals(LOBBY_NETHER_KEY) && to.equals(World.OVERWORLD)) {
            return LOBBY_OVERWORLD_KEY;
        }
        if (from.equals(LOBBY_OVERWORLD_KEY) && to.equals(World.END)) {
            return LOBBY_END_KEY;
        }
        if (from.equals(LOBBY_END_KEY) && to.equals(World.OVERWORLD)) {
            return LOBBY_OVERWORLD_KEY;
        }
        return null;
    }

    private BlockPos portalTargetPos(RegistryKey<World> from, RegistryKey<World> target, BlockPos sourcePos, ServerWorld targetWorld) {
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

        int y = targetWorld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        return new BlockPos(x, y, z);
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
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, flat.getX(), flat.getZ()) + 1;
            return new BlockPos(flat.getX(), y, flat.getZ());
        }
        return null;
    }

    private BlockPos fallbackRandomTeamBase(ServerWorld world, BlockPos center) {
        BlockPos flat = randomPointInRadius(center, RANDOM_SPAWN_RADIUS);
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, flat.getX(), flat.getZ()) + 1;
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
        BlockPos offset = base.pos().add(dx, 0, dz);

        ServerWorld world = server.getWorld(base.worldKey());
        if (world == null) {
            return base;
        }

        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, offset.getX(), offset.getZ()) + 1;
        return new SpawnPoint(base.worldKey(), new BlockPos(offset.getX(), y, offset.getZ()));
    }

    private SpawnPoint resolveRespawn(int team, MinecraftServer server) {
        SpawnPoint bed = teamBeds.get(team);
        if (bed != null) {
            ServerWorld bedWorld = server.getWorld(bed.worldKey());
            if (bedWorld != null && bedWorld.getBlockState(bed.pos()).isIn(BlockTags.BEDS)) {
                return new SpawnPoint(bed.worldKey(), bed.pos().up());
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
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.pos().getX(), spawn.pos().getZ()) + 1;
        return new SpawnPoint(spawn.worldKey(), new BlockPos(spawn.pos().getX(), y, spawn.pos().getZ()));
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

    private record SpawnPoint(RegistryKey<World> worldKey, BlockPos pos) {
    }

    private record PendingBedPlacement(UUID ownerId, int team, RegistryKey<World> worldKey, BlockPos approxPos, long expiresAtTick) {
    }
}
