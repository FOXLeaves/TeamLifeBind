package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractButton.class)
public abstract class ArchiveButtonMixin {
    @Inject(method="extractDefaultSprite",at=@At("HEAD"),cancellable=true)
    private void tlb$button(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (ArchiveTheme.widgetsEnabled()) { ArchiveTheme.button(g,(AbstractWidget)(Object)this); ci.cancel(); }
    }
}
