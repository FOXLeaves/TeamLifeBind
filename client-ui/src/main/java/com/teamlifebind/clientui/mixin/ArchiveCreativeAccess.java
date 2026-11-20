package com.teamlifebind.clientui.mixin;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(CreativeModeInventoryScreen.class)
public interface ArchiveCreativeAccess {
    @Invoker("selectTab") void tlb$tab(CreativeModeTab tab);
}
