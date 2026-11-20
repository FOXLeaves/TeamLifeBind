package com.teamlifebind.clientui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Uses supplied real progress; negative means indeterminate. Nothing here completes a game task. */
public final class ArchiveLoading {
    private static final long EPOCH=System.nanoTime();
    private static boolean nativeFontsReady;
    private static int deferredTextFrames;
    private ArchiveLoading() {}
    public static void resourceFontsReady(){nativeFontsReady=true;}
    public static boolean textReady(){return nativeFontsReady;}
    static int deferredTextFrames(){return deferredTextFrames;}
    public static float revealProgress(long elapsedMillis){return AuiMenuSettings.motion()?Math.clamp(elapsedMillis/650F,0,1):1;}
    public static void draw(GuiGraphicsExtractor g,float progress,float reveal,String title,String stage,boolean opaque) {
        float clock=AuiMenuSettings.motion()?(System.nanoTime()-EPOCH)/1_000_000_000F:0;
        AuiGraphics.submit(g,new ArchiveLoadingState(0,0,g.guiWidth(),g.guiHeight(),progress,clock,reveal,opaque));
        // The initial LoadingOverlay precedes FontManager's first resource publication.
        // Keep the real GPU progress bar; drawing even ASCII with the missing font produces tofu.
        if(!nativeFontsReady){if(Boolean.getBoolean("teamlifebind.archive.selftest"))deferredTextFrames++;return;}
        g.nextStratum();
        var font=Minecraft.getInstance().font;
        int w=g.guiWidth(),h=g.guiHeight(),alpha=Math.round((1-reveal)*255);
        if (alpha<=0) return;
        int gold=alpha<<24|ArchiveTheme.GOLD&0xFFFFFF, silver=alpha<<24|ArchiveTheme.SILVER&0xFFFFFF;
        g.text(font,"TEAM LIFE BIND",Math.max(16,w/16),Math.max(14,h/13),gold,false);
        g.text(font,"0.1.2",Math.max(16,w/16),Math.max(14,h/13)+14,silver,false);
        g.centeredText(font,title,w/2,h*2/3,gold);
        g.centeredText(font,stage,w/2,h*2/3+18,silver);
        String value=progress<0?"···":Math.round(Math.clamp(progress,0,1)*100)+"%";
        g.centeredText(font,value,w/2,h*2/3+38,gold);
    }
}
