package com.teamlifebind.neoforge.mixin;

import com.teamlifebind.neoforge.GunClientPresentation;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The rotor model receives a temporary render copy; the real inventory stack stays untouched. */
@Mixin(ItemModelResolver.class)
abstract class GunRotorModelMixin {
    @ModifyVariable(method = "updateForTopItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack tlb$rotatingGunRenderStack(ItemStack value, ItemStackRenderState renderState,
            ItemStack original, ItemDisplayContext context, Level level, ItemOwner owner, int seed) {
        return GunClientPresentation.renderStack(value, context, owner);
    }
}
