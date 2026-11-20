package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin({TitleScreen.class,PauseScreen.class})
public abstract class ArchiveTitleMixin {
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void tlb$title(GuiGraphicsExtractor g, int x, int y, float dt, CallbackInfo ci) {
        if (ArchiveTheme.enabled()) { ArchiveTheme.title((Screen)(Object)this,g,x,y,dt); ci.cancel(); }
    }
}
