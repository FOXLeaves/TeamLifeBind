package com.teamlifebind.neoforge.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Retain the pre-26.3.0.65 extraction descriptor for Sodium 0.9.2's optional NeoForge hook. */
@Mixin(value=LevelExtractor.class,priority=500)
abstract class SodiumLegacyExtractionMixin {
    @Shadow private void extractVisibleBlockEntities(Camera camera,float partialTick,LevelRenderState state){throw new AssertionError();}
    @Unique
    private void extractVisibleBlockEntities(Camera camera,float partialTick,LevelRenderState state,Frustum unused){
        extractVisibleBlockEntities(camera,partialTick,state);
    }
}
