package com.teamlifebind.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Both directions register codecs without loading any client class on a dedicated server. */
public final class TeamGunNetwork {
    private TeamGunNetwork() {}

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(GunReloadPayload.TYPE, GunReloadPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GunInputPayload.TYPE, GunInputPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GunVisualPayload.TYPE, GunVisualPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(GunReloadPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (context.server().getPlayerList().getPlayer(player.getUUID()) == player) TeamLifeBindFabric.reloadHeldGun(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(GunInputPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (context.server().getPlayerList().getPlayer(player.getUUID()) == player) TeamLifeBindFabric.acceptGunInput(player, payload);
            });
        });
    }

    public static void sendVisual(ServerPlayer player, GunVisualPayload payload) {
        if (ServerPlayNetworking.canSend(player, GunVisualPayload.TYPE)) ServerPlayNetworking.send(player, payload);
    }
}
