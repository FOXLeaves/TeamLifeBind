package com.teamlifebind.fabric;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.teamlifebind.common.SpellCatalogue;
import com.teamlifebind.common.SpellDefinition;
import com.teamlifebind.common.MagicMechanic;
import com.teamlifebind.common.SpellGeometry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/** World-space SDF volumes. This client renderer never creates Minecraft particles. */
public final class MagicShaderRenderer {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("TeamLifeBind/MagicShaders");
    private static final int MAX_SCENES = 24;
    private static final int MAX_DRAWS = 8;
    private static final int FRAME_BYTES = 192;
    private static final int MAX_EFFECT_PIXELS = 1_382_400;
    private static final int[] SCHOOL_COLORS = {0xFF6235, 0x43B9FF, 0xC0F9FF, 0x95FFDE, 0xDDBA76, 0xCFA2FF, 0xFFF1A9,
        0x874CDD, 0xF57AFF, 0x69E66B, 0x8D79FF, 0x68FFF0, 0xDF385B, 0x91CDF5};
    private static final Map<Long, Scene> SCENES = new LinkedHashMap<>();
    private static final Matrix4f EXACT_PROJECTION = new Matrix4f();
    private static boolean hasProjection;
    private static Object currentLevel;
    private static UUID currentCamera;
    private static volatile boolean releaseRequested;
    private static boolean failed;
    private static PipelineCache cache;
    private static RenderPipeline volumePipeline;
    private static RenderPipeline compositePipeline;
    private static GpuBuffer triangle;
    private static GpuTexture depthCopy;
    private static GpuTextureView depthView;
    private static TextureTarget effectTarget;
    private static GpuFormat outputFormat;

    private MagicShaderRenderer() { }

    /** Called on the client packet executor; the packet carries bounded server-owned geometry. */
    public static synchronized void accept(MagicScenePayload packet) {
        Minecraft client = Minecraft.getInstance();
        if (packet.operation() == MagicScenePayload.CLEAR_CASTER) { SCENES.values().removeIf(scene -> scene.caster().equals(packet.caster())); return; }
        if (packet.operation() == MagicScenePayload.REMOVE_RUN) {
            long group = packet.sceneId() >>> 8;
            SCENES.values().removeIf(scene -> scene.caster().equals(packet.caster()) && (scene.id() >>> 8) == group);
            return;
        }
        if (packet.operation() == MagicScenePayload.REMOVE) { SCENES.remove(packet.sceneId()); return; }
        if (client.level == null || !client.level.dimension().identifier().equals(packet.dimension())) return;
        SpellDefinition spell = SpellCatalogue.byId(packet.spellId());
        if (spell == null || packet.phase() < 0 || packet.phase() > 3 || packet.mechanic() < 0 || packet.mechanic() > 19) return;
        if (currentLevel != null && currentLevel != client.level) clear();
        currentLevel = client.level;
        Scene scene = new Scene(packet.sceneId(), packet.caster(), spell, packet.phase(), packet.mechanic(),
            new Vec3(packet.originX(), packet.originY(), packet.originZ()), new Vec3(packet.endX(), packet.endY(), packet.endZ()),
            Math.clamp(packet.radius(), 0.15F, 32F), Math.clamp(packet.ageTicks(), 0, 600),
            Math.clamp(packet.durationTicks(), 1, 600), client.level.getGameTime());
        SCENES.remove(packet.sceneId());
        if (SCENES.size() == MAX_SCENES) SCENES.remove(SCENES.keySet().iterator().next());
        SCENES.put(packet.sceneId(), scene);
    }

    /** Tick owns dimension/camera cleanup; animation uses extracted world time, respecting pause. */
    public static synchronized void tick(Minecraft client) {
        UUID camera = client.getCameraEntity() == null ? null : client.getCameraEntity().getUUID();
        if (client.level == null || (currentLevel != null && currentLevel != client.level)
            || (currentCamera != null && !currentCamera.equals(camera))) {
            clear();
            releaseRequested = true;
        }
        currentLevel = client.level;
        currentCamera = camera;
        if (client.level != null) {
            long now = client.level.getGameTime();
            SCENES.values().removeIf(scene -> now - scene.receivedTick() > 20
                || scene.ageTicks() + now - scene.receivedTick() > scene.durationTicks() + 6);
        }
    }

