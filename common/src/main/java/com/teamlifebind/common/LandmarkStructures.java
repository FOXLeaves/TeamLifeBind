package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Hand-built exploration landmarks, expressed only with vanilla block states. */
public final class LandmarkStructures {
    public static final int MAX_BLOCKS = 20_000;
    public static final int MAX_SIZE = 48;
    public enum Kind { FORTRESS, TOWER, DEPOT }
    public enum LootTier { BASIC, MILITARY, ARCANE, TREASURE }

    public record BlockSpec(String id, Map<String, String> properties) {
        public BlockSpec {
            Objects.requireNonNull(id);
            if (!id.matches("minecraft:[a-z0-9_]+")) throw new IllegalArgumentException("Vanilla block id required");
            properties = Map.copyOf(Objects.requireNonNull(properties));
        }
        public BlockSpec(String id) { this(id, Map.of()); }
    }
    public record Block(int x, int y, int z, BlockSpec spec) {
        public Block { Objects.requireNonNull(spec); }
    }
    public record LootPoint(int x, int y, int z, LootTier tier) {
        public LootPoint { Objects.requireNonNull(tier); }
    }
    public record Blueprint(Kind kind, int width, int height, int depth, List<Block> blocks, List<LootPoint> lootPoints) {
        public Blueprint {
            Objects.requireNonNull(kind);
            if (width < 1 || width > MAX_SIZE || depth < 1 || depth > MAX_SIZE || height < 1 || height > 48) {
                throw new IllegalArgumentException("Landmark dimensions exceed generation neighbourhood");
            }
            blocks = List.copyOf(blocks);
            lootPoints = List.copyOf(lootPoints);
            if (blocks.size() > MAX_BLOCKS || lootPoints.size() < 4 || lootPoints.size() > 8) {
                throw new IllegalArgumentException("Landmark placement budget exceeded");
            }
        }
    }

    private static final BlockSpec AIR = state("air");
    private static final BlockSpec BRICK = state("stone_bricks");
    private static final BlockSpec MOSS = state("mossy_stone_bricks");
    private static final BlockSpec CRACKED = state("cracked_stone_bricks");
    private static final BlockSpec DEEPSLATE = state("deepslate_tiles");
    private static final BlockSpec OAK = state("oak_planks");
    private static final BlockSpec SPRUCE = state("spruce_planks");
    private static final BlockSpec GLASS = state("light_blue_stained_glass");
    private static final List<Blueprint> ALL = List.of(fortress(), tower(), depot());
    private static final Map<Kind, List<Blueprint>> ROTATED = rotations();

    private LandmarkStructures() { }
    public static List<Blueprint> all() { return ALL; }
    public static Blueprint blueprint(Kind kind) { return ALL.get(Objects.requireNonNull(kind).ordinal()); }
    public static Blueprint blueprint(Kind kind, int quarterTurns) { return ROTATED.get(Objects.requireNonNull(kind)).get(Math.floorMod(quarterTurns, 4)); }

    /** Clockwise rotation also rotates directional block properties; returned positions remain nonnegative. */
    public static Blueprint rotate(Blueprint blueprint, int quarterTurns) {
        Objects.requireNonNull(blueprint);
        int turns = Math.floorMod(quarterTurns, 4);
        if (turns == 0) return blueprint;
        if (blueprint == ALL.get(blueprint.kind.ordinal())) return blueprint(blueprint.kind, turns);
        return rotateUncached(blueprint, turns);
    }

    private static Map<Kind, List<Blueprint>> rotations() {
        Map<Kind, List<Blueprint>> result = new LinkedHashMap<>();
        for (Blueprint blueprint : ALL) {
            result.put(blueprint.kind, List.of(blueprint, rotateUncached(blueprint, 1), rotateUncached(blueprint, 2), rotateUncached(blueprint, 3)));
        }
        return Map.copyOf(result);
    }

    private static Blueprint rotateUncached(Blueprint source, int turns) {
        Blueprint current = source;
        for (int turn = 0; turn < turns; turn++) {
            List<Block> blocks = new ArrayList<>(current.blocks.size());
            for (Block block : current.blocks) blocks.add(new Block(current.depth - 1 - block.z, block.y, block.x, rotateState(block.spec)));
            List<LootPoint> loot = new ArrayList<>(current.lootPoints.size());
            for (LootPoint point : current.lootPoints) loot.add(new LootPoint(current.depth - 1 - point.z, point.y, point.x, point.tier));
            current = new Blueprint(current.kind, current.depth, current.height, current.width, blocks, loot);
        }
        return current;
    }

