package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveOverlays;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChatComponent.class)
public abstract class ArchiveChatMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",at=@At("HEAD"),cancellable=true)
    private void tlb$chat(GuiGraphicsExtractor g,Font font,int x,int y,int width,ChatComponent.DisplayMode mode,boolean focused,CallbackInfo ci){if(ArchiveOverlays.chat(g))ci.cancel();}
}
