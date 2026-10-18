package com.teamlifebind.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teamlifebind.fabric.MagicClientPresentation;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Balanced pose scope keeps the cast-book pose from leaking into the offhand or later renders. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class MagicCastingPoseMixin {
    @Unique private boolean tlb$magicPosePushed;
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void tlb$beginSpellPose(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands,
            float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
            float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        tlb$magicPosePushed = MagicClientPresentation.shouldTransform(stack, hand);
        if (tlb$magicPosePushed) { pose.pushPose(); MagicClientPresentation.transform(pose, partialTick); }
    }
    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void tlb$endSpellPose(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands,
            float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
            float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (tlb$magicPosePushed) { pose.popPose(); tlb$magicPosePushed = false; }
    }
}
