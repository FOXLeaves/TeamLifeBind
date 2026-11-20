package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveTheme;
import com.teamlifebind.clientui.ArchiveLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ConnectScreen.class)
public abstract class ArchiveConnectMixin {
    @Shadow private Component status;
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void tlb$connecting(GuiGraphicsExtractor g,int x,int y,float dt,CallbackInfo ci) {
        if(!ArchiveTheme.enabled())return;
        ArchiveLoading.draw(g,-1,0,ArchiveTheme.tr("family.social"),status.getString(),Minecraft.getInstance().level==null);
        ArchiveTheme.title((Screen)(Object)this,g,x,y,dt);ci.cancel();
    }
}
