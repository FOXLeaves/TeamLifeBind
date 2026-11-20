package com.teamlifebind.clientui;

import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.screen.AuiLinkedScreen;
import com.teamlifebind.common.NativeHud;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Opt-in regression for final GUI composition; a transparent source texture alone is insufficient. */
final class MagicSigilProbe {
    private static final Path OUT=Path.of(System.getProperty("teamlifebind.sigil.output","tlb-sigil-test"));
    private static final int[][] SIZES={{1280,720,2},{2420,1408,3}};
    private static final int[] COLORS={0xff335577,0xffa05a37};
    private static final List<String> evidence=new ArrayList<>();
    private static Fixture screen;
    private static int ticks,stage,entered;
    private static int[] animationBefore;
    private static float animationClock;
    private static boolean done;
    static void tick(){
        if(done)return;
        try{
            var mc=Minecraft.getInstance();ticks++;
            if(ticks>1800)throw new AssertionError("Sigil composition timeout");
            if(screen==null){
                if(mc.gui.overlay()!=null||mc.gui.screen()==null||ticks<50)return;
                Files.createDirectories(OUT);screen=new Fixture();configure();return;
            }
            if(ticks-entered<12||!AuiPageReadiness.ready(screen.getLinkedDocument())||MagicSigilRenderer.lastTarget()==null)return;
            verify();stage++;
            if(stage==10){evidence.add("SIGIL_ALPHA_PASS 10 final-compositor cases; two backgrounds, two window sizes, tiers 1/8 and live animation");finish(null);}
            else configure();
        }catch(Throwable failure){finish(failure);}
    }
    private static void configure(){
        var mc=Minecraft.getInstance();int[] size=SIZES[Math.min(stage/4,1)];
        org.lwjgl.sdl.SDLVideo.SDL_RestoreWindow(mc.getWindow().handle());mc.getWindow().setWindowed(size[0],size[1]);mc.options.guiScale().set(size[2]);mc.resizeGui();
        screen.background=COLORS[stage>=8?0:stage%2];screen.tier=stage>=8?8:(stage/2)%2==0?1:8;
        if(stage==8&&!AuiMenuSettings.motion())AuiMenuSettings.toggleMotion();
        if(mc.gui.screen()!=screen)mc.gui.setScreen(screen);AuiScreenTransitions.finish();entered=ticks;
    }
    private static void verify()throws Exception{
        var mc=Minecraft.getInstance();var source=MagicSigilRenderer.lastTarget();var state=MagicSigilRenderer.lastState();
        int[] raw=AuiInkSelfTest.readback(source,OUT.resolve("sigil-source-"+stage+".png"));
        var main=mc.gameRenderer.mainRenderTarget();int[] frame=AuiInkSelfTest.readbackFrame(main,OUT.resolve("sigil-frame-"+stage+".png"));
        double sx=main.width/(double)mc.getWindow().getGuiScaledWidth(),sy=main.height/(double)mc.getWindow().getGuiScaledHeight();
        int count=0,worst=0;
        for(double v:new double[]{.03,.97})for(double u:new double[]{.03,.97}){
            int pixel=raw[(int)(v*(source.height-1))*source.width+(int)(u*(source.width-1))];
            if((pixel>>>24)!=0)throw new AssertionError("Sigil source has an opaque corner: "+Integer.toHexString(pixel));
            int x=(int)((state.x0()+(state.x1()-state.x0())*u)*sx),y=(int)((state.y0()+(state.y1()-state.y0())*v)*sy);
            int controlX=(int)((state.x0()-6)*sx),actual=frame[y*main.width+x],background=frame[y*main.width+controlX];
            int error=Math.max(Math.abs((actual>>16&255)-(background>>16&255)),Math.max(Math.abs((actual>>8&255)-(background>>8&255)),Math.abs((actual&255)-(background&255))));
            worst=Math.max(worst,error);if(error>4)count++;
        }
        String detail="stage="+stage+" tier="+screen.tier+" width="+main.width+" sourceAlpha=0 opaqueCorners="+count+" worstRgbError="+worst;
        evidence.add("SIGIL_COMPOSITE "+detail);
        if(count>0)throw new AssertionError("Black/opaque preview rectangle in final GUI: "+detail);
        if(Arrays.stream(raw).filter(p->(p>>>24)>20).count()<100)throw new AssertionError("Transparent preview lost the 3D model");
        if(stage==8){animationBefore=raw;animationClock=state.clock();}
        if(stage==9){
            if(state.clock()<=animationClock||Arrays.equals(animationBefore,raw))throw new AssertionError("Transparent 3D preview stopped animating");
            evidence.add("SIGIL_ANIMATION_PASS same spell, changing GPU frames, transparent corners retained");
            verifyFrameCapture();
        }
    }
    private static void verifyFrameCapture()throws Exception{
        var target=new com.mojang.blaze3d.pipeline.TextureTarget("Sigil frame capture regression",4,4,com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM,null);
        try{
            var encoder=com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorTexture(target.getColorTexture(),new org.joml.Vector4f(.2f,.4f,.6f,0));encoder.submit();
            var file=OUT.resolve("frame-capture-alpha-zero.png");int[] raw=AuiInkSelfTest.readbackFrame(target,file);
            var image=javax.imageio.ImageIO.read(file.toFile());
            for(int i=0;i<raw.length;i++){
                if(raw[i]!=0x00336699||image.getRGB(i%4,i/4)!=0xff336699)
                    throw new AssertionError("Frame capture discarded valid RGB with zero alpha");
            }
            evidence.add("SIGIL_FRAME_CAPTURE_PASS opaque PNG preserves raw world RGB with alpha zero");
        }finally{target.destroyBuffers();}
    }
    private static void finish(Throwable failure){
        done=true;if(failure!=null){failure.printStackTrace();evidence.add("FAIL "+failure);}
        if(AuiMenuSettings.motion())AuiMenuSettings.toggleMotion();
        try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),String.join("\n",evidence)+"\n");}catch(Exception error){error.printStackTrace();}
        Minecraft.getInstance().stop();
    }
    private static final class Fixture extends Screen implements AuiLinkedScreen {
        private AuiMagicPage page;
        int background,tier;
        Fixture(){super(Component.literal("Sigil alpha regression"));}
        @Override protected void init(){if(page==null)page=new AuiMagicPage();else page.resize();}
        @Override public Document getLinkedDocument(){return page==null?null:page.document();}
        @Override public void removed(){if(page!=null){page.close();page=null;}}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
            g.fill(0,0,width,height,background);g.nextStratum();
            var slots=List.of(new NativeHud.Slot(tier==8?"末日红莲":"火矢","就绪",0xffff923f,tier,tier==8?160:20,0,0,true,true),new NativeHud.Slot("未学习","",0xff989898,0,0,0,0,false,false),new NativeHud.Slot("未学习","",0xff989898,0,0,0,0,false,false));
            page.render(g,x,y,new NativeHud.Magic("奥术编织","魔力","","",230,230,0,slots,0,0),0,"");
        }
    }
}
