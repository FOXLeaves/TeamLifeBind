package com.teamlifebind.paper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Survival state is kept apart from the lobby and disposable competition inventories. */
final class SurvivalProfiles {
    static final String WORLD_NAME = "tlb_survival";
    private final TeamLifeBindPaperPlugin plugin;
    private final Path directory;

    SurvivalProfiles(TeamLifeBindPaperPlugin plugin) {
        this.plugin = plugin;
        directory = plugin.getDataFolder().toPath().resolve("survival-playerdata");
    }

    static boolean isSurvivalWorld(World world) {
        if (world == null) return false;
        String name = world.getName();
        return name.equals(WORLD_NAME) || name.equals(WORLD_NAME + "_nether") || name.equals(WORLD_NAME + "_the_end");
    }

    boolean save(Player player) {
        if (player == null) return true;
        if (!applyOfflineDeath(player)) return false;
        if (!isSurvivalWorld(player.getWorld())) return true;
        if (player.isDead()) return forget(player.getUniqueId());
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return true;
        try {
            player.closeInventory();
            YamlConfiguration data = new YamlConfiguration();
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack item : player.getInventory().getContents())
                items.add(item == null || TeamMagicController.isTestFocus(item) ? null : item.clone());
            data.set("inventory", items);
            data.set("held", player.getInventory().getHeldItemSlot());
            data.set("level", player.getLevel());
            data.set("experience", player.getExp());
            data.set("total-experience", player.getTotalExperience());
            data.set("food", player.getFoodLevel());
            data.set("saturation", player.getSaturation());
            Location at = player.getLocation();
            data.set("world", at.getWorld().getName());
            data.set("x", at.getX()); data.set("y", at.getY()); data.set("z", at.getZ());
            data.set("yaw", at.getYaw()); data.set("pitch", at.getPitch());
            byte[] encoded = data.saveToString().getBytes(StandardCharsets.UTF_8);
            if (encoded.length > 2097152) throw new java.io.IOException("Survival profile exceeds 2 MiB");
            Files.createDirectories(directory);
            Path target = path(player.getUniqueId()), pending = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(pending, encoded);
            try { Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (Exception error) { plugin.getLogger().warning("Cannot save survival profile " + player.getUniqueId() + ": " + error.getMessage()); return false; }
    }

    /** Consuming the snapshot makes a later death unable to replay an earlier inventory. */
    Location restore(Player player) {
        if (!applyOfflineDeath(player)) return null;
        Path target = path(player.getUniqueId());
        if (!Files.isRegularFile(target)) return null;
        try {
            if (Files.size(target) > 2097152) throw new java.io.IOException("Survival profile exceeds 2 MiB");
            YamlConfiguration data = new YamlConfiguration();
            data.loadFromString(Files.readString(target, StandardCharsets.UTF_8));
            List<?> saved = data.getList("inventory", List.of());
            if (saved.size() > player.getInventory().getSize()) throw new IllegalArgumentException("Invalid survival inventory size");
            ItemStack[] contents = new ItemStack[player.getInventory().getSize()];
            for (int index = 0; index < saved.size(); index++) {
                Object item = saved.get(index);
                if (item != null && !(item instanceof ItemStack)) throw new IllegalArgumentException("Invalid survival item");
                contents[index] = item == null || TeamMagicController.isTestFocus((ItemStack) item) ? null : ((ItemStack) item).clone();
            }
            String worldName = data.getString("world", WORLD_NAME);
            World world = Bukkit.getWorld(worldName);
            if (world == null && (worldName.equals(WORLD_NAME + "_nether") || worldName.equals(WORLD_NAME + "_the_end")))
                world = new org.bukkit.WorldCreator(worldName).environment(worldName.endsWith("_nether") ? World.Environment.NETHER : World.Environment.THE_END).createWorld();
            double x = data.getDouble("x"), y = data.getDouble("y"), z = data.getDouble("z");
            float yaw = (float) data.getDouble("yaw"), pitch = (float) data.getDouble("pitch");
            if (!isSurvivalWorld(world) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 29999984 || Math.abs(z) > 29999984 || y < world.getMinHeight() || y >= world.getMaxHeight()
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Invalid survival location");
            double experience = data.getDouble("experience"), saturation = data.getDouble("saturation", 5);
            if (!Double.isFinite(experience) || !Double.isFinite(saturation)) throw new IllegalArgumentException("Invalid survival experience/food");
            Files.delete(target);
            player.getInventory().setContents(contents);
            player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, data.getInt("held"))));
            player.setTotalExperience(Math.max(0, data.getInt("total-experience")));
            player.setLevel(Math.max(0, Math.min(21863, data.getInt("level"))));
            player.setExp((float) Math.max(0, Math.min(0.999999, experience)));
            player.setFoodLevel(Math.max(0, Math.min(20, data.getInt("food", 20))));
            player.setSaturation((float) Math.max(0, Math.min(20, saturation)));
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception error) {
            plugin.getLogger().warning("Cannot restore survival profile " + player.getUniqueId() + ": " + error.getMessage());
            return null;
        }
    }

    boolean forget(UUID player) {
        try { Files.deleteIfExists(path(player)); return true; }
        catch (java.io.IOException error) { plugin.getLogger().warning("Cannot invalidate survival profile " + player + ": " + error.getMessage()); return false; }
    }
    /** This tombstone survives a restart even if the absent player never reconnects this round. */
    boolean markOfflineDeath(UUID player, boolean keepInventory) {
        try {
            Path checkpoint = path(player);
            if (Files.isRegularFile(checkpoint) && Files.size(checkpoint) <= 2097152) {
                YamlConfiguration previous = new YamlConfiguration();
                previous.loadFromString(Files.readString(checkpoint, StandardCharsets.UTF_8));
                World previousWorld = Bukkit.getWorld(previous.getString("world", WORLD_NAME));
                if (isSurvivalWorld(previousWorld))
                    keepInventory = Boolean.TRUE.equals(previousWorld.getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY));
            }
            YamlConfiguration data = new YamlConfiguration();
            data.set("version", 1); data.set("keep-inventory", keepInventory);
            Files.createDirectories(directory);
            Path target = deathPath(player), pending = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(pending, data.saveToString(), StandardCharsets.UTF_8);
            try { Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING); }
            return forget(player);
        } catch (Exception error) { plugin.getLogger().warning("Cannot record offline survival death " + player + ": " + error.getMessage()); return false; }
    }
    boolean applyOfflineDeath(Player player) {
        Path marker = deathPath(player.getUniqueId());
        if (!Files.exists(marker)) return true;
        try {
            if (Files.size(marker) > 2048) throw new java.io.IOException("Invalid offline death marker");
            YamlConfiguration data = new YamlConfiguration();
            data.loadFromString(Files.readString(marker, StandardCharsets.UTF_8));
            if (data.getInt("version") != 1 || !data.isBoolean("keep-inventory")) throw new IllegalArgumentException("Invalid offline death marker");
            player.closeInventory();
            if (!data.getBoolean("keep-inventory")) {
                for (ItemStack item : player.getInventory().getContents())
                    if (item != null && !item.getType().isAir() && !TeamMagicController.isTestFocus(item))
                        player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
                player.getInventory().clear(); player.setLevel(0); player.setExp(0); player.setTotalExperience(0);
            }
            if (!forget(player.getUniqueId())) return false;
            Files.delete(marker);
            return true;
        } catch (Exception error) { plugin.getLogger().warning("Cannot apply offline survival death " + player.getUniqueId() + ": " + error.getMessage()); return false; }
    }
    private Path path(UUID player) { return directory.resolve(player + ".yml"); }
    private Path deathPath(UUID player) { return directory.resolve(player + ".dead"); }
}
