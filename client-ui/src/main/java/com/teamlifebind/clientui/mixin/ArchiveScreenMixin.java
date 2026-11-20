package com.teamlifebind.clientui.mixin;

import com.teamlifebind.clientui.ArchiveChrome;
import com.teamlifebind.clientui.ArchiveTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ArchiveScreenMixin {
    @Inject(method="extractRenderStateWithTooltipAndSubtitles", at=@At("HEAD"), cancellable=true)
    private void tlb$chrome(GuiGraphicsExtractor g, int x, int y, float dt, CallbackInfo ci) {
        com.teamlifebind.clientui.ArchiveBackdrop.draw((Screen)(Object)this,g,x,y);
        if (com.teamlifebind.clientui.ArchivePresenter.draw((Screen)(Object)this,g,x,y)) { ci.cancel(); return; }
        ArchiveChrome.draw((Screen)(Object)this, g);
    }
    @Inject(method={"extractBackground", "extractBlurredBackground", "extractPanorama", "extractMenuBackground", "extractTransparentBackground"}, at=@At("HEAD"), cancellable=true)
    private void tlb$noStockBackground(CallbackInfo ci) { if (ArchiveTheme.enabled()&&!com.teamlifebind.clientui.ClientRenderCompat.externalOptions((Screen)(Object)this)) ci.cancel(); }
    @Inject(method="init(II)V", at=@At("TAIL"))
    private void tlb$layout(int w, int h, CallbackInfo ci) { com.teamlifebind.clientui.ArchivePolicy.stripFriends((Screen)(Object)this);ArchiveTheme.layoutActions((Screen)(Object)this); }
}
