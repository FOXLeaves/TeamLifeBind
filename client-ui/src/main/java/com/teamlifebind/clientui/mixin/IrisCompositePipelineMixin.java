package com.teamlifebind.clientui.mixin;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Iris 1.11.7 binds its own composite program/samplers; its unused native shell needs no InSampler. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
abstract class IrisCompositePipelineMixin {
    @Unique private static boolean tlb$legacyIris(){
        try{return String.valueOf(Class.forName("net.irisshaders.iris.Iris").getMethod("getVersion").invoke(null)).matches("1\\.11\\.7(?:\\+.*)?");}
        catch(ReflectiveOperationException|LinkageError ignored){return false;}
    }
    @Redirect(method="<clinit>",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withFragmentShader(Ljava/lang/String;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"),require=0)
    private static RenderPipeline.Builder tlb$uniformlessShell(RenderPipeline.Builder builder,String shader){
        if(tlb$legacyIris()&&shader.equals("core/blit_screen")){
            org.slf4j.LoggerFactory.getLogger("TeamLifeBind/RenderCompat").info("Iris 1.11.7 composite pipeline compatibility enabled");
            return builder.withFragmentShader(Identifier.fromNamespaceAndPath("teamlifebind","core/iris_composite_shell"));
        }
        return builder.withFragmentShader(shader);
    }
    @Redirect(method="<clinit>",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withBindGroupLayout(Lcom/mojang/renderpearl/api/pipeline/BindGroupLayout;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"),require=0)
    private static RenderPipeline.Builder tlb$actualCompositeBindings(RenderPipeline.Builder builder,BindGroupLayout layout){
        return tlb$legacyIris()&&layout==BindGroupLayouts.IN_SAMPLER?builder:builder.withBindGroupLayout(layout);
    }
    @Redirect(method="<clinit>",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withDepthStencilState(Lcom/mojang/renderpearl/api/pipeline/DepthStencilState;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"),require=0)
    private static RenderPipeline.Builder tlb$colorOnlyComposite(RenderPipeline.Builder builder,DepthStencilState state){
        return tlb$legacyIris()?builder:builder.withDepthStencilState(state);
    }
}
