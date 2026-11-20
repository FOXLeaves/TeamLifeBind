package com.teamlifebind.clientui;

import com.mojang.blaze3d.platform.InputConstants;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.layout.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import java.nio.file.Path;
import java.util.List;

/** Opt-in fixture: hover a real title card, navigate to settings, then return with Escape. */
final class ArchiveScreenMotionProbe {
    private final Screen title;
    private final Path output;
    private final List<String> results;
    private int phase, ticks, opened, captures;
    private boolean returning;
    private long clock;
    private boolean framePrepared;
    private int[] oldUi, middleUi;
    private int oldEffectId;
    ArchiveScreenMotionProbe(Screen title,Path output,List<String> results){
        this.title=title;this.output=output;this.results=results;AuiScreenTransitions.clockForTest(()->clock);
    }
    boolean tick() {
        check(++ticks<400,"Actual screen transition timed out");
        var mc=Minecraft.getInstance();var doc=ArchivePresenter.document();
        if(phase==0){
            if(doc==null||!AuiPageReadiness.ready(doc)||AuiClient.inkFrame().effect())return false;
            try(var ignored=Document.withContext(doc)){
                var card=doc.querySelector(".control").getBoundingClientRect();
                var point=doc.documentToScreenPosition(new Position(card.left+card.width/2,card.top+card.height/2));var w=mc.getWindow();
                mc.mouseHandler.onMove(w.handle(),point.x*w.getScreenWidth()/w.getGuiScaledWidth(),point.y*w.getScreenHeight()/w.getGuiScaledHeight(),0,0);
            }
            opened=ticks;phase=1;return false;
        }
        if(phase==1){
            if(ticks-opened<8)return false;
            try(var ignored=Document.withContext(doc)){
                var first=doc.querySelector(".control");check(first.isHover,"Fixture pointer did not hover the title card");
                var card=first.getBoundingClientRect();var pane=doc.getElementById("control-scroll").getBoundingClientRect();
                check(card.top>=pane.top-.1&&card.left>=pane.left-.1&&card.right<=pane.right+.1,"Hovered title card loses its rounded outline");
            }
            capture("title-hover",mc.gameRenderer.mainRenderTarget());
            oldUi=capture("screen-old",AuiInkEffect.lastUiTarget());
            oldEffectId=AuiInkEffect.lastEffectId();
            mc.gui.setScreen(new OptionsScreen(title,mc.options));
            check(AuiScreenTransitions.frame().effect()&&AuiScreenTransitions.frame().progress()==0,"Screen wipe did not wait for the incoming page");
            phase=2;return false;
        }
        if(phase==2){
            if(doc==null||!AuiPageReadiness.ready(doc)){
                check(AuiScreenTransitions.frame().progress()==0,"Font/CSS wait used up the liquid wipe");return false;
            }
            var frame=AuiScreenTransitions.frame();
            if(captures<3&&!framePrepared){
                clock+=AuiInkTransition.CONTENT_MILLIS*250_000L;framePrepared=true;opened=ticks;return false;
            }
            if(framePrepared&&ticks-opened<3)return false;
            if(captures==3&&ticks-opened<3)return false;
            if(frame.effect()&&captures<3){
                // Several catch-up ticks can run before a render. Wait for the actual GPU frame.
                var rendered=AuiInkEffect.lastFrame();
                if(rendered==null||rendered.serial()!=frame.serial()||Math.abs(rendered.progress()-frame.progress())>.001)return false;
                check(AuiInkEffect.lastUiTarget()!=null&&AuiInkEffect.lastUiTarget()!=AuiInkEffect.lastSourceTarget(),"Screen change bypassed the liquid compositor");
                check(Math.abs(frame.progress()-(captures+1)*.25)<.001,"Incorrect visible wipe progress");
                if(!returning&&captures==1){
                    var owner=mc.gui.screen();
                    try(var ignored=Document.withContext(doc)){
                        String caption=net.minecraft.network.chat.Component.translatable("options.video").getString();
                        var action=doc.querySelectorAll(".control").stream().filter(element->element.getTextContent().contains(caption)).findFirst().orElseThrow();
                        var bounds=action.getBoundingClientRect();var point=doc.documentToScreenPosition(new Position(bounds.left+bounds.width/2,bounds.top+bounds.height/2));
                        var click=new net.minecraft.client.input.MouseButtonEvent(point.x,point.y,new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));
                        ArchivePresenter.mouse(owner,click,true,false);ArchivePresenter.mouse(owner,click,false,false);
                        check(mc.gui.screen()==owner,"Pointer activated a new-page button under the outgoing UI");
                    }
                }
                var pixels=capture((returning?"return":"screen")+"-wipe-"+captures,AuiInkEffect.lastUiTarget());
                if(captures==1)middleUi=pixels;captures++;
                framePrepared=false;opened=ticks;if(captures==3)clock+=AuiInkTransition.CONTENT_MILLIS*250_000L;return false;
            }
            if(!AuiScreenTransitions.active()){
                check(captures==3,"Liquid screen transition skipped its visible intermediate frames: "+captures);
                var newUi=capture(returning?"return-new":"screen-new",AuiInkEffect.lastSourceTarget());
                int oldPixels=0,newPixels=0,contour=0;
                for(int i=0;i<oldUi.length;i++)if(oldUi[i]!=newUi[i]){
                    if(middleUi[i]==oldUi[i])oldPixels++;
                    else if(middleUi[i]==newUi[i])newPixels++;
                    else contour++;
                }
                if(oldPixels<=100||newPixels<=100||contour<=100){
                    capture("history-failure",AuiInkEffect.lastHistoryTarget());
                    throw new IllegalStateException("Actual page wipe did not retain both pages: "+oldPixels+"/"+newPixels+"/"+contour+" oldRenderer="+oldEffectId+" newRenderer="+AuiInkEffect.lastEffectId()+" return="+returning);
                }
                if(!returning){
                    oldUi=newUi;
                    oldEffectId=AuiInkEffect.lastEffectId();
                    ArchivePresenter.key(mc.gui.screen(),new KeyEvent(InputConstants.KEY_ESCAPE,0,0));
                    check(mc.gui.screen()==title,"Escape did not immediately execute native navigation");
                    check(AuiScreenTransitions.frame().direction()<0,"Return wipe did not reverse its direction");
                    returning=true;captures=0;return false;
                }
                phase=3;
            }
            return false;
        }
        AuiScreenTransitions.clockForTest(null);
        var motionKey=new KeyEvent(InputConstants.KEY_M,0,InputConstants.MOD_CONTROL|InputConstants.MOD_SHIFT);
        ArchivePresenter.key(mc.gui.screen(),motionKey);check(!AuiMenuSettings.motion(),"Motion shortcut did not disable animation");
        ArchivePresenter.key(mc.gui.screen(),motionKey);check(AuiMenuSettings.motion(),"Motion shortcut did not restore animation");
        mc.gui.setScreen(new OptionsScreen(title,mc.options));
        AuiMenuSettings.toggleMotion();check(!AuiScreenTransitions.active(),"Reduced motion did not cancel a pending page wipe");
        AuiMenuSettings.toggleMotion();check(!AuiScreenTransitions.active(),"Turning motion on replayed a stale page wipe");
        mc.gui.setScreen(title);
        results.add("PASS SCREEN_LIQUID_MOTION hoverOutline=complete contents=420ms realGpuIntermediateFrames=6 return=reversed nativeEscape=immediate reducedMotion=cancelled pointerDuringWipe=blocked motionShortcut=working");
        return true;
    }
    private int[] capture(String name,com.mojang.blaze3d.pipeline.RenderTarget target){
        try{return AuiInkSelfTest.readback(target,output.resolve(name+".png"));}
        catch(Exception error){throw new IllegalStateException("Direct UI readback failed",error);}
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