    private static BlockSpec rotateState(BlockSpec state) {
        if (state.properties.isEmpty()) return state;
        Map<String, String> rotated = new LinkedHashMap<>(state.properties);
        String facing = rotated.get("facing");
        if (facing != null) rotated.put("facing", switch (facing) {
            case "north" -> "east"; case "east" -> "south"; case "south" -> "west"; case "west" -> "north"; default -> facing;
        });
        String axis = rotated.get("axis");
        if ("x".equals(axis)) rotated.put("axis", "z");
        if ("z".equals(axis)) rotated.put("axis", "x");
        for (String direction : List.of("north", "east", "south", "west")) {
            if (state.properties.containsKey(direction)) {
                String next = switch (direction) { case "north" -> "east"; case "east" -> "south"; case "south" -> "west"; default -> "north"; };
                rotated.put(next, state.properties.get(direction));
            }
        }
        return new BlockSpec(state.id, rotated);
    }

    private static Blueprint fortress() {
        Builder b = new Builder(Kind.FORTRESS, 45, 15, 45);
        b.box(0, 0, 0, 44, 0, 44, BRICK);
        b.box(0, 1, 0, 44, 6, 44, AIR);
        // A continuous three-block-wide wall walk and open, five-block-wide southern gate.
        b.box(0, 1, 0, 44, 7, 2, BRICK); b.box(0, 1, 42, 44, 7, 44, BRICK);
        b.box(0, 1, 3, 2, 7, 41, BRICK); b.box(42, 1, 3, 44, 7, 41, BRICK);
        b.box(20, 1, 0, 24, 4, 2, AIR);
        for (int edge = 0; edge <= 44; edge += 2) {
            b.put(edge, 8, 0, BRICK); b.put(edge, 8, 44, BRICK);
            b.put(0, 8, edge, BRICK); b.put(44, 8, edge, BRICK);
        }
        for (int x : new int[] {0, 38}) for (int z : new int[] {0, 38}) watchtower(b, x, z);
        // The courtyard stair reaches the western wall without ladders or flight.
        stair(b, 4, 0, 10, 7, 3, "south");
        b.box(1, 7, 17, 6, 7, 19, BRICK);
        b.box(10, 1, 34, 34, 6, 42, BRICK);
        b.box(11, 1, 35, 33, 6, 41, AIR);
        b.box(20, 1, 34, 24, 3, 34, AIR);
        b.box(9, 7, 33, 35, 7, 42, SPRUCE);
        for (int x : new int[] {14, 18, 26, 30}) b.box(x, 3, 34, x + 1, 4, 34, GLASS);
        b.box(21, 1, 21, 23, 1, 23, state("water"));
        for (int p = 20; p <= 24; p++) { b.put(p, 1, 20, MOSS); b.put(p, 1, 24, MOSS); b.put(20, 1, p, MOSS); b.put(24, 1, p, MOSS); }
        // A roofed supply awning leaves the central courtyard open to the sky.
        for (int x : new int[] {32, 38}) for (int z : new int[] {14, 20}) b.box(x, 1, z, x, 4, z, log("oak_log", "y"));
        b.box(31, 5, 13, 39, 5, 21, SPRUCE);
        for (int z : new int[] {8, 30}) { b.put(3, 1, z, BRICK); b.put(41, 1, z, BRICK); b.put(3, 2, z, state("lantern")); b.put(41, 2, z, state("lantern")); }
        b.loot(3, 1, 3, LootTier.BASIC);
        b.loot(14, 1, 38, LootTier.BASIC);
        b.loot(30, 1, 38, LootTier.ARCANE);
        b.loot(35, 1, 17, LootTier.MILITARY);
        b.loot(1, 8, 25, LootTier.MILITARY);
        b.loot(41, 8, 41, LootTier.TREASURE);
        return b.build();
    }

