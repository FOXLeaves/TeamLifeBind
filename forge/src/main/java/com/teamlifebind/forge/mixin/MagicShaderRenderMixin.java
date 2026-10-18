package com.teamlifebind.forge.mixin;

import com.teamlifebind.forge.MagicShaderRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World effects run before first-person hands integrate their separate projection/depth. */
@Mixin(GameRenderer.class)
abstract class MagicShaderRenderMixin {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"), index = 0)
    private Matrix4f tlb$captureMagicProjection(Matrix4f projection) { MagicShaderRenderer.projection(projection); return projection; }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V", shift = At.Shift.AFTER))
    private void tlb$renderMagicVolumes(CallbackInfo ci) { MagicShaderRenderer.render((GameRenderer) (Object) this); }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void tlb$reloadMagicShaders(ResourceManager resources, CallbackInfo ci) { MagicShaderRenderer.requestReload(); }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void tlb$clearMagicWorld(ClientLevel level, CallbackInfo ci) { MagicShaderRenderer.requestReload(); }

    @Inject(method = "close", at = @At("HEAD"))
    private void tlb$closeMagicShaders(CallbackInfo ci) { MagicShaderRenderer.close(); }
}
