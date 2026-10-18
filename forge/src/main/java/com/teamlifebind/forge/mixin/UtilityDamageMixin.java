package com.teamlifebind.forge.mixin;

import com.teamlifebind.forge.TeamLifeBindForge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class UtilityDamageMixin {
    @Unique private float tlb$dragonHealthBefore;
    // actuallyHurt has already passed invulnerability and records post-mitigation health before hurtServer checks death.
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void tlb$beforeDragonDamage(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        if ((Object) this instanceof EnderDragon dragon) tlb$dragonHealthBefore = dragon.getHealth();
    }
    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void tlb$afterDragonDamage(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        if ((Object) this instanceof EnderDragon dragon) TeamLifeBindForge.recordCampaignDragonDamage(dragon, source, tlb$dragonHealthBefore, Math.max(0.0F, tlb$dragonHealthBefore - dragon.getHealth()));
    }
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void tlb$interruptDamagedChannel(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player && amount > 0.0F && cir.getReturnValue()) {
            TeamLifeBindForge.interruptUtilityDamage(player);
            TeamLifeBindForge.recordCampaignDamage(player, source, amount);
        }
    }
}
