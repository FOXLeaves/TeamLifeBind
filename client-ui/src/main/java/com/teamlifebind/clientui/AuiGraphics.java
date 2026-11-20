package com.teamlifebind.clientui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/** Shared accessor contract; the loader's client-only mixin implements it. */
public final class AuiGraphics {
    public interface Access { GuiRenderState tlb$auiState(); }
    private AuiGraphics() {}
    public static void submit(GuiGraphicsExtractor graphics, PictureInPictureRenderState state) {
        ((Access) graphics).tlb$auiState().addPicturesInPictureState(state);
    }
    public static void submitElement(GuiGraphicsExtractor graphics, GuiElementRenderState state) {
        ((Access) graphics).tlb$auiState().addGuiElement(state);
    }
}
