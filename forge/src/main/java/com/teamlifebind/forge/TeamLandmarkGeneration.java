package com.teamlifebind.forge;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Seed-stable, bounded landmarks placed only while new chunks receive biome decoration. */
public final class TeamLandmarkGeneration {
    private static final int UPDATE_FLAGS = 2 | 16;
    private static final BlockState FOUNDATION = Blocks.STONE_BRICKS.defaultBlockState();

    private TeamLandmarkGeneration() { }

    public static void generate(WorldGenLevel world, ChunkAccess owner, long seed) {
        // A WorldGenRegion supplies a bounded write neighbourhood and serializes overlapping generation work.
        if (!(world instanceof WorldGenRegion region) || !world.dimensionType().hasSkyLight()
                || world.dimensionType().hasCeiling() || world.dimensionType().hasEnderDragonFight()
                || world.getLevel().dimension().identifier().getPath().equals("lobby_overworld")) return;
        int chunkX = owner.getPos().x(), chunkZ = owner.getPos().z();
        var candidate = LandmarkPlacement.candidate(seed, Math.floorDiv(chunkX, 32), Math.floorDiv(chunkZ, 32));
        if (chunkX != candidate.ownerChunkX() || chunkZ != candidate.ownerChunkZ()) return;
        var blueprint = candidate.blueprint();
        int x0 = candidate.originX(), z0 = candidate.originZ();
        if (!region.isWithinWriteZone(new BlockPos(x0, world.getMinY(), z0))
                || !region.isWithinWriteZone(new BlockPos(x0 + blueprint.width() - 1, world.getMinY(), z0 + blueprint.depth() - 1))) return;
        var assessed = LandmarkPlacement.assess(candidate, (x, z) -> surface(world, x, z));
        if (assessed.isEmpty()) return;
        int floorY = assessed.get().baseY();
        if (floorY + blueprint.height() >= world.getMaxY()) return;
        int[][] ground = validateFootprint(world, blueprint, x0, z0, floorY);
        if (ground == null) return;
        // Resolve the small, immutable palette before the first write; malformed states cannot leave half a building.
        Map<LandmarkStructures.BlockSpec, BlockState> palette = Palette.STATES;
        for (var block : blueprint.blocks()) {
            int x = x0 + block.x(), y = floorY + block.y(), z = z0 + block.z();
            BlockState state = palette.get(block.spec());
            if (block.y() == 0 && state.isSolid()) {
                for (int supportY = y - 1; supportY > ground[block.x()][block.z()]; supportY--) {
                    world.setBlock(new BlockPos(x, supportY, z), FOUNDATION, UPDATE_FLAGS);
                }
            }
            world.setBlock(new BlockPos(x, y, z), state, UPDATE_FLAGS);
        }
        for (int index = 0; index < blueprint.lootPoints().size(); index++) {
            var point = blueprint.lootPoints().get(index);
            BlockPos pos = new BlockPos(x0 + point.x(), floorY + point.y(), z0 + point.z());
            BlockState state = world.getBlockState(pos);
            if (!state.is(Blocks.BARREL)) throw new IllegalStateException("Landmark loot point is not a barrel");
            long lootSeed = candidate.lootSeed() ^ (0x9e3779b97f4a7c15L * (index + 1));
            FilledBarrel barrel = new FilledBarrel(pos, state);
            barrel.fill(point.tier(), lootSeed);
            // ProtoChunk removes its pending dummy NBT here. Items persist in the normal vanilla barrel format.
            ChunkAccess chunk = world.getChunk(pos);
            chunk.setBlockEntity(barrel);
            chunk.markUnsaved();
        }
    }

    private static LandmarkPlacement.Surface surface(WorldGenLevel world, int x, int z) {
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        while (y > world.getMinY() && naturalAir(world.getBlockState(pos))) pos.setY(--y);
        BlockState ground = world.getBlockState(pos);
        return new LandmarkPlacement.Surface(y, ground.isSolid(), ground.getFluidState().isEmpty(), naturalGround(ground));
    }

