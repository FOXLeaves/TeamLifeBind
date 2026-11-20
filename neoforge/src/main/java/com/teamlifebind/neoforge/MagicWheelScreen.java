package com.teamlifebind.neoforge;

import com.teamlifebind.common.NativeHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Releasing the rebound wheel key commits selection. It never casts a spell. */
public final class MagicWheelScreen extends Screen implements com.teamlifebind.aui.screen.AuiLinkedScreen {
    private com.teamlifebind.clientui.AuiMagicPage page;
    private int hover = MagicClientPresentation.selected();
    private boolean committed;
    public MagicWheelScreen() { super(Component.translatable("magic.teamlifebind.wheel")); }
    @Override protected void init() { if (page == null) page = new com.teamlifebind.clientui.AuiMagicPage(); else page.resize(); }
    @Override public com.teamlifebind.aui.init.Document getLinkedDocument() { return page == null ? null : page.document(); }
    @Override public void removed() { if (page != null) { page.close(); page = null; } super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hover = page.render(graphics, mouseX, mouseY, MagicClientPresentation.hudModel(), hover,
            NativeHudRenderer.text("wheel_hint", TeamMagicClient.WHEEL.getTranslatedKeyMessage(), TeamMagicClient.CAST.getTranslatedKeyMessage()));
    }
    @Override public boolean keyPressed(KeyEvent key) {
        if (key.key() >= com.mojang.blaze3d.platform.InputConstants.KEY_1 && key.key() <= com.mojang.blaze3d.platform.InputConstants.KEY_3) { int next = key.key() - com.mojang.blaze3d.platform.InputConstants.KEY_1; if (MagicClientPresentation.hudModel().slots().get(next).learned()) hover = next; page.keyboardSelection(); return true; }
        if (key.key() == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT || key.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) { int step = key.key() == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT ? -1 : 1; for (int i=0;i<3;i++) { hover = Math.floorMod(hover+step,3); if (MagicClientPresentation.hudModel().slots().get(hover).learned()) break; } page.keyboardSelection(); return true; }
        if (key.isConfirmation()) { commit(); return true; }
        return super.keyPressed(key);
    }
    @Override public boolean mouseClicked(MouseButtonEvent key, boolean twice) { if (key.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) { int next = page.selectionAt(key.x(), key.y()); if (next >= 0) { hover = next; commit(); } return true; } return super.mouseClicked(key, twice); }
    private void commit() { if (!committed) { committed = true; MagicClientPresentation.select(hover); } minecraft.gui.setScreen(null); }
    @Override public boolean keyReleased(KeyEvent key) { if (TeamMagicClient.WHEEL.matches(key)) { commit(); return true; } return super.keyReleased(key); }
    @Override public boolean mouseReleased(MouseButtonEvent key) { if (TeamMagicClient.WHEEL.matchesMouse(key)) { commit(); return true; } return super.mouseReleased(key); }
    @Override public void tick() { if (!minecraft.isWindowActive() || !MagicClientPresentation.focus(minecraft) || minecraft.player == null || !minecraft.player.isAlive()) minecraft.gui.setScreen(null); }
    @Override public void onClose() { minecraft.gui.setScreen(null); }
}
