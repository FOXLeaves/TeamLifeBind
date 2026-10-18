package com.teamlifebind.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.FeatureCountTracker;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class RoundSeededNoiseChunkGenerator extends NoiseBasedChunkGenerator {

    static final MapCodec<RoundSeededNoiseChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(RoundSeededNoiseChunkGenerator::generatorSettings),
                    Codec.LONG.optionalFieldOf("seed_offset", 0L).forGetter(RoundSeededNoiseChunkGenerator::seedOffset)
            ).apply(instance, instance.stable(RoundSeededNoiseChunkGenerator::new))
    );

    private final long seedOffset;
    private volatile @Nullable RoundGenerationState roundGenerationState;
    private volatile @Nullable List<FeatureSorter.StepFeatureData> cachedFeatureSteps;
    private volatile @Nullable StructureSteps cachedStructureSteps;

    RoundSeededNoiseChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, long seedOffset) {
        super(biomeSource, settings);
        this.seedOffset = seedOffset;
    }

    long seedOffset() {
        return seedOffset;
    }

    synchronized void setRoundSeed(long roundSeed, RegistryAccess registryAccess) {
        long seed = mixSeed(roundSeed + this.seedOffset);
        RandomState randomState = RandomState.create(
                registryAccess.lookupOrThrow(Registries.NOISE),
                seed,
                generatorSettings().value()
        );
        ChunkGeneratorStructureState structureState = super.createState(
                registryAccess.lookupOrThrow(Registries.STRUCTURE_SET), randomState, seed
        );
        structureState.ensureStructuresGenerated();
        this.roundGenerationState = new RoundGenerationState(seed, randomState, structureState);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState randomState, long seed) {
        RoundGenerationState state = this.roundGenerationState;
        return super.createState(
                structures,
                state == null ? randomState : state.randomState(),
                state == null ? mixSeed(this.seedOffset) : state.seed()
        );
    }

    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager,
            ResourceKey<Level> levelKey
    ) {
        RoundGenerationState state = this.roundGenerationState;
        if (state == null) {
            super.createStructures(registryAccess, structureState, structureManager, chunk, structureTemplateManager, levelKey);
            return;
        }

        super.createStructures(registryAccess, state.structures(), structureManager, chunk, structureTemplateManager, levelKey);
    }

    @Override
    public java.util.concurrent.CompletableFuture<ChunkAccess> createBiomes(
            RandomState randomState,
            Blender blender,
            StructureManager structureManager,
            ChunkAccess chunk
    ) {
        return super.createBiomes(resolvedRandomState(randomState), blender, structureManager, chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        ChunkPos chunkPos = chunk.getPos();
        if (SharedConstants.debugVoidTerrain(chunkPos)) {
            return;
        }

        SectionPos sectionPos = SectionPos.of(chunkPos, level.getMinSectionY());
        BlockPos origin = sectionPos.origin();
        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<Integer, List<Structure>> structuresByStep = structuresByStep(structureRegistry);
        List<FeatureSorter.StepFeatureData> featureSteps = featureSteps();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(effectiveSeed(), origin.getX(), origin.getZ());
        Set<Holder<Biome>> nearbyBiomes = new ObjectArraySet<>();

        ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach(nearbyPos -> {
            ChunkAccess nearbyChunk = level.getChunk(nearbyPos.x(), nearbyPos.z());
            for (LevelChunkSection section : nearbyChunk.getSections()) {
                section.getBiomes().getAll(nearbyBiomes::add);
            }
        });
        nearbyBiomes.retainAll(this.biomeSource.possibleBiomes());

        Registry<PlacedFeature> placedFeatureRegistry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        FeaturePlacer featurePlacer = new FeaturePlacer(level, this);
        int totalSteps = Math.max(GenerationStep.Decoration.values().length, featureSteps.size());

        for (int step = 0; step < totalSteps; step++) {
            int structureIndex = 0;
            if (structureManager.shouldGenerateStructures()) {
                for (Structure structure : structuresByStep.getOrDefault(step, Collections.emptyList())) {
                    random.setFeatureSeed(decorationSeed, structureIndex, step);
                    Supplier<String> label = () -> structureRegistry.getResourceKey(structure)
                            .map(Object::toString)
                            .orElseGet(structure::toString);
                    level.setCurrentlyGenerating(label);
                    structureManager.startsForStructure(chunkPos.x(), chunkPos.z(), structure)
                            .forEach(start -> start.placeInChunk(level, structureManager, this, random, writableArea(chunk), chunkPos));
                    structureIndex++;
                }
            }

            if (step >= featureSteps.size()) {
                continue;
            }

            IntSet featureIndexes = new IntArraySet();
            for (Holder<Biome> biome : nearbyBiomes) {
                List<HolderSet<PlacedFeature>> features = biome.value().getGenerationSettings().features();
                if (step >= features.size()) {
                    continue;
                }

                FeatureSorter.StepFeatureData featureData = featureSteps.get(step);
                features.get(step)
                        .stream()
                        .map(Holder::value)
                        .forEach(feature -> featureIndexes.add(featureData.indexMapping().applyAsInt(feature)));
            }

            int[] orderedIndexes = featureIndexes.toIntArray();
            Arrays.sort(orderedIndexes);
            FeatureSorter.StepFeatureData featureData = featureSteps.get(step);
            for (int featureIndex : orderedIndexes) {
                PlacedFeature placedFeature = featureData.features().get(featureIndex);
                Supplier<String> label = () -> placedFeatureRegistry.getResourceKey(placedFeature)
                        .map(Object::toString)
                        .orElseGet(placedFeature::toString);
                level.setCurrentlyGenerating(label);
                random.setFeatureSeed(decorationSeed, featureIndex, step);
                featurePlacer.placeWithBiomeCheck(placedFeature, random, origin);
            }
        }

        level.setCurrentlyGenerating(null);
        if (SharedConstants.DEBUG_FEATURE_COUNT) {
            FeatureCountTracker.chunkDecorated(level.getLevel());
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<ChunkAccess> buildTerrain(
            ChunkAccess chunk,
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            BiomeManager biomeManager,
            WorldGenRegion region,
            Set<Holder<Biome>> biomes
    ) {
        return super.buildTerrain(chunk, blender, resolvedRandomState(randomState), structureManager, biomeManager, region, biomes);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor level, RandomState randomState) {
        return super.getBaseHeight(x, z, heightmap, level, resolvedRandomState(randomState));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        return super.getBaseColumn(x, z, level, resolvedRandomState(randomState));
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos, SamplerContext context) {
        super.addDebugScreenInfo(lines, resolvedRandomState(randomState), pos, context);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        if (this.generatorSettings().value().disableMobGeneration()) {
            return;
        }

        ChunkPos chunkPos = region.getCenter();
        BlockPos biomePos = chunkPos.getWorldPosition().atY(region.getMaxY());
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(effectiveSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biomePos, chunkPos, random);
    }

    private List<FeatureSorter.StepFeatureData> featureSteps() {
        List<FeatureSorter.StepFeatureData> cached = this.cachedFeatureSteps;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (this.cachedFeatureSteps == null) {
                this.cachedFeatureSteps = FeatureSorter.buildFeaturesPerStep(
                        List.copyOf(this.biomeSource.possibleBiomes()),
                        biome -> biome.value().getGenerationSettings().features(),
                        true
                );
            }
            return this.cachedFeatureSteps;
        }
    }

    private RandomState resolvedRandomState(RandomState fallback) {
        RoundGenerationState state = this.roundGenerationState;
        return state != null ? state.randomState() : fallback;
    }

    private long effectiveSeed() {
        RoundGenerationState state = this.roundGenerationState;
        return state != null ? state.seed() : mixSeed(this.seedOffset);
    }

    private Map<Integer, List<Structure>> structuresByStep(Registry<Structure> registry) {
        StructureSteps cached = this.cachedStructureSteps;
        if (cached != null && cached.registry() == registry) {
            return cached.byStep();
        }
        synchronized (this) {
            cached = this.cachedStructureSteps;
            if (cached == null || cached.registry() != registry) {
                cached = new StructureSteps(registry, Map.copyOf(registry.stream().collect(
                        Collectors.groupingBy(structure -> structure.step().ordinal(), Collectors.toUnmodifiableList())
                )));
                this.cachedStructureSteps = cached;
            }
            return cached.byStep();
        }
    }

    private static BoundingBox writableArea(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        LevelHeightAccessor level = chunk.getHeightAccessorForGeneration();
        int minY = level.getMinY() + 1;
        int maxY = level.getMaxY();
        return new BoundingBox(minX, minY, minZ, minX + 15, maxY, minZ + 15);
    }

    private static long mixSeed(long seed) {
        long mixed = seed;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    private record StructureSteps(Registry<Structure> registry, Map<Integer, List<Structure>> byStep) {
    }

    private record RoundGenerationState(long seed, RandomState randomState, ChunkGeneratorStructureState structures) {
    }
}
