package com.teamlifebind.clientui;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.system.MemoryStack;

/** GPU-only composition of old/new transparent UI. Never reads a world attachment. */
public final class AuiInkEffect implements AutoCloseable {
    private static final java.util.Set<AuiInkEffect> INSTANCES = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private TextureTarget history, output;
    private PipelineCache cache;
    private RenderPipeline pipeline;
    private GpuBuffer triangle;
    private long serial = -1;
    private boolean historyValid, outputWasVisible;
    private static RenderTarget presentedUi;
    private static AuiInkEffect presentedOwner;
    private static TextureTarget navigationHistory;
    private static long navigationSerial = -1;
    private static volatile RenderTarget lastUiTarget;
    private static volatile RenderTarget lastSourceTarget;
    private static volatile AuiInkTransition.Frame lastFrame;
    private static volatile int lastEffectId;
    private static volatile RenderTarget lastHistoryTarget;
    public static RenderTarget lastUiTarget() { return lastUiTarget; }
    static RenderTarget lastSourceTarget() { return lastSourceTarget; }
    static AuiInkTransition.Frame lastFrame() { return lastFrame; }
    static int lastEffectId() { return lastEffectId; }
    static RenderTarget lastHistoryTarget() { return lastHistoryTarget; }
    public AuiInkEffect() { INSTANCES.add(this); }
    /** Freeze before the old screen/renderer is disposed, including during a liquid interruption. */
    static void retainBeforeNavigation(AuiInkTransition.Frame frame) {
        if (!frame.effect() || presentedUi == null || presentedUi.getColorTexture() == null) return;
        if (navigationHistory == null)
            navigationHistory = new TextureTarget("TLB outgoing screen UI", presentedUi.width, presentedUi.height, GpuFormat.RGBA8_UNORM, null);
        else if (navigationHistory.width != presentedUi.width || navigationHistory.height != presentedUi.height)
            navigationHistory.resize(presentedUi.width, presentedUi.height);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
            presentedUi.getColorTexture(), navigationHistory.getColorTexture(), 0, 0, 0, 0, 0, presentedUi.width, presentedUi.height);
        navigationSerial = frame.serial();
    }
    private static void releaseNavigationHistory() {
        if (navigationHistory != null) navigationHistory.destroyBuffers();
        navigationHistory = null; navigationSerial = -1;
    }
    static void releaseIdle() {
        if (!AuiScreenTransitions.active()) releaseNavigationHistory();
        if (!AuiScreenTransitions.active()&&!AuiMenuPage.hasActivePage()&&!ArchivePresenter.hasActivePage()) for (var effect : java.util.List.copyOf(INSTANCES)) effect.releaseResources();
    }

    /** Called before the PIP attachment is cleared, so no idle-frame copy is needed. */
    public void captureBeforeDraw(TextureTarget source, AuiInkTransition.Frame frame) {
        if (!frame.effect()) { outputWasVisible = false; return; }
        ensure(source.width, source.height);
        if (frame.serial() == serial) return;
        if (frame.mode() == AuiInkTransition.Mode.OPEN) historyValid = false;
        else {
            boolean retained = navigationHistory != null && navigationSerial == frame.serial()
                && navigationHistory.width == source.width && navigationHistory.height == source.height;
            RenderTarget previous = retained ? navigationHistory : outputWasVisible ? output : source;
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                previous.getColorTexture(), history.getColorTexture(), 0, 0, 0, 0, 0, source.width, source.height);
            historyValid = true;
        }
        serial = frame.serial();
        if (recording()) lastHistoryTarget = history;
    }
    public GpuTextureView apply(TextureTarget source, AuiInkTransition.Frame frame) {
        if (recording()) { lastSourceTarget = source; lastFrame = frame; lastEffectId = System.identityHashCode(this); }
        if (!frame.effect()) {
            if (recording()) lastUiTarget = source;
            presentedUi = source; presentedOwner = this;
            outputWasVisible = false; return source.getColorTextureView();
        }
        ensure(source.width, source.height);
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var stack = MemoryStack.stackPush()) {
            var values = stack.malloc(48);
            values.putFloat(source.width).putFloat(source.height).putFloat(frame.progress()).putFloat(frame.progress() * 1.3F);
            var rect = frame.region();
            values.putFloat(rect.left()).putFloat(rect.top()).putFloat(rect.right()).putFloat(rect.bottom());
            values.putFloat(frame.axis()).putFloat(frame.direction()).putFloat(frame.mode().ordinal()).putFloat(historyValid ? 1 : 0).flip();
            var uniform = encoder.transientMemory().uploadGpu(values, 256, GpuBuffer.USAGE_UNIFORM);
            try (var pass = encoder.createRenderPass(() -> "TLB transparent liquid UI transition", output.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(cache.get(pipeline));
                pass.setUniform("InkFrame", uniform);
                var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                pass.setUniform("PresentUi", source.getColorTextureView(), sampler);
                pass.setUniform("PreviousUi", historyValid ? history.getColorTextureView() : source.getColorTextureView(), sampler);
                pass.setVertexBuffer(0, triangle.slice()); pass.draw(3, 1, 0, 0);
            }
        }
        outputWasVisible = true;
        presentedUi = output; presentedOwner = this;
        if (recording()) lastUiTarget = output;
        return output.getColorTextureView();
    }
    private static boolean recording() {
        return Boolean.getBoolean("teamlifebind.aui.selftest") || Boolean.getBoolean("teamlifebind.archive.selftest");
    }
    private void ensure(int width, int height) {
        if (history == null) {
            history = new TextureTarget("TLB previous UI", width, height, GpuFormat.RGBA8_UNORM, null);
            output = new TextureTarget("TLB liquid UI", width, height, GpuFormat.RGBA8_UNORM, null);
        } else if (history.width != width || history.height != height) {
            history.resize(width, height); output.resize(width, height); historyValid = outputWasVisible = false;
        }
        if (cache != null) return;
        cache = new PipelineCache(RenderSystem.getDevice(), new ResourceShaders(Minecraft.getInstance().getResourceManager()));
        pipeline = RenderPipeline.builder().withLocation(id("pipeline/menu_ink"))
            .withVertexShader(id("core/menu_ink")).withFragmentShader(id("core/menu_ink"))
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("InkFrame", UniformType.UNIFORM_BUFFER)
                .withUniform("PresentUi", UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("PreviousUi", UniformType.COMBINED_IMAGE_SAMPLER).build())
            .withVertexBinding(0, DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL)).build();
        if (cache.get(pipeline) == null) throw new IllegalStateException("TLB liquid UI shader did not compile");
        try (var stack = MemoryStack.stackPush()) {
            var vertices = stack.malloc(36);
            vertices.putFloat(-1).putFloat(-1).putFloat(0).putFloat(3).putFloat(-1).putFloat(0)
                .putFloat(-1).putFloat(3).putFloat(0).flip();
            triangle = RenderSystem.getDevice().createBuffer(() -> "TLB UI screen triangle", GpuBuffer.USAGE_VERTEX, vertices);
        }
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("tlb_aui", path); }
    @Override public void close() {
        if (presentedOwner == this) { presentedUi = null; presentedOwner = null; }
        INSTANCES.remove(this); releaseResources();
        if (INSTANCES.isEmpty() && !AuiScreenTransitions.active()) releaseNavigationHistory();
    }
    private void releaseResources() {
        if (presentedOwner == this && (presentedUi == history || presentedUi == output)) { presentedUi = null; presentedOwner = null; }
        if (history != null) history.destroyBuffers(); if (output != null) output.destroyBuffers();
        if (triangle != null) triangle.close(); if (cache != null) cache.close();
        if (lastUiTarget == history || lastUiTarget == output) lastUiTarget = null;
        history = output = null; triangle = null; cache = null; pipeline = null;
        historyValid = outputWasVisible = false; serial = -1;
    }
    private record ResourceShaders(ResourceManager resources) implements ShaderSource {
        @Override public String getShader(Identifier location, ShaderType type) {
            try (var reader = resources.openAsReader(type.idConverter().idToFile(location))) {
                StringBuilder text = new StringBuilder(); char[] data = new char[4096];
                for (int n; (n = reader.read(data)) >= 0;) text.append(data, 0, n);
                return text.toString();
            } catch (IOException failure) { throw new UncheckedIOException("Cannot read UI shader " + location, failure); }
        }
        @Override public CachedIncludeSource getInclude(Identifier location) { return CachedIncludeSource.createError("Self-contained UI shader: " + location); }
        @Override public void close() {}
    }
}
