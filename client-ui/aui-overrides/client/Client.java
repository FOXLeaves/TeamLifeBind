package com.sighs.apricityui.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.dev.ResourceManager;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.task.FrameScheduler;
import com.sighs.apricityui.task.MouseMoveEngine;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.render.DocumentLayerOrder;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLScancode;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;

import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.world.WorldWindow;

public class Client {
    private static int lastWindowWidth = -1;
    private static int lastWindowHeight = -1;
    private static int lastFramebufferWidth = -1;
    private static int lastFramebufferHeight = -1;
    private static double lastGuiScale = -1.0d;

    /** Ticked by the host client; no independent networking or global input capture. */
    public static void tick() {
        FrameScheduler.tick();
        com.sighs.apricityui.task.MouseMoveEngine.poll(Client::getMousePosition);
        Window window = Minecraft.getInstance().getWindow();
        int w = window.getWidth(), h = window.getHeight();
        double scale = window.getGuiScale();
        if (w != lastFramebufferWidth || h != lastFramebufferHeight || scale != lastGuiScale) {
            lastFramebufferWidth = w; lastFramebufferHeight = h; lastGuiScale = scale;
            for (Document document : Document.getAll()) {
                if (!document.isDisposed()) document.applyViewport(true);
            }
        }
    }

    public static Position getMousePosition() {
        Minecraft mc = Minecraft.getInstance();
        MouseHandler mouseHandler = mc.mouseHandler;
        Window window = mc.getWindow();

        return MouseCoordinates.toGui(mouseHandler.xpos(), mouseHandler.ypos(),
                window.getScreenWidth(), window.getScreenHeight(),
                window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    /**
     * Returns the pointer position represented by the screen for world-window picking.
     * A grabbed (pointer-locked) cursor has virtual coordinates that track look
     * movement, while the visible crosshair remains fixed at the center of the
     * GUI viewport.
     */
    public static Position getMousePositionForWorldInteraction() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (mc.mouseHandler.isMouseGrabbed()) {
            return new Position(
                    window.getGuiScaledWidth() * 0.5d,
                    window.getGuiScaledHeight() * 0.5d
            );
        }
        return getMousePosition();
    }

    /**
     * Returns the live cursor position directly from SDL, bypassing the cached
     * {@link MouseHandler} position.
     *
     * <p>26.3 has no GLFW: {@code SDL_GetMouseState} reports the pointer in the
     * focused window's coordinates (the same space {@code MouseHandler} caches),
     * and the window handle is an {@code SDL_Window*} — it is only checked for
     * "is there a window at all".</p>
     */
    public static Position getMousePositionDirectly() {
        Window window = Minecraft.getInstance().getWindow();
        if (window == null || window.handle() == 0L) return null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xBuf = stack.mallocFloat(1);
            FloatBuffer yBuf = stack.mallocFloat(1);
            SDLMouse.SDL_GetMouseState(xBuf, yBuf);
            return MouseCoordinates.toGui(xBuf.get(0), yBuf.get(0),
                    window.getScreenWidth(), window.getScreenHeight(),
                    window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
    }

    /**
     * Polls the physical state of a {@code key.keyboard.*} / {@code key.mouse.*}
     * binding name.
     *
     * <p>26.3's {@link InputConstants#getKey(String)} owns the name table (the
     * old hand-written {@code KEY_MAP} duplicated it), and
     * {@code PhysicalKeyState} reads SDL's keyboard/mouse state in the same
     * scancode/1-based-button space, so no per-key table is needed here.</p>
     */
    public static boolean isKeyPressed(String keyName) {
        if (keyName == null || keyName.isEmpty()) {
            return false;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Window window = minecraft.getWindow();
            if (window == null || window.handle() == 0L) return false;
            return com.sighs.apricityui.spi.PhysicalKeyState.isDown(InputConstants.getKey(keyName));
        } catch (RuntimeException | LinkageError e) {
            // Unknown names throw IllegalArgumentException; SDL state reads are
            // only safe on the main thread.
            return false;
        }
    }

    public static Window getWindow() {
        return Minecraft.getInstance().getWindow();
    }

    public static Size getWindowSize() {
        Window window = Minecraft.getInstance().getWindow();
        return new Size(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public static int getDefaultFontWidth(String text) {
        return getDefaultFontWidth(text, false, false, 0);
    }

    public static int getDefaultFontWidth(String text, boolean bold) {
        return getDefaultFontWidth(text, bold, false, 0);
    }

    public static int getDefaultFontWidth(String text, boolean bold, boolean oblique) {
        return getDefaultFontWidth(text, bold, oblique, 0);
    }

    public static int getDefaultFontWidth(String text, boolean bold, boolean oblique, double strokeWidth) {
        double stroke = Math.max(0, strokeWidth) * 2;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            int fontStyle = java.awt.Font.PLAIN;
            if (bold) fontStyle |= java.awt.Font.BOLD;
            if (oblique) fontStyle |= java.awt.Font.ITALIC;
            java.awt.Font fallbackFont = new java.awt.Font("Microsoft YaHei", fontStyle, 16);
            int width = new java.awt.Canvas().getFontMetrics(fallbackFont).stringWidth(text == null ? "" : text);
            return (int) Math.ceil(width + stroke);
        }
        if (!bold && !oblique) return (int) Math.ceil(minecraft.font.width(text) + stroke);
        MutableComponent renderText = Component.literal(text);
        if (bold) renderText = renderText.withStyle(ChatFormatting.BOLD);
        if (oblique) renderText = renderText.withStyle(ChatFormatting.ITALIC);
        return (int) Math.ceil(minecraft.font.width(renderText) + stroke);
    }

    /** Draws native glyphs immediately, retaining the current target, clip and painter order. */
    public static void drawDefaultFont(PoseStack poseStack, Text text, String content, Position position) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) return;
        com.sighs.apricityui.neoforge.ItemRenderService.submitImmediately(
                com.sighs.apricityui.render.OutputTargets.currentTarget(),
                immediate -> submitDefaultFont(immediate, poseStack, text, content, position));
    }

    private static void submitDefaultFont(SubmitNodeCollector collector, PoseStack poseStack,
                                          Text text, String content, Position position) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        try {
            poseStack.translate(position.x, position.y, 0);
            // 默认字体也要保留 z 轴缩放，避免在容器 Screen 中把文本深度压扁后被后续菜单/物品绘制覆盖。
            float scale = (float) text.defaultFontScale();
            poseStack.scale(scale, scale, 1f);
            MutableComponent renderText = Component.literal(content == null ? "" : content);
            if (text.isBold()) renderText = renderText.withStyle(ChatFormatting.BOLD);
            if (text.isOblique()) renderText = renderText.withStyle(ChatFormatting.ITALIC);
            if (text.isUnderlined()) renderText = renderText.withStyle(ChatFormatting.UNDERLINE);
            if (text.isStrikethrough()) renderText = renderText.withStyle(ChatFormatting.STRIKETHROUGH);
            FormattedCharSequence sequence = renderText.getVisualOrderText();
            int stroke = Math.max(0, (int) Math.ceil(text.strokeWidth));
            if (stroke > 0) {
                int strokeColor = text.strokeColor.getValue();
                for (int ox = -stroke; ox <= stroke; ox++) {
                    for (int oy = -stroke; oy <= stroke; oy++) {
                        if (ox == 0 && oy == 0) continue;
                        if (ox * ox + oy * oy > stroke * stroke) continue;
                        com.sighs.apricityui.neoforge.FeatureRenderBridge.submitText(
                                collector, poseStack, font, sequence, ox, oy, strokeColor, false);
                    }
                }
            }
            com.sighs.apricityui.neoforge.FeatureRenderBridge.submitText(
                    collector, poseStack, font, sequence, 0, 0, text.color.getValue(), false);
        } finally {
            poseStack.popPose();
        }
    }

    public static void drawDefaultFont(PoseStack poseStack, Text text, Position position) {
        drawDefaultFont(poseStack, text, text.content, position);
    }

    /**
     * Draws the frame-timing HUD overlay, if enabled.
     */
    public static void drawFrameTimingHud(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics) {
        if (guiGraphics == null || !com.sighs.apricityui.render.FrameTimingHud.isEnabled()) return;
        String text = com.sighs.apricityui.render.FrameTimingHud.frameStatsText();
        if (text == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.font.width(text) + 8;
        guiGraphics.pose().pushMatrix();
        guiGraphics.fill(2, 2, 2 + width, 16, 0xCC000000);
        guiGraphics.text(minecraft.font, text, 6, 6, 0xFF00FF66, false);
        guiGraphics.pose().popMatrix();
    }

}
