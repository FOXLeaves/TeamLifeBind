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

@EventBusSubscriber(modid = TeamLifeBindNeoForge.MOD_ID, value = Dist.CLIENT)
public final class TeamMagicClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("teamlifebind", "magic"));
    static final KeyMapping CAST = new KeyMapping("key.teamlifebind.magic_cast", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY);
    static final KeyMapping WHEEL = new KeyMapping("key.teamlifebind.magic_wheel", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY);
    static final KeyMapping SCROLL = new KeyMapping("key.teamlifebind.magic_scroll", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CATEGORY);
    private TeamMagicClient() {}
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.registerCategory(CATEGORY); event.register(CAST); event.register(WHEEL); event.register(SCROLL); }
    @SubscribeEvent public static void payloads(RegisterClientPayloadHandlersEvent event) { event.register(MagicSyncPayload.TYPE, (payload, context) -> MagicClientPresentation.receive(payload)); event.register(MagicScenePayload.TYPE, (payload, context) -> MagicShaderRenderer.accept(payload)); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance(); MagicShaderRenderer.tick(client); boolean channel = client.getConnection() != null && client.getConnection().hasChannel(MagicInputPayload.TYPE);
        MagicClientPresentation.tick(client, channel, ClientPacketDistributor::sendToServer);
        boolean cast = false, wheel = false; while (CAST.consumeClick()) cast = true; while (WHEEL.consumeClick()) wheel = true; while (SCROLL.consumeClick()) { }
        if (!channel || !MagicClientPresentation.controls(client)) return;
        if (wheel && WHEEL.isDown()) client.gui.setScreen(new MagicWheelScreen()); else if (cast) MagicClientPresentation.cast();
    }
    public static boolean scroll(double amount) {
        Minecraft client = Minecraft.getInstance();
        if (amount == 0 || !SCROLL.isDown() || !MagicClientPresentation.controls(client) || client.getConnection() == null || !client.getConnection().hasChannel(MagicInputPayload.TYPE)) return false;
        MagicClientPresentation.scroll(amount); return true;
    }
}
