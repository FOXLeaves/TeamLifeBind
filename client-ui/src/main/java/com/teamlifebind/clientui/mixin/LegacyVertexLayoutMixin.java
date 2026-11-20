package com.teamlifebind.clientui.mixin;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 26.3 validates all sixteen vertex slots; older Iris pipeline records only list the used slots. */
@Mixin(FrontendRenderPipeline.class)
abstract class LegacyVertexLayoutMixin {
    @ModifyVariable(method="<init>",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private static List<VertexFormat> tlb$completeVertexSlots(List<VertexFormat> formats){
        if(formats.size()>=16)return formats;
        var complete=new ArrayList<VertexFormat>(16);complete.addAll(formats);
        while(complete.size()<16)complete.add(null);
        return Collections.unmodifiableList(complete);
    }
}
