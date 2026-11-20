package com.teamlifebind.clientui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

public record ArchiveBackdropState(int x0,int y0,int x1,int y1,float clock,float pointerX,float pointerY,float focus)
        implements PictureInPictureRenderState {
    @Override public float scale(){return 1;}
    @Override public ScreenRectangle scissorArea(){return null;}
    @Override public ScreenRectangle bounds(){return PictureInPictureRenderState.getBounds(x0,y0,x1,y1,null);}
}
