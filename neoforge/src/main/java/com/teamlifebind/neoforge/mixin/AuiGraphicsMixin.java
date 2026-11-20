package com.teamlifebind.neoforge.mixin;
import com.teamlifebind.clientui.AuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(GuiGraphicsExtractor.class)
public interface AuiGraphicsMixin extends AuiGraphics.Access {
    @Accessor("guiRenderState") GuiRenderState tlb$auiState();
}
