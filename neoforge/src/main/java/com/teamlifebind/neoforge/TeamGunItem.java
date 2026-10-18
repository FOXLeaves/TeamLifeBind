package com.teamlifebind.neoforge;

import com.teamlifebind.common.GunProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** Right-click is the native aim pose; independent validated key state authorizes firing. */
final class TeamGunItem extends Item {
    private final GunProfile profile;

    TeamGunItem(Properties properties, GunProfile profile) {
        super(properties.stacksTo(1));
        this.profile = profile;
    }

    GunProfile profile() { return profile; }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.FAIL;
        if (!level.isClientSide() && (!(player instanceof ServerPlayer serverPlayer)
                || !TeamLifeBindNeoForge.manager().beginGunUse(serverPlayer))) return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.NONE; }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) { return stack; }
}
