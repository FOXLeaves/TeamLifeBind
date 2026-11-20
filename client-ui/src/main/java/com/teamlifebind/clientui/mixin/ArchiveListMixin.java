package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractSelectionList.class)
public abstract class ArchiveListMixin {
    @Inject(method="extractListBackground",at=@At("HEAD"),cancellable=true)
    private void tlb$list(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (!ArchiveTheme.widgetsEnabled()) return;
        var b=(AbstractWidget)(Object)this;
        ArchiveTheme.panel(g,b.getX(),b.getY(),b.getWidth(),b.getHeight(),ArchiveTheme.GOLD,false); ci.cancel();
    }
    @Inject(method="extractListSeparators",at=@At("HEAD"),cancellable=true)
    private void tlb$separators(CallbackInfo ci) { if (ArchiveTheme.widgetsEnabled()) ci.cancel(); }
    @Inject(method="extractSelection",at=@At("HEAD"),cancellable=true)
    private void tlb$selection(GuiGraphicsExtractor g, @Coerce Object value, int color, CallbackInfo ci) {
        if (!ArchiveTheme.widgetsEnabled()) return;
        var entry=(net.minecraft.client.gui.layouts.LayoutElement)value;
        ArchiveTheme.panel(g,entry.getX(),entry.getY(),entry.getWidth(),entry.getHeight(),ArchiveTheme.GOLD,true); ci.cancel();
    }
}
