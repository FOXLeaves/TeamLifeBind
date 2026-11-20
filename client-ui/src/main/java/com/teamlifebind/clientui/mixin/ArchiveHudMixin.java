package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchiveHud;
import com.teamlifebind.clientui.ArchiveTheme;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Hud.class)
public abstract class ArchiveHudMixin {
    @Shadow private net.minecraft.network.chat.Component overlayMessageString;
    @Shadow private int overlayMessageTime;
    @Inject(method="extractOverlayMessage",at=@At("HEAD"),cancellable=true)
    private void tlb$countdown(GuiGraphicsExtractor graphics,DeltaTracker delta,CallbackInfo ci){if(com.teamlifebind.clientui.ArchiveCountdown.draw(graphics,overlayMessageString,overlayMessageTime,delta.getGameTimeDeltaPartialTick(false)))ci.cancel();}
    @Inject(method={"extractHotbarAndDecorations","extractHotbar"},require=0,at=@At("HEAD"),cancellable=true)
    private void tlb$hud(GuiGraphicsExtractor g,DeltaTracker delta,CallbackInfo ci){if(ArchiveHud.render(g)||ArchiveTheme.enabled()&&net.minecraft.client.Minecraft.getInstance().gui.screen()!=null)ci.cancel();}
    @Inject(method="extractEffects",at=@At("HEAD"),cancellable=true)
    private void tlb$effects(GuiGraphicsExtractor g,DeltaTracker delta,CallbackInfo ci){if(ArchiveTheme.enabled())ci.cancel();}
    @Inject(method="displayScoreboardSidebar",at=@At("HEAD"),cancellable=true)
    private void tlb$scoreboard(GuiGraphicsExtractor g,net.minecraft.world.scores.Objective objective,CallbackInfo ci){if(!objective.getName().equals("tlb_sidebar")&&com.teamlifebind.clientui.ArchiveOverlays.scoreboard(g,objective))ci.cancel();}
    // NeoForge splits decorations into registered layers rather than the vanilla composite method.
    @Inject(method={"extractHealthLevel","extractArmorLevel","extractFoodLevel","extractAirLevel","extractExperienceLevel","extractContextualInfoBar","extractContextualInfoBarBackground"},require=0,at=@At("HEAD"),cancellable=true)
    private void tlb$splitDecorations(CallbackInfo ci){if(ArchiveTheme.enabled()&&net.minecraft.client.Minecraft.getInstance().player!=null&&!net.minecraft.client.Minecraft.getInstance().player.isSpectator())ci.cancel();}
}
