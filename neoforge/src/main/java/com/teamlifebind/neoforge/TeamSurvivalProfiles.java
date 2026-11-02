package com.teamlifebind.neoforge;

import com.mojang.serialization.DynamicOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/** Per-world survival snapshots are isolated from temporary lobby and combat inventories. */
public final class TeamSurvivalProfiles {
    private static final int VERSION = 1;
    private static final int MAIN_SLOTS = 36;
    private static final long MAX_BYTES = 4L * 1024L * 1024L;
    private static final long MAX_DEATH_MARKER_BYTES = 128L;
    private static final String DEATH_MARKER_VERSION = "TLB-SUR-OFFLINE-DEATH-1:";
    private static final Set<ResourceKey<Level>> SURVIVAL_LEVELS = Set.of(Level.OVERWORLD, Level.NETHER, Level.END);

    public TeamSurvivalProfiles() { }

    /** A restored snapshot is consumed; a later capture is required before another mode transition. */
    public boolean restore(ServerPlayer player) {
        Path file = profile(player);
        // A deferred shared death outranks any old inventory, including after a restart.
        if (Files.exists(deathMarker(player.level().getServer(), player.getUUID()))) return false;
        if (!Files.isRegularFile(file)) return false;
        boolean consumed = false;
        try {
            if (Files.size(file) > MAX_BYTES) throw new IOException("Survival profile exceeds size limit");
            CompoundTag data = NbtIo.readCompressed(file, NbtAccounter.create(MAX_BYTES));
            if (data.getInt("version").orElse(-1) != VERSION || !player.getUUID().toString().equals(data.getString("player").orElse(""))) {
                throw new IOException("Unsupported survival profile");
            }
            Identifier dimension = Identifier.tryParse(data.getString("dimension").orElse(""));
            ResourceKey<Level> key = dimension == null ? null : ResourceKey.create(Registries.DIMENSION, dimension);
            if (key == null || !SURVIVAL_LEVELS.contains(key)) throw new IOException("Invalid survival dimension");
            ServerLevel world = player.level().getServer().getLevel(key);
            if (world == null) return false;
            double x = data.getDouble("x").orElse(Double.NaN), y = data.getDouble("y").orElse(Double.NaN), z = data.getDouble("z").orElse(Double.NaN);
            float yaw = data.getFloat("yaw").orElse(Float.NaN), pitch = data.getFloat("pitch").orElse(Float.NaN);
            int selected = data.getInt("selected").orElse(-1), level = data.getInt("level").orElse(-1), total = data.getInt("total").orElse(-1);
            float progress = data.getFloat("progress").orElse(Float.NaN), health = data.getFloat("health").orElse(Float.NaN), saturation = data.getFloat("saturation").orElse(Float.NaN);
            int food = data.getInt("food").orElse(-1);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 29999984 || Math.abs(z) > 29999984
                || y < world.getMinY() - 64 || y > world.getMaxY() + 64 || !Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90
                || selected < 0 || selected > 8 || level < 0 || total < 0 || !Float.isFinite(progress) || progress < 0 || progress >= 1
                || !Float.isFinite(health) || health <= 0 || health > 1024 || food < 0 || food > 20 || !Float.isFinite(saturation) || saturation < 0 || saturation > 20) {
                throw new IOException("Invalid survival profile values");
            }
            DynamicOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            ListTag items = data.getList("items").orElseThrow(() -> new IOException("Missing survival inventory"));
            ListTag equipment = data.getList("equipment").orElseThrow(() -> new IOException("Missing survival equipment"));
            if (items.size() > MAIN_SLOTS || equipment.size() > EquipmentSlot.values().length - 1) throw new IOException("Too many survival slots");
            List<Slot> slots = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (int index = 0; index < items.size(); index++) {
                CompoundTag entry = items.getCompound(index).orElseThrow(() -> new IOException("Invalid survival slot"));
                int slot = entry.getInt("slot").orElse(-1);
                if (slot < 0 || slot >= MAIN_SLOTS || !seen.add(slot)) throw new IOException("Invalid survival inventory slot");
                slots.add(new Slot(slot, decode(entry, ops)));
            }
            List<Equipment> worn = new ArrayList<>();
            Set<EquipmentSlot> seenEquipment = new HashSet<>();
            for (int index = 0; index < equipment.size(); index++) {
                CompoundTag entry = equipment.getCompound(index).orElseThrow(() -> new IOException("Invalid survival equipment"));
                EquipmentSlot slot;
                try { slot = EquipmentSlot.valueOf(entry.getString("slot").orElse("")); }
                catch (IllegalArgumentException invalid) { throw new IOException("Invalid survival equipment slot"); }
                if (slot == EquipmentSlot.MAINHAND || !seenEquipment.add(slot)) throw new IOException("Duplicate survival equipment slot");
                worn.add(new Equipment(slot, decode(entry, ops)));
            }
            // Validate every item before moving or replacing any inventory content.
            if (!player.teleportTo(world, x, y, z, Set.of(), yaw, pitch, false)) return false;
            Files.delete(file);
            consumed = true;
            player.getInventory().clearContent();
            for (EquipmentSlot slot : EquipmentSlot.values()) if (slot != EquipmentSlot.MAINHAND) player.setItemSlot(slot, ItemStack.EMPTY);
            for (Slot slot : slots) player.getInventory().setItem(slot.index, slot.stack);
            for (Equipment equipmentSlot : worn) player.setItemSlot(equipmentSlot.slot, equipmentSlot.stack);
            player.getInventory().setSelectedSlot(selected);
            player.experienceLevel = level;
            player.totalExperience = total;
            player.experienceProgress = progress;
            player.setHealth(Math.min(player.getMaxHealth(), health));
            player.getFoodData().setFoodLevel(food);
            player.getFoodData().setSaturation(saturation);
            player.fallDistance = 0;
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return true;
        } catch (IOException | IllegalArgumentException error) {
            if (consumed) throw new IllegalStateException("Survival inventory commit failed", error);
            TeamLifeBindNeoForge.LOGGER.warn("Cannot restore survival profile for {}: {}", player.getUUID(), error.getClass().getSimpleName());
            return false;
        }
    }

    public void capture(ServerPlayer player) { tryCapture(player); }

    /** Callers that will clear an inventory can abort the transition if persistence fails. */
    public boolean tryCapture(ServerPlayer player) {
        if (!SURVIVAL_LEVELS.contains(player.level().dimension())) return false;
        if (Files.exists(deathMarker(player.level().getServer(), player.getUUID()))) return false;
        if (!player.isAlive()) { invalidate(player); return false; }
        Path file = profile(player), pending = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            CompoundTag data = new CompoundTag();
            data.putInt("version", VERSION); data.putString("player", player.getUUID().toString());
            data.putString("dimension", player.level().dimension().identifier().toString());
            data.putDouble("x", player.getX()); data.putDouble("y", player.getY()); data.putDouble("z", player.getZ());
            data.putFloat("yaw", player.getYRot()); data.putFloat("pitch", player.getXRot());
            data.putInt("selected", player.getInventory().getSelectedSlot());
            data.putInt("level", player.experienceLevel); data.putInt("total", player.totalExperience); data.putFloat("progress", player.experienceProgress);
            data.putFloat("health", player.getHealth()); data.putInt("food", player.getFoodData().getFoodLevel()); data.putFloat("saturation", player.getFoodData().getSaturationLevel());
            DynamicOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            ListTag items = new ListTag(), equipment = new ListTag();
            for (int slot = 0; slot < MAIN_SLOTS; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.isEmpty()) continue;
                CompoundTag entry = encode(stack, ops); entry.putInt("slot", slot); items.add(entry);
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot == EquipmentSlot.MAINHAND) continue;
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                CompoundTag entry = encode(stack, ops); entry.putString("slot", slot.name()); equipment.add(entry);
            }
            data.put("items", items); data.put("equipment", equipment);
            if (data.sizeInBytes() > MAX_BYTES) throw new IOException("Survival profile exceeds size limit");
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(data, pending);
            if (Files.size(pending) > MAX_BYTES) throw new IOException("Survival profile exceeds size limit");
            try { Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException | IllegalArgumentException error) {
            TeamLifeBindNeoForge.LOGGER.warn("Cannot save survival profile for {}: {}", player.getUUID(), error.getClass().getSimpleName());
            try { Files.deleteIfExists(pending); } catch (IOException ignored) { }
            return false;
        }
    }

    /** Death must never replay a previously saved inventory after vanilla has dropped it. */
    public void invalidate(ServerPlayer player) {
        try { Files.deleteIfExists(profile(player)); }
        catch (IOException error) { TeamLifeBindNeoForge.LOGGER.warn("Cannot invalidate survival profile for {}: {}", player.getUUID(), error.getClass().getSimpleName()); }
    }

    /** Persist the deferred death before removing its inventory snapshot, so interrupted writes cannot revive it. */
    public boolean markOfflineDeath(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return false;
        Path marker = deathMarker(server, playerId), pending = marker.resolveSibling(marker.getFileName() + ".tmp");
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(pending, DEATH_MARKER_VERSION + playerId, StandardCharsets.UTF_8);
            try { Files.move(pending, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(pending, marker, StandardCopyOption.REPLACE_EXISTING); }
            Files.deleteIfExists(profile(server, playerId));
            return true;
        } catch (IOException | IllegalArgumentException error) {
            TeamLifeBindNeoForge.LOGGER.warn("Cannot record offline survival death for {}: {}", playerId, error.getClass().getSimpleName());
            try { Files.deleteIfExists(pending); } catch (IOException ignored) { }
            return false;
        }
    }

    /** Settle a deferred shared death before an adapter captures the returning player's vanilla playerdata. */
    public boolean applyOfflineDeath(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Path marker = deathMarker(server, player.getUUID());
        if (!Files.exists(marker)) return true;
        try {
            if (!Files.isRegularFile(marker) || Files.size(marker) > MAX_DEATH_MARKER_BYTES
                || !(DEATH_MARKER_VERSION + player.getUUID()).equals(Files.readString(marker, StandardCharsets.UTF_8)))
                throw new IOException("Invalid offline survival death marker");
            // Complete persistence checks before touching live inventory. The marker also blocks restore/capture.
            Files.deleteIfExists(profile(server, player.getUUID()));
            player.closeContainer();
            if (!player.level().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY)) {
                player.getInventory().dropAll();
                player.totalExperience = 0;
                player.experienceLevel = 0;
                player.experienceProgress = 0.0F;
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            }
            Files.delete(marker);
            return true;
        } catch (IOException | IllegalArgumentException error) {
            TeamLifeBindNeoForge.LOGGER.warn("Cannot settle offline survival death for {}: {}", player.getUUID(), error.getClass().getSimpleName());
            return false;
        }
    }

    private static ItemStack decode(CompoundTag data, DynamicOps<Tag> ops) throws IOException {
        Tag raw = data.get("item");
        if (raw == null) throw new IOException("Missing survival item");
        return ItemStack.CODEC.parse(ops, raw).result().orElseThrow(() -> new IOException("Invalid survival item"));
    }
    private static CompoundTag encode(ItemStack stack, DynamicOps<Tag> ops) throws IOException {
        CompoundTag entry = new CompoundTag();
        Tag raw = ItemStack.CODEC.encodeStart(ops, stack).result().orElseThrow(() -> new IOException("Cannot encode survival item"));
        entry.put("item", raw); return entry;
    }
    private static Path profile(ServerPlayer player) {
        return profile(player.level().getServer(), player.getUUID());
    }
    private static Path profile(MinecraftServer server, UUID playerId) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/teamlifebind-survival").resolve(playerId + ".dat");
    }
    private static Path deathMarker(MinecraftServer server, UUID playerId) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/teamlifebind-survival").resolve(playerId + ".dead");
    }
    private record Slot(int index, ItemStack stack) { }
    private record Equipment(EquipmentSlot slot, ItemStack stack) { }
}
