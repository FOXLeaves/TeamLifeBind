package com.teamlifebind.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;

public final class TeamMagicClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("teamlifebind", "magic"));
    static final KeyMapping CAST = new KeyMapping("key.teamlifebind.magic_cast", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY);
    static final KeyMapping WHEEL = new KeyMapping("key.teamlifebind.magic_wheel", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY);
    static final KeyMapping SCROLL = new KeyMapping("key.teamlifebind.magic_scroll", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CATEGORY);
    static final KeyMapping HUD_EDITOR = new KeyMapping("key.teamlifebind.hud_editor", InputConstants.Type.KEYBOARD, InputConstants.KEY_F8, CATEGORY);
    private TeamMagicClient() {}
    static void register() {
        net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent.BUS.addListener(event -> event.register(new com.teamlifebind.aui.client.gui.pip.ApricityUiPipRenderer()));
        net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent.BUS.addListener(event -> event.register(new com.teamlifebind.clientui.ArchiveLoadingRenderer()));
        net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent.BUS.addListener(event -> event.register(new com.teamlifebind.clientui.ArchiveBackdropRenderer()));
        net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent.BUS.addListener(event -> event.register(new com.teamlifebind.clientui.MagicSigilRenderer()));
        TeamMagicNetwork.registerClientSync(MagicClientPresentation::receive);
        TeamMagicNetwork.registerClientScene(MagicShaderRenderer::accept);
        WorldVfxNetwork.registerClient(MagicShaderRenderer::acceptWorld);
        RegisterKeyMappingsEvent.BUS.addListener(event -> { event.register(CAST); event.register(WHEEL); event.register(SCROLL); event.register(HUD_EDITOR); });
        TickEvent.ClientTickEvent.Post.BUS.addListener(TeamMagicClient::tick);
    }
    private static void tick(TickEvent.ClientTickEvent.Post event) {
        com.teamlifebind.clientui.AuiClient.tick();
        Minecraft client = Minecraft.getInstance(); MagicShaderRenderer.tick(client); boolean channel = client.getConnection() != null && TeamMagicNetwork.canSendInput(client.getConnection().getConnection());
        MagicClientPresentation.tick(client, channel, payload -> TeamMagicNetwork.CHANNEL.send(payload, PacketDistributor.SERVER.noArg()));
        while(HUD_EDITOR.consumeClick())if(client.player!=null&&client.gui.screen()==null)client.gui.setScreen(new com.teamlifebind.clientui.HudEditorScreen(null));
        boolean cast = false, wheel = false; while (CAST.consumeClick()) cast = true; while (WHEEL.consumeClick()) wheel = true; while (SCROLL.consumeClick()) { }
        if (!channel || !MagicClientPresentation.controls(client)) return;
        if (wheel && WHEEL.isDown()) client.gui.setScreen(new MagicWheelScreen()); else if (cast) MagicClientPresentation.cast();
    }
    public static boolean scroll(double amount) {
        Minecraft client = Minecraft.getInstance();
        if (amount == 0 || !SCROLL.isDown() || !MagicClientPresentation.controls(client) || client.getConnection() == null || !TeamMagicNetwork.canSendInput(client.getConnection().getConnection())) return false;
        MagicClientPresentation.scroll(amount); return true;
    }
}
