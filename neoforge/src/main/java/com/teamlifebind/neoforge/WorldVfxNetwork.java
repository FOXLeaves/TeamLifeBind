package com.teamlifebind.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class WorldVfxNetwork {
    private WorldVfxNetwork() { }
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().executesOn(HandlerThread.MAIN).playToClient(WorldVfxPayload.TYPE, WorldVfxPayload.CODEC);
    }
    static void send(ServerPlayer player, WorldVfxPayload payload) {
        if (player.connection.getConnection().channel() != null && player.connection.hasChannel(WorldVfxPayload.TYPE)) player.connection.send(payload);
    }
}
