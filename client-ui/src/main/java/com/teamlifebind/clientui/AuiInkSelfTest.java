package com.teamlifebind.clientui;

import com.teamlifebind.aui.event.Event;
import com.teamlifebind.aui.init.Document;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongConsumer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Opt-in native GPU checks. The fixture clock is held while each frame is read back. */
final class AuiInkSelfTest {
    private final AuiMenuPage page;
    private final AuiInkTransition transition;
    private final Path output;
    private final List<String> evidence;
    private final LongConsumer advance;
    private int step, waiting;
    private volatile boolean captured, capturing;
    private volatile Throwable failure;
    private int[] transparent, oldUi, oldEndpoint, midUi, newUi, localUi, localSource, interrupted;
    private AuiInkTransition.Region localRegion;
    private int width, height;
    private static final int FRAMES = 17;

    AuiInkSelfTest(AuiMenuPage page, AuiInkTransition transition, Path output, List<String> evidence, LongConsumer advance) {
        this.page = page; this.transition = transition; this.output = output; this.evidence = evidence; this.advance = advance;
        begin();
    }
    boolean tick() {
        if (failure != null) throw new IllegalStateException("Liquid UI GPU regression", failure);
        if (capturing) {
            if (!captured) return false;
            capturing = captured = false; step++;
            if (step == FRAMES * 2 + 2) { verify(); transition.finish(); return true; }
            begin();
        }
        if (++waiting < 5) return false;
        capturing = true; captured = false;
        Path png = output.resolve("ink-" + step + ".png");
        try {
            int[] pixels = capture(AuiInkEffect.lastUiTarget(), png);
            if (step == 0) transparent = pixels;
            if (step == FRAMES - 1) oldUi = pixels;
            if (step == FRAMES) oldEndpoint = pixels;
            if (step == FRAMES + 10) midUi = pixels;
            if (step == FRAMES * 2 - 1) newUi = pixels;
            if (step == FRAMES * 2) {
                localUi = pixels; localSource = capture(AuiInkEffect.lastSourceTarget(), output.resolve("ink-source.png"));
            }
            if (step == FRAMES * 2 + 1) interrupted = pixels;
        } catch (Throwable error) { failure = error; }
        captured = true;
        return false;
    }
    private void begin() {
        waiting = 0;
        if (step == 0) transition.start(AuiInkTransition.Mode.OPEN, AuiInkTransition.Region.FULL, false, 1, 1000);
        else if (step == FRAMES) {
            var group = page.document().getElementById("group-rules"); group.dispatchEvent(new Event(group, "click", true));
            transition.start(AuiInkTransition.Mode.CONTENT, AuiInkTransition.Region.FULL, true, 1, 1000);
        } else if (step == FRAMES * 2) {
            var doc = page.document(); var bounds = doc.getElementById("cards").getBoundingClientRect(); var viewport = doc.getViewportSize();
            localRegion = new AuiInkTransition.Region((float)(bounds.left / viewport.width()), (float)(bounds.top / viewport.height()),
                (float)(bounds.right / viewport.width()), (float)(bounds.bottom / viewport.height()));
            var group = doc.getElementById("group-all"); group.dispatchEvent(new Event(group, "click", true));
            transition.start(AuiInkTransition.Mode.CONTENT, localRegion, true, -1, 1000);
            advance.accept(500_000_000L);
        } else if (step == FRAMES * 2 + 1) {
            var group = page.document().getElementById("group-match"); group.dispatchEvent(new Event(group, "click", true));
            transition.start(AuiInkTransition.Mode.CONTENT, AuiInkTransition.Region.FULL, false, -1, 1000);
        }
        if (step > 0 && step < FRAMES || step > FRAMES && step < FRAMES * 2) {
            advance.accept(1_000_000_000L / (FRAMES - 1));
        }
    }
    /** Vanilla Screenshot deliberately forces alpha=255; read the UI attachment directly. */
    private int[] capture(RenderTarget target, Path file) throws Exception {
        width = target.width; height = target.height;
        return readback(target, file);
    }
    static int[] readback(RenderTarget target, Path file) throws Exception {
        return readback(target, file, false);
    }
    /** The main framebuffer has meaningful RGB even where its alpha is zero. */
    static int[] readbackFrame(RenderTarget target, Path file) throws Exception {
        return readback(target, file, true);
    }
    private static int[] readback(RenderTarget target, Path file, boolean frame) throws Exception {
        int width = target.width, height = target.height;
        var device = RenderSystem.getDevice();
        int[] pixels = new int[width * height];
        try (var buffer = device.createBuffer(() -> "TLB transparent UI regression readback",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long)width * height * 4)) {
            var encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(target.getColorTexture(), buffer, 0, () -> {}, 0);
            try (var fence = encoder.createFence()) {
                encoder.submit(); require(fence.awaitCompletion(5_000_000_000L), "UI GPU readback timeout");
            }
            try (var mapping = buffer.map(true, false)) {
                var bytes = mapping.data();
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int i = ((height - 1 - y) * width + x) * 4;
                    int r = Byte.toUnsignedInt(bytes.get(i)), g = Byte.toUnsignedInt(bytes.get(i + 1));
                    int b = Byte.toUnsignedInt(bytes.get(i + 2)), a = Byte.toUnsignedInt(bytes.get(i + 3));
                    pixels[y * width + x] = a << 24 | r << 16 | g << 8 | b;
                }
            }
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        // UI attachments use premultiplied alpha; full frames follow Vanilla Screenshot's opaque output.
        // Keep the returned raw GPU data intact for assertions in both cases.
        int[] straight = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i], a = c >>> 24;
            straight[i] = frame ? 0xff000000 | c & 0xffffff : a == 0 ? 0 : a << 24 | Math.min(255, (c >> 16 & 255) * 255 / a) << 16
                | Math.min(255, (c >> 8 & 255) * 255 / a) << 8 | Math.min(255, (c & 255) * 255 / a);
        }
        image.setRGB(0, 0, width, height, straight, 0, width); ImageIO.write(image, "png", file.toFile());
        return pixels;
    }
    private void verify() {
        require(Arrays.stream(transparent).allMatch(p -> (p >>> 24) == 0), "Opening start is not transparent");
        long covered = Arrays.stream(oldUi).filter(p -> (p >>> 24) != 0).count();
        require(covered > 1000 && covered < oldUi.length * .94, "Settled UI hides the whole world");
        require(Arrays.equals(oldUi, oldEndpoint), "Old UI was not retained at the start of replacement");
        require(!Arrays.equals(oldUi, newUi), "UI replacement did not reach different content");
        require(Arrays.equals(localUi, interrupted), "Interrupted transition jumped back to stale UI");
        int oldPixels = 0, newPixels = 0, movingPixels = 0;
        for (int i = 0; i < midUi.length; i++) {
            if (oldUi[i] == newUi[i]) continue;
            if (midUi[i] == oldUi[i]) oldPixels++;
            else if (midUi[i] == newUi[i]) newPixels++;
            else movingPixels++;
        }
        require(oldPixels > 100 && newPixels > 100 && movingPixels > 100,
            "Replacement is not a spatial old/new contour transition: " + oldPixels + "/" + newPixels + "/" + movingPixels);
        int outside = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            float u = (x + .5F) / width, v = (y + .5F) / height;
            if (u <= localRegion.left() || u >= localRegion.right() || v <= localRegion.top() || v >= localRegion.bottom()) {
                int i = y * width + x;
                require(localUi[i] == localSource[i], "Local transition altered pixels outside its pane at " + x + "," + y);
                outside++;
            }
        }
        evidence.add("PASS UI_INK_GPU transparency, retained old frame, old/new contour mix, local pane stability, interrupted continuity; covered="
            + covered + " old=" + oldPixels + " new=" + newPixels + " contour=" + movingPixels + " outside=" + outside);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
