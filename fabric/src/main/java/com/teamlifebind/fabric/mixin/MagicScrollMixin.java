package com.teamlifebind.fabric.mixin;

import com.teamlifebind.fabric.TeamMagicClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MagicScrollMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void tlb$magicScroll(long window, double horizontal, double vertical, CallbackInfo ci) { if (TeamMagicClient.scroll(vertical)) ci.cancel(); }
}