    private static void watchtower(Builder b, int x, int z) {
        b.box(x, 1, z, x + 6, 12, z + 6, BRICK);
        b.box(x + 1, 1, z + 1, x + 5, 12, z + 5, AIR);
        b.box(x, 7, z, x + 6, 7, z + 6, OAK);
        b.box(x, 13, z, x + 6, 13, z + 6, DEEPSLATE);
        for (int p = 0; p < 7; p += 2) { b.put(x + p, 14, z, BRICK); b.put(x + p, 14, z + 6, BRICK); b.put(x, 14, z + p, BRICK); b.put(x + 6, 14, z + p, BRICK); }
        int interiorX = x == 0 ? x + 6 : x;
        int interiorZ = z == 0 ? z + 6 : z;
        b.box(interiorX, 1, z + 2, interiorX, 3, z + 4, AIR);
        b.box(x + 2, 1, interiorZ, x + 4, 3, interiorZ, AIR);
        b.box(interiorX, 8, z + 2, interiorX, 10, z + 4, AIR);
        b.box(x + 2, 8, interiorZ, x + 4, 10, interiorZ, AIR);
        int exteriorX = x == 0 ? x : x + 6;
        int exteriorZ = z == 0 ? z : z + 6;
        for (int y : new int[] {3, 10}) { b.box(exteriorX, y, z + 3, exteriorX, y + 1, z + 3, GLASS); b.box(x + 3, y, exteriorZ, x + 3, y + 1, exteriorZ, GLASS); }
    }

    private static Blueprint tower() {
        Builder b = new Builder(Kind.TOWER, 33, 39, 33);
        b.box(0, 0, 0, 32, 0, 32, BRICK);
        b.box(6, 1, 6, 26, 35, 26, AIR);
        for (int y = 1; y <= 35; y++) {
            b.box(6, y, 6, 26, y, 6, DEEPSLATE); b.box(6, y, 26, 26, y, 26, DEEPSLATE);
            b.box(6, y, 7, 6, y, 25, DEEPSLATE); b.box(26, y, 7, 26, y, 25, DEEPSLATE);
        }
        for (int floor = 7; floor <= 35; floor += 7) {
            b.box(7, floor, 7, 25, floor, 25, OAK);
            b.box(8, floor, 9, 10, floor, 14, AIR);
        }
        for (int floor = 0; floor < 35; floor += 7) stair(b, 8, floor, 9, 7, 3, "south");
        for (int x : new int[] {6, 26}) for (int z : new int[] {6, 26}) b.box(x, 1, z, x, 37, z, state("chiseled_deepslate"));
        for (int base = 0; base <= 28; base += 7) {
            for (int p : new int[] {11, 16, 21}) {
                b.box(6, base + 3, p, 6, base + 4, p, GLASS); b.box(26, base + 3, p, 26, base + 4, p, GLASS);
                b.box(p, base + 3, 6, p, base + 4, 6, GLASS); b.box(p, base + 3, 26, p, base + 4, 26, GLASS);
            }
            for (int z : new int[] {8, 24}) { b.put(24, base + 5, z, new BlockSpec("minecraft:lantern", Map.of("hanging", "true"))); b.put(24, base + 6, z, log("iron_chain", "y")); }
        }
        b.box(15, 1, 6, 17, 4, 6, AIR);
        // Two full blocks of headroom above the final observation floor.
        b.box(5, 38, 5, 27, 38, 27, DEEPSLATE);
        for (int p = 5; p <= 27; p += 2) { b.put(p, 37, 5, state("polished_deepslate")); b.put(p, 37, 27, state("polished_deepslate")); b.put(5, 37, p, state("polished_deepslate")); b.put(27, 37, p, state("polished_deepslate")); }
        b.put(16, 37, 16, state("amethyst_block")); b.put(16, 38, 16, state("lightning_rod"));
        b.loot(21, 1, 21, LootTier.BASIC);
        b.loot(21, 8, 11, LootTier.ARCANE);
        b.loot(21, 15, 21, LootTier.MILITARY);
        b.loot(21, 22, 11, LootTier.ARCANE);
        b.loot(21, 29, 21, LootTier.ARCANE);
        b.loot(21, 36, 11, LootTier.TREASURE);
        return b.build();
    }

