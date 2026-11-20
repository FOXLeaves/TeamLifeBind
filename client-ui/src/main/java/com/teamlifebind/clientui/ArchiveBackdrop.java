package com.teamlifebind.clientui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Front-end atmosphere only. Game worlds never enter this renderer or its textures. */
public final class ArchiveBackdrop {
    private static long lastNanos;
    private static double phase;
    private static float pointerX,pointerY,focus=1;
    private static Float testClock;
    private static long submissions;
    private ArchiveBackdrop(){}
    public static void draw(Screen screen,GuiGraphicsExtractor g,int x,int y){
        var mc=Minecraft.getInstance();
        if(!ArchiveTheme.enabled()||mc.level!=null||screen==null||ClientRenderCompat.externalOptions(screen))return;
        if(Boolean.getBoolean("teamlifebind.archive.selftest")||Boolean.getBoolean("teamlifebind.archive.e2e"))submissions++;
        long now=System.nanoTime();double dt=lastNanos==0?0:Math.clamp((now-lastNanos)/1_000_000_000.0,0,.1);lastNanos=now;
        boolean moving=AuiMenuSettings.motion()&&mc.isWindowActive();
        if(moving){
            phase+=dt;
            float blend=(float)(1-Math.exp(-dt*7));
            pointerX+=(Math.clamp(x/(float)Math.max(1,g.guiWidth())*2-1,-1,1)-pointerX)*blend;
            pointerY+=(Math.clamp(y/(float)Math.max(1,g.guiHeight())*2-1,-1,1)-pointerY)*blend;
        }
        float desired=ArchiveTheme.family(screen).equals("title")?1:.35F;
        focus=moving?focus+(desired-focus)*(float)(1-Math.exp(-dt*8)):desired;
        if(Math.abs(focus-desired)<.0005F)focus=desired;
        float clock=testClock==null?(float)phase:testClock;
        AuiGraphics.submit(g,new ArchiveBackdropState(0,0,g.guiWidth(),g.guiHeight(),clock,testClock==null?pointerX:0,testClock==null?pointerY:0,focus));
        g.nextStratum();
    }
    static void clockForTest(Float value){
        if(!Boolean.getBoolean("teamlifebind.archive.selftest"))throw new IllegalStateException("Backdrop fixture is disabled");
        testClock=value;
    }
    static float activeSeconds(){return (float)phase;}
    static long submissions(){return submissions;}
}
