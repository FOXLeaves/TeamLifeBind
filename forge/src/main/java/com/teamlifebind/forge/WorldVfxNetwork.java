package com.teamlifebind.forge;

import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;

public final class WorldVfxNetwork {
    private static Consumer<WorldVfxPayload> receiver = ignored -> { };
    private static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath("teamlifebind", "world_vfx_v3"))
        .networkProtocolVersion(1).optional().payloadChannel().play().clientbound()
        .addMain(WorldVfxPayload.TYPE, WorldVfxPayload.CODEC, (payload, context) -> receiver.accept(payload)).build();
    private WorldVfxNetwork() { }
    static void register() { }
    static void registerClient(Consumer<WorldVfxPayload> handler) { receiver = handler; }
    static void send(ServerPlayer player, WorldVfxPayload payload) {
        var connection = player.connection.getConnection();
        if (connection.channel() != null && CHANNEL.isRemotePresent(connection)) CHANNEL.send(payload, connection);
    }
}
