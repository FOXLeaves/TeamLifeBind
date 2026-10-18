package com.teamlifebind.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Physical-client subscriber; Options handles rebinding and persistence. */
@EventBusSubscriber(modid = TeamLifeBindNeoForge.MOD_ID, value = Dist.CLIENT)
public final class TeamGunClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("teamlifebind", "controls"));
    private static final KeyMapping RELOAD = new KeyMapping("key.teamlifebind.reload",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY);

    private TeamGunClient() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(RELOAD);
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(GunVisualPayload.TYPE, (payload, context) -> GunClientPresentation.receive(payload));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        boolean pressed = false;
        while (RELOAD.consumeClick()) pressed = true;
        Minecraft client = Minecraft.getInstance();
        boolean channel = client.getConnection() != null && client.getConnection().hasChannel(GunInputPayload.TYPE);
        GunClientPresentation.tick(client, channel, payload -> ClientPacketDistributor.sendToServer(payload));
        if (pressed && channel && GunClientPresentation.canControl(client) && GunClientPresentation.hasInitializedGun(client)) {
            ClientPacketDistributor.sendToServer(GunReloadPayload.INSTANCE);
        }
    }
}
