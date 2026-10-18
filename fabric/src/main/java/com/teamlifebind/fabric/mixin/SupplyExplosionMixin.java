package com.teamlifebind.fabric.mixin;

import com.teamlifebind.fabric.TeamLifeBindFabric;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.class)
abstract class SupplyExplosionMixin {
    @Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
    private void tlb$protectLockedExplosion(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> drops, CallbackInfo ci) {
        if (TeamLifeBindFabric.isLockedSupply(level, pos)) ci.cancel();
    }
}
