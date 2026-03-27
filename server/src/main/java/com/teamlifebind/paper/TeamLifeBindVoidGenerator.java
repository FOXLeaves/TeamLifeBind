package com.teamlifebind.paper;

import java.util.Random;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

final class TeamLifeBindVoidGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(
            WorldInfo worldInfo,
            Random random,
            int chunkX,
            int chunkZ,
            ChunkData chunkData
    ) {
        // 空世界，不生成任何方块
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public Location getFixedSpawnLocation(
            World world,
            Random random
    ) {
        return new Location(world, 0.5D, 101.0D, 0.5D);
    }

    @Override
    public int getBaseHeight(
            WorldInfo worldInfo,
            Random random,
            int x,
            int z,
            HeightMap heightMap
    ) {
        return 0;
    }
}
