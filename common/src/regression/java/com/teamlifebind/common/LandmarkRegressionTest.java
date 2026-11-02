package com.teamlifebind.common;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Exercises generated geometry and navigation, rather than mirroring the block-building recipes. */
final class LandmarkRegressionTest {
    private static int assertions;
    private LandmarkRegressionTest() { }
    static int run() {
        assertions = 0;
        geometryAndReachableSupplies();
        rotations();
        deterministicPlacementAndTerrain();
        lootAndNaturalBookProbability();
        System.out.println("Landmark generation and exploration regression passed (" + assertions + " assertions).");
        return assertions;
    }

    private static void geometryAndReachableSupplies() {
        eq(3, LandmarkStructures.all().size());
        for (LandmarkStructures.Blueprint blueprint : LandmarkStructures.all()) {
            Map<Pos, LandmarkStructures.BlockSpec> blocks = index(blueprint);
            yes(blueprint.blocks().size() <= 20_000, "Bounded placement count " + blueprint.kind());
            eq(blueprint.blocks().size(), blocks.size());
            yes(blueprint.lootPoints().size() >= 4 && blueprint.lootPoints().size() <= 8, "Scattered supplies");
            long clear = blueprint.blocks().stream().filter(b -> b.spec().id().equals("minecraft:air")).count();
            yes(clear > 1_000, "Landmarks contain cleared, walkable interiors");
            yes(blueprint.blocks().stream().anyMatch(b -> b.spec().id().endsWith("_stairs")), "A walkable stair is provided");
            yes(blueprint.blocks().stream().anyMatch(b -> b.spec().id().contains("glass")), "Windows are provided");
            for (LandmarkStructures.Block block : blueprint.blocks()) {
                if (!block.spec().id().equals("minecraft:lantern")) continue;
                if ("true".equals(block.spec().properties().get("hanging"))) {
                    eq("minecraft:iron_chain", blocks.get(new Pos(block.x(), block.y() + 1, block.z())).id());
                    yes(solid(blocks.get(new Pos(block.x(), block.y() + 2, block.z()))), "Hanging lantern chain is anchored into a ceiling");
                } else yes(solid(blocks.get(new Pos(block.x(), block.y() - 1, block.z()))), "Standing lantern has a pedestal");
            }
            int entranceX = switch (blueprint.kind()) { case FORTRESS -> 22; case TOWER -> 16; case DEPOT -> 12; };
            Set<Pos> reachable = walk(blueprint, blocks, new Pos(entranceX, 1, 0));
            yes(reachable.size() > 500, "Substantial traversable floor area in " + blueprint.kind());
            for (LandmarkStructures.LootPoint point : blueprint.lootPoints()) {
                eq("minecraft:barrel", blocks.get(new Pos(point.x(), point.y(), point.z())).id());
                boolean accessible = false;
                for (int[] d : DIRECTIONS) accessible |= reachable.contains(new Pos(point.x() + d[0], point.y(), point.z() + d[1]));
                yes(accessible, "Loot reachable on foot in " + blueprint.kind() + " at " + point);
                yes(solid(blocks.get(new Pos(point.x(), point.y() - 1, point.z()))), "Loot has a supporting floor: " + point);
            }
            int highestSupply = blueprint.lootPoints().stream().mapToInt(LandmarkStructures.LootPoint::y).max().orElseThrow();
            yes(highestSupply > 1, "Upper-storey supplies exist");
            System.out.println("  " + blueprint.kind() + ": " + blueprint.width() + "x" + blueprint.height() + "x" + blueprint.depth()
                    + ", " + blueprint.blocks().size() + " placements, " + reachable.size() + " reachable standing positions, " + blueprint.lootPoints().size() + " supply points");
        }
    }

