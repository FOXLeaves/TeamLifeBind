package com.teamlifebind.neoforge;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/** The cooperative survival team's bed belongs to its persistent world, not a combat round. */
final class TeamSurvivalBeds {
    private static final int VERSION = 1;
    private static final long MAX_BYTES = 8192;
    private static final Set<ResourceKey<Level>> SURVIVAL_LEVELS = Set.of(Level.OVERWORLD, Level.NETHER, Level.END);

    private TeamSurvivalBeds() { }

    static TeamCampaignController.Anchor load(MinecraftServer server) {
        if (server == null) return null;
        Path file = file(server);
        if (!Files.isRegularFile(file)) return null;
        try {
            if (Files.size(file) > MAX_BYTES) throw new IOException("Survival bed record exceeds size limit");
            Properties data = new Properties();
            try (var input = Files.newBufferedReader(file)) { data.load(input); }
            if (Integer.parseInt(data.getProperty("version", "-1")) != VERSION) throw new IOException("Unsupported survival bed record");
            Identifier dimension = Identifier.tryParse(data.getProperty("dimension", ""));
            ResourceKey<Level> key = dimension == null ? null : ResourceKey.create(Registries.DIMENSION, dimension);
            BlockPos pos = new BlockPos(Integer.parseInt(data.getProperty("x", "")), Integer.parseInt(data.getProperty("y", "")), Integer.parseInt(data.getProperty("z", "")));
            if (!valid(server, key, pos)) return null;
            return new TeamCampaignController.Anchor(key, pos);
        } catch (IOException | IllegalArgumentException error) {
            TeamLifeBindNeoForge.LOGGER.warn("Cannot load survival team bed: {}", error.getClass().getSimpleName());
            return null;
        }
    }

    static boolean save(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos) {
        if (server == null) return false;
        Path file = file(server), pending = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            if (dimension == null || pos == null) {
                Files.deleteIfExists(file);
                return true;
            }
            if (!valid(server, dimension, pos)) return false;
            Properties data = new Properties();
            data.setProperty("version", Integer.toString(VERSION));
            data.setProperty("dimension", dimension.identifier().toString());
            data.setProperty("x", Integer.toString(pos.getX()));
            data.setProperty("y", Integer.toString(pos.getY()));
            data.setProperty("z", Integer.toString(pos.getZ()));
            Files.createDirectories(file.getParent());
            try (var output = Files.newBufferedWriter(pending)) { data.store(output, "TeamLifeBind cooperative survival bed"); }
            try { Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException | IllegalArgumentException error) {
            TeamLifeBindNeoForge.LOGGER.warn("Cannot save survival team bed: {}", error.getClass().getSimpleName());
            try { Files.deleteIfExists(pending); } catch (IOException ignored) { }
            return false;
        }
    }

    private static boolean valid(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || !SURVIVAL_LEVELS.contains(dimension) || pos == null
            || Math.abs((long) pos.getX()) > 29999984 || Math.abs((long) pos.getZ()) > 29999984) return false;
        ServerLevel world = server.getLevel(dimension);
        return world != null && pos.getY() >= world.getMinY() && pos.getY() < world.getMaxY()
            && world.getBlockState(pos).is(BlockTags.BEDS);
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/teamlifebind-survival-bed.properties");
    }
}
