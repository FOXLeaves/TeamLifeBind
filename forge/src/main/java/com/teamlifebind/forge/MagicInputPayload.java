package com.teamlifebind.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Only selection, cast, and cancel intent crosses the trust boundary. */
public record MagicInputPayload(int slot, int action) implements CustomPacketPayload {
    public static final int SELECT = 0, CAST = 1, CANCEL = 2;
    public static final Type<MagicInputPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("teamlifebind", "magic_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MagicInputPayload> CODEC = new StreamCodec<FriendlyByteBuf, MagicInputPayload>() {
        @Override public MagicInputPayload decode(FriendlyByteBuf buffer) { return new MagicInputPayload(buffer.readVarInt(), buffer.readVarInt()); }
        @Override public void encode(FriendlyByteBuf buffer, MagicInputPayload value) { buffer.writeVarInt(value.slot()); buffer.writeVarInt(value.action()); }
    }.cast();
    @Override public Type<MagicInputPayload> type() { return TYPE; }
}