    public static synchronized void clear() { SCENES.clear(); hasProjection = false; }

    /** Receives the exact bob/nausea-adjusted projection used to render world depth. */
    public static void projection(Matrix4f projection) { EXACT_PROJECTION.set(projection); hasProjection = true; }

    /** Reload and world switches release resources on the render thread, never the packet thread. */
    public static void requestReload() { clear(); releaseRequested = true; failed = false; }

    public static void close() {
        clear();
        if (RenderSystem.isOnRenderThread()) release(); else releaseRequested = true;
    }

    /** Invoked directly after LevelRenderer.render, before hand rendering replaces world depth. */
    public static void render(GameRenderer renderer) {
        RenderSystem.assertOnRenderThread();
        if (releaseRequested) { release(); releaseRequested = false; }
        List<Scene> scenes;
        synchronized (MagicShaderRenderer.class) { scenes = new ArrayList<>(SCENES.values()); }
        if (failed || scenes.isEmpty() || !hasProjection) return;
        var level = renderer.gameRenderState().levelRenderState;
        CameraRenderState camera = level.cameraRenderState;
        RenderTarget main = renderer.mainRenderTarget();
        if (camera == null || !camera.initialized || main.width < 1 || main.height < 1
            || main.getDepthTexture() == null || main.getColorTextureView() == null) return;
        Matrix4f viewProjection = new Matrix4f(EXACT_PROJECTION).mul(camera.viewRotationMatrix);
        Matrix4f inverse = new Matrix4f(viewProjection).invert();
        if (!Float.isFinite(inverse.m00())) return;
        double now = level.gameTime + level.worldPartialTicks;
        // Resolve attached geometry before culling, sorting, scissor bounds and uniform upload.
        // Network packets remain sparse; each frame uses the same interpolation as its player.
        scenes.replaceAll(scene -> followCaster(scene, camera));
        scenes.removeIf(scene -> now - scene.receivedTick() > 20 || scene.age(now) > scene.durationTicks() + 6
            || scene.center().distanceToSqr(camera.pos) > Math.pow(112 + scene.extent(), 2)
            || (camera.cullFrustum != null && !camera.cullFrustum.isVisible(scene.bounds())));
        scenes.sort(Comparator.comparingDouble(scene -> scene.center().distanceToSqr(camera.pos)));
        if (scenes.isEmpty()) return;
        try {
            ensureResources(main);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(main.getDepthTexture(), depthCopy, 0, 0, 0, 0, 0, main.width, main.height);
            encoder.clearColorTexture(effectTarget.getColorTexture(), new Vector4f());
            int fps = Minecraft.getInstance().getFps();
            int limit = fps > 0 && fps < 24 ? 2 : fps > 0 && fps < 40 ? 4 : MAX_DRAWS;
            int quality = fps > 0 && fps < 24 ? 16 : fps > 0 && fps < 45 ? 24 : 32;
            long pixelBudget = Math.max(MAX_EFFECT_PIXELS, (long) effectTarget.width * effectTarget.height);
            long usedPixels = 0;
            int drawn = 0;
            List<Draw> draws = new ArrayList<>();
            for (Scene scene : scenes) {
                if (drawn >= limit) break;
                Rectangle rect = screenBounds(scene, camera.pos, viewProjection, effectTarget.width, effectTarget.height);
                if (rect == null) continue;
                long pixels = (long) rect.width() * rect.height();
                if (drawn > 0 && usedPixels + pixels > pixelBudget) continue;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var profile = scene.spell().behaviour().visual();
                    int rgb = SCHOOL_COLORS[scene.spell().school().ordinal()];
                    Vec3 center = scene.center().subtract(camera.pos);
                    Vec3 axis = scene.end().subtract(scene.origin());
                    double length = Math.min(96, axis.length());
                    axis = length > 0.0001 ? axis.normalize() : new Vec3(0, 1, 0);
                    var frame = Std140Builder.onStack(stack, FRAME_BYTES).putMat4f(inverse)
                        .putVec4(effectTarget.width, effectTarget.height, (float) (now / 20.0), RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1 : 0)
                        .putVec4((float) center.x, (float) center.y, (float) center.z, scene.radius())
                        .putVec4((float) axis.x, (float) axis.y, (float) axis.z, scene.segment() ? (float) (length * 0.5) : scene.radius())
                        .putVec4(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, scene.spell().tier())
                        .putVec4((float) (scene.age(now) / 20.0), scene.durationTicks() / 20F, scene.phase(), scene.mechanic())
                        .putVec4(profile.cast().ordinal(), profile.travel().ordinal(), profile.impact().ordinal(), profile.finish().ordinal())
                        .putVec4(profile.motion().ordinal(), SpellCatalogue.all().indexOf(scene.spell()),
                            scene.spell().superTier() ? 1.5F : 0.65F + scene.spell().tier() * 0.09F,
                            quality == 32 && pixels <= ((long) effectTarget.width * effectTarget.height) / 4 ? 48 : quality)
                        .putVec4(scene.spell().school().ordinal(), scene.mechanic(), SpellCatalogue.all().indexOf(scene.spell()), 0).get();
                    // Transient memory owns the slice until GPU completion; no overwritten UBO or per-frame GPU leaks.
                    var uniform = encoder.transientMemory().uploadGpu(frame, 256, GpuBuffer.USAGE_UNIFORM);
                    draws.add(new Draw(uniform, rect));
                }
                usedPixels += pixels;
                drawn++;
            }
            if (drawn > 0) {
                try (var pass = encoder.createRenderPass(() -> "TeamLifeBind magic world volumes", effectTarget.getColorTextureView(), Optional.empty())) {
                    pass.setPipeline(cache.get(volumePipeline));
                    pass.setUniform("SceneDepth", depthView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                    pass.setVertexBuffer(0, triangle.slice());
                    for (Draw draw : draws) {
                        Rectangle rect = draw.rectangle();
                        pass.setUniform("MagicFrame", draw.uniform());
                        pass.enableScissor(rect.x(), rect.y(), rect.width(), rect.height());
                        // RenderPearl: vertexCount, instanceCount, firstVertex, firstInstance.
                        pass.draw(3, 1, 0, 0);
                    }
                    pass.disableScissor();
                }
                try (var pass = encoder.createRenderPass(() -> "TeamLifeBind magic bloom composite", main.getColorTextureView(), Optional.empty())) {
                    pass.setPipeline(cache.get(compositePipeline));
                    pass.setUniform("MagicFrame", draws.getLast().uniform());
                    pass.setUniform("MagicColor", effectTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                    pass.setUniform("SceneDepth", depthView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                    pass.setVertexBuffer(0, triangle.slice());
                    pass.draw(3, 1, 0, 0);
                }
            }
            encoder.submit();
        } catch (RuntimeException exception) {
            failed = true;
            release();
            LOGGER.error("Magic shader rendering failed; resource reload will retry the pipeline", exception);
        }
    }

    private static void ensureResources(RenderTarget main) {
        var device = RenderSystem.getDevice();
        GpuFormat color = main.getColorTexture().getFormat();
        if (cache == null || outputFormat != color) {
            release();
            outputFormat = color;
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            cache = new PipelineCache(device, new ResourceShaders(resources));
            var layout = BindGroupLayout.builder().withUniform("MagicFrame", UniformType.UNIFORM_BUFFER)
                .withUniform("SceneDepth", UniformType.COMBINED_IMAGE_SAMPLER).build();
            volumePipeline = RenderPipeline.builder().withLocation(id("pipeline/magic_volume"))
                .withVertexShader(id("core/magic_volume")).withFragmentShader(id("core/magic_volume"))
                .withBindGroupLayout(layout).withVertexBinding(0, DefaultVertexFormat.POSITION)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.ADDITIVE), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL)).build();
            compositePipeline = RenderPipeline.builder().withLocation(id("pipeline/magic_composite"))
                .withVertexShader(id("core/magic_composite")).withFragmentShader(id("core/magic_composite"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MagicFrame", UniformType.UNIFORM_BUFFER).withUniform("MagicColor", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("SceneDepth", UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withVertexBinding(0, DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.ADDITIVE), color, ColorTargetState.WRITE_COLOR)).build();
            if (cache.get(volumePipeline) == null || cache.get(compositePipeline) == null) throw new IllegalStateException("Magic pipeline did not compile");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var vertices = stack.malloc(36);
                vertices.putFloat(-1).putFloat(-1).putFloat(0).putFloat(3).putFloat(-1).putFloat(0)
                    .putFloat(-1).putFloat(3).putFloat(0).flip();
                triangle = device.createBuffer(() -> "TeamLifeBind magic screen triangle", GpuBuffer.USAGE_VERTEX, vertices);
            }
        }
        if (depthCopy == null || depthCopy.getWidth(0) != main.width || depthCopy.getHeight(0) != main.height
            || depthCopy.getFormat() != main.getDepthTexture().getFormat()) {
            releaseTargets();
            depthCopy = device.createTexture("TeamLifeBind magic world depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                main.getDepthTexture().getFormat(), main.width, main.height, 1, 1);
            depthView = device.createTextureView(depthCopy);
            // A fixed pixel ceiling makes 4K and high-DPI windows stay inside a 1.38-million-pixel raymarch budget.
            double scale = Math.min(1, Math.sqrt(MAX_EFFECT_PIXELS / ((double) main.width * main.height)));
            effectTarget = new TextureTarget("TeamLifeBind magic radiance", Math.max(1, (int) (main.width * scale)),
                Math.max(1, (int) (main.height * scale)), GpuFormat.RGBA16_FLOAT, null);
        }
    }

    private static Scene followCaster(Scene scene, CameraRenderState camera) {
        boolean casting = scene.phase() == SpellGeometry.Phase.CAST.ordinal();
        boolean travelling = scene.phase() == SpellGeometry.Phase.TRAVEL.ordinal();
        boolean orbit = travelling && (scene.mechanic() == MagicMechanic.ORBIT.ordinal()
            || scene.mechanic() == MagicMechanic.SHIELD.ordinal());
        boolean channel = travelling && scene.mechanic() == MagicMechanic.CHANNEL.ordinal();
        if (!casting && !orbit && !channel) return scene;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return scene;
        var caster = client.level.getPlayerByUUID(scene.caster());
        if (caster == null || caster.isRemoved() || caster.level() != client.level) return scene;
        boolean cameraCaster = caster == client.getCameraEntity();
        float partial = cameraCaster ? camera.cameraEntityPartialTicks
            : client.getDeltaTracker().getGameTimeDeltaPartialTick(!client.level.tickRateManager().isEntityFrozen(caster));
        if (!Float.isFinite(partial)) return scene;
        boolean firstPerson = cameraCaster && camera.isFirstPerson;
        // The camera interpolates xo; entity models interpolate xOld. They can differ.
        Vec3 feet = caster.tickCount == 0 ? caster.position() : firstPerson ? caster.getPosition(partial)
            : new Vec3(Mth.lerp(partial, caster.xOld, caster.getX()),
                Mth.lerp(partial, caster.yOld, caster.getY()), Mth.lerp(partial, caster.zOld, caster.getZ()));
        Vec3 origin = channel ? firstPerson ? camera.pos : feet.add(0, caster.getEyeHeight(), 0)
            : feet.add(0, casting ? 0.12 : 1, 0);
        if (!Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)) return scene;
        // A live channel keeps its server-authorized collision endpoint. World effects never attach.
        Vec3 end = channel ? scene.end() : scene.end().add(origin.subtract(scene.origin()));
        return new Scene(scene.id(), scene.caster(), scene.spell(), scene.phase(), scene.mechanic(), origin, end,
            scene.radius(), scene.ageTicks(), scene.durationTicks(), scene.receivedTick());
    }

