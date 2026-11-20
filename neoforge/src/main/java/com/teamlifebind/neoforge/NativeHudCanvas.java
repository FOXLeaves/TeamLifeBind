package com.teamlifebind.neoforge;

import com.teamlifebind.common.NativeHud;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Client-only adapter. All geometry stays in scaled GUI coordinates. */
final class NativeHudCanvas implements NativeHud.Canvas {
    private final GuiGraphicsExtractor graphics;
    private final Font font;

    NativeHudCanvas(GuiGraphicsExtractor graphics, Font font) { this.graphics = graphics; this.font = font; }
    @Override public int width(String text) { return font.width(text); }
    @Override public void rect(int x, int y, int width, int height, int color) {
        color = com.teamlifebind.clientui.ArchiveTheme.hudColor(color);
        if (width > 0 && height > 0) graphics.fill(x, y, x + width, y + height, color);
    }
    @Override public void text(String text, int x, int y, int color, float scale) {
        if (text.isEmpty()) return;
        color = com.teamlifebind.clientui.ArchiveTheme.hudColor(color);
        if (scale == 1) graphics.text(font, text, x, y, color, false);
        else {
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(x, y).scale(scale);
                graphics.text(font, text, 0, 0, color, false);
            } finally { graphics.pose().popMatrix(); }
        }
    }
}
