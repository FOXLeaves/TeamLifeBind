package com.teamlifebind.clientui.mixin;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AbstractContainerScreen.class)
public interface ArchiveContainerAccess {
    @Accessor("leftPos") int tlb$left();
    @Accessor("topPos") int tlb$top();
    @Accessor("hoveredSlot") void tlb$hovered(Slot slot);
}
