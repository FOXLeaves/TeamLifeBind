package com.teamlifebind.forge.mixin;

import com.teamlifebind.forge.GunClientPresentation;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture real binding transitions before KeyMapping changes its state; repeated down events do not become new shots. */
@Mixin(KeyMapping.class)
abstract class GunKeyStateMixin {
    @Inject(method = "setDown", at = @At("HEAD"))
    private void tlb$recordGunAttackEdge(boolean down, CallbackInfo ci) {
        GunClientPresentation.recordAttackKey((KeyMapping) (Object) this, down);
    }
}
