package com.teamlifebind.paper;

import com.teamlifebind.common.SupplyDropRules;
import com.teamlifebind.common.TeamLifeBindCombatMath;
import com.teamlifebind.common.TeamUtilityState;
import com.teamlifebind.common.TeamUtilityState.Channel;
import com.teamlifebind.common.TeamUtilityState.ChannelKind;
import com.teamlifebind.common.TeamUtilityState.TeamMark;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

/** Bukkit item, HUD and container adapter for the shared round-local utility rules. */
final class TeamUtilityController {
    private final TeamLifeBindPaperPlugin plugin;
    private final TeamUtilityState state = new TeamUtilityState();
    private final Map<UUID, BandageHand> bandageHands = new HashMap<>();
    private final Map<UUID, Long> lastInteractionTick = new HashMap<>();
    private final Map<String, Location> lockedDrops = new HashMap<>();
    private final List<Location> plannedDrops = new ArrayList<>();
    private final Random random = new Random();
    private final NamespacedKey whistleKey;
    private final NamespacedKey bandageKey;
    private final NamespacedKey dropKey;
    private Boolean activeDynamicCount;
    private Integer activeConfiguredCount;
    private boolean suppliesPlanned;
    private boolean finaleAnnounced;

    TeamUtilityController(TeamLifeBindPaperPlugin plugin) {
        this.plugin = plugin;
        whistleKey = new NamespacedKey(plugin, "resonance_whistle");
        bandageKey = new NamespacedKey(plugin, "emergency_bandage");
        dropKey = new NamespacedKey(plugin, "locked_supply");
    }

    void registerRecipes() {
        registerRecipe("resonance_whistle", createWhistle(null), Material.COPPER_INGOT, Material.COPPER_INGOT, Material.REDSTONE);
        registerRecipe("emergency_bandage", createBandage(null), Material.PAPER, Material.PAPER, Material.STRING, Material.STRING);
    }

    private void registerRecipe(String name, ItemStack result, Material... ingredients) {
        NamespacedKey key = new NamespacedKey(plugin, name);
        Bukkit.removeRecipe(key);
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (Material ingredient : ingredients) {
            recipe.addIngredient(ingredient);
        }
        Bukkit.addRecipe(recipe);
    }

    ItemStack createWhistle(Player viewer) {
        return createItem(viewer, Material.GOAT_HORN, whistleKey, "resonance_whistle");
    }

    ItemStack createBandage(Player viewer) {
        return createItem(viewer, Material.PAPER, bandageKey, "emergency_bandage");
    }

