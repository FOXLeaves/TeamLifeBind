package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveLoading;
import com.teamlifebind.clientui.ArchiveTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LoadingOverlay.class)
public abstract class ArchiveLoadingMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow private float currentProgress;
    @Shadow private long fadeInStart;
    @Shadow private long fadeOutStart;
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void tlb$loading(GuiGraphicsExtractor g,int x,int y,float dt,CallbackInfo ci) {
        if(!ArchiveTheme.enabled())return;
        long now=Util.getMillis();if(fadeInStart<0)fadeInStart=now;
        float reveal=fadeOutStart<0?0:ArchiveLoading.revealProgress(now-fadeOutStart);
        if(fadeOutStart>=0 && minecraft.gui.screen()!=null) minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(g,x,y,dt);
        currentProgress=Math.max(currentProgress,Math.clamp(reload.getActualProgress(),0,1));
        var completion=reload.done();if(completion.isDone()&&!completion.isCompletedExceptionally()&&!completion.isCancelled())ArchiveLoading.resourceFontsReady();
        ArchiveLoading.draw(g,currentProgress,reveal,"TEAM LIFE BIND","RESOURCE RELOAD",true);
        if(reveal>=1)minecraft.gui.setOverlay(null);
        ci.cancel();
    }
}
