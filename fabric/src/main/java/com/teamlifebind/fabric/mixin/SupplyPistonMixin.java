package com.teamlifebind.fabric.mixin;

import com.teamlifebind.fabric.TeamLifeBindFabric;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
abstract class SupplyPistonMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void tlb$protectLockedMovement(BlockState state, Level level, BlockPos pos, Direction direction, boolean destroyBlocks, Direction pistonDirection, CallbackInfoReturnable<Boolean> cir) {
        if (TeamLifeBindFabric.isLockedSupply(level, pos)) cir.setReturnValue(false);
    }
}