    private static int[][] validateFootprint(WorldGenLevel world, LandmarkStructures.Blueprint blueprint, int x0, int z0, int floorY) {
        int[][] heights = new int[blueprint.width()][blueprint.depth()];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < blueprint.width(); dx++) for (int dz = 0; dz < blueprint.depth(); dz++) {
            int x = x0 + dx, z = z0 + dz;
            var ground = surface(world, x, z);
            if (!ground.solid() || !ground.dry() || !ground.replaceable() || ground.height() >= floorY
                    || floorY - ground.height() > LandmarkPlacement.MAX_FOUNDATION_DEPTH) return null;
            heights[dx][dz] = ground.height();
            for (int y = floorY - LandmarkPlacement.MAX_FOUNDATION_DEPTH; y <= floorY + blueprint.height(); y++) {
                BlockState existing = world.getBlockState(pos.set(x, y, z));
                if (existing.hasBlockEntity() || y > ground.height() && !naturalAir(existing)) return null;
            }
        }
        return heights;
    }

    private static boolean naturalGround(BlockState state) {
        return switch (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()) {
            case "grass_block", "dirt", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "sand", "red_sand", "gravel",
                 "stone", "deepslate", "granite", "diorite", "andesite", "calcite", "tuff", "terracotta", "snow_block" -> true;
            default -> false;
        };
    }

    private static boolean naturalAir(BlockState state) {
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (!state.getFluidState().isEmpty()) return false;
        if (state.isAir() || name.endsWith("_leaves")) return true;
        return switch (name) {
            case "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "snow", "dandelion", "poppy",
                 "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
                 "oxeye_daisy", "cornflower", "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony",
                 "brown_mushroom", "red_mushroom", "pink_petals", "wildflowers", "leaf_litter",
                 "short_dry_grass", "tall_dry_grass", "bush", "firefly_bush" -> true;
            default -> false;
        };
    }

    /** Fully parsed once after registry bootstrap; subsequent generation workers perform only map lookups. */
    private static final class Palette {
        private static final Map<LandmarkStructures.BlockSpec, BlockState> STATES = create();
        private static Map<LandmarkStructures.BlockSpec, BlockState> create() {
            Map<LandmarkStructures.BlockSpec, BlockState> states = new HashMap<>();
            for (LandmarkStructures.Kind kind : LandmarkStructures.Kind.values()) for (int rotation = 0; rotation < 4; rotation++) {
                for (var block : LandmarkStructures.blueprint(kind, rotation).blocks()) {
                    states.computeIfAbsent(block.spec(), Palette::parse);
                }
            }
            return Map.copyOf(states);
        }
        private static BlockState parse(LandmarkStructures.BlockSpec spec) {
            StringBuilder text = new StringBuilder(spec.id());
            if (!spec.properties().isEmpty()) {
                text.append('[');
                boolean first = true;
                for (var entry : spec.properties().entrySet()) {
                    if (!first) text.append(',');
                    first = false;
                    text.append(entry.getKey()).append('=').append(entry.getValue());
                }
                text.append(']');
            }
            try { return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text.toString(), false).blockState(); }
            catch (CommandSyntaxException exception) { throw new IllegalArgumentException("Invalid landmark palette state " + text, exception); }
        }
    }

    private static ItemStack lootItem(String id) {
        if (id.startsWith("tlb:spell/")) {
            var spell = SpellCatalogue.byId(id.substring("tlb:spell/".length()));
            if (spell == null) throw new IllegalArgumentException("Unknown landmark spell " + id);
            ItemStack stack = new ItemStack(TeamLifeBindForge.magicItem(TeamMagicItem.Kind.SPELL_BOOK));
            CompoundTag data = new CompoundTag();
            data.putString(TeamMagicController.SPELL_ID, spell.id());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable("spell.teamlifebind." + spell.id()));
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) spell.tier()), List.of(), List.of(), List.of(TeamMagicRuntime.color(spell.school()))));
            stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("T" + spell.tier() + " | MP " + spell.manaCost() + " | CD " + spell.cooldownTicks() / 20.0 + "s"),
                Component.literal(spell.zhDescription()), Component.literal(spell.enDescription()))));
            return stack;
        }
        if (id.startsWith("tlb:")) {
            String key = id.substring(4);
            for (var kind : TeamMagicItem.Kind.values()) if (kind.id().equals(key) && kind != TeamMagicItem.Kind.SPELL_BOOK) {
                return new ItemStack(TeamLifeBindForge.magicItem(kind));
            }
            throw new IllegalArgumentException("Unsupported landmark custom item " + id);
        }
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow(() -> new IllegalArgumentException("Unknown landmark item " + id));
        return new ItemStack(item);
    }

    /** Inventory initialization runs before this vanilla-format BE is published to the generation chunk. */
    private static final class FilledBarrel extends BarrelBlockEntity {
        private FilledBarrel(BlockPos pos, BlockState state) { super(pos, state); }
        private void fill(LandmarkStructures.LootTier tier, long seed) {
            List<Integer> slots = new ArrayList<>();
            for (int slot = 0; slot < getContainerSize(); slot++) slots.add(slot);
            Collections.shuffle(slots, new Random(seed ^ 0x632be59bd9b4e019L));
            int next = 0;
            for (var item : LandmarkLoot.roll(tier, new Random(seed))) {
                ItemStack prototype = lootItem(item.id());
                int remaining = item.count();
                while (remaining > 0 && next < slots.size()) {
                    ItemStack stack = prototype.copy();
                    int amount = Math.min(remaining, stack.getMaxStackSize());
                    stack.setCount(amount);
                    getItems().set(slots.get(next++), stack);
                    remaining -= amount;
                }
            }
        }
    }
}
