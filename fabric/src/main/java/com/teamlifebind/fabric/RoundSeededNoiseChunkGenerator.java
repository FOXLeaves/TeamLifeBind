package com.teamlifebind.fabric;

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
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

final class RoundSeededNoiseChunkGenerator extends ChunkGenerator {

    static final MapCodec<RoundSeededNoiseChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(RoundSeededNoiseChunkGenerator::settings),
            Codec.LONG.optionalFieldOf("seed_offset", 0L).forGetter(RoundSeededNoiseChunkGenerator::seedOffset)
        ).apply(instance, instance.stable(RoundSeededNoiseChunkGenerator::new))
    );

    private final NoiseBasedChunkGenerator delegate;
    private final Holder<NoiseGeneratorSettings> settings;
    private final long seedOffset;
    private volatile RoundGenerationState roundGenerationState;
    private volatile List<FeatureSorter.StepFeatureData> cachedFeatureSteps;
    private volatile StructureSteps cachedStructureSteps;

    RoundSeededNoiseChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, long seedOffset) {
        super(biomeSource);
        this.delegate = new NoiseBasedChunkGenerator(biomeSource, settings);
        this.settings = settings;
        this.seedOffset = seedOffset;
    }

    Holder<NoiseGeneratorSettings> settings() {
        return settings;
    }

    long seedOffset() {
        return seedOffset;
    }

    synchronized void setRoundSeed(long roundSeed, RegistryAccess registryManager) {
        long seed = mixSeed(roundSeed + seedOffset);
        RandomState noiseConfig = RandomState.create(registryManager.lookupOrThrow(Registries.NOISE), seed, settings.value());
        ChunkGeneratorStructureState structureState = super.createState(
            registryManager.lookupOrThrow(Registries.STRUCTURE_SET), noiseConfig, seed
        );
        structureState.ensureStructuresGenerated();
        this.roundGenerationState = new RoundGenerationState(seed, noiseConfig, structureState);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkPos getOrigin(RandomState noiseConfig) {
        return delegate.getOrigin(noiseConfig);
    }

    @Override
    public ChunkGeneratorStructureState createState(
        HolderLookup<net.minecraft.world.level.levelgen.structure.StructureSet> structureSets,
        RandomState noiseConfig,
        long seed
    ) {
        RoundGenerationState state = roundGenerationState;
        return super.createState(
            structureSets,
            state == null ? noiseConfig : state.noiseConfig(),
            state == null ? mixSeed(seedOffset) : state.seed()
        );
    }

    @Override
    public java.util.concurrent.CompletableFuture<ChunkAccess> createBiomes(
        RandomState noiseConfig,
        Blender blender,
        StructureManager structureAccessor,
        ChunkAccess chunk
    ) {
        return delegate.createBiomes(resolvedNoiseConfig(noiseConfig), blender, structureAccessor, chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel world, ChunkAccess chunk, StructureManager structureAccessor) {
        ChunkPos chunkPos = chunk.getPos();
        if (SharedConstants.debugVoidTerrain(chunkPos)) {
            return;
        }

        SectionPos sectionPos = SectionPos.of(chunkPos, world.getMinSectionY());
        BlockPos origin = sectionPos.origin();
        Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<Integer, List<Structure>> structuresByStep = structuresByStep(structureRegistry);
        List<FeatureSorter.StepFeatureData> featureSteps = featureSteps();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long populationSeed = random.setDecorationSeed(effectiveSeed(), origin.getX(), origin.getZ());
        Set<Holder<Biome>> nearbyBiomes = new ObjectArraySet<>();

        ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach(nearbyPos -> {
            ChunkAccess nearbyChunk = world.getChunk(nearbyPos.x(), nearbyPos.z());
            for (LevelChunkSection section : nearbyChunk.getSections()) {
                section.getBiomes().getAll(nearbyBiomes::add);
            }
        });
        nearbyBiomes.retainAll(this.biomeSource.possibleBiomes());

        Registry<PlacedFeature> placedFeatureRegistry = world.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        FeaturePlacer featurePlacer = new FeaturePlacer(world, this);
        int totalSteps = Math.max(GenerationStep.Decoration.values().length, featureSteps.size());

        for (int step = 0; step < totalSteps; step++) {
            int structureIndex = 0;
            if (structureAccessor.shouldGenerateStructures()) {
                for (net.minecraft.world.level.levelgen.structure.Structure structure : structuresByStep.getOrDefault(step, Collections.emptyList())) {
                    random.setFeatureSeed(populationSeed, structureIndex, step);
                    Supplier<String> label = () -> registryLabel(structureRegistry, structure);
                    world.setCurrentlyGenerating(label);
                    for (StructureStart start : structureAccessor.startsForStructure(chunkPos.x(), chunkPos.z(), structure)) {
                        start.placeInChunk(world, structureAccessor, this, random, writableArea(chunk), chunkPos);
                    }
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
                features.get(step).stream().map(Holder::value).forEach(feature -> featureIndexes.add(featureData.indexMapping().applyAsInt(feature)));
            }

            int[] orderedIndexes = featureIndexes.toIntArray();
            Arrays.sort(orderedIndexes);
            FeatureSorter.StepFeatureData featureData = featureSteps.get(step);
            for (int featureIndex : orderedIndexes) {
                PlacedFeature placedFeature = featureData.features().get(featureIndex);
                Supplier<String> label = () -> registryLabel(placedFeatureRegistry, placedFeature);
                world.setCurrentlyGenerating(label);
                random.setFeatureSeed(populationSeed, featureIndex, step);
                featurePlacer.placeWithBiomeCheck(placedFeature, random, origin);
            }
        }

        world.setCurrentlyGenerating(null);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void spawnOriginalMobs(WorldGenRegion region) {
        if (settings.value().disableMobGeneration()) {
            return;
        }

        ChunkPos chunkPos = region.getCenter();
        BlockPos biomePos = chunkPos.getMiddleBlockPosition(region.getMaxY());
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(effectiveSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biomePos, chunkPos, random);
    }

    @Override
    public void createStructures(
        RegistryAccess registryManager,
        ChunkGeneratorStructureState structurePlacementCalculator,
        StructureManager structureAccessor,
        ChunkAccess chunk,
        StructureTemplateManager structureTemplateManager,
        ResourceKey<Level> worldKey
    ) {
        RoundGenerationState state = roundGenerationState;
        ChunkGeneratorStructureState battleState = state == null ? structurePlacementCalculator : state.structures();
        super.createStructures(
            registryManager,
            battleState,
            structureAccessor,
            chunk,
            structureTemplateManager,
            worldKey
        );
    }

    @Override
    public java.util.concurrent.CompletableFuture<ChunkAccess> buildTerrain(
        ChunkAccess chunk,
        Blender blender,
        RandomState noiseConfig,
        StructureManager structureAccessor,
        BiomeManager biomeManager,
        WorldGenRegion region,
        Set<Holder<Biome>> biomes
    ) {
        return delegate.buildTerrain(chunk, blender, resolvedNoiseConfig(noiseConfig), structureAccessor, biomeManager, region, biomes);
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public int getGenDepth() {
        return delegate.getGenDepth();
    }

    @Override
    public int getMinY() {
        return delegate.getMinY();
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor world, RandomState noiseConfig) {
        return delegate.getBaseHeight(x, z, heightmap, world, resolvedNoiseConfig(noiseConfig));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor world, RandomState noiseConfig) {
        return delegate.getBaseColumn(x, z, world, resolvedNoiseConfig(noiseConfig));
    }

    @Override
    public void addDebugScreenInfo(List<String> text, RandomState noiseConfig, BlockPos pos, SamplerContext context) {
        delegate.addDebugScreenInfo(text, resolvedNoiseConfig(noiseConfig), pos, context);
    }

    private List<FeatureSorter.StepFeatureData> featureSteps() {
        List<FeatureSorter.StepFeatureData> cached = cachedFeatureSteps;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (cachedFeatureSteps == null) {
                cachedFeatureSteps = FeatureSorter.buildFeaturesPerStep(
                    List.copyOf(this.biomeSource.possibleBiomes()),
                    biome -> biome.value().getGenerationSettings().features(),
                    true
                );
            }
            return cachedFeatureSteps;
        }
    }

    private RandomState resolvedNoiseConfig(RandomState fallback) {
        RoundGenerationState state = roundGenerationState;
        return state != null ? state.noiseConfig() : fallback;
    }

    private Map<Integer, List<Structure>> structuresByStep(Registry<Structure> registry) {
        StructureSteps cached = cachedStructureSteps;
        if (cached != null && cached.registry() == registry) {
            return cached.byStep();
        }
        synchronized (this) {
            cached = cachedStructureSteps;
            if (cached == null || cached.registry() != registry) {
                cached = new StructureSteps(registry, Map.copyOf(registry.stream().collect(
                    Collectors.groupingBy(structure -> structure.step().ordinal(), Collectors.toUnmodifiableList())
                )));
                cachedStructureSteps = cached;
            }
            return cached.byStep();
        }
    }

    private static <T> String registryLabel(Registry<T> registry, T value) {
        ResourceKey<T> key = registry.getResourceKey(value).orElse(null);
        return key != null ? key.identifier().toString() : value.toString();
    }

    private long effectiveSeed() {
        RoundGenerationState state = roundGenerationState;
        return state != null ? state.seed() : mixSeed(seedOffset);
    }

    private static BoundingBox writableArea(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        LevelHeightAccessor heightView = chunk.getHeightAccessorForGeneration();
        int minY = heightView.getMinY() + 1;
        int maxY = heightView.getMaxY();
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

    private record RoundGenerationState(long seed, RandomState noiseConfig, ChunkGeneratorStructureState structures) {
    }
}
