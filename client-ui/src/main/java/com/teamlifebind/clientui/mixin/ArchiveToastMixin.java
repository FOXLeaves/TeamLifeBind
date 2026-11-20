package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveOverlays;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
@Mixin(ToastManager.class)
public abstract class ArchiveToastMixin {
    @Inject(method="addToast",at=@At("HEAD"),cancellable=true)
    private void tlb$removeFriendsToast(net.minecraft.client.gui.components.toasts.Toast toast,CallbackInfo ci){if(toast instanceof net.minecraft.client.gui.components.toasts.FriendToast)ci.cancel();}
    @Shadow @Final private List<?> visibleToasts;
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void tlb$toast(GuiGraphicsExtractor g,CallbackInfo ci){if(ArchiveOverlays.toasts(g,visibleToasts))ci.cancel();}
}