    private static Blueprint depot() {
        Builder b = new Builder(Kind.DEPOT, 45, 12, 33);
        b.box(0, 0, 0, 44, 0, 32, BRICK);
        b.box(2, 1, 3, 42, 7, 29, AIR);
        // Two wings share a central loading doorway; the eastern wing contains the loft stair.
        b.box(2, 1, 3, 42, 6, 3, SPRUCE); b.box(2, 1, 29, 42, 6, 29, SPRUCE);
        b.box(2, 1, 4, 2, 6, 28, SPRUCE); b.box(42, 1, 4, 42, 6, 28, SPRUCE);
        b.box(22, 1, 4, 22, 5, 28, BRICK);
        b.box(22, 1, 14, 22, 3, 18, AIR);
        b.box(9, 1, 3, 15, 4, 3, AIR); b.box(29, 1, 3, 35, 4, 3, AIR);
        for (int x : new int[] {2, 12, 22, 32, 42}) for (int z : new int[] {3, 29}) b.box(x, 1, z, x, 7, z, log("stripped_oak_log", "y"));
        for (int x : new int[] {6, 18, 26, 38}) b.box(x, 3, 29, x + 2, 4, 29, GLASS);
        for (int z : new int[] {9, 20}) { b.box(2, 3, z, 2, 4, z + 2, GLASS); b.box(42, 3, z, 42, 4, z + 2, GLASS); }
        // Loft covers only the rear half; its railing overlooks the loading hall.
        b.box(3, 5, 20, 41, 5, 28, OAK);
        for (int x = 3; x <= 41; x++) b.put(x, 6, 20, new BlockSpec("minecraft:oak_fence", Map.of("north", "false", "east", "true", "south", "false", "west", "true", "waterlogged", "false")));
        b.box(31, 5, 17, 33, 5, 19, AIR); b.box(31, 6, 20, 33, 6, 20, AIR);
        stair(b, 31, 0, 16, 5, 3, "south");
        // Pitched roof with six visible courses instead of a flat solid cap.
        for (int step = 0; step <= 5; step++) {
            int left = 2 + step * 2;
            int right = 30 - step * 2;
            b.box(1, 6 + step, left, 43, 6 + step, left + 1, SPRUCE);
            b.box(1, 6 + step, right - 1, 43, 6 + step, right, SPRUCE);
        }
        b.box(1, 11, 14, 43, 11, 18, DEEPSLATE);
        // Storage racks frame broad walking lanes, with a protected working corner.
        for (int x : new int[] {5, 18, 25, 38}) { b.box(x, 1, 8, x + 1, 2, 10, OAK); b.box(x, 1, 25, x + 1, 2, 27, OAK); }
        b.put(7, 1, 25, state("crafting_table")); b.put(8, 1, 25, state("furnace"));
        for (int x : new int[] {8, 19, 27, 39}) { b.put(x, 3, 22, new BlockSpec("minecraft:lantern", Map.of("hanging", "true"))); b.put(x, 4, 22, log("iron_chain", "y")); }
        b.loot(8, 1, 9, LootTier.BASIC);
        b.loot(17, 1, 26, LootTier.MILITARY);
        b.loot(27, 1, 9, LootTier.MILITARY);
        b.loot(37, 1, 26, LootTier.BASIC);
        b.loot(8, 6, 25, LootTier.ARCANE);
        b.loot(38, 6, 25, LootTier.TREASURE);
        return b.build();
    }

    private static void stair(Builder b, int x, int floor, int z, int rise, int width, String facing) {
        for (int step = 1; step <= rise; step++) {
            for (int dx = 0; dx < width; dx++) b.put(x + dx, floor + step, z + step - 1, new BlockSpec("minecraft:stone_brick_stairs", Map.of("facing", facing, "half", "bottom", "shape", "straight", "waterlogged", "false")));
            b.box(x, floor + step + 1, z + step - 1, x + width - 1, floor + step + 2, z + step - 1, AIR);
        }
    }

    private static BlockSpec state(String name) { return new BlockSpec("minecraft:" + name); }
    private static BlockSpec log(String name, String axis) { return new BlockSpec("minecraft:" + name, Map.of("axis", axis)); }

    private static final class Builder {
        private final Kind kind;
        private final int width, height, depth;
        private final Map<Long, Block> blocks = new LinkedHashMap<>();
        private final List<LootPoint> loot = new ArrayList<>();
        private Builder(Kind kind, int width, int height, int depth) { this.kind = kind; this.width = width; this.height = height; this.depth = depth; }
        private void put(int x, int y, int z, BlockSpec spec) {
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth) throw new IllegalArgumentException("Block outside " + kind + ": " + x + "," + y + "," + z);
            blocks.put(((long) y << 32) | ((long) x << 16) | z, new Block(x, y, z, spec));
        }
        private void box(int x1, int y1, int z1, int x2, int y2, int z2, BlockSpec spec) {
            for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) put(x, y, z, spec);
        }
        private void loot(int x, int y, int z, LootTier tier) {
            put(x, y, z, new BlockSpec("minecraft:barrel", Map.of("facing", "up", "open", "false")));
            loot.add(new LootPoint(x, y, z, tier));
        }
        private Blueprint build() {
            List<Block> sorted = new ArrayList<>(blocks.values());
            sorted.sort(Comparator.comparingInt(Block::y).thenComparingInt(Block::x).thenComparingInt(Block::z));
            return new Blueprint(kind, width, height, depth, sorted, loot);
        }
    }
}
