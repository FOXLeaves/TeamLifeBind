package com.teamlifebind.forge;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server confirmation drives visual recoil and reloads; it cannot authorize client damage. */
public record GunVisualPayload(UUID serial, long sequence, int ammo, int reloadRemaining, float heat, int reason, boolean fired) implements CustomPacketPayload {
    public static final Type<GunVisualPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("teamlifebind", "gun_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunVisualPayload> CODEC = new StreamCodec<FriendlyByteBuf, GunVisualPayload>() {
        @Override public GunVisualPayload decode(FriendlyByteBuf buffer) {
            return new GunVisualPayload(buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(), buffer.readVarInt(), buffer.readBoolean());
        }
        @Override public void encode(FriendlyByteBuf buffer, GunVisualPayload value) {
            buffer.writeUUID(value.serial()); buffer.writeVarLong(value.sequence()); buffer.writeVarInt(value.ammo());
            buffer.writeVarInt(value.reloadRemaining()); buffer.writeFloat(value.heat()); buffer.writeVarInt(value.reason()); buffer.writeBoolean(value.fired());
        }
    }.cast();
    @Override public Type<GunVisualPayload> type() { return TYPE; }
}
