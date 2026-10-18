package com.teamlifebind.forge;

import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;

/** Play-only, serverbound intent; Forge transfers the handler to the server thread. */
public final class TeamGunNetwork {
    public static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder
            .named(Identifier.fromNamespaceAndPath("teamlifebind", "gun_controls"))
            .networkProtocolVersion(2).optional().payloadChannel().play().serverbound()
            .addMain(GunReloadPayload.TYPE, GunReloadPayload.CODEC, TeamGunNetwork::handleReload)
            .addMain(GunInputPayload.TYPE, GunInputPayload.CODEC, TeamGunNetwork::handleInput)
            .clientbound().addMain(GunVisualPayload.TYPE, GunVisualPayload.CODEC, TeamGunNetwork::handleVisual)
            .build();
    private static Consumer<GunVisualPayload> clientVisual = ignored -> {};

    private TeamGunNetwork() {}

    public static void register() { /* Loading the channel registers its payload codec and handler. */ }

    public static void registerClientVisual(Consumer<GunVisualPayload> receiver) { clientVisual = receiver; }

    public static boolean canSendInput(net.minecraft.network.Connection connection) { return CHANNEL.isRemotePresent(connection); }

    public static void sendVisual(ServerPlayer player, GunVisualPayload payload) {
        var connection = player.connection.getConnection();
        if (connection.channel() != null && CHANNEL.isRemotePresent(connection)) CHANNEL.send(payload, connection);
    }

    private static void handleVisual(GunVisualPayload payload, CustomPayloadEvent.Context context) { clientVisual.accept(payload); }

    private static void handleInput(GunInputPayload payload, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            TeamLifeBindForge.acceptGunInput(player, payload);
        }
    }

    private static void handleReload(GunReloadPayload payload, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            TeamLifeBindForge.reloadHeldGun(player);
        }
    }
}
