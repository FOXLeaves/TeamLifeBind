package com.teamlifebind.fabric.mixin;

import com.teamlifebind.fabric.TeamLifeBindFabric;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class UtilityAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void tlb$interruptAttackingChannel(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            TeamLifeBindFabric.interruptUtility(player);
            TeamLifeBindFabric.recordCampaignAttack(player, target);
        }
    }
}
