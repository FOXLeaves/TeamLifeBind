package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveOverlays;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
@Mixin(PlayerTabOverlay.class)
public abstract class ArchiveRosterMixin {
    @Shadow private Component header;
    @Shadow private Component footer;
    @Shadow protected abstract List<PlayerInfo> getPlayerInfos();
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void tlb$roster(GuiGraphicsExtractor g,int width,Scoreboard scoreboard,Objective objective,CallbackInfo ci){if(ArchiveOverlays.roster(g,(PlayerTabOverlay)(Object)this,getPlayerInfos(),header,footer,scoreboard,objective))ci.cancel();}
}
