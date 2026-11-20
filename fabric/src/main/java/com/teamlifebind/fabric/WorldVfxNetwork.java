package com.teamlifebind.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class WorldVfxNetwork {
    private WorldVfxNetwork() { }
    static void register() { PayloadTypeRegistry.clientboundPlay().register(WorldVfxPayload.TYPE, WorldVfxPayload.CODEC); }
    static void send(ServerPlayer player, WorldVfxPayload payload) {
        if (ServerPlayNetworking.canSend(player, WorldVfxPayload.TYPE)) ServerPlayNetworking.send(player, payload);
    }
}
