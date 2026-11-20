package com.teamlifebind.forge;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Cosmetic server events only. No client-to-server codec or gameplay authority. */
public record WorldVfxPayload(long id, UUID owner, Identifier dimension, int kind,
        double x, double y, double z, double endX, double endY, double endZ, float radius) implements CustomPacketPayload {
    public enum Kind {
        MUZZLE(4, 0xFFD18A, true, false), TRACER(5, 0xFFCB76, true, false),
        IMPACT(12, 0xFFC896, false, false), EXPLOSION(26, 0xFF692A, false, false),
        BEACON(24, 0x63DDFF, true, false), SUPPLY_OPEN(32, 0xFFD977, false, false),
        HEAL(28, 0x72FFD0, false, true), MANA(28, 0x789EFF, false, true),
        REVIVE(36, 0xA3FFF0, false, true), BOSS(40, 0xFF6C92, false, false),
        VICTORY(48, 0xFFD177, false, true), RELIC(36, 0xC398FF, false, true),
        TEAM_SIGNAL(34, 0x6FFFE6, false, false), TEAM_ANCHOR(40, 0x88DFFF, false, false),
        TOTEM_RESCUE(38, 0xC6FF89, false, true), SCOUT_PULSE(32, 0x75D9FF, false, false),
        CONTROL_POINT(24, 0xFFCC72, false, false), CONTROL_CAPTURE(40, 0xFFE6A0, false, false),
        SPELL_LEARN(42, 0xD5B5FF, false, true), MANA_GROWTH(42, 0x7DE7FF, false, true),
        ENCOUNTER_SPAWN(28, 0xFF8065, false, false), WAVE_CLEAR(42, 0xA5FFD0, false, true),
        ROUTE_LOCK(40, 0x8AC8FF, false, false), BORDER_WARNING(48, 0xFFA15D, false, true);
        final int ticks, color;
        final boolean segment, attached;
        Kind(int ticks, int color, boolean segment, boolean attached) {
            this.ticks = ticks; this.color = color; this.segment = segment; this.attached = attached;
        }
        boolean sustained() { return this == BEACON || this == CONTROL_POINT; }
    }
    public WorldVfxPayload {
        if (owner == null || dimension == null || kind < 0 || kind >= Kind.values().length
                || !valid(x) || !valid(y) || !valid(z) || !valid(endX) || !valid(endY) || !valid(endZ)
                || !Float.isFinite(radius) || radius < 0.05F || radius > 16
                || square(x-endX) + square(y-endY) + square(z-endZ) > 192*192)
            throw new IllegalArgumentException("Invalid bounded world visual");
    }
    private static boolean valid(double value) { return Double.isFinite(value) && Math.abs(value) <= 30_000_000; }
    private static double square(double value) { return value * value; }
    // A separate capability prevents older clients from decoding unknown kind IDs.
    public static final Type<WorldVfxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("teamlifebind", "world_vfx_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldVfxPayload> CODEC = new StreamCodec<FriendlyByteBuf, WorldVfxPayload>() {
        public WorldVfxPayload decode(FriendlyByteBuf b) {
            return new WorldVfxPayload(b.readVarLong(), b.readUUID(), b.readIdentifier(), b.readVarInt(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat());
        }
        public void encode(FriendlyByteBuf b, WorldVfxPayload v) {
            b.writeVarLong(v.id); b.writeUUID(v.owner); b.writeIdentifier(v.dimension); b.writeVarInt(v.kind);
            b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z);
            b.writeDouble(v.endX); b.writeDouble(v.endY); b.writeDouble(v.endZ); b.writeFloat(v.radius);
        }
    }.cast();
    @Override public Type<WorldVfxPayload> type() { return TYPE; }
}
