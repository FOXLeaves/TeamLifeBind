package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveOverlays;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
@Mixin(BossHealthOverlay.class)
public abstract class ArchiveBossMixin {
    @Shadow @Final private Map<UUID,LerpingBossEvent> events;
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void tlb$boss(GuiGraphicsExtractor g,CallbackInfo ci){if(ArchiveOverlays.boss(g,events.values()))ci.cancel();}
}
