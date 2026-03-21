package com.teamlifebind.paper;

import com.teamlifebind.common.GameOptions;
import com.teamlifebind.common.HealthPreset;
import com.teamlifebind.common.StartResult;
import com.teamlifebind.common.TeamLifeBindLanguage;
import com.teamlifebind.common.TeamLifeBindEngine;
import com.teamlifebind.paper.api.TeamLifeBindMatchSnapshot;
import com.teamlifebind.paper.api.TeamLifeBindPaperApi;
import com.teamlifebind.paper.api.TeamLifeBindPlayerSnapshot;
import com.teamlifebind.paper.api.TeamLifeBindSidebarSnapshot;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.attribute.Attribute;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

public final class TeamLifeBindPaperPlugin extends JavaPlugin implements TeamLifeBindPaperApi {

    private static final long TEAM_WIPE_GUARD_MS = 2_000L;
    private static final int SPAWN_SEARCH_ATTEMPTS = 256;
    private static final int SAFE_SPAWN_SEARCH_RADIUS_BLOCKS = 12;
    private static final int MATCH_PRELOAD_SEARCH_RADIUS_BLOCKS = 32;
    private static final int TELEPORT_PRELOAD_RADIUS_BLOCKS = 16;
    private static final int FORCED_PLATFORM_RADIUS_BLOCKS = 1;
    private static final int FORCED_PLATFORM_FOUNDATION_DEPTH = 2;
    private static final int FORCED_PLATFORM_CLEARANCE_BLOCKS = 3;
    private static final int IMPORTANT_NOTICE_STAY_TICKS = 80;
    private static final long RESPAWN_INVULNERABILITY_TICKS = 100L;
    private static final int LOBBY_MENU_SLOT = 6;
    private static final int LOBBY_GUIDE_SLOT = 7;
    private static final int LOBBY_RECIPES_SLOT = 8;
    private static final int LOBBY_MENU_SIZE = 27;
    private static final long SUPPLY_BOAT_DROP_CONFIRM_WINDOW_MS = 3_000L;
    private static final long LOCATOR_UPDATE_INTERVAL_TICKS = 10L;
    private static final long TAB_UPDATE_INTERVAL_TICKS = 20L;
    private static final float LOCATOR_MIN_AZIMUTH_DELTA = 0.035F;
    private static final String RULE_LOCATOR_BAR = "locatorBar";
    private static final String RULE_ANNOUNCE_ADVANCEMENTS = "announceAdvancements";
    private static final String RULE_DO_IMMEDIATE_RESPAWN = "doImmediateRespawn";
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
    private static final String SCOREBOARD_OBJECTIVE_NAME = "tlb_sidebar";
    private static final String SCOREBOARD_TEAM_PREFIX = "tlb_line_";
    private static final String NAME_COLOR_TEAM_PREFIX = "tlb_name_";
    private static final String NAME_COLOR_TEAM_NEUTRAL = NAME_COLOR_TEAM_PREFIX + "neutral";
    private static final ChatColor[] TAB_TEAM_COLORS = {
        ChatColor.AQUA,
        ChatColor.YELLOW,
        ChatColor.LIGHT_PURPLE,
        ChatColor.GOLD,
        ChatColor.BLUE,
        ChatColor.WHITE,
        ChatColor.DARK_AQUA,
        ChatColor.GRAY
    };
    private static final String[] SCOREBOARD_ENTRIES = {
        ChatColor.BLACK.toString(),
        ChatColor.DARK_BLUE.toString(),
        ChatColor.DARK_GREEN.toString(),
        ChatColor.DARK_AQUA.toString(),
        ChatColor.DARK_RED.toString(),
        ChatColor.DARK_PURPLE.toString(),
        ChatColor.GOLD.toString(),
        ChatColor.GRAY.toString(),
        ChatColor.DARK_GRAY.toString(),
        ChatColor.BLUE.toString(),
        ChatColor.GREEN.toString(),
        ChatColor.AQUA.toString(),
        ChatColor.RED.toString(),
        ChatColor.LIGHT_PURPLE.toString(),
        ChatColor.YELLOW.toString()
    };

    private final TeamLifeBindEngine engine = new TeamLifeBindEngine();
    private final Random random = new Random();
    private final Map<Integer, Location> configuredTeamSpawns = new HashMap<>();
    private final Map<Integer, Location> matchTeamSpawns = new HashMap<>();
    private final Map<Integer, Location> teamBedSpawns = new HashMap<>();
    private final Map<String, Integer> placedTeamBedOwners = new HashMap<>();
    private final Map<Integer, Long> teamWipeGuardUntil = new HashMap<>();
    private final Map<UUID, Set<UUID>> trackedLocatorTargets = new HashMap<>();
    private final Map<UUID, Map<UUID, Float>> trackedLocatorAngles = new HashMap<>();
    private final Map<UUID, Scoreboard> managedScoreboards = new HashMap<>();
    private final Map<UUID, Long> supplyBoatDropConfirmUntil = new HashMap<>();
    private final Map<UUID, Long> respawnInvulnerabilityTokens = new HashMap<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Set<UUID> noRespawnLockedPlayers = new LinkedHashSet<>();
    private final Set<String> noRespawnDimensions = new LinkedHashSet<>();

    private int teamCount;
    private HealthPreset healthPreset;
    private int randomSpawnRadius;
    private int minTeamDistance;
    private int teamSpreadRadius;
    private boolean announceTeamAssignment;
    private boolean noRespawnEnabled;
    private int readyCountdownSeconds;
    private String lobbyWorldName;
    private String matchWorldPrefix;
    private String activeMatchWorldName;
    private boolean scoreboardEnabled;
    private boolean tabEnabled;
    private boolean advancementsEnabled;
    private long scoreboardUpdateIntervalTicks;
    private int countdownTaskId = -1;
    private int locatorTaskId = -1;
    private int scoreboardTaskId = -1;
    private int tabTaskId = -1;
    private NamespacedKey teamBedItemKey;
    private NamespacedKey teamBedOwnerTeamKey;
    private NamespacedKey supplyBoatItemKey;
    private NamespacedKey lobbyMenuItemKey;
    private NamespacedKey lobbyMenuActionKey;
    private TeamLifeBindLanguage language;
    private PaperLocatorBarSupport locatorBarSupport;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettingsFromConfig();
        loadLanguage();
        initializeItemKeys();
        locatorBarSupport = new PaperLocatorBarSupport(getLogger());
        registerTeamBedRecipe();
        ensureLobbyWorld();

        TeamLifeBindCommand command = new TeamLifeBindCommand(this);
        if (getCommand("tlb") != null) {
            getCommand("tlb").setExecutor(command);
            getCommand("tlb").setTabCompleter(command);
        } else {
            getLogger().warning("Command 'tlb' is missing from plugin.yml");
        }