    private static Rectangle screenBounds(Scene scene, Vec3 camera, Matrix4f matrix, int width, int height) {
        Vec3 relative = scene.center().subtract(camera);
        double extent = scene.extent();
        if (relative.lengthSqr() <= extent * extent) return new Rectangle(0, 0, width, height);
        float minX = 1, minY = 1, maxX = -1, maxY = -1;
        for (int i = 0; i < 8; i++) {
            Vector4f corner = matrix.transform(new Vector4f((float) (relative.x + ((i & 1) == 0 ? -extent : extent)),
                (float) (relative.y + ((i & 2) == 0 ? -extent : extent)), (float) (relative.z + ((i & 4) == 0 ? -extent : extent)), 1));
            if (corner.w <= 0.05F) return new Rectangle(0, 0, width, height);
            minX = Math.min(minX, corner.x / corner.w); maxX = Math.max(maxX, corner.x / corner.w);
            minY = Math.min(minY, corner.y / corner.w); maxY = Math.max(maxY, corner.y / corner.w);
        }
        int left = Math.clamp((int) Math.floor((minX * 0.5F + 0.5F) * width) - 2, 0, width);
        int right = Math.clamp((int) Math.ceil((maxX * 0.5F + 0.5F) * width) + 2, 0, width);
        int bottom = Math.clamp((int) Math.floor((minY * 0.5F + 0.5F) * height) - 2, 0, height);
        int top = Math.clamp((int) Math.ceil((maxY * 0.5F + 0.5F) * height) + 2, 0, height);
        return right > left && top > bottom ? new Rectangle(left, bottom, right - left, top - bottom) : null;
    }

