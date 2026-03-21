package com.teamlifebind.paper;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

public final class TeamLifeBindListener implements Listener {

    private final TeamLifeBindPaperPlugin plugin;

    public TeamLifeBindListener(TeamLifeBindPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(plugin::isSupplyBoatItem);
        plugin.handlePlayerDeath(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeamBedPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.isTeamBedItem(event.getItem().getItemStack())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.normalizeTeamBedInventory(player));
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }

        Player killer = dragon.getKiller();
        if (killer != null) {
            plugin.handleDragonKill(killer);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.isMatchRunning()) {
            Location lobby = plugin.resolveLobbySpawn();
            if (lobby != null) {
                event.setRespawnLocation(lobby);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.teleportToLobby(event.getPlayer());
                plugin.sendLobbyGuide(event.getPlayer());
            });
            return;
        }
        if (plugin.isRespawnLocked(event.getPlayer().getUniqueId())) {
            Location spectator = plugin.resolveCurrentSpectatorLocation();
            if (spectator != null) {
                event.setRespawnLocation(spectator);
            }
            plugin.handleBlockedRespawn(event.getPlayer());
            return;
        }
        if (!plugin.hasAssignedMatchTeam(event.getPlayer().getUniqueId())) {
            Location spectator = plugin.resolveCurrentSpectatorLocation();
            if (spectator != null) {
                event.setRespawnLocation(spectator);
            }
            Bukkit.getScheduler().runTask(plugin, () -> plugin.moveUnassignedPlayerToSpectator(event.getPlayer(), true));
            return;
        }
        Location respawn = plugin.resolveRespawnLocation(event.getPlayer().getUniqueId());
        if (respawn != null) {
            event.setRespawnLocation(respawn);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.ensureSupplyBoat(event.getPlayer());
            plugin.grantRespawnInvulnerability(event.getPlayer());
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.onPlayerLeave(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        Player attacker = resolvePlayerDamager(event.getDamager());
        if (!plugin.shouldCancelFriendlyFire(attacker, target)) {
            return;
        }

        event.setCancelled(true);
        plugin.notifyFriendlyFireBlocked(attacker);
    }

    @EventHandler
    public void onLobbyMenuInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!plugin.isLobbyMenuItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        plugin.openLobbyMenu(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLobbyMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!plugin.isLobbyMenuTitle(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getCurrentItem() == null) {
            return;
        }
        plugin.handleLobbyMenuClick(player, event.getCurrentItem(), event.getClick());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLobbyMenuDrag(InventoryDragEvent event) {
        if (plugin.isLobbyMenuTitle(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null || !plugin.isTeamBedItem(event.getRecipe().getResult())) {
            return;
        }
        Player viewer = event.getViewers().stream()
            .filter(Player.class::isInstance)
            .map(Player.class::cast)
            .findFirst()
            .orElse(null);
        event.getInventory().setResult(plugin.previewCraftedTeamBed(viewer));
    }

    @EventHandler
    public void onBedPlace(BlockPlaceEvent event) {
        if (!Tag.BEDS.isTagged(event.getBlockPlaced().getType())) {
            return;
        }

        TeamLifeBindPaperPlugin.TeamBedPlacementResult result = plugin.registerPlacedTeamBed(
            event.getPlayer(),
            event.getItemInHand(),
            normalizeBedFoot(event.getBlockPlaced())
        );
        if (result == TeamLifeBindPaperPlugin.TeamBedPlacementResult.ACCEPTED) {
            Bukkit.getScheduler().runTask(plugin, () -> consumeResidualPlacedTeamBedToken(event.getPlayer(), event.getHand()));
            return;
        }

        event.setCancelled(true);
        if (result == TeamLifeBindPaperPlugin.TeamBedPlacementResult.EXPLODE) {
            event.setCancelled(true);
            consumeOnePlacedBed(event);
            plugin.explodeVanillaBed(event.getPlayer(), event.getBlockPlaced().getLocation());
        }
    }

    @EventHandler
    public void onBedBreak(BlockBreakEvent event) {
        if (!Tag.BEDS.isTagged(event.getBlock().getType())) {
            return;
        }
        Integer ownerTeam = plugin.clearTeamBedAt(normalizeBedFoot(event.getBlock()));
        if (ownerTeam == null) {
            return;
        }
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5D, 0.5D, 0.5D), plugin.createOwnedTeamBedDrop(ownerTeam));
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.YELLOW + plugin.text("bed.personal_spawn_disabled"));
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        Location redirected = plugin.resolvePortalDestination(event.getFrom(), event.getCause());
        if (redirected != null) {
            event.setTo(redirected);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropSupplyBoat(PlayerDropItemEvent event) {
        if (plugin.isTeamBedItem(event.getItemDrop().getItemStack())) {
            if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
                return;
            }
            event.getItemDrop().getItemStack().setAmount(1);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.consumeResidualDroppedTeamBedToken(event.getPlayer(), event.getItemDrop().getItemStack()));
            return;
        }
        if (!plugin.isDiscardProtectedBoatItem(event.getItemDrop().getItemStack())) {
            return;
        }
        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        if (plugin.shouldDiscardSupplyBoatNow(event.getPlayer())) {
            event.getItemDrop().remove();
            plugin.notifySupplyBoatDiscarded(event.getPlayer());
            return;
        }
        event.setCancelled(true);
        event.getItemDrop().remove();
        plugin.notifySupplyBoatDropHint(event.getPlayer());
    }

    private Location normalizeBedFoot(Block block) {
        if (!(block.getBlockData() instanceof Bed bed)) {
            return block.getLocation();
        }
        if (bed.getPart() == Bed.Part.HEAD) {
            return block.getRelative(bed.getFacing().getOppositeFace()).getLocation();
        }
        return block.getLocation();
    }

    private void consumeOnePlacedBed(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        ItemStack handItem = event.getItemInHand();
        int consumed = plugin.isTeamBedItem(handItem) ? Math.min(2, handItem.getAmount()) : 1;
        int nextAmount = handItem.getAmount() - consumed;
        if (nextAmount <= 0) {
            event.getPlayer().getInventory().setItem(event.getHand(), null);
            return;
        }
        handItem.setAmount(nextAmount);
        event.getPlayer().getInventory().setItem(event.getHand(), handItem);
    }

    private void consumeResidualPlacedTeamBedToken(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        ItemStack handItem = player.getInventory().getItem(hand);
        if (!plugin.isTeamBedItem(handItem)) {
            return;
        }
        int nextAmount = handItem.getAmount() - 1;
        if (nextAmount <= 0) {
            player.getInventory().setItem(hand, null);
            return;
        }
        handItem.setAmount(nextAmount);
        player.getInventory().setItem(hand, handItem);
    }

    private Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof TNTPrimed primed && primed.getSource() instanceof Player player) {
            return player;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }
}
