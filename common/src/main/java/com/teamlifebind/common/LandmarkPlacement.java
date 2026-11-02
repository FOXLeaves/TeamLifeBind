package com.teamlifebind.common;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/** One deterministic candidate per region; generation adapters own persistence and full-column safety checks. */
public final class LandmarkPlacement {
    public static final int REGION_SIZE = 512;
    public static final int MAX_SLOPE = 4;
    public static final int MAX_FOUNDATION_DEPTH = 5;
    private static final long SALT = 0x544c425f4c414e44L;

    private LandmarkPlacement() { }

    public record Candidate(LandmarkStructures.Kind kind, int rotation, int originX, int originZ, long lootSeed) {
        public Candidate {
            Objects.requireNonNull(kind);
            rotation = Math.floorMod(rotation, 4);
            if (Math.floorMod(originX, 16) != 0 || Math.floorMod(originZ, 16) != 0) {
                throw new IllegalArgumentException("Landmark origin must align to chunks");
            }
        }
        public LandmarkStructures.Blueprint blueprint() { return LandmarkStructures.rotate(LandmarkStructures.blueprint(kind), rotation); }
        public int ownerChunkX() { return Math.floorDiv(originX, 16) + 1; }
        public int ownerChunkZ() { return Math.floorDiv(originZ, 16) + 1; }
    }

    /** Height is the highest natural solid block, not the first air block. */
    public record Surface(int height, boolean solid, boolean dry, boolean replaceable) { }
    @FunctionalInterface public interface Terrain { Surface sample(int x, int z); }
    public record Site(Candidate candidate, int groundY, int minGroundY, int maxGroundY) {
        public Site { Objects.requireNonNull(candidate); }
        public int baseY() { return groundY + 1; }
        public LandmarkStructures.Blueprint blueprint() { return candidate.blueprint(); }
    }

    public static Candidate candidate(long worldSeed, int regionX, int regionZ) {
        Random random = new Random(mix(worldSeed ^ SALT ^ mix(regionX * 341873128712L) ^ mix(regionZ * 132897987541L)));
        LandmarkStructures.Kind[] kinds = LandmarkStructures.Kind.values();
        LandmarkStructures.Kind kind = kinds[random.nextInt(kinds.length)];
        int rotation = random.nextInt(4);
        // 96..384 leaves at least 83 blocks between a 45-wide landmark and its region boundary.
        int x = Math.toIntExact((long) regionX * REGION_SIZE + 96L + random.nextInt(19) * 16L);
        int z = Math.toIntExact((long) regionZ * REGION_SIZE + 96L + random.nextInt(19) * 16L);
        return new Candidate(kind, rotation, x, z, random.nextLong());
    }

    /** Cheap 5 by 5 rejection pass. Adapters must additionally inspect every foundation column before writing. */
    public static Optional<Site> assess(Candidate candidate, Terrain terrain) {
        Objects.requireNonNull(candidate);
        Objects.requireNonNull(terrain);
        LandmarkStructures.Blueprint blueprint = candidate.blueprint();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int ix = 0; ix < 5; ix++) for (int iz = 0; iz < 5; iz++) {
            int x = candidate.originX + (blueprint.width() - 1) * ix / 4;
            int z = candidate.originZ + (blueprint.depth() - 1) * iz / 4;
            Surface surface = terrain.sample(x, z);
            if (surface == null || !surface.solid || !surface.dry || !surface.replaceable) return Optional.empty();
            min = Math.min(min, surface.height);
            max = Math.max(max, surface.height);
            if ((long) max - min > MAX_SLOPE) return Optional.empty();
        }
        return Optional.of(new Site(candidate, max, min, max));
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
