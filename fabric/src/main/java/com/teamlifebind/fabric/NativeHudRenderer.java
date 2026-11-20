package com.teamlifebind.fabric;

import com.teamlifebind.common.GunProfile;
import com.teamlifebind.common.NativeHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Purely visual: ammo and cast state remain owned and confirmed by the server. */
public final class NativeHudRenderer {
    private NativeHudRenderer() { }

    public static void render(GuiGraphicsExtractor graphics) {
        NativeSidebarHud.renderExpanded(graphics);
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui.hud.isHidden()
                || client.gui.screen() != null && !(client.gui.screen() instanceof com.teamlifebind.clientui.HudEditorScreen) || client.gui.overlay() != null
                || !client.player.isAlive() || client.player.isSpectator()) return;
        MagicClientPresentation.renderHud(graphics);
        var state = GunClientPresentation.hudState(client);
        if (state == null) return;
        boolean infinite = state.profile().usesHeat();
        boolean warning = state.overheated() || !infinite && state.ammo() <= Math.max(1, state.capacity() / 5);
        String status = !state.confirmed() ? text("gun.syncing") : state.overheated() ? text("gun.overheated")
            : state.reloadRemaining() > 0 ? NativeHud.seconds(state.reloadRemaining()) : text("ready");
        com.teamlifebind.clientui.ArchiveHud.weapon(graphics,
            new NativeHud.Weapon(Component.translatable("item.teamlifebind." + state.profile().id()).getString(),
                text(state.profile().fireMode() == GunProfile.FireMode.AUTO ? "gun.auto" : "gun.semi"), status,
                text("gun.reload", reloadBinding(client)), text("gun.reserve"), text("gun.heat"),
                state.ammo(), state.capacity(), state.reloadRemaining(), state.reloadTotal(), state.heat(),
                infinite, warning, state.confirmed()));
    }

    private static String reloadBinding(Minecraft client) {
        for (var mapping : client.options.keyMappings) {
            if (mapping.getName().equals("key.teamlifebind.reload")) return mapping.getTranslatedKeyMessage().getString();
        }
        return "?";
    }

    static String text(String key, Object... arguments) {
        return Component.translatable("hud.teamlifebind." + key, arguments).getString();
    }
}