    private static void releaseTargets() {
        if (effectTarget != null) { effectTarget.destroyBuffers(); effectTarget = null; }
        if (depthView != null) { depthView.close(); depthView = null; }
        if (depthCopy != null) { depthCopy.close(); depthCopy = null; }
    }

    private static void release() {
        releaseTargets();
        if (triangle != null) { triangle.close(); triangle = null; }
        if (cache != null) { cache.close(); cache = null; }
        volumePipeline = null; compositePipeline = null; outputFormat = null;
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("teamlifebind", path); }
    private record Rectangle(int x, int y, int width, int height) { }
    private record Draw(com.mojang.renderpearl.api.buffers.GpuBufferSlice uniform, Rectangle rectangle) { }
    private record Scene(long id, UUID caster, SpellDefinition spell, int phase, int mechanic, Vec3 origin, Vec3 end,
                         float radius, float ageTicks, int durationTicks, long receivedTick) {
        double age(double now) { return Math.max(0, ageTicks + now - receivedTick); }
        boolean segment() { return phase == 1 && (mechanic == 0 || mechanic == 1 || mechanic == 2 || mechanic == 5 || mechanic == 10 || mechanic == 16 || mechanic == 17); }
        Vec3 center() { return segment() ? origin.add(end).scale(0.5) : mechanic == 12 && phase == 1 ? origin.add(0, radius * 0.5, 0) : origin; }
        double extent() { return radius * 1.8 + (segment() ? Math.min(48, origin.distanceTo(end) * 0.5) : 0); }
        AABB bounds() { Vec3 center = center(); double extent = extent(); return new AABB(center.x - extent, center.y - extent, center.z - extent, center.x + extent, center.y + extent, center.z + extent); }
    }

    /** Uses the active resource manager, so resource-pack overrides and F3+T work normally. */
    private static final class ResourceShaders implements ShaderSource {
        private final ResourceManager resources;
        ResourceShaders(ResourceManager resources) { this.resources = resources; }
        @Override public String getShader(Identifier location, ShaderType type) {
            try (var reader = resources.openAsReader(type.idConverter().idToFile(location))) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[4096];
                for (int count; (count = reader.read(buffer)) >= 0;) text.append(buffer, 0, count);
                return text.toString();
            } catch (IOException exception) { throw new UncheckedIOException("Cannot load magic shader " + location, exception); }
        }
        @Override public CachedIncludeSource getInclude(Identifier location) { return CachedIncludeSource.createError("Magic shaders are self-contained: " + location); }
        @Override public void close() { }
    }
}
