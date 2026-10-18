package com.teamlifebind.forge.mixin;

import com.teamlifebind.forge.TeamLifeBindForge;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomizableContainer.class)
interface NaturalMagicLootMixin {
    // The source loot table is captured before vanilla clears it; the resulting contents persist normally.
    @Inject(method = "unpackLootTable", at = @At("HEAD"))
    private void tlb$captureNatural(Player player, CallbackInfo ci) {
        RandomizableContainer self = (RandomizableContainer) this;
        TeamLifeBindForge.magic().captureNatural(self, self.getLootTable());
    }
    @Inject(method = "unpackLootTable", at = @At("TAIL"))
    private void tlb$appendNatural(Player player, CallbackInfo ci) { TeamLifeBindForge.magic().finishNatural((RandomizableContainer) this, player); }
}
