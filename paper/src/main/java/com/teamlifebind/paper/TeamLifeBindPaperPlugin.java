package com.teamlifebind.paper;

import com.teamlifebind.common.GameOptions;
import com.teamlifebind.common.HealthPreset;
import com.teamlifebind.common.StartResult;
import com.teamlifebind.common.TeamLifeBindCombatMath;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.EntityEffect;
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
import org.bukkit.scoreboard.Criteria;
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
    private static final int MATCH_OBSERVATION_SECONDS = 10;
    private static final int RESPAWN_COUNTDOWN_SECONDS = 5;
    private static final int MATCH_OBSERVATION_TICKS = MATCH_OBSERVATION_SECONDS * 20;
    private static final int RESPAWN_COUNTDOWN_TICKS = RESPAWN_COUNTDOWN_SECONDS * 20;
    private static final long RESPAWN_INVULNERABILITY_TICKS = 100L;
    private static final int LOBBY_MENU_SLOT = 6;
    private static final int LOBBY_GUIDE_SLOT = 7;
    private static final int LOBBY_RECIPES_SLOT = 8;
    private static final int LOBBY_MENU_SIZE = 27;
    private static final long SUPPLY_BOAT_DROP_CONFIRM_WINDOW_MS = 3_000L;
    private static final long LOCATOR_UPDATE_INTERVAL_TICKS = 10L;
    private static final long TAB_UPDATE_INTERVAL_TICKS = 20L;
    private static final double HEALTH_SYNC_EPSILON = 0.01D;
    private static final int TEAM_REGEN_IDLE_TICKS = 60;
    private static final int TEAM_REGEN_INTERVAL_TICKS = 40;
    private static final double TEAM_REGEN_AMOUNT = 1.0D;
    private static final int EFFECT_DURATION_SYNC_TOLERANCE_TICKS = 10;
    private static final int TOTEM_REGENERATION_TICKS = 900;
    private static final int TOTEM_FIRE_RESISTANCE_TICKS = 800;
    private static final int TOTEM_ABSORPTION_TICKS = 100;
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
    private static final LegacyComponentSerializer LEGACY_SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();

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
    private final Map<UUID, String> scoreboardTitles = new HashMap<>();
    private final Map<UUID, List<String>> scoreboardLines = new HashMap<>();
    private final Map<UUID, Long> supplyBoatDropConfirmUntil = new HashMap<>();
    private final Map<UUID, PendingRespawn> pendingRespawns = new HashMap<>();
    private final Map<UUID, PendingMatchObservation> pendingMatchObservations = new HashMap<>();
    private final Map<UUID, Long> respawnInvulnerabilityTokens = new HashMap<>();
    private final Map<Integer, Double> teamSharedHealth = new HashMap<>();
    private final Map<Integer, Set<UUID>> observedTeamHealthPlayers = new HashMap<>();
    private final Map<Integer, Integer> teamSharedExperience = new HashMap<>();
    private final Map<Integer, Set<UUID>> observedTeamExperiencePlayers = new HashMap<>();
    private final Map<Integer, Integer> teamSharedFoodLevels = new HashMap<>();
    private final Map<Integer, Double> teamSharedSaturation = new HashMap<>();
    private final Map<Integer, Set<UUID>> observedTeamFoodPlayers = new HashMap<>();
    private final Map<Integer, Long> teamLastDamageTicks = new HashMap<>();
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
    private int readyCountdownRemainingSeconds = -1;
    private int countdownTaskId = -1;
    private int locatorTaskId = -1;
    private int scoreboardTaskId = -1;
    private int tabTaskId = -1;
    private int respawnTaskId = -1;
    private NamespacedKey teamBedItemKey;
    private NamespacedKey teamBedOwnerTeamKey;
    private NamespacedKey supplyBoatItemKey;
    private NamespacedKey lobbyMenuItemKey;
    private NamespacedKey lobbyMenuActionKey;
    private TeamLifeBindLanguage language;
    private PaperLocatorBarSupport locatorBarSupport;
    private long sharedStateTick = 0L;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettingsFromConfig();
        cleanupStaleMatchWorldsOnStartup();
        loadLanguage();
        initializeItemKeys();
        locatorBarSupport = new PaperLocatorBarSupport(getLogger());
        registerTeamBedRecipe();
        ensureLobbyWorld();

        TeamLifeBindCommand command = new TeamLifeBindCommand(this);
        PluginCommand tlbCommand = getCommand("tlb");
        if (tlbCommand != null) {
            tlbCommand.setExecutor(command);
            tlbCommand.setTabCompleter(command);
        } else {
            getLogger().warning("Command 'tlb' is missing from plugin.yml");
        }

        Bukkit.getPluginManager().registerEvents(new TeamLifeBindListener(this), this);
        Bukkit.getServicesManager().register(TeamLifeBindPaperApi.class, this, this, ServicePriority.Normal);
        startLocatorTask();
        startScoreboardTask();
        startTabTask();
        startRespawnTask();
        teleportAllToLobby();
    }

    @Override
    public void onDisable() {
        cancelCountdown();
        clearTrackedLocatorTargets();
        cancelLocatorTask();
        cancelScoreboardTask();
        cancelTabTask();
        cancelRespawnTask();
        pendingRespawns.clear();
        pendingMatchObservations.clear();
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

    public boolean isUnassignedMatchPlayer(UUID playerId) {
        return playerId == null || engine.teamForPlayer(playerId) == null;
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
            playerId != null ? pendingRespawnSeconds(playerId) : -1,
            player != null ? player.getWorld().getName() : null
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
        forgetSharedTeamHealthPlayer(playerId);
        forgetSharedTeamExperiencePlayer(playerId);
        forgetSharedTeamFoodPlayer(playerId);
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
            movePlayerToSpectator(player, resolveCurrentSpectatorLocation(player.getUniqueId()), false);
            return;
        }

        int respawnSecondsRemaining = pendingRespawnSeconds(player.getUniqueId());
        if (respawnSecondsRemaining > 0) {
            movePlayerToSpectator(player, resolveCurrentSpectatorLocation(player.getUniqueId()), false);
            sendActionBar(player, ChatColor.YELLOW, text("respawn.wait", respawnSecondsRemaining));
            return;
        }

        int observationSecondsRemaining = pendingMatchObservationSeconds(player.getUniqueId());
        if (observationSecondsRemaining > 0) {
            player.setGameMode(GameMode.SURVIVAL);
            PendingMatchObservation pending = pendingMatchObservations.get(player.getUniqueId());
            if (pending != null) {
                Location anchor = pending.anchor();
                if (anchor != null && (isOutsideActiveMatchWorld(player.getWorld()) || !samePosition(player.getLocation(), anchor))) {
                    player.teleport(anchor, PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            }
            sendActionBar(player, ChatColor.AQUA, text("match.observe.wait", observationSecondsRemaining));
            return;
        }

        player.setGameMode(GameMode.SURVIVAL);
        if (isOutsideActiveMatchWorld(player.getWorld())) {
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
        return world != null && lobbyWorldName.equals(world.getName());
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

    public void setTabEnabled(boolean enabled) {
        this.tabEnabled = enabled;
        getConfig().set("tab.enabled", this.tabEnabled);
        saveConfig();
        startTabTask();
        updateBuiltInScoreboards();
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
            movePlayerToSpectator(player, resolveCurrentSpectatorLocation(player.getUniqueId()), false);
            player.sendMessage(ChatColor.RED + text("player.eliminated.no_respawn"));
        });
    }

    public void beginRespawnCountdown(Player player) {
        if (player == null || !player.isOnline() || !engine.isRunning()) {
            return;
        }
        if (isRespawnLocked(player.getUniqueId())) {
            handleBlockedRespawn(player);
            return;
        }

        Integer team = engine.teamForPlayer(player.getUniqueId());
        if (team == null) {
            moveUnassignedPlayerToSpectator(player, true);
            return;
        }

        pendingMatchObservations.remove(player.getUniqueId());
        pendingRespawns.put(player.getUniqueId(), new PendingRespawn(team, RESPAWN_COUNTDOWN_TICKS, RESPAWN_COUNTDOWN_SECONDS));
        movePlayerToSpectator(player, resolveCurrentSpectatorLocation(player.getUniqueId()), false);
        sendActionBar(player, ChatColor.YELLOW, text("respawn.wait", RESPAWN_COUNTDOWN_SECONDS));
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

    TeamBedPlacementResult registerPlacedTeamBed(Player player, ItemStack stack, Location bedFoot) {
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

        if (hasActiveTeamBed(ownerTeam)) {
            player.sendMessage(ChatColor.RED + text("bed.limit_reached"));
            return TeamBedPlacementResult.DENIED;
        }

        Location normalized = normalizeBlockCenter(bedFoot);
        placedTeamBedOwners.put(bedLocationKey(normalized), ownerTeam);
        teamBedSpawns.put(ownerTeam, normalized);
        player.sendMessage(ChatColor.GREEN + text("bed.updated", teamLabel(ownerTeam)));
        return TeamBedPlacementResult.ACCEPTED;
    }

    private boolean hasActiveTeamBed(int team) {
        Location existing = teamBedSpawns.get(team);
        if (existing == null) {
            return false;
        }
        if (isValidBedBlock(existing)) {
            return true;
        }
        teamBedSpawns.remove(team);
        placedTeamBedOwners.remove(bedLocationKey(normalizeBlockCenter(existing)));
        return false;
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
            broadcastColoredMessage(ChatColor.YELLOW, text("bed.destroyed", teamLabel(ownerTeam)));
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

    private int pendingRespawnSeconds(UUID playerId) {
        PendingRespawn pending = playerId == null ? null : pendingRespawns.get(playerId);
        if (pending == null) {
            return -1;
        }
        return Math.max(1, (pending.remainingTicks() + 19) / 20);
    }

    private int pendingMatchObservationSeconds(UUID playerId) {
        PendingMatchObservation pending = playerId == null ? null : pendingMatchObservations.get(playerId);
        if (pending == null) {
            return -1;
        }
        return Math.max(1, (pending.remainingTicks() + 19) / 20);
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
                            beginMatchObservation(player, entry.getValue());
                        }

                        broadcastImportantNotice(text("match.notice.start.title"), text("match.notice.start.subtitle"));

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
        pendingRespawns.clear();
        pendingMatchObservations.clear();
        clearSharedTeamState();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        supplyBoatDropConfirmUntil.clear();
        teleportAllToLobby();
        cleanupActiveMatchWorld();
        broadcastColoredMessage(ChatColor.GOLD, text("match.stopped"));
    }

    public void handlePlayerDeath(Player deadPlayer) {
        pendingMatchObservations.remove(deadPlayer.getUniqueId());
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
        broadcastImportantNotice(text("match.win", teamLabel(team), reason), "");
        broadcastColoredMessage(ChatColor.GREEN, text("match.win", teamLabel(team), reason));
        engine.stop();
        clearTrackedLocatorTargets();
        readyPlayers.clear();
        matchTeamSpawns.clear();
        teamBedSpawns.clear();
        placedTeamBedOwners.clear();
        pendingRespawns.clear();
        pendingMatchObservations.clear();
        clearSharedTeamState();
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
        if (this.lobbyWorldName.isBlank()) {
            this.lobbyWorldName = "tlb_lobby";
        }
        this.matchWorldPrefix = getConfig().getString("match-world-prefix", "tlb_match");
        if (this.matchWorldPrefix.isBlank()) {
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
        meta.displayName(legacyComponent(displayName));
        meta.lore(legacyComponents(List.of(ChatColor.GRAY + text("item.team_bed.lore"))));
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
        setPlayerMaxHealth(player, 20.0D);
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

    public Location resolveCurrentSpectatorLocation(UUID playerId) {
        if (playerId != null) {
            Location respawn = resolveRespawnLocation(playerId);
            if (respawn != null && respawn.getWorld() != null) {
                return respawn.clone().add(0.0D, 8.0D, 0.0D);
            }
        }
        return resolveCurrentSpectatorLocation();
    }

    private ItemStack createLobbyMenuItem() {
        ItemStack item = new ItemStack(Material.COMPASS, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(legacyComponent(ChatColor.AQUA + text("item.menu.name")));
        meta.lore(legacyComponents(List.of(ChatColor.GRAY + text("item.menu.lore"))));
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
        meta.displayName(legacyComponent(ChatColor.GOLD + displayName));
        meta.title(Component.text(title));
        meta.author(Component.text(author));
        meta.addPages(pages.stream().map(Component::text).toArray(Component[]::new));
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
            sendActionBar(player, ChatColor.YELLOW, text("boat.drop_confirm"));
        }
    }

    public void notifySupplyBoatDiscarded(Player player) {
        if (player != null) {
            sendActionBar(player, ChatColor.GRAY, text("boat.discarded"));
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

    private boolean isOutsideActiveMatchWorld(World world) {
        if (world == null || activeMatchWorldName == null || activeMatchWorldName.isBlank()) {
            return true;
        }
        String worldName = world.getName();
        return !worldName.equals(activeMatchWorldName)
            && !worldName.equals(activeMatchWorldName + "_nether")
            && !worldName.equals(activeMatchWorldName + "_the_end");
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
        if (currentToken == null || currentToken != token) {
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

        preloadAnchorsAsync(world, plannedAnchors.values()).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }

            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    Map<Integer, Location> resolved = new LinkedHashMap<>();
                    for (Map.Entry<Integer, SpawnAnchor> entry : plannedAnchors.entrySet()) {
                        SpawnAnchor anchor = entry.getValue();
                        Location safe = resolveSafeSurfaceOrFallback(world, anchor.x(), anchor.z());
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
        Location safe = resolveSafeSurfaceOrFallback(world, x, z);
        safe.setYaw(base.getYaw());
        safe.setPitch(base.getPitch());
        return safe;
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
            .filter(Objects::nonNull)
            .min(Comparator.comparingDouble(location -> horizontalDistanceSquared(location, x, z)))
            .orElse(null);
    }

    private Location forcedSafeSurface(World world, int x, int z) {
        return Objects.requireNonNullElseGet(safeSurface(world, x, z, MATCH_PRELOAD_SEARCH_RADIUS_BLOCKS), () -> fallbackSafeSurface(world, x, z));
    }

    private Location fallbackSafeSurface(World world, int x, int z) {
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

    private CompletableFuture<Void> preloadAnchorsAsync(World world, Collection<SpawnAnchor> anchors) {
        List<Location> locations = new ArrayList<>();
        for (SpawnAnchor anchor : anchors) {
            locations.add(new Location(world, anchor.x() + 0.5D, world.getMinHeight() + 1, anchor.z() + 0.5D, anchor.yaw(), anchor.pitch()));
        }
        return preloadLocationsAsync(locations, MATCH_PRELOAD_SEARCH_RADIUS_BLOCKS);
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
        double targetMaxHealth = resolvePlayerSharedMaxHealth(player);
        setPlayerMaxHealth(player, targetMaxHealth);
        player.setHealth(Math.min(maxHealth(player), targetMaxHealth));
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
    }

    private double resolvePlayerSharedMaxHealth(Player player) {
        if (player == null) {
            return healthPreset.maxHealth();
        }
        Integer team = engine.teamForPlayer(player.getUniqueId());
        if (team == null) {
            return healthPreset.maxHealth();
        }
        int totalExperience = teamSharedExperience.getOrDefault(team, player.getTotalExperience());
        int sharedLevel = TeamLifeBindCombatMath.resolveExperienceState(totalExperience).level();
        return TeamLifeBindCombatMath.sharedMaxHealth(healthPreset, sharedLevel);
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
        Location safe = resolveSafeSurfaceOrFallback(base.getWorld(), base.getBlockX(), base.getBlockZ());
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
        if (!trimmed.contains(":")) {
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
        readyCountdownRemainingSeconds = remain[0];
        broadcastColoredMessage(ChatColor.GOLD, text("ready.countdown_started"));
        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (engine.isRunning()) {
                cancelCountdown();
                return;
            }

            List<Player> current = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (current.size() < teamCount) {
                broadcastColoredMessage(ChatColor.YELLOW, text("ready.countdown_cancel_players"));
                cancelCountdown();
                return;
            }
            for (Player player : current) {
                if (!readyPlayers.contains(player.getUniqueId())) {
                    broadcastColoredMessage(ChatColor.YELLOW, text("ready.countdown_cancel_unready"));
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
                broadcastColoredMessage(ChatColor.GOLD, text("ready.countdown_tick", remain[0]));
            }
            if (remain[0] <= 5) {
                playReadyCountdownSound(remain[0]);
            }
            remain[0]--;
            readyCountdownRemainingSeconds = remain[0];
        }, 20L, 20L);
    }

    private void cancelCountdown() {
        readyCountdownRemainingSeconds = -1;
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
        applyManagedWorldRules(world);
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
            applyManagedWorldRules(world);
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
            applyManagedWorldRules(target);
        }
        if (target == null) {
            return null;
        }
        return resolveSafeSurfaceOrFallback(target, x, z);
    }

    private World.Environment inferEnvironment(String worldName) {
        if (worldName.endsWith("_nether")) {
            return World.Environment.NETHER;
        }
        if (worldName.endsWith("_the_end")) {
            return World.Environment.THE_END;
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

    private void cleanupStaleMatchWorldsOnStartup() {
        activeMatchWorldName = null;
        File worldContainer = Bukkit.getWorldContainer();
        File[] folders = worldContainer.listFiles(File::isDirectory);
        if (folders == null) {
            return;
        }

        for (String worldName : collectStaleMatchWorldNames(folders)) {
            cleanupMatchWorldByName(worldName);
        }
    }

    private Set<String> collectStaleMatchWorldNames(File[] folders) {
        String prefix = matchWorldPrefix + "_";
        Set<String> staleWorldNames = new LinkedHashSet<>();
        for (File folder : folders) {
            String name = folder.getName();
            if (!name.isBlank() && name.startsWith(prefix)) {
                staleWorldNames.add(name);
            }
        }
        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            if (worldName.startsWith(prefix)) {
                staleWorldNames.add(worldName);
            }
        }
        return staleWorldNames;
    }

    private void cleanupMatchWorldByName(String worldName) {
        if (worldName.isBlank() || !worldName.startsWith(matchWorldPrefix + "_")) {
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            for (Player player : new ArrayList<>(world.getPlayers())) {
                teleportToLobby(player);
            }
            if (!Bukkit.unloadWorld(world, false)) {
                getLogger().warning("Failed to unload stale match world: " + worldName);
            }
        }

        File folder = new File(Bukkit.getWorldContainer(), worldName);
        deleteRecursively(folder);
    }

    private void loadLanguage() {
        this.language = TeamLifeBindLanguage.load(getDataFolder().toPath(), getLogger()::warning);
    }

    private void handleReadyStateChange(Player player, boolean ready) {
        if (language == null) {
            return;
        }
        if (engine.isRunning()) {
            player.sendMessage(ChatColor.RED + text("ready.in_match"));
            return;
        }

        if (ready) {
            if (!readyPlayers.add(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + text("ready.already"));
                return;
            }
            broadcastColoredMessage(ChatColor.AQUA, text("ready.marked", player.getName()));
        } else {
            if (!readyPlayers.remove(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + text("ready.not_marked"));
                return;
            }
            broadcastColoredMessage(ChatColor.YELLOW, text("ready.unmarked", player.getName()));
        }

        evaluateReadyCountdown();
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

    public boolean isLobbyMenuTitle(Component title) {
        if (title == null) {
            return false;
        }
        return isLobbyMenuTitle(LEGACY_SECTION_SERIALIZER.serialize(title));
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
            sendActionBar(attacker, ChatColor.RED, text(isInLobby(attacker) ? "lobby.pvp_denied" : "combat.friendly_fire_blocked"));
        }
    }

    public boolean tryUseTeamTotem(Player target, double finalDamage) {
        if (target == null || !target.isOnline() || finalDamage <= 0.0D || !engine.isRunning() || shouldExcludeFromSharedHealth(target)) {
            return false;
        }

        Integer team = engine.teamForPlayer(target.getUniqueId());
        if (team == null) {
            return false;
        }
        if ((target.getHealth() + target.getAbsorptionAmount()) - finalDamage > HEALTH_SYNC_EPSILON) {
            return false;
        }

        List<Player> players = resolveTotemEligibleTeamPlayers(team);
        if (players.isEmpty()) {
            return false;
        }

        Player totemOwner = null;
        int totemSlot = -1;
        for (Player player : players) {
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
        for (Player player : players) {
            applyTeamTotemRescue(player);
        }
        return true;
    }

    public void handleMilkBucketConsumed(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Integer team = engine.teamForPlayer(player.getUniqueId());
        if (team == null) {
            clearPotionEffects(player);
            return;
        }
        for (Player teammate : resolveTotemEligibleTeamPlayers(team)) {
            clearPotionEffects(teammate);
        }
    }

    private List<Player> resolveTotemEligibleTeamPlayers(int team) {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldExcludeFromSharedHealth(player)) {
                continue;
            }
            Integer playerTeam = engine.teamForPlayer(player.getUniqueId());
            if (playerTeam != null && playerTeam == team) {
                players.add(player);
            }
        }
        return players;
    }

    private void clearPotionEffects(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
    }

    private int findTotemSlot(Player player) {
        if (player == null || !player.isOnline()) {
            return -1;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack != null && stack.getType() == Material.TOTEM_OF_UNDYING && stack.getAmount() > 0) {
                return slot;
            }
        }
        return -1;
    }

    private void consumeInventoryItem(Player player, int slot) {
        if (player == null || slot < 0) {
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        if (stack.getAmount() <= 1) {
            player.getInventory().setItem(slot, null);
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
        player.getInventory().setItem(slot, stack);
    }

    private void applyTeamTotemRescue(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setHealth(Math.min(maxHealth(player), 1.0D));
        player.setAbsorptionAmount(0.0D);
        player.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, TOTEM_REGENERATION_TICKS, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, TOTEM_FIRE_RESISTANCE_TICKS, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, TOTEM_ABSORPTION_TICKS, 1, false, true, true));
        player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 40));
        player.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
        playSound(player, Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
    }

    private Inventory createLobbyMenuInventory(Player player) {
        Inventory inventory = Bukkit.createInventory(null, LOBBY_MENU_SIZE, legacyComponent(ChatColor.DARK_AQUA + text("menu.inventory.title")));
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
        meta.displayName(legacyComponent(ChatColor.GOLD + label));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(legacyComponents(lore));
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

    private void broadcastImportantNotice(String title, String subtitle) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(legacyComponent(trimScoreboardText(title)), legacyComponent(trimScoreboardText(subtitle)), 10, IMPORTANT_NOTICE_STAY_TICKS, 20));
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
            player.showTitle(Title.title(legacyComponent(trimScoreboardText(title)), legacyComponent(trimScoreboardText(subtitle)), 10, IMPORTANT_NOTICE_STAY_TICKS, 20));
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
            if (readyCountdownRemainingSeconds >= 0) {
                lines.add(ChatColor.GOLD + text("scoreboard.line.countdown", readyCountdownRemainingSeconds));
            }
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
        lines.add(ChatColor.GOLD + text("scoreboard.line.respawn", respawnStatusText(playerSnapshot)));
        int observationSecondsRemaining = viewer != null ? pendingMatchObservationSeconds(viewer.getUniqueId()) : -1;
        if (observationSecondsRemaining > 0) {
            lines.add(ChatColor.YELLOW + text("scoreboard.line.observe", text("scoreboard.observe.countdown", observationSecondsRemaining)));
        }
        lines.add(ChatColor.RED + text("scoreboard.line.health", healthPresetLabel(matchSnapshot.healthPreset())));
        lines.add(ChatColor.BLUE + text("scoreboard.line.teams", matchSnapshot.configuredTeamCount()));
        lines.add(ChatColor.RED + text("scoreboard.line.beds", matchSnapshot.bedTeams().size()));
        if (observationSecondsRemaining <= 0) {
            lines.add(ChatColor.DARK_AQUA + text("scoreboard.line.dimension", dimensionLabel(viewer)));
        }
        lines.add(ChatColor.LIGHT_PURPLE + text("scoreboard.line.norespawn", stateLabel(matchSnapshot.noRespawnEnabled())));
        return lines;
    }

    private String bedStatusText(boolean placed) {
        return text(placed ? "scoreboard.bed.present" : "scoreboard.bed.missing");
    }

    private String respawnStatusText(TeamLifeBindPlayerSnapshot playerSnapshot) {
        if (playerSnapshot == null) {
            return text("scoreboard.respawn.ready");
        }
        if (playerSnapshot.respawnLocked()) {
            return text("scoreboard.respawn.locked");
        }
        if (playerSnapshot.respawnSecondsRemaining() > 0) {
            return text("scoreboard.respawn.countdown", playerSnapshot.respawnSecondsRemaining());
        }
        return text("scoreboard.respawn.ready");
    }

    private String dimensionLabel(Player viewer) {
        if (viewer == null) {
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

    public boolean isMatchObservationLocked(UUID playerId) {
        return playerId != null && pendingMatchObservationSeconds(playerId) > 0;
    }

    private boolean samePosition(Location left, Location right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getWorld() == null || right.getWorld() == null || !left.getWorld().equals(right.getWorld())) {
            return false;
        }
        return Math.abs(left.getX() - right.getX()) <= 1.0E-4D
            && Math.abs(left.getY() - right.getY()) <= 1.0E-4D
            && Math.abs(left.getZ() - right.getZ()) <= 1.0E-4D;
    }

    private void playReadyCountdownSound(int secondsRemaining) {
        float pitch = 1.0F + ((5 - secondsRemaining) * 0.1F);
        for (Player player : Bukkit.getOnlinePlayers()) {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.9F, pitch);
        }
    }

    private void playSound(Player player, Sound sound, float volume, float pitch) {
        if (player == null || !player.isOnline() || sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private Component coloredComponent(ChatColor color, String message) {
        return Component.text(message == null ? "" : message, color == null ? NamedTextColor.WHITE : color.namedTextColor());
    }

    private Component legacyComponent(String message) {
        return LEGACY_SECTION_SERIALIZER.deserialize(message == null ? "" : message);
    }

    private List<Component> legacyComponents(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Component> components = new ArrayList<>(messages.size());
        for (String message : messages) {
            components.add(legacyComponent(message));
        }
        return components;
    }

    private void sendActionBar(Player player, ChatColor color, String message) {
        if (player == null || !player.isOnline() || message == null) {
            return;
        }
        player.sendActionBar(coloredComponent(color, message));
    }

    private void broadcastColoredMessage(ChatColor color, String message) {
        if (message == null) {
            return;
        }
        Bukkit.broadcast(coloredComponent(color, message));
    }

    private AttributeInstance maxHealthAttribute(Player player) {
        return player == null ? null : player.getAttribute(Attribute.MAX_HEALTH);
    }

    private double maxHealth(Player player) {
        AttributeInstance attribute = maxHealthAttribute(player);
        return attribute != null ? attribute.getValue() : 20.0D;
    }

    private double attributeValue(Player player, Attribute attribute) {
        if (player == null || attribute == null) {
            return 0.0D;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        return instance != null ? instance.getValue() : 0.0D;
    }

    private void setPlayerMaxHealth(Player player, double maxHealth) {
        AttributeInstance attribute = maxHealthAttribute(player);
        if (attribute != null && Math.abs(attribute.getBaseValue() - maxHealth) > 0.001D) {
            attribute.setBaseValue(maxHealth);
        }
    }

    private Location resolveSafeSurfaceOrFallback(World world, int x, int z) {
        return Objects.requireNonNullElseGet(safeSurface(world, x, z, SAFE_SPAWN_SEARCH_RADIUS_BLOCKS), () -> forcedSafeSurface(world, x, z));
    }

    private void startRespawnTask() {
        cancelRespawnTask();
        respawnTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            processPendingRespawns();
            processPendingMatchObservations();
            processSharedTeamHealth();
        }, 1L, 1L);
    }

    private void cancelRespawnTask() {
        if (respawnTaskId == -1) {
            return;
        }
        Bukkit.getScheduler().cancelTask(respawnTaskId);
        respawnTaskId = -1;
    }

    private void processPendingRespawns() {
        if (pendingRespawns.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, PendingRespawn>> iterator = pendingRespawns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingRespawn> entry = iterator.next();
            PendingRespawn pending = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            int remainingTicks = pending.remainingTicks() - 1;
            if (remainingTicks <= 0) {
                if (player != null && player.isOnline()) {
                    completePendingRespawn(player);
                }
                iterator.remove();
                continue;
            }

            int secondsLeft = (remainingTicks + 19) / 20;
            if (player != null && player.isOnline()) {
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
                if (secondsLeft != pending.lastShownSeconds()) {
                sendActionBar(player, ChatColor.YELLOW, text("respawn.wait", secondsLeft));
                }
            }
            entry.setValue(new PendingRespawn(pending.team(), remainingTicks, secondsLeft));
        }
    }

    private void processPendingMatchObservations() {
        if (pendingMatchObservations.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, PendingMatchObservation>> iterator = pendingMatchObservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingMatchObservation> entry = iterator.next();
            PendingMatchObservation pending = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            int remainingTicks = pending.remainingTicks() - 1;
            if (player != null && player.isOnline()) {
                sendActionBar(player, ChatColor.AQUA, text("match.observe.wait", Math.max(1, (remainingTicks + 19) / 20)));
            }
            if (remainingTicks <= 0) {
                if (player != null && player.isOnline()) {
                    completeMatchObservation(player);
                }
                iterator.remove();
                continue;
            }

            int secondsLeft = (remainingTicks + 19) / 20;
            entry.setValue(new PendingMatchObservation(pending.anchor(), remainingTicks, secondsLeft));
        }
    }

    private void processSharedTeamHealth() {
        if (!engine.isRunning()) {
            clearSharedTeamState();
            return;
        }

        sharedStateTick++;
        Map<Integer, List<Player>> playersByTeam = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldExcludeFromSharedHealth(player)) {
                continue;
            }
            Integer team = engine.teamForPlayer(player.getUniqueId());
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

        for (Map.Entry<Integer, List<Player>> entry : playersByTeam.entrySet()) {
            int team = entry.getKey();
            List<Player> players = entry.getValue();
            Set<UUID> currentPlayers = new HashSet<>();
            for (Player player : players) {
                currentPlayers.add(player.getUniqueId());
            }

            TeamLifeBindCombatMath.ExperienceState experienceState = processSharedTeamExperience(team, players, currentPlayers);
            applySharedMaxHealth(players, experienceState.level());
            processSharedTeamFood(team, players, currentPlayers);
            applySharedPotionEffects(players);
            processSharedTeamHealthForTeam(team, players, currentPlayers);
        }
    }

    private void clearSharedTeamState() {
        teamSharedHealth.clear();
        observedTeamHealthPlayers.clear();
        teamSharedExperience.clear();
        observedTeamExperiencePlayers.clear();
        teamSharedFoodLevels.clear();
        teamSharedSaturation.clear();
        observedTeamFoodPlayers.clear();
        teamLastDamageTicks.clear();
    }

    private TeamLifeBindCombatMath.ExperienceState processSharedTeamExperience(int team, List<Player> players, Set<UUID> currentPlayers) {
        Integer baselineValue = teamSharedExperience.get(team);
        Set<UUID> observedPlayers = observedTeamExperiencePlayers.computeIfAbsent(team, ignored -> new HashSet<>());
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
        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            if (!observedPlayers.contains(playerId)) {
                applySharedExperience(player, baseline);
                continue;
            }

            int currentExperience = Math.max(0, player.getTotalExperience());
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

    private int resolveInitialSharedExperience(List<Player> players) {
        int initialExperience = 0;
        for (Player player : players) {
            initialExperience = Math.max(initialExperience, Math.max(0, player.getTotalExperience()));
        }
        return initialExperience;
    }

    private void applySharedExperience(List<Player> players, int totalExperience) {
        for (Player player : players) {
            applySharedExperience(player, totalExperience);
        }
    }

    private void applySharedExperience(Player player, int totalExperience) {
        if (player == null || !player.isOnline()) {
            return;
        }
        TeamLifeBindCombatMath.ExperienceState state = TeamLifeBindCombatMath.resolveExperienceState(totalExperience);
        if (player.getTotalExperience() == state.totalExperience()
            && player.getLevel() == state.level()
            && Math.abs(player.getExp() - state.progress()) <= 0.0001F) {
            return;
        }
        player.setTotalExperience(state.totalExperience());
        player.setLevel(state.level());
        player.setExp(state.progress());
    }

    private void processSharedTeamFood(int team, List<Player> players, Set<UUID> currentPlayers) {
        Integer baselineFoodValue = teamSharedFoodLevels.get(team);
        Double baselineSaturationValue = teamSharedSaturation.get(team);
        Set<UUID> observedPlayers = observedTeamFoodPlayers.computeIfAbsent(team, ignored -> new HashSet<>());
        if (baselineFoodValue == null || baselineSaturationValue == null) {
            int initialFood = resolveInitialSharedFood(players);
            double initialSaturation = resolveInitialSharedSaturation(players, initialFood);
            teamSharedFoodLevels.put(team, initialFood);
            teamSharedSaturation.put(team, initialSaturation);
            applySharedFood(players, initialFood, initialSaturation);
            observedPlayers.clear();
            observedPlayers.addAll(currentPlayers);
            return;
        }

        int baselineFood = clampFoodLevel(baselineFoodValue);
        double baselineSaturation = clampSaturation(baselineFood, baselineSaturationValue);
        int positiveFoodDelta = 0;
        int negativeFoodDelta = 0;
        double positiveSaturationDelta = 0.0D;
        double negativeSaturationDelta = 0.0D;
        boolean changed = false;

        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            if (!observedPlayers.contains(playerId)) {
                applySharedFood(player, baselineFood, baselineSaturation);
                continue;
            }

            int currentFood = clampFoodLevel(player.getFoodLevel());
            if (currentFood != baselineFood) {
                int foodDelta = currentFood - baselineFood;
                positiveFoodDelta = Math.max(positiveFoodDelta, foodDelta);
                negativeFoodDelta = Math.min(negativeFoodDelta, foodDelta);
                changed = true;
            }

            double currentSaturation = clampSaturation(currentFood, player.getSaturation());
            if (Math.abs(currentSaturation - baselineSaturation) > 0.001D) {
                double saturationDelta = currentSaturation - baselineSaturation;
                positiveSaturationDelta = Math.max(positiveSaturationDelta, saturationDelta);
                negativeSaturationDelta = Math.min(negativeSaturationDelta, saturationDelta);
                changed = true;
            }
        }

        int targetFood = changed ? clampFoodLevel(baselineFood + positiveFoodDelta + negativeFoodDelta) : baselineFood;
        double targetSaturation = changed
            ? clampSaturation(targetFood, baselineSaturation + positiveSaturationDelta + negativeSaturationDelta)
            : clampSaturation(targetFood, baselineSaturation);
        teamSharedFoodLevels.put(team, targetFood);
        teamSharedSaturation.put(team, targetSaturation);
        applySharedFood(players, targetFood, targetSaturation);
        observedPlayers.clear();
        observedPlayers.addAll(currentPlayers);
    }

    private int resolveInitialSharedFood(List<Player> players) {
        int initialFood = 20;
        for (Player player : players) {
            initialFood = Math.min(initialFood, clampFoodLevel(player.getFoodLevel()));
        }
        return initialFood;
    }

    private double resolveInitialSharedSaturation(List<Player> players, int sharedFoodLevel) {
        double initialSaturation = sharedFoodLevel;
        for (Player player : players) {
            initialSaturation = Math.min(initialSaturation, clampSaturation(sharedFoodLevel, player.getSaturation()));
        }
        return clampSaturation(sharedFoodLevel, initialSaturation);
    }

    private int clampFoodLevel(int foodLevel) {
        return Math.max(0, Math.min(foodLevel, 20));
    }

    private double clampSaturation(int foodLevel, double saturation) {
        return Math.max(0.0D, Math.min(saturation, foodLevel));
    }

    private void applySharedFood(List<Player> players, int foodLevel, double saturation) {
        for (Player player : players) {
            applySharedFood(player, foodLevel, saturation);
        }
    }

    private void applySharedFood(Player player, int foodLevel, double saturation) {
        if (player == null || !player.isOnline()) {
            return;
        }
        int clampedFood = clampFoodLevel(foodLevel);
        double clampedSaturation = clampSaturation(clampedFood, saturation);
        if (player.getFoodLevel() != clampedFood) {
            player.setFoodLevel(clampedFood);
        }
        if (Math.abs(player.getSaturation() - clampedSaturation) > 0.001D) {
            player.setSaturation((float) clampedSaturation);
        }
    }

    private void applySharedMaxHealth(List<Player> players, int sharedLevel) {
        double targetMaxHealth = TeamLifeBindCombatMath.sharedMaxHealth(healthPreset, sharedLevel);
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            setPlayerMaxHealth(player, targetMaxHealth);
            if (player.getHealth() > maxHealth(player)) {
                player.setHealth(maxHealth(player));
            }
        }
    }

    private void applySharedPotionEffects(List<Player> players) {
        Map<String, PotionEffect> sharedEffects = collectSharedPotionEffects(players);
        if (sharedEffects.isEmpty()) {
            return;
        }
        for (Player player : players) {
            Map<String, PotionEffect> currentEffects = new HashMap<>();
            for (PotionEffect effect : player.getActivePotionEffects()) {
                currentEffects.put(effect.getType().getKey().toString(), effect);
            }
            for (Map.Entry<String, PotionEffect> entry : sharedEffects.entrySet()) {
                PotionEffect current = currentEffects.get(entry.getKey());
                PotionEffect shared = entry.getValue();
                if (!shouldRefreshPotionEffect(current, shared)) {
                    continue;
                }
                player.removePotionEffect(shared.getType());
                player.addPotionEffect(
                    new PotionEffect(
                        shared.getType(),
                        shared.getDuration(),
                        shared.getAmplifier(),
                        shared.isAmbient(),
                        shared.hasParticles(),
                        shared.hasIcon()
                    )
                );
            }
        }
    }

    private Map<String, PotionEffect> collectSharedPotionEffects(List<Player> players) {
        Map<String, PotionEffect> effects = new HashMap<>();
        for (Player player : players) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                String effectKey = effect.getType().getKey().toString();
                PotionEffect current = effects.get(effectKey);
                if (current == null
                    || effect.getAmplifier() > current.getAmplifier()
                    || (effect.getAmplifier() == current.getAmplifier() && effect.getDuration() > current.getDuration())) {
                    effects.put(effectKey, effect);
                }
            }
        }
        return effects;
    }

    private boolean shouldRefreshPotionEffect(PotionEffect current, PotionEffect shared) {
        if (shared == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        return current.getAmplifier() != shared.getAmplifier()
            || current.isAmbient() != shared.isAmbient()
            || current.hasParticles() != shared.hasParticles()
            || current.hasIcon() != shared.hasIcon()
            || current.getDuration() + EFFECT_DURATION_SYNC_TOLERANCE_TICKS < shared.getDuration();
    }

    private void processSharedTeamHealthForTeam(int team, List<Player> players, Set<UUID> currentPlayers) {
        Double baselineValue = teamSharedHealth.get(team);
        Set<UUID> observedPlayers = observedTeamHealthPlayers.computeIfAbsent(team, ignored -> new HashSet<>());
        if (baselineValue == null) {
            double initialHealth = resolveInitialSharedHealth(players);
            teamSharedHealth.put(team, initialHealth);
            applySharedTeamHealth(players, initialHealth);
            observedPlayers.clear();
            observedPlayers.addAll(currentPlayers);
            return;
        }

        double baseline = clampSharedHealth(baselineValue, players);
        double delta = 0.0D;
        boolean changed = false;
        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            if (!observedPlayers.contains(playerId)) {
                double clampedBaseline = clampPlayerHealth(player, baseline);
                if (Math.abs(player.getHealth() - clampedBaseline) > HEALTH_SYNC_EPSILON) {
                    player.setHealth(clampedBaseline);
                }
                continue;
            }

            double currentHealth = clampPlayerHealth(player, player.getHealth());
            if (Math.abs(currentHealth - baseline) <= HEALTH_SYNC_EPSILON) {
                continue;
            }

            double playerDelta = currentHealth - baseline;
            if (playerDelta < 0.0D) {
                double armor = attributeValue(player, Attribute.ARMOR);
                double toughness = attributeValue(player, Attribute.ARMOR_TOUGHNESS);
                playerDelta = -TeamLifeBindCombatMath.applyExtraArmorReduction((float) -playerDelta, armor, toughness);
                teamLastDamageTicks.put(team, sharedStateTick);
            }
            delta += playerDelta;
            changed = true;
        }

        double targetHealth = changed ? clampSharedHealth(baseline + delta, players) : baseline;
        targetHealth = applySharedBonusRegen(team, players, targetHealth);
        teamSharedHealth.put(team, targetHealth);
        applySharedTeamHealth(players, targetHealth);
        observedPlayers.clear();
        observedPlayers.addAll(currentPlayers);
    }

    private boolean shouldExcludeFromSharedHealth(Player player) {
        return player == null
            || !player.isOnline()
            || player.isDead()
            || player.getGameMode() == GameMode.SPECTATOR
            || pendingRespawns.containsKey(player.getUniqueId());
    }

    private double resolveInitialSharedHealth(List<Player> players) {
        double initialHealth = resolveSharedHealthCap(players);
        for (Player player : players) {
            initialHealth = Math.min(initialHealth, clampPlayerHealth(player, player.getHealth()));
        }
        return clampSharedHealth(initialHealth, players);
    }

    private double clampSharedHealth(double health, List<Player> players) {
        return Math.max(0.0D, Math.min(health, resolveSharedHealthCap(players)));
    }

    private double clampPlayerHealth(Player player, double health) {
        return Math.max(0.0D, Math.min(health, maxHealth(player)));
    }

    private double resolveSharedHealthCap(List<Player> players) {
        double maxHealth = TeamLifeBindCombatMath.SHARED_MAX_HEALTH_CAP;
        for (Player player : players) {
            maxHealth = Math.min(maxHealth, maxHealth(player));
        }
        return maxHealth;
    }

    private double applySharedBonusRegen(int team, List<Player> players, double targetHealth) {
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

    private void applySharedTeamHealth(List<Player> players, double targetHealth) {
        for (Player player : players) {
            double clampedHealth = clampPlayerHealth(player, targetHealth);
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

    private void completePendingRespawn(Player player) {
        if (player == null || !player.isOnline() || !engine.isRunning()) {
            return;
        }
        if (isRespawnLocked(player.getUniqueId())) {
            handleBlockedRespawn(player);
            return;
        }
        if (isUnassignedMatchPlayer(player.getUniqueId())) {
            moveUnassignedPlayerToSpectator(player, true);
            return;
        }

        Location respawn = resolveRespawnLocation(player.getUniqueId());
        if (respawn == null) {
            return;
        }
        player.teleport(respawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        applyPresetHealth(player);
        ensureSupplyBoat(player);
        player.setGameMode(GameMode.SURVIVAL);
        grantRespawnInvulnerability(player);
        sendActionBar(player, ChatColor.GREEN, text("respawn.ready"));
    }

    private void beginMatchObservation(Player player, Location anchor) {
        if (player == null || anchor == null) {
            return;
        }
        pendingMatchObservations.put(player.getUniqueId(), new PendingMatchObservation(anchor.clone(), MATCH_OBSERVATION_TICKS, MATCH_OBSERVATION_SECONDS));
    }

    private void completeMatchObservation(Player player) {
        if (player == null || !player.isOnline() || !engine.isRunning()) {
            return;
        }
        playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.9F, 1.2F);
        sendActionBar(player, ChatColor.GREEN, text("match.observe.ready"));
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
        player.sendPlayerListHeaderAndFooter(buildTabListHeader(), buildTabListFooter(player));
    }

    private Component buildTabListHeader() {
        String state = text(engine.isRunning() ? "scoreboard.state.running" : "scoreboard.state.lobby");
        return legacyComponent(ChatColor.GOLD + "" + ChatColor.BOLD + text("scoreboard.title")
            + "\n"
            + ChatColor.YELLOW + text("scoreboard.line.state", state));
    }

    private Component buildTabListFooter(Player viewer) {
        if (!engine.isRunning()) {
            return legacyComponent(ChatColor.GREEN + text("scoreboard.line.ready", readyPlayers.size(), Bukkit.getOnlinePlayers().size())
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.AQUA + text("scoreboard.line.teams", teamCount)
                + "\n"
                + ChatColor.RED + text("scoreboard.line.health", healthPresetLabel(healthPreset))
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.LIGHT_PURPLE + text("scoreboard.line.norespawn", stateLabel(noRespawnEnabled)));
        }

        Integer playerTeam = engine.teamForPlayer(viewer.getUniqueId());
        boolean hasBed = playerTeam != null && teamBedSpawns.containsKey(playerTeam);
        return legacyComponent(ChatColor.GREEN + text("scoreboard.line.team", playerTeam != null ? teamLabel(playerTeam) : text("scoreboard.value.none"))
            + ChatColor.DARK_GRAY + " | "
            + ChatColor.AQUA + text("scoreboard.line.team_bed", playerTeam != null ? bedStatusText(hasBed) : text("scoreboard.value.none"))
            + "\n"
            + ChatColor.DARK_AQUA + text("scoreboard.line.dimension", dimensionLabel(viewer))
            + ChatColor.DARK_GRAY + " | "
            + ChatColor.LIGHT_PURPLE + text("scoreboard.line.norespawn", stateLabel(noRespawnEnabled)));
    }

    private void clearTabListDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
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
        UUID playerId = player.getUniqueId();
        Scoreboard board = ensureManagedScoreboard(playerId);

        Objective objective = board.getObjective(SCOREBOARD_OBJECTIVE_NAME);
        boolean objectiveCreated = false;
        if (objective == null) {
            objective = board.registerNewObjective(SCOREBOARD_OBJECTIVE_NAME, Criteria.DUMMY, legacyComponent(trimScoreboardText(text("scoreboard.title"))));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objectiveCreated = true;
        }

        TeamLifeBindSidebarSnapshot snapshot = getSidebarSnapshot(playerId);
        String title = trimScoreboardText(snapshot.title());
        if (objectiveCreated || !title.equals(scoreboardTitles.get(playerId))) {
            objective.displayName(legacyComponent(title));
            scoreboardTitles.put(playerId, title);
        }

        List<String> rawLines = snapshot.lines();
        int used = Math.min(rawLines.size(), SCOREBOARD_ENTRIES.length);
        List<String> lines = new ArrayList<>(used);
        for (int index = 0; index < used; index++) {
            lines.add(trimScoreboardText(rawLines.get(index)));
        }
        List<String> previousLines = objectiveCreated ? Collections.emptyList() : scoreboardLines.getOrDefault(playerId, Collections.emptyList());
        for (int index = 0; index < used; index++) {
            String entry = SCOREBOARD_ENTRIES[index];
            Team lineTeam = board.getTeam(SCOREBOARD_TEAM_PREFIX + index);
            if (lineTeam == null) {
                lineTeam = board.registerNewTeam(SCOREBOARD_TEAM_PREFIX + index);
                lineTeam.addEntry(entry);
            }
            if (previousLines.size() != used || !lines.get(index).equals(previousLines.get(index))) {
                lineTeam.prefix(legacyComponent(lines.get(index)));
                objective.getScore(entry).setScore(used - index);
            }
        }

        for (int index = used; index < previousLines.size(); index++) {
            String entry = SCOREBOARD_ENTRIES[index];
            board.resetScores(entry);
            Team lineTeam = board.getTeam(SCOREBOARD_TEAM_PREFIX + index);
            if (lineTeam != null) {
                lineTeam.prefix(Component.empty());
                lineTeam.suffix(Component.empty());
            }
        }
        scoreboardLines.put(playerId, lines);

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
        team.color(asNamedTextColor(color));
        team.prefix(legacyComponent(prefix == null ? "" : prefix));
        return team;
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

    private NamedTextColor asNamedTextColor(ChatColor color) {
        return color == null ? NamedTextColor.WHITE : color.namedTextColor();
    }

    private Scoreboard ensureManagedScoreboard(UUID playerId) {
        Scoreboard existing = managedScoreboards.get(playerId);
        if (existing != null) {
            return existing;
        }
        ScoreboardManager manager = Objects.requireNonNull(Bukkit.getScoreboardManager(), "Scoreboard manager unavailable");
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
        scoreboardTitles.remove(playerId);
        scoreboardLines.remove(playerId);
        Scoreboard managed = managedScoreboards.remove(playerId);
        if (managed == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        ScoreboardManager manager = Objects.requireNonNull(Bukkit.getScoreboardManager(), "Scoreboard manager unavailable");
        if (player != null && player.isOnline() && player.getScoreboard() == managed) {
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
        if (viewerTeam != null) {
            World viewerWorld = viewer.getWorld();
            for (Player target : onlinePlayers.values()) {
                if (target.getUniqueId().equals(viewer.getUniqueId())) {
                    continue;
                }
                if (target.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (!viewerWorld.getUID().equals(target.getWorld().getUID())) {
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
        applyManagedWorldRules(Bukkit.getWorld(lobbyWorldName));
        if (activeMatchWorldName == null || activeMatchWorldName.isBlank()) {
            return;
        }
        for (String worldName : List.of(activeMatchWorldName, activeMatchWorldName + "_nether", activeMatchWorldName + "_the_end")) {
            applyManagedWorldRules(Bukkit.getWorld(worldName));
        }
    }

    private void applyManagedWorldRules(World world) {
        setBooleanGameRule(world, RULE_LOCATOR_BAR, false);
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

    private record PendingRespawn(int team, int remainingTicks, int lastShownSeconds) {}

    private record PendingMatchObservation(Location anchor, int remainingTicks, int lastShownSeconds) {}

    private record SpawnAnchor(int x, int z, float yaw, float pitch) {}
}
