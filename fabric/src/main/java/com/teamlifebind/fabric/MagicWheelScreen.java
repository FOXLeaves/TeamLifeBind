package com.teamlifebind.fabric;

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
        int cx = width / 2, cy = height / 2;
        graphics.fill(0, 0, width, height, 0x96120F25);
        if (Math.hypot(mouseX - cx, mouseY - cy) > 15) hover = Math.floorMod((int) Math.floor((Math.atan2(mouseY - cy, mouseX - cx) + Math.PI / 2 + Math.PI / 3 + Math.PI * 2) / (Math.PI * 2 / 3)), 3);
        for (int slot = 0; slot < 3; slot++) {
            double angle = slot * Math.PI * 2 / 3 - Math.PI / 2;
            int x = cx + (int) (Math.cos(angle) * 78), y = cy + (int) (Math.sin(angle) * 67);
            var spell = MagicClientPresentation.at(slot);
            int color = spell == null ? 0xFF56616E : 0xFF000000 | TeamMagicRuntime.color(spell.school());
            graphics.fill(x - 67, y - 17, x + 67, y + 18, slot == hover ? 0xE5484062 : 0xBF211D35);
            graphics.fill(x - 67, y - 17, x + 67, y - 15, color);
            graphics.centeredText(font, (slot + 1) + " " + MagicClientPresentation.name(spell), x, y - 9, slot == hover ? 0xFFFFFFFF : color);
            if (spell != null) graphics.centeredText(font, Component.translatable("magic.teamlifebind.wheel.cost", spell.manaCost(), Math.round(MagicClientPresentation.cooldown(slot) / 20.0)), x, y + 4, 0xFFB9C3DD);
        }
        graphics.centeredText(font, title, cx, cy - 10, 0xFFEEE6FF);
        graphics.centeredText(font, Component.translatable("magic.teamlifebind.wheel.release"), cx, cy + 5, 0xFFB6A9CD);
    }
    private void commit() { if (!committed) { committed = true; MagicClientPresentation.select(hover); } minecraft.gui.setScreen(null); }
    @Override public boolean keyReleased(KeyEvent key) { if (TeamMagicClient.WHEEL.matches(key)) { commit(); return true; } return super.keyReleased(key); }
    @Override public boolean mouseReleased(MouseButtonEvent key) { if (TeamMagicClient.WHEEL.matchesMouse(key)) { commit(); return true; } return super.mouseReleased(key); }
    @Override public void tick() { if (!minecraft.isWindowActive() || !MagicClientPresentation.focus(minecraft) || minecraft.player == null || !minecraft.player.isAlive()) minecraft.gui.setScreen(null); }
    @Override public void onClose() { minecraft.gui.setScreen(null); }
}
