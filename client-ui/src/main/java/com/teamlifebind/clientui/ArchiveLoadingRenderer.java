package com.teamlifebind.clientui;
import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Boot-safe shader: classpath resources, no DOM, font, atlas, world texture or loaded resource pack. */
public final class ArchiveLoadingRenderer extends PictureInPictureRenderer<ArchiveLoadingState> {
    private TextureTarget target;
    private PipelineCache cache;
    private RenderPipeline pipeline;
    private GpuBuffer triangle;
    private static TextureTarget lastTarget;
    static TextureTarget lastTarget() { return lastTarget; }
    @Override public Class<ArchiveLoadingState> getRenderStateClass() { return ArchiveLoadingState.class; }
    @Override protected String getTextureLabel() { return "TLB liquid loading"; }
    @Override protected void renderToTexture(ArchiveLoadingState state,PoseStack pose,SubmitNodeCollector collector) {}
    public boolean canBeReusedFor(ArchiveLoadingState state,int width,int height) { return true; }
    @Override public void prepare(ArchiveLoadingState state,GuiRenderState gui,FeatureRenderDispatcher features,int scale) {
        var window=Minecraft.getInstance().getWindow(); int w=window.getWidth(),h=window.getHeight();
        if(w<=0||h<=0||scale<=0) return;
        if(target==null) target=new TextureTarget("TLB loading ink",w,h,GpuFormat.RGBA8_UNORM,null);
        else if(target.width!=w||target.height!=h) target.resize(w,h);
        if(cache==null) {
            cache=new PipelineCache(RenderSystem.getDevice(),new BootShaders());
            pipeline=RenderPipeline.builder().withLocation(id("pipeline/loading_wave"))
                .withVertexShader(id("core/menu_ink")).withFragmentShader(id("core/loading_wave"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("LoadingFrame",UniformType.UNIFORM_BUFFER).build())
                .withVertexBinding(0,DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
                .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
            if(cache.get(pipeline)==null) throw new IllegalStateException("TLB loading shader did not compile");
            try(var stack=MemoryStack.stackPush()) {
                var data=stack.malloc(36);data.putFloat(-1).putFloat(-1).putFloat(0).putFloat(3).putFloat(-1).putFloat(0).putFloat(-1).putFloat(3).putFloat(0).flip();
                triangle=RenderSystem.getDevice().createBuffer(()->"TLB loading triangle",GpuBuffer.USAGE_VERTEX,data);
            }
        }
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        try(var stack=MemoryStack.stackPush()) {
            var values=stack.malloc(32);values.putFloat(w).putFloat(h).putFloat(state.progress()).putFloat(state.clock())
                .putFloat(state.reveal()).putFloat(state.opaque()?1:0).putFloat(0).putFloat(0).flip();
            var uniform=encoder.transientMemory().uploadGpu(values,256,GpuBuffer.USAGE_UNIFORM);
            try(var pass=encoder.createRenderPass(()->"TLB loading wave",target.getColorTextureView(),Optional.empty())) {
                pass.setPipeline(cache.get(pipeline));pass.setUniform("LoadingFrame",uniform);pass.setVertexBuffer(0,triangle.slice());pass.draw(3,1,0,0);
            }
        }
        gui.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            TextureSetup.singleTexture(target.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)),state.pose(),
            state.x0(),state.y0(),state.x1(),state.y1(),0,(float)state.x1()*scale/w,1,1-(float)state.y1()*scale/h,-1,null,null));
        if(Boolean.getBoolean("teamlifebind.aui.selftest")||Boolean.getBoolean("teamlifebind.aui.selftest.capture"))lastTarget=target;
    }
    private static Identifier id(String p) { return Identifier.fromNamespaceAndPath("tlb_aui",p); }
    @Override public void close() { if(lastTarget==target)lastTarget=null;if(target!=null)target.destroyBuffers();if(triangle!=null)triangle.close();if(cache!=null)cache.close();super.close(); }
    private static final class BootShaders implements ShaderSource {
        @Override public String getShader(Identifier id,ShaderType type) {
            String path="/assets/tlb_aui/"+type.idConverter().idToFile(id).getPath();
            try(var in=ArchiveLoadingRenderer.class.getResourceAsStream(path)) {
                if(in==null)throw new IllegalStateException("Missing boot UI shader "+path);
                return new String(in.readAllBytes(),StandardCharsets.UTF_8);
            }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        }
        @Override public CachedIncludeSource getInclude(Identifier id) { return CachedIncludeSource.createError("Self-contained loading shader"); }
        @Override public void close() {}
    }
}
