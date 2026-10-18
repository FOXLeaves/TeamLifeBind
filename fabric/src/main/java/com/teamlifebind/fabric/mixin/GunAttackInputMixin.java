package com.teamlifebind.fabric.mixin;

import com.teamlifebind.fabric.GunClientPresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A held gun sends its own trigger intent instead of the vanilla melee or mining action. */
@Mixin(Minecraft.class)
abstract class GunAttackInputMixin {
    @Shadow public MultiPlayerGameMode gameMode;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void tlb$drainGunTrigger(CallbackInfo ci) {
        GunClientPresentation.drainAttackClicks((Minecraft) (Object) this);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void tlb$useGunForAim(CallbackInfo ci) {
        if (!GunClientPresentation.blocksVanillaAttack()) return;
        Minecraft client = (Minecraft) (Object) this;
        if (gameMode != null && client.player != null && !client.player.isUsingItem()) {
            gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        }
        ci.cancel();
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void tlb$blockGunMelee(CallbackInfoReturnable<Boolean> cir) {
        if (GunClientPresentation.blocksVanillaAttack()) {
            if (gameMode != null) gameMode.stopDestroyBlock();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void tlb$blockGunMining(boolean attackHeld, CallbackInfo ci) {
        if (GunClientPresentation.blocksVanillaAttack()) {
            if (gameMode != null) gameMode.stopDestroyBlock();
            ci.cancel();
        }
    }
}
