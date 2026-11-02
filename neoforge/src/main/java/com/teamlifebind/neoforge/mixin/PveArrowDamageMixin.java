package com.teamlifebind.neoforge.mixin;

import com.teamlifebind.neoforge.TeamLifeBindNeoForge;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob arrows calculate their own damage independently of the melee attack attribute. */
@Mixin(AbstractArrow.class)
abstract class PveArrowDamageMixin {
    @Shadow private double baseDamage;

    @Inject(method = "setBaseDamageFromMob", at = @At("TAIL"))
    private void tlb$scalePveArrow(float power, CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (!arrow.level().isClientSide()) baseDamage *= TeamLifeBindNeoForge.pveProjectileDamageScale(arrow.getOwner());
    }
}
