package com.teamlifebind.forge;

import com.teamlifebind.common.NativeHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Releasing the rebound wheel key commits selection. It never casts a spell. */
public final class MagicWheelScreen extends Screen {
    private int hover = MagicClientPresentation.selected();
    private boolean committed;
    public MagicWheelScreen() { super(Component.translatable("magic.teamlifebind.wheel")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hover = NativeHud.wheelHover(width, height, mouseX, mouseY, hover);
        NativeHud.wheel(new NativeHudCanvas(graphics, font), width, height, MagicClientPresentation.hudModel(), hover,
            title.getString(), NativeHudRenderer.text("wheel_hint", TeamMagicClient.WHEEL.getTranslatedKeyMessage(),
                TeamMagicClient.CAST.getTranslatedKeyMessage()));
    }
    private void commit() { if (!committed) { committed = true; MagicClientPresentation.select(hover); } minecraft.gui.setScreen(null); }
    @Override public boolean keyReleased(KeyEvent key) { if (TeamMagicClient.WHEEL.matches(key)) { commit(); return true; } return super.keyReleased(key); }
    @Override public boolean mouseReleased(MouseButtonEvent key) { if (TeamMagicClient.WHEEL.matchesMouse(key)) { commit(); return true; } return super.mouseReleased(key); }
    @Override public void tick() { if (!minecraft.isWindowActive() || !MagicClientPresentation.focus(minecraft) || minecraft.player == null || !minecraft.player.isAlive()) minecraft.gui.setScreen(null); }
    @Override public void onClose() { minecraft.gui.setScreen(null); }
}