        Bukkit.getPluginManager().registerEvents(new TeamLifeBindListener(this), this);
        Bukkit.getServicesManager().register(TeamLifeBindPaperApi.class, this, this, ServicePriority.Normal);
        startLocatorTask();
        startScoreboardTask();
        startTabTask();
        teleportAllToLobby();
    }

    @Override
    public void onDisable() {
        cancelCountdown();
        clearTrackedLocatorTargets();
        cancelLocatorTask();
        cancelScoreboardTask();
        cancelTabTask();
        clearManagedScoreboards();
        clearTabListDisplays();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearRespawnInvulnerability(player);
        }
        Bukkit.getServicesManager().unregisterAll(this);
    }

    public void reloadPluginConfig(CommandSender sender) {
        reloadConfig();
        loadSettingsFromConfig();
        loadLanguage();
        ensureLobbyWorld();
        applyManagedWorldRules();
        startScoreboardTask();
        startTabTask();
        sender.sendMessage(ChatColor.GREEN + text("command.reload.success"));
    }

    public boolean isMatchRunning() {
        return engine.isRunning();
    }

    public boolean hasActiveMatchSession() {
        return engine.isRunning()
            || countdownTaskId != -1
            || (activeMatchWorldName != null && !activeMatchWorldName.isBlank());
    }

    public int getTeamCount() {
        return teamCount;
    }

    public HealthPreset getHealthPreset() {
        return healthPreset;
    }

    public Integer teamForPlayer(UUID playerId) {
        return engine.teamForPlayer(playerId);
    }

    public boolean hasAssignedMatchTeam(UUID playerId) {
        return playerId != null && engine.teamForPlayer(playerId) != null;
    }

    public String text(String key, Object... args) {
        return language != null ? language.text(key, args) : key;
    }

    public String teamLabel(int team) {
        return language != null ? language.teamLabel(team) : ("Team #" + team);
    }

    public String healthPresetLabel(HealthPreset preset) {
        return language != null ? language.healthPresetName(preset) : preset.name();
    }

    public String stateLabel(boolean enabled) {
        return language != null ? language.state(enabled) : String.valueOf(enabled);
    }

    @Override
    public boolean isBuiltinScoreboardEnabled() {
        return scoreboardEnabled;
    }

    @Override
    public TeamLifeBindMatchSnapshot getMatchSnapshot() {
        return new TeamLifeBindMatchSnapshot(
            engine.isRunning(),
            engine.configuredTeamCount() > 0 ? engine.configuredTeamCount() : teamCount,
            healthPreset,
            Bukkit.getOnlinePlayers().size(),
            readyPlayers,
            engine.assignments(),
            teamBedSpawns.keySet(),
            noRespawnEnabled,
            noRespawnDimensions,
            activeMatchWorldName
        );
    }

    @Override
    public TeamLifeBindPlayerSnapshot getPlayerSnapshot(UUID playerId) {
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        return new TeamLifeBindPlayerSnapshot(
            playerId,
            playerId == null ? null : engine.teamForPlayer(playerId),
            player != null && player.isOnline(),
            playerId != null && readyPlayers.contains(playerId),
            player != null && player.getGameMode() == GameMode.SPECTATOR,
            playerId != null && isRespawnLocked(playerId),
            -1,
            player != null && player.getWorld() != null ? player.getWorld().getName() : null
        );
    }

    @Override
    public TeamLifeBindSidebarSnapshot getSidebarSnapshot(UUID viewerId) {
        Player viewer = viewerId == null ? null : Bukkit.getPlayer(viewerId);
        return new TeamLifeBindSidebarSnapshot(text("scoreboard.title"), buildSidebarLines(viewer));
    }

    public void markReady(Player player) {
        handleReadyStateChange(player, true);
    }

    public void unready(Player player) {
        handleReadyStateChange(player, false);
    }

    public void onPlayerLeave(UUID playerId) {
        readyPlayers.remove(playerId);
        clearManagedScoreboard(playerId);
        trackedLocatorTargets.remove(playerId);
        trackedLocatorAngles.remove(playerId);
        supplyBoatDropConfirmUntil.remove(playerId);
        respawnInvulnerabilityTokens.remove(playerId);
        evaluateReadyCountdown();
    }

    public void handlePlayerJoin(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        onPlayerLeave(player.getUniqueId());
        if (!engine.isRunning()) {
            teleportToLobby(player);
            sendLobbyGuide(player);
            return;
        }

        Integer team = engine.teamForPlayer(player.getUniqueId());
        if (team == null) {
            moveUnassignedPlayerToSpectator(player, true);
            return;
        }

        if (isRespawnLocked(player.getUniqueId())) {
            movePlayerToSpectator(player, resolveCurrentSpectatorLocation(), false);
            return;
        }

        player.setGameMode(GameMode.SURVIVAL);
        if (!isActiveMatchWorld(player.getWorld())) {
            Location target = resolveRespawnLocation(player.getUniqueId());
            if (target != null) {
                player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
    }

    public void sendLobbyGuide(Player player) {
        player.sendMessage(ChatColor.GOLD + text("lobby.guide"));
    }

    public void teleportToLobby(Player player) {
        Location lobbySpawn = resolveLobbySpawn();
        if (lobbySpawn == null) {
            return;
        }
        preparePlayerForLobby(player);
        player.teleport(lobbySpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public Location resolvePortalDestination(Location from, PlayerTeleportEvent.TeleportCause cause) {
        if (from == null || from.getWorld() == null || cause == null) {
            return null;
        }

        String base = engine.isRunning() ? activeMatchWorldName : lobbyWorldName;
        if (base == null || base.isBlank()) {
            return null;
        }

        String fromWorld = from.getWorld().getName();
        String nether = base + "_nether";
        String end = base + "_the_end";

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (fromWorld.equals(base)) {
                return portalLocation(nether, from.getBlockX() / 8, from.getBlockZ() / 8);
            }
            if (fromWorld.equals(nether)) {
                return portalLocation(base, from.getBlockX() * 8, from.getBlockZ() * 8);
            }
            return null;
        }

        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (fromWorld.equals(base)) {
                return portalLocation(end, 0, 0);
            }
            if (fromWorld.equals(end)) {
                return portalLocation(base, 0, 0);
            }
        }

        return null;
    }

    public Location resolveLobbySpawn() {
        World lobby = ensureLobbyWorld();
        if (lobby == null) {
            return null;
        }
        return new Location(lobby, 0.5D, 101.0D, 0.5D);
    }

    public boolean isInLobby(Player player) {
        return player != null && isLobbyWorld(player.getWorld());
    }

    public boolean isLobbyWorld(World world) {
        return world != null && lobbyWorldName != null && lobbyWorldName.equals(world.getName());
    }

    public String noRespawnStatusText() {
        List<String> blocked = new ArrayList<>(noRespawnDimensions);
        String blockedText = blocked.isEmpty() ? text("scoreboard.value.none") : blocked.toString();
        return ChatColor.YELLOW + text("norespawn.status", stateLabel(noRespawnEnabled), blockedText);
    }

    public void setNoRespawnEnabled(boolean enabled) {
        this.noRespawnEnabled = enabled;
        if (!enabled) {
            noRespawnLockedPlayers.clear();
        }
        getConfig().set("no-respawn.enabled", this.noRespawnEnabled);
        saveConfig();
    }

    public boolean isAnnounceTeamAssignmentEnabled() {
        return announceTeamAssignment;
    }

    public void setAnnounceTeamAssignment(boolean enabled) {
        this.announceTeamAssignment = enabled;
        getConfig().set("announce-team-assignment", this.announceTeamAssignment);
        saveConfig();
    }

    public void setBuiltinScoreboardEnabled(boolean enabled) {
        this.scoreboardEnabled = enabled;
        getConfig().set("scoreboard.enabled", this.scoreboardEnabled);
        saveConfig();
        startScoreboardTask();
    }

    public boolean isTabEnabled() {
        return tabEnabled;
    }

    public void setTabEnabled(boolean enabled) {
        this.tabEnabled = enabled;
        getConfig().set("tab.enabled", this.tabEnabled);
        saveConfig();
        startTabTask();
        updateBuiltInScoreboards();
    }

    public boolean isAdvancementsEnabled() {
        return advancementsEnabled;
    }

    public void setAdvancementsEnabled(boolean enabled) {
        this.advancementsEnabled = enabled;
        getConfig().set("advancements.enabled", this.advancementsEnabled);
        saveConfig();
        applyManagedWorldRules();
    }

    public boolean addNoRespawnDimension(String rawDimensionId) {
        String normalized = normalizeDimensionId(rawDimensionId);
        if (normalized == null) {
            return false;
        }
        boolean changed = noRespawnDimensions.add(normalized);
        if (changed) {
            getConfig().set("no-respawn.blocked-dimensions", new ArrayList<>(noRespawnDimensions));
            saveConfig();
        }
        return true;
    }

    public boolean removeNoRespawnDimension(String rawDimensionId) {
        String normalized = normalizeDimensionId(rawDimensionId);
        if (normalized == null) {
            return false;
        }
        boolean changed = noRespawnDimensions.remove(normalized);
        if (changed) {
            getConfig().set("no-respawn.blocked-dimensions", new ArrayList<>(noRespawnDimensions));
            saveConfig();
        }
        return changed;
    }

    public void clearNoRespawnDimensions() {
        noRespawnDimensions.clear();
        getConfig().set("no-respawn.blocked-dimensions", new ArrayList<>(noRespawnDimensions));
        saveConfig();
    }

    public boolean isRespawnLocked(UUID playerId) {
        return noRespawnEnabled && noRespawnLockedPlayers.contains(playerId);
    }

    public void handleBlockedRespawn(Player player) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(ChatColor.RED + text("player.eliminated.no_respawn"));
        });
    }

    public void setTeamCount(int newCount) {
        this.teamCount = Math.max(2, Math.min(32, newCount));
        getConfig().set("team-count", this.teamCount);
        saveConfig();
    }

    public void setHealthPreset(HealthPreset preset) {
        this.healthPreset = preset;
        getConfig().set("health-preset", preset.name());
        saveConfig();
    }

    public boolean setTeamSpawn(int team, Location location) {
        if (team < 1 || team > 32 || location == null || location.getWorld() == null) {
            return false;
        }
        configuredTeamSpawns.put(team, location.clone());
        getConfig().set("spawns." + team, location);
        saveConfig();
        return true;
    }

    public void clearSpawns() {
        configuredTeamSpawns.clear();
        getConfig().set("spawns", null);
        getConfig().createSection("spawns");
        saveConfig();
    }

    public boolean isTeamBedItem(ItemStack stack) {
        return hasItemMarker(stack, teamBedItemKey);
    }

    public void normalizeTeamBedInventory(Player player) {
        if (player == null) {
            return;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            normalizeTeamBedStack(stack);
        }
        normalizeTeamBedStack(player.getInventory().getItemInOffHand());
    }

    public void consumeResidualDroppedTeamBedToken(Player player, ItemStack droppedStack) {
        if (player == null || player.getGameMode() == GameMode.CREATIVE || !isTeamBedItem(droppedStack)) {
            return;
        }
        Integer ownerTeam = resolveTeamBedOwnerTeam(droppedStack);
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isResidualDroppedTeamBed(stack, ownerTeam)) {
                continue;
            }
            player.getInventory().setItem(slot, null);
            return;
        }
    }

    public ItemStack previewCraftedTeamBed(Player player) {
        Integer ownerTeam = player == null ? null : engine.teamForPlayer(player.getUniqueId());
        return createTeamBedItem(ownerTeam);
    }

    public void explodeVanillaBed(Player player, Location placementLocation) {
        Location center = placementLocation.clone().add(0.5D, 0.5D, 0.5D);
        Bukkit.getScheduler().runTask(this, () -> {
            World world = center.getWorld();
            if (world != null) {
                world.createExplosion(center, 3.0F, false, false, player);
            }
        });
    }

    public TeamBedPlacementResult registerPlacedTeamBed(Player player, ItemStack stack, Location bedFoot) {
        if (!isTeamBedItem(stack)) {
            return TeamBedPlacementResult.EXPLODE;
        }
        if (!engine.isRunning()) {
            player.sendMessage(ChatColor.RED + text("bed.place_only_in_match"));
            return TeamBedPlacementResult.DENIED;
        }

        Integer playerTeam = engine.teamForPlayer(player.getUniqueId());
        if (playerTeam == null) {
            player.sendMessage(ChatColor.RED + text("bed.not_on_team"));
            return TeamBedPlacementResult.DENIED;
        }

        Integer ownerTeam = resolveTeamBedOwnerTeam(stack);
        if (ownerTeam == null || !ownerTeam.equals(playerTeam)) {
            player.sendMessage(ChatColor.RED + text("bed.wrong_team", ownerTeam != null ? teamLabel(ownerTeam) : text("scoreboard.value.none")));
            return TeamBedPlacementResult.EXPLODE;
        }

        Location normalized = normalizeBlockCenter(bedFoot);
        placedTeamBedOwners.put(bedLocationKey(normalized), ownerTeam);
        teamBedSpawns.put(ownerTeam, normalized);
        player.sendMessage(ChatColor.GREEN + text("bed.updated", teamLabel(ownerTeam)));
        return TeamBedPlacementResult.ACCEPTED;
    }

    public Integer clearTeamBedAt(Location bedFoot) {
        Location normalized = normalizeBlockCenter(bedFoot);
        Integer ownerTeam = placedTeamBedOwners.remove(bedLocationKey(normalized));
        if (ownerTeam == null) {
            for (Map.Entry<Integer, Location> entry : teamBedSpawns.entrySet()) {
                if (sameBlock(entry.getValue(), normalized)) {
                    ownerTeam = entry.getKey();
                    break;
                }
            }
        }
        if (ownerTeam == null) {
            return null;
        }

        Location activeBed = teamBedSpawns.get(ownerTeam);
        if (sameBlock(activeBed, normalized)) {
            teamBedSpawns.remove(ownerTeam);
            Bukkit.broadcastMessage(ChatColor.YELLOW + text("bed.destroyed", teamLabel(ownerTeam)));
        }
        return ownerTeam;
    }

    public ItemStack createOwnedTeamBedDrop(int ownerTeam) {
        ItemStack item = createTeamBedItem(ownerTeam);
        item.setAmount(1);
        return item;
    }

    public Location resolveRespawnLocation(UUID playerId) {
        if (!engine.isRunning()) {
            return null;
        }

        Integer team = engine.teamForPlayer(playerId);
        if (team == null) {
            return null;
        }

        Location bed = teamBedSpawns.get(team);
        if (isValidBedBlock(bed)) {
            return findSafeRespawnAroundBed(bed);
        }
        if (bed != null) {
            teamBedSpawns.remove(team);
        }

        Location teamSpawn = matchTeamSpawns.get(team);
        if (teamSpawn == null) {
            teamSpawn = configuredTeamSpawns.get(team);
        }
        if (teamSpawn == null || teamSpawn.getWorld() == null) {
            return null;
        }
        return resolveSafeRespawnBase(teamSpawn);
    }

    public void grantRespawnInvulnerability(Player player) {
        if (player == null) {
            return;
        }
        long token = System.nanoTime();
        respawnInvulnerabilityTokens.put(player.getUniqueId(), token);
        player.setInvulnerable(true);
        player.setNoDamageTicks((int) RESPAWN_INVULNERABILITY_TICKS);
        Bukkit.getScheduler().runTaskLater(this, () -> clearRespawnInvulnerability(player.getUniqueId(), token), RESPAWN_INVULNERABILITY_TICKS);
    }

    public void startMatch(CommandSender sender) {
        if (hasActiveMatchSession()) {
            sender.sendMessage(ChatColor.RED + text("match.already_running"));
            return;
        }

        List<UUID> queuedPlayers = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        if (queuedPlayers.size() < teamCount) {
            sender.sendMessage(ChatColor.RED + text("match.need_players", teamCount));
            return;
        }

        cleanupActiveMatchWorld();
        World world = createNewMatchWorld();
        if (world == null) {
            sender.sendMessage(ChatColor.RED + text("match.create_world_failed"));
            return;
        }

        cancelCountdown();
        clearTrackedLocatorTargets();
        readyPlayers.clear();
        matchTeamSpawns.clear();
        teamBedSpawns.clear();
        placedTeamBedOwners.clear();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        supplyBoatDropConfirmUntil.clear();
        sender.sendMessage(ChatColor.YELLOW + text("match.preparing_world"));

        resolveTeamBaseSpawnsAsync(world, teamCount).whenComplete((baseSpawns, throwable) ->
            Bukkit.getScheduler().runTask(this, () -> {
                if (throwable != null || baseSpawns == null || baseSpawns.size() < teamCount) {
                    sender.sendMessage(ChatColor.RED + text("match.prepare_spawns_failed"));
                    cleanupActiveMatchWorld();
                    return;
                }
                if (engine.isRunning()) {
                    return;
                }
                if (activeMatchWorldName == null || !activeMatchWorldName.equals(world.getName())) {
                    sender.sendMessage(ChatColor.RED + text("match.prepare_interrupted"));
                    return;
                }

                List<Player> online = new ArrayList<>();
                for (UUID playerId : queuedPlayers) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        online.add(player);
                    }
                }
                if (online.size() < teamCount) {
                    sender.sendMessage(ChatColor.RED + text("match.need_players_after_prepare", teamCount));
                    cleanupActiveMatchWorld();
                    return;
                }

                StartResult result = engine.start(online.stream().map(Player::getUniqueId).toList(), new GameOptions(teamCount, healthPreset));
                Map<Player, Location> assignedSpawns = new LinkedHashMap<>();
                matchTeamSpawns.clear();
                for (Map.Entry<Integer, Location> entry : baseSpawns.entrySet()) {
                    matchTeamSpawns.put(entry.getKey(), entry.getValue().clone());
                }

                for (Player player : online) {
                    int team = result.teamByPlayer().get(player.getUniqueId());
                    Location spawn = randomizeAround(baseSpawns.get(team));
                    assignedSpawns.put(player, spawn);
                }

                preloadLocationsAsync(assignedSpawns.values(), TELEPORT_PRELOAD_RADIUS_BLOCKS).whenComplete((unused, preloadError) ->
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (!engine.isRunning() || activeMatchWorldName == null || !activeMatchWorldName.equals(world.getName())) {
                            return;
                        }
                        if (preloadError != null) {
                            getLogger().warning("Failed to preload teleport chunks: " + preloadError.getMessage());
                        }

                        for (Map.Entry<Player, Location> entry : assignedSpawns.entrySet()) {
                            Player player = entry.getKey();
                            if (!player.isOnline()) {
                                continue;
                            }
                            int team = result.teamByPlayer().get(player.getUniqueId());
                            preparePlayerForMatch(player);
                            applyPresetHealth(player);
                            player.teleport(entry.getValue(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                            player.sendMessage(ChatColor.AQUA + text("match.player_assignment", teamLabel(team), healthPresetLabel(healthPreset)));
                        }

                        broadcastImportantNotice(text("match.notice.start.title"), text("match.notice.start.subtitle"), IMPORTANT_NOTICE_STAY_TICKS);

                        if (announceTeamAssignment) {
                            announceTeams(result.teamByPlayer());
                        }
                    })
                );
            })
        );
    }

    public void stopMatch(CommandSender sender) {
        if (!hasActiveMatchSession()) {
            sender.sendMessage(ChatColor.YELLOW + text("match.no_running"));
            return;
        }
        if (engine.isRunning()) {
            engine.stop();
        }
        cancelCountdown();
        clearTrackedLocatorTargets();
        readyPlayers.clear();
        matchTeamSpawns.clear();
        teamBedSpawns.clear();
        placedTeamBedOwners.clear();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        supplyBoatDropConfirmUntil.clear();
        teleportAllToLobby();
        cleanupActiveMatchWorld();
        Bukkit.broadcastMessage(ChatColor.GOLD + text("match.stopped"));
    }

    public void handlePlayerDeath(Player deadPlayer) {
        if (engine.isRunning() && shouldLockNoRespawn(deadPlayer.getWorld())) {
            noRespawnLockedPlayers.add(deadPlayer.getUniqueId());
        }

        var result = engine.onPlayerDeath(deadPlayer.getUniqueId());
        if (!result.triggered()) {
            return;
        }
        if (!acquireTeamWipeGuard(result.team())) {
            return;
        }

        notifyTeamWiped(result.team(), result.victims());

        Bukkit.getScheduler().runTask(this, () -> {
            for (UUID victimId : result.victims()) {
                if (victimId.equals(deadPlayer.getUniqueId())) {
                    continue;
                }
                Player victim = Bukkit.getPlayer(victimId);
                if (victim != null && victim.isOnline() && !victim.isDead()) {
                    victim.setHealth(0.0D);
                }
            }
        });
    }

    public void handleDragonKill(Player killer) {
        if (!engine.isRunning()) {
            return;
        }
        Integer winner = engine.onObjectiveCompleted(killer.getUniqueId());
        if (winner == null) {
            return;
        }
        declareWinner(winner, text("match.win_reason.dragon", killer.getName()));
    }

    public String statusText() {
        if (!engine.isRunning()) {
            return ChatColor.YELLOW + text("match.status.idle.paper");
        }
        return ChatColor.GREEN
            + text("match.status.running", teamCount, healthPresetLabel(healthPreset), sortedActiveTeams(teamBedSpawns.keySet()), stateLabel(noRespawnEnabled));
    }

    private void declareWinner(int team, String reason) {
        broadcastImportantNotice(text("match.win", teamLabel(team), reason), "", IMPORTANT_NOTICE_STAY_TICKS);
        Bukkit.broadcastMessage(ChatColor.GREEN + text("match.win", teamLabel(team), reason));
        engine.stop();
        clearTrackedLocatorTargets();
        readyPlayers.clear();
        matchTeamSpawns.clear();
        teamBedSpawns.clear();
        placedTeamBedOwners.clear();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        supplyBoatDropConfirmUntil.clear();
        teleportAllToLobby();
        cleanupActiveMatchWorld();
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

    private void loadSettingsFromConfig() {
        this.teamCount = Math.max(2, Math.min(32, getConfig().getInt("team-count", 2)));
        this.healthPreset = HealthPreset.fromString(getConfig().getString("health-preset", "ONE_HEART"));
        this.randomSpawnRadius = Math.max(128, getConfig().getInt("random-spawn-radius", 750));
        this.minTeamDistance = Math.max(64, getConfig().getInt("min-team-distance", 280));
        this.teamSpreadRadius = Math.max(0, getConfig().getInt("team-spread-radius", 16));
        this.announceTeamAssignment = getConfig().getBoolean("announce-team-assignment", true);
        this.readyCountdownSeconds = Math.max(3, getConfig().getInt("ready-countdown-seconds", 10));
        this.lobbyWorldName = getConfig().getString("lobby-world-directory", "tlb_lobby");
        if (this.lobbyWorldName == null || this.lobbyWorldName.isBlank()) {
            this.lobbyWorldName = "tlb_lobby";
        }
        this.matchWorldPrefix = getConfig().getString("match-world-prefix", "tlb_match");
        if (this.matchWorldPrefix == null || this.matchWorldPrefix.isBlank()) {
            this.matchWorldPrefix = "tlb_match";
        }
        this.scoreboardEnabled = getConfig().getBoolean("scoreboard.enabled", true);
        this.tabEnabled = getConfig().getBoolean("tab.enabled", true);
        this.advancementsEnabled = getConfig().getBoolean("advancements.enabled", false);
        this.scoreboardUpdateIntervalTicks = Math.max(5L, getConfig().getLong("scoreboard.update-interval-ticks", 20L));
        this.noRespawnEnabled = getConfig().getBoolean("no-respawn.enabled", true);

        this.noRespawnDimensions.clear();
        if (!getConfig().isSet("no-respawn.blocked-dimensions")) {
            this.noRespawnDimensions.add("minecraft:the_end");
        } else {
            List<String> blockedDims = getConfig().getStringList("no-respawn.blocked-dimensions");
            for (String raw : blockedDims) {
                String normalized = normalizeDimensionId(raw);
                if (normalized != null) {
                    this.noRespawnDimensions.add(normalized);
                }
            }
        }

        this.configuredTeamSpawns.clear();
        ConfigurationSection spawns = getConfig().getConfigurationSection("spawns");
        if (spawns == null) {
            return;
        }
        for (String key : spawns.getKeys(false)) {
            try {
                int team = Integer.parseInt(key);
                Location location = spawns.getLocation(key);
                if (team >= 1 && location != null && location.getWorld() != null) {
                    configuredTeamSpawns.put(team, location);
                }
            } catch (NumberFormatException ignored) {
                getLogger().warning("Invalid team spawn key: " + key);
            }
        }
    }

    private void registerTeamBedRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "team_bed_recipe");
        Bukkit.removeRecipe(recipeKey);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createTeamBedItem(null));
        recipe.shape("WWW", "WWW", "PPP");
        recipe.setIngredient('W', new RecipeChoice.MaterialChoice(new ArrayList<>(Tag.WOOL.getValues())));
        recipe.setIngredient('P', new RecipeChoice.MaterialChoice(new ArrayList<>(Tag.PLANKS.getValues())));
        Bukkit.addRecipe(recipe);
    }

    private ItemStack createTeamBedItem(Integer ownerTeam) {
        ItemStack item = new ItemStack(Material.WHITE_BED, 2);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String displayName = ChatColor.GOLD + text("item.team_bed.name");
        if (ownerTeam != null) {
            displayName += ChatColor.YELLOW + " [" + teamLabel(ownerTeam) + "]";
        }
        meta.setDisplayName(displayName);
        meta.setLore(List.of(ChatColor.GRAY + text("item.team_bed.lore")));
        meta.setMaxStackSize(2);
        meta.getPersistentDataContainer().set(teamBedItemKey, PersistentDataType.BYTE, (byte) 1);
        if (ownerTeam != null) {
            meta.getPersistentDataContainer().set(teamBedOwnerTeamKey, PersistentDataType.INTEGER, ownerTeam);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void normalizeTeamBedStack(ItemStack stack) {
        if (!isTeamBedItem(stack) || stack.getAmount() >= 2) {
            return;
        }
        stack.setAmount(2);
    }

    private boolean isResidualDroppedTeamBed(ItemStack stack, Integer ownerTeam) {
        return isTeamBedItem(stack) && stack.getAmount() == 1 && sameTeamBedOwner(resolveTeamBedOwnerTeam(stack), ownerTeam);
    }

    private boolean sameTeamBedOwner(Integer left, Integer right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private void initializeItemKeys() {
        teamBedItemKey = new NamespacedKey(this, "team_bed_item");
        teamBedOwnerTeamKey = new NamespacedKey(this, "team_bed_owner_team");
        supplyBoatItemKey = new NamespacedKey(this, "supply_boat_item");
        lobbyMenuItemKey = new NamespacedKey(this, "lobby_menu_item");
        lobbyMenuActionKey = new NamespacedKey(this, "lobby_menu_action");
    }

    private void preparePlayerForLobby(Player player) {
        resetPlayerState(player, true);
        giveLobbyItems(player);
    }

    private void preparePlayerForMatch(Player player) {
        resetPlayerState(player, false);
        giveMatchStarterItems(player);
    }

    private void preparePlayerForSpectator(Player player) {
        resetPlayerState(player, false);
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void resetPlayerState(Player player, boolean lobbyState) {
        player.closeInventory();
        clearRespawnInvulnerability(player);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.getInventory().setHeldItemSlot(0);
        player.setExp(0.0F);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0D);
        } else {
            player.setMaxHealth(20.0D);
        }
        player.setHealth(20.0D);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setGameMode(lobbyState ? GameMode.ADVENTURE : GameMode.SURVIVAL);
    }

    private void giveLobbyItems(Player player) {
        player.getInventory().setItem(LOBBY_MENU_SLOT, createLobbyMenuItem());
        player.getInventory().setItem(LOBBY_GUIDE_SLOT, createGuideBookItem());
        player.getInventory().setItem(LOBBY_RECIPES_SLOT, createRecipeBookItem());
    }

    private void giveMatchStarterItems(Player player) {
        player.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.WOODEN_PICKAXE));
        player.getInventory().setItem(2, new ItemStack(Material.WOODEN_AXE));
        player.getInventory().setItem(3, createSupplyBoatItem());
    }

    public Location resolveCurrentSpectatorLocation() {
        for (Location spawn : matchTeamSpawns.values()) {
            if (spawn != null && spawn.getWorld() != null) {
                return spawn.clone().add(0.0D, 8.0D, 0.0D);
            }
        }
        if (activeMatchWorldName != null && !activeMatchWorldName.isBlank()) {
            World world = Bukkit.getWorld(activeMatchWorldName);
            if (world != null) {
                int y = Math.max(world.getSeaLevel() + 8, world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 8);
                return new Location(world, 0.5D, y, 0.5D);
            }
        }
        return resolveLobbySpawn();
    }

    private ItemStack createLobbyMenuItem() {
        ItemStack item = new ItemStack(Material.COMPASS, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.AQUA + text("item.menu.name"));
        meta.setLore(List.of(ChatColor.GRAY + text("item.menu.lore")));
        meta.getPersistentDataContainer().set(lobbyMenuItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuideBookItem() {
        return createWrittenBookItem(
            text("item.guide.name"),
            text("book.guide.title"),
            text("book.guide.author"),
            List.of(text("book.guide.page1"), text("book.guide.page2"))
        );
    }

    private ItemStack createRecipeBookItem() {
        return createWrittenBookItem(
            text("item.recipes.name"),
            text("book.recipes.title"),
            text("book.recipes.author"),
            List.of(text("book.recipes.page1"), text("book.recipes.page2"))
        );
    }

    private ItemStack createWrittenBookItem(String displayName, String title, String author, List<String> pages) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK, 1);
        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            return item;
        }
        meta.setDisplayName(ChatColor.GOLD + displayName);
        meta.setTitle(title);
        meta.setAuthor(author);
        meta.setPages(pages);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isLobbyMenuItem(ItemStack stack) {
        return hasItemMarker(stack, lobbyMenuItemKey);
    }

    public boolean isSupplyBoatItem(ItemStack stack) {
        return hasItemMarker(stack, supplyBoatItemKey);
    }

    public boolean isDiscardProtectedBoatItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        String name = stack.getType().name();
        return name.endsWith("_BOAT") || name.endsWith("_RAFT");
    }

    public void ensureSupplyBoat(Player player) {
        if (player == null || !engine.isRunning()) {
            return;
        }
        supplyBoatDropConfirmUntil.remove(player.getUniqueId());
        if (playerHasSupplyBoat(player)) {
            return;
        }
        player.getInventory().addItem(createSupplyBoatItem());
    }

    public boolean shouldDiscardSupplyBoatNow(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long expiresAt = supplyBoatDropConfirmUntil.get(playerId);
        if (expiresAt != null && expiresAt >= now) {
            supplyBoatDropConfirmUntil.remove(playerId);
            return true;
        }
        supplyBoatDropConfirmUntil.put(playerId, now + SUPPLY_BOAT_DROP_CONFIRM_WINDOW_MS);
        return false;
    }

    public void notifySupplyBoatDropHint(Player player) {
        if (player != null) {
            player.sendActionBar(ChatColor.YELLOW + text("boat.drop_confirm"));
        }
    }

    public void notifySupplyBoatDiscarded(Player player) {
        if (player != null) {
            player.sendActionBar(ChatColor.GRAY + text("boat.discarded"));
        }
    }

    private ItemStack createSupplyBoatItem() {
        ItemStack item = new ItemStack(Material.OAK_BOAT, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(supplyBoatItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean hasItemMarker(ItemStack stack, NamespacedKey key) {
        if (stack == null || key == null || stack.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean isActiveMatchWorld(World world) {
        if (world == null || activeMatchWorldName == null || activeMatchWorldName.isBlank()) {
            return false;
        }
        String worldName = world.getName();
        return worldName.equals(activeMatchWorldName)
            || worldName.equals(activeMatchWorldName + "_nether")
            || worldName.equals(activeMatchWorldName + "_the_end");
    }

    public void movePlayerToSpectator(Player player, Location target, boolean resetState) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (resetState) {
            preparePlayerForSpectator(player);
        } else {
            player.setGameMode(GameMode.SPECTATOR);
        }
        if (target != null) {
            player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }

    public void moveUnassignedPlayerToSpectator(Player player, boolean notify) {
        movePlayerToSpectator(player, resolveCurrentSpectatorLocation(), true);
        if (notify) {
            player.sendMessage(ChatColor.YELLOW + text("player.unassigned_spectator"));
        }
    }

    private boolean playerHasSupplyBoat(Player player) {
        if (player == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isSupplyBoatItem(stack)) {
                return true;
            }
        }
        return isSupplyBoatItem(player.getInventory().getItemInOffHand());
    }

    private Integer resolveTeamBedOwnerTeam(ItemStack stack) {
        if (!isTeamBedItem(stack)) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(teamBedOwnerTeamKey, PersistentDataType.INTEGER);
    }

    private void clearRespawnInvulnerability(UUID playerId, long token) {
        Long currentToken = respawnInvulnerabilityTokens.get(playerId);
        if (currentToken == null || currentToken.longValue() != token) {
            return;
        }
        respawnInvulnerabilityTokens.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.setInvulnerable(false);
            player.setNoDamageTicks(0);
        }
    }

    private void clearRespawnInvulnerability(Player player) {
        if (player == null) {
            return;
        }
        respawnInvulnerabilityTokens.remove(player.getUniqueId());
        player.setInvulnerable(false);
        player.setNoDamageTicks(0);
    }

    private String bedLocationKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private CompletableFuture<Map<Integer, Location>> resolveTeamBaseSpawnsAsync(World world, int teams) {
        Map<Integer, SpawnAnchor> plannedAnchors = planTeamBaseAnchors(world, teams);
        CompletableFuture<Map<Integer, Location>> future = new CompletableFuture<>();

        preloadAnchorsAsync(world, plannedAnchors.values(), MATCH_PRELOAD_SEARCH_RADIUS_BLOCKS).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }

            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    Map<Integer, Location> resolved = new LinkedHashMap<>();
                    for (Map.Entry<Integer, SpawnAnchor> entry : plannedAnchors.entrySet()) {
                        SpawnAnchor anchor = entry.getValue();
                        Location safe = safeSurface(world, anchor.x(), anchor.z(), SAFE_SPAWN_SEARCH_RADIUS_BLOCKS);
                        if (safe == null) {
                            safe = forcedSafeSurface(world, anchor.x(), anchor.z());
                        }
                        safe.setYaw(anchor.yaw());
                        safe.setPitch(anchor.pitch());
                        resolved.put(entry.getKey(), safe);
                    }
                    future.complete(resolved);
                } catch (Throwable ex) {
                    future.completeExceptionally(ex);
                }
            });
        });

        return future;
    }

    private Map<Integer, SpawnAnchor> planTeamBaseAnchors(World world, int teams) {
        Map<Integer, SpawnAnchor> planned = new LinkedHashMap<>();
        List<SpawnAnchor> chosen = new ArrayList<>();
        Location center = world.getSpawnLocation();

        for (int team = 1; team <= teams; team++) {
            Location configured = configuredTeamSpawns.get(team);
            if (configured != null) {
                SpawnAnchor anchor = new SpawnAnchor(configured.getBlockX(), configured.getBlockZ(), configured.getYaw(), configured.getPitch());
                planned.put(team, anchor);
                chosen.add(anchor);
                continue;
            }

            SpawnAnchor selected = null;
            int minDistance = minTeamDistance;
            for (int relax = 0; relax < 8 && selected == null; relax++) {
                selected = findRandomTeamBaseAnchor(center, chosen, minDistance);
                minDistance = Math.max(64, (int) Math.floor(minDistance * 0.85D));
            }
            if (selected == null) {
                selected = fallbackRandomTeamBaseAnchor(center);
            }

            planned.put(team, selected);
            chosen.add(selected);
        }

        return planned;
    }

    private SpawnAnchor findRandomTeamBaseAnchor(Location center, List<SpawnAnchor> chosen, int minDistance) {
        for (int attempt = 0; attempt < SPAWN_SEARCH_ATTEMPTS; attempt++) {
            SpawnAnchor candidate = randomSurfaceAnchor(center, randomSpawnRadius);
            if (isFarEnough(candidate, chosen, minDistance)) {
                return candidate;
            }
        }
        return null;
    }

    private SpawnAnchor fallbackRandomTeamBaseAnchor(Location center) {
        return randomSurfaceAnchor(center, randomSpawnRadius);
    }

    private SpawnAnchor randomSurfaceAnchor(Location center, int radius) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        return new SpawnAnchor(x, z, center.getYaw(), center.getPitch());
    }

    private boolean isFarEnough(SpawnAnchor candidate, List<SpawnAnchor> chosen, int minDistance) {
        long minDistanceSq = (long) minDistance * minDistance;
        for (SpawnAnchor existing : chosen) {
            long dx = candidate.x() - existing.x();
            long dz = candidate.z() - existing.z();
            long distSq = (dx * dx) + (dz * dz);
            if (distSq < minDistanceSq) {
                return false;
            }
        }
        return true;
    }

    private Location randomizeAround(Location base) {
        if (teamSpreadRadius <= 0) {
            return base.clone();
        }

        World world = base.getWorld();
        if (world == null) {
            return base.clone();
        }

        int offsetX = random.nextInt((teamSpreadRadius * 2) + 1) - teamSpreadRadius;
        int offsetZ = random.nextInt((teamSpreadRadius * 2) + 1) - teamSpreadRadius;
        int x = base.getBlockX() + offsetX;
        int z = base.getBlockZ() + offsetZ;
        Location safe = safeSurface(world, x, z, SAFE_SPAWN_SEARCH_RADIUS_BLOCKS);
        if (safe == null) {
            safe = forcedSafeSurface(world, x, z);
        }
        safe.setYaw(base.getYaw());
        safe.setPitch(base.getPitch());
        return safe;
    }

    private Location safeSurface(World world, int x, int z) {
        return safeSurface(world, x, z, SAFE_SPAWN_SEARCH_RADIUS_BLOCKS);
    }

    private Location safeSurface(World world, int x, int z, int searchRadius) {
        Location exact = findSafeSurfaceAt(world, x, z);
        if (exact != null) {
            return exact;
        }

        List<Location> ringCandidates = new ArrayList<>();
        for (int distance = 1; distance <= searchRadius; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                ringCandidates.add(findSafeSurfaceAt(world, x + dx, z + distance));
                ringCandidates.add(findSafeSurfaceAt(world, x + dx, z - distance));
            }
            for (int dz = -distance + 1; dz <= distance - 1; dz++) {
                ringCandidates.add(findSafeSurfaceAt(world, x + distance, z + dz));
                ringCandidates.add(findSafeSurfaceAt(world, x - distance, z + dz));
            }
        }

        return ringCandidates.stream()
            .filter(location -> location != null)
            .min(Comparator.comparingDouble(location -> horizontalDistanceSquared(location, x, z)))
            .orElse(null);
    }

    private Location forcedSafeSurface(World world, int x, int z) {
        Location nearby = safeSurface(world, x, z, MATCH_PRELOAD_SEARCH_RADIUS_BLOCKS);
        if (nearby != null) {
            return nearby;
        }

        if (world.getEnvironment() == World.Environment.NETHER) {
            int y = Math.max(world.getSeaLevel() + 1, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
            return new Location(world, x + 0.5D, y, z + 0.5D);
        }
        return createFallbackSpawnPlatform(world, x, z);
    }

    private Location findSafeSurfaceAt(World world, int x, int z) {
        int minY = world.getMinHeight() + 1;
        int topY = Math.min(world.getMaxHeight() - 2, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
        for (int y = topY; y >= Math.max(minY, topY - 24); y--) {
            Location feet = new Location(world, x + 0.5D, y, z + 0.5D);
            if (isSafeSpawnLocation(feet)) {
                return feet;
            }
        }
        return null;
    }

    private boolean isSafeSpawnLocation(Location feet) {
        if (feet == null || feet.getWorld() == null) {
            return false;
        }
        Block feetBlock = feet.getBlock();
        Block headBlock = feet.clone().add(0.0D, 1.0D, 0.0D).getBlock();
        Block groundBlock = feet.clone().add(0.0D, -1.0D, 0.0D).getBlock();
        return isSafeSpawnSpace(feetBlock)
            && isSafeSpawnSpace(headBlock)
            && isSafeGroundBlock(feet.getWorld(), groundBlock);
    }

    private boolean isSafeSpawnSpace(Block block) {
        Material type = block.getType();
        return block.isPassable() && !hasUnsafeFluid(block) && !isHazardousMaterial(type) && !isUnsafeAquaticSpawnBlock(type);
    }

    private boolean isSafeGroundBlock(World world, Block block) {
        if (world == null || block == null) {
            return false;
        }
        Material type = block.getType();
        if (hasUnsafeFluid(block) || isHazardousMaterial(type) || isUnsafeAquaticSpawnBlock(type)) {
            return false;
        }
        return switch (world.getEnvironment()) {
            case NORMAL -> type == Material.GRASS_BLOCK;
            case THE_END -> type == Material.END_STONE;
            default -> type.isSolid() && !Tag.LOGS.isTagged(type) && !type.name().endsWith("_LEAVES");
        };
    }

    private Location createFallbackSpawnPlatform(World world, int x, int z) {
        int highestY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int feetY = Math.max(world.getSeaLevel() + 2, highestY + 1);
        int platformY = Math.max(world.getMinHeight(), feetY - 1);
        Material topMaterial = fallbackPlatformTopMaterial(world);
        Material foundationMaterial = topMaterial == Material.GRASS_BLOCK ? Material.DIRT : topMaterial;

        for (int dx = -FORCED_PLATFORM_RADIUS_BLOCKS; dx <= FORCED_PLATFORM_RADIUS_BLOCKS; dx++) {
            for (int dz = -FORCED_PLATFORM_RADIUS_BLOCKS; dz <= FORCED_PLATFORM_RADIUS_BLOCKS; dz++) {
                for (int depth = 1; depth <= FORCED_PLATFORM_FOUNDATION_DEPTH; depth++) {
                    world.getBlockAt(x + dx, platformY - depth, z + dz).setType(foundationMaterial, false);
                }
                world.getBlockAt(x + dx, platformY, z + dz).setType(topMaterial, false);
                for (int clearance = 1; clearance <= FORCED_PLATFORM_CLEARANCE_BLOCKS; clearance++) {
                    world.getBlockAt(x + dx, platformY + clearance, z + dz).setType(Material.AIR, false);
                }
            }
        }

        return new Location(world, x + 0.5D, platformY + 1.0D, z + 0.5D);
    }

    private Material fallbackPlatformTopMaterial(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> Material.GRASS_BLOCK;
            case THE_END -> Material.END_STONE;
            default -> Material.STONE;
        };
    }

    private boolean hasUnsafeFluid(Block block) {
        if (block == null) {
            return true;
        }
        if (block.isLiquid()) {
            return true;
        }
        return block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private boolean isUnsafeAquaticSpawnBlock(Material material) {
        return switch (material) {
            case SEAGRASS,
                TALL_SEAGRASS,
                KELP,
                KELP_PLANT,
                SEA_PICKLE,
                BUBBLE_COLUMN,
                TUBE_CORAL,
                BRAIN_CORAL,
                BUBBLE_CORAL,
                FIRE_CORAL,
                HORN_CORAL,
                DEAD_TUBE_CORAL,
                DEAD_BRAIN_CORAL,
                DEAD_BUBBLE_CORAL,
                DEAD_FIRE_CORAL,
                DEAD_HORN_CORAL,
                TUBE_CORAL_FAN,
                BRAIN_CORAL_FAN,
                BUBBLE_CORAL_FAN,
                FIRE_CORAL_FAN,
                HORN_CORAL_FAN,
                DEAD_TUBE_CORAL_FAN,
                DEAD_BRAIN_CORAL_FAN,
                DEAD_BUBBLE_CORAL_FAN,
                DEAD_FIRE_CORAL_FAN,
                DEAD_HORN_CORAL_FAN,
                TUBE_CORAL_WALL_FAN,
                BRAIN_CORAL_WALL_FAN,
                BUBBLE_CORAL_WALL_FAN,
                FIRE_CORAL_WALL_FAN,
                HORN_CORAL_WALL_FAN,
                DEAD_TUBE_CORAL_WALL_FAN,
                DEAD_BRAIN_CORAL_WALL_FAN,
                DEAD_BUBBLE_CORAL_WALL_FAN,
                DEAD_FIRE_CORAL_WALL_FAN,
                DEAD_HORN_CORAL_WALL_FAN -> true;
            default -> false;
        };
    }

    private boolean isHazardousMaterial(Material material) {
        return switch (material) {
            case LAVA, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, SWEET_BERRY_BUSH, WITHER_ROSE, FIRE, SOUL_FIRE, POWDER_SNOW, COBWEB ->
                true;
            default -> false;
        };
    }

    private double horizontalDistanceSquared(Location location, int x, int z) {
        double dx = location.getX() - (x + 0.5D);
        double dz = location.getZ() - (z + 0.5D);
        return (dx * dx) + (dz * dz);
    }

    private CompletableFuture<Void> preloadAnchorsAsync(World world, Collection<SpawnAnchor> anchors, int blockRadius) {
        List<Location> locations = new ArrayList<>();
        for (SpawnAnchor anchor : anchors) {
            locations.add(new Location(world, anchor.x() + 0.5D, world.getMinHeight() + 1, anchor.z() + 0.5D, anchor.yaw(), anchor.pitch()));
        }
        return preloadLocationsAsync(locations, blockRadius);
    }

    private CompletableFuture<Void> preloadLocationsAsync(Collection<Location> locations, int blockRadius) {
        if (locations == null || locations.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        Set<String> loadedRanges = new HashSet<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Location location : locations) {
            if (location == null || location.getWorld() == null) {
                continue;
            }
            World world = location.getWorld();
            int chunkRadius = Math.max(1, (blockRadius + 15) >> 4);
            int chunkX = Math.floorDiv(location.getBlockX(), 16);
            int chunkZ = Math.floorDiv(location.getBlockZ(), 16);
            int minChunkX = chunkX - chunkRadius;
            int maxChunkX = chunkX + chunkRadius;
            int minChunkZ = chunkZ - chunkRadius;
            int maxChunkZ = chunkZ + chunkRadius;
            String key = world.getUID() + ":" + minChunkX + ":" + minChunkZ + ":" + maxChunkX + ":" + maxChunkZ;
            if (!loadedRanges.add(key)) {
                continue;
            }

            CompletableFuture<Void> future = new CompletableFuture<>();
            futures.add(future);
            world.getChunksAtAsync(minChunkX, minChunkZ, maxChunkX, maxChunkZ, true, () -> future.complete(null));
        }

        return futures.isEmpty() ? CompletableFuture.completedFuture(null) : CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private void applyPresetHealth(Player player) {
        player.setMaxHealth(healthPreset.maxHealth());
        player.setHealth(Math.min(player.getMaxHealth(), healthPreset.maxHealth()));
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
    }

    private boolean isValidBedBlock(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return Tag.BEDS.isTagged(location.getBlock().getType());
    }

    private Location findSafeRespawnAroundBed(Location bedFoot) {
        List<Location> candidates = List.of(
            bedFoot.clone().add(0.5D, 1.0D, 0.5D),
            bedFoot.clone().add(1.5D, 1.0D, 0.5D),
            bedFoot.clone().add(-0.5D, 1.0D, 0.5D),
            bedFoot.clone().add(0.5D, 1.0D, 1.5D),
            bedFoot.clone().add(0.5D, 1.0D, -0.5D)
        );

        for (Location candidate : candidates) {
            if (isSafeForRespawn(candidate)) {
                return candidate;
            }
        }

        return resolveSafeRespawnBase(bedFoot);
    }

    private boolean isSafeForRespawn(Location feet) {
        return isSafeSpawnLocation(feet);
    }

    private Location resolveSafeRespawnBase(Location base) {
        if (base == null || base.getWorld() == null) {
            return null;
        }
        Location safe = safeSurface(base.getWorld(), base.getBlockX(), base.getBlockZ(), SAFE_SPAWN_SEARCH_RADIUS_BLOCKS);
        if (safe == null) {
            safe = forcedSafeSurface(base.getWorld(), base.getBlockX(), base.getBlockZ());
        }
        safe.setYaw(base.getYaw());
        safe.setPitch(base.getPitch());
        return safe;
    }

    private Location normalizeBlockCenter(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return location.clone();
        }
        return new Location(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private boolean sameBlock(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        return first.getWorld().getUID().equals(second.getWorld().getUID())
            && first.getBlockX() == second.getBlockX()
            && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }

    private String sortedActiveTeams(Set<Integer> teams) {
        List<Integer> list = new ArrayList<>(teams);
        Collections.sort(list);
        return list.toString();
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

    private boolean shouldLockNoRespawn(World world) {
        if (!noRespawnEnabled || world == null) {
            return false;
        }
        if (activeMatchWorldName != null && world.getName().equals(activeMatchWorldName + "_the_end")) {
            return true;
        }
        return noRespawnDimensions.contains(world.getKey().toString());
    }

    private String normalizeDimensionId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase();
        if (trimmed.isEmpty() || !trimmed.contains(":")) {
            return null;
        }
        String[] split = trimmed.split(":", 2);
        if (split.length != 2 || split[0].isBlank() || split[1].isBlank()) {
            return null;
        }
        return split[0] + ":" + split[1];
    }

    private void evaluateReadyCountdown() {
        if (engine.isRunning()) {
            cancelCountdown();
            return;
        }

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.size() < teamCount) {
            cancelCountdown();
            return;
        }

        for (Player player : online) {
            if (!readyPlayers.contains(player.getUniqueId())) {
                cancelCountdown();
                return;
            }
        }

        if (countdownTaskId != -1) {
            return;
        }

        final int[] remain = new int[] { readyCountdownSeconds };
        Bukkit.broadcastMessage(ChatColor.GOLD + text("ready.countdown_started"));
        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (engine.isRunning()) {
                cancelCountdown();
                return;
            }

            List<Player> current = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (current.size() < teamCount) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + text("ready.countdown_cancel_players"));
                cancelCountdown();
                return;
            }
            for (Player player : current) {
                if (!readyPlayers.contains(player.getUniqueId())) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + text("ready.countdown_cancel_unready"));
                    cancelCountdown();
                    return;
                }
            }

            if (remain[0] <= 0) {
                cancelCountdown();
                startMatch(Bukkit.getConsoleSender());
                return;
            }

            if (remain[0] <= 5 || remain[0] % 5 == 0) {
                Bukkit.broadcastMessage(ChatColor.GOLD + text("ready.countdown_tick", remain[0]));
            }
            remain[0]--;
        }, 20L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTaskId == -1) {
            return;
        }
        Bukkit.getScheduler().cancelTask(countdownTaskId);
        countdownTaskId = -1;
    }

    private World ensureLobbyWorld() {
        World world = ensureWorld(lobbyWorldName, World.Environment.NORMAL, true);
        if (world == null) {
            return null;
        }

        prepareLobbyPlatform(world);
        world.setSpawnLocation(0, 101, 0);
        world.setTime(6000L);
        applyManagedWorldRules(world, false);
        return world;
    }

    private void prepareLobbyPlatform(World world) {
        int y = 100;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                world.getBlockAt(x, y, z).setType(Material.GLASS);
                world.getBlockAt(x, y + 1, z).setType(Material.AIR);
                world.getBlockAt(x, y + 2, z).setType(Material.AIR);
            }
        }
        world.getBlockAt(0, y, 0).setType(Material.SEA_LANTERN);
    }

    private void teleportAllToLobby() {
        Location lobby = resolveLobbySpawn();
        if (lobby == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            teleportToLobby(player);
        }
    }

    private World createNewMatchWorld() {
        String worldName = matchWorldPrefix + "_" + System.currentTimeMillis();
        World world = ensureWorld(worldName, World.Environment.NORMAL, false);
        if (world != null) {
            applyManagedWorldRules(world, false);
            activeMatchWorldName = worldName;
        }
        return world;
    }

    private void cleanupActiveMatchWorld() {
        if (activeMatchWorldName == null || activeMatchWorldName.isBlank()) {
            return;
        }
        String worldName = activeMatchWorldName;
        activeMatchWorldName = null;
        if (!worldName.startsWith(matchWorldPrefix + "_")) {
            return;
        }

        List<String> worlds = List.of(worldName, worldName + "_nether", worldName + "_the_end");
        for (String candidate : worlds) {
            World world = Bukkit.getWorld(candidate);
            if (world == null) {
                continue;
            }
            for (Player player : new ArrayList<>(world.getPlayers())) {
                teleportToLobby(player);
            }
            if (!Bukkit.unloadWorld(world, false)) {
                getLogger().warning("Failed to unload match world: " + candidate);
            }
        }

        for (String candidate : worlds) {
            File folder = new File(Bukkit.getWorldContainer(), candidate);
            deleteRecursively(folder);
        }
    }

    private World ensureWorld(String worldName, World.Environment environment, boolean voidGenerator) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world;
        }

        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(environment);
        if (voidGenerator) {
            creator.generator(new TeamLifeBindVoidGenerator());
        }
        return creator.createWorld();
    }

    private Location portalLocation(String worldName, int x, int z) {
        World target = Bukkit.getWorld(worldName);
        if (target == null) {
            target = ensureWorld(worldName, inferEnvironment(worldName), false);
            applyManagedWorldRules(target, false);
        }
        if (target == null) {
            return null;
        }
        Location safe = safeSurface(target, x, z, SAFE_SPAWN_SEARCH_RADIUS_BLOCKS);
        return safe != null ? safe : forcedSafeSurface(target, x, z);
    }

    private World.Environment inferEnvironment(String worldName) {
        if (worldName != null) {
            if (worldName.endsWith("_nether")) {
                return World.Environment.NETHER;
            }
            if (worldName.endsWith("_the_end")) {
                return World.Environment.THE_END;
            }
        }
        return World.Environment.NORMAL;
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            getLogger().warning("Failed to delete: " + file.getAbsolutePath());
        }
    }

    private void loadLanguage() {
        this.language = TeamLifeBindLanguage.load(getDataFolder().toPath(), getLogger()::warning);
    }

    private boolean handleReadyStateChange(Player player, boolean ready) {
        if (language == null) {
            return false;
        }
        if (engine.isRunning()) {
            player.sendMessage(ChatColor.RED + text("ready.in_match"));
            return true;
        }

        if (ready) {
            if (!readyPlayers.add(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + text("ready.already"));
                return true;
            }
            Bukkit.broadcastMessage(ChatColor.AQUA + text("ready.marked", player.getName()));
        } else {
            if (!readyPlayers.remove(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + text("ready.not_marked"));
                return true;
            }
            Bukkit.broadcastMessage(ChatColor.YELLOW + text("ready.unmarked", player.getName()));
        }

        evaluateReadyCountdown();
        return true;
    }

    public void openLobbyMenu(Player player) {
        if (player == null) {
            return;
        }
        player.openInventory(createLobbyMenuInventory(player));
    }

    public boolean isLobbyMenuTitle(String title) {
        if (title == null) {
            return false;
        }
        return ChatColor.stripColor(title).equals(ChatColor.stripColor(ChatColor.DARK_AQUA + text("menu.inventory.title")));
    }

    public void handleLobbyMenuClick(Player player, ItemStack clickedItem, ClickType clickType) {
        String action = resolveMenuAction(clickedItem);
        if (player == null || action == null || clickType == null) {
            return;
        }
        boolean decrement;
        if (clickType == ClickType.LEFT) {
            decrement = false;
        } else if (clickType == ClickType.RIGHT) {
            decrement = true;
        } else {
            return;
        }

        switch (action) {
            case MENU_ACTION_READY -> markReady(player);
            case MENU_ACTION_UNREADY -> unready(player);
            case MENU_ACTION_STATUS -> player.sendMessage(statusText());
            case MENU_ACTION_HELP -> player.performCommand("tlb help");
            case MENU_ACTION_TEAMS -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text("command.no_permission"));
                } else if (hasActiveMatchSession()) {
                    player.sendMessage(ChatColor.RED + text("command.team_count.running"));
                } else {
                    int updatedCount = stepTeamCount(teamCount, decrement);
                    setTeamCount(updatedCount);
                    player.sendMessage(ChatColor.GREEN + text("command.team_count.updated", updatedCount));
                }
            }
            case MENU_ACTION_HEALTH -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text("command.no_permission"));
                } else if (hasActiveMatchSession()) {
                    player.sendMessage(ChatColor.RED + text("command.health.running"));
                } else {
                    HealthPreset next = stepHealthPreset(healthPreset, decrement);
                    setHealthPreset(next);
                    player.sendMessage(ChatColor.GREEN + text("command.health.updated", healthPresetLabel(next)));
                }
            }
            case MENU_ACTION_NORESPAWN -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text("command.no_permission"));
                } else {
                    boolean enabled = !noRespawnEnabled;
                    setNoRespawnEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text(enabled ? "command.norespawn.enabled" : "command.norespawn.disabled"));
                }
            }
            case MENU_ACTION_ANNOUNCE_TEAMS -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text("command.no_permission"));
                } else {
                    boolean enabled = !announceTeamAssignment;
                    setAnnounceTeamAssignment(enabled);
                    player.sendMessage(ChatColor.GREEN + text("config.announce_team_assignment.updated", stateLabel(enabled)));
                }
            }
            case MENU_ACTION_SCOREBOARD -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text("command.no_permission"));
                } else {
                    boolean enabled = !scoreboardEnabled;
                    setBuiltinScoreboardEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text("config.scoreboard.updated", stateLabel(enabled)));
                }
            }
            case MENU_ACTION_TAB -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text("command.no_permission"));
                } else {
                    boolean enabled = !tabEnabled;
                    setTabEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text("config.tab.updated", stateLabel(enabled)));
                }
            }
            case MENU_ACTION_ADVANCEMENTS -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text("command.no_permission"));
                } else {
                    boolean enabled = !advancementsEnabled;
                    setAdvancementsEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text("config.advancements.updated", stateLabel(enabled)));
                }
            }
            case MENU_ACTION_START -> {
                if (player.hasPermission("teamlifebind.admin")) {
                    startMatch(player);
                } else {
                    player.sendMessage(ChatColor.RED + text("command.no_permission"));
                }
            }
            case MENU_ACTION_STOP -> {
                if (player.hasPermission("teamlifebind.admin")) {
                    stopMatch(player);
                } else {
                    player.sendMessage(ChatColor.RED + text("command.no_permission"));
                }
            }
            case MENU_ACTION_RELOAD -> {
                if (player.hasPermission("teamlifebind.admin")) {
                    reloadPluginConfig(player);
                } else {
                    player.sendMessage(ChatColor.RED + text("command.no_permission"));
                }
            }
            default -> {
                return;
            }
        }

        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) {
                openLobbyMenu(player);
            }
        });
    }

    public boolean shouldCancelFriendlyFire(Player attacker, Player target) {
        if (attacker == null || target == null) {
            return false;
        }
        if (attacker.getUniqueId().equals(target.getUniqueId())) {
            return false;
        }
        if (isLobbyWorld(target.getWorld())) {
            return attacker.getGameMode() != GameMode.SPECTATOR && target.getGameMode() != GameMode.SPECTATOR;
        }
        if (!engine.isRunning()) {
            return false;
        }
        Integer attackerTeam = engine.teamForPlayer(attacker.getUniqueId());
        Integer targetTeam = engine.teamForPlayer(target.getUniqueId());
        return attackerTeam != null
            && attackerTeam.equals(targetTeam)
            && attacker.getGameMode() != GameMode.SPECTATOR
            && target.getGameMode() != GameMode.SPECTATOR;
    }

    public void notifyFriendlyFireBlocked(Player attacker) {
        if (attacker != null) {
            attacker.sendActionBar(
                ChatColor.RED + text(isInLobby(attacker) ? "lobby.pvp_denied" : "combat.friendly_fire_blocked")
            );
        }
    }

    private Inventory createLobbyMenuInventory(Player player) {
        Inventory inventory = Bukkit.createInventory(null, LOBBY_MENU_SIZE, ChatColor.DARK_AQUA + text("menu.inventory.title"));
        boolean ready = readyPlayers.contains(player.getUniqueId());
        String stateKey = engine.isRunning() ? "scoreboard.state.running" : "scoreboard.state.lobby";

        inventory.setItem(
            10,
            createMenuButton(
                ready ? MENU_ACTION_UNREADY : MENU_ACTION_READY,
                ready ? Material.RED_WOOL : Material.LIME_WOOL,
                ready ? text("menu.button.unready") : text("menu.button.ready"),
                List.of(ChatColor.GRAY + text("menu.info.status", text(stateKey)))
            )
        );
        inventory.setItem(
            13,
            createMenuButton(
                MENU_ACTION_STATUS,
                Material.CLOCK,
                text("menu.button.status"),
                List.of(
                    ChatColor.YELLOW + text("menu.info.status", text(stateKey)),
                    ChatColor.AQUA + text("menu.info.team", resolveMenuTeamLabel(player))
                )
            )
        );
        inventory.setItem(
            16,
            createMenuButton(
                MENU_ACTION_HELP,
                Material.BOOK,
                text("menu.button.help"),
                List.of(ChatColor.GRAY + text("menu.info.tip"))
            )
        );

        if (player.hasPermission("teamlifebind.admin")) {
            inventory.setItem(
                18,
                createMenuButton(
                    MENU_ACTION_TAB,
                    tabEnabled ? Material.COMPASS : Material.GRAY_DYE,
                    text("menu.button.tab"),
                    List.of(
                        ChatColor.YELLOW + text("menu.value.toggle", stateLabel(tabEnabled)),
                        ChatColor.GRAY + text("menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                19,
                createMenuButton(
                    MENU_ACTION_TEAMS,
                    Material.CYAN_WOOL,
                    text("menu.button.teams"),
                    List.of(
                        ChatColor.YELLOW + text("menu.value.team_count", teamCount),
                        ChatColor.GRAY + text("menu.action.adjust_number")
                    )
                )
            );
            inventory.setItem(
                20,
                createMenuButton(
                    MENU_ACTION_HEALTH,
                    Material.GOLDEN_APPLE,
                    text("menu.button.health"),
                    List.of(
                        ChatColor.YELLOW + text("menu.value.health", healthPresetLabel(healthPreset)),
                        ChatColor.GRAY + text("menu.action.adjust_cycle")
                    )
                )
            );
            inventory.setItem(
                21,
                createMenuButton(
                    MENU_ACTION_NORESPAWN,
                    noRespawnEnabled ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                    text("menu.button.norespawn"),
                    List.of(
                        ChatColor.YELLOW + text("menu.value.toggle", stateLabel(noRespawnEnabled)),
                        ChatColor.GRAY + text("menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                22,
                createMenuButton(
                    MENU_ACTION_ANNOUNCE_TEAMS,
                    announceTeamAssignment ? Material.NAME_TAG : Material.PAPER,
                    text("menu.button.announce_teams"),
                    List.of(
                        ChatColor.YELLOW + text("menu.value.toggle", stateLabel(announceTeamAssignment)),
                        ChatColor.GRAY + text("menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                23,
                createMenuButton(
                    MENU_ACTION_SCOREBOARD,
                    scoreboardEnabled ? Material.MAP : Material.GRAY_DYE,
                    text("menu.button.scoreboard"),
                    List.of(
                        ChatColor.YELLOW + text("menu.value.toggle", stateLabel(scoreboardEnabled)),
                        ChatColor.GRAY + text("menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                24,
                createMenuButton(
                    hasActiveMatchSession() ? MENU_ACTION_STOP : MENU_ACTION_START,
                    hasActiveMatchSession() ? Material.BARRIER : Material.DIAMOND_SWORD,
                    hasActiveMatchSession() ? text("menu.button.stop") : text("menu.button.start"),
                    List.of(ChatColor.GRAY + text("menu.info.status", text(stateKey)))
                )
            );
            inventory.setItem(
                25,
                createMenuButton(
                    MENU_ACTION_RELOAD,
                    Material.REPEATER,
                    text("menu.button.reload"),
                    List.of(ChatColor.GRAY + text("command.reload.success"))
                )
            );
            inventory.setItem(
                26,
                createMenuButton(
                    MENU_ACTION_ADVANCEMENTS,
                    advancementsEnabled ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                    text("menu.button.advancements"),
                    List.of(
                        ChatColor.YELLOW + text("menu.value.toggle", stateLabel(advancementsEnabled)),
                        ChatColor.GRAY + text("menu.action.toggle")
                    )
                )
            );
        }
        return inventory;
    }

    private ItemStack createMenuButton(String action, Material material, String label, List<String> lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.GOLD + label);
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore);
        }
        meta.getPersistentDataContainer().set(lobbyMenuActionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private String resolveMenuAction(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(lobbyMenuActionKey, PersistentDataType.STRING);
    }

    private String resolveMenuTeamLabel(Player player) {
        Integer team = player != null ? engine.teamForPlayer(player.getUniqueId()) : null;
        return team != null ? teamLabel(team) : text("menu.info.no_team");
    }

    private void broadcastImportantNotice(String title, String subtitle, int stayTicks) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(trimScoreboardText(title), trimScoreboardText(subtitle), 10, Math.max(20, stayTicks), 20);
        }
    }

    private void notifyTeamWiped(int team, Collection<UUID> victims) {
        if (victims == null || victims.isEmpty()) {
            return;
        }
        String title = text("match.notice.team_wipe.title");
        String subtitle = text("match.notice.team_wipe.subtitle", teamLabel(team));
        String message = ChatColor.RED + text("match.life_bind_wipe", teamLabel(team));
        for (UUID victimId : victims) {
            Player player = Bukkit.getPlayer(victimId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.sendTitle(trimScoreboardText(title), trimScoreboardText(subtitle), 10, Math.max(20, IMPORTANT_NOTICE_STAY_TICKS), 20);
            player.sendMessage(message);
        }
    }

    private List<String> buildSidebarLines(Player viewer) {
        TeamLifeBindMatchSnapshot matchSnapshot = getMatchSnapshot();
        TeamLifeBindPlayerSnapshot playerSnapshot = viewer != null ? getPlayerSnapshot(viewer.getUniqueId()) : getPlayerSnapshot(null);
        List<String> lines = new ArrayList<>();
        if (!matchSnapshot.running()) {
            lines.add(ChatColor.YELLOW + text("scoreboard.line.state", text("scoreboard.state.lobby")));
            lines.add(ChatColor.GREEN + text("scoreboard.line.ready", matchSnapshot.readyPlayers().size(), matchSnapshot.onlinePlayerCount()));
            lines.add(ChatColor.AQUA + text("scoreboard.line.teams", matchSnapshot.configuredTeamCount()));
            lines.add(ChatColor.RED + text("scoreboard.line.health", healthPresetLabel(matchSnapshot.healthPreset())));
            lines.add(ChatColor.LIGHT_PURPLE + text("scoreboard.line.norespawn", stateLabel(matchSnapshot.noRespawnEnabled())));
            return lines;
        }

        Integer playerTeam = playerSnapshot.team();
        boolean hasBed = playerTeam != null && matchSnapshot.bedTeams().contains(playerTeam);
        lines.add(ChatColor.YELLOW + text("scoreboard.line.state", text("scoreboard.state.running")));
        lines.add(ChatColor.AQUA + text("scoreboard.line.team", playerTeam != null ? teamLabel(playerTeam) : text("scoreboard.value.none")));
        lines.add(ChatColor.GREEN + text("scoreboard.line.team_bed", playerTeam != null ? bedStatusText(hasBed) : text("scoreboard.value.none")));
        lines.add(ChatColor.BLUE + text("scoreboard.line.teams", matchSnapshot.configuredTeamCount()));
        lines.add(ChatColor.RED + text("scoreboard.line.beds", matchSnapshot.bedTeams().size()));
        lines.add(ChatColor.DARK_AQUA + text("scoreboard.line.dimension", dimensionLabel(viewer)));
        lines.add(ChatColor.LIGHT_PURPLE + text("scoreboard.line.norespawn", stateLabel(matchSnapshot.noRespawnEnabled())));
        return lines;
    }

    private String bedStatusText(boolean placed) {
        return text(placed ? "scoreboard.bed.present" : "scoreboard.bed.missing");
    }

    private String dimensionLabel(Player viewer) {
        if (viewer == null || viewer.getWorld() == null) {
            return text("scoreboard.dimension.unknown");
        }
        return switch (viewer.getWorld().getEnvironment()) {
            case NETHER -> text("scoreboard.dimension.nether");
            case THE_END -> text("scoreboard.dimension.end");
            case NORMAL -> text("scoreboard.dimension.overworld");
            default -> text("scoreboard.dimension.unknown");
        };
    }

    private String trimScoreboardText(String raw) {
        if (raw == null || raw.isBlank()) {
            return " ";
        }
        return raw.length() <= 64 ? raw : raw.substring(0, 64);
    }

    private void startScoreboardTask() {
        cancelScoreboardTask();
        if (!scoreboardEnabled) {
            clearManagedScoreboards();
            return;
        }
        updateBuiltInScoreboards();
        scoreboardTaskId = Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(this, this::updateBuiltInScoreboards, scoreboardUpdateIntervalTicks, scoreboardUpdateIntervalTicks);
    }

    private void cancelScoreboardTask() {
        if (scoreboardTaskId == -1) {
            return;
        }
        Bukkit.getScheduler().cancelTask(scoreboardTaskId);
        scoreboardTaskId = -1;
    }

    private void startTabTask() {
        cancelTabTask();
        if (!tabEnabled) {
            clearTabListDisplays();
            clearTabNameColors();
            return;
        }
        updateTabListDisplays();
        tabTaskId = Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(this, this::updateTabListDisplays, TAB_UPDATE_INTERVAL_TICKS, TAB_UPDATE_INTERVAL_TICKS);
    }

    private void cancelTabTask() {
        if (tabTaskId == -1) {
            return;
        }
        Bukkit.getScheduler().cancelTask(tabTaskId);
        tabTaskId = -1;
    }

    private void updateTabListDisplays() {
        if (!tabEnabled) {
            clearTabListDisplays();
            clearTabNameColors();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyTabListDisplay(player);
        }
    }

    private void applyTabListDisplay(Player player) {
        if (player == null) {
            return;
        }
        player.setPlayerListHeaderFooter(buildTabListHeader(player), buildTabListFooter(player));
    }

    private String buildTabListHeader(Player viewer) {
        String state = text(engine.isRunning() ? "scoreboard.state.running" : "scoreboard.state.lobby");
        return ChatColor.GOLD + "" + ChatColor.BOLD + text("scoreboard.title")
            + "\n"
            + ChatColor.YELLOW + text("scoreboard.line.state", state);
    }

    private String buildTabListFooter(Player viewer) {
        if (!engine.isRunning()) {
            return ChatColor.GREEN + text("scoreboard.line.ready", readyPlayers.size(), Bukkit.getOnlinePlayers().size())
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.AQUA + text("scoreboard.line.teams", teamCount)
                + "\n"
                + ChatColor.RED + text("scoreboard.line.health", healthPresetLabel(healthPreset))
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.LIGHT_PURPLE + text("scoreboard.line.norespawn", stateLabel(noRespawnEnabled));
        }

        Integer playerTeam = engine.teamForPlayer(viewer.getUniqueId());
        boolean hasBed = playerTeam != null && teamBedSpawns.containsKey(playerTeam);
        return ChatColor.GREEN + text("scoreboard.line.team", playerTeam != null ? teamLabel(playerTeam) : text("scoreboard.value.none"))
            + ChatColor.DARK_GRAY + " | "
            + ChatColor.AQUA + text("scoreboard.line.team_bed", playerTeam != null ? bedStatusText(hasBed) : text("scoreboard.value.none"))
            + "\n"
            + ChatColor.DARK_AQUA + text("scoreboard.line.dimension", dimensionLabel(viewer))
            + ChatColor.DARK_GRAY + " | "
            + ChatColor.LIGHT_PURPLE + text("scoreboard.line.norespawn", stateLabel(noRespawnEnabled));
    }

    private void clearTabListDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListHeaderFooter("", "");
        }
    }

    private void updateBuiltInScoreboards() {
        if (!scoreboardEnabled) {
            clearManagedScoreboards();
            return;
        }
        for (UUID playerId : new ArrayList<>(managedScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                clearManagedScoreboard(playerId);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyBuiltInScoreboard(player);
        }
    }

    private void applyBuiltInScoreboard(Player player) {
        Scoreboard board = ensureManagedScoreboard(player.getUniqueId());
        if (board == null) {
            return;
        }

        Objective objective = board.getObjective(SCOREBOARD_OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(SCOREBOARD_OBJECTIVE_NAME, "dummy", trimScoreboardText(text("scoreboard.title")));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        TeamLifeBindSidebarSnapshot snapshot = getSidebarSnapshot(player.getUniqueId());
        objective.setDisplayName(trimScoreboardText(snapshot.title()));
        List<String> lines = snapshot.lines();
        int used = Math.min(lines.size(), SCOREBOARD_ENTRIES.length);
        for (int index = 0; index < used; index++) {
            String entry = SCOREBOARD_ENTRIES[index];
            Team lineTeam = board.getTeam(SCOREBOARD_TEAM_PREFIX + index);
            if (lineTeam == null) {
                lineTeam = board.registerNewTeam(SCOREBOARD_TEAM_PREFIX + index);
                lineTeam.addEntry(entry);
            }
            lineTeam.setPrefix(trimScoreboardText(lines.get(index)));
            objective.getScore(entry).setScore(used - index);
        }

        for (int index = used; index < SCOREBOARD_ENTRIES.length; index++) {
            String entry = SCOREBOARD_ENTRIES[index];
            board.resetScores(entry);
            Team lineTeam = board.getTeam(SCOREBOARD_TEAM_PREFIX + index);
            if (lineTeam != null) {
                lineTeam.setPrefix("");
                if (!lineTeam.getSuffix().isEmpty()) {
                    lineTeam.setSuffix("");
                }
            }
        }

        if (tabEnabled) {
            applyViewerNameColorTeams(player, board);
        } else {
            clearNameColorTeams(board);
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    private void applyViewerNameColorTeams(Player viewer, Scoreboard board) {
        if (viewer == null || board == null) {
            return;
        }

        clearNameColorTeams(board);
        Team neutralTeam = ensureNameColorTeam(board, NAME_COLOR_TEAM_NEUTRAL, ChatColor.WHITE, "");
        for (Player target : Bukkit.getOnlinePlayers()) {
            Team bucket = resolveNameColorTeam(viewer, board, target, neutralTeam);
            bucket.addEntry(target.getName());
        }
    }

    private Team ensureNameColorTeam(Scoreboard board, String teamName, ChatColor color, String prefix) {
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        team.setColor(color);
        team.setPrefix(prefix == null ? "" : prefix);
        return team;
    }

    private void clearTeamEntries(Team team) {
        if (team == null) {
            return;
        }
        for (String entry : new HashSet<>(team.getEntries())) {
            team.removeEntry(entry);
        }
    }

    private Team resolveNameColorTeam(Player viewer, Scoreboard board, Player target, Team neutralTeam) {
        if (!engine.isRunning()) {
            return neutralTeam;
        }
        Integer targetTeam = target == null ? null : engine.teamForPlayer(target.getUniqueId());
        if (targetTeam != null) {
            Integer viewerTeam = viewer != null ? engine.teamForPlayer(viewer.getUniqueId()) : null;
            ChatColor color = resolveTabTeamColor(viewerTeam, targetTeam);
            return ensureNameColorTeam(
                board,
                NAME_COLOR_TEAM_PREFIX + "team_" + targetTeam,
                color,
                buildTabTeamPrefix(targetTeam, color)
            );
        }
        return neutralTeam;
    }

    private void clearTabNameColors() {
        for (Scoreboard board : managedScoreboards.values()) {
            clearNameColorTeams(board);
        }
    }

    private void clearNameColorTeams(Scoreboard board) {
        if (board == null) {
            return;
        }
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (team.getName().startsWith(NAME_COLOR_TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }

    private ChatColor resolveTabTeamColor(Integer viewerTeam, int targetTeam) {
        if (viewerTeam != null) {
            return viewerTeam == targetTeam ? ChatColor.GREEN : ChatColor.RED;
        }
        return TAB_TEAM_COLORS[Math.floorMod(targetTeam - 1, TAB_TEAM_COLORS.length)];
    }

    private String buildTabTeamPrefix(int team, ChatColor color) {
        return ChatColor.DARK_GRAY + "[" + color + "T" + team + ChatColor.DARK_GRAY + "] " + color;
    }

    private Scoreboard ensureManagedScoreboard(UUID playerId) {
        Scoreboard existing = managedScoreboards.get(playerId);
        if (existing != null) {
            return existing;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return null;
        }
        Scoreboard created = manager.getNewScoreboard();
        managedScoreboards.put(playerId, created);
        return created;
    }

    private void clearManagedScoreboards() {
        for (UUID playerId : new ArrayList<>(managedScoreboards.keySet())) {
            clearManagedScoreboard(playerId);
        }
    }

    private void clearManagedScoreboard(UUID playerId) {
        Scoreboard managed = managedScoreboards.remove(playerId);
        if (managed == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (player != null && player.isOnline() && manager != null && player.getScoreboard() == managed) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    private void startLocatorTask() {
        cancelLocatorTask();
        locatorTaskId = Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(this, this::updateTrackedLocatorTargets, LOCATOR_UPDATE_INTERVAL_TICKS, LOCATOR_UPDATE_INTERVAL_TICKS);
    }

    private void cancelLocatorTask() {
        if (locatorTaskId == -1) {
            return;
        }
        Bukkit.getScheduler().cancelTask(locatorTaskId);
        locatorTaskId = -1;
    }

    private void updateTrackedLocatorTargets() {
        if (locatorBarSupport == null || !locatorBarSupport.isAvailable()) {
            trackedLocatorTargets.clear();
            trackedLocatorAngles.clear();
            return;
        }
        if (!engine.isRunning()) {
            clearTrackedLocatorTargets();
            return;
        }

        Map<UUID, Player> onlinePlayers = new LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayers.put(player.getUniqueId(), player);
        }

        trackedLocatorTargets.keySet().removeIf(playerId -> !onlinePlayers.containsKey(playerId));
        trackedLocatorAngles.keySet().removeIf(playerId -> !onlinePlayers.containsKey(playerId));
        for (Player viewer : onlinePlayers.values()) {
            syncTrackedLocatorTargets(viewer, onlinePlayers);
        }
    }

    private void syncTrackedLocatorTargets(Player viewer, Map<UUID, Player> onlinePlayers) {
        Integer viewerTeam = engine.teamForPlayer(viewer.getUniqueId());
        Set<UUID> desiredTargets = new LinkedHashSet<>();
        if (viewerTeam != null && viewer.getWorld() != null) {
            for (Player target : onlinePlayers.values()) {
                if (target.getUniqueId().equals(viewer.getUniqueId())) {
                    continue;
                }
                if (target.getWorld() == null || target.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (!viewer.getWorld().getUID().equals(target.getWorld().getUID())) {
                    continue;
                }
                if (!viewerTeam.equals(engine.teamForPlayer(target.getUniqueId()))) {
                    continue;
                }
                desiredTargets.add(target.getUniqueId());
            }
        }

        Set<UUID> tracked = trackedLocatorTargets.computeIfAbsent(viewer.getUniqueId(), ignored -> new LinkedHashSet<>());
        Map<UUID, Float> cachedAngles = trackedLocatorAngles.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashMap<>());
        for (UUID trackedTarget : new ArrayList<>(tracked)) {
            if (!desiredTargets.contains(trackedTarget)) {
                locatorBarSupport.remove(viewer, trackedTarget);
                tracked.remove(trackedTarget);
                cachedAngles.remove(trackedTarget);
            }
        }

        for (UUID targetId : desiredTargets) {
            Player target = onlinePlayers.get(targetId);
            if (target == null) {
                continue;
            }
            float azimuth = locatorBarSupport.azimuth(viewer, target);
            boolean add = tracked.add(targetId);
            Float previousAzimuth = cachedAngles.get(targetId);
            if (add || previousAzimuth == null || angleDelta(previousAzimuth, azimuth) >= LOCATOR_MIN_AZIMUTH_DELTA) {
                locatorBarSupport.sendAzimuth(viewer, target, azimuth, add);
                cachedAngles.put(targetId, azimuth);
            }
        }

        if (tracked.isEmpty()) {
            trackedLocatorTargets.remove(viewer.getUniqueId());
            trackedLocatorAngles.remove(viewer.getUniqueId());
        }
    }

    private void clearTrackedLocatorTargets() {
        if (locatorBarSupport == null || !locatorBarSupport.isAvailable()) {
            trackedLocatorTargets.clear();
            trackedLocatorAngles.clear();
            return;
        }
        for (Map.Entry<UUID, Set<UUID>> entry : trackedLocatorTargets.entrySet()) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            for (UUID targetId : entry.getValue()) {
                locatorBarSupport.remove(viewer, targetId);
            }
        }
        trackedLocatorTargets.clear();
        trackedLocatorAngles.clear();
    }

    private float angleDelta(float first, float second) {
        float diff = Math.abs(first - second);
        float turn = (float) (Math.PI * 2.0D);
        return Math.min(diff, turn - diff);
    }

    private void applyManagedWorldRules() {
        applyManagedWorldRules(Bukkit.getWorld(lobbyWorldName), false);
        if (activeMatchWorldName == null || activeMatchWorldName.isBlank()) {
            return;
        }
        for (String worldName : List.of(activeMatchWorldName, activeMatchWorldName + "_nether", activeMatchWorldName + "_the_end")) {
            applyManagedWorldRules(Bukkit.getWorld(worldName), false);
        }
    }

    private void applyManagedWorldRules(World world, boolean locatorEnabled) {
        setBooleanGameRule(world, RULE_LOCATOR_BAR, locatorEnabled);
        setBooleanGameRule(world, RULE_ANNOUNCE_ADVANCEMENTS, advancementsEnabled);
        setBooleanGameRule(world, RULE_DO_IMMEDIATE_RESPAWN, true);
    }

    private void setBooleanGameRule(World world, String ruleName, boolean enabled) {
        GameRule<Boolean> rule = resolveBooleanGameRule(ruleName);
        if (world != null && rule != null) {
            world.setGameRule(rule, enabled);
        }
    }

    private GameRule<Boolean> resolveBooleanGameRule(String ruleName) {
        String fieldName = switch (ruleName) {
            case RULE_LOCATOR_BAR -> "LOCATOR_BAR";
            case RULE_ANNOUNCE_ADVANCEMENTS -> "ANNOUNCE_ADVANCEMENTS";
            case RULE_DO_IMMEDIATE_RESPAWN -> "DO_IMMEDIATE_RESPAWN";
            default -> null;
        };
        if (fieldName == null) {
            return null;
        }

        try {
            Object value = GameRule.class.getField(fieldName).get(null);
            if (value instanceof GameRule<?> rule) {
                @SuppressWarnings("unchecked")
                GameRule<Boolean> booleanRule = (GameRule<Boolean>) rule;
                return booleanRule;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private void announceTeams(Map<UUID, Integer> assignments) {
        Map<Integer, List<String>> byTeam = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : assignments.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            byTeam.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>()).add(player.getName());
        }

        List<Integer> teams = new ArrayList<>(byTeam.keySet());
        Collections.sort(teams);
        for (int team : teams) {
            String message = ChatColor.AQUA + text("match.team_announcement", teamLabel(team), String.join(", ", byTeam.get(team)));
            for (Map.Entry<UUID, Integer> entry : assignments.entrySet()) {
                if (!entry.getValue().equals(team)) {
                    continue;
                }
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    player.sendMessage(message);
                }
            }
        }
    }

    enum TeamBedPlacementResult {
        ACCEPTED,
        DENIED,
        EXPLODE
    }

    private record SpawnAnchor(int x, int z, float yaw, float pitch) {}
}
