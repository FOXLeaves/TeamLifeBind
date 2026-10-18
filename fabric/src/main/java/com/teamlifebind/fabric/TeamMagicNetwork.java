package com.teamlifebind.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class TeamMagicNetwork {
    private TeamMagicNetwork() {}
    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(MagicInputPayload.TYPE, MagicInputPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MagicSyncPayload.TYPE, MagicSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MagicScenePayload.TYPE, MagicScenePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MagicInputPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (context.server().getPlayerList().getPlayer(player.getUUID()) == player) TeamLifeBindFabric.manager().magic().acceptInput(player, payload);
            });
        });
    }
    public static void sendSync(ServerPlayer player, MagicSyncPayload payload) {
        if (ServerPlayNetworking.canSend(player, MagicSyncPayload.TYPE)) ServerPlayNetworking.send(player, payload);
    }
    public static void sendScene(ServerPlayer player, MagicScenePayload payload) {
        if (ServerPlayNetworking.canSend(player, MagicScenePayload.TYPE)) ServerPlayNetworking.send(player, payload);
    }
}
