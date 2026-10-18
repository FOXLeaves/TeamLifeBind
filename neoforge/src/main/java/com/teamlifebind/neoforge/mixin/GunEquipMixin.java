package com.teamlifebind.neoforge.mixin;

import com.teamlifebind.neoforge.GunClientPresentation;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ammo synchronization updates the same visible gun instead of restarting its equip animation. */
@Mixin(FirstPersonHandsAndItems.class)
abstract class GunEquipMixin {
    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void tlb$keepSameGunVisible(ItemStack previous, ItemStack current, LocalPlayer player,
            CallbackInfoReturnable<Boolean> cir) {
        if (GunClientPresentation.sameGun(previous, current)) cir.setReturnValue(true);
    }
}
