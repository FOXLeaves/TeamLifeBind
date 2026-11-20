package com.teamlifebind.clientui;

import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;
import com.teamlifebind.aui.layout.Position;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.client.Minecraft;

public final class HudLayout {
    public enum Part {
        HOTBAR("quickdock",392,64,.5,.96),MAGIC("arcana",272,130,.02,.96),WEAPON("combat",230,160,.02,.65),
        VITALS("vitals",206,120,.97,.85),STATUS("sidebar",240,250,.97,.25),NOTICES("notifications",300,90,.97,.04),
        CHAT("hud-chat",440,150,.02,.6),BOSSES("bosses",440,70,.5,.04),EFFECTS("effects",200,44,.97,.02),COUNTDOWN("countdown",300,220,.5,.4);
        public final String id;final double width,height,x,y;
        Part(String id,double width,double height,double x,double y){this.id=id;this.width=width;this.height=height;this.x=x;this.y=y;}
        public String label(){return ArchiveTheme.tr("editor.part."+id);}
    }
    public record Bounds(double left,double top,double width,double height,boolean placeholder){}
    private record Applied(String original,String rendered,double scale){}
    private static final Map<Element,Applied> applied=new WeakHashMap<>();
    private static final EnumMap<Part,WeakReference<Document>> sources=new EnumMap<>(Part.class);
    private static HudLayoutPreferences preferences;
    private HudLayout(){}
    public static HudLayoutPreferences preferences(){
        if(preferences==null)preferences=new HudLayoutPreferences(Minecraft.getInstance().gameDirectory.toPath().resolve("config/teamlifebind-hud-layout.properties"));
        return preferences;
    }
    public static boolean custom(Part part){return preferences().get(part.id)!=null;}
    public static void apply(Document document){
        if(document==null)return;
        try(var ignored=Document.withContext(document)){
            for(var part:Part.values()){
                var node=document.getElementById(part.id);if(node==null)continue;sources.put(part,new WeakReference<>(document));
                String current=Objects.requireNonNullElse(node.getAttribute("style"),"");var old=applied.get(node);var placement=preferences().get(part.id);
                if(placement==null){if(old!=null){if(current.equals(old.rendered()))node.setAttribute("style",old.original());applied.remove(node);}continue;}
                boolean stillApplied=old!=null&&current.equals(old.rendered());
                String original=stillApplied?old.original():current;double previousScale=stillApplied?old.scale():1;
                var box=node.getBoundingClientRect();double naturalWidth=box.width/previousScale,naturalHeight=box.height/previousScale;
                double scale=Math.min(placement.scale(),Math.min(document.getViewportSize().width()/Math.max(1,naturalWidth),document.getViewportSize().height()/Math.max(1,naturalHeight)));
                double w=naturalWidth*scale,h=naturalHeight*scale;
                double x=placement.x()*Math.max(0,document.getViewportSize().width()-w),y=placement.y()*Math.max(0,document.getViewportSize().height()-h);
                String style=original+";left:"+x+"px;top:"+y+"px;right:auto;bottom:auto;transform:scale("+scale+");transform-origin:top left;margin:0";
                if(!style.equals(current))node.setAttribute("style",style);applied.put(node,new Applied(original,style,scale));
            }
        }
    }
    public static Bounds bounds(Part part,Document editor){
        var reference=sources.get(part);var source=reference==null?null:reference.get();
        if(source!=null){var node=source.getElementById(part.id);if(node!=null&&!Objects.requireNonNullElse(node.getAttribute("style"),"").contains("display:none")){
            var box=node.getBoundingClientRect();if(box.width>1&&box.height>1){
                var a=editor.guiToDocumentPosition(source.documentToGuiPosition(new Position(box.left,box.top)));
                var b=editor.guiToDocumentPosition(source.documentToGuiPosition(new Position(box.right,box.bottom)));
                return new Bounds(a.x,a.y,b.x-a.x,b.y-a.y,false);
            }
        }}
        double vw=editor.getViewportSize().width(),vh=editor.getViewportSize().height();var saved=preferences().get(part.id);
        double naturalWidth=Math.min(part.width,vw*.8),naturalHeight=Math.min(part.height,vh*.5);
        double scale=Math.min(saved==null?1:saved.scale(),Math.min(vw/naturalWidth,vh/naturalHeight)),w=naturalWidth*scale,h=naturalHeight*scale;
        return new Bounds((saved==null?part.x:saved.x())*Math.max(0,vw-w),(saved==null?part.y:saved.y())*Math.max(0,vh-h),w,h,true);
    }
    public static HudLayoutPreferences.Placement placement(Part part,Document editor){
        var saved=preferences().get(part.id);if(saved!=null)return saved;
        var box=bounds(part,editor);return new HudLayoutPreferences.Placement(box.left()/Math.max(1,editor.getViewportSize().width()-box.width()),box.top()/Math.max(1,editor.getViewportSize().height()-box.height()),1);
    }
}
