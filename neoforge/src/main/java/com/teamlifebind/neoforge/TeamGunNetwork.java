package com.teamlifebind.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** NeoForge registers the play-only intent and runs its handler on the server thread. */
public final class TeamGunNetwork {
    private TeamGunNetwork() {}

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").optional().executesOn(HandlerThread.MAIN)
                .playToServer(GunReloadPayload.TYPE, GunReloadPayload.CODEC, TeamGunNetwork::handleReload)
                .playToServer(GunInputPayload.TYPE, GunInputPayload.CODEC, TeamGunNetwork::handleInput)
                .playToClient(GunVisualPayload.TYPE, GunVisualPayload.CODEC);
    }

    public static void sendVisual(ServerPlayer player, GunVisualPayload payload) {
        if (player.connection.getConnection().channel() != null && player.connection.hasChannel(GunVisualPayload.TYPE)) player.connection.send(payload);
    }

    private static void handleInput(GunInputPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            TeamLifeBindNeoForge.acceptGunInput(player, payload);
        }
    }

    private static void handleReload(GunReloadPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            TeamLifeBindNeoForge.reloadHeldGun(player);
        }
    }
}
