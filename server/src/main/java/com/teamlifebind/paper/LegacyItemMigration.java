package com.teamlifebind.paper;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Retires pre-removal custom weapons without changing unmarked vanilla equipment. */
final class LegacyItemMigration {
    private static final NamespacedKey RETIRED_PROFILE = new NamespacedKey("teamlifebind", "gun_profile");

    private LegacyItemMigration() { }

    static boolean isRetired(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
            && stack.getItemMeta().getPersistentDataContainer().has(RETIRED_PROFILE);
    }

    static ItemStack retire(ItemStack stack) {
        // Rebuilding removes the old native consumable component as well as PDC/model/lore.
        // Merely removing the gun marker would leave the 3,600-second use action behind.
        return isRetired(stack) ? new ItemStack(Material.IRON_HORSE_ARMOR, stack.getAmount()) : stack;
    }

    static void retire(Inventory inventory) {
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack original = inventory.getItem(slot);
            if (isRetired(original)) inventory.setItem(slot, retire(original));
        }
    }

    static void retire(Player player) {
        retire(player.getInventory());
        retire(player.getEnderChest());
        ItemStack cursor = player.getItemOnCursor();
        if (isRetired(cursor)) player.setItemOnCursor(retire(cursor));
    }
}
