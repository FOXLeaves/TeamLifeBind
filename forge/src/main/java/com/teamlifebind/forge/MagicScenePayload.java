package com.teamlifebind.forge;

import com.teamlifebind.common.MagicMechanic;
import com.teamlifebind.common.SpellCatalogue;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sparse, server-authoritative shader scene intents. Contains no rendered vertices or particles. */
public record MagicScenePayload(long sceneId, UUID caster, Identifier dimension, String spellId,
        int operation, int phase, int mechanic, float ageTicks, int durationTicks,
        double originX, double originY, double originZ, double endX, double endY, double endZ, float radius)
        implements CustomPacketPayload {
    public static final int UPSERT = 0, REMOVE = 1, CLEAR_CASTER = 2, REMOVE_RUN = 3;
    public static final Type<MagicScenePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("teamlifebind", "magic_scene"));
    public MagicScenePayload {
        Objects.requireNonNull(caster); Objects.requireNonNull(dimension); Objects.requireNonNull(spellId);
        if (sceneId < 0 || operation < UPSERT || operation > REMOVE_RUN || phase < 0 || phase > 3
                || mechanic < 0 || mechanic >= MagicMechanic.values().length || !Float.isFinite(ageTicks)
                || ageTicks < 0 || ageTicks > 12000 || durationTicks < 1 || durationTicks > 600
                || !Float.isFinite(radius) || radius < 0 || radius > 32
                || !coordinate(originX) || !coordinate(originY) || !coordinate(originZ)
                || !coordinate(endX) || !coordinate(endY) || !coordinate(endZ)
                || (operation == UPSERT && (sceneId == 0 || radius <= 0 || SpellCatalogue.byId(spellId) == null))
                || (operation == REMOVE_RUN && (sceneId == 0 || (sceneId & 255) != 0))) {
            throw new IllegalArgumentException("Invalid magic scene intent");
        }
    }
    private static boolean coordinate(double value) { return Double.isFinite(value) && Math.abs(value) <= 30_000_000; }
    public static final StreamCodec<RegistryFriendlyByteBuf, MagicScenePayload> CODEC = new StreamCodec<FriendlyByteBuf, MagicScenePayload>() {
        @Override public MagicScenePayload decode(FriendlyByteBuf b) {
            return new MagicScenePayload(b.readVarLong(), b.readUUID(), Identifier.parse(b.readUtf(120)), b.readUtf(80),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readVarInt(),
                    b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat());
        }
        @Override public void encode(FriendlyByteBuf b, MagicScenePayload v) {
            b.writeVarLong(v.sceneId); b.writeUUID(v.caster); b.writeUtf(v.dimension.toString(), 120); b.writeUtf(v.spellId, 80);
            b.writeVarInt(v.operation); b.writeVarInt(v.phase); b.writeVarInt(v.mechanic); b.writeFloat(v.ageTicks); b.writeVarInt(v.durationTicks);
            b.writeDouble(v.originX); b.writeDouble(v.originY); b.writeDouble(v.originZ);
            b.writeDouble(v.endX); b.writeDouble(v.endY); b.writeDouble(v.endZ); b.writeFloat(v.radius);
        }
    }.cast();
    @Override public Type<MagicScenePayload> type() { return TYPE; }
}
