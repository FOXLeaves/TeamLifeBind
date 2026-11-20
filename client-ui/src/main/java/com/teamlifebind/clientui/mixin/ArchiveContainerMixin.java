package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerScreen.class)
public abstract class ArchiveContainerMixin {
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected Slot hoveredSlot;
    @Inject(method="extractSlot",at=@At("HEAD"))
    private void tlb$slot(GuiGraphicsExtractor g, Slot slot, int x, int y, CallbackInfo ci) {
        if (ArchiveTheme.enabled() && !(((Object)this) instanceof com.teamlifebind.clientui.AuiMenuScreen) && slot.isActive())
            ArchiveTheme.slot(g,slot.x,slot.y,slot==hoveredSlot,slot.hasItem());
    }
    @Inject(method={"extractSlotHighlightBack","extractSlotHighlightFront"},at=@At("HEAD"),cancellable=true)
    private void tlb$noStockHighlight(CallbackInfo ci) { if (ArchiveTheme.enabled()) ci.cancel(); }
}
