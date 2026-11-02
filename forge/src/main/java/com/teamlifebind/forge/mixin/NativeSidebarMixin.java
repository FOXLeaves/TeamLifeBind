package com.teamlifebind.forge.mixin;

import com.teamlifebind.forge.NativeSidebarHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
abstract class NativeSidebarMixin {
    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void tlb$nativeSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        if (NativeSidebarHud.render(graphics, objective)) ci.cancel();
    }
}
