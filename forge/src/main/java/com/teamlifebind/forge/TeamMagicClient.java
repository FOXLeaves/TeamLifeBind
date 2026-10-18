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
    private TeamMagicClient() {}
    static void register() {
        TeamMagicNetwork.registerClientSync(MagicClientPresentation::receive);
        TeamMagicNetwork.registerClientScene(MagicShaderRenderer::accept);
        RegisterKeyMappingsEvent.BUS.addListener(event -> { event.register(CAST); event.register(WHEEL); event.register(SCROLL); });
        TickEvent.ClientTickEvent.Post.BUS.addListener(TeamMagicClient::tick);
    }
    private static void tick(TickEvent.ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance(); MagicShaderRenderer.tick(client); boolean channel = client.getConnection() != null && TeamMagicNetwork.canSendInput(client.getConnection().getConnection());
        MagicClientPresentation.tick(client, channel, payload -> TeamMagicNetwork.CHANNEL.send(payload, PacketDistributor.SERVER.noArg()));
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
