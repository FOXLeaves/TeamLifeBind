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
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.StructureType;
import org.bukkit.Tag;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.Axis;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Orientable;
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
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

public final class TeamLifeBindPaperPlugin extends JavaPlugin implements TeamLifeBindPaperApi {

    private static final long TEAM_WIPE_GUARD_MS = 2_000L;
    private static final int SPAWN_SEARCH_ATTEMPTS = 256;
    private static final int SAFE_SPAWN_SEARCH_RADIUS_BLOCKS = 4;
    private static final int MATCH_PRELOAD_RADIUS_BLOCKS = 32;
    private static final int TELEPORT_PRELOAD_RADIUS_BLOCKS = 24;
    private static final int MATCH_WARMUP_RADIUS_BLOCKS = 96;
    private static final int CHUNK_PRELOADS_PER_TICK = 1;
    private static final int FORCED_PLATFORM_RADIUS_BLOCKS = 1;
    private static final int FORCED_PLATFORM_FOUNDATION_DEPTH = 2;
    private static final int FORCED_PLATFORM_CLEARANCE_BLOCKS = 3;
    private static final int IMPORTANT_NOTICE_STAY_TICKS = 80;
    private static final int MATCH_OBSERVATION_SECONDS = 10;
    private static final int RESPAWN_COUNTDOWN_SECONDS = 5;
    private static final int MATCH_OBSERVATION_TICKS = MATCH_OBSERVATION_SECONDS * 20;
    private static final int RESPAWN_COUNTDOWN_TICKS = RESPAWN_COUNTDOWN_SECONDS * 20;
    private static final long RESPAWN_INVULNERABILITY_TICKS = 100L;
    private static final long TEAM_MODE_VOTE_COOLDOWN_MS = 5_000L;
    private static final long LOBBY_FIXED_TIME_TICKS = 6_000L;
    private static final int LOBBY_CLEAR_WEATHER_TICKS = 1_000_000;
    private static final int TEAM_MODE_GROUPING_SECONDS = 60;
    private static final int SPECIAL_MATCH_VOTE_SECONDS = 30;
    private static final int MAX_PARTY_JOIN_ID_LENGTH = 16;
    private static final int LOBBY_TEAM_MODE_SLOT = 5;
    private static final int LOBBY_MENU_SLOT = 6;
    private static final int LOBBY_GUIDE_SLOT = 7;
    private static final int LOBBY_RECIPES_SLOT = 8;
    private static final int LOBBY_MENU_SIZE = 54;
    private static final int LOBBY_MENU_READY_SLOT = 11;
    private static final int LOBBY_MENU_STATUS_SLOT = 13;
    private static final int LOBBY_MENU_HELP_SLOT = 15;
    private static final int LOBBY_MENU_TEAMS_SLOT = 19;
    private static final int LOBBY_MENU_HEALTH_SLOT = 21;
    private static final int LOBBY_MENU_HEALTH_SYNC_SLOT = 23;
    private static final int LOBBY_MENU_NORESPAWN_SLOT = 25;
    private static final int LOBBY_MENU_ANNOUNCE_TEAMS_SLOT = 29;
    private static final int LOBBY_MENU_SCOREBOARD_SLOT = 31;
    private static final int LOBBY_MENU_TAB_SLOT = 33;
    private static final int LOBBY_MENU_ADVANCEMENTS_SLOT = 35;
    private static final int LOBBY_MENU_AUTO_PORTALS_SLOT = 37;
    private static final int LOBBY_MENU_STRONGHOLD_HINTS_SLOT = 39;
    private static final int LOBBY_MENU_LANGUAGE_SLOT = 40;
    private static final int LOBBY_MENU_END_TOTEMS_SLOT = 41;
    private static final int LOBBY_MENU_SUPPLY_DROPS_SLOT = 43;
    private static final int LOBBY_MENU_START_STOP_SLOT = 48;
    private static final int LOBBY_MENU_RELOAD_SLOT = 50;
    private static final int LOBBY_MENU_INFO_SLOT = 49;
    private static final int DEV_MENU_SIZE = 54;
    private static final int DEV_MENU_TEAM_BED_SLOT = 10;
    private static final int DEV_MENU_TRACKING_WHEEL_SLOT = 12;
    private static final int DEV_MENU_DEATH_EXEMPTION_SLOT = 14;
    private static final int DEV_MENU_LIFE_CURSE_SLOT = 16;
    private static final int DEV_MENU_SUPPLY_BOAT_SLOT = 28;
    private static final int DEV_MENU_MENU_COMPASS_SLOT = 30;
    private static final int DEV_MENU_GUIDE_BOOK_SLOT = 32;
    private static final int DEV_MENU_RECIPE_BOOK_SLOT = 34;
    private static final int DEV_MENU_INFO_SLOT = 49;
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
    private static final double DEATH_EXEMPTION_RESCUE_HEALTH = 8.0D;
    private static final int DEATH_EXEMPTION_REGENERATION_TICKS = 1_400;
    private static final int DEATH_EXEMPTION_FIRE_RESISTANCE_TICKS = 1_200;
    private static final int DEATH_EXEMPTION_ABSORPTION_TICKS = 600;
    private static final int DEATH_EXEMPTION_ABSORPTION_AMPLIFIER = 3;
    private static final int DEATH_EXEMPTION_RESISTANCE_TICKS = 200;
    private static final int DEATH_EXEMPTION_RESISTANCE_AMPLIFIER = 1;
    private static final int DEATH_EXEMPTION_NO_DAMAGE_TICKS = 100;
    private static final double DEATH_EXEMPTION_BONUS_ABSORPTION = 20.0D;
    private static final long MATCH_AUTO_PORTAL_DELAY_TICKS = 18_000L;
    private static final long MATCH_SUPPLY_DROP_DELAY_TICKS = 24_000L;
    private static final long MATCH_STRONGHOLD_HINT_DELAY_TICKS = 36_000L;
    private static final long MATCH_STRONGHOLD_HINT_INTERVAL_TICKS = 3_600L;
    private static final long TRACKING_WHEEL_UPDATE_INTERVAL_TICKS = 20L;
    private static final long LIFE_CURSE_DURATION_TICKS = 3_600L;
    private static final int DEFAULT_SUPPLY_DROP_COUNT = 5;
    private static final float LOCATOR_MIN_AZIMUTH_DELTA = 0.035F;
    private static final String RULE_LOCATOR_BAR = "locatorBar";
    private static final String RULE_ANNOUNCE_ADVANCEMENTS = "announceAdvancements";
    private static final String RULE_SHOW_DEATH_MESSAGES = "showDeathMessages";
    private static final String RULE_DO_IMMEDIATE_RESPAWN = "doImmediateRespawn";
    private static final String RULE_DO_DAYLIGHT_CYCLE = "doDaylightCycle";
    private static final String RULE_DO_WEATHER_CYCLE = "doWeatherCycle";
    private static final String MENU_ACTION_READY = "ready";
    private static final String MENU_ACTION_UNREADY = "unready";
    private static final String MENU_ACTION_STATUS = "status";
    private static final String MENU_ACTION_HELP = "help";
    private static final String MENU_ACTION_LANGUAGE = "language";
    private static final String MENU_ACTION_TEAMS = "teams";
    private static final String MENU_ACTION_HEALTH = "health";
    private static final String MENU_ACTION_HEALTH_SYNC = "healthsync";
    private static final String MENU_ACTION_NORESPAWN = "norespawn";
    private static final String MENU_ACTION_ANNOUNCE_TEAMS = "announce_teams";
    private static final String MENU_ACTION_SCOREBOARD = "scoreboard";
    private static final String MENU_ACTION_TAB = "tab";
    private static final String MENU_ACTION_ADVANCEMENTS = "advancements";
    private static final String MENU_ACTION_AUTO_PORTALS = "auto_portals";
    private static final String MENU_ACTION_STRONGHOLD_HINTS = "stronghold_hints";
    private static final String MENU_ACTION_END_TOTEMS = "end_totems";
    private static final String MENU_ACTION_SUPPLY_DROPS = "supply_drops";
    private static final String MENU_ACTION_SPECIAL_HEALTH = "special_health";
    private static final String MENU_ACTION_SPECIAL_HEALTH_SYNC = "special_health_sync";
    private static final String MENU_ACTION_SPECIAL_NORESPAWN = "special_norespawn";
    private static final String MENU_ACTION_SPECIAL_AUTO_PORTALS = "special_auto_portals";
    private static final String MENU_ACTION_START = "start";
    private static final String MENU_ACTION_STOP = "stop";
    private static final String MENU_ACTION_RELOAD = "reload";
    private static final String SCOREBOARD_OBJECTIVE_NAME = "tlb_sidebar";
    private static final String SCOREBOARD_TEAM_PREFIX = "tlb_line_";
    private static final String NAME_COLOR_TEAM_PREFIX = "tlb_name_";
    private static final String NAME_COLOR_TEAM_NEUTRAL = NAME_COLOR_TEAM_PREFIX + "neutral";
    private static final String PLAYER_LANGUAGE_CONFIG_FILE = "player-languages.properties";
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
    private final Map<UUID, String> scoreboardTitles = new HashMap<>();
    private final Map<UUID, List<String>> scoreboardLines = new HashMap<>();
    private final Map<UUID, Long> supplyBoatDropConfirmUntil = new HashMap<>();
    private final Map<String, UUID> manualPortalUsers = new HashMap<>();
    private final Map<UUID, PendingRespawn> pendingRespawns = new HashMap<>();
    private final Map<UUID, PendingMatchObservation> pendingMatchObservations = new HashMap<>();
    private final Map<UUID, Long> respawnInvulnerabilityTokens = new HashMap<>();
    private final Map<UUID, String> playerLanguageCodes = new HashMap<>();
    private final Map<String, TeamLifeBindLanguage> languageCache = new HashMap<>();
    private final Map<UUID, Long> lifeCurseUntil = new HashMap<>();
    private final Map<Integer, Long> lifeCurseTeamImmunityUntil = new HashMap<>();
    private final Map<Integer, Double> teamSharedHealth = new HashMap<>();
    private final Map<Integer, Set<UUID>> observedTeamHealthPlayers = new HashMap<>();
    private final Map<Integer, Integer> teamSharedExperience = new HashMap<>();
    private final Map<Integer, Set<UUID>> observedTeamExperiencePlayers = new HashMap<>();
    private final Map<Integer, Integer> teamSharedFoodLevels = new HashMap<>();
    private final Map<Integer, Double> teamSharedSaturation = new HashMap<>();
    private final Map<Integer, Set<UUID>> observedTeamFoodPlayers = new HashMap<>();
    private final Map<Integer, Long> teamLastDamageTicks = new HashMap<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Set<UUID> lifeCurseDamageGuard = new HashSet<>();
    private final Set<UUID> noRespawnLockedPlayers = new LinkedHashSet<>();
    private final Set<String> noRespawnDimensions = new LinkedHashSet<>();
    private final List<String> availableLanguageCodes = new ArrayList<>();
    private final List<PortalSite> generatedPortalSites = new ArrayList<>();
    private final Map<UUID, Boolean> teamModeVotes = new HashMap<>();
    private final Map<UUID, Long> teamModeVoteCooldownUntil = new HashMap<>();
    private final Map<UUID, String> pendingPartyJoinIds = new HashMap<>();
    private final Map<UUID, Integer> preparedTeamAssignments = new HashMap<>();
    private final Map<UUID, HealthPreset> specialHealthVotes = new HashMap<>();
    private final Map<UUID, Boolean> specialNoRespawnVotes = new HashMap<>();
    private final Map<UUID, Boolean> specialHealthSyncVotes = new HashMap<>();
    private final Map<UUID, Boolean> specialAutoPortalVotes = new HashMap<>();

