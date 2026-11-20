package com.teamlifebind.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class TeamMagicClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("teamlifebind", "magic"));
    static final KeyMapping CAST = new KeyMapping("key.teamlifebind.magic_cast", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY);
    static final KeyMapping WHEEL = new KeyMapping("key.teamlifebind.magic_wheel", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY);
    static final KeyMapping SCROLL = new KeyMapping("key.teamlifebind.magic_scroll", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CATEGORY);
    static final KeyMapping HUD_EDITOR = new KeyMapping("key.teamlifebind.hud_editor", InputConstants.Type.KEYBOARD, InputConstants.KEY_F8, CATEGORY);
    private TeamMagicClient() {}
    static void register() {
        net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(context -> new com.teamlifebind.aui.client.gui.pip.ApricityUiPipRenderer());
        net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(context -> new com.teamlifebind.clientui.ArchiveLoadingRenderer());
        net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(context -> new com.teamlifebind.clientui.ArchiveBackdropRenderer());
        net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(context -> new com.teamlifebind.clientui.MagicSigilRenderer());
        KeyMappingHelper.registerKeyMapping(CAST); KeyMappingHelper.registerKeyMapping(WHEEL); KeyMappingHelper.registerKeyMapping(SCROLL); KeyMappingHelper.registerKeyMapping(HUD_EDITOR);
        ClientPlayNetworking.registerGlobalReceiver(MagicSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> MagicClientPresentation.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(MagicScenePayload.TYPE, (payload, context) -> context.client().execute(() -> MagicShaderRenderer.accept(payload)));
        ClientPlayNetworking.registerGlobalReceiver(WorldVfxPayload.TYPE, (payload, context) -> context.client().execute(() -> MagicShaderRenderer.acceptWorld(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.teamlifebind.clientui.AuiClient.tick();
            MagicShaderRenderer.tick(client);
            boolean channel = client.getConnection() != null && ClientPlayNetworking.canSend(MagicInputPayload.TYPE);
            MagicClientPresentation.tick(client, channel, ClientPlayNetworking::send);
            while(HUD_EDITOR.consumeClick())if(client.player!=null&&client.gui.screen()==null)client.gui.setScreen(new com.teamlifebind.clientui.HudEditorScreen(null));
            boolean cast = false, wheel = false; while (CAST.consumeClick()) cast = true; while (WHEEL.consumeClick()) wheel = true; while (SCROLL.consumeClick()) { }
            if (!channel || !MagicClientPresentation.controls(client)) return;
            if (wheel && WHEEL.isDown()) client.gui.setScreen(new MagicWheelScreen()); else if (cast) MagicClientPresentation.cast();
        });
    }
    public static boolean scroll(double amount) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (amount == 0 || !SCROLL.isDown() || !MagicClientPresentation.controls(client) || !ClientPlayNetworking.canSend(MagicInputPayload.TYPE)) return false;
        MagicClientPresentation.scroll(amount); return true;
    }
}
