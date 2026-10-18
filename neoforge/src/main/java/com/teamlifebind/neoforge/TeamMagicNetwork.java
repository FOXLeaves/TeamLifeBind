package com.teamlifebind.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class TeamMagicNetwork {
    private TeamMagicNetwork() {}
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().executesOn(HandlerThread.MAIN)
                .playToServer(MagicInputPayload.TYPE, MagicInputPayload.CODEC, TeamMagicNetwork::handleInput)
                .playToClient(MagicSyncPayload.TYPE, MagicSyncPayload.CODEC)
                .playToClient(MagicScenePayload.TYPE, MagicScenePayload.CODEC);
    }
    public static void sendSync(ServerPlayer player, MagicSyncPayload payload) {
        if (player.connection.getConnection().channel() != null && player.connection.hasChannel(MagicSyncPayload.TYPE)) player.connection.send(payload);
    }
    public static void sendScene(ServerPlayer player, MagicScenePayload payload) {
        if (player.connection.getConnection().channel() != null && player.connection.hasChannel(MagicScenePayload.TYPE)) player.connection.send(payload);
    }
    private static void handleInput(MagicInputPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player) TeamLifeBindNeoForge.manager().magic().acceptInput(player, payload);
    }
}
