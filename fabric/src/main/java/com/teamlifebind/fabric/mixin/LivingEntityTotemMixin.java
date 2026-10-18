package com.teamlifebind.fabric.mixin;

import com.teamlifebind.fabric.TeamLifeBindFabric;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityTotemMixin {
    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void tlb$checkTeamTotem(DamageSource source, CallbackInfoReturnable<Boolean> callback) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }
        // Vanilla has already applied lethal damage, but has not committed death yet.
        if (player.isDeadOrDying() && TeamLifeBindFabric.tryUseTeamTotemAtDeath(player)) {
            callback.setReturnValue(true);
            return;
        }
        if (!TeamLifeBindFabric.shouldBlockVanillaTotem(player)) {
            return;
        }
        // Match vanilla's first death-protection item; preserve other mods' protection items.
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack item = player.getItemInHand(hand);
            if (item.get(DataComponents.DEATH_PROTECTION) != null) {
                if (item.is(Items.TOTEM_OF_UNDYING)) {
                    callback.setReturnValue(false);
                }
                return;
            }
        }
    }
}
