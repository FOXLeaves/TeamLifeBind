package com.teamlifebind.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;

/** Loaded only on the physical client; Options handles rebinding and persistence. */
final class TeamGunClient {
    private static final KeyMapping RELOAD = new KeyMapping("key.teamlifebind.reload",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_R,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("teamlifebind", "controls")));

    private TeamGunClient() {}

    static void register() {
        TeamGunNetwork.registerClientVisual(GunClientPresentation::receive);
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(RELOAD));
        TickEvent.ClientTickEvent.Post.BUS.addListener(TeamGunClient::tick);
    }

    private static void tick(TickEvent.ClientTickEvent.Post event) {
        boolean pressed = false;
        while (RELOAD.consumeClick()) pressed = true;
        Minecraft client = Minecraft.getInstance();
        boolean channel = client.getConnection() != null && TeamGunNetwork.canSendInput(client.getConnection().getConnection());
        GunClientPresentation.tick(client, channel, payload -> TeamGunNetwork.CHANNEL.send(payload, PacketDistributor.SERVER.noArg()));
        if (pressed && channel && GunClientPresentation.canControl(client) && GunClientPresentation.hasInitializedGun(client)) {
            TeamGunNetwork.CHANNEL.send(GunReloadPayload.INSTANCE, PacketDistributor.SERVER.noArg());
        }
    }
}
