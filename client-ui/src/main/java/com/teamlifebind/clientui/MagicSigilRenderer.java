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

/** Ray-marched 3D spell preview, isolated from world depth and capped at 384 pixels / 30 fps. */
public final class MagicSigilRenderer extends PictureInPictureRenderer<MagicSigilState> {
    private TextureTarget target;
    private PipelineCache cache;
    private RenderPipeline pipeline;
    private GpuBuffer triangle;
    private MagicSigilState drawn;
    private static TextureTarget lastTarget;
    private static MagicSigilState lastState;
    private static long presentations;
    static TextureTarget lastTarget(){return lastTarget;}
    static MagicSigilState lastState(){return lastState;}
    static long presentations(){return presentations;}
    @Override public Class<MagicSigilState> getRenderStateClass(){return MagicSigilState.class;}
    @Override protected String getTextureLabel(){return "TLB sigil atmosphere";}
    @Override protected void renderToTexture(MagicSigilState state,PoseStack pose,SubmitNodeCollector collector){}
    public boolean canBeReusedFor(MagicSigilState state,int width,int height){return true;}
    @Override public void prepare(MagicSigilState state,GuiRenderState gui,FeatureRenderDispatcher features,int scale){
        var window=Minecraft.getInstance().getWindow();int physicalW=window.getWidth(),physicalH=window.getHeight();
        if(physicalW<=0||physicalH<=0||scale<=0)return;
        int w=Math.clamp((state.x1()-state.x0())*scale,32,384),h=Math.clamp((state.y1()-state.y0())*scale,32,384);
        if(target==null)target=new TextureTarget("TLB sigil background",w,h,GpuFormat.RGBA8_UNORM,null);
        else if(target.width!=w||target.height!=h){target.resize(w,h);drawn=null;}
        if(cache==null){
            cache=new PipelineCache(RenderSystem.getDevice(),new Shaders());
            pipeline=RenderPipeline.builder().withLocation(id("pipeline/magic_sigil"))
                .withVertexShader(id("core/menu_ink")).withFragmentShader(id("core/magic_sigil"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("SigilFrame",UniformType.UNIFORM_BUFFER).build())
                .withVertexBinding(0,DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_ALL)).build();
            if(cache.get(pipeline)==null)throw new IllegalStateException("TLB sigil shader did not compile");
            try(var stack=MemoryStack.stackPush()){
                var data=stack.malloc(36);data.putFloat(-1).putFloat(-1).putFloat(0).putFloat(3).putFloat(-1).putFloat(0).putFloat(-1).putFloat(3).putFloat(0).flip();
                triangle=RenderSystem.getDevice().createBuffer(()->"TLB sigil triangle",GpuBuffer.USAGE_VERTEX,data);
            }
        }
        boolean changed=drawn==null||Math.abs(state.clock()-drawn.clock())>=1F/30
            ||state.red()!=drawn.red()||state.green()!=drawn.green()||state.blue()!=drawn.blue()||state.seed()!=drawn.seed()||state.tier()!=drawn.tier();
        if(changed){
            var encoder=RenderSystem.getDevice().createCommandEncoder();
            // JOML's default Vector4f has w=1; that makes the premultiplied target opaque black.
            encoder.clearColorTexture(target.getColorTexture(),new org.joml.Vector4f(0,0,0,0));
            try(var stack=MemoryStack.stackPush()){
                var values=stack.malloc(32);values.putFloat(w).putFloat(h).putFloat(state.clock()).putFloat(state.tier())
                    .putFloat(state.red()).putFloat(state.green()).putFloat(state.blue()).putFloat(state.seed()).flip();
                var uniform=encoder.transientMemory().uploadGpu(values,256,GpuBuffer.USAGE_UNIFORM);
                try(var pass=encoder.createRenderPass(()->"TLB sigil atmosphere",target.getColorTextureView(),Optional.empty())){
                    pass.setPipeline(cache.get(pipeline));pass.setUniform("SigilFrame",uniform);pass.setVertexBuffer(0,triangle.slice());pass.draw(3,1,0,0);
                }
            }
            drawn=state;
        }
        gui.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            TextureSetup.singleTexture(target.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),state.pose(),
            state.x0(),state.y0(),state.x1(),state.y1(),0,1,1,0,-1,null,null));
        if(Boolean.getBoolean("teamlifebind.archive.selftest")||System.getProperty("teamlifebind.archive.e2e.output")!=null){lastTarget=target;lastState=drawn;presentations++;}
    }
    private static Identifier id(String path){return Identifier.fromNamespaceAndPath("tlb_aui",path);}
    @Override public void close(){if(lastTarget==target){lastTarget=null;lastState=null;}if(target!=null)target.destroyBuffers();if(triangle!=null)triangle.close();if(cache!=null)cache.close();super.close();}
    private static final class Shaders implements ShaderSource{
        @Override public String getShader(Identifier id,ShaderType type){
            String path="/assets/tlb_aui/"+type.idConverter().idToFile(id).getPath();
            try(var input=MagicSigilRenderer.class.getResourceAsStream(path)){
                if(input==null)throw new IllegalStateException("Missing sigil shader "+path);
                return new String(input.readAllBytes(),StandardCharsets.UTF_8);
            }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        }
        @Override public CachedIncludeSource getInclude(Identifier id){return CachedIncludeSource.createError("Self-contained sigil shader");}
        @Override public void close(){}
    }
}
