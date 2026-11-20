package com.teamlifebind.fabric;

import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import com.teamlifebind.fabric.WorldVfxPayload.Kind;

/** Bounded events from successful gameplay transitions, shared by nearby observers. */
public final class WorldVfx {
    private static final UUID WORLD = new UUID(0, 0);
    private static final WeakHashMap<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();
    private static long sequence;
    private static final class Budget { long tick = Long.MIN_VALUE; int count; }
    private WorldVfx() { }
    public static void player(ServerPlayer player, Kind kind, float radius) {
        emit(player.level(), player.getUUID(), kind, player.position().add(0, 1, 0), player.position().add(0, 2, 0), radius);
    }
    public static void personal(ServerPlayer player, Kind kind, float radius) {
        send(player.level(), player.getUUID(), kind, player.position().add(0, 1, 0), player.position().add(0, 2, 0),
            radius, ++sequence, java.util.List.of(player));
    }
    public static void at(ServerLevel level, Kind kind, Vec3 point, float radius) {
        emit(level, WORLD, kind, point, point.add(0, 1, 0), radius);
    }
    public static void emit(ServerLevel level, UUID owner, Kind kind, Vec3 start, Vec3 end, float radius) {
        send(level, owner, kind, start, end, radius, ++sequence);
    }
    public static void team(ServerLevel level, Iterable<ServerPlayer> viewers, Kind kind, Vec3 point, float radius) {
        send(level, WORLD, kind, point, point.add(0, 1, 0), radius, ++sequence, viewers);
    }
    public static void controlPoint(ServerLevel level, net.minecraft.core.BlockPos pos, float radius) {
        Vec3 start = Vec3.atBottomCenterOf(pos).add(0, 0.12, 0);
        send(level, WORLD, Kind.CONTROL_POINT, start, start.add(0, 1, 0), radius, pos.asLong());
    }
    public static void beacon(ServerLevel level, net.minecraft.core.BlockPos pos) {
        // A stable per-dimension ID refreshes the same light column rather than stacking it.
        Vec3 start = Vec3.atCenterOf(pos);
        send(level, WORLD, Kind.BEACON, start, start.add(0, 18, 0), 1.25F, pos.asLong());
    }
    private static void send(ServerLevel level, UUID owner, Kind kind, Vec3 start, Vec3 end, float radius, long id) {
        send(level, owner, kind, start, end, radius, id, level.players());
    }
    private static void send(ServerLevel level, UUID owner, Kind kind, Vec3 start, Vec3 end, float radius, long id, Iterable<ServerPlayer> viewers) {
        Budget budget = BUDGETS.computeIfAbsent(level, ignored -> new Budget());
        long tick = level.getGameTime();
        if (budget.tick != tick) { budget.tick = tick; budget.count = 0; }
        if (budget.count >= 96) return;
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() > 192*192) end = start.add(direction.normalize().scale(192));
        WorldVfxPayload payload;
        try { payload = new WorldVfxPayload(id, owner, level.dimension().identifier(), kind.ordinal(),
            start.x, start.y, start.z, end.x, end.y, end.z, radius); }
        catch (IllegalArgumentException ignored) { return; } // Cosmetic bounds never interrupt combat.
        budget.count++;
        Vec3 center = start.add(end).scale(0.5);
        double reach = 112 + start.distanceTo(end) * 0.5;
        for (ServerPlayer viewer : viewers) if (viewer.level() == level && viewer.position().distanceToSqr(center) <= reach*reach)
            WorldVfxNetwork.send(viewer, payload);
    }
}
