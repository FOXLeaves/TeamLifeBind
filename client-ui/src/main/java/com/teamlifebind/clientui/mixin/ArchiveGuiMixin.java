package com.teamlifebind.clientui.mixin;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Gui.class)
public abstract class ArchiveGuiMixin {
    @Inject(method="setScreen",at=@At("HEAD"),cancellable=true)
    private void tlb$removeFriends(Screen screen,CallbackInfo ci){if(com.teamlifebind.clientui.ArchivePolicy.blocked(screen))ci.cancel();}
    @ModifyVariable(method="setScreen",at=@At("HEAD"),argsOnly=true)
    private Screen tlb$closePage(Screen screen) { com.teamlifebind.clientui.AuiScreenTransitions.changingTo(screen); com.teamlifebind.clientui.ArchivePresenter.close(); return screen; }
}
