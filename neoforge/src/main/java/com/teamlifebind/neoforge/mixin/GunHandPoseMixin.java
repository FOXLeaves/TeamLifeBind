package com.teamlifebind.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teamlifebind.neoforge.GunClientPresentation;
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

@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class GunHandPoseMixin {
    // The renderer calls this private method sequentially on the render thread for each hand.
    @Unique private boolean tlb$gunPosePushed;

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void tlb$beginGunPose(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands,
            float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
            float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        tlb$gunPosePushed = GunClientPresentation.shouldTransform(stack, hand);
        if (tlb$gunPosePushed) {
            pose.pushPose();
            GunClientPresentation.transform(pose, partialTick);
        }
    }

    // RETURN covers scoping and missing-avatar early returns as well as the normal render path.
    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void tlb$endGunPose(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands,
            float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
            float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (tlb$gunPosePushed) {
            pose.popPose();
            tlb$gunPosePushed = false;
        }
    }
}
