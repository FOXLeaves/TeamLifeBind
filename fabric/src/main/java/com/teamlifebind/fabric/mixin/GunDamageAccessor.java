package com.teamlifebind.fabric.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gun damage restores the pre-existing ordinary damage cooldown after each aggregate hit. */
@Mixin(LivingEntity.class)
public interface GunDamageAccessor {
    @Accessor("lastHurt") float tlb$getLastHurt();
    @Accessor("lastHurt") void tlb$setLastHurt(float amount);
}
