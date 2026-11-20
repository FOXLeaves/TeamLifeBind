package com.teamlifebind.clientui;
import com.teamlifebind.aui.client.gui.ApricityGuiLayers;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.render.ScreenDocumentCache;
import com.teamlifebind.common.NativeHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Locale;

/** Live three-slot spell selection. Selection is not a cast and never spends mana. */
public final class AuiMagicPage implements AutoCloseable {
    private final Document page=AuiClient.open("teamlifebind/magic.html");
    private double lastX=Double.NaN,lastY=Double.NaN;
    private long generation=-1;
    private final boolean[] learned=new boolean[3];
    private final long started=System.nanoTime();
    public AuiMagicPage() {
        bind();
    }
    private void bind(){
        generation=page.getRefreshGeneration();
        try(var ignored=Document.withContext(page)) {
            text("magic-heading",ArchiveTheme.tr("magic.title"));text("magic-intro",ArchiveTheme.tr("magic.intro"));
            text("magic-memory",ArchiveTheme.tr("magic.memory"));text("magic-mana-label",ArchiveTheme.tr("magic.mana"));
            text("magic-release",ArchiveTheme.tr("magic.release"));
            // This DOM node is only a layout anchor; the native GPU preview supplies every visible pixel.
            page.querySelector(".magic-sigil").setAttribute("style","opacity:0;background:transparent;border:0");
        }
    }
    private void text(String id,String value){var e=page.getElementById(id);if(!e.getTextContent().equals(value))e.setTextContent(value);}
    private void attr(String id,String key,String value){var e=page.getElementById(id);if(!value.equals(e.getAttribute(key)))e.setAttribute(key,value);}
    public int render(GuiGraphicsExtractor g,int x,int y,NativeHud.Magic model,int selected,String instruction) {
        page.applyViewport(false);
        if(generation!=page.getRefreshGeneration())bind();
        if(AuiPageReadiness.placeholder(g,page))return selected;
        try(var ignored=Document.withContext(page)) {
            for(int i=0;i<3;i++)learned[i]=model.slots().get(i).learned();
            boolean first=!Double.isFinite(lastX),moved=!first&&Math.hypot(x-lastX,y-lastY)>=3;
            if(first||moved){lastX=x;lastY=y;}
            if(moved){int next=selectionAt(x,y);if(next>=0)selected=next;}
            for(int i=0;i<3;i++) {
                var slot=model.slots().get(i);
                attr("magic-slot-"+i,"class","spell-memory"+(selected==i?" selected":"")+(slot.learned()?"":" empty"));
                text("magic-name-"+i,slot.name());text("magic-state-"+i,slot.state());
                text("magic-cost-"+i,slot.learned()?ArchiveTheme.tr("magic.cost",slot.manaCost(),slot.tier()):ArchiveTheme.tr("magic.unlearned"));
                int ratio=slot.cooldownTotal()<=0?0:Math.clamp(Math.round(slot.cooldown()*100F/slot.cooldownTotal()),0,100);
                attr("magic-cooldown-"+i,"style","width:"+ratio+"%");
            }
            text("magic-value",Math.round(model.mana())+" / "+model.maximum());
            attr("magic-reserve","style","width:"+Math.clamp(model.mana()*100/Math.max(1,model.maximum()),0,100)+"%");
            text("magic-controls",instruction);
            if(!String.valueOf(selected).equals(page.body.getAttribute("data-selected")))page.body.setAttribute("data-selected",String.valueOf(selected));
            ScreenDocumentCache.attach(page);ApricityGuiLayers.submitUi(g);
            var preview=page.querySelector(".magic-sigil").getBoundingClientRect();
            var top=page.documentToGuiPosition(new com.teamlifebind.aui.layout.Position(preview.left,preview.top));
            var bottom=page.documentToGuiPosition(new com.teamlifebind.aui.layout.Position(preview.right,preview.bottom));
            var spell=model.slots().get(Math.clamp(selected,0,2));int color=spell.color();
            AuiGraphics.submit(g,new MagicSigilState((int)top.x,(int)top.y,(int)Math.ceil(bottom.x),(int)Math.ceil(bottom.y),
                AuiMenuSettings.motion()?(System.nanoTime()-started)/1_000_000_000F:0,
                ((color>>16)&255)/255F,((color>>8)&255)/255F,(color&255)/255F,
                (spell.name().hashCode()&1023)/163F,spell.tier()));
            return selected;
        }
    }
    public Document document(){return page;}
    public void keyboardSelection(){lastX=lastY=Double.NaN;}
    public int selectionAt(double x,double y){
        page.applyViewport(false);if(!AuiPageReadiness.ready(page))return -1;
        var point=page.guiToDocumentPosition(new com.teamlifebind.aui.layout.Position(x,y));
        for(int i=0;i<3;i++){
            var box=page.getElementById("magic-slot-"+i).getBoundingClientRect();
            if(learned[i]&&point.x>=box.left&&point.x<box.right&&point.y>=box.top&&point.y<box.bottom)return i;
        }
        return -1;
    }
    public void resize(){page.applyViewport(true);}
    @Override public void close(){page.remove();}
}
