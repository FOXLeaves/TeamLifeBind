package com.teamlifebind.fabric.mixin;

import com.teamlifebind.fabric.TeamLifeBindFabric;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BaseContainerBlockEntity.class, RandomizableContainerBlockEntity.class})
abstract class SupplyContainerMixin {
    @Inject(method = "canOpen", at = @At("HEAD"), cancellable = true)
    private void tlb$protectLockedMenu(Player player, CallbackInfoReturnable<Boolean> cir) {
        BaseContainerBlockEntity container = (BaseContainerBlockEntity) (Object) this;
        if (container.getLevel() != null && TeamLifeBindFabric.isLockedSupply(container.getLevel(), container.getBlockPos())) cir.setReturnValue(false);
    }
    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void tlb$protectLockedInsertion(int slot, ItemStack stack, CallbackInfo ci) {
        BaseContainerBlockEntity container = (BaseContainerBlockEntity) (Object) this;
        if (container.getLevel() != null && TeamLifeBindFabric.isLockedSupply(container.getLevel(), container.getBlockPos())) ci.cancel();
    }
}
