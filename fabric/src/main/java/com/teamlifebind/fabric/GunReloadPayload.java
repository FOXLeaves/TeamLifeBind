package com.teamlifebind.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Reload intent only: the server reads the currently held gun and owns its ammunition. */
public record GunReloadPayload() implements CustomPacketPayload {
    public static final GunReloadPayload INSTANCE = new GunReloadPayload();
    public static final Type<GunReloadPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(TeamLifeBindFabric.MOD_ID, "gun_reload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunReloadPayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public Type<GunReloadPayload> type() { return TYPE; }
}
