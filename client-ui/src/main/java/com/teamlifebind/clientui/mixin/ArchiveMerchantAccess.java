package com.teamlifebind.clientui.mixin;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(MerchantScreen.class)
public interface ArchiveMerchantAccess {
    @Accessor("shopItem") void tlb$offer(int index);
    @Invoker("postButtonClick") void tlb$confirmOffer();
}
