package com.teamlifebind.clientui;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
public record ArchiveLoadingState(int x0,int y0,int x1,int y1,float progress,float clock,float reveal,boolean opaque) implements PictureInPictureRenderState {
    @Override public float scale() { return 1; }
    @Override public ScreenRectangle scissorArea() { return null; }
    @Override public ScreenRectangle bounds() { return PictureInPictureRenderState.getBounds(x0,y0,x1,y1,null); }
}
