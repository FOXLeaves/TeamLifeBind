package com.teamlifebind.clientui;

import com.teamlifebind.aui.init.*;
import com.teamlifebind.aui.layout.Size;
import com.teamlifebind.aui.parser.HTML;
import java.nio.file.*;
import java.util.*;

/** Shipping DOM/renderer layout, not a CSS string test: selection labels must never move the hotbar. */
public final class HudLayoutProbe {
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(args[0]);Path file=Path.of(args[1]).resolve("test-hud-layout.properties");Files.deleteIfExists(file);
        var preferences=new HudLayoutPreferences(file);var field=HudLayout.class.getDeclaredField("preferences");field.setAccessible(true);field.set(null,preferences);
        Size.setRootFontOverride(16.0);String html=Files.readString(assets.resolve("hud.html"));
        for(String name:List.of("hud.css","corners.css","motion.css"))html=html.replace("<link rel=\"stylesheet\" href=\""+name+"\">","<style>"+Files.readString(assets.resolve(name)).replaceAll("@font-face\\s*\\{[^}]*}","")+"</style>");
        for(int[] size:new int[][]{{1920,1080},{1152,720},{720,480},{576,384}}){
            String path="hud-placement-"+size[0]+".html";HTML.putTemple(path,html.replace("mode=browser,user-scalable=false","mode=fixed,width="+size[0]+",height="+size[1]+",scale=1,user-scalable=false"));
            var doc=Document.create(path);preferences.clear();
            try(var ignored=Document.withContext(doc)){
                var bar=doc.getElementById("quickbar");for(int i=0;i<9;i++){var slot=Element.init(doc.createElement("div"));slot.setAttribute("class","quick-slot");bar.appendChild(slot);}
                doc.getElementById("arcana").setAttribute("style","");doc.getElementById("arcana-value").setTextContent("100 / 100");
                doc.flushPendingStyleUpdates();doc.commitRenderState();
                var original=doc.getElementById("quickdock").getBoundingClientRect();var slots=doc.querySelectorAll(".quick-slot");
                if(Math.abs((original.left+original.right)/2-size[0]/2.0)>1)throw new AssertionError("Default nine-slot hotbar is not centered");
                if(Math.abs(original.width-(slots.getLast().getBoundingClientRect().right-slots.getFirst().getBoundingClientRect().left+10))>1)throw new AssertionError("Hotbar background extends beyond its nine cells: "+size[0]+" width="+original.width);
                double left=original.left,top=original.top;
                for(String label:List.of("", "A very long selected item name ".repeat(5),"短名称")){
                    doc.getElementById("quick-name").setTextContent(label);doc.getElementById("experience").setTextContent("LV. 99999");doc.flushPendingStyleUpdates();doc.commitRenderState();
                    var box=doc.getElementById("quickdock").getBoundingClientRect();if(Math.abs(left-box.left)>.1||Math.abs(top-box.top)>.1)throw new AssertionError("Hotbar moves when held-item or experience text changes");
                }
                preferences.put("quickdock",new HudLayoutPreferences.Placement(.6,.75,1.25));preferences.put("arcana",new HudLayoutPreferences.Placement(.12,.8,1.3));
                HudLayout.apply(doc);doc.commitRenderState();var first=doc.getElementById("arcana").getBoundingClientRect();
                for(int frame=0;frame<20;frame++){
                    // Exactly what the live mana updater does before the layout override is applied again.
                    doc.getElementById("arcana").setAttribute("style","");doc.getElementById("arcana-value").setTextContent(frame+" / 300");
                    HudLayout.apply(doc);doc.flushPendingStyleUpdates();doc.commitRenderState();var box=doc.getElementById("arcana").getBoundingClientRect();
                    if(Math.abs(box.width-first.width)>.1||Math.abs(box.left-first.left)>.1)throw new AssertionError("Per-frame updates compound the saved HUD scale/position");
                }
                preferences.put("quickdock",new HudLayoutPreferences.Placement(1,1,1.8));HudLayout.apply(doc);doc.commitRenderState();
                var large=doc.getElementById("quickdock").getBoundingClientRect();if(large.left<-.1||large.top<-.1||large.right>size[0]+.1||large.bottom>size[1]+.1)throw new AssertionError("Enlarged HUD falls outside the resized viewport");
                var saved=preferences.snapshot();preferences.save();if(!new HudLayoutPreferences(file).snapshot().equals(saved))throw new AssertionError("HUD edit does not survive a restart");
                preferences.clear();HudLayout.apply(doc);doc.commitRenderState();var reset=doc.getElementById("quickdock").getBoundingClientRect();
                if(Math.abs(reset.left-left)>.1||Math.abs(reset.top-top)>.1)throw new AssertionError("Reset does not restore the original HUD anchor");
                System.out.println("HUD_LAYOUT_PASS "+size[0]+" stableNineCells=true individualScale=true restart=true reset=true");
            }finally{doc.remove();}
        }
        Files.writeString(file,"quickdock.x=NaN\nquickdock.y=0\nquickdock.scale=1\narcana.x=3\narcana.y=-2\narcana.scale=9\n");
        var invalid=new HudLayoutPreferences(file);if(invalid.get("quickdock")!=null||invalid.get("arcana").x()!=1||invalid.get("arcana").y()!=0||invalid.get("arcana").scale()!=1.8)throw new AssertionError("Invalid saved layout is not bounded");
        Files.deleteIfExists(file);System.out.println("HUD_LAYOUT_PERSISTENCE_PASS bounded values; invalid entry isolated; real property-file round trip");
    }
}
