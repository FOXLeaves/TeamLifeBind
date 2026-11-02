package com.teamlifebind.forge.mixin;

import com.teamlifebind.forge.NativeHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
abstract class MagicHudMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void tlb$magicHud(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) { NativeHudRenderer.render(graphics); }
}
