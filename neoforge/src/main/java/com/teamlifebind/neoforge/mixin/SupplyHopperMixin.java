package com.teamlifebind.neoforge.mixin;

import com.teamlifebind.neoforge.TeamLifeBindNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
abstract class SupplyHopperMixin {
    @Inject(method = "getBlockContainer", at = @At("HEAD"), cancellable = true)
    private static void tlb$protectLockedTransfer(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Container> cir) {
        if (TeamLifeBindNeoForge.isLockedSupply(level, pos)) cir.setReturnValue(null);
    }
}
