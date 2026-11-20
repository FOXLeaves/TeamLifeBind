package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveLoading;
import com.teamlifebind.clientui.ArchiveTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelLoadingScreen.class)
public abstract class ArchiveLevelLoadingMixin {
    @Shadow private LevelLoadTracker loadTracker;
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void tlb$level(GuiGraphicsExtractor g,int x,int y,float dt,CallbackInfo ci) {
        if(!ArchiveTheme.enabled())return;
        ArchiveLoading.draw(g,loadTracker.hasProgress()?loadTracker.serverProgress():-1,0,ArchiveTheme.tr("loading.world"),ArchiveTheme.tr("loading.terrain"),Minecraft.getInstance().level==null);ci.cancel();
    }
}
