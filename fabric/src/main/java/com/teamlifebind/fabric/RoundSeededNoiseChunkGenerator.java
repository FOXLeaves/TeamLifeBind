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
import net.minecraft.block.BlockState;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.noise.NoiseConfig;

final class RoundSeededNoiseChunkGenerator extends ChunkGenerator {

    static final MapCodec<RoundSeededNoiseChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
            ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings").forGetter(RoundSeededNoiseChunkGenerator::settings),
            Codec.LONG.optionalFieldOf("seed_offset", 0L).forGetter(RoundSeededNoiseChunkGenerator::seedOffset)
        ).apply(instance, instance.stable(RoundSeededNoiseChunkGenerator::new))
    );

    private final NoiseChunkGenerator delegate;
    private final RegistryEntry<ChunkGeneratorSettings> settings;
    private final long seedOffset;
    private volatile long roundSeed;
    private volatile NoiseConfig seededNoiseConfig;
    private volatile List<PlacedFeatureIndexer.IndexedFeatures> cachedFeatureSteps;

    RoundSeededNoiseChunkGenerator(BiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings, long seedOffset) {
        super(biomeSource);
        this.delegate = new NoiseChunkGenerator(biomeSource, settings);
        this.settings = settings;
        this.seedOffset = seedOffset;
    }

    RegistryEntry<ChunkGeneratorSettings> settings() {
        return settings;
    }

    long seedOffset() {
        return seedOffset;
    }

    synchronized void setRoundSeed(long roundSeed, DynamicRegistryManager registryManager) {
        this.roundSeed = roundSeed;
        this.seededNoiseConfig = NoiseConfig.create(settings.value(), registryManager.getOrThrow(RegistryKeys.NOISE_PARAMETERS), effectiveSeed());
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public StructurePlacementCalculator createStructurePlacementCalculator(
        RegistryWrapper<net.minecraft.structure.StructureSet> structureSets,
        NoiseConfig noiseConfig,
        long seed
    ) {
        return super.createStructurePlacementCalculator(structureSets, resolvedNoiseConfig(noiseConfig), effectiveSeed());
    }

    @Override
    public java.util.concurrent.CompletableFuture<Chunk> populateBiomes(
        NoiseConfig noiseConfig,
        Blender blender,
        StructureAccessor structureAccessor,
        Chunk chunk
    ) {
        return delegate.populateBiomes(resolvedNoiseConfig(noiseConfig), blender, structureAccessor, chunk);
    }

    @Override
    public void carve(
        ChunkRegion region,
        long seed,
        NoiseConfig noiseConfig,
        BiomeAccess biomeAccess,
        StructureAccessor structureAccessor,
        Chunk chunk
    ) {
        delegate.carve(region, effectiveSeed(), resolvedNoiseConfig(noiseConfig), biomeAccess, structureAccessor, chunk);
    }

    @Override
    public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor) {
        ChunkPos chunkPos = chunk.getPos();
        if (SharedConstants.isOutsideGenerationArea(chunkPos)) {
            return;
        }

        ChunkSectionPos sectionPos = ChunkSectionPos.from(chunkPos, world.getBottomSectionCoord());
        BlockPos origin = sectionPos.getMinPos();
        Registry<net.minecraft.world.gen.structure.Structure> structureRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
        Map<Integer, List<net.minecraft.world.gen.structure.Structure>> structuresByStep = structureRegistry.stream()
            .collect(Collectors.groupingBy(structure -> structure.getFeatureGenerationStep().ordinal()));
        List<PlacedFeatureIndexer.IndexedFeatures> featureSteps = featureSteps();
        ChunkRandom random = new ChunkRandom(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
        long populationSeed = random.setPopulationSeed(effectiveSeed(), origin.getX(), origin.getZ());
        Set<RegistryEntry<Biome>> nearbyBiomes = new ObjectArraySet<>();

        ChunkPos.stream(sectionPos.toChunkPos(), 1).forEach(nearbyPos -> {
            Chunk nearbyChunk = world.getChunk(nearbyPos.x, nearbyPos.z);
            for (ChunkSection section : nearbyChunk.getSectionArray()) {
                section.getBiomeContainer().forEachValue(nearbyBiomes::add);
            }
        });
        nearbyBiomes.retainAll(this.biomeSource.getBiomes());

        Registry<PlacedFeature> placedFeatureRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.PLACED_FEATURE);
        int totalSteps = Math.max(GenerationStep.Feature.values().length, featureSteps.size());

        for (int step = 0; step < totalSteps; step++) {
            int structureIndex = 0;
            if (structureAccessor.shouldGenerateStructures()) {
                for (net.minecraft.world.gen.structure.Structure structure : structuresByStep.getOrDefault(step, Collections.emptyList())) {
                    random.setDecoratorSeed(populationSeed, structureIndex, step);
                    Supplier<String> label = () -> structureRegistry.getId(structure).toString();
                    world.setCurrentlyGeneratingStructureName(label);
                    for (StructureStart start : structureAccessor.getStructureStarts(sectionPos, structure)) {
                        start.place(world, structureAccessor, this, random, writableArea(chunk), chunkPos);
                    }
                    structureIndex++;
                }
            }

            if (step >= featureSteps.size()) {
                continue;
            }

            IntSet featureIndexes = new IntArraySet();
            for (RegistryEntry<Biome> biome : nearbyBiomes) {
                List<RegistryEntryList<PlacedFeature>> features = this.getGenerationSettings(biome).getFeatures();
                if (step >= features.size()) {
                    continue;
                }

                PlacedFeatureIndexer.IndexedFeatures featureData = featureSteps.get(step);
                features.get(step).stream().map(RegistryEntry::value).forEach(feature -> featureIndexes.add(featureData.indexMapping().applyAsInt(feature)));
            }

            int[] orderedIndexes = featureIndexes.toIntArray();
            Arrays.sort(orderedIndexes);
            PlacedFeatureIndexer.IndexedFeatures featureData = featureSteps.get(step);
            for (int featureIndex : orderedIndexes) {
                PlacedFeature placedFeature = featureData.features().get(featureIndex);
                Supplier<String> label = () -> placedFeatureRegistry.getId(placedFeature).toString();
                world.setCurrentlyGeneratingStructureName(label);
                random.setDecoratorSeed(populationSeed, featureIndex, step);
                placedFeature.generate(world, this, random, origin);
            }
        }

        world.setCurrentlyGeneratingStructureName(null);
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structureAccessor, NoiseConfig noiseConfig, Chunk chunk) {
        delegate.buildSurface(region, structureAccessor, resolvedNoiseConfig(noiseConfig), chunk);
    }

    @Override
    public void populateEntities(ChunkRegion region) {
        if (settings.value().mobGenerationDisabled()) {
            return;
        }

        ChunkPos chunkPos = region.getCenterPos();
        RegistryEntry<Biome> biome = region.getBiome(chunkPos.getCenterAtY(region.getTopYInclusive()));
        ChunkRandom random = new ChunkRandom(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
        random.setPopulationSeed(effectiveSeed(), chunkPos.getStartX(), chunkPos.getStartZ());
        SpawnHelper.populateEntities(region, biome, chunkPos, random);
    }

    @Override
    public void setStructureStarts(
        DynamicRegistryManager registryManager,
        StructurePlacementCalculator structurePlacementCalculator,
        StructureAccessor structureAccessor,
        Chunk chunk,
        StructureTemplateManager structureTemplateManager,
        RegistryKey<World> worldKey
    ) {
        super.setStructureStarts(
            registryManager,
            createStructurePlacementCalculator(
                registryManager.getOrThrow(RegistryKeys.STRUCTURE_SET),
                resolvedNoiseConfig(registryManager),
                effectiveSeed()
            ),
            structureAccessor,
            chunk,
            structureTemplateManager,
            worldKey
        );
    }

    @Override
    public java.util.concurrent.CompletableFuture<Chunk> populateNoise(
        Blender blender,
        NoiseConfig noiseConfig,
        StructureAccessor structureAccessor,
        Chunk chunk
    ) {
        return delegate.populateNoise(blender, resolvedNoiseConfig(noiseConfig), structureAccessor, chunk);
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public int getWorldHeight() {
        return delegate.getWorldHeight();
    }

    @Override
    public int getMinimumY() {
        return delegate.getMinimumY();
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        return delegate.getHeight(x, z, heightmap, world, resolvedNoiseConfig(noiseConfig));
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        return delegate.getColumnSample(x, z, world, resolvedNoiseConfig(noiseConfig));
    }

    @Override
    public void appendDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        delegate.appendDebugHudText(text, resolvedNoiseConfig(noiseConfig), pos);
    }

    private List<PlacedFeatureIndexer.IndexedFeatures> featureSteps() {
        List<PlacedFeatureIndexer.IndexedFeatures> cached = cachedFeatureSteps;
        if (cached != null) {
            return cached;
        }

        List<PlacedFeatureIndexer.IndexedFeatures> built = PlacedFeatureIndexer.collectIndexedFeatures(
            List.copyOf(this.biomeSource.getBiomes()),
            biome -> this.getGenerationSettings(biome).getFeatures(),
            true
        );
        this.cachedFeatureSteps = built;
        return built;
    }

    private NoiseConfig resolvedNoiseConfig(NoiseConfig fallback) {
        NoiseConfig config = seededNoiseConfig;
        return config != null ? config : fallback;
    }

    private NoiseConfig resolvedNoiseConfig(DynamicRegistryManager registryManager) {
        NoiseConfig config = seededNoiseConfig;
        if (config != null) {
            return config;
        }
        return NoiseConfig.create(settings.value(), registryManager.getOrThrow(RegistryKeys.NOISE_PARAMETERS), effectiveSeed());
    }

    private long effectiveSeed() {
        return mixSeed(roundSeed + seedOffset);
    }

    private static BlockBox writableArea(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getStartX();
        int minZ = chunkPos.getStartZ();
        HeightLimitView heightView = chunk.getHeightLimitView();
        int minY = heightView.getBottomY() + 1;
        int maxY = heightView.getTopYInclusive();
        return new BlockBox(minX, minY, minZ, minX + 15, maxY, minZ + 15);
    }

    private static long mixSeed(long seed) {
        long mixed = seed;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }
}
