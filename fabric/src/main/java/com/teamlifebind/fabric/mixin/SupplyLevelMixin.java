package com.teamlifebind.fabric.mixin;

import com.teamlifebind.fabric.TeamLifeBindFabric;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class SupplyLevelMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void tlb$protectLockedSupply(BlockPos pos, BlockState blockState, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if (TeamLifeBindFabric.isLockedSupply((Level) (Object) this, pos)) cir.setReturnValue(false);
    }
}