    private static void rotations() {
        for (LandmarkStructures.Blueprint original : LandmarkStructures.all()) {
            eq(original, LandmarkStructures.rotate(original, 4));
            for (int turns = 0; turns < 4; turns++) {
                LandmarkStructures.Blueprint rotated = LandmarkStructures.rotate(original, turns);
                eq(original.blocks().size(), rotated.blocks().size());
                eq(original.lootPoints().size(), rotated.lootPoints().size());
                Map<Pos, LandmarkStructures.BlockSpec> blocks = index(rotated);
                for (LandmarkStructures.LootPoint point : rotated.lootPoints()) eq("minecraft:barrel", blocks.get(new Pos(point.x(), point.y(), point.z())).id());
                String stairFacing = List.of("south", "west", "north", "east").get(turns);
                yes(rotated.blocks().stream().filter(block -> block.spec().id().endsWith("_stairs")).allMatch(block -> stairFacing.equals(block.spec().properties().get("facing"))), "Stair ascent rotates with geometry");
                if (original.kind() == LandmarkStructures.Kind.DEPOT) {
                    LandmarkStructures.BlockSpec railing = rotated.blocks().stream().filter(block -> block.spec().id().equals("minecraft:oak_fence")).findFirst().orElseThrow().spec();
                    eq((turns & 1) == 0 ? "true" : "false", railing.properties().get("east"));
                    eq((turns & 1) == 0 ? "false" : "true", railing.properties().get("north"));
                }
                // Rotating four successive times must restore both geometry and directional states.
                LandmarkStructures.Blueprint cycled = rotated;
                for (int i = 0; i < 4; i++) cycled = LandmarkStructures.rotate(cycled, 1);
                eq(index(rotated), index(cycled));
                eq(rotated.lootPoints(), cycled.lootPoints());
            }
        }
    }