    private int teamCount;
    private HealthPreset healthPreset;
    private int randomSpawnRadius;
    private int minTeamDistance;
    private int teamSpreadRadius;
    private boolean announceTeamAssignment;
    private String configuredLanguageCode = TeamLifeBindLanguage.DEFAULT_LANGUAGE;
    private boolean healthSyncEnabled;
    private boolean noRespawnEnabled;
    private int readyCountdownSeconds;
    private String lobbyWorldName;
    private String matchWorldPrefix;
    private String activeMatchWorldName;
    private boolean scoreboardEnabled;
    private boolean tabEnabled;
    private boolean advancementsEnabled;
    private boolean serverResourcePackEnabled;
    private String serverResourcePackUrl = "";
    private byte[] serverResourcePackSha1;
    private boolean autoNetherPortalsEnabled;
    private boolean anonymousStrongholdHintsEnabled;
    private boolean endTotemRestrictionEnabled;
    private int supplyDropCount;
    private long scoreboardUpdateIntervalTicks;
    private int readyCountdownRemainingSeconds = -1;
    private int countdownTaskId = -1;
    private int locatorTaskId = -1;
    private int scoreboardTaskId = -1;
    private int tabTaskId = -1;
    private int respawnTaskId = -1;
    private int preMatchTaskId = -1;
    private int preMatchRemainingSeconds = -1;
    private NamespacedKey teamBedItemKey;
    private NamespacedKey teamBedOwnerTeamKey;
    private NamespacedKey supplyBoatItemKey;
    private NamespacedKey trackingWheelItemKey;
    private NamespacedKey deathExemptionTotemItemKey;
    private NamespacedKey lifeCursePotionItemKey;
    private NamespacedKey teamModeVoteItemKey;
    private NamespacedKey lobbyProtectedItemKey;
    private NamespacedKey lobbyMenuItemKey;
    private NamespacedKey lobbyMenuActionKey;
    private TeamLifeBindLanguage language;
    private PaperLocatorBarSupport locatorBarSupport;
    private ServerChunkPreloadCompat chunkPreloadCompat;
    private ServerUiCompat serverUiCompat;
    private long sharedStateTick = 0L;
    private long matchStartTick = -1L;
    private long nextStrongholdHintTick = -1L;
    private long lastTrackingWheelUpdateTick = -1L;
    private boolean autoNetherPortalsSpawned;
    private boolean supplyDropsSpawned;
    private boolean preMatchTeamModePhase;
    private boolean preMatchSpecialVotePhase;
    private boolean pendingSpecialMatchPhase;
    private HealthPreset activeRoundHealthPreset;
    private Boolean activeRoundNoRespawnEnabled;
    private Boolean activeRoundHealthSyncEnabled;
    private Boolean activeRoundAutoNetherPortalsEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettingsFromConfig();
        cleanupStaleMatchWorldsOnStartup();
        loadLanguage();
        initializeItemKeys();
        locatorBarSupport = new PaperLocatorBarSupport(getLogger());
        chunkPreloadCompat = new ServerChunkPreloadCompat(getLogger());
        serverUiCompat = new ServerUiCompat(getLogger());
        registerTeamBedRecipe();
        registerTrackingWheelRecipe();
        registerDeathExemptionTotemRecipe();
        ensureLobbyWorld();
        cleanupLobbyCompanionWorlds();

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
        sendConfiguredResourcePackToAllPlayers();
    }

    @Override
    public void onDisable() {
        cancelCountdown();
        clearTrackedLocatorTargets();
        cancelLocatorTask();
        cancelScoreboardTask();
        cancelTabTask();
        cancelRespawnTask();
        cancelPreMatchPhase();
        clearRoundOptions();
        pendingRespawns.clear();
        pendingMatchObservations.clear();
        clearManagedScoreboards();
        clearTabListDisplays();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearRespawnInvulnerability(player);
        }
        savePlayerLanguageCodes();
        Bukkit.getServicesManager().unregisterAll(this);
    }

    public void reloadPluginConfig(CommandSender sender) {
        reloadConfig();
        loadSettingsFromConfig();
        loadLanguage();
        ensureLobbyWorld();
        cleanupLobbyCompanionWorlds();
        applyManagedWorldRules();
        startScoreboardTask();
        startTabTask();
        if (!engine.isRunning()) {
            refreshLobbyItemsForAllLobbyPlayers();
        }
        sendConfiguredResourcePackToAllPlayers();
        sender.sendMessage(ChatColor.GREEN + text(sender, "command.reload.success"));
    }

    public boolean isMatchRunning() {
        return engine.isRunning();
    }

    public boolean hasActiveMatchSession() {
        return engine.isRunning()
            || countdownTaskId != -1
            || preMatchTaskId != -1
            || (activeMatchWorldName != null && !activeMatchWorldName.isBlank());
    }

    public int getTeamCount() {
        return teamCount;
    }

    public HealthPreset getHealthPreset() {
        return healthPreset;
    }

    private HealthPreset effectiveHealthPreset() {
        return activeRoundHealthPreset != null ? activeRoundHealthPreset : healthPreset;
    }

    private boolean effectiveNoRespawnEnabled() {
        return activeRoundNoRespawnEnabled != null ? activeRoundNoRespawnEnabled : noRespawnEnabled;
    }

    private boolean effectiveHealthSyncEnabled() {
        return activeRoundHealthSyncEnabled != null ? activeRoundHealthSyncEnabled : healthSyncEnabled;
    }

    private boolean effectiveAutoNetherPortalsEnabled() {
        return activeRoundAutoNetherPortalsEnabled != null ? activeRoundAutoNetherPortalsEnabled : autoNetherPortalsEnabled;
    }

    private RoundOptions defaultRoundOptions(boolean specialMatch) {
        return new RoundOptions(
            healthPreset,
            noRespawnEnabled,
            healthSyncEnabled,
            autoNetherPortalsEnabled,
            specialMatch
        );
    }

    private void applyRoundOptions(RoundOptions roundOptions) {
        RoundOptions resolved = roundOptions == null ? defaultRoundOptions(false) : roundOptions;
        activeRoundHealthPreset = resolved.healthPreset();
        activeRoundNoRespawnEnabled = resolved.noRespawnEnabled();
        activeRoundHealthSyncEnabled = resolved.healthSyncEnabled();
        activeRoundAutoNetherPortalsEnabled = resolved.autoNetherPortalsEnabled();
    }

    private void clearRoundOptions() {
        activeRoundHealthPreset = null;
        activeRoundNoRespawnEnabled = null;
        activeRoundHealthSyncEnabled = null;
        activeRoundAutoNetherPortalsEnabled = null;
    }

    private boolean hasConfiguredServerResourcePack() {
        return serverResourcePackEnabled && serverResourcePackUrl != null && !serverResourcePackUrl.isBlank();
    }

    private void applyServerItemModel(ItemMeta meta, String modelPath) {
        if (meta == null || modelPath == null || modelPath.isBlank() || !hasConfiguredServerResourcePack()) {
            return;
        }
        meta.setItemModel(new NamespacedKey("teamlifebind", modelPath));
    }

    private void sendConfiguredResourcePack(Player player) {
        if (player == null || !player.isOnline() || !hasConfiguredServerResourcePack()) {
            return;
        }
        try {
            if (serverResourcePackSha1 != null && serverResourcePackSha1.length == 20) {
                player.setResourcePack(serverResourcePackUrl, serverResourcePackSha1);
            } else {
                player.setResourcePack(serverResourcePackUrl);
            }
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Invalid TeamLifeBind resource pack URL: " + serverResourcePackUrl);
        }
    }

    private void sendConfiguredResourcePackToAllPlayers() {
        if (!hasConfiguredServerResourcePack()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendConfiguredResourcePack(player);
        }
    }

    public boolean isUnassignedMatchPlayer(UUID playerId) {
        return playerId == null || engine.teamForPlayer(playerId) == null;
    }

    public String text(String key, Object... args) {
        return language != null ? language.text(key, args) : key;
    }

    public String text(CommandSender sender, String key, Object... args) {
        if (sender instanceof Player player) {
            return text(player, key, args);
        }
        return text(key, args);
    }

    public String text(Player player, String key, Object... args) {
        return resolveLanguage(player).text(key, args);
    }

    public String teamLabel(int team) {
        return language != null ? language.teamLabel(team) : ("Team #" + team);
    }

    public String teamLabel(Player player, int team) {
        return resolveLanguage(player).teamLabel(team);
    }

    public String healthPresetLabel(HealthPreset preset) {
        return language != null ? language.healthPresetName(preset) : preset.name();
    }

    public String healthPresetLabel(Player player, HealthPreset preset) {
        return resolveLanguage(player).healthPresetName(preset);
    }

    public String stateLabel(boolean enabled) {
        return language != null ? language.state(enabled) : String.valueOf(enabled);
    }

    public String stateLabel(Player player, boolean enabled) {
        return resolveLanguage(player).state(enabled);
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
            effectiveHealthPreset(),
            Bukkit.getOnlinePlayers().size(),
            readyPlayers,
            engine.assignments(),
            teamBedSpawns.keySet(),
            effectiveNoRespawnEnabled(),
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
        return new TeamLifeBindSidebarSnapshot(text(viewer, "scoreboard.title"), buildSidebarLines(viewer));
    }

    public void markReady(Player player) {
        handleReadyStateChange(player, true);
    }

    public void unready(Player player) {
        handleReadyStateChange(player, false);
    }

    public void onPlayerLeave(UUID playerId) {
        readyPlayers.remove(playerId);
        teamModeVotes.remove(playerId);
        teamModeVoteCooldownUntil.remove(playerId);
        pendingPartyJoinIds.remove(playerId);
        preparedTeamAssignments.remove(playerId);
        specialHealthVotes.remove(playerId);
        specialNoRespawnVotes.remove(playerId);
        specialHealthSyncVotes.remove(playerId);
        specialAutoPortalVotes.remove(playerId);
        clearManagedScoreboard(playerId);
        trackedLocatorTargets.remove(playerId);
        trackedLocatorAngles.remove(playerId);
        supplyBoatDropConfirmUntil.remove(playerId);
        respawnInvulnerabilityTokens.remove(playerId);
        forgetSharedTeamHealthPlayer(playerId);
        forgetSharedTeamExperiencePlayer(playerId);
        forgetSharedTeamFoodPlayer(playerId);
        evaluateReadyCountdown();
        if (!engine.isRunning()) {
            refreshLobbyItemsForAllLobbyPlayers();
        }
    }

    public void handlePlayerJoin(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        onPlayerLeave(player.getUniqueId());
        sendConfiguredResourcePack(player);
        if (!engine.isRunning()) {
            teleportToLobby(player);
            sendLobbyGuide(player);
            evaluateReadyCountdown();
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
            sendActionBar(player, ChatColor.YELLOW, text(player, "respawn.wait", respawnSecondsRemaining));
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
            sendActionBar(player, ChatColor.AQUA, text(player, "match.observe.wait", observationSecondsRemaining));
            return;
        }

        player.setGameMode(GameMode.SURVIVAL);
        if (isOutsideActiveMatchWorld(player.getWorld())) {
            Location target = resolveRespawnLocation(player.getUniqueId());
            if (target != null) {
                player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
        notifyEndTotemRestriction(player);
    }

    public void sendLobbyGuide(Player player) {
        player.sendMessage(ChatColor.GOLD + text(player, "lobby.guide"));
    }

    public void teleportToLobby(Player player) {
        Location lobbySpawn = resolveLobbySpawn();
        if (lobbySpawn == null) {
            return;
        }
        preparePlayerForLobby(player);
        player.teleport(lobbySpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public boolean isManagedPortal(Location from, PlayerTeleportEvent.TeleportCause cause) {
        if (from == null || from.getWorld() == null || cause == null) {
            return false;
        }

        String base = engine.isRunning() ? activeMatchWorldName : lobbyWorldName;
        if (base == null || base.isBlank()) {
            return false;
        }

        String fromWorld = from.getWorld().getName();
        String nether = base + "_nether";
        String end = base + "_the_end";

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return fromWorld.equals(base) || fromWorld.equals(nether);
        }

        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            return fromWorld.equals(base) || fromWorld.equals(end);
        }

        return false;
    }

    public Location resolvePortalDestination(Player player, Location from, PlayerTeleportEvent.TeleportCause cause) {
        if (!isManagedPortal(from, cause)) {
            return null;
        }

        if (!engine.isRunning()) {
            return resolveLobbySpawn();
        }

        String base = engine.isRunning() ? activeMatchWorldName : lobbyWorldName;
        String fromWorld = from.getWorld().getName();
        String nether = base + "_nether";
        String end = base + "_the_end";

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            Location destination = null;
            if (fromWorld.equals(base)) {
                destination = portalLocation(nether, from.getBlockX() / 8, from.getBlockZ() / 8);
            } else if (fromWorld.equals(nether)) {
                destination = portalLocation(base, from.getBlockX() * 8, from.getBlockZ() * 8);
            }
            if (destination != null && !claimManualPortalUse(player, from)) {
                return null;
            }
            return destination;
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

    private boolean claimManualPortalUse(Player player, Location from) {
        if (player == null || from == null || from.getWorld() == null) {
            return false;
        }
        if (!engine.isRunning() || !effectiveAutoNetherPortalsEnabled() || isGeneratedPortalSite(from)) {
            return true;
        }
        String portalKey = manualPortalKey(from);
        UUID owner = manualPortalUsers.get(portalKey);
        if (owner == null) {
            manualPortalUsers.put(portalKey, player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + text(player, "portal.manual.bound"));
            return true;
        }
        if (owner.equals(player.getUniqueId())) {
            return true;
        }
        player.sendMessage(ChatColor.RED + text(player, "portal.manual.locked"));
        return false;
    }

    private boolean isGeneratedPortalSite(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        for (PortalSite site : generatedPortalSites) {
            if (isNearPortalAnchor(location, site.overworld()) || isNearPortalAnchor(location, site.nether())) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearPortalAnchor(Location location, Location anchor) {
        if (location == null || anchor == null || location.getWorld() == null || anchor.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getUID().equals(anchor.getWorld().getUID())) {
            return false;
        }
        return location.distanceSquared(anchor) <= 64.0D;
    }

    private String manualPortalKey(Location location) {
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            return "";
        }
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean foundPortal = false;
        for (int x = baseX - 2; x <= baseX + 2; x++) {
            for (int y = baseY - 3; y <= baseY + 3; y++) {
                for (int z = baseZ - 2; z <= baseZ + 2; z++) {
                    if (world.getBlockAt(x, y, z).getType() != Material.NETHER_PORTAL) {
                        continue;
                    }
                    foundPortal = true;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        if (!foundPortal) {
            return world.getName() + ":" + baseX + ":" + baseY + ":" + baseZ;
        }
        return world.getName() + ":" + minX + ":" + minY + ":" + minZ + ":" + maxX + ":" + maxY + ":" + maxZ;
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

    public boolean isLobbyCompanionWorld(World world) {
        return world != null && isLobbyCompanionWorldName(world.getName());
    }

    public void enforceSingleLobby(Player player) {
        if (player == null || !player.isOnline() || engine.isRunning() || !isLobbyCompanionWorld(player.getWorld())) {
            return;
        }
        teleportToLobby(player);
        sendLobbyGuide(player);
    }

    private boolean isLobbyCompanionWorldName(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        return worldName.equals(lobbyNetherWorldName()) || worldName.equals(lobbyEndWorldName());
    }

    private String lobbyNetherWorldName() {
        return lobbyWorldName + "_nether";
    }

    private String lobbyEndWorldName() {
        return lobbyWorldName + "_the_end";
    }

    public String noRespawnStatusText() {
        return noRespawnStatusText(null);
    }

    public String noRespawnStatusText(Player viewer) {
        List<String> blocked = new ArrayList<>(noRespawnDimensions);
        String blockedText = blocked.isEmpty() ? text(viewer, "scoreboard.value.none") : blocked.toString();
        return ChatColor.YELLOW + text(viewer, "norespawn.status", stateLabel(viewer, effectiveNoRespawnEnabled()), blockedText);
    }

    public String healthSyncStatusText() {
        return healthSyncStatusText(null);
    }

    public String healthSyncStatusText(Player viewer) {
        return ChatColor.YELLOW + text(viewer, "command.healthsync.current", stateLabel(viewer, effectiveHealthSyncEnabled()));
    }

    public String languageStatusText() {
        return languageStatusText(null);
    }

    public String languageStatusText(Player viewer) {
        return ChatColor.YELLOW + text(viewer, "command.language.current", effectiveLanguageCode(viewer), String.join(", ", availableLanguageCodes));
    }

    public void setNoRespawnEnabled(boolean enabled) {
        this.noRespawnEnabled = enabled;
        if (!enabled) {
            noRespawnLockedPlayers.clear();
        }
        getConfig().set("no-respawn.enabled", this.noRespawnEnabled);
        saveConfig();
    }

    public void setHealthSyncEnabled(boolean enabled) {
        this.healthSyncEnabled = enabled;
        clearSharedHealthTracking();
        getConfig().set("health-sync.enabled", this.healthSyncEnabled);
        saveConfig();
    }

    public void setAutoNetherPortalsEnabled(boolean enabled) {
        this.autoNetherPortalsEnabled = enabled;
        getConfig().set("timed-events.auto-nether-portals.enabled", this.autoNetherPortalsEnabled);
        saveConfig();
    }

    public void setAnonymousStrongholdHintsEnabled(boolean enabled) {
        this.anonymousStrongholdHintsEnabled = enabled;
        getConfig().set("timed-events.anonymous-stronghold-hints.enabled", this.anonymousStrongholdHintsEnabled);
        saveConfig();
    }

    public void setEndTotemRestrictionEnabled(boolean enabled) {
        this.endTotemRestrictionEnabled = enabled;
        getConfig().set("end-totem-restriction.enabled", this.endTotemRestrictionEnabled);
        saveConfig();
    }

    public void setSupplyDropCount(int count) {
        this.supplyDropCount = Math.max(0, count);
        getConfig().set("timed-events.supply-drops.count", this.supplyDropCount);
        saveConfig();
    }

    public boolean cycleLanguage(Player player, boolean previous) {
        if (availableLanguageCodes.isEmpty()) {
            return false;
        }
        int currentIndex = availableLanguageCodes.indexOf(effectiveLanguageCode(player));
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int nextIndex = Math.floorMod(currentIndex + (previous ? -1 : 1), availableLanguageCodes.size());
        return setPlayerLanguageCode(player, availableLanguageCodes.get(nextIndex));
    }

    public boolean setPlayerLanguageCode(Player player, String rawLanguageCode) {
        if (player == null) {
            return false;
        }
        String normalized = normalizeLanguageCode(rawLanguageCode);
        if (normalized == null) {
            return false;
        }
        if (!availableLanguageCodes.contains(normalized)) {
            return false;
        }
        if (normalized.equals(configuredLanguageCode)) {
            playerLanguageCodes.remove(player.getUniqueId());
        } else {
            playerLanguageCodes.put(player.getUniqueId(), normalized);
        }
        savePlayerLanguageCodes();
        refreshLocalizedUiState(player);
        return true;
    }

    public String availableLanguageSummary() {
        return String.join(", ", availableLanguageCodes);
    }

    public List<String> availableLanguageCodes() {
        return List.copyOf(availableLanguageCodes);
    }

    public String configuredLanguageCode() {
        return configuredLanguageCode;
    }

    public String effectiveLanguageCode(Player player) {
        if (player == null) {
            return configuredLanguageCode != null ? configuredLanguageCode : TeamLifeBindLanguage.DEFAULT_LANGUAGE;
        }
        String override = playerLanguageCodes.get(player.getUniqueId());
        return override != null ? override : (configuredLanguageCode != null ? configuredLanguageCode : TeamLifeBindLanguage.DEFAULT_LANGUAGE);
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
        return effectiveNoRespawnEnabled() && noRespawnLockedPlayers.contains(playerId);
    }

    public void handleBlockedRespawn(Player player) {
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            movePlayerToSpectator(player, resolveCurrentSpectatorLocation(player.getUniqueId()), false);
            player.sendMessage(ChatColor.RED + text(player, "player.eliminated.no_respawn"));
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
        sendActionBar(player, ChatColor.YELLOW, text(player, "respawn.wait", RESPAWN_COUNTDOWN_SECONDS));
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
            if (isTeamBedItem(stack) && stack.getAmount() == 1) {
                stack.setAmount(2);
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isTeamBedItem(offHand) && offHand.getAmount() == 1) {
            offHand.setAmount(2);
        }
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
            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
            } else {
                player.getInventory().setItem(slot, null);
            }
            return;
        }
    }

    public void consumeAllTeamBedTokens(Player player, ItemStack droppedStack) {
        if (player == null || player.getGameMode() == GameMode.CREATIVE || !isTeamBedItem(droppedStack)) {
            return;
        }
        Integer ownerTeam = resolveTeamBedOwnerTeam(droppedStack);
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isTeamBedItem(stack)) {
                continue;
            }
            Integer stackOwnerTeam = resolveTeamBedOwnerTeam(stack);
            if (sameTeamBedOwner(stackOwnerTeam, ownerTeam)) {
                player.getInventory().setItem(slot, null);
            }
        }
        normalizeTeamBedStack(player.getInventory().getItemInOffHand());
    }

    public ItemStack previewCraftedTeamBed(Player player) {
        Integer ownerTeam = player == null ? null : engine.teamForPlayer(player.getUniqueId());
        return createTeamBedItem(player, ownerTeam);
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
            player.sendMessage(ChatColor.RED + text(player, "bed.place_only_in_match"));
            return TeamBedPlacementResult.DENIED;
        }

        Integer playerTeam = engine.teamForPlayer(player.getUniqueId());
        if (playerTeam == null) {
            player.sendMessage(ChatColor.RED + text(player, "bed.not_on_team"));
            return TeamBedPlacementResult.DENIED;
        }

        Integer ownerTeam = resolveTeamBedOwnerTeam(stack);
        if (ownerTeam == null || !ownerTeam.equals(playerTeam)) {
            player.sendMessage(
                ChatColor.RED + text(player, "bed.wrong_team", ownerTeam != null ? teamLabel(player, ownerTeam) : text(player, "scoreboard.value.none"))
            );
            return TeamBedPlacementResult.EXPLODE;
        }

        if (hasActiveTeamBed(ownerTeam)) {
            player.sendMessage(ChatColor.RED + text(player, "bed.limit_reached"));
            return TeamBedPlacementResult.DENIED;
        }

        Location normalized = normalizeBlockCenter(bedFoot);
        placedTeamBedOwners.put(bedLocationKey(normalized), ownerTeam);
        teamBedSpawns.put(ownerTeam, normalized);
        player.sendMessage(ChatColor.GREEN + text(player, "bed.updated", teamLabel(player, ownerTeam)));
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
            int destroyedTeam = ownerTeam;
            broadcastColoredMessage(ChatColor.YELLOW, player -> text(player, "bed.destroyed", teamLabel(player, destroyedTeam)));
        }
        return ownerTeam;
    }

    public ItemStack createOwnedTeamBedDrop(int ownerTeam) {
        ItemStack item = createTeamBedItem(ownerTeam);
        item.setAmount(1);
        return item;
    }

    public ItemStack createOwnedTeamBedDrop(Player player, int ownerTeam) {
        ItemStack item = createTeamBedItem(player, ownerTeam);
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
        startMatch(sender, null, defaultRoundOptions(false));
    }

    private void startMatch(CommandSender sender, Map<UUID, Integer> preparedAssignments, RoundOptions roundOptions) {
        if (hasActiveMatchSession()) {
            sender.sendMessage(ChatColor.RED + text(sender, "match.already_running"));
            return;
        }

        RoundOptions resolvedRoundOptions = roundOptions == null ? defaultRoundOptions(false) : roundOptions;
        List<UUID> queuedPlayers = preparedAssignments == null || preparedAssignments.isEmpty()
            ? Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList()
            : new ArrayList<>(preparedAssignments.keySet());
        if (queuedPlayers.size() < teamCount) {
            sender.sendMessage(ChatColor.RED + text(sender, "match.need_players", teamCount));
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + text(sender, "match.preparing_world"));
        cleanupActiveMatchWorld();
        World world = createNewMatchWorld();
        if (world == null) {
            sender.sendMessage(ChatColor.RED + text(sender, "match.create_world_failed"));
            return;
        }

        cancelCountdown();
        clearTrackedLocatorTargets();
        readyPlayers.clear();
        teamModeVotes.clear();
        pendingPartyJoinIds.clear();
        preparedTeamAssignments.clear();
        clearSpecialMatchVotes();
        matchTeamSpawns.clear();
        teamBedSpawns.clear();
        placedTeamBedOwners.clear();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        supplyBoatDropConfirmUntil.clear();
        resetTimedMatchState();
        clearRoundOptions();

        resolveTeamBaseSpawnsAsync(world, teamCount).whenComplete((baseSpawns, throwable) ->
            Bukkit.getScheduler().runTask(this, () -> {
                if (throwable != null || baseSpawns == null || baseSpawns.size() < teamCount) {
                    clearRoundOptions();
                    sender.sendMessage(ChatColor.RED + text(sender, "match.prepare_spawns_failed"));
                    cleanupActiveMatchWorld();
                    return;
                }
                if (engine.isRunning()) {
                    return;
                }
                if (activeMatchWorldName == null || !activeMatchWorldName.equals(world.getName())) {
                    clearRoundOptions();
                    sender.sendMessage(ChatColor.RED + text(sender, "match.prepare_interrupted"));
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
                    clearRoundOptions();
                    sender.sendMessage(ChatColor.RED + text(sender, "match.need_players_after_prepare", teamCount));
                    cleanupActiveMatchWorld();
                    return;
                }

                applyRoundOptions(resolvedRoundOptions);

                StartResult result;
                try {
                    if (preparedAssignments == null || preparedAssignments.isEmpty()) {
                        result = engine.start(online.stream().map(Player::getUniqueId).toList(), new GameOptions(teamCount, resolvedRoundOptions.healthPreset()));
                    } else {
                        Map<UUID, Integer> filteredAssignments = new LinkedHashMap<>();
                        for (Player player : online) {
                            Integer assignedTeam = preparedAssignments.get(player.getUniqueId());
                            if (assignedTeam != null) {
                                filteredAssignments.put(player.getUniqueId(), assignedTeam);
                            }
                        }
                        result = engine.startWithAssignments(filteredAssignments, new GameOptions(teamCount, resolvedRoundOptions.healthPreset()));
                    }
                } catch (IllegalArgumentException exception) {
                    clearRoundOptions();
                    sender.sendMessage(ChatColor.RED + text(sender, "match.prepare_spawns_failed"));
                    cleanupActiveMatchWorld();
                    return;
                }

                markMatchStart();
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
                            player.sendMessage(
                                ChatColor.AQUA
                                    + text(
                                        player,
                                        "match.player_assignment",
                                        teamLabel(player, team),
                                        healthPresetLabel(player, resolvedRoundOptions.healthPreset())
                                    )
                            );
                            beginMatchObservation(player, entry.getValue());
                        }

                        startBackgroundMatchWarmup(baseSpawns.values(), assignedSpawns.values());

                        broadcastImportantNotice(
                            player -> text(player, "match.notice.start.title"),
                            player -> text(player, "match.notice.start.subtitle")
                        );

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
            sender.sendMessage(ChatColor.YELLOW + text(sender, "match.no_running"));
            return;
        }
        if (engine.isRunning()) {
            engine.stop();
        }
        cancelCountdown();
        cancelPreMatchPhase();
        clearTrackedLocatorTargets();
        readyPlayers.clear();
        teamModeVotes.clear();
        pendingPartyJoinIds.clear();
        preparedTeamAssignments.clear();
        clearSpecialMatchVotes();
        matchTeamSpawns.clear();
        teamBedSpawns.clear();
        placedTeamBedOwners.clear();
        pendingRespawns.clear();
        pendingMatchObservations.clear();
        clearSharedTeamState();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        supplyBoatDropConfirmUntil.clear();
        resetTimedMatchState();
        clearRoundOptions();
        teleportAllToLobby();
        cleanupActiveMatchWorld();
        broadcastColoredMessage(ChatColor.GOLD, player -> text(player, "match.stopped"));
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
        declareWinner(winner, player -> text(player, "match.win_reason.dragon", killer.getName()));
    }

    public String statusText() {
        return statusText(null);
    }

    public String statusText(Player viewer) {
        if (preMatchTeamModePhase) {
            return ChatColor.AQUA + text(viewer, "match.status.team_mode", preMatchRemainingSeconds);
        }
        if (preMatchSpecialVotePhase) {
            return ChatColor.LIGHT_PURPLE + text(viewer, "match.status.special_match", preMatchRemainingSeconds);
        }
        if (!engine.isRunning()) {
            return ChatColor.YELLOW + text(viewer, "match.status.idle.paper");
        }
        return ChatColor.GREEN
            + text(
                viewer,
                "match.status.running",
                teamCount,
                healthPresetLabel(viewer, effectiveHealthPreset()),
                sortedActiveTeams(teamBedSpawns.keySet()),
                stateLabel(viewer, effectiveNoRespawnEnabled())
            );
    }

    private void declareWinner(int team, Function<Player, String> reasonFactory) {
        broadcastImportantNotice(
            player -> text(player, "match.win", teamLabel(player, team), reasonFactory.apply(player)),
            player -> ""
        );
        broadcastColoredMessage(ChatColor.GREEN, player -> text(player, "match.win", teamLabel(player, team), reasonFactory.apply(player)));
        engine.stop();
        clearTrackedLocatorTargets();
        readyPlayers.clear();
        teamModeVotes.clear();
        pendingPartyJoinIds.clear();
        preparedTeamAssignments.clear();
        clearSpecialMatchVotes();
        matchTeamSpawns.clear();
        teamBedSpawns.clear();
        placedTeamBedOwners.clear();
        pendingRespawns.clear();
        pendingMatchObservations.clear();
        clearSharedTeamState();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        supplyBoatDropConfirmUntil.clear();
        resetTimedMatchState();
        clearRoundOptions();
        teleportAllToLobby();
        cleanupActiveMatchWorld();
    }

    private void markMatchStart() {
        matchStartTick = sharedStateTick;
        nextStrongholdHintTick = matchStartTick + MATCH_STRONGHOLD_HINT_DELAY_TICKS;
        lastTrackingWheelUpdateTick = -1L;
        autoNetherPortalsSpawned = false;
        supplyDropsSpawned = false;
        generatedPortalSites.clear();
        manualPortalUsers.clear();
        lifeCurseUntil.clear();
        lifeCurseTeamImmunityUntil.clear();
        lifeCurseDamageGuard.clear();
    }

    private void resetTimedMatchState() {
        matchStartTick = -1L;
        nextStrongholdHintTick = -1L;
        lastTrackingWheelUpdateTick = -1L;
        autoNetherPortalsSpawned = false;
        supplyDropsSpawned = false;
        generatedPortalSites.clear();
        manualPortalUsers.clear();
        lifeCurseUntil.clear();
        lifeCurseTeamImmunityUntil.clear();
        lifeCurseDamageGuard.clear();
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
        this.healthSyncEnabled = getConfig().getBoolean("health-sync.enabled", true);
        this.scoreboardEnabled = getConfig().getBoolean("scoreboard.enabled", true);
        this.tabEnabled = getConfig().getBoolean("tab.enabled", true);
        this.advancementsEnabled = getConfig().getBoolean("advancements.enabled", false);
        this.serverResourcePackEnabled = getConfig().getBoolean("resource-pack.enabled", false);
        this.serverResourcePackUrl = getConfig().getString("resource-pack.url", "").trim();
        this.serverResourcePackSha1 = parseSha1(getConfig().getString("resource-pack.sha1", ""));
        this.autoNetherPortalsEnabled = getConfig().getBoolean("timed-events.auto-nether-portals.enabled", true);
        this.anonymousStrongholdHintsEnabled = getConfig().getBoolean("timed-events.anonymous-stronghold-hints.enabled", true);
        this.endTotemRestrictionEnabled = getConfig().getBoolean("end-totem-restriction.enabled", true);
        this.supplyDropCount = Math.max(0, getConfig().getInt("timed-events.supply-drops.count", DEFAULT_SUPPLY_DROP_COUNT));
        this.scoreboardUpdateIntervalTicks = Math.max(5L, getConfig().getLong("scoreboard.update-interval-ticks", 20L));
        this.noRespawnEnabled = getConfig().getBoolean("no-respawn.enabled", true);
        if (this.serverResourcePackEnabled && this.serverResourcePackUrl.isBlank()) {
            getLogger().warning("TeamLifeBind resource pack is enabled but resource-pack.url is blank. Custom item textures will stay disabled.");
        }
        if (!this.healthSyncEnabled) {
            clearSharedHealthTracking();
        }

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

    private void registerTrackingWheelRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "tracking_wheel_recipe");
        Bukkit.removeRecipe(recipeKey);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createTrackingWheelItem());
        recipe.shape("DDD", "DCD", "DDD");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('C', Material.COMPASS);
        Bukkit.addRecipe(recipe);
    }

    private void registerDeathExemptionTotemRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "death_exemption_totem_recipe");
        Bukkit.removeRecipe(recipeKey);

        SmithingTransformRecipe recipe = new SmithingTransformRecipe(
            recipeKey,
            createDeathExemptionTotemItem(),
            new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
            new RecipeChoice.MaterialChoice(Material.TOTEM_OF_UNDYING),
            new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT)
        );
        Bukkit.addRecipe(recipe);
    }

    private ItemStack createTeamBedItem(Integer ownerTeam) {
        return createTeamBedItem(null, ownerTeam);
    }

    private ItemStack createTeamBedItem(Player player, Integer ownerTeam) {
        ItemStack item = new ItemStack(Material.WHITE_BED, 2);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String displayName = ChatColor.GOLD + text(player, "item.team_bed.name");
        if (ownerTeam != null) {
            displayName += ChatColor.YELLOW + " [" + teamLabel(player, ownerTeam) + "]";
        }
        meta.setDisplayName(displayName);
        meta.setLore(List.of(ChatColor.GRAY + text(player, "item.team_bed.lore")));
        meta.setMaxStackSize(2);
        applyServerItemModel(meta, "team_bed");
        meta.getPersistentDataContainer().set(teamBedItemKey, PersistentDataType.BYTE, (byte) 1);
        if (ownerTeam != null) {
            meta.getPersistentDataContainer().set(teamBedOwnerTeamKey, PersistentDataType.INTEGER, ownerTeam);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTrackingWheelItem() {
        return createTrackingWheelItem(null);
    }

    private ItemStack createTrackingWheelItem(Player player) {
        ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.GOLD + text(player, "item.tracking_wheel.name"));
        meta.setLore(List.of(ChatColor.GRAY + text(player, "item.tracking_wheel.lore")));
        applyServerItemModel(meta, "tracking_wheel");
        meta.getPersistentDataContainer().set(trackingWheelItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDeathExemptionTotemItem() {
        return createDeathExemptionTotemItem(null);
    }

    private ItemStack createDeathExemptionTotemItem(Player player) {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.DARK_RED + text(player, "item.death_exemption_totem.name"));
        meta.setLore(List.of(ChatColor.GRAY + text(player, "item.death_exemption_totem.lore")));
        applyServerItemModel(meta, "death_exemption_totem");
        meta.getPersistentDataContainer().set(deathExemptionTotemItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLifeCursePotionItem() {
        return createLifeCursePotionItem(null);
    }

    private ItemStack createLifeCursePotionItem(Player player) {
        ItemStack item = new ItemStack(Material.POTION);
        if (!(item.getItemMeta() instanceof PotionMeta meta)) {
            return item;
        }
        meta.setBasePotionType(PotionType.WATER);
        meta.setColor(Color.fromRGB(84, 18, 18));
        meta.setDisplayName(ChatColor.DARK_RED + text(player, "item.life_curse_potion.name"));
        meta.setLore(List.of(ChatColor.GRAY + text(player, "item.life_curse_potion.lore")));
        applyServerItemModel(meta, "life_curse_potion");
        meta.getPersistentDataContainer().set(lifeCursePotionItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTrackingWheelItem(ItemStack stack) {
        return hasItemMarker(stack, trackingWheelItemKey);
    }

    public boolean isDeathExemptionTotemItem(ItemStack stack) {
        return hasItemMarker(stack, deathExemptionTotemItemKey);
    }

    public boolean isLifeCursePotionItem(ItemStack stack) {
        return hasItemMarker(stack, lifeCursePotionItemKey);
    }

    private void normalizeTeamBedStack(ItemStack stack) {
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
        trackingWheelItemKey = new NamespacedKey(this, "tracking_wheel_item");
        deathExemptionTotemItemKey = new NamespacedKey(this, "death_exemption_totem_item");
        lifeCursePotionItemKey = new NamespacedKey(this, "life_curse_potion_item");
        teamModeVoteItemKey = new NamespacedKey(this, "team_mode_vote_item");
        lobbyProtectedItemKey = new NamespacedKey(this, "lobby_protected_item");
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
        if (lobbyState) {
            player.setPlayerTime(LOBBY_FIXED_TIME_TICKS, false);
            player.setPlayerWeather(WeatherType.CLEAR);
        } else {
            player.resetPlayerTime();
            player.resetPlayerWeather();
        }
    }

    private void giveLobbyItems(Player player) {
        player.getInventory().setItem(LOBBY_TEAM_MODE_SLOT, createTeamModeVoteItem(player));
        player.getInventory().setItem(LOBBY_MENU_SLOT, createLobbyMenuItem(player));
        player.getInventory().setItem(LOBBY_GUIDE_SLOT, createGuideBookItem(player));
        player.getInventory().setItem(LOBBY_RECIPES_SLOT, createRecipeBookItem(player));
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

    private ItemStack createLobbyMenuItem(Player player) {
        ItemStack item = new ItemStack(Material.COMPASS, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.AQUA + text(player, "item.menu.name"));
        meta.setLore(List.of(ChatColor.GRAY + text(player, "item.menu.lore")));
        meta.getPersistentDataContainer().set(lobbyProtectedItemKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(lobbyMenuItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTeamModeVoteItem(Player player) {
        boolean enabled = teamModeVoteEnabled(player == null ? null : player.getUniqueId());
        ItemStack item = new ItemStack(Material.PAPER, enabled ? 2 : 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        int yesVotes = countTeamModeVotes(true);
        int noVotes = countTeamModeVotes(false);
        long cooldownSeconds = remainingTeamModeVoteCooldownSeconds(player == null ? null : player.getUniqueId());
        meta.setDisplayName(ChatColor.YELLOW + text(player, "item.team_mode_vote.name"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + text(player, "item.team_mode_vote.current", stateLabel(player, enabled)));
        lore.add(ChatColor.GRAY + text(player, "item.team_mode_vote.counts", yesVotes, noVotes));
        lore.add(
            cooldownSeconds > 0L
                ? ChatColor.RED + text(player, "item.team_mode_vote.cooldown", cooldownSeconds)
                : ChatColor.GRAY + text(player, "item.team_mode_vote.tip")
        );
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(lobbyProtectedItemKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(teamModeVoteItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuideBookItem(Player player) {
        return createWrittenBookItem(
            text(player, "item.guide.name"),
            text(player, "book.guide.title"),
            text(player, "book.guide.author"),
            List.of(text(player, "book.guide.page1"), text(player, "book.guide.page2"))
        );
    }

    private ItemStack createRecipeBookItem(Player player) {
        return createWrittenBookItem(
            text(player, "item.recipes.name"),
            text(player, "book.recipes.title"),
            text(player, "book.recipes.author"),
            List.of(text(player, "book.recipes.page1"), text(player, "book.recipes.page2"))
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
        meta.getPersistentDataContainer().set(lobbyProtectedItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isLobbyMenuItem(ItemStack stack) {
        return hasItemMarker(stack, lobbyMenuItemKey);
    }

    public boolean isTeamModeVoteItem(ItemStack stack) {
        return hasItemMarker(stack, teamModeVoteItemKey);
    }

    public boolean isProtectedLobbyItem(ItemStack stack) {
        return hasItemMarker(stack, lobbyProtectedItemKey);
    }

    private boolean teamModeVoteEnabled(UUID playerId) {
        return playerId != null && Boolean.TRUE.equals(teamModeVotes.get(playerId));
    }

    private int countTeamModeVotes(boolean enabled) {
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != null && online.isOnline() && teamModeVoteEnabled(online.getUniqueId()) == enabled) {
                count++;
            }
        }
        return count;
    }

    private boolean isTeamModeVotePassed(Collection<Player> players) {
        if (players == null || players.isEmpty()) {
            return false;
        }
        int yesVotes = 0;
        for (Player player : players) {
            if (player != null && teamModeVoteEnabled(player.getUniqueId())) {
                yesVotes++;
            }
        }
        return yesVotes > (players.size() - yesVotes);
    }

    private void refreshLobbyItemsForAllLobbyPlayers() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != null && online.isOnline() && isInLobby(online) && !engine.isRunning()) {
                giveLobbyItems(online);
            }
        }
    }

    public void toggleTeamModeVote(Player player) {
        if (player == null) {
            return;
        }
        if (engine.isRunning() || countdownTaskId != -1 || isPreMatchPhaseActive()) {
            player.sendMessage(ChatColor.RED + text(player, "team_mode.vote.locked"));
            return;
        }
        long now = System.currentTimeMillis();
        long remainingMillis = remainingTeamModeVoteCooldownMillis(player.getUniqueId(), now);
        if (remainingMillis > 0L) {
            long remainingSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
            giveLobbyItems(player);
            player.sendMessage(ChatColor.YELLOW + text(player, "team_mode.vote.cooldown", remainingSeconds));
            return;
        }
        boolean next = !teamModeVoteEnabled(player.getUniqueId());
        teamModeVotes.put(player.getUniqueId(), next);
        teamModeVoteCooldownUntil.put(player.getUniqueId(), now + TEAM_MODE_VOTE_COOLDOWN_MS);
        giveLobbyItems(player);
        player.sendMessage(ChatColor.GREEN + text(player, next ? "team_mode.vote.enabled" : "team_mode.vote.disabled"));
        refreshLobbyItemsForAllLobbyPlayers();
    }

    private long remainingTeamModeVoteCooldownMillis(UUID playerId, long now) {
        if (playerId == null) {
            return 0L;
        }
        return Math.max(0L, teamModeVoteCooldownUntil.getOrDefault(playerId, 0L) - now);
    }

    private long remainingTeamModeVoteCooldownSeconds(UUID playerId) {
        long remainingMillis = remainingTeamModeVoteCooldownMillis(playerId, System.currentTimeMillis());
        return remainingMillis <= 0L ? 0L : Math.max(1L, (remainingMillis + 999L) / 1000L);
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
            sendActionBar(player, ChatColor.YELLOW, text(player, "boat.drop_confirm"));
        }
    }

    public void notifySupplyBoatDiscarded(Player player) {
        if (player != null) {
            sendActionBar(player, ChatColor.GRAY, text(player, "boat.discarded"));
        }
    }

    public void notifyLobbyItemLocked(Player player) {
        if (player != null) {
            sendActionBar(player, ChatColor.RED, text(player, "lobby.item_locked"));
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
            player.sendMessage(ChatColor.YELLOW + text(player, "player.unassigned_spectator"));
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
        return preloadLocationsAsync(locations, MATCH_PRELOAD_RADIUS_BLOCKS);
    }

    private CompletableFuture<Void> preloadLocationsAsync(Collection<Location> locations, int blockRadius) {
        if (locations == null || locations.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        Set<String> loadedChunks = new LinkedHashSet<>();
        List<ChunkLoadRequest> requests = new ArrayList<>();
        for (Location location : locations) {
            if (location == null || location.getWorld() == null) {
                continue;
            }
            World world = location.getWorld();
            int chunkRadius = Math.max(0, (blockRadius + 15) >> 4);
            int chunkX = Math.floorDiv(location.getBlockX(), 16);
            int chunkZ = Math.floorDiv(location.getBlockZ(), 16);
            int minChunkX = chunkX - chunkRadius;
            int maxChunkX = chunkX + chunkRadius;
            int minChunkZ = chunkZ - chunkRadius;
            int maxChunkZ = chunkZ + chunkRadius;
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    String key = world.getUID() + ":" + cx + ":" + cz;
                    if (loadedChunks.add(key)) {
                        requests.add(new ChunkLoadRequest(world.getUID(), cx, cz));
                    }
                }
            }
        }

        if (requests.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        if (chunkPreloadCompat != null && chunkPreloadCompat.isAsyncAvailable()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (ChunkLoadRequest request : requests) {
                World world = Bukkit.getWorld(request.worldId());
                if (world == null || world.isChunkLoaded(request.chunkX(), request.chunkZ())) {
                    continue;
                }
                futures.add(chunkPreloadCompat.preloadChunk(world, request.chunkX(), request.chunkZ(), true));
            }
            if (futures.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                try {
                    int processed = 0;
                    while (index < requests.size() && processed < CHUNK_PRELOADS_PER_TICK) {
                        ChunkLoadRequest request = requests.get(index++);
                        World world = Bukkit.getWorld(request.worldId());
                        if (world != null && !world.isChunkLoaded(request.chunkX(), request.chunkZ())) {
                            world.loadChunk(request.chunkX(), request.chunkZ(), true);
                        }
                        processed++;
                    }
                    if (index >= requests.size()) {
                        future.complete(null);
                        cancel();
                    }
                } catch (Throwable ex) {
                    future.completeExceptionally(ex);
                    cancel();
                }
            }
        }.runTaskTimer(this, 1L, 1L);
        return future;
    }

    private void startBackgroundMatchWarmup(Collection<Location> baseSpawns, Collection<Location> assignedSpawns) {
        List<Location> warmupLocations = new ArrayList<>();
        if (baseSpawns != null) {
            for (Location location : baseSpawns) {
                if (location != null && location.getWorld() != null) {
                    warmupLocations.add(location.clone());
                }
            }
        }
        if (assignedSpawns != null) {
            for (Location location : assignedSpawns) {
                if (location != null && location.getWorld() != null) {
                    warmupLocations.add(location.clone());
                }
            }
        }
        if (warmupLocations.isEmpty()) {
            return;
        }

        preloadLocationsAsync(warmupLocations, MATCH_WARMUP_RADIUS_BLOCKS).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                getLogger().warning("Failed to warm up match chunks: " + throwable.getMessage());
            }
        });
    }

    private void applyPresetHealth(Player player) {
        double targetMaxHealth = resolvePlayerSharedMaxHealth(player);
        setPlayerMaxHealth(player, targetMaxHealth);
        player.setHealth(Math.min(maxHealth(player), targetMaxHealth));
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
    }

    private double resolvePlayerSharedMaxHealth(Player player) {
        HealthPreset resolvedHealthPreset = effectiveHealthPreset();
        if (player == null) {
            return resolvedHealthPreset.maxHealth();
        }
        Integer team = engine.teamForPlayer(player.getUniqueId());
        if (team == null) {
            return resolvedHealthPreset.maxHealth();
        }
        int totalExperience = teamSharedExperience.getOrDefault(team, player.getTotalExperience());
        int sharedLevel = TeamLifeBindCombatMath.resolveExperienceState(totalExperience).level();
        return TeamLifeBindCombatMath.sharedMaxHealth(resolvedHealthPreset, sharedLevel);
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

    private int stepSupplyDropCount(int current, boolean decrement) {
        if (decrement) {
            return Math.max(0, current - 1);
        }
        return Math.min(12, current + 1);
    }

    private HealthPreset stepHealthPreset(HealthPreset current, boolean decrement) {
        HealthPreset[] presets = HealthPreset.values();
        int offset = decrement ? -1 : 1;
        return presets[Math.floorMod(current.ordinal() + offset, presets.length)];
    }

    private boolean shouldLockNoRespawn(World world) {
        if (!effectiveNoRespawnEnabled() || world == null) {
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
            cancelPreMatchPhase();
            return;
        }

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (!hasEnoughOnlinePlayers(online)) {
            if (countdownTaskId != -1 || isPreMatchPhaseActive()) {
                broadcastColoredMessage(ChatColor.YELLOW, player -> text(player, "ready.countdown_cancel_players"));
            }
            cancelCountdown();
            cancelPreMatchPhase();
            return;
        }

        if (!areAllOnlinePlayersReady(online)) {
            if (countdownTaskId != -1 || isPreMatchPhaseActive()) {
                broadcastColoredMessage(ChatColor.YELLOW, player -> text(player, "ready.countdown_cancel_unready"));
            }
            cancelCountdown();
            cancelPreMatchPhase();
            return;
        }

        if (countdownTaskId != -1 || isPreMatchPhaseActive()) {
            return;
        }

        final int[] remain = new int[] { readyCountdownSeconds };
        readyCountdownRemainingSeconds = remain[0];
        broadcastColoredMessage(ChatColor.GOLD, player -> text(player, "ready.countdown_started"));
        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (engine.isRunning()) {
                cancelCountdown();
                return;
            }

            List<Player> current = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (current.size() < teamCount) {
                broadcastColoredMessage(ChatColor.YELLOW, player -> text(player, "ready.countdown_cancel_players"));
                cancelCountdown();
                return;
            }
            for (Player player : current) {
                if (!readyPlayers.contains(player.getUniqueId())) {
                    broadcastColoredMessage(ChatColor.YELLOW, viewer -> text(viewer, "ready.countdown_cancel_unready"));
                    cancelCountdown();
                    return;
                }
            }

            if (remain[0] <= 0) {
                cancelCountdown();
                beginAutomaticStartFlow();
                return;
            }

            if (remain[0] <= 5 || remain[0] % 5 == 0) {
                broadcastColoredMessage(ChatColor.GOLD, player -> text(player, "ready.countdown_tick", remain[0]));
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

    private boolean isPreMatchPhaseActive() {
        return preMatchTaskId != -1;
    }

    private boolean hasEnoughOnlinePlayers(Collection<Player> players) {
        return players != null && players.size() >= teamCount;
    }

    private boolean areAllOnlinePlayersReady(Collection<Player> players) {
        if (players == null) {
            return false;
        }
        for (Player player : players) {
            if (player == null || !readyPlayers.contains(player.getUniqueId())) {
                return false;
            }
        }
        return true;
    }

    private void beginAutomaticStartFlow() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (!hasEnoughOnlinePlayers(online) || !areAllOnlinePlayersReady(online)) {
            return;
        }

        preparedTeamAssignments.clear();
        clearSpecialMatchVotes();
        pendingPartyJoinIds.clear();

        boolean teamModeEnabled = isTeamModeVotePassed(online);
        boolean specialMatchEnabled = random.nextBoolean();
        if (teamModeEnabled) {
            startTeamModePhase(specialMatchEnabled);
            return;
        }
        if (specialMatchEnabled) {
            startSpecialMatchVotePhase();
            return;
        }
        startMatch(Bukkit.getConsoleSender());
    }

    private void startTeamModePhase(boolean specialMatchAfterwards) {
        stopPreMatchTimer();
        clearSpecialMatchVotes();
        preparedTeamAssignments.clear();
        pendingPartyJoinIds.clear();
        preMatchTeamModePhase = true;
        preMatchSpecialVotePhase = false;
        pendingSpecialMatchPhase = specialMatchAfterwards;
        preMatchRemainingSeconds = TEAM_MODE_GROUPING_SECONDS;
        broadcastImportantNotice(
            player -> text(player, "team_mode.phase.title"),
            player -> text(player, "team_mode.phase.subtitle", TEAM_MODE_GROUPING_SECONDS)
        );
        broadcastColoredMessage(ChatColor.AQUA, player -> text(player, "team_mode.phase.start", TEAM_MODE_GROUPING_SECONDS));
        preMatchTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (engine.isRunning()) {
                cancelPreMatchPhase();
                return;
            }
            List<Player> current = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (!hasEnoughOnlinePlayers(current)) {
                broadcastColoredMessage(ChatColor.YELLOW, player -> text(player, "ready.countdown_cancel_players"));
                cancelPreMatchPhase();
                return;
            }
            if (!areAllOnlinePlayersReady(current)) {
                broadcastColoredMessage(ChatColor.YELLOW, player -> text(player, "ready.countdown_cancel_unready"));
                cancelPreMatchPhase();
                return;
            }
            if (preMatchRemainingSeconds <= 0) {
                finalizeTeamModePhase(current);
                return;
            }
            if (preMatchRemainingSeconds <= 5 || preMatchRemainingSeconds % 15 == 0) {
                broadcastColoredMessage(ChatColor.AQUA, player -> text(player, "team_mode.phase.tick", preMatchRemainingSeconds));
            }
            preMatchRemainingSeconds--;
        }, 20L, 20L);
    }

    private void finalizeTeamModePhase(List<Player> players) {
        Map<UUID, Integer> assignments = buildPreparedTeamAssignments(players);
        boolean specialMatchAfterwards = pendingSpecialMatchPhase;
        stopPreMatchTimer();
        preMatchTeamModePhase = false;
        pendingSpecialMatchPhase = false;
        preMatchRemainingSeconds = -1;
        preparedTeamAssignments.clear();
        preparedTeamAssignments.putAll(assignments);
        pendingPartyJoinIds.clear();
        broadcastColoredMessage(ChatColor.GREEN, player -> text(player, "team_mode.phase.complete"));
        if (specialMatchAfterwards) {
            startSpecialMatchVotePhase();
            return;
        }
        startPreparedMatch(defaultRoundOptions(false));
    }

    private void startSpecialMatchVotePhase() {
        stopPreMatchTimer();
        preMatchTeamModePhase = false;
        preMatchSpecialVotePhase = true;
        pendingSpecialMatchPhase = false;
        preMatchRemainingSeconds = SPECIAL_MATCH_VOTE_SECONDS;
        seedSpecialMatchVotes(new ArrayList<>(Bukkit.getOnlinePlayers()));
        broadcastImportantNotice(
            player -> text(player, "special_match.phase.title"),
            player -> text(player, "special_match.phase.subtitle", SPECIAL_MATCH_VOTE_SECONDS)
        );
        broadcastColoredMessage(ChatColor.LIGHT_PURPLE, player -> text(player, "special_match.phase.start", SPECIAL_MATCH_VOTE_SECONDS));
        preMatchTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (engine.isRunning()) {
                cancelPreMatchPhase();
                return;
            }
            List<Player> current = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (!hasEnoughOnlinePlayers(current)) {
                broadcastColoredMessage(ChatColor.YELLOW, player -> text(player, "ready.countdown_cancel_players"));
                cancelPreMatchPhase();
                return;
            }
            if (!areAllOnlinePlayersReady(current)) {
                broadcastColoredMessage(ChatColor.YELLOW, player -> text(player, "ready.countdown_cancel_unready"));
                cancelPreMatchPhase();
                return;
            }
            if (preMatchRemainingSeconds <= 0) {
                finalizeSpecialMatchVotePhase(current);
                return;
            }
            if (preMatchRemainingSeconds <= 5 || preMatchRemainingSeconds % 10 == 0) {
                broadcastColoredMessage(ChatColor.LIGHT_PURPLE, player -> text(player, "special_match.phase.tick", preMatchRemainingSeconds));
            }
            preMatchRemainingSeconds--;
        }, 20L, 20L);
    }

    private void finalizeSpecialMatchVotePhase(List<Player> players) {
        RoundOptions roundOptions = resolveSpecialRoundOptions(players);
        stopPreMatchTimer();
        preMatchSpecialVotePhase = false;
        preMatchRemainingSeconds = -1;
        clearSpecialMatchVotes();
        broadcastColoredMessage(
            ChatColor.LIGHT_PURPLE,
            player -> text(
                player,
                "special_match.phase.complete",
                healthPresetLabel(player, roundOptions.healthPreset()),
                stateLabel(player, roundOptions.noRespawnEnabled()),
                stateLabel(player, roundOptions.autoNetherPortalsEnabled()),
                stateLabel(player, roundOptions.healthSyncEnabled())
            )
        );
        startPreparedMatch(roundOptions);
    }

    private void startPreparedMatch(RoundOptions roundOptions) {
        Map<UUID, Integer> assignments = preparedTeamAssignments.isEmpty() ? null : new LinkedHashMap<>(preparedTeamAssignments);
        preparedTeamAssignments.clear();
        startMatch(Bukkit.getConsoleSender(), assignments, roundOptions == null ? defaultRoundOptions(false) : roundOptions);
    }

    private void stopPreMatchTimer() {
        if (preMatchTaskId == -1) {
            return;
        }
        Bukkit.getScheduler().cancelTask(preMatchTaskId);
        preMatchTaskId = -1;
    }

    private void cancelPreMatchPhase() {
        stopPreMatchTimer();
        preMatchRemainingSeconds = -1;
        preMatchTeamModePhase = false;
        preMatchSpecialVotePhase = false;
        pendingSpecialMatchPhase = false;
        pendingPartyJoinIds.clear();
        preparedTeamAssignments.clear();
        clearSpecialMatchVotes();
    }

    private void clearSpecialMatchVotes() {
        specialHealthVotes.clear();
        specialNoRespawnVotes.clear();
        specialHealthSyncVotes.clear();
        specialAutoPortalVotes.clear();
    }

    public boolean isTeamModeGroupingPhaseActive() {
        return preMatchTeamModePhase;
    }

    public String teamModeJoinStatusText(Player player) {
        String currentId = player == null ? null : pendingPartyJoinIds.get(player.getUniqueId());
        String shownId = currentId == null ? text(player, "scoreboard.value.none") : currentId;
        if (!preMatchTeamModePhase) {
            return ChatColor.YELLOW + text(player, "team_mode.join.status.inactive", shownId);
        }
        return ChatColor.AQUA + text(player, "team_mode.join.status.active", shownId, preMatchRemainingSeconds, maxPartySizeForCurrentLobby());
    }

    public void clearPendingPartyJoinId(Player player) {
        if (player == null) {
            return;
        }
        if (!preMatchTeamModePhase) {
            player.sendMessage(ChatColor.RED + text(player, "team_mode.join.inactive"));
            return;
        }
        pendingPartyJoinIds.remove(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + text(player, "team_mode.join.cleared"));
    }

    public boolean setPendingPartyJoinId(Player player, String rawId) {
        if (player == null) {
            return false;
        }
        if (!preMatchTeamModePhase) {
            player.sendMessage(ChatColor.RED + text(player, "team_mode.join.inactive"));
            return false;
        }

        String normalized = normalizePendingPartyJoinId(rawId);
        if (normalized == null) {
            player.sendMessage(ChatColor.RED + text(player, "team_mode.join.invalid", MAX_PARTY_JOIN_ID_LENGTH));
            return false;
        }

        int maxPartySize = maxPartySizeForCurrentLobby();
        int currentSize = 1;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null || !online.isOnline() || online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (normalized.equals(pendingPartyJoinIds.get(online.getUniqueId()))) {
                currentSize++;
            }
        }
        if (currentSize > maxPartySize) {
            player.sendMessage(ChatColor.RED + text(player, "team_mode.join.too_large", maxPartySize));
            return false;
        }

        pendingPartyJoinIds.put(player.getUniqueId(), normalized);
        player.sendMessage(ChatColor.GREEN + text(player, "team_mode.join.updated", normalized, currentSize, maxPartySize));
        return true;
    }

    private String normalizePendingPartyJoinId(String rawId) {
        if (rawId == null) {
            return null;
        }
        String normalized = rawId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_PARTY_JOIN_ID_LENGTH) {
            return null;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if ((current >= 'a' && current <= 'z')
                || (current >= '0' && current <= '9')
                || current == '_'
                || current == '-') {
                continue;
            }
            return null;
        }
        return normalized;
    }

    private byte[] parseSha1(String rawSha1) {
        if (rawSha1 == null) {
            return null;
        }
        String normalized = rawSha1.trim().replace(" ", "").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.matches("[0-9a-f]{40}")) {
            getLogger().warning("Invalid TeamLifeBind resource pack sha1. Expected 40 hex chars, got: " + rawSha1);
            return null;
        }
        byte[] decoded = new byte[20];
        for (int index = 0; index < decoded.length; index++) {
            int start = index * 2;
            decoded[index] = (byte) Integer.parseInt(normalized.substring(start, start + 2), 16);
        }
        return decoded;
    }

    private int maxPartySizeForCurrentLobby() {
        int onlineCount = Math.max(teamCount, Bukkit.getOnlinePlayers().size());
        return Math.max(1, (int) Math.ceil(onlineCount / (double) teamCount));
    }

    private void seedSpecialMatchVotes(List<Player> players) {
        clearSpecialMatchVotes();
        if (players == null) {
            return;
        }
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            UUID playerId = player.getUniqueId();
            specialHealthVotes.put(playerId, healthPreset);
            specialNoRespawnVotes.put(playerId, noRespawnEnabled);
            specialHealthSyncVotes.put(playerId, healthSyncEnabled);
            specialAutoPortalVotes.put(playerId, autoNetherPortalsEnabled);
        }
    }

    private RoundOptions resolveSpecialRoundOptions(List<Player> players) {
        return new RoundOptions(
            resolveSpecialHealthVote(players),
            resolveBooleanVote(players, specialNoRespawnVotes, noRespawnEnabled),
            resolveBooleanVote(players, specialHealthSyncVotes, healthSyncEnabled),
            resolveBooleanVote(players, specialAutoPortalVotes, autoNetherPortalsEnabled),
            true
        );
    }

    private HealthPreset resolveSpecialHealthVote(List<Player> players) {
        Map<HealthPreset, Integer> counts = new LinkedHashMap<>();
        for (HealthPreset preset : HealthPreset.values()) {
            counts.put(preset, 0);
        }
        if (players != null) {
            for (Player player : players) {
                if (player == null || !player.isOnline()) {
                    continue;
                }
                HealthPreset selected = specialHealthVotes.getOrDefault(player.getUniqueId(), healthPreset);
                counts.put(selected, counts.getOrDefault(selected, 0) + 1);
            }
        }

        HealthPreset selected = healthPreset;
        int selectedVotes = -1;
        for (HealthPreset preset : HealthPreset.values()) {
            int currentVotes = counts.getOrDefault(preset, 0);
            if (currentVotes > selectedVotes || (currentVotes == selectedVotes && preset == healthPreset)) {
                selected = preset;
                selectedVotes = currentVotes;
            }
        }
        return selected;
    }

    private boolean resolveBooleanVote(List<Player> players, Map<UUID, Boolean> voteMap, boolean fallback) {
        int enabledVotes = 0;
        int disabledVotes = 0;
        if (players != null) {
            for (Player player : players) {
                if (player == null || !player.isOnline()) {
                    continue;
                }
                boolean value = voteMap.getOrDefault(player.getUniqueId(), fallback);
                if (value) {
                    enabledVotes++;
                } else {
                    disabledVotes++;
                }
            }
        }
        if (enabledVotes == disabledVotes) {
            return fallback;
        }
        return enabledVotes > disabledVotes;
    }

    private String currentLobbyStateKey() {
        if (engine.isRunning()) {
            return "scoreboard.state.running";
        }
        if (preMatchSpecialVotePhase) {
            return "scoreboard.state.special_match";
        }
        if (preMatchTeamModePhase) {
            return "scoreboard.state.team_mode";
        }
        return "scoreboard.state.lobby";
    }

    private HealthPreset currentSpecialHealthVote(Player player) {
        return player == null ? healthPreset : specialHealthVotes.getOrDefault(player.getUniqueId(), healthPreset);
    }

    private boolean currentSpecialNoRespawnVote(Player player) {
        return player != null && specialNoRespawnVotes.getOrDefault(player.getUniqueId(), noRespawnEnabled);
    }

    private boolean currentSpecialHealthSyncVote(Player player) {
        return player != null && specialHealthSyncVotes.getOrDefault(player.getUniqueId(), healthSyncEnabled);
    }

    private boolean currentSpecialAutoPortalVote(Player player) {
        return player != null && specialAutoPortalVotes.getOrDefault(player.getUniqueId(), autoNetherPortalsEnabled);
    }

    private void updateSpecialHealthVote(Player player, boolean decrement) {
        if (player == null) {
            return;
        }
        if (!preMatchSpecialVotePhase) {
            player.sendMessage(ChatColor.RED + text(player, "special_match.vote.inactive"));
            return;
        }
        HealthPreset next = stepHealthPreset(currentSpecialHealthVote(player), decrement);
        specialHealthVotes.put(player.getUniqueId(), next);
        player.sendMessage(ChatColor.LIGHT_PURPLE + text(player, "special_match.vote.updated", text(player, "menu.button.health"), healthPresetLabel(player, next)));
    }

    private void updateSpecialBooleanVote(Player player, Map<UUID, Boolean> voteMap, boolean fallback, String label) {
        if (player == null) {
            return;
        }
        if (!preMatchSpecialVotePhase) {
            player.sendMessage(ChatColor.RED + text(player, "special_match.vote.inactive"));
            return;
        }
        boolean next = !voteMap.getOrDefault(player.getUniqueId(), fallback);
        voteMap.put(player.getUniqueId(), next);
        player.sendMessage(ChatColor.LIGHT_PURPLE + text(player, "special_match.vote.updated", label, stateLabel(player, next)));
    }

    private Map<UUID, Integer> buildPreparedTeamAssignments(List<Player> players) {
        List<Player> orderedPlayers = players == null ? new ArrayList<>() : new ArrayList<>(players);
        orderedPlayers.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int playerCount = orderedPlayers.size();
        int baseSize = playerCount / teamCount;
        int remainder = playerCount % teamCount;
        Map<Integer, Integer> remainingSlots = new LinkedHashMap<>();
        Map<Integer, Boolean> groupedTeams = new HashMap<>();
        for (int team = 1; team <= teamCount; team++) {
            remainingSlots.put(team, baseSize + (team <= remainder ? 1 : 0));
            groupedTeams.put(team, false);
        }

        List<UUID> ungroupedPlayers = new ArrayList<>();
        Map<String, List<UUID>> groupedPlayers = new LinkedHashMap<>();
        for (Player player : orderedPlayers) {
            UUID playerId = player.getUniqueId();
            String joinId = pendingPartyJoinIds.get(playerId);
            if (joinId == null || joinId.isBlank()) {
                ungroupedPlayers.add(playerId);
                continue;
            }
            groupedPlayers.computeIfAbsent(joinId, ignored -> new ArrayList<>()).add(playerId);
        }

        int maxPartySize = maxPartySizeForCurrentLobby();
        List<GroupAssignmentUnit> groupedUnits = new ArrayList<>();
        for (Map.Entry<String, List<UUID>> entry : groupedPlayers.entrySet()) {
            List<UUID> members = entry.getValue();
            for (int index = 0; index < members.size(); index += maxPartySize) {
                int endIndex = Math.min(index + maxPartySize, members.size());
                groupedUnits.add(new GroupAssignmentUnit(entry.getKey(), new ArrayList<>(members.subList(index, endIndex))));
            }
        }
        groupedUnits.sort(
            Comparator.comparingInt((GroupAssignmentUnit unit) -> unit.members().size())
                .reversed()
                .thenComparing(GroupAssignmentUnit::joinId)
        );

        Map<UUID, Integer> assignments = new LinkedHashMap<>();
        for (GroupAssignmentUnit unit : groupedUnits) {
            int team = selectTeamForUnit(remainingSlots, groupedTeams, unit.members().size());
            groupedTeams.put(team, true);
            int remaining = remainingSlots.getOrDefault(team, 0) - unit.members().size();
            remainingSlots.put(team, remaining);
            for (UUID playerId : unit.members()) {
                assignments.put(playerId, team);
            }
        }

        for (UUID playerId : ungroupedPlayers) {
            int team = selectTeamForUnit(remainingSlots, groupedTeams, 1);
            remainingSlots.put(team, remainingSlots.getOrDefault(team, 0) - 1);
            assignments.put(playerId, team);
        }
        return assignments;
    }

    private int selectTeamForUnit(Map<Integer, Integer> remainingSlots, Map<Integer, Boolean> groupedTeams, int size) {
        int selectedTeam = -1;
        boolean selectedHasGroupedMembers = true;
        int selectedRemaining = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Integer> entry : remainingSlots.entrySet()) {
            int team = entry.getKey();
            int remaining = entry.getValue();
            if (remaining < size) {
                continue;
            }
            boolean teamHasGroupedMembers = groupedTeams.getOrDefault(team, false);
            boolean preferThisTeam = !teamHasGroupedMembers;
            boolean preferSelectedTeam = !selectedHasGroupedMembers;
            if (selectedTeam == -1
                || (preferThisTeam && !preferSelectedTeam)
                || (preferThisTeam == preferSelectedTeam && remaining > selectedRemaining)
                || (preferThisTeam == preferSelectedTeam && remaining == selectedRemaining && team < selectedTeam)) {
                selectedTeam = team;
                selectedHasGroupedMembers = teamHasGroupedMembers;
                selectedRemaining = remaining;
            }
        }

        if (selectedTeam != -1) {
            return selectedTeam;
        }

        for (Map.Entry<Integer, Integer> entry : remainingSlots.entrySet()) {
            if (entry.getValue() >= size) {
                return entry.getKey();
            }
        }
        return 1;
    }

    private World ensureLobbyWorld() {
        World world = ensureWorld(lobbyWorldName, World.Environment.NORMAL, true);
        if (world == null) {
            return null;
        }

        prepareLobbyPlatform(world);
        world.setSpawnLocation(0, 101, 0);
        applyLobbyWorldPresentation(world);
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

        deleteWorldFoldersAsync(worlds);
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
        if (!engine.isRunning() && isLobbyCompanionWorldName(worldName)) {
            return resolveLobbySpawn();
        }
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

    private void deleteWorldFoldersAsync(Collection<String> worldNames) {
        if (worldNames == null || worldNames.isEmpty()) {
            return;
        }
        List<File> folders = worldNames.stream()
            .filter(Objects::nonNull)
            .map(name -> new File(Bukkit.getWorldContainer(), name))
            .toList();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            for (File folder : folders) {
                deleteRecursively(folder);
            }
        });
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

    private void cleanupLobbyCompanionWorlds() {
        if (engine.isRunning()) {
            return;
        }
        List<String> worldsToDelete = new ArrayList<>();
        for (String worldName : List.of(lobbyNetherWorldName(), lobbyEndWorldName())) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                for (Player player : new ArrayList<>(world.getPlayers())) {
                    teleportToLobby(player);
                    sendLobbyGuide(player);
                }
                if (!Bukkit.unloadWorld(world, false)) {
                    getLogger().warning("Failed to unload lobby companion world: " + worldName);
                    continue;
                }
            }
            worldsToDelete.add(worldName);
        }
        if (!worldsToDelete.isEmpty()) {
            deleteWorldFoldersAsync(worldsToDelete);
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

        deleteWorldFoldersAsync(List.of(worldName));
    }

    private void loadLanguage() {
        Path baseDirectory = getDataFolder().toPath();
        refreshAvailableLanguageCodes(baseDirectory.resolve("lang"));
        this.configuredLanguageCode = readConfiguredLanguageCode(baseDirectory.resolve("language.properties"));
        if (!availableLanguageCodes.contains(this.configuredLanguageCode)) {
            this.configuredLanguageCode = availableLanguageCodes.isEmpty() ? TeamLifeBindLanguage.DEFAULT_LANGUAGE : availableLanguageCodes.getFirst();
        }
        languageCache.clear();
        this.language = resolveLanguageForCode(this.configuredLanguageCode);
        loadPlayerLanguageCodes(baseDirectory.resolve(PLAYER_LANGUAGE_CONFIG_FILE));
    }

    private void handleReadyStateChange(Player player, boolean ready) {
        if (language == null) {
            return;
        }
        if (engine.isRunning()) {
            player.sendMessage(ChatColor.RED + text(player, "ready.in_match"));
            return;
        }

        if (ready) {
            if (!readyPlayers.add(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + text(player, "ready.already"));
                return;
            }
            broadcastColoredMessage(ChatColor.AQUA, viewer -> text(viewer, "ready.marked", player.getName()));
        } else {
            if (!readyPlayers.remove(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + text(player, "ready.not_marked"));
                return;
            }
            broadcastColoredMessage(ChatColor.YELLOW, viewer -> text(viewer, "ready.unmarked", player.getName()));
        }

        evaluateReadyCountdown();
    }

    public void openLobbyMenu(Player player) {
        if (player == null) {
            return;
        }
        player.openInventory(createLobbyMenuInventory(player));
    }

    public void openDevMenu(Player player) {
        if (player == null) {
            return;
        }
        player.openInventory(createDevMenuInventory(player));
    }

    public boolean isLobbyMenuTitle(String title) {
        if (title == null) {
            return false;
        }
        return matchesLocalizedMenuTitle(title, "menu.inventory.title");
    }

    public boolean isDevMenuTitle(String title) {
        if (title == null) {
            return false;
        }
        return matchesLocalizedMenuTitle(title, "dev.menu.title");
    }

    private boolean matchesLocalizedMenuTitle(String title, String key) {
        String plainTitle = ChatColor.stripColor(title);
        if (plainTitle == null) {
            return false;
        }
        for (String code : availableLanguageCodes) {
            String localized = ChatColor.stripColor(ChatColor.DARK_AQUA + resolveLanguageForCode(code).text(key));
            if (plainTitle.equals(localized)) {
                return true;
            }
        }
        String fallback = ChatColor.stripColor(ChatColor.DARK_AQUA + text(key));
        return plainTitle.equals(fallback);
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
            case MENU_ACTION_STATUS -> player.sendMessage(statusText(player));
            case MENU_ACTION_HELP -> player.performCommand("tlb help");
            case MENU_ACTION_LANGUAGE -> {
                if (cycleLanguage(player, decrement)) {
                    player.sendMessage(ChatColor.GREEN + text(player, "command.language.updated", effectiveLanguageCode(player)));
                } else {
                    player.sendMessage(ChatColor.RED + text(player, "command.language.invalid", availableLanguageSummary()));
                }
            }
            case MENU_ACTION_SPECIAL_HEALTH -> updateSpecialHealthVote(player, decrement);
            case MENU_ACTION_SPECIAL_HEALTH_SYNC -> updateSpecialBooleanVote(
                player,
                specialHealthSyncVotes,
                healthSyncEnabled,
                text(player, "menu.button.health_sync")
            );
            case MENU_ACTION_SPECIAL_NORESPAWN -> updateSpecialBooleanVote(
                player,
                specialNoRespawnVotes,
                noRespawnEnabled,
                text(player, "menu.button.norespawn")
            );
            case MENU_ACTION_SPECIAL_AUTO_PORTALS -> updateSpecialBooleanVote(
                player,
                specialAutoPortalVotes,
                autoNetherPortalsEnabled,
                text(player, "menu.button.auto_portals")
            );
            case MENU_ACTION_TEAMS -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else if (hasActiveMatchSession()) {
                    player.sendMessage(ChatColor.RED + text(player, "command.team_count.running"));
                } else {
                    int updatedCount = stepTeamCount(teamCount, decrement);
                    setTeamCount(updatedCount);
                    player.sendMessage(ChatColor.GREEN + text(player, "command.team_count.updated", updatedCount));
                }
            }
            case MENU_ACTION_HEALTH -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else if (hasActiveMatchSession()) {
                    player.sendMessage(ChatColor.RED + text(player, "command.health.running"));
                } else {
                    HealthPreset next = stepHealthPreset(healthPreset, decrement);
                    setHealthPreset(next);
                    player.sendMessage(ChatColor.GREEN + text(player, "command.health.updated", healthPresetLabel(player, next)));
                }
            }
            case MENU_ACTION_HEALTH_SYNC -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else {
                    boolean enabled = !healthSyncEnabled;
                    setHealthSyncEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text(player, enabled ? "command.healthsync.enabled" : "command.healthsync.disabled"));
                }
            }
            case MENU_ACTION_NORESPAWN -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else {
                    boolean enabled = !noRespawnEnabled;
                    setNoRespawnEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text(player, enabled ? "command.norespawn.enabled" : "command.norespawn.disabled"));
                }
            }
            case MENU_ACTION_ANNOUNCE_TEAMS -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else {
                    boolean enabled = !announceTeamAssignment;
                    setAnnounceTeamAssignment(enabled);
                    player.sendMessage(ChatColor.GREEN + text(player, "config.announce_team_assignment.updated", stateLabel(player, enabled)));
                }
            }
            case MENU_ACTION_SCOREBOARD -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else {
                    boolean enabled = !scoreboardEnabled;
                    setBuiltinScoreboardEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text(player, "config.scoreboard.updated", stateLabel(player, enabled)));
                }
            }
            case MENU_ACTION_TAB -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else {
                    boolean enabled = !tabEnabled;
                    setTabEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text(player, "config.tab.updated", stateLabel(player, enabled)));
                }
            }
            case MENU_ACTION_ADVANCEMENTS -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else {
                    boolean enabled = !advancementsEnabled;
                    setAdvancementsEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text(player, "config.advancements.updated", stateLabel(player, enabled)));
                }
            }
            case MENU_ACTION_AUTO_PORTALS -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else {
                    boolean enabled = !autoNetherPortalsEnabled;
                    setAutoNetherPortalsEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text(player, "config.auto_portals.updated", stateLabel(player, enabled)));
                }
            }
            case MENU_ACTION_STRONGHOLD_HINTS -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else {
                    boolean enabled = !anonymousStrongholdHintsEnabled;
                    setAnonymousStrongholdHintsEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text(player, "config.stronghold_hints.updated", stateLabel(player, enabled)));
                }
            }
            case MENU_ACTION_START -> {
                if (player.hasPermission("teamlifebind.admin")) {
                    startMatch(player);
                } else {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                }
            }
            case MENU_ACTION_STOP -> {
                if (player.hasPermission("teamlifebind.admin")) {
                    stopMatch(player);
                } else {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                }
            }
            case MENU_ACTION_RELOAD -> {
                if (player.hasPermission("teamlifebind.admin")) {
                    reloadPluginConfig(player);
                } else {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                }
            }
            case MENU_ACTION_END_TOTEMS -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else {
                    boolean enabled = !endTotemRestrictionEnabled;
                    setEndTotemRestrictionEnabled(enabled);
                    player.sendMessage(ChatColor.GREEN + text(player, "config.end_totems.updated", stateLabel(player, enabled)));
                }
            }
            case MENU_ACTION_SUPPLY_DROPS -> {
                if (!player.hasPermission("teamlifebind.admin")) {
                    player.sendMessage(ChatColor.RED + text(player, "command.no_permission"));
                } else {
                    int updated = stepSupplyDropCount(supplyDropCount, decrement);
                    setSupplyDropCount(updated);
                    player.sendMessage(ChatColor.GREEN + text(player, "config.supply_drops.updated", updated));
                }
            }
            default -> {
                return;
            }
        }

        if (MENU_ACTION_LANGUAGE.equals(action)) {
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) {
                openLobbyMenu(player);
            }
        });
    }

    public void handleDevMenuClick(Player player, ItemStack clickedItem) {
        if (player == null || clickedItem == null || clickedItem.getType() == Material.AIR || !isDevMenuItem(clickedItem)) {
            return;
        }
        ItemStack granted = localizedDevMenuGrant(player, clickedItem);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(granted);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private ItemStack localizedDevMenuGrant(Player player, ItemStack clickedItem) {
        if (isTeamBedItem(clickedItem)) {
            return createTeamBedItem(player, resolveTeamBedOwnerTeam(clickedItem));
        }
        if (isTrackingWheelItem(clickedItem)) {
            return createTrackingWheelItem(player);
        }
        if (isDeathExemptionTotemItem(clickedItem)) {
            return createDeathExemptionTotemItem(player);
        }
        if (isLifeCursePotionItem(clickedItem)) {
            return createLifeCursePotionItem(player);
        }
        if (isLobbyMenuItem(clickedItem)) {
            return createLobbyMenuItem(player);
        }
        if (isSupplyBoatItem(clickedItem)) {
            return createSupplyBoatItem();
        }
        if (isProtectedLobbyItem(clickedItem) && clickedItem.getType() == Material.WRITTEN_BOOK) {
            ItemMeta meta = clickedItem.getItemMeta();
            String title = meta instanceof BookMeta bookMeta ? bookMeta.getTitle() : null;
            if (Objects.equals(title, text(player, "book.guide.title"))) {
                return createGuideBookItem(player);
            }
            return createRecipeBookItem(player);
        }
        return clickedItem.clone();
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
            sendActionBar(attacker, ChatColor.RED, text(attacker, isInLobby(attacker) ? "lobby.pvp_denied" : "combat.friendly_fire_blocked"));
        }
    }

    public boolean tryUseTeamTotem(Player target, double finalDamage) {
        if (target == null || !target.isOnline() || finalDamage <= 0.0D) {
            return false;
        }
        if ((target.getHealth() + target.getAbsorptionAmount()) - finalDamage > HEALTH_SYNC_EPSILON) {
            return false;
        }

        if (engine.isRunning() && !shouldExcludeFromSharedHealth(target)) {
            Integer team = engine.teamForPlayer(target.getUniqueId());
            if (team != null) {
                List<Player> players = resolveTotemEligibleTeamPlayers(team);
                if (!players.isEmpty()) {
                    Player totemOwner = null;
                    int totemSlot = -1;
                    boolean upgradedTotem = false;
                    boolean inEnd = target.getWorld().getEnvironment() == World.Environment.THE_END;
                    for (Player player : players) {
                        int candidateSlot = findDeathExemptionTotemSlot(player);
                        if (candidateSlot >= 0) {
                            totemOwner = player;
                            totemSlot = candidateSlot;
                            upgradedTotem = true;
                            break;
                        }
                        if (inEnd && endTotemRestrictionEnabled) {
                            continue;
                        }
                        candidateSlot = findTotemSlot(player);
                        if (candidateSlot >= 0) {
                            totemOwner = player;
                            totemSlot = candidateSlot;
                            break;
                        }
                    }
                    if (totemOwner != null) {
                        consumeInventoryItem(totemOwner, totemSlot);
                        clearTeamSharedHealthTracking(team);
                        teamLastDamageTicks.put(team, sharedStateTick);
                        for (Player player : players) {
                            applyTeamTotemRescue(player, upgradedTotem, upgradedTotem && !inEnd);
                        }
                        return true;
                    }
                }
            }
        }

        return tryUseStandaloneDeathExemptionTotem(target);
    }

    private boolean tryUseStandaloneDeathExemptionTotem(Player target) {
        int totemSlot = findDeathExemptionTotemSlot(target);
        if (totemSlot < 0) {
            return false;
        }
        consumeInventoryItem(target, totemSlot);
        boolean inEnd = target.getWorld().getEnvironment() == World.Environment.THE_END;
        applyTeamTotemRescue(target, true, !inEnd);
        return true;
    }

    public void handleMilkBucketConsumed(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Integer team = engine.teamForPlayer(player.getUniqueId());
        if (team == null) {
            lifeCurseUntil.remove(player.getUniqueId());
            clearPotionEffects(player);
            return;
        }
        lifeCurseTeamImmunityUntil.remove(team);
        for (Player teammate : resolveTotemEligibleTeamPlayers(team)) {
            lifeCurseUntil.remove(teammate.getUniqueId());
            clearPotionEffects(teammate);
        }
    }

    public void handleLifeCursePotionConsumed(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        long expiresAt = sharedStateTick + LIFE_CURSE_DURATION_TICKS;
        Integer team = engine.teamForPlayer(player.getUniqueId());
        if (effectiveHealthSyncEnabled() && team != null) {
            lifeCurseTeamImmunityUntil.put(team, expiresAt);
            player.sendMessage(ChatColor.DARK_RED + text(player, "life_curse.immunity_enabled"));
            return;
        }
        lifeCurseUntil.put(player.getUniqueId(), expiresAt);
        player.sendMessage(ChatColor.DARK_RED + text(player, "life_curse.enabled"));
    }

    public void handleLifeCurseAttack(Player attacker, Player target, double finalDamage) {
        if (attacker == null || target == null || finalDamage <= 0.0D || !engine.isRunning()) {
            return;
        }
        if (lifeCurseDamageGuard.contains(target.getUniqueId())) {
            return;
        }
        Long expiresAt = lifeCurseUntil.get(attacker.getUniqueId());
        if (expiresAt == null || expiresAt < sharedStateTick) {
            return;
        }
        Integer attackerTeam = engine.teamForPlayer(attacker.getUniqueId());
        Integer targetTeam = engine.teamForPlayer(target.getUniqueId());
        if (attackerTeam == null || targetTeam == null || attackerTeam.equals(targetTeam)) {
            return;
        }

        for (Player teammate : Bukkit.getOnlinePlayers()) {
            if (teammate == null || !teammate.isOnline() || teammate.isDead()) {
                continue;
            }
            if (teammate.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }
            Integer teammateTeam = engine.teamForPlayer(teammate.getUniqueId());
            if (teammateTeam == null || teammateTeam != targetTeam) {
                continue;
            }
            lifeCurseDamageGuard.add(teammate.getUniqueId());
            try {
                teammate.damage(finalDamage, attacker);
            } finally {
                Bukkit.getScheduler().runTask(this, () -> lifeCurseDamageGuard.remove(teammate.getUniqueId()));
            }
        }
    }

    public void notifyEndTotemRestriction(Player player) {
        if (player == null || !player.isOnline() || !engine.isRunning() || !endTotemRestrictionEnabled) {
            return;
        }
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        player.sendMessage(ChatColor.RED + text(player, "end_totem.warning"));
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
            if (stack != null && stack.getType() == Material.TOTEM_OF_UNDYING && stack.getAmount() > 0 && !isDeathExemptionTotemItem(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private int findDeathExemptionTotemSlot(Player player) {
        if (player == null || !player.isOnline()) {
            return -1;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isDeathExemptionTotemItem(stack) && stack.getAmount() > 0) {
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

    private void applyTeamTotemRescue(Player player, boolean upgradedTotem, boolean grantBonusAbsorption) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        double rescuedHealth = upgradedTotem ? DEATH_EXEMPTION_RESCUE_HEALTH : 1.0D;
        player.setHealth(Math.min(maxHealth(player), rescuedHealth));
        player.setAbsorptionAmount(0.0D);
        int regenerationAmplifier = upgradedTotem ? 2 : 1;
        int regenerationTicks = upgradedTotem ? DEATH_EXEMPTION_REGENERATION_TICKS : TOTEM_REGENERATION_TICKS;
        int fireResistanceTicks = upgradedTotem ? DEATH_EXEMPTION_FIRE_RESISTANCE_TICKS : TOTEM_FIRE_RESISTANCE_TICKS;
        int absorptionAmplifier = upgradedTotem ? DEATH_EXEMPTION_ABSORPTION_AMPLIFIER : 1;
        int absorptionTicks = upgradedTotem ? DEATH_EXEMPTION_ABSORPTION_TICKS : TOTEM_ABSORPTION_TICKS;
        player.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, regenerationTicks, regenerationAmplifier, false, true, true));
        player.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, fireResistanceTicks, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, absorptionTicks, absorptionAmplifier, false, true, true));
        if (upgradedTotem) {
            player.addPotionEffect(
                new PotionEffect(
                    org.bukkit.potion.PotionEffectType.RESISTANCE,
                    DEATH_EXEMPTION_RESISTANCE_TICKS,
                    DEATH_EXEMPTION_RESISTANCE_AMPLIFIER,
                    false,
                    true,
                    true
                )
            );
        }
        if (grantBonusAbsorption) {
            player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), DEATH_EXEMPTION_BONUS_ABSORPTION));
        }
        player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), upgradedTotem ? DEATH_EXEMPTION_NO_DAMAGE_TICKS : 40));
        player.playEffect(EntityEffect.TOTEM_RESURRECT);
        playSound(player, Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
    }

    private Inventory createLobbyMenuInventory(Player player) {
        Inventory inventory = Bukkit.createInventory(null, LOBBY_MENU_SIZE, ChatColor.DARK_AQUA + text(player, "menu.inventory.title"));
        boolean ready = readyPlayers.contains(player.getUniqueId());
        boolean admin = player.hasPermission("teamlifebind.admin");
        boolean specialVotePhase = preMatchSpecialVotePhase;
        String stateKey = currentLobbyStateKey();
        decorateLobbyMenuFrame(inventory);

        inventory.setItem(
            LOBBY_MENU_READY_SLOT,
            createMenuButton(
                ready ? MENU_ACTION_UNREADY : MENU_ACTION_READY,
                ready ? Material.RED_WOOL : Material.LIME_WOOL,
                ready ? text(player, "menu.button.unready") : text(player, "menu.button.ready"),
                List.of(ChatColor.GRAY + text(player, "menu.info.status", text(player, stateKey)))
            )
        );
        inventory.setItem(
            LOBBY_MENU_STATUS_SLOT,
            createMenuButton(
                MENU_ACTION_STATUS,
                Material.CLOCK,
                text(player, "menu.button.status"),
                List.of(
                    ChatColor.YELLOW + text(player, "menu.info.status", text(player, stateKey)),
                    ChatColor.AQUA + text(player, "menu.info.team", resolveMenuTeamLabel(player))
                )
            )
        );
        inventory.setItem(
            LOBBY_MENU_HELP_SLOT,
            createMenuButton(
                MENU_ACTION_HELP,
                Material.BOOK,
                text(player, "menu.button.help"),
                List.of(ChatColor.GRAY + text(player, "menu.info.tip"))
            )
        );
        inventory.setItem(
            LOBBY_MENU_LANGUAGE_SLOT,
            createMenuButton(
                MENU_ACTION_LANGUAGE,
                Material.GLOBE_BANNER_PATTERN,
                text(player, "menu.button.language"),
                List.of(
                    ChatColor.YELLOW + text(player, "menu.value.language", effectiveLanguageCode(player)),
                    ChatColor.GRAY + text(player, "menu.info.language_options", availableLanguageSummary()),
                    ChatColor.GRAY + text(player, "menu.action.adjust_cycle")
                )
            )
        );

        if (specialVotePhase) {
            inventory.setItem(
                LOBBY_MENU_HEALTH_SLOT,
                createMenuButton(
                    MENU_ACTION_SPECIAL_HEALTH,
                    Material.GOLDEN_APPLE,
                    text(player, "menu.button.health"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.health", healthPresetLabel(player, currentSpecialHealthVote(player))),
                        ChatColor.GRAY + text(player, "menu.action.adjust_cycle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_HEALTH_SYNC_SLOT,
                createMenuButton(
                    MENU_ACTION_SPECIAL_HEALTH_SYNC,
                    currentSpecialHealthSyncVote(player) ? Material.LIME_DYE : Material.GRAY_DYE,
                    text(player, "menu.button.health_sync"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, currentSpecialHealthSyncVote(player))),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_NORESPAWN_SLOT,
                createMenuButton(
                    MENU_ACTION_SPECIAL_NORESPAWN,
                    currentSpecialNoRespawnVote(player) ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                    text(player, "menu.button.norespawn"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, currentSpecialNoRespawnVote(player))),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_AUTO_PORTALS_SLOT,
                createMenuButton(
                    MENU_ACTION_SPECIAL_AUTO_PORTALS,
                    currentSpecialAutoPortalVote(player) ? Material.OBSIDIAN : Material.GRAY_DYE,
                    text(player, "menu.button.auto_portals"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, currentSpecialAutoPortalVote(player))),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
        } else if (admin && !isPreMatchPhaseActive()) {
            inventory.setItem(
                LOBBY_MENU_HEALTH_SYNC_SLOT,
                createMenuButton(
                    MENU_ACTION_HEALTH_SYNC,
                    healthSyncEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                    text(player, "menu.button.health_sync"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, healthSyncEnabled)),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_TEAMS_SLOT,
                createMenuButton(
                    MENU_ACTION_TEAMS,
                    Material.CYAN_WOOL,
                    text(player, "menu.button.teams"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.team_count", teamCount),
                        ChatColor.GRAY + text(player, "menu.action.adjust_number")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_HEALTH_SLOT,
                createMenuButton(
                    MENU_ACTION_HEALTH,
                    Material.GOLDEN_APPLE,
                    text(player, "menu.button.health"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.health", healthPresetLabel(player, healthPreset)),
                        ChatColor.GRAY + text(player, "menu.action.adjust_cycle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_NORESPAWN_SLOT,
                createMenuButton(
                    MENU_ACTION_NORESPAWN,
                    noRespawnEnabled ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                    text(player, "menu.button.norespawn"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, noRespawnEnabled)),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_ANNOUNCE_TEAMS_SLOT,
                createMenuButton(
                    MENU_ACTION_ANNOUNCE_TEAMS,
                    announceTeamAssignment ? Material.NAME_TAG : Material.PAPER,
                    text(player, "menu.button.announce_teams"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, announceTeamAssignment)),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_SCOREBOARD_SLOT,
                createMenuButton(
                    MENU_ACTION_SCOREBOARD,
                    scoreboardEnabled ? Material.MAP : Material.GRAY_DYE,
                    text(player, "menu.button.scoreboard"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, scoreboardEnabled)),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_TAB_SLOT,
                createMenuButton(
                    MENU_ACTION_TAB,
                    tabEnabled ? Material.COMPASS : Material.GRAY_DYE,
                    text(player, "menu.button.tab"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, tabEnabled)),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_ADVANCEMENTS_SLOT,
                createMenuButton(
                    MENU_ACTION_ADVANCEMENTS,
                    advancementsEnabled ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                    text(player, "menu.button.advancements"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, advancementsEnabled)),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_AUTO_PORTALS_SLOT,
                createMenuButton(
                    MENU_ACTION_AUTO_PORTALS,
                    autoNetherPortalsEnabled ? Material.OBSIDIAN : Material.GRAY_DYE,
                    text(player, "menu.button.auto_portals"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, autoNetherPortalsEnabled)),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_STRONGHOLD_HINTS_SLOT,
                createMenuButton(
                    MENU_ACTION_STRONGHOLD_HINTS,
                    anonymousStrongholdHintsEnabled ? Material.ENDER_EYE : Material.GRAY_DYE,
                    text(player, "menu.button.stronghold_hints"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, anonymousStrongholdHintsEnabled)),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_END_TOTEMS_SLOT,
                createMenuButton(
                    MENU_ACTION_END_TOTEMS,
                    endTotemRestrictionEnabled ? Material.END_STONE : Material.GRAY_DYE,
                    text(player, "menu.button.end_totems"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.toggle", stateLabel(player, endTotemRestrictionEnabled)),
                        ChatColor.GRAY + text(player, "menu.action.toggle")
                    )
                )
            );
            inventory.setItem(
                LOBBY_MENU_SUPPLY_DROPS_SLOT,
                createMenuButton(
                    MENU_ACTION_SUPPLY_DROPS,
                    supplyDropCount > 0 ? Material.BARREL : Material.GRAY_DYE,
                    text(player, "menu.button.supply_drops"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.value.supply_drops", supplyDropCount),
                        ChatColor.GRAY + text(player, "menu.action.adjust_number")
                    )
                )
            );
        }

        if (admin) {
            inventory.setItem(
                LOBBY_MENU_START_STOP_SLOT,
                createMenuButton(
                    hasActiveMatchSession() ? MENU_ACTION_STOP : MENU_ACTION_START,
                    hasActiveMatchSession() ? Material.BARRIER : Material.DIAMOND_SWORD,
                    hasActiveMatchSession() ? text(player, "menu.button.stop") : text(player, "menu.button.start"),
                    List.of(ChatColor.GRAY + text(player, "menu.info.status", text(player, stateKey)))
                )
            );
            inventory.setItem(
                LOBBY_MENU_RELOAD_SLOT,
                createMenuButton(
                    MENU_ACTION_RELOAD,
                    Material.REPEATER,
                    text(player, "menu.button.reload"),
                    List.of(ChatColor.GRAY + text(player, "command.reload.success"))
                )
            );
        }

        if (specialVotePhase) {
            inventory.setItem(
                LOBBY_MENU_INFO_SLOT,
                createMenuInfoCard(
                    text(player, "special_match.phase.title"),
                    List.of(
                        ChatColor.LIGHT_PURPLE + text(player, "menu.info.special_vote_phase", preMatchRemainingSeconds),
                        ChatColor.YELLOW + text(player, "menu.value.health", healthPresetLabel(player, currentSpecialHealthVote(player))),
                        ChatColor.GRAY + text(player, "menu.info.special_vote_tip")
                    )
                )
            );
        } else if (preMatchTeamModePhase) {
            String joinId = pendingPartyJoinIds.get(player.getUniqueId());
            inventory.setItem(
                LOBBY_MENU_INFO_SLOT,
                createMenuInfoCard(
                    text(player, "team_mode.phase.title"),
                    List.of(
                        ChatColor.AQUA + text(player, "menu.info.team_mode_phase", preMatchRemainingSeconds),
                        ChatColor.YELLOW + text(player, "menu.info.team_mode_join", joinId == null ? text(player, "scoreboard.value.none") : joinId),
                        ChatColor.GRAY + text(player, "menu.info.team_mode_command")
                    )
                )
            );
        } else if (admin) {
            inventory.setItem(
                LOBBY_MENU_INFO_SLOT,
                createMenuInfoCard(
                    text(player, "menu.inventory.title"),
                    List.of(
                        ChatColor.YELLOW + text(player, "menu.info.admin_tip"),
                        ChatColor.GRAY + text(player, "menu.action.adjust_number"),
                        ChatColor.GRAY + text(player, "menu.action.adjust_cycle")
                    )
                )
            );
        } else {
            inventory.setItem(
                LOBBY_MENU_INFO_SLOT,
                createMenuInfoCard(
                    text(player, "menu.inventory.title"),
                    List.of(
                        ChatColor.GRAY + text(player, "menu.info.tip"),
                        ChatColor.YELLOW + text(player, "menu.info.admin_only")
                    )
                )
            );
        }
        return inventory;
    }

    private Inventory createDevMenuInventory(Player player) {
        Inventory inventory = Bukkit.createInventory(null, DEV_MENU_SIZE, ChatColor.DARK_AQUA + text(player, "dev.menu.title"));
        decorateLobbyMenuFrame(inventory);
        inventory.setItem(DEV_MENU_TEAM_BED_SLOT, createTeamBedItem(player, null));
        inventory.setItem(DEV_MENU_TRACKING_WHEEL_SLOT, createTrackingWheelItem(player));
        inventory.setItem(DEV_MENU_DEATH_EXEMPTION_SLOT, createDeathExemptionTotemItem(player));
        inventory.setItem(DEV_MENU_LIFE_CURSE_SLOT, createLifeCursePotionItem(player));
        inventory.setItem(DEV_MENU_SUPPLY_BOAT_SLOT, createSupplyBoatItem());
        inventory.setItem(DEV_MENU_MENU_COMPASS_SLOT, createLobbyMenuItem(player));
        inventory.setItem(DEV_MENU_GUIDE_BOOK_SLOT, createGuideBookItem(player));
        inventory.setItem(DEV_MENU_RECIPE_BOOK_SLOT, createRecipeBookItem(player));
        inventory.setItem(
            DEV_MENU_INFO_SLOT,
            createMenuInfoCard(
                text(player, "dev.menu.info.title"),
                List.of(
                    ChatColor.YELLOW + text(player, "dev.menu.info.line1"),
                    ChatColor.GRAY + text(player, "dev.menu.info.line2"),
                    ChatColor.GRAY + text(player, "dev.menu.info.line3")
                )
            )
        );
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
            meta.setLore(new ArrayList<>(lore));
        }
        meta.getPersistentDataContainer().set(lobbyMenuActionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMenuInfoCard(String label, List<String> lore) {
        ItemStack item = new ItemStack(Material.LECTERN, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.AQUA + label);
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(new ArrayList<>(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    private void decorateLobbyMenuFrame(Inventory inventory) {
        ItemStack frame = createMenuFrameItem();
        for (int slot = 0; slot < LOBBY_MENU_SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) {
                inventory.setItem(slot, frame.clone());
            }
        }
    }

    private ItemStack createMenuFrameItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.DARK_GRAY + " ");
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

    private boolean isDevMenuItem(ItemStack stack) {
        return isTeamBedItem(stack)
            || isTrackingWheelItem(stack)
            || isDeathExemptionTotemItem(stack)
            || isLifeCursePotionItem(stack)
            || isSupplyBoatItem(stack)
            || isProtectedLobbyItem(stack);
    }

    private String resolveMenuTeamLabel(Player player) {
        Integer team = player != null ? engine.teamForPlayer(player.getUniqueId()) : null;
        return team != null ? teamLabel(player, team) : text(player, "menu.info.no_team");
    }

    private TeamLifeBindLanguage resolveLanguage(Player player) {
        return resolveLanguageForCode(effectiveLanguageCode(player));
    }

    private TeamLifeBindLanguage resolveLanguageForCode(String languageCode) {
        String normalized = normalizeLanguageCode(languageCode);
        if (normalized == null || !availableLanguageCodes.contains(normalized)) {
            normalized = configuredLanguageCode != null ? configuredLanguageCode : TeamLifeBindLanguage.DEFAULT_LANGUAGE;
        }
        TeamLifeBindLanguage cached = languageCache.get(normalized);
        if (cached != null) {
            return cached;
        }
        TeamLifeBindLanguage loaded = TeamLifeBindLanguage.load(getDataFolder().toPath(), normalized, getLogger()::warning);
        languageCache.put(normalized, loaded);
        return loaded;
    }

    private void loadPlayerLanguageCodes(Path configPath) {
        playerLanguageCodes.clear();
        if (configPath == null || !Files.exists(configPath)) {
            return;
        }

        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException ex) {
            getLogger().warning("Failed to read player language config " + configPath + ": " + ex.getMessage());
            return;
        }

        boolean changed = false;
        for (String rawPlayerId : properties.stringPropertyNames()) {
            UUID playerId;
            try {
                playerId = UUID.fromString(rawPlayerId);
            } catch (IllegalArgumentException ex) {
                changed = true;
                continue;
            }

            String normalized = normalizeLanguageCode(properties.getProperty(rawPlayerId));
            if (normalized == null || normalized.equals(configuredLanguageCode) || !availableLanguageCodes.contains(normalized)) {
                changed = true;
                continue;
            }
            playerLanguageCodes.put(playerId, normalized);
        }

        if (changed) {
            savePlayerLanguageCodes();
        }
    }

    private boolean savePlayerLanguageCodes() {
        Path configPath = getDataFolder().toPath().resolve(PLAYER_LANGUAGE_CONFIG_FILE);
        Properties properties = new Properties();
        for (Map.Entry<UUID, String> entry : playerLanguageCodes.entrySet()) {
            properties.setProperty(entry.getKey().toString(), entry.getValue());
        }
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            properties.store(writer, "TeamLifeBind per-player language overrides");
            return true;
        } catch (IOException ex) {
            getLogger().warning("Failed to write player language config " + configPath + ": " + ex.getMessage());
            return false;
        }
    }

    private void refreshAvailableLanguageCodes(Path languageDirectory) {
        availableLanguageCodes.clear();
        availableLanguageCodes.add(TeamLifeBindLanguage.DEFAULT_LANGUAGE);
        if (languageDirectory != null && Files.isDirectory(languageDirectory)) {
            try (var paths = Files.list(languageDirectory)) {
                paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".properties"))
                    .map(name -> normalizeLanguageCode(name.substring(0, name.length() - ".properties".length())))
                    .filter(Objects::nonNull)
                    .forEach(code -> {
                        if (!availableLanguageCodes.contains(code)) {
                            availableLanguageCodes.add(code);
                        }
                    });
            } catch (IOException ex) {
                getLogger().warning("Failed to list available languages under " + languageDirectory + ": " + ex.getMessage());
            }
        }
        availableLanguageCodes.sort((left, right) -> {
            if (TeamLifeBindLanguage.DEFAULT_LANGUAGE.equals(left)) {
                return TeamLifeBindLanguage.DEFAULT_LANGUAGE.equals(right) ? 0 : -1;
            }
            if (TeamLifeBindLanguage.DEFAULT_LANGUAGE.equals(right)) {
                return 1;
            }
            return left.compareToIgnoreCase(right);
        });
    }

    private String readConfiguredLanguageCode(Path configPath) {
        Properties properties = new Properties();
        if (configPath == null || !Files.exists(configPath)) {
            return TeamLifeBindLanguage.DEFAULT_LANGUAGE;
        }
        try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException ex) {
            getLogger().warning("Failed to read language config " + configPath + ": " + ex.getMessage());
            return TeamLifeBindLanguage.DEFAULT_LANGUAGE;
        }
        String normalized = normalizeLanguageCode(properties.getProperty("language"));
        return normalized != null ? normalized : TeamLifeBindLanguage.DEFAULT_LANGUAGE;
    }

    private boolean writeConfiguredLanguageCode(String languageCode) {
        Path configPath = getDataFolder().toPath().resolve("language.properties");
        Properties properties = new Properties();
        if (Files.exists(configPath)) {
            try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException ex) {
                getLogger().warning("Failed to read language config " + configPath + ": " + ex.getMessage());
            }
        }
        properties.setProperty("language", languageCode);
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            properties.store(writer, "TeamLifeBind language selector");
            return true;
        } catch (IOException ex) {
            getLogger().warning("Failed to write language config " + configPath + ": " + ex.getMessage());
            return false;
        }
    }

    private String normalizeLanguageCode(String rawLanguageCode) {
        if (rawLanguageCode == null || rawLanguageCode.isBlank()) {
            return null;
        }
        return rawLanguageCode.trim().toLowerCase().replace('-', '_');
    }

    private void refreshLocalizedUiState() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshLocalizedUiState(player);
        }
    }

    private void refreshLocalizedUiState(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (scoreboardEnabled) {
            applyBuiltInScoreboard(player);
        }
        if (tabEnabled) {
            applyTabListDisplay(player);
        }
        if (isInLobby(player)) {
            giveLobbyItems(player);
        }
        String openTitle = player.getOpenInventory().getTitle();
        boolean reopenLobbyMenu = isLobbyMenuTitle(openTitle);
        boolean reopenDevMenu = !reopenLobbyMenu && isDevMenuTitle(openTitle);
        if (!reopenLobbyMenu && !reopenDevMenu) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (reopenLobbyMenu) {
                player.openInventory(createLobbyMenuInventory(player));
            } else if (reopenDevMenu) {
                player.openInventory(createDevMenuInventory(player));
            }
        });
    }

    private void broadcastImportantNotice(String title, String subtitle) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            showTitle(player, trimScoreboardText(title), trimScoreboardText(subtitle));
        }
    }

    private void broadcastImportantNotice(Function<Player, String> titleFactory, Function<Player, String> subtitleFactory) {
        if (titleFactory == null || subtitleFactory == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            showTitle(player, trimScoreboardText(titleFactory.apply(player)), trimScoreboardText(subtitleFactory.apply(player)));
        }
    }

    private void notifyTeamWiped(int team, Collection<UUID> victims) {
        if (victims == null || victims.isEmpty()) {
            return;
        }
        for (UUID victimId : victims) {
            Player player = Bukkit.getPlayer(victimId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            showTitle(player, trimScoreboardText(text(player, "match.notice.team_wipe.title")), trimScoreboardText(text(player, "match.notice.team_wipe.subtitle")));
            player.sendMessage(ChatColor.RED + text(player, "match.life_bind_wipe"));
        }
    }

    private List<String> buildSidebarLines(Player viewer) {
        TeamLifeBindMatchSnapshot matchSnapshot = getMatchSnapshot();
        TeamLifeBindPlayerSnapshot playerSnapshot = viewer != null ? getPlayerSnapshot(viewer.getUniqueId()) : getPlayerSnapshot(null);
        List<String> lines = new ArrayList<>();
        if (!matchSnapshot.running()) {
            lines.add(ChatColor.YELLOW + text(viewer, "scoreboard.line.state", text(viewer, currentLobbyStateKey())));
            lines.add(ChatColor.GREEN + text(viewer, "scoreboard.line.ready", matchSnapshot.readyPlayers().size(), matchSnapshot.onlinePlayerCount()));
            if (readyCountdownRemainingSeconds >= 0) {
                lines.add(ChatColor.GOLD + text(viewer, "scoreboard.line.countdown", readyCountdownRemainingSeconds));
            } else if (preMatchRemainingSeconds >= 0) {
                lines.add(ChatColor.GOLD + text(viewer, "scoreboard.line.countdown", preMatchRemainingSeconds));
            }
            if (preMatchTeamModePhase && viewer != null) {
                String joinId = pendingPartyJoinIds.get(viewer.getUniqueId());
                lines.add(ChatColor.AQUA + text(viewer, "scoreboard.line.join_id", joinId == null ? text(viewer, "scoreboard.value.none") : joinId));
            }
            lines.add(ChatColor.AQUA + text(viewer, "scoreboard.line.teams", matchSnapshot.configuredTeamCount()));
            lines.add(ChatColor.RED + text(viewer, "scoreboard.line.health", healthPresetLabel(viewer, matchSnapshot.healthPreset())));
            lines.add(ChatColor.LIGHT_PURPLE + text(viewer, "scoreboard.line.norespawn", stateLabel(viewer, matchSnapshot.noRespawnEnabled())));
            return lines;
        }

        Integer playerTeam = playerSnapshot.team();
        boolean hasBed = playerTeam != null && matchSnapshot.bedTeams().contains(playerTeam);
        lines.add(ChatColor.YELLOW + text(viewer, "scoreboard.line.state", text(viewer, "scoreboard.state.running")));
        lines.add(ChatColor.AQUA + text(viewer, "scoreboard.line.team", playerTeam != null ? teamLabel(viewer, playerTeam) : text(viewer, "scoreboard.value.none")));
        lines.add(ChatColor.GREEN + text(viewer, "scoreboard.line.team_bed", playerTeam != null ? bedStatusText(viewer, hasBed) : text(viewer, "scoreboard.value.none")));
        lines.add(ChatColor.GOLD + text(viewer, "scoreboard.line.respawn", respawnStatusText(viewer, playerSnapshot)));
        int observationSecondsRemaining = viewer != null ? pendingMatchObservationSeconds(viewer.getUniqueId()) : -1;
        if (observationSecondsRemaining > 0) {
            lines.add(ChatColor.YELLOW + text(viewer, "scoreboard.line.observe", text(viewer, "scoreboard.observe.countdown", observationSecondsRemaining)));
        }
        lines.add(ChatColor.RED + text(viewer, "scoreboard.line.health", healthPresetLabel(viewer, matchSnapshot.healthPreset())));
        lines.add(ChatColor.BLUE + text(viewer, "scoreboard.line.teams", matchSnapshot.configuredTeamCount()));
        lines.add(ChatColor.RED + text(viewer, "scoreboard.line.beds", matchSnapshot.bedTeams().size()));
        if (observationSecondsRemaining <= 0) {
            lines.add(ChatColor.DARK_AQUA + text(viewer, "scoreboard.line.dimension", dimensionLabel(viewer)));
        }
        lines.add(ChatColor.LIGHT_PURPLE + text(viewer, "scoreboard.line.norespawn", stateLabel(viewer, matchSnapshot.noRespawnEnabled())));
        return lines;
    }

    private String bedStatusText(Player viewer, boolean placed) {
        return text(viewer, placed ? "scoreboard.bed.present" : "scoreboard.bed.missing");
    }

    private String respawnStatusText(Player viewer, TeamLifeBindPlayerSnapshot playerSnapshot) {
        if (playerSnapshot == null) {
            return text(viewer, "scoreboard.respawn.ready");
        }
        if (playerSnapshot.respawnLocked()) {
            return text(viewer, "scoreboard.respawn.locked");
        }
        if (playerSnapshot.respawnSecondsRemaining() > 0) {
            return text(viewer, "scoreboard.respawn.countdown", playerSnapshot.respawnSecondsRemaining());
        }
        return text(viewer, "scoreboard.respawn.ready");
    }

    private String dimensionLabel(Player viewer) {
        if (viewer == null) {
            return text("scoreboard.dimension.unknown");
        }
        return switch (viewer.getWorld().getEnvironment()) {
            case NETHER -> text(viewer, "scoreboard.dimension.nether");
            case THE_END -> text(viewer, "scoreboard.dimension.end");
            case NORMAL -> text(viewer, "scoreboard.dimension.overworld");
            default -> text(viewer, "scoreboard.dimension.unknown");
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

    private String coloredText(ChatColor color, String message) {
        return (color == null ? "" : color.toString()) + (message == null ? "" : message);
    }

    private void sendActionBar(Player player, ChatColor color, String message) {
        if (player == null || !player.isOnline() || message == null) {
            return;
        }
        serverUiCompat.sendActionBar(player, coloredText(color, message));
    }

    private void broadcastColoredMessage(ChatColor color, String message) {
        if (message == null) {
            return;
        }
        Bukkit.broadcastMessage(coloredText(color, message));
    }

    private void broadcastColoredMessage(ChatColor color, Function<Player, String> messageFactory) {
        if (messageFactory == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            String message = messageFactory.apply(player);
            if (message != null) {
                player.sendMessage(coloredText(color, message));
            }
        }
    }

    private void showTitle(Player player, String title, String subtitle) {
        if (player == null || !player.isOnline()) {
            return;
        }
        serverUiCompat.sendTitle(player, title, subtitle, 10, IMPORTANT_NOTICE_STAY_TICKS, 20);
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
        Location safe = findSafeSurfaceAt(world, x, z);
        if (safe != null) {
            return safe;
        }
        safe = safeSurface(world, x, z, SAFE_SPAWN_SEARCH_RADIUS_BLOCKS);
        return safe != null ? safe : fallbackSafeSurface(world, x, z);
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
                    sendActionBar(player, ChatColor.YELLOW, text(player, "respawn.wait", secondsLeft));
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
                sendActionBar(player, ChatColor.AQUA, text(player, "match.observe.wait", Math.max(1, (remainingTicks + 19) / 20)));
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
            updateTrackingWheelHud();
            return;
        }

        sharedStateTick++;
        processTimedMatchFeatures();
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
            applyPresetResistance(players);
            if (effectiveHealthSyncEnabled() && !isLifeCurseSyncImmune(team)) {
                processSharedTeamHealthForTeam(team, players, currentPlayers);
            } else {
                clearTeamSharedHealthTracking(team);
                teamLastDamageTicks.remove(team);
            }
        }

        updateTrackingWheelHud();
    }

    private boolean isLifeCurseSyncImmune(int team) {
        Long expiresAt = lifeCurseTeamImmunityUntil.get(team);
        return expiresAt != null && expiresAt >= sharedStateTick;
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
        double targetMaxHealth = TeamLifeBindCombatMath.sharedMaxHealth(effectiveHealthPreset(), sharedLevel);
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

    private void applyPresetResistance(List<Player> players) {
        long elapsedTicks = matchStartTick < 0L ? 0L : Math.max(0L, sharedStateTick - matchStartTick);
        int amplifier = TeamLifeBindCombatMath.presetResistanceAmplifier(effectiveHealthPreset(), elapsedTicks);
        if (amplifier < 0) {
            return;
        }
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            PotionEffect current = player.getPotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
            if (current != null && current.getAmplifier() > amplifier && current.getDuration() > 40) {
                continue;
            }
            if (current != null && current.getAmplifier() == amplifier && current.getDuration() > 40 && !current.hasParticles() && !current.hasIcon()) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 80, amplifier, false, false, false), true);
        }
    }

    private void processTimedMatchFeatures() {
        if (!engine.isRunning() || matchStartTick < 0L) {
            lifeCurseUntil.clear();
            lifeCurseTeamImmunityUntil.clear();
            return;
        }

        lifeCurseUntil.entrySet().removeIf(entry -> entry.getValue() < sharedStateTick);
        lifeCurseTeamImmunityUntil.entrySet().removeIf(entry -> entry.getValue() < sharedStateTick);

        long elapsedTicks = Math.max(0L, sharedStateTick - matchStartTick);
        if (effectiveAutoNetherPortalsEnabled() && !autoNetherPortalsSpawned && elapsedTicks >= MATCH_AUTO_PORTAL_DELAY_TICKS) {
            autoNetherPortalsSpawned = true;
            spawnAutoNetherPortals();
        }
        if (supplyDropCount > 0 && !supplyDropsSpawned && elapsedTicks >= MATCH_SUPPLY_DROP_DELAY_TICKS) {
            supplyDropsSpawned = true;
            deploySupplyDrops();
        }
        if (anonymousStrongholdHintsEnabled && nextStrongholdHintTick >= 0L && sharedStateTick >= nextStrongholdHintTick) {
            broadcastAnonymousStrongholdHint();
            nextStrongholdHintTick = sharedStateTick + MATCH_STRONGHOLD_HINT_INTERVAL_TICKS;
        }
    }

    private void updateTrackingWheelHud() {
        if (sharedStateTick == lastTrackingWheelUpdateTick) {
            return;
        }
        if (lastTrackingWheelUpdateTick >= 0L && (sharedStateTick - lastTrackingWheelUpdateTick) < TRACKING_WHEEL_UPDATE_INTERVAL_TICKS) {
            return;
        }
        lastTrackingWheelUpdateTick = sharedStateTick;
        if (!engine.isRunning()) {
            return;
        }

        List<Player> activePlayers = new ArrayList<>();
        Set<Integer> teamsWithTrackingWheel = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            activePlayers.add(player);
            Integer team = engine.teamForPlayer(player.getUniqueId());
            if (team != null && playerHasTrackingWheel(player)) {
                teamsWithTrackingWheel.add(team);
            }
        }

        for (Player viewer : activePlayers) {
            if (viewer == null || !viewer.isOnline() || viewer.isDead() || viewer.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Integer viewerTeam = engine.teamForPlayer(viewer.getUniqueId());
            if (viewerTeam == null || !teamsWithTrackingWheel.contains(viewerTeam)) {
                continue;
            }
            TrackingTarget target = resolveNearestEnemyTeam(viewer, viewerTeam, activePlayers);
            if (target == null) {
                continue;
            }
            String arrow = TeamLifeBindCombatMath.trackingArrow(
                viewer.getLocation().getYaw(),
                viewer.getLocation().getX(),
                viewer.getLocation().getZ(),
                target.location().getX(),
                target.location().getZ()
            );
            sendActionBar(viewer, ChatColor.GOLD, text(viewer, "tracking.wheel.display", teamLabel(viewer, target.team()), arrow));
        }
    }

    private boolean playerHasTrackingWheel(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isTrackingWheelItem(stack)) {
                return true;
            }
        }
        return isTrackingWheelItem(player.getInventory().getItemInOffHand());
    }

    private TrackingTarget resolveNearestEnemyTeam(Player viewer, int viewerTeam, Collection<Player> candidates) {
        double bestDistanceSquared = Double.MAX_VALUE;
        TrackingTarget bestTarget = null;
        for (Player candidate : candidates) {
            if (candidate == null || !candidate.isOnline() || candidate.isDead() || candidate.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Integer team = engine.teamForPlayer(candidate.getUniqueId());
            if (team == null || team == viewerTeam) {
                continue;
            }
            double distanceSquared = viewer.getLocation().distanceSquared(candidate.getLocation());
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestTarget = new TrackingTarget(team, candidate.getLocation());
            }
        }
        return bestTarget;
    }

    private void spawnAutoNetherPortals() {
        World baseWorld = activeMatchWorldName == null ? null : Bukkit.getWorld(activeMatchWorldName);
        World netherWorld = activeMatchWorldName == null ? null : Bukkit.getWorld(activeMatchWorldName + "_nether");
        if (baseWorld == null || netherWorld == null) {
            return;
        }

        List<Location> anchors = resolveActiveTeamAnchors();
        if (anchors.size() < 2) {
            return;
        }

        Location centroid = centroidOf(anchors, baseWorld);
        double averageDistance = averageDistanceTo(centroid, anchors);
        int radius = (int) Math.max(72.0D, Math.min(160.0D, averageDistance * 0.45D));
        generatedPortalSites.clear();

        for (int index = 0; index < 3; index++) {
            double angle = (Math.PI / 6.0D) + ((Math.PI * 2.0D) / 3.0D) * index;
            int x = centroid.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = centroid.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            Location overworldAnchor = resolveSafeSurfaceOrFallback(baseWorld, x, z);
            Location netherAnchor = resolveSafeSurfaceOrFallback(netherWorld, Math.floorDiv(x, 8), Math.floorDiv(z, 8));
            boolean xAxis = (index % 2) == 0;
            buildPortalFrame(overworldAnchor, xAxis);
            buildPortalFrame(netherAnchor, xAxis);
            generatedPortalSites.add(new PortalSite(overworldAnchor.clone(), netherAnchor.clone()));
            int portalIndex = index + 1;
            broadcastColoredMessage(
                ChatColor.DARK_PURPLE,
                player -> text(
                    player,
                    "timed.auto_portal.broadcast",
                    portalIndex,
                    overworldAnchor.getBlockX(),
                    overworldAnchor.getBlockY(),
                    overworldAnchor.getBlockZ()
                )
            );
        }
    }

    private void deploySupplyDrops() {
        World baseWorld = activeMatchWorldName == null ? null : Bukkit.getWorld(activeMatchWorldName);
        if (baseWorld == null) {
            return;
        }
        List<Location> anchors = resolveActiveTeamAnchors();
        if (anchors.isEmpty()) {
            return;
        }
        Location centroid = centroidOf(anchors, baseWorld);
        double averageDistance = averageDistanceTo(centroid, anchors);
        int radius = (int) Math.max(64.0D, Math.min(220.0D, averageDistance * 0.7D));

        for (int index = 0; index < supplyDropCount; index++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            int x = centroid.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = centroid.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            Location drop = resolveSafeSurfaceOrFallback(baseWorld, x, z);
            Material containerType = random.nextBoolean() ? Material.BARREL : Material.CHEST;
            drop.getBlock().setType(containerType, false);
            if (drop.getBlock().getState() instanceof Container container) {
                fillSupplyDrop(container.getInventory());
                container.update(true, false);
            }
            baseWorld.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, drop.clone().add(0.5D, 1.0D, 0.5D), 40, 0.35D, 0.7D, 0.35D, 0.01D);
            int dropIndex = index + 1;
            broadcastColoredMessage(
                ChatColor.AQUA,
                player -> text(player, "timed.supply_drop.broadcast", dropIndex, drop.getBlockX(), drop.getBlockY(), drop.getBlockZ())
            );
        }
    }

    private void fillSupplyDrop(org.bukkit.inventory.Inventory inventory) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Material.GOLDEN_APPLE, 2 + random.nextInt(3)));
        loot.add(new ItemStack(Material.ENDER_PEARL, 2 + random.nextInt(5)));
        loot.add(new ItemStack(Material.ARROW, 16 + random.nextInt(33)));
        loot.add(new ItemStack(Material.COOKED_BEEF, 12 + random.nextInt(13)));
        loot.add(new ItemStack(Material.OBSIDIAN, 4 + random.nextInt(9)));
        loot.add(new ItemStack(Material.IRON_INGOT, 8 + random.nextInt(9)));
        loot.add(new ItemStack(Material.DIAMOND, 1 + random.nextInt(3)));
        loot.add(createLifeCursePotionItem());
        if (random.nextBoolean()) {
            loot.add(createTrackingWheelItem());
        }
        if (random.nextInt(4) == 0) {
            loot.add(createDeathExemptionTotemItem());
        }
        Collections.shuffle(loot, random);
        for (ItemStack stack : loot) {
            inventory.addItem(stack);
        }
    }

    private void broadcastAnonymousStrongholdHint() {
        World baseWorld = activeMatchWorldName == null ? null : Bukkit.getWorld(activeMatchWorldName);
        if (baseWorld == null) {
            return;
        }
        List<Location> anchors = resolveActiveTeamAnchors();
        if (anchors.isEmpty()) {
            return;
        }
        Collections.shuffle(anchors, random);
        int revealCount = Math.min(anchors.size(), Math.max(1, Math.min(2, teamCount / 2)));
        Set<String> announced = new LinkedHashSet<>();
        for (int index = 0; index < revealCount; index++) {
            Location source = anchors.get(index);
            Location stronghold = baseWorld.locateNearestStructure(source, StructureType.STRONGHOLD, 2048, false);
            if (stronghold == null) {
                continue;
            }
            String key = stronghold.getBlockX() + ":" + stronghold.getBlockZ();
            if (!announced.add(key)) {
                continue;
            }
            broadcastColoredMessage(
                ChatColor.LIGHT_PURPLE,
                player -> text(player, "timed.stronghold_hint.broadcast", stronghold.getBlockX(), stronghold.getBlockZ())
            );
        }
    }

    private List<Location> resolveActiveTeamAnchors() {
        World baseWorld = activeMatchWorldName == null ? null : Bukkit.getWorld(activeMatchWorldName);
        if (baseWorld == null) {
            return List.of();
        }
        List<Location> anchors = new ArrayList<>();
        for (int team = 1; team <= teamCount; team++) {
            Location anchor = resolveActiveTeamAnchor(team, baseWorld);
            if (anchor != null) {
                anchors.add(anchor);
            }
        }
        return anchors;
    }

    private Location resolveActiveTeamAnchor(int team, World baseWorld) {
        double totalX = 0.0D;
        double totalZ = 0.0D;
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Integer playerTeam = engine.teamForPlayer(player.getUniqueId());
            if (playerTeam == null || playerTeam != team || player.getWorld() != baseWorld) {
                continue;
            }
            totalX += player.getLocation().getX();
            totalZ += player.getLocation().getZ();
            count++;
        }
        if (count > 0) {
            return resolveSafeSurfaceOrFallback(baseWorld, (int) Math.round(totalX / count), (int) Math.round(totalZ / count));
        }

        Location teamBed = teamBedSpawns.get(team);
        if (teamBed != null && teamBed.getWorld() == baseWorld) {
            return resolveSafeSurfaceOrFallback(baseWorld, teamBed.getBlockX(), teamBed.getBlockZ());
        }
        Location teamSpawn = matchTeamSpawns.get(team);
        if (teamSpawn != null && teamSpawn.getWorld() == baseWorld) {
            return resolveSafeSurfaceOrFallback(baseWorld, teamSpawn.getBlockX(), teamSpawn.getBlockZ());
        }
        return null;
    }

    private Location centroidOf(List<Location> locations, World world) {
        double totalX = 0.0D;
        double totalZ = 0.0D;
        for (Location location : locations) {
            totalX += location.getX();
            totalZ += location.getZ();
        }
        int x = (int) Math.round(totalX / Math.max(1, locations.size()));
        int z = (int) Math.round(totalZ / Math.max(1, locations.size()));
        return resolveSafeSurfaceOrFallback(world, x, z);
    }

    private double averageDistanceTo(Location center, List<Location> locations) {
        if (center == null || locations.isEmpty()) {
            return 0.0D;
        }
        double total = 0.0D;
        for (Location location : locations) {
            total += center.distance(location);
        }
        return total / locations.size();
    }

    private void buildPortalFrame(Location anchor, boolean xAxis) {
        if (anchor == null || anchor.getWorld() == null) {
            return;
        }
        World world = anchor.getWorld();
        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY() - 1;
        int baseZ = anchor.getBlockZ();
        int widthX = xAxis ? 1 : 0;
        int widthZ = xAxis ? 0 : 1;

        for (int frameOffset = -1; frameOffset <= 2; frameOffset++) {
            for (int y = 0; y <= 4; y++) {
                Block block = world.getBlockAt(baseX + frameOffset * widthX, baseY + y, baseZ + frameOffset * widthZ);
                boolean border = y == 0 || y == 4 || frameOffset == -1 || frameOffset == 2;
                block.setType(border ? Material.OBSIDIAN : Material.NETHER_PORTAL, false);
                if (!border && block.getBlockData() instanceof Orientable orientable) {
                    orientable.setAxis(xAxis ? Axis.X : Axis.Z);
                    block.setBlockData(orientable, false);
                }
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
        Integer team = engine.teamForPlayer(player.getUniqueId());
        if (team != null) {
            applySharedExperience(player, teamSharedExperience.getOrDefault(team, Math.max(0, player.getTotalExperience())));
        }
        ensureSupplyBoat(player);
        player.setGameMode(GameMode.SURVIVAL);
        grantRespawnInvulnerability(player);
        sendActionBar(player, ChatColor.GREEN, text(player, "respawn.ready"));
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
        sendActionBar(player, ChatColor.GREEN, text(player, "match.observe.ready"));
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
        serverUiCompat.setPlayerListHeaderFooter(player, buildTabListHeader(player), buildTabListFooter(player));
    }

    private String buildTabListHeader(Player viewer) {
        String state = text(viewer, currentLobbyStateKey());
        return ChatColor.GOLD + "" + ChatColor.BOLD + text(viewer, "scoreboard.title")
            + "\n"
            + ChatColor.YELLOW + text(viewer, "scoreboard.line.state", state);
    }

    private String buildTabListFooter(Player viewer) {
        if (!engine.isRunning()) {
            return ChatColor.GREEN + text(viewer, "scoreboard.line.ready", readyPlayers.size(), Bukkit.getOnlinePlayers().size())
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.AQUA + text(viewer, "scoreboard.line.teams", teamCount)
                + "\n"
                + ChatColor.RED + text(viewer, "scoreboard.line.health", healthPresetLabel(viewer, healthPreset))
                + ChatColor.DARK_GRAY + " | "
                + ChatColor.LIGHT_PURPLE + text(viewer, "scoreboard.line.norespawn", stateLabel(viewer, noRespawnEnabled));
        }

        Integer playerTeam = engine.teamForPlayer(viewer.getUniqueId());
        boolean hasBed = playerTeam != null && teamBedSpawns.containsKey(playerTeam);
        return ChatColor.GREEN + text(viewer, "scoreboard.line.team", playerTeam != null ? teamLabel(viewer, playerTeam) : text(viewer, "scoreboard.value.none"))
            + ChatColor.DARK_GRAY + " | "
            + ChatColor.AQUA + text(viewer, "scoreboard.line.team_bed", playerTeam != null ? bedStatusText(viewer, hasBed) : text(viewer, "scoreboard.value.none"))
            + "\n"
            + ChatColor.DARK_AQUA + text(viewer, "scoreboard.line.dimension", dimensionLabel(viewer))
            + ChatColor.DARK_GRAY + " | "
            + ChatColor.LIGHT_PURPLE + text(viewer, "scoreboard.line.norespawn", stateLabel(viewer, effectiveNoRespawnEnabled()));
    }

    private void clearTabListDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            serverUiCompat.setPlayerListHeaderFooter(player, "", "");
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
            objective = board.registerNewObjective(SCOREBOARD_OBJECTIVE_NAME, Criteria.DUMMY, trimScoreboardText(text(player, "scoreboard.title")));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objectiveCreated = true;
        }

        TeamLifeBindSidebarSnapshot snapshot = getSidebarSnapshot(playerId);
        String title = trimScoreboardText(snapshot.title());
        if (objectiveCreated || !title.equals(scoreboardTitles.get(playerId))) {
            objective.setDisplayName(title);
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
                lineTeam.setPrefix(lines.get(index));
                objective.getScore(entry).setScore(used - index);
            }
        }

        for (int index = used; index < previousLines.size(); index++) {
            String entry = SCOREBOARD_ENTRIES[index];
            board.resetScores(entry);
            Team lineTeam = board.getTeam(SCOREBOARD_TEAM_PREFIX + index);
            if (lineTeam != null) {
                lineTeam.setPrefix("");
                lineTeam.setSuffix("");
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
        team.setColor(color == null ? org.bukkit.ChatColor.WHITE : color.bukkitColor());
        team.setPrefix(prefix == null ? "" : prefix);
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
        if (locatorBarSupport == null || locatorBarSupport.isUnavailable()) {
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
        if (locatorBarSupport == null || locatorBarSupport.isUnavailable()) {
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
        setBooleanGameRule(world, RULE_SHOW_DEATH_MESSAGES, false);
        setBooleanGameRule(world, RULE_DO_IMMEDIATE_RESPAWN, true);
    }

    private void applyLobbyWorldPresentation(World world) {
        if (world == null || !isLobbyWorld(world)) {
            return;
        }
        setBooleanGameRule(world, RULE_DO_DAYLIGHT_CYCLE, false);
        setBooleanGameRule(world, RULE_DO_WEATHER_CYCLE, false);
        world.setTime(LOBBY_FIXED_TIME_TICKS);
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(0);
        world.setClearWeatherDuration(LOBBY_CLEAR_WEATHER_TICKS);
    }

    public void syncManagedPlayerState(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (isInLobby(player)) {
            syncLobbyPlayerState(player);
            return;
        }
        player.resetPlayerTime();
        player.resetPlayerWeather();
    }

    private void syncLobbyPlayerState(Player player) {
        World world = player.getWorld();
        if (world == null || !isLobbyWorld(world)) {
            return;
        }
        applyLobbyWorldPresentation(world);
        player.resetPlayerTime();
        player.resetPlayerWeather();
        player.setPlayerTime(LOBBY_FIXED_TIME_TICKS, false);
        player.setPlayerWeather(WeatherType.CLEAR);
        resyncLobbyVisibility(player);
    }

    private void resyncLobbyVisibility(Player focus) {
        if (focus == null || !focus.isOnline() || !isInLobby(focus)) {
            return;
        }
        World lobbyWorld = focus.getWorld();
        if (lobbyWorld == null) {
            return;
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == null
                || !other.isOnline()
                || other.getUniqueId().equals(focus.getUniqueId())
                || !isInLobby(other)
                || other.getWorld() == null
                || !lobbyWorld.getUID().equals(other.getWorld().getUID())) {
                continue;
            }
            focus.hidePlayer(this, other);
            other.hidePlayer(this, focus);
            focus.showPlayer(this, other);
            other.showPlayer(this, focus);
        }
    }

    private void setBooleanGameRule(World world, String ruleName, boolean enabled) {
        GameRule<Boolean> rule = resolveBooleanGameRule(ruleName);
        if (world != null && rule != null) {
            world.setGameRule(rule, enabled);
        }
    }

    private GameRule<Boolean> resolveBooleanGameRule(String ruleName) {
        return switch (ruleName) {
            case RULE_LOCATOR_BAR -> resolveBooleanGameRuleByFields("LOCATOR_BAR");
            case RULE_ANNOUNCE_ADVANCEMENTS -> resolveBooleanGameRuleByFields("SHOW_ADVANCEMENT_MESSAGES", "ANNOUNCE_ADVANCEMENTS");
            case RULE_SHOW_DEATH_MESSAGES -> resolveBooleanGameRuleByFields("SHOW_DEATH_MESSAGES");
            case RULE_DO_IMMEDIATE_RESPAWN -> resolveBooleanGameRuleByFields("IMMEDIATE_RESPAWN", "DO_IMMEDIATE_RESPAWN");
            case RULE_DO_DAYLIGHT_CYCLE -> resolveBooleanGameRuleByFields("DO_DAYLIGHT_CYCLE", "DAYLIGHT_CYCLE");
            case RULE_DO_WEATHER_CYCLE -> resolveBooleanGameRuleByFields("DO_WEATHER_CYCLE", "WEATHER_CYCLE");
            default -> null;
        };
    }

    private GameRule<Boolean> resolveBooleanGameRuleByFields(String... fieldNames) {
        if (fieldNames == null || fieldNames.length == 0) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            try {
                Object value = GameRule.class.getField(fieldName).get(null);
                if (value instanceof GameRule<?> rule) {
                    @SuppressWarnings("unchecked")
                    GameRule<Boolean> booleanRule = (GameRule<Boolean>) rule;
                    return booleanRule;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next compatible field name for this server version.
            }
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
            for (Map.Entry<UUID, Integer> entry : assignments.entrySet()) {
                if (!entry.getValue().equals(team)) {
                    continue;
                }
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    player.sendMessage(
                        ChatColor.AQUA + text(player, "match.team_announcement", teamLabel(player, team), String.join(", ", byTeam.get(team)))
                    );
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

    private record ChunkLoadRequest(UUID worldId, int chunkX, int chunkZ) {}

    private record SpawnAnchor(int x, int z, float yaw, float pitch) {}

    private record TrackingTarget(int team, Location location) {}

    private record PortalSite(Location overworld, Location nether) {}

    private record GroupAssignmentUnit(String joinId, List<UUID> members) {}

    private record RoundOptions(
        HealthPreset healthPreset,
        boolean noRespawnEnabled,
        boolean healthSyncEnabled,
        boolean autoNetherPortalsEnabled,
        boolean specialMatch
    ) {}
}
