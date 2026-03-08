package com.teamlifebind.paper;

import org.bukkit.Location;
import org.bukkit.ChatColor;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

public final class TeamLifeBindListener implements Listener {

    private final TeamLifeBindPaperPlugin plugin;

    public TeamLifeBindListener(TeamLifeBindPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.handlePlayerDeath(event.getPlayer());
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }

        Player killer = dragon.getKiller();
        if (killer == null) {
            return;
        }
        plugin.handleDragonKill(killer);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.isMatchRunning()) {
            Location lobby = plugin.resolveLobbySpawn();
            if (lobby != null) {
                event.setRespawnLocation(lobby);
            }
            return;
        }
        if (plugin.isRespawnLocked(event.getPlayer().getUniqueId())) {
            plugin.handleBlockedRespawn(event.getPlayer());
            return;
        }
        Location respawn = plugin.resolveRespawnLocation(event.getPlayer().getUniqueId());
        if (respawn != null) {
            event.setRespawnLocation(respawn);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.onPlayerLeave(event.getPlayer().getUniqueId());
        if (plugin.isMatchRunning()) {
            return;
        }
        plugin.teleportToLobby(event.getPlayer());
        plugin.sendLobbyGuide(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.onPlayerLeave(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onBedPlace(BlockPlaceEvent event) {
        if (!Tag.BEDS.isTagged(event.getBlockPlaced().getType())) {
            return;
        }

        if (!plugin.isTeamBedItem(event.getItemInHand())) {
            event.setCancelled(true);
            consumeOnePlacedBed(event);
            plugin.explodeVanillaBed(event.getPlayer(), event.getBlockPlaced().getLocation());
            return;
        }

        Location bedFoot = normalizeBedFoot(event.getBlockPlaced());
        if (!plugin.registerPlacedTeamBed(event.getPlayer(), bedFoot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBedBreak(BlockBreakEvent event) {
        if (!Tag.BEDS.isTagged(event.getBlock().getType())) {
            return;
        }
        Location bedFoot = normalizeBedFoot(event.getBlock());
        plugin.clearTeamBedAt(bedFoot);
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.YELLOW + "[TLB] Beds do not set personal spawn points.");
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        Location redirected = plugin.resolvePortalDestination(event.getFrom(), event.getCause());
        if (redirected != null) {
            event.setTo(redirected);
        }
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
        ItemStack handItem = event.getItemInHand();
        int nextAmount = handItem.getAmount() - 1;
        if (nextAmount <= 0) {
            event.getPlayer().getInventory().setItem(event.getHand(), null);
            return;
        }
        handItem.setAmount(nextAmount);
        event.getPlayer().getInventory().setItem(event.getHand(), handItem);
    }
}