    private ItemStack createItem(Player viewer, Material material, NamespacedKey marker, String id) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + plugin.text(viewer, "item." + id + ".name"));
        meta.setLore(List.of(ChatColor.GRAY + plugin.text(viewer, "item." + id + ".lore")));
        meta.setItemModel(new NamespacedKey("teamlifebind", id));
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        if (marker.equals(whistleKey)) {
            meta.setMaxStackSize(1);
        }
        item.setItemMeta(meta);
        return item;
    }

    boolean isUtilityItem(ItemStack stack) {
        return hasMarker(stack, whistleKey) || hasMarker(stack, bandageKey);
    }

    ItemStack localizedItem(Player viewer, ItemStack stack) {
        return hasMarker(stack, whistleKey) ? createWhistle(viewer)
            : hasMarker(stack, bandageKey) ? createBandage(viewer) : null;
    }

    private boolean hasMarker(ItemStack stack, NamespacedKey key) {
        return stack != null && !stack.getType().isAir() && stack.hasItemMeta()
            && stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    boolean use(Player player, EquipmentSlot hand, ItemStack item) {
        if (!isUtilityItem(item)) {
            return false;
        }
        if (!plugin.isUtilityParticipant(player)) {
            message(player, "utility.match_only");
            return true;
        }
        int team = plugin.utilityTeam(player);
        long tick = plugin.utilityTick();
        if (Long.valueOf(tick).equals(lastInteractionTick.put(player.getUniqueId(), tick))) {
            return true;
        }
        if (hasMarker(item, whistleKey)) {
            long cooldown = state.whistleCooldownRemainingTicks(team, tick);
            if (cooldown > 0L) {
                message(player, "utility.cooldown", seconds(cooldown));
                return true;
            }
            Location eye = player.getEyeLocation();
            RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), TeamUtilityState.WHISTLE_RANGE_BLOCKS, FluidCollisionMode.NEVER, true);
            Location target = hit == null ? eye.clone().add(eye.getDirection().multiply(TeamUtilityState.WHISTLE_RANGE_BLOCKS))
                : hit.getHitPosition().toLocation(player.getWorld());
            TeamMark mark = state.tryMark(team, dimension(player.getWorld()), target.getX(), target.getY(), target.getZ(), tick);
            if (mark != null) {
                for (Player teammate : plugin.utilityTeamPlayers(team)) {
                    if (dimension(teammate.getWorld()).equals(mark.dimension())) {
                        message(teammate, "utility.whistle.mark", player.getName(), floor(mark.x()), floor(mark.y()), floor(mark.z()));
                    } else {
                        message(teammate, "utility.whistle.other_dimension", player.getName(), mark.dimension(), floor(mark.x()), floor(mark.y()), floor(mark.z()));
                    }
                }
            }
            return true;
        }
        if (state.channelForPlayer(player.getUniqueId()) != null) {
            interrupt(player);
            return true;
        }
        if (plugin.utilityHeal(player, false) <= 0.0D) {
            message(player, "utility.bandage.full_health");
            return true;
        }
        long cooldown = state.bandageCooldownRemainingTicks(team, tick);
        if (cooldown > 0L) {
            message(player, "utility.cooldown", seconds(cooldown));
            return true;
        }
        EquipmentSlot actualHand = hand == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        int hotbarSlot = player.getInventory().getHeldItemSlot();
        Channel channel = state.startBandage(player.getUniqueId(), team, dimension(player.getWorld()), actualHand + ":" + hotbarSlot, tick);
        if (channel == null) {
            message(player, "utility.channel_busy");
        } else {
            bandageHands.put(player.getUniqueId(), new BandageHand(actualHand, hotbarSlot, item.clone()));
            message(player, "utility.bandage.started", seconds(TeamUtilityState.BANDAGE_CHANNEL_TICKS));
        }
        return true;
    }

    boolean beginSupplyClaim(Player player, Block block) {
        if (!isLocked(block)) {
            return false;
        }
        if (!plugin.isUtilityParticipant(player)) {
            message(player, "utility.match_only");
            return true;
        }
        if (!player.getInventory().getItemInMainHand().getType().isAir()) {
            message(player, "supply.claim.locked");
            return true;
        }
        if (!withinClaimRadius(player, block.getLocation())) {
            message(player, "supply.claim.too_far");
            return true;
        }
        Channel current = state.channelForPlayer(player.getUniqueId());
        if (current != null) {
            interrupt(player);
            return true;
        }
        Channel channel = state.startSupplyClaim(player.getUniqueId(), plugin.utilityTeam(player), dimension(player.getWorld()), dropId(block.getLocation()), plugin.utilityTick());
        message(player, channel == null ? "utility.channel_busy" : "supply.claim.started", seconds(SupplyDropRules.CLAIM_TICKS));
        return true;
    }

    void interrupt(Player player) {
        if (player != null && state.cancel(player.getUniqueId())) {
            bandageHands.remove(player.getUniqueId());
            if (player.isOnline()) {
                message(player, "utility.cancelled");
            }
        }
    }

    void removePlayer(UUID playerId) {
        state.cancel(playerId);
        bandageHands.remove(playerId);
        lastInteractionTick.remove(playerId);
    }

    void changedHotbar(Player player) {
        BandageHand held = bandageHands.get(player.getUniqueId());
        if (held != null && held.hand() == EquipmentSlot.HAND) {
            interrupt(player);
        }
    }

    void changedInventory(Player player) {
        Channel channel = state.channelForPlayer(player.getUniqueId());
        if (channel != null && channel.kind() == ChannelKind.BANDAGE) {
            interrupt(player);
        }
    }

    boolean usesInventorySlot(Player player, int slot) {
        BandageHand held = bandageHands.get(player.getUniqueId());
        return held != null && slot == (held.hand() == EquipmentSlot.OFF_HAND ? 40 : held.hotbarSlot());
    }

    void tick(long tick) {
        for (Channel channel : state.channels()) {
            Player player = Bukkit.getPlayer(channel.playerId());
            if (state.channelForPlayer(channel.playerId()) != channel) {
                bandageHands.remove(channel.playerId());
                continue;
            }
            if (!plugin.isUtilityParticipant(player) || !dimension(player.getWorld()).equals(channel.dimension())
                || !Integer.valueOf(channel.team()).equals(plugin.utilityTeam(player))) {
                interrupt(player);
                removePlayer(channel.playerId());
                continue;
            }
            long remaining = TeamUtilityState.remainingChannelTicks(channel, tick);
            if (channel.kind() == ChannelKind.BANDAGE) {
                BandageHand held = bandageHands.get(channel.playerId());
                if (!validBandageHand(player, held)) {
                    interrupt(player);
                    continue;
                }
                if (remaining == 0L) {
                    double healed = plugin.utilityHeal(player, false);
                    if (healed <= 0.0D) {
                        interrupt(player);
                        message(player, "utility.bandage.full_health");
                    } else if (state.completeBandage(channel, tick)) {
                        ItemStack stack = player.getInventory().getItem(held.hand());
                        stack.setAmount(stack.getAmount() - 1);
                        player.getInventory().setItem(held.hand(), stack.getAmount() == 0 ? null : stack);
                        bandageHands.remove(channel.playerId());
                        message(player, "utility.bandage.complete", plugin.utilityHeal(player, true));
                    }
                } else if (tick % 20L == 0L) {
                    plugin.utilityActionBar(player, "utility.bandage.progress", seconds(remaining));
                }
            } else {
                Location location = lockedDrops.get(channel.dropId());
                if (location == null || !isLocked(location.getBlock()) || !withinClaimRadius(player, location)) {
                    interrupt(player);
                    continue;
                }
                if (remaining == 0L && location.getBlock().getState() instanceof Container container) {
                    List<Channel> competitors = state.channels().stream()
                        .filter(other -> other != channel && channel.dropId().equals(other.dropId())).toList();
                    if (!state.completeSupplyClaim(channel, tick)) {
                        continue;
                    }
                    container.setLock("");
                    container.getPersistentDataContainer().remove(dropKey);
                    container.update(true, false);
                    plugin.fillUtilitySupply(container.getInventory());
                    lockedDrops.remove(channel.dropId());
                    message(player, "supply.claim.complete");
                    plugin.campaigns().claimedSupply(player);
                    for (Channel competitor : competitors) {
                        Player loser = Bukkit.getPlayer(competitor.playerId());
                        if (loser != null) {
                            message(loser, "supply.claim.claimed");
                        }
                    }
                    player.openInventory(container.getInventory());
                } else if (tick % 20L == 0L) {
                    plugin.utilityActionBar(player, "supply.claim.progress", seconds(remaining));
                }
            }
        }
        if (tick % 20L == 0L) {
            updateMarkHud(tick);
        }
    }

    private boolean validBandageHand(Player player, BandageHand held) {
        if (held == null || (held.hand() == EquipmentSlot.HAND && player.getInventory().getHeldItemSlot() != held.hotbarSlot())) {
            return false;
        }
        ItemStack item = player.getInventory().getItem(held.hand());
        return hasMarker(item, bandageKey) && item.getAmount() > 0 && item.isSimilar(held.item());
    }

    boolean hasHud(Player player) {
        Integer team = plugin.utilityTeam(player);
        return state.channelForPlayer(player.getUniqueId()) != null
            || (team != null && state.markForTeam(team, plugin.utilityTick()) != null);
    }

    boolean hasChannel(Player player) { return state.channelForPlayer(player.getUniqueId()) != null; }

    private void updateMarkHud(long tick) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isUtilityParticipant(player) || state.channelForPlayer(player.getUniqueId()) != null || plugin.campaigns().hasHud(player)) {
                continue;
            }
            TeamMark mark = state.markForTeam(plugin.utilityTeam(player), tick);
            if (mark == null) {
                continue;
            }
            if (!dimension(player.getWorld()).equals(mark.dimension())) {
                plugin.utilityActionBar(player, "utility.whistle.actionbar.other_dimension", mark.dimension(), floor(mark.x()), floor(mark.y()), floor(mark.z()), seconds(TeamUtilityState.remainingMarkTicks(mark, tick)));
                continue;
            }
            Location location = player.getLocation();
            String arrow = TeamLifeBindCombatMath.trackingArrow(location.getYaw(), location.getX(), location.getZ(), mark.x(), mark.z());
            double dx = location.getX() - mark.x();
            double dy = location.getY() - mark.y();
            double dz = location.getZ() - mark.z();
            plugin.utilityActionBar(player, "utility.whistle.actionbar", arrow, Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)), floor(mark.x()), floor(mark.y()), floor(mark.z()), seconds(TeamUtilityState.remainingMarkTicks(mark, tick)));
        }
    }

    void startRound() {
        clear();
        activeDynamicCount = plugin.getConfig().getBoolean("timed-events.supply-drops.dynamic-count", true);
        activeConfiguredCount = plugin.utilityConfiguredSupplyCount();
    }

    boolean dynamicCount() {
        return activeDynamicCount != null ? activeDynamicCount : plugin.getConfig().getBoolean("timed-events.supply-drops.dynamic-count", true);
    }

    String supplyRules(Player viewer) {
        return plugin.text(viewer, dynamicCount() ? "rules.supply.dynamic" : "rules.supply.fixed", supplyCount());
    }

    int supplyCount() {
        return suppliesPlanned ? plannedDrops.size() : SupplyDropRules.resolveCount(configuredCount(), dynamicCount(), plugin.utilityEligibleTeamCount());
    }

    boolean suppliesEnabled() {
        return configuredCount() > 0;
    }

    private int configuredCount() {
        return activeConfiguredCount != null ? activeConfiguredCount : plugin.utilityConfiguredSupplyCount();
    }

    void previewSupplies(long remainingTicks) {
        if (suppliesPlanned) {
            return;
        }
        suppliesPlanned = true;
        World world = plugin.utilityMatchWorld();
        List<Location> anchors = plugin.utilityTeamAnchors();
        if (world == null || anchors.isEmpty()) {
            return;
        }
        int count = SupplyDropRules.resolveCount(configuredCount(), dynamicCount(), plugin.utilityEligibleTeamCount());
        double centerX = anchors.stream().mapToDouble(Location::getX).average().orElse(0.0D);
        double centerZ = anchors.stream().mapToDouble(Location::getZ).average().orElse(0.0D);
        double averageDistance = anchors.stream().mapToDouble(anchor -> Math.hypot(anchor.getX() - centerX, anchor.getZ() - centerZ)).average().orElse(0.0D);
        int radius = (int) Math.max(64.0D, Math.min(220.0D, averageDistance * 0.7D));
        Set<String> sites = new HashSet<>();
        for (int attempt = 0; plannedDrops.size() < count && attempt < Math.max(count * 20, 1); attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            Location location = plugin.utilitySafeSurface(world, (int) Math.round(centerX + Math.cos(angle) * distance), (int) Math.round(centerZ + Math.sin(angle) * distance));
            if (location != null && location.getBlock().getType().isAir() && sites.add(dropId(location))) {
                plannedDrops.add(location.getBlock().getLocation());
            }
        }
        broadcast("supply.preview", seconds(remainingTicks), plannedDrops.size());
        for (int index = 0; index < plannedDrops.size(); index++) {
            Location location = plannedDrops.get(index);
            broadcast("supply.preview.location", index + 1, location.getBlockX(), location.getBlockZ());
        }
    }

    void deploySupplies() {
        if (!suppliesPlanned) {
            previewSupplies(0L);
        }
        for (int index = 0; index < plannedDrops.size(); index++) {
            Location location = plannedDrops.get(index);
            Block block = location.getBlock();
            if (!block.getType().isAir()) {
                continue;
            }
            block.setType(Material.BARREL, false);
            if (!(block.getState() instanceof Container container)) {
                continue;
            }
            String id = dropId(location);
            container.getInventory().clear();
            container.setLock("TeamLifeBind:" + id);
            container.getPersistentDataContainer().set(dropKey, PersistentDataType.STRING, id);
            container.update(true, false);
            lockedDrops.put(id, location);
            location.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, location.clone().add(0.5D, 1.0D, 0.5D), 40, 0.35D, 0.7D, 0.35D, 0.01D);
            broadcast("timed.supply_drop.broadcast", index + 1, location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    void previewFinale(long remainingTicks) {
        if (!finaleAnnounced) {
            finaleAnnounced = true;
            broadcast("match.finale.preview", seconds(remainingTicks));
        }
    }

    boolean isLocked(Block block) {
        if (lockedDrops.isEmpty() || block == null || block.getType() != Material.BARREL) {
            return false;
        }
        String id = dropId(block.getLocation());
        return lockedDrops.containsKey(id) && block.getState() instanceof Container container
            && id.equals(container.getPersistentDataContainer().get(dropKey, PersistentDataType.STRING));
    }

    boolean locksInventory(Inventory inventory) {
        if (lockedDrops.isEmpty()) {
            return false;
        }
        Location location = inventory == null ? null : inventory.getLocation();
        return location != null && isLocked(location.getBlock());
    }

    String supplyHint(Player viewer) {
        if (plannedDrops.isEmpty()) return plugin.text(viewer, "campaign.point.no_supply");
        Location location = plannedDrops.get(0);
        return plugin.text(viewer, "campaign.point.location", dimension(location.getWorld()), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    void clear() {
        for (Map.Entry<String, Location> entry : lockedDrops.entrySet()) {
            Location location = entry.getValue();
            World world = location.getWorld();
            if (world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                && location.getBlock().getState() instanceof Container container
                && entry.getKey().equals(container.getPersistentDataContainer().get(dropKey, PersistentDataType.STRING))) {
                container.setLock("");
                container.getPersistentDataContainer().remove(dropKey);
                container.update(true, false);
            }
        }
        state.clear();
        bandageHands.clear();
        lastInteractionTick.clear();
        lockedDrops.clear();
        plannedDrops.clear();
        suppliesPlanned = false;
        finaleAnnounced = false;
        activeDynamicCount = null;
        activeConfiguredCount = null;
    }

    private boolean withinClaimRadius(Player player, Location location) {
        return player.getWorld().equals(location.getWorld())
            && player.getLocation().distanceSquared(location.clone().add(0.5D, 0.5D, 0.5D)) <= SupplyDropRules.CLAIM_RADIUS_BLOCKS * SupplyDropRules.CLAIM_RADIUS_BLOCKS;
    }

    private void message(Player player, String key, Object... args) {
        player.sendMessage(ChatColor.AQUA + plugin.text(player, key, args));
    }

    private void broadcast(String key, Object... args) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            message(player, key, args);
        }
    }

    private static String dimension(World world) {
        return world.getKey().toString();
    }

    private static String dropId(Location location) {
        return dimension(location.getWorld()) + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static long seconds(long ticks) {
        return (ticks + 19L) / 20L;
    }

    private static int floor(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private record BandageHand(EquipmentSlot hand, int hotbarSlot, ItemStack item) {
    }
}
