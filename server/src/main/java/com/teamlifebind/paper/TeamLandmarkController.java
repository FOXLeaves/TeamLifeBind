package com.teamlifebind.paper;

import com.teamlifebind.common.LandmarkLoot;
import com.teamlifebind.common.LandmarkPlacement;
import com.teamlifebind.common.LandmarkStructures;
import com.teamlifebind.common.SpellCatalogue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Builds seed-stable landmarks during new chunk population, entirely inside LimitedRegion. */
final class TeamLandmarkController implements Listener, AutoCloseable {
    private static final NamespacedKey LOOT_MARKER = new NamespacedKey("teamlifebind", "landmark_loot");
    private static final NamespacedKey NATURAL_ROLLED = new NamespacedKey("teamlifebind", "magic_natural_rolled");
    private final TeamLifeBindPaperPlugin plugin;
    private final String lobbyWorld;
    private final boolean enabled;
    private final Map<LandmarkStructures.BlockSpec, BlockData> blockData;
    private final Map<String, ItemStack> customItems;
    private final BlockPopulator populator = new LandmarkPopulator();

    TeamLandmarkController(TeamLifeBindPaperPlugin plugin) {
        this.plugin = plugin;
        lobbyWorld = plugin.getConfig().getString("lobby-world-directory", "tlb_lobby");
        enabled = plugin.getConfig().getBoolean("world-landmarks.enabled", true);
        Map<LandmarkStructures.BlockSpec, BlockData> data = new HashMap<>();
        for (LandmarkStructures.Kind kind : LandmarkStructures.Kind.values()) {
            for (int rotation = 0; rotation < 4; rotation++) {
                for (var block : LandmarkStructures.rotate(LandmarkStructures.blueprint(kind), rotation).blocks()) {
                    data.computeIfAbsent(block.spec(), TeamLandmarkController::createBlockData);
                }
            }
        }
        blockData = Map.copyOf(data);
        Map<String, ItemStack> items = new HashMap<>();
        for (var spell : SpellCatalogue.all()) items.put("tlb:spell/" + spell.id(), plugin.magic().createBook(spell, null));
        for (String id : List.of("arcane_grimoire", "mana_potion", "greater_mana_potion", "mana_apple", "amnesia_tome")) {
            items.put("tlb:" + id, plugin.magic().createCompanion(id, null));
        }
        items.put("tlb:resonance_whistle", plugin.utilities().createWhistle(null));
        items.put("tlb:emergency_bandage", plugin.utilities().createBandage(null));
        items.put("tlb:silent_sigil", plugin.campaigns().createSigil(null));
        items.put("tlb:scout_rocket", plugin.campaigns().createRocket(null));
        customItems = Map.copyOf(items);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds()) attach(world);
    }

    @EventHandler public void onWorldInit(WorldInitEvent event) { attach(event.getWorld()); }
    @EventHandler public void onPluginDisable(PluginDisableEvent event) { if (event.getPlugin() == plugin) close(); }

    private void attach(World world) {
        if (!enabled || world.getEnvironment() != World.Environment.NORMAL || lobbyWorld.equals(world.getName())) return;
        if (!world.getPopulators().contains(populator)) world.getPopulators().add(populator);
    }

    @Override public void close() { for (World world : Bukkit.getWorlds()) world.getPopulators().remove(populator); }

    private static BlockData createBlockData(LandmarkStructures.BlockSpec spec) {
        StringBuilder value = new StringBuilder(spec.id());
        if (!spec.properties().isEmpty()) {
            value.append('[');
            boolean first = true;
            for (var entry : spec.properties().entrySet()) {
                if (!first) value.append(',');
                first = false;
                value.append(entry.getKey()).append('=').append(entry.getValue());
            }
            value.append(']');
        }
        return Bukkit.createBlockData(value.toString());
    }

    private final class LandmarkPopulator extends BlockPopulator {
        @Override public void populate(WorldInfo world, Random unused, int chunkX, int chunkZ, LimitedRegion region) {
            var candidate = LandmarkPlacement.candidate(world.getSeed(), Math.floorDiv(chunkX, 32), Math.floorDiv(chunkZ, 32));
            if (chunkX != (candidate.originX() >> 4) + 1 || chunkZ != (candidate.originZ() >> 4) + 1) return;
            var blueprint = candidate.blueprint();
            int x0 = candidate.originX(), z0 = candidate.originZ();
            if (!region.isInRegion(x0, world.getMinHeight(), z0)
                    || !region.isInRegion(x0 + blueprint.width() - 1, world.getMinHeight(), z0 + blueprint.depth() - 1)) return;
            var assessed = LandmarkPlacement.assess(candidate, (x, z) -> surface(region, x, z, world.getMinHeight()));
            if (assessed.isEmpty()) return;
            var site = assessed.get();
            int floorY = site.baseY();
            if (floorY + blueprint.height() >= world.getMaxHeight()) return;
            int[][] ground = validateFootprint(region, blueprint, x0, z0, floorY, world.getMinHeight());
            if (ground == null) return;
            for (var block : blueprint.blocks()) {
                int x = x0 + block.x(), y = floorY + block.y(), z = z0 + block.z();
                BlockData data = blockData.get(block.spec());
                if (block.y() == 0 && data.getMaterial().isSolid()) {
                    for (int supportY = y - 1; supportY > ground[block.x()][block.z()]; supportY--) {
                        region.setType(x, supportY, z, Material.STONE_BRICKS);
                    }
                }
                region.setBlockData(x, y, z, data);
            }
            for (int index = 0; index < blueprint.lootPoints().size(); index++) {
                var point = blueprint.lootPoints().get(index);
                var state = region.getBlockState(x0 + point.x(), floorY + point.y(), z0 + point.z());
                if (!(state instanceof Container container)) throw new IllegalStateException("Landmark loot point is not a container");
                long seed = candidate.lootSeed() ^ (0x9e3779b97f4a7c15L * (index + 1));
                fill(container, point.tier(), seed);
                container.update(true, false);
            }
        }
    }

    private static LandmarkPlacement.Surface surface(LimitedRegion region, int x, int z, int minY) {
        int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        // Some generators retain a height map above replaceable surface vegetation.
        while (y > minY && naturalAir(region.getType(x, y, z))) y--;
        Material ground = region.getType(x, y, z);
        return new LandmarkPlacement.Surface(y, ground.isSolid(), ground != Material.WATER && ground != Material.LAVA,
            naturalGround(ground));
    }

    private static int[][] validateFootprint(LimitedRegion region, LandmarkStructures.Blueprint blueprint, int x0, int z0, int floorY, int minY) {
        for (var state : region.getTileEntities()) {
            if (state instanceof TileState && state.getX() >= x0 && state.getX() < x0 + blueprint.width()
                    && state.getZ() >= z0 && state.getZ() < z0 + blueprint.depth()
                    && state.getY() >= floorY - 5 && state.getY() <= floorY + blueprint.height()) return null;
        }
        int[][] ground = new int[blueprint.width()][blueprint.depth()];
        for (int dx = 0; dx < blueprint.width(); dx++) for (int dz = 0; dz < blueprint.depth(); dz++) {
            int x = x0 + dx, z = z0 + dz;
            var surface = surface(region, x, z, minY);
            if (!surface.solid() || !surface.dry() || !surface.replaceable()
                    || surface.height() >= floorY || floorY - surface.height() > 5) return null;
            ground[dx][dz] = surface.height();
            for (int y = surface.height() + 1; y <= floorY + blueprint.height(); y++) {
                if (!naturalAir(region.getType(x, y, z))) return null;
            }
        }
        return ground;
    }

    private static boolean naturalGround(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM, SAND, RED_SAND, GRAVEL,
                 STONE, DEEPSLATE, GRANITE, DIORITE, ANDESITE, CALCITE, TUFF, TERRACOTTA, SNOW_BLOCK -> true;
            default -> false;
        };
    }

    private static boolean naturalAir(Material material) {
        if (material.isAir() || material.name().endsWith("_LEAVES")) return true;
        return switch (material) {
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH, SNOW, DANDELION, POPPY,
                 BLUE_ORCHID, ALLIUM, AZURE_BLUET, RED_TULIP, ORANGE_TULIP, WHITE_TULIP, PINK_TULIP,
                 OXEYE_DAISY, CORNFLOWER, LILY_OF_THE_VALLEY, SUNFLOWER, LILAC, ROSE_BUSH, PEONY,
                 BROWN_MUSHROOM, RED_MUSHROOM, PINK_PETALS, WILDFLOWERS, LEAF_LITTER,
                 SHORT_DRY_GRASS, TALL_DRY_GRASS, BUSH, FIREFLY_BUSH -> true;
            default -> false;
        };
    }

    private void fill(Container container, LandmarkStructures.LootTier tier, long seed) {
        if (container.getPersistentDataContainer().has(LOOT_MARKER, PersistentDataType.BYTE)) return;
        var inventory = container.getSnapshotInventory();
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) slots.add(slot);
        Collections.shuffle(slots, new Random(seed ^ 0x632be59bd9b4e019L));
        int nextSlot = 0;
        for (var item : LandmarkLoot.roll(tier, new Random(seed))) {
            ItemStack prototype = customItems.get(item.id());
            if (prototype == null && item.id().startsWith("minecraft:")) {
                Material type = Material.matchMaterial(item.id());
                if (type != null && type.isItem() && !type.isAir()) prototype = new ItemStack(type);
            }
            if (prototype == null) throw new IllegalStateException("Unsupported landmark loot: " + item.id());
            int remaining = item.count();
            while (remaining > 0 && nextSlot < slots.size()) {
                ItemStack stack = prototype.clone();
                int amount = Math.min(remaining, stack.getMaxStackSize());
                stack.setAmount(amount);
                inventory.setItem(slots.get(nextSlot++), stack);
                remaining -= amount;
            }
        }
        container.getPersistentDataContainer().set(LOOT_MARKER, PersistentDataType.BYTE, (byte) 1);
        container.getPersistentDataContainer().set(NATURAL_ROLLED, PersistentDataType.BYTE, (byte) 1);
    }
}