    private static void deterministicPlacementAndTerrain() {
        Set<LandmarkStructures.Kind> observed = new HashSet<>();
        for (long seed : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 12_345_678_901L}) {
            for (int rx = -12; rx <= 12; rx++) for (int rz = -12; rz <= 12; rz++) {
                LandmarkPlacement.Candidate candidate = LandmarkPlacement.candidate(seed, rx, rz);
                eq(candidate, LandmarkPlacement.candidate(seed, rx, rz));
                eq(0, Math.floorMod(candidate.originX(), 16)); eq(0, Math.floorMod(candidate.originZ(), 16));
                eq(rx, Math.floorDiv(candidate.originX(), LandmarkPlacement.REGION_SIZE));
                eq(rz, Math.floorDiv(candidate.originZ(), LandmarkPlacement.REGION_SIZE));
                observed.add(candidate.kind());
                LandmarkStructures.Blueprint blueprint = candidate.blueprint();
                int ownerMinX = candidate.ownerChunkX() * 16 - 16;
                int ownerMinZ = candidate.ownerChunkZ() * 16 - 16;
                yes(candidate.originX() >= ownerMinX && candidate.originX() + blueprint.width() <= ownerMinX + 48, "Fits owner neighbourhood X");
                yes(candidate.originZ() >= ownerMinZ && candidate.originZ() + blueprint.depth() <= ownerMinZ + 48, "Fits owner neighbourhood Z");
            }
        }
        eq(3, observed.size());
        LandmarkPlacement.Candidate candidate = LandmarkPlacement.candidate(55, -1, 1);
        int[] calls = {0};
        LandmarkPlacement.Site flat = LandmarkPlacement.assess(candidate, (x, z) -> {
            calls[0]++;
            return new LandmarkPlacement.Surface(70, true, true, true);
        }).orElseThrow();
        eq(25, calls[0]); eq(70, flat.groundY()); eq(71, flat.baseY());
        yes(LandmarkPlacement.assess(candidate, (x, z) -> new LandmarkPlacement.Surface(70 + Math.floorMod(x, 5), true, true, true)).isPresent(), "Four-block slope allowed");
        yes(LandmarkPlacement.assess(candidate, (x, z) -> new LandmarkPlacement.Surface(x == candidate.originX() ? 70 : 75, true, true, true)).isEmpty(), "Cliffs rejected");
        for (LandmarkPlacement.Surface bad : List.of(new LandmarkPlacement.Surface(70, false, true, true), new LandmarkPlacement.Surface(70, true, false, true), new LandmarkPlacement.Surface(70, true, true, false))) {
            yes(LandmarkPlacement.assess(candidate, (x, z) -> bad).isEmpty(), "Unsafe terrain rejected");
        }
        yes(LandmarkPlacement.assess(candidate, (x, z) -> null).isEmpty(), "Unavailable neighbour rejected");
    }

    private static void lootAndNaturalBookProbability() {
        Set<String> ids = new HashSet<>();
        for (LandmarkStructures.LootTier tier : LandmarkStructures.LootTier.values()) {
            for (int seed = 0; seed < 1_000; seed++) {
                List<LandmarkLoot.LootItem> items = LandmarkLoot.roll(tier, new Random(seed));
                eq(items, LandmarkLoot.roll(tier, new Random(seed)));
                yes(items.size() >= 3 && items.size() <= 6, "Bounded container contents");
                for (LandmarkLoot.LootItem item : items) {
                    ids.add(item.id());
                    yes(item.count() >= 1 && item.count() <= 64, "Valid item stack");
                    yes(!item.id().contains("gun") && !item.id().contains("rifle") && !item.id().contains("pistol"), "Normal landmark loot excludes guns");
                }
            }
            // Every tier uses exactly the natural table: 12% book chance, conditional super weight 1/10,000.
            for (int roll : new int[] {0, 1_199, 1_200, 9_999}) {
                ScriptedRandom random = new ScriptedRandom(roll);
                List<LandmarkLoot.LootItem> items = LandmarkLoot.roll(tier, random);
                long books = items.stream().filter(item -> item.id().startsWith("tlb:spell/")).count();
                eq(roll < MagicLootRules.NATURAL_BOOK_CHANCE ? 1L : 0L, books);
                if (books > 0) {
                    String id = items.stream().filter(item -> item.id().startsWith("tlb:spell/")).findFirst().orElseThrow().id().substring("tlb:spell/".length());
                    yes(SpellCatalogue.all().stream().anyMatch(book -> book.id().equals(id) && book.superTier()), "Natural super-tier edge retained");
                }
            }
        }
        for (String id : List.of("tlb:mana_potion", "tlb:greater_mana_potion", "tlb:mana_apple", "minecraft:bread", "minecraft:crossbow")) yes(ids.contains(id), "Companion or exploration loot exists: " + id);
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private record Pos(int x, int y, int z) { }
    private static Map<Pos, LandmarkStructures.BlockSpec> index(LandmarkStructures.Blueprint blueprint) {
        Map<Pos, LandmarkStructures.BlockSpec> result = new HashMap<>();
        for (LandmarkStructures.Block block : blueprint.blocks()) {
            yes(block.x() >= 0 && block.x() < blueprint.width() && block.y() >= 0 && block.y() < blueprint.height() && block.z() >= 0 && block.z() < blueprint.depth(), "Block in blueprint bounds");
            yes(result.put(new Pos(block.x(), block.y(), block.z()), block.spec()) == null, "One final state per position");
        }
        return result;
    }
    private static Set<Pos> walk(LandmarkStructures.Blueprint blueprint, Map<Pos, LandmarkStructures.BlockSpec> blocks, Pos entrance) {
        Set<Pos> visited = new HashSet<>();
        ArrayDeque<Pos> pending = new ArrayDeque<>();
        yes(standing(blocks, entrance), "Walkable ground entrance: " + blueprint.kind());
        visited.add(entrance); pending.add(entrance);
        while (!pending.isEmpty()) {
            Pos from = pending.removeFirst();
            for (int[] direction : DIRECTIONS) for (int dy : new int[] {0, 1, -1}) {
                Pos next = new Pos(from.x + direction[0], from.y + dy, from.z + direction[1]);
                if (next.x < 0 || next.x >= blueprint.width() || next.z < 0 || next.z >= blueprint.depth() || next.y < 1 || next.y > blueprint.height()) continue;
                if (standing(blocks, next) && visited.add(next)) pending.addLast(next);
            }
        }
        return visited;
    }
    private static boolean standing(Map<Pos, LandmarkStructures.BlockSpec> blocks, Pos feet) {
        return solid(blocks.get(new Pos(feet.x, feet.y - 1, feet.z)))
                && !solid(blocks.get(feet)) && !solid(blocks.get(new Pos(feet.x, feet.y + 1, feet.z)));
    }
    private static boolean solid(LandmarkStructures.BlockSpec block) {
        return block != null && !Set.of("minecraft:air", "minecraft:water", "minecraft:lantern", "minecraft:lightning_rod").contains(block.id());
    }
    private static final class ScriptedRandom extends Random {
        private final int naturalRoll;
        private int tableCalls;
        private ScriptedRandom(int naturalRoll) { this.naturalRoll = naturalRoll; }
        @Override public int nextInt(int bound) {
            if (bound == MagicLootRules.WEIGHT_TOTAL) return tableCalls++ == 0 ? naturalRoll : bound - 1;
            return 0;
        }
        @Override public boolean nextBoolean() { return false; }
    }
    private static void yes(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void eq(Object expected, Object actual) { assertions++; if (!Objects.equals(expected, actual)) throw new AssertionError("Landmark: expected " + expected + " but was " + actual); }
}
