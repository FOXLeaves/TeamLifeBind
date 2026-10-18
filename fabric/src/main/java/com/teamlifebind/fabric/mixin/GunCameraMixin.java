package com.teamlifebind.fabric.mixin;

import com.teamlifebind.fabric.GunClientPresentation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
abstract class GunCameraMixin {
    @Unique
    private boolean tlb$isLocalFirstPersonCamera() {
        Camera camera = (Camera) (Object) this;
        Minecraft client = Minecraft.getInstance();
        return client.player != null && camera.entity() == client.player && camera.isInitialized()
            && !camera.isDetached() && !camera.isPanoramicMode();
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void tlb$gunWorldFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (tlb$isLocalFirstPersonCamera()) {
            cir.setReturnValue(GunClientPresentation.worldFov(cir.getReturnValue(), partialTick));
        }
    }

    @Inject(method = "calculateHudFov", at = @At("RETURN"), cancellable = true)
    private void tlb$gunModelFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (tlb$isLocalFirstPersonCamera()) {
            cir.setReturnValue(GunClientPresentation.modelFov(cir.getReturnValue(), partialTick));
        }
    }
}
