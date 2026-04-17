package com.teamlifebind.paper;

import org.bukkit.Bukkit;
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
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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
        if (plugin.isMatchRunning()) {
            event.setDeathMessage(null);
        }
        plugin.handlePlayerDeath(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (plugin.isLifeCursePotionItem(event.getItem())) {
            plugin.handleLifeCursePotionConsumed(event.getPlayer());
            return;
        }
        if (event.getItem().getType() == org.bukkit.Material.MILK_BUCKET) {
            plugin.handleMilkBucketConsumed(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeamBedPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.isTeamBedItem(event.getItem().getItemStack())) {
            return;
        }
        ItemStack droppedStack = event.getItem().getItemStack();
        if (droppedStack.getAmount() < 2) {
            droppedStack.setAmount(2);
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
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.teleportToLobby(event.getPlayer());
                plugin.sendLobbyGuide(event.getPlayer());
            });
            return;
        }
        if (plugin.isRespawnLocked(event.getPlayer().getUniqueId())) {
            plugin.handleBlockedRespawn(event.getPlayer());
            return;
        }
        if (plugin.isUnassignedMatchPlayer(event.getPlayer().getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.moveUnassignedPlayerToSpectator(event.getPlayer(), true));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.beginRespawnCountdown(event.getPlayer()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.handlePlayerJoin(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.syncManagedPlayerState(event.getPlayer()));
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.enforceSingleLobby(event.getPlayer());
            plugin.syncManagedPlayerState(event.getPlayer());
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || !plugin.isMatchObservationLocked(event.getPlayer().getUniqueId())) {
            return;
        }
        var from = event.getFrom();
        var to = event.getTo();
        if (from.getX() == to.getX()
            && from.getY() == to.getY()
            && from.getZ() == to.getZ()) {
            return;
        }
        var locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLifeCurseDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        Player attacker = resolvePlayerDamager(event.getDamager());
        if (attacker == null) {
            return;
        }
        plugin.handleLifeCurseAttack(attacker, target, event.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        if (plugin.tryUseTeamTotem(target, event.getFinalDamage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLobbyMenuInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (plugin.isTeamModeVoteItem(event.getItem())) {
            event.setCancelled(true);
            plugin.toggleTeamModeVote(event.getPlayer());
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
        if (plugin.isLobbyMenuTitle(event.getView().getTitle())) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getCurrentItem() == null) {
                return;
            }
            plugin.handleLobbyMenuClick(player, event.getCurrentItem(), event.getClick());
            return;
        }
        if (!plugin.isDevMenuTitle(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory() || event.getCurrentItem() == null) {
            return;
        }
        plugin.handleDevMenuClick(player, event.getCurrentItem());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLobbyMenuDrag(InventoryDragEvent event) {
        if (plugin.isLobbyMenuTitle(event.getView().getTitle()) || plugin.isDevMenuTitle(event.getView().getTitle())) {
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
            itemInPlaceHand(event),
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
        event.getBlock()
            .getWorld()
            .dropItemNaturally(event.getBlock().getLocation().add(0.5D, 0.5D, 0.5D), plugin.createOwnedTeamBedDrop(event.getPlayer(), ownerTeam));
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        event.setUseBed(Event.Result.DENY);
        event.getPlayer().sendMessage(ChatColor.YELLOW + plugin.text("bed.personal_spawn_disabled"));
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        if (!plugin.isManagedPortal(event.getFrom(), event.getCause())) {
            return;
        }
        event.setCancelled(true);
        Location redirected = plugin.resolvePortalDestination(event.getPlayer(), event.getFrom(), event.getCause());
        if (redirected == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                event.getPlayer().teleport(redirected, PlayerTeleportEvent.TeleportCause.PLUGIN);
                plugin.notifyEndTotemRestriction(event.getPlayer());
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.enforceSingleLobby(event.getPlayer());
            plugin.syncManagedPlayerState(event.getPlayer());
            plugin.notifyEndTotemRestriction(event.getPlayer());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropSupplyBoat(PlayerDropItemEvent event) {
        if (plugin.isTeamBedItem(event.getItemDrop().getItemStack())) {
            if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
                return;
            }
            ItemStack stack = event.getItemDrop().getItemStack();
            if (stack.getAmount() > 1) {
                stack.setAmount(1);
            }
            Bukkit.getScheduler().runTask(plugin, () -> plugin.consumeAllTeamBedTokens(event.getPlayer(), stack));
            return;
        }
        if (plugin.isInLobby(event.getPlayer()) && plugin.isProtectedLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getItemDrop().remove();
            plugin.notifyLobbyItemLocked(event.getPlayer());
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
        ItemStack handItem = itemInPlaceHand(event);
        int consumed = plugin.isTeamBedItem(handItem) ? Math.min(2, handItem.getAmount()) : 1;
        int nextAmount = handItem.getAmount() - consumed;
        if (nextAmount <= 0) {
            event.getPlayer().getInventory().setItem(event.getHand(), null);
            return;
        }
        handItem.setAmount(nextAmount);
        event.getPlayer().getInventory().setItem(event.getHand(), handItem);
    }

    private ItemStack itemInPlaceHand(BlockPlaceEvent event) {
        return event.getPlayer().getInventory().getItem(event.getHand());
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
