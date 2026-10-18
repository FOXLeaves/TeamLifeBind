package com.teamlifebind.forge;

import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;

public final class TeamMagicNetwork {
    public static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath("teamlifebind", "magic_controls"))
            .networkProtocolVersion(1).optional().payloadChannel().play().serverbound()
            .addMain(MagicInputPayload.TYPE, MagicInputPayload.CODEC, TeamMagicNetwork::handleInput)
            .clientbound().addMain(MagicSyncPayload.TYPE, MagicSyncPayload.CODEC, TeamMagicNetwork::handleSync)
            .addMain(MagicScenePayload.TYPE, MagicScenePayload.CODEC, TeamMagicNetwork::handleScene).build();
    private static Consumer<MagicSyncPayload> clientSync = ignored -> {};
    private static Consumer<MagicScenePayload> clientScene = ignored -> {};
    private TeamMagicNetwork() {}
    public static void register() { }
    public static void registerClientSync(Consumer<MagicSyncPayload> receiver) { clientSync = receiver; }
    public static void registerClientScene(Consumer<MagicScenePayload> receiver) { clientScene = receiver; }
    public static boolean canSendInput(net.minecraft.network.Connection connection) { return CHANNEL.isRemotePresent(connection); }
    public static void sendSync(ServerPlayer player, MagicSyncPayload payload) {
        var connection = player.connection.getConnection();
        if (connection.channel() != null && CHANNEL.isRemotePresent(connection)) CHANNEL.send(payload, connection);
    }
    private static void handleSync(MagicSyncPayload payload, CustomPayloadEvent.Context context) { clientSync.accept(payload); }
    private static void handleScene(MagicScenePayload payload, CustomPayloadEvent.Context context) { clientScene.accept(payload); }
    public static void sendScene(ServerPlayer player, MagicScenePayload payload) {
        var connection = player.connection.getConnection();
        if (connection.channel() != null && CHANNEL.isRemotePresent(connection)) CHANNEL.send(payload, connection);
    }
    private static void handleInput(MagicInputPayload payload, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player) TeamLifeBindForge.manager().magic().acceptInput(player, payload);
    }
}
