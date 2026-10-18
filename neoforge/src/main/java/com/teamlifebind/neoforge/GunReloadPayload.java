package com.teamlifebind.neoforge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A reload intent carries no weapon, ammunition or round state from the client. */
public record GunReloadPayload() implements CustomPacketPayload {
    public static final GunReloadPayload INSTANCE = new GunReloadPayload();
    public static final Type<GunReloadPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("teamlifebind", "gun_reload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunReloadPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<GunReloadPayload> type() { return TYPE; }
}
