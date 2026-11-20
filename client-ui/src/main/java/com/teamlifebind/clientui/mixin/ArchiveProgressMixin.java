package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveLoading;
import com.teamlifebind.clientui.ArchiveTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ProgressScreen.class)
public abstract class ArchiveProgressMixin {
    @Shadow private Component header;
    @Shadow private Component stage;
    @Shadow private int progress;
    @Shadow private boolean stop;
    @Shadow @Final private boolean clearScreenAfterStop;
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void tlb$progress(GuiGraphicsExtractor g,int x,int y,float dt,CallbackInfo ci) {
        if(!ArchiveTheme.enabled())return;
        if(stop && clearScreenAfterStop)Minecraft.getInstance().gui.setScreen(null);
        else ArchiveLoading.draw(g,progress/100F,0,header==null?"TEAM LIFE BIND":header.getString(),stage==null?"":stage.getString(),Minecraft.getInstance().level==null);
        ci.cancel();
    }
}
