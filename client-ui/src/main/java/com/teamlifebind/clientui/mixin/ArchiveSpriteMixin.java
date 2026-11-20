package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveTheme;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiGraphicsExtractor.class)
public abstract class ArchiveSpriteMixin {
    @Inject(method="blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;IIIII)V",at=@At("HEAD"),cancellable=true)
    private void tlb$atlasSprite(RenderPipeline pipeline,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,int x,int y,int w,int h,int color,CallbackInfo ci) {
        if (sprite.atlasLocation().getPath().contains("/gui.png") && ArchiveTheme.sprite((GuiGraphicsExtractor)(Object)this,sprite.contents().name(),x,y,w,h,color))ci.cancel();
    }
    @Inject(method="fillGradient",at=@At("HEAD"),cancellable=true)
    private void tlb$noStockBackdrop(int x0,int y0,int x1,int y1,int top,int bottom,CallbackInfo ci) {
        var g=(GuiGraphicsExtractor)(Object)this;
        if (ArchiveTheme.widgetsEnabled() && x0<=0 && y0<=0 && x1>=g.guiWidth() && y1>=g.guiHeight()) ci.cancel();
    }
    @Inject(method="blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",at=@At("HEAD"),cancellable=true)
    private void tlb$sprite(RenderPipeline p, Identifier id, int x, int y, int w, int h, int color, CallbackInfo ci) {
        if (ArchiveTheme.sprite((GuiGraphicsExtractor)(Object)this,id,x,y,w,h,color)) ci.cancel();
    }
    @Inject(method="blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIIII)V",at=@At("HEAD"),cancellable=true)
    private void tlb$cropped(RenderPipeline p, Identifier id, int tw, int th, int u, int v, int x, int y, int w, int h, int color, CallbackInfo ci) {
        if (ArchiveTheme.sprite((GuiGraphicsExtractor)(Object)this,id,x,y,w,h,color)) ci.cancel();
    }
    @Inject(method="blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V",at=@At("HEAD"),cancellable=true)
    private void tlb$texture(RenderPipeline p, Identifier id, int x, int y, float u, float v, int w, int h, int sw, int sh, int tw, int th, int color, CallbackInfo ci) {
        if (ArchiveTheme.texture((GuiGraphicsExtractor)(Object)this,id,x,y,w,h,color)) ci.cancel();
    }
    @ModifyVariable(method={
        "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
        "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
        "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"
    },at=@At("HEAD"),argsOnly=true,ordinal=2)
    private int tlb$readable(int color) { return ArchiveTheme.readable(color); }
}
