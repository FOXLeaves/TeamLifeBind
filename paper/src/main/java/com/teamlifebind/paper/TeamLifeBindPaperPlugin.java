package com.teamlifebind.paper;

import com.teamlifebind.common.GameOptions;
import com.teamlifebind.common.HealthPreset;
import com.teamlifebind.common.StartResult;
import com.teamlifebind.common.TeamLifeBindEngine;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class TeamLifeBindPaperPlugin extends JavaPlugin {

    private static final long TEAM_WIPE_GUARD_MS = 2_000L;
    private static final int SPAWN_SEARCH_ATTEMPTS = 256;

    private final TeamLifeBindEngine engine = new TeamLifeBindEngine();
    private final Random random = new Random();
    private final Map<Integer, Location> configuredTeamSpawns = new HashMap<>();
    private final Map<Integer, Location> matchTeamSpawns = new HashMap<>();
    private final Map<Integer, Location> teamBedSpawns = new HashMap<>();
    private final Map<Integer, Long> teamWipeGuardUntil = new HashMap<>();
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
    private int countdownTaskId = -1;
    private NamespacedKey teamBedItemKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettingsFromConfig();
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
        teleportAllToLobby();
    }

    @Override
    public void onDisable() {
        cancelCountdown();
    }

    public void reloadPluginConfig(CommandSender sender) {
        reloadConfig();
        loadSettingsFromConfig();
        ensureLobbyWorld();
        sender.sendMessage(ChatColor.GREEN + "[TLB] Config reloaded.");
    }

    public boolean isMatchRunning() {
        return engine.isRunning();
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

    public void markReady(Player player) {
        if (engine.isRunning()) {
            player.sendMessage(ChatColor.RED + "[TLB] 比赛进行中，不能准备。");
            return;
        }
        readyPlayers.add(player.getUniqueId());
        Bukkit.broadcastMessage(ChatColor.AQUA + "[TLB] " + player.getName() + " 已准备。");
        evaluateReadyCountdown();
    }

    public void unready(Player player) {
        if (readyPlayers.remove(player.getUniqueId())) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[TLB] " + player.getName() + " 取消准备。");
        }
        evaluateReadyCountdown();
    }

    public void onPlayerLeave(UUID playerId) {
        readyPlayers.remove(playerId);
        evaluateReadyCountdown();
    }

    public void sendLobbyGuide(Player player) {
        player.sendMessage(ChatColor.GOLD + "[TLB] 当前在大厅，使用 /tlb ready 准备开始。");
    }

    public void teleportToLobby(Player player) {
        Location lobbySpawn = resolveLobbySpawn();
        if (lobbySpawn == null) {
            return;
        }
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

    public String noRespawnStatusText() {
        return ChatColor.YELLOW
            + "[TLB] norespawn="
            + noRespawnEnabled
            + " | blocked="
            + new ArrayList<>(noRespawnDimensions);
    }

    public void setNoRespawnEnabled(boolean enabled) {
        this.noRespawnEnabled = enabled;
        if (!enabled) {
            noRespawnLockedPlayers.clear();
        }
        getConfig().set("no-respawn.enabled", this.noRespawnEnabled);
        saveConfig();
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
            player.sendMessage(ChatColor.RED + "[TLB] You died in a no-respawn dimension and are now eliminated.");
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
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(teamBedItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public void explodeVanillaBed(Player player, Location placementLocation) {
        player.sendMessage(ChatColor.RED + "[TLB] Normal beds are disabled and explode instantly.");
        Location center = placementLocation.clone().add(0.5D, 0.5D, 0.5D);
        Bukkit.getScheduler().runTask(this, () -> {
            World world = center.getWorld();
            if (world != null) {
                world.createExplosion(center, 3.0F, false, false, player);
            }
        });
    }

    public boolean registerPlacedTeamBed(Player player, Location bedFoot) {
        if (!engine.isRunning()) {
            player.sendMessage(ChatColor.RED + "[TLB] Team bed can only be placed during an active match.");
            return false;
        }

        Integer team = engine.teamForPlayer(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "[TLB] You are not assigned to a team.");
            return false;
        }

        Location normalized = normalizeBlockCenter(bedFoot);
        teamBedSpawns.put(team, normalized);
        player.sendMessage(ChatColor.GREEN + "[TLB] Team #" + team + " bed spawn updated.");
        return true;
    }

    public void clearTeamBedAt(Location bedFoot) {
        Integer matchedTeam = null;
        for (Map.Entry<Integer, Location> entry : teamBedSpawns.entrySet()) {
            if (sameBlock(entry.getValue(), bedFoot)) {
                matchedTeam = entry.getKey();
                break;
            }
        }
        if (matchedTeam == null) {
            return;
        }

        teamBedSpawns.remove(matchedTeam);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "[TLB] Team #" + matchedTeam + " bed was destroyed.");
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
        return teamSpawn.clone();
    }

    public void startMatch(CommandSender sender) {
        if (engine.isRunning()) {
            sender.sendMessage(ChatColor.RED + "[TLB] Match already running.");
            return;
        }

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.size() < teamCount) {
            sender.sendMessage(ChatColor.RED + "[TLB] Need at least " + teamCount + " players online.");
            return;
        }

        cleanupActiveMatchWorld();
        World world = createNewMatchWorld();
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "[TLB] Failed to create match world.");
            return;
        }

        cancelCountdown();
        readyPlayers.clear();
        matchTeamSpawns.clear();
        teamBedSpawns.clear();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();

        List<UUID> ids = online.stream().map(Player::getUniqueId).toList();
        StartResult result = engine.start(ids, new GameOptions(teamCount, healthPreset));
        Map<Integer, Location> baseSpawns = resolveTeamBaseSpawns(world, teamCount);

        for (Map.Entry<Integer, Location> entry : baseSpawns.entrySet()) {
            matchTeamSpawns.put(entry.getKey(), entry.getValue().clone());
        }

        for (Player player : online) {
            int team = result.teamByPlayer().get(player.getUniqueId());
            Location spawn = randomizeAround(baseSpawns.get(team));
            applyPresetHealth(player);
            player.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
            player.sendMessage(
                ChatColor.AQUA + "[TLB] You are on Team #" + team + ". " + ChatColor.GRAY + "Health preset: " + healthPreset.name()
            );
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "[TLB] Match started. Team count: " + teamCount + ". PvP is allowed. world=" + world.getName());
        Bukkit.broadcastMessage(ChatColor.GOLD + "[TLB] Craft Team Bed: 6 wool + 3 planks (bottom row).");

        if (announceTeamAssignment) {
            announceTeams(result.teamByPlayer());
        }
    }

    public void stopMatch(CommandSender sender) {
        if (!engine.isRunning()) {
            sender.sendMessage(ChatColor.YELLOW + "[TLB] No running match.");
            return;
        }
        engine.stop();
        readyPlayers.clear();
        matchTeamSpawns.clear();
        teamBedSpawns.clear();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
        teleportAllToLobby();
        cleanupActiveMatchWorld();
        Bukkit.broadcastMessage(ChatColor.GOLD + "[TLB] Match stopped.");
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

        Bukkit.broadcastMessage(ChatColor.RED + "[TLB] Team #" + result.team() + " triggered life-bind wipe.");

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
        declareWinner(winner, killer.getName() + " killed the Ender Dragon first");
    }

    public String statusText() {
        if (!engine.isRunning()) {
            return ChatColor.YELLOW + "[TLB] Status: idle";
        }
        return ChatColor.GREEN
            + "[TLB] Status: running | teams="
            + teamCount
            + " | health="
            + healthPreset.name()
            + " | team-beds="
            + sortedActiveTeams(teamBedSpawns.keySet())
            + " | norespawn="
            + noRespawnEnabled;
    }

    private void declareWinner(int team, String reason) {
        Bukkit.broadcastMessage(ChatColor.GREEN + "[TLB] Team #" + team + " wins. " + ChatColor.GRAY + "(" + reason + ")");
        engine.stop();
        readyPlayers.clear();
        matchTeamSpawns.clear();
        teamBedSpawns.clear();
        teamWipeGuardUntil.clear();
        noRespawnLockedPlayers.clear();
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
        teamBedItemKey = new NamespacedKey(this, "team_bed_item");
        NamespacedKey recipeKey = new NamespacedKey(this, "team_bed_recipe");
        Bukkit.removeRecipe(recipeKey);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createTeamBedItem());
        recipe.shape("WWW", "WWW", "PPP");
        recipe.setIngredient('W', new RecipeChoice.MaterialChoice(new ArrayList<>(Tag.WOOL.getValues())));
        recipe.setIngredient('P', new RecipeChoice.MaterialChoice(new ArrayList<>(Tag.PLANKS.getValues())));
        Bukkit.addRecipe(recipe);
    }

    private ItemStack createTeamBedItem() {
        ItemStack item = new ItemStack(Material.WHITE_BED, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.GOLD + "Team Bed");
        meta.setLore(List.of(ChatColor.GRAY + "Respawn point for your team."));
        meta.getPersistentDataContainer().set(teamBedItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private Map<Integer, Location> resolveTeamBaseSpawns(World world, int teams) {
        Map<Integer, Location> resolved = new LinkedHashMap<>();
        List<Location> chosen = new ArrayList<>();
        Location center = world.getSpawnLocation();

        for (int team = 1; team <= teams; team++) {
            Location configured = configuredTeamSpawns.get(team);
            if (configured != null && configured.getWorld() != null) {
                resolved.put(team, configured.clone());
                chosen.add(normalizeBlockCenter(configured));
                continue;
            }

            Location selected = null;
            int minDistance = minTeamDistance;
            for (int relax = 0; relax < 8 && selected == null; relax++) {
                selected = findRandomTeamBase(world, center, chosen, minDistance);
                minDistance = Math.max(64, (int) Math.floor(minDistance * 0.85D));
            }
            if (selected == null) {
                selected = fallbackRandomTeamBase(world, center);
            }

            resolved.put(team, selected);
            chosen.add(normalizeBlockCenter(selected));
        }
        return resolved;
    }

    private Location findRandomTeamBase(World world, Location center, List<Location> chosen, int minDistance) {
        for (int attempt = 0; attempt < SPAWN_SEARCH_ATTEMPTS; attempt++) {
            Location candidate = randomSurface(world, center, randomSpawnRadius);
            if (isFarEnough(candidate, chosen, minDistance)) {
                return candidate;
            }
        }
        return null;
    }

    private Location fallbackRandomTeamBase(World world, Location center) {
        return randomSurface(world, center, randomSpawnRadius);
    }

    private Location randomSurface(World world, Location center, int radius) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        return safeSurface(world, x, z);
    }

    private boolean isFarEnough(Location candidate, List<Location> chosen, int minDistance) {
        long minDistanceSq = (long) minDistance * minDistance;
        for (Location existing : chosen) {
            if (existing.getWorld() == null || candidate.getWorld() == null) {
                continue;
            }
            if (!existing.getWorld().getUID().equals(candidate.getWorld().getUID())) {
                continue;
            }
            long dx = candidate.getBlockX() - existing.getBlockX();
            long dz = candidate.getBlockZ() - existing.getBlockZ();
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
        Location safe = safeSurface(world, x, z);
        safe.setYaw(base.getYaw());
        safe.setPitch(base.getPitch());
        return safe;
    }

    private Location safeSurface(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5D, y, z + 0.5D);
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

        World world = bedFoot.getWorld();
        if (world == null) {
            return bedFoot.clone();
        }
        return safeSurface(world, bedFoot.getBlockX(), bedFoot.getBlockZ());
    }

    private boolean isSafeForRespawn(Location feet) {
        if (feet.getWorld() == null) {
            return false;
        }
        Location head = feet.clone().add(0.0D, 1.0D, 0.0D);
        return feet.getBlock().isPassable() && head.getBlock().isPassable();
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
        Bukkit.broadcastMessage(ChatColor.GOLD + "[TLB] 全员准备完成，倒计时开始。");
        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (engine.isRunning()) {
                cancelCountdown();
                return;
            }

            List<Player> current = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (current.size() < teamCount) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "[TLB] 在线人数不足，倒计时取消。");
                cancelCountdown();
                return;
            }
            for (Player player : current) {
                if (!readyPlayers.contains(player.getUniqueId())) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "[TLB] 有玩家取消准备，倒计时取消。");
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
                Bukkit.broadcastMessage(ChatColor.GOLD + "[TLB] 比赛将在 " + remain[0] + " 秒后开始。");
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
        ensureWorld(lobbyWorldName + "_nether", World.Environment.NETHER, false);
        ensureWorld(lobbyWorldName + "_the_end", World.Environment.THE_END, false);
        if (world == null) {
            return null;
        }

        prepareLobbyPlatform(world);
        world.setSpawnLocation(0, 101, 0);
        world.setTime(6000L);
        return world;
    }

    private void prepareLobbyPlatform(World world) {
        int y = 100;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                world.getBlockAt(x, y, z).setType(Material.STONE);
                world.getBlockAt(x, y + 1, z).setType(Material.AIR);
                world.getBlockAt(x, y + 2, z).setType(Material.AIR);
            }
        }
        world.getBlockAt(0, y + 1, 0).setType(Material.TORCH);
    }

    private void teleportAllToLobby() {
        Location lobby = resolveLobbySpawn();
        if (lobby == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(lobby, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }

    private World createNewMatchWorld() {
        String worldName = matchWorldPrefix + "_" + System.currentTimeMillis();
        World world = ensureWorld(worldName, World.Environment.NORMAL, false);
        if (world != null) {
            ensureWorld(worldName + "_nether", World.Environment.NETHER, false);
            ensureWorld(worldName + "_the_end", World.Environment.THE_END, false);
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
            return null;
        }
        int y = target.getHighestBlockYAt(x, z) + 1;
        return new Location(target, x + 0.5D, y, z + 0.5D);
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
            Bukkit.broadcastMessage(ChatColor.AQUA + "[TLB] Team #" + team + ": " + String.join(", ", byTeam.get(team)));
        }
    }
}
