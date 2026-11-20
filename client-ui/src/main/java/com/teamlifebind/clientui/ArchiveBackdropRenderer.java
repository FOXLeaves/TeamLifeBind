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

/** Analytic orbital scene; its capped attachment is independent of full-resolution HTML and the world. */
public final class ArchiveBackdropRenderer extends PictureInPictureRenderer<ArchiveBackdropState> {
    private TextureTarget target;
    private PipelineCache cache;
    private RenderPipeline pipeline;
    private GpuBuffer triangle;
    private ArchiveBackdropState drawn;
    private static TextureTarget lastTarget;
    private static ArchiveBackdropState lastState;
    private static long presentations;
    static TextureTarget lastTarget(){return lastTarget;}
    static ArchiveBackdropState lastState(){return lastState;}
    static long presentations(){return presentations;}
    @Override public Class<ArchiveBackdropState> getRenderStateClass(){return ArchiveBackdropState.class;}
    @Override protected String getTextureLabel(){return "TLB orbital atmosphere";}
    @Override protected void renderToTexture(ArchiveBackdropState state,PoseStack pose,SubmitNodeCollector collector){}
    public boolean canBeReusedFor(ArchiveBackdropState state,int width,int height){return true;}
    @Override public void prepare(ArchiveBackdropState state,GuiRenderState gui,FeatureRenderDispatcher features,int scale){
        var window=Minecraft.getInstance().getWindow();int physicalW=window.getWidth(),physicalH=window.getHeight();
        if(physicalW<=0||physicalH<=0||scale<=0)return;
        float ratio=Math.min(1,Math.min(1280F/physicalW,720F/physicalH));
        int w=Math.max(1,Math.round(physicalW*ratio)),h=Math.max(1,Math.round(physicalH*ratio));
        if(target==null)target=new TextureTarget("TLB orbital background",w,h,GpuFormat.RGBA8_UNORM,null);
        else if(target.width!=w||target.height!=h){target.resize(w,h);drawn=null;}
        if(cache==null){
            cache=new PipelineCache(RenderSystem.getDevice(),new Shaders());
            pipeline=RenderPipeline.builder().withLocation(id("pipeline/menu_orbit"))
                .withVertexShader(id("core/menu_ink")).withFragmentShader(id("core/menu_orbit"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("OrbitFrame",UniformType.UNIFORM_BUFFER).build())
                .withVertexBinding(0,DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
                .withColorTargetState(new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
            if(cache.get(pipeline)==null)throw new IllegalStateException("TLB orbital shader did not compile");
            try(var stack=MemoryStack.stackPush()){
                var data=stack.malloc(36);data.putFloat(-1).putFloat(-1).putFloat(0).putFloat(3).putFloat(-1).putFloat(0).putFloat(-1).putFloat(3).putFloat(0).flip();
                triangle=RenderSystem.getDevice().createBuffer(()->"TLB orbital triangle",GpuBuffer.USAGE_VERTEX,data);
            }
        }
        boolean changed=drawn==null||Math.abs(state.clock()-drawn.clock())>=1F/30
            ||Math.abs(state.pointerX()-drawn.pointerX())>.001F||Math.abs(state.pointerY()-drawn.pointerY())>.001F
            ||state.focus()!=drawn.focus();
        if(changed){
            var encoder=RenderSystem.getDevice().createCommandEncoder();
            try(var stack=MemoryStack.stackPush()){
                var values=stack.malloc(32);values.putFloat(w).putFloat(h).putFloat(state.clock()).putFloat(state.focus())
                    .putFloat(state.pointerX()).putFloat(state.pointerY()).putFloat(0).putFloat(0).flip();
                var uniform=encoder.transientMemory().uploadGpu(values,256,GpuBuffer.USAGE_UNIFORM);
                try(var pass=encoder.createRenderPass(()->"TLB orbital atmosphere",target.getColorTextureView(),Optional.empty())){
                    pass.setPipeline(cache.get(pipeline));pass.setUniform("OrbitFrame",uniform);pass.setVertexBuffer(0,triangle.slice());pass.draw(3,1,0,0);
                }
            }
            drawn=state;
        }
        gui.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            TextureSetup.singleTexture(target.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),state.pose(),
            state.x0(),state.y0(),state.x1(),state.y1(),0,(float)state.x1()*scale/physicalW,1,1-(float)state.y1()*scale/physicalH,-1,null,null));
        if(Boolean.getBoolean("teamlifebind.archive.selftest")){lastTarget=target;lastState=drawn;presentations++;}
    }
    private static Identifier id(String path){return Identifier.fromNamespaceAndPath("tlb_aui",path);}
    @Override public void close(){if(lastTarget==target){lastTarget=null;lastState=null;}if(target!=null)target.destroyBuffers();if(triangle!=null)triangle.close();if(cache!=null)cache.close();super.close();}
    private static final class Shaders implements ShaderSource{
        @Override public String getShader(Identifier id,ShaderType type){
            String path="/assets/tlb_aui/"+type.idConverter().idToFile(id).getPath();
            try(var input=ArchiveBackdropRenderer.class.getResourceAsStream(path)){
                if(input==null)throw new IllegalStateException("Missing orbital shader "+path);
                return new String(input.readAllBytes(),StandardCharsets.UTF_8);
            }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        }
        @Override public CachedIncludeSource getInclude(Identifier id){return CachedIncludeSource.createError("Self-contained orbital shader");}
        @Override public void close(){}
    }
}
