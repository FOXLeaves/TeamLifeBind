package com.teamlifebind.paper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.ComplexEntityPart;
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
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
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
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
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
        plugin.magic().stopTest(event.getEntity(), false);
        plugin.magic().interrupt(event.getEntity());
        event.getDrops().removeIf(TeamMagicController::isTestFocus);
        event.getDrops().removeIf(plugin::isSupplyBoatItem);
        if (plugin.isMatchRunning()) {
            event.setDeathMessage(null);
        }
        plugin.handlePlayerDeath(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (TeamMagicController.isMagicItem(event.getItem())) { event.setCancelled(true); return; }
        if (TeamGunController.profile(event.getItem()) != null) {
            event.setCancelled(true);
            return;
        }
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
        if (plugin.campaigns().dragonKilled(dragon)) return;

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
        plugin.magic().login(event.getPlayer());
        plugin.handlePlayerJoin(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.syncManagedPlayerState(event.getPlayer()));
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        plugin.magic().interrupt(event.getPlayer());
        plugin.utilities().interrupt(event.getPlayer());
        plugin.guns().cancel(event.getPlayer().getUniqueId());
        plugin.campaigns().enteredWorld(event.getPlayer());
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
        plugin.magic().disconnect(event.getPlayer());
        plugin.campaigns().disconnect(event.getPlayer());
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCampaignDamage(EntityDamageEvent event) {
        if (event.getDamage() <= 0.0D) return;
        if (event.getFinalDamage() > 0 && event.getEntity() instanceof Player victim) plugin.magic().interruptCasting(victim);
        if (event.getEntity() instanceof Player victim) plugin.campaigns().recordDamage(victim);
        if (!(event instanceof EntityDamageByEntityEvent attack)) return;
        Player attacker = resolvePlayerDamager(attack.getDamager());
        if (attacker == null) return;
        plugin.campaigns().recordDamage(attacker);
        Entity target = event.getEntity();
        EnderDragon dragon = target instanceof EnderDragon direct ? direct
            : target instanceof ComplexEntityPart part && part.getParent() instanceof EnderDragon parent ? parent : null;
        if (dragon != null) plugin.campaigns().dragonDamage(attacker, dragon, event.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        if (event.getDamage() > 0.0D) {
            plugin.utilities().interrupt(target);
            if (event instanceof EntityDamageByEntityEvent attack) {
                plugin.utilities().interrupt(resolvePlayerDamager(attack.getDamager()));
            }
        }
        if (plugin.tryUseTeamTotem(target, event.getFinalDamage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaTotemResurrection(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getHand() == null) {
            return;
        }
        ItemStack heldItem = player.getInventory().getItem(event.getHand());
        if (plugin.shouldBlockVanillaTotem(player, heldItem)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (!plugin.isDeathExemptionTotemItem(result)) {
            return;
        }
        Player viewer = event.getView().getPlayer() instanceof Player player ? player : null;
        plugin.refreshTotemItemLore(viewer, result);
        event.setResult(result);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (plugin.utilities().locksInventory(event.getInventory())) {
            event.setCancelled(true);
            if (event.getPlayer() instanceof Player player) {
                player.sendMessage(plugin.text(player, "supply.claim.locked"));
            }
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            plugin.refreshTotemInventoryLore(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTotemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && plugin.isDeathExemptionTotemItem(event.getItem().getItemStack())) {
            ItemStack stack = event.getItem().getItemStack();
            plugin.refreshTotemItemLore(player, stack);
            event.getItem().setItemStack(stack);
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
        if (plugin.magic().menuClick(event)) return;
        if (plugin.campaigns().menuClick(event)) return;
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (TeamMagicController.isTestFocus(event.getCurrentItem()) || TeamMagicController.isTestFocus(event.getCursor())
            || (event.getClick() == ClickType.NUMBER_KEY && TeamMagicController.isTestFocus(player.getInventory().getItem(event.getHotbarButton())))) {
            if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING && event.getView().getTopInventory().getType() != InventoryType.CREATIVE) { event.setCancelled(true); return; }
        }
        if (event.getAction() != InventoryAction.NOTHING
            && ((event.getClickedInventory() == player.getInventory() && plugin.utilities().usesInventorySlot(player, event.getSlot()))
                || (event.getClick() == ClickType.NUMBER_KEY && plugin.utilities().usesInventorySlot(player, event.getHotbarButton()))
                || (event.getClick() == ClickType.SWAP_OFFHAND && plugin.utilities().usesInventorySlot(player, 40)))) {
            plugin.utilities().changedInventory(player);
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
        if (TeamMagicController.isTestFocus(event.getOldCursor()) && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) { event.setCancelled(true); return; }
        if (plugin.magic().isMenu(event.getView().getTopInventory())) { event.setCancelled(true); return; }
        if (plugin.campaigns().isMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            for (int rawSlot : event.getRawSlots()) {
                if (event.getView().getInventory(rawSlot) == player.getInventory()
                    && plugin.utilities().usesInventorySlot(player, event.getView().convertSlot(rawSlot))) {
                    plugin.utilities().changedInventory(player);
                    break;
                }
            }
        }
        if (plugin.isLobbyMenuTitle(event.getView().getTitle()) || plugin.isDevMenuTitle(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (java.util.Arrays.stream(event.getInventory().getMatrix()).anyMatch(TeamMagicController::isTestFocus)) { event.getInventory().setResult(null); return; }
        if (event.getRecipe() != null && plugin.campaigns().isItem(event.getRecipe().getResult())) {
            Player viewer = event.getViewers().stream().filter(Player.class::isInstance).map(Player.class::cast).findFirst().orElse(null);
            event.getInventory().setResult(plugin.campaigns().localizedItem(viewer, event.getRecipe().getResult()));
            return;
        }
        if (event.getRecipe() != null && plugin.utilities().isUtilityItem(event.getRecipe().getResult())) {
            Player viewer = event.getViewers().stream().filter(Player.class::isInstance).map(Player.class::cast).findFirst().orElse(null);
            event.getInventory().setResult(plugin.utilities().localizedItem(viewer, event.getRecipe().getResult()));
            return;
        }
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
        if (plugin.utilities().isLocked(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
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
        if (TeamMagicController.isTestFocus(event.getItemDrop().getItemStack())) { event.setCancelled(true); return; }
        plugin.magic().interruptCasting(event.getPlayer());
        plugin.utilities().changedHotbar(event.getPlayer());
        plugin.guns().cancel(event.getPlayer().getUniqueId());
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

    @EventHandler
    public void onUtilityInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (plugin.magic().use(event.getPlayer(), event.getHand(), event.getItem())) {
            event.setUseInteractedBlock(Event.Result.DENY); event.setUseItemInHand(Event.Result.DENY); return;
        }
        if (TeamGunController.profile(event.getItem()) != null) {
            if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            boolean allowed = event.getHand() == EquipmentSlot.HAND && plugin.guns().beginUse(event.getPlayer());
            event.setUseItemInHand(allowed ? org.bukkit.event.Event.Result.ALLOW : org.bukkit.event.Event.Result.DENY);
            return;
        }
        if (plugin.campaigns().use(event.getPlayer(), event.getHand(), event.getItem())
            || plugin.utilities().use(event.getPlayer(), event.getHand(), event.getItem())) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && plugin.utilities().isLocked(event.getClickedBlock())) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                plugin.utilities().beginSupplyClaim(event.getPlayer(), event.getClickedBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUtilityAttack(EntityDamageByEntityEvent event) {
        plugin.utilities().interrupt(resolvePlayerDamager(event.getDamager()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onGunEntityUse(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        // The Paper carrier item must never become horse equipment.
        if (TeamGunController.profile(event.getPlayer().getInventory().getItem(event.getHand())) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGunDispense(org.bukkit.event.block.BlockDispenseEvent event) {
        if (event instanceof org.bukkit.event.block.BlockDispenseArmorEvent && TeamGunController.profile(event.getItem()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUtilityHotbarChange(PlayerItemHeldEvent event) {
        plugin.magic().interruptCasting(event.getPlayer());
        plugin.utilities().changedHotbar(event.getPlayer());
        plugin.guns().cancel(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onUtilityHandSwap(PlayerSwapHandItemsEvent event) {
        if (plugin.magic().swap(event.getPlayer())) { event.setCancelled(true); return; }
        plugin.magic().interruptCasting(event.getPlayer());
        if (plugin.guns().requestReload(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        plugin.utilities().changedInventory(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMagicNaturalLoot(org.bukkit.event.world.LootGenerateEvent event) { plugin.magic().naturalLoot(event); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMagicContainerPlaced(BlockPlaceEvent event) { plugin.magic().placedContainer(event.getBlockPlaced()); }

    @EventHandler(ignoreCancelled = true)
    public void onSupplyTransfer(InventoryMoveItemEvent event) {
        if (TeamMagicController.isTestFocus(event.getItem())) { event.setCancelled(true); return; }
        if (plugin.utilities().locksInventory(event.getSource()) || plugin.utilities().locksInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSupplyExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(plugin.utilities()::isLocked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSupplyBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(plugin.utilities()::isLocked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSupplyPiston(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(plugin.utilities()::isLocked)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSupplyPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(plugin.utilities()::isLocked)) {
            event.setCancelled(true);
        }
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
