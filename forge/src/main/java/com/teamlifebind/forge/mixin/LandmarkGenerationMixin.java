package com.teamlifebind.forge.mixin;

import com.teamlifebind.forge.TeamLandmarkGeneration;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class LandmarkGenerationMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void tlb$generateLandmark(WorldGenLevel world, ChunkAccess chunk, StructureManager structures, CallbackInfo ci) {
        TeamLandmarkGeneration.generate(world, chunk, world.getSeed());
    }
}
