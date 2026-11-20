package com.teamlifebind.clientui;

import com.teamlifebind.aui.client.gui.ApricityGuiLayers;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;
import com.teamlifebind.aui.layout.Position;
import com.teamlifebind.aui.render.ScreenDocumentCache;
import com.teamlifebind.aui.screen.AuiLinkedScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Client-only editing transaction; no game data, inventory action or server permission is involved. */
public final class HudEditorScreen extends Screen implements AuiLinkedScreen {
    private final Screen parent;
    private final Map<String,HudLayoutPreferences.Placement> initial=HudLayout.preferences().snapshot();
    private final EnumMap<HudLayout.Part,Element> handles=new EnumMap<>(HudLayout.Part.class);
    private Document page;
    private HudLayout.Part selected=HudLayout.Part.HOTBAR;
    private boolean saved,dragging;
    private Position dragStart;
    private HudLayoutPreferences.Placement startPlacement;
    private HudLayout.Bounds startBounds;
    private long generation=-1;
    public HudEditorScreen(Screen parent){super(Component.translatable("ui.teamlifebind.archive.editor.title"));this.parent=parent;}
    @Override protected void init(){if(page==null)page=AuiClient.open("teamlifebind/hud-editor.html");else page.applyViewport(true);bind();}
    private void bind(){
        generation=page.getRefreshGeneration();handles.clear();
        try(var ignored=Document.withContext(page)){
            text("editor-title",ArchiveTheme.tr("editor.title"));text("editor-hint",ArchiveTheme.tr("editor.hint"));
            for(String action:List.of("save","cancel","reset","reset-all"))text("editor-"+action,ArchiveTheme.tr("editor."+action));
            var list=page.getElementById("editor-parts");list.setTextContent("");var canvas=page.getElementById("editor-handles");canvas.setTextContent("");
            for(var part:HudLayout.Part.values()){
                var button=node("button","editor-part",part.label());button.setAttribute("id","edit-part-"+part.name());list.appendChild(button);
                var handle=node("div","hud-handle","");handle.setAttribute("id","edit-handle-"+part.name());handle.appendChild(node("span","hud-handle-label",part.label()));canvas.appendChild(handle);handles.put(part,handle);
            }
        }
    }
    private Element node(String tag,String css,String value){var node=Element.init(page.createElement(tag));node.setAttribute("class",css);if(!value.isEmpty())node.setTextContent(value);return node;}
    private void text(String id,String value){var node=page.getElementById(id);if(!node.getTextContent().equals(value))node.setTextContent(value);}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick){
        page.applyViewport(false);if(generation!=page.getRefreshGeneration())bind();
        if(AuiPageReadiness.placeholder(graphics,page))return;
        try(var ignored=Document.withContext(page)){
            for(var part:HudLayout.Part.values()){
                var box=HudLayout.bounds(part,page);var handle=handles.get(part);
                handle.setAttribute("class","hud-handle"+(part==selected?" selected":"")+(box.placeholder()?" placeholder":""));
                handle.setAttribute("style",box.placeholder()&&part!=selected?"display:none":"left:"+box.left()+"px;top:"+box.top()+"px;width:"+box.width()+"px;height:"+box.height()+"px");
                page.getElementById("edit-part-"+part.name()).setAttribute("class","editor-part"+(part==selected?" selected":""));
            }
            var placement=HudLayout.placement(selected,page);text("editor-selected",selected.label());text("editor-scale",Math.round(placement.scale()*100)+"%");
            ScreenDocumentCache.attach(page);ApricityGuiLayers.submitOverlay(graphics);
        }
    }
    private boolean hit(String id,Position point){var box=page.getElementById(id).getBoundingClientRect();return point.x>=box.left&&point.x<box.right&&point.y>=box.top&&point.y<box.bottom;}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(event.button()!=InputConstants.MOUSE_BUTTON_LEFT)return true;
        var point=page.guiToDocumentPosition(new Position(event.x(),event.y()));
        for(String action:List.of("save","cancel","reset","reset-all","minus","plus"))if(hit("editor-"+action,point)){action(action);return true;}
        if(hit("editor-parts",point)){for(var part:HudLayout.Part.values())if(hit("edit-part-"+part.name(),point)){selected=part;return true;}return true;}
        if(hit("editor-panel",point))return true;
        // The active handle wins overlapping preview areas; component buttons always remain reachable.
        if(hit("edit-handle-"+selected.name(),point)){begin(point);return true;}
        for(var part:HudLayout.Part.values())if(hit("edit-handle-"+part.name(),point)){selected=part;begin(point);return true;}
        return true;
    }
    private void begin(Position point){dragging=true;dragStart=point;startPlacement=HudLayout.placement(selected,page);startBounds=HudLayout.bounds(selected,page);}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(!dragging)return true;
        var point=page.guiToDocumentPosition(new Position(event.x(),event.y()));
        HudLayout.preferences().put(selected.id,new HudLayoutPreferences.Placement(
            startPlacement.x()+(point.x-dragStart.x)/Math.max(1,page.getViewportSize().width()-startBounds.width()),
            startPlacement.y()+(point.y-dragStart.y)/Math.max(1,page.getViewportSize().height()-startBounds.height()),startPlacement.scale()));
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){dragging=false;return true;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        var point=page.guiToDocumentPosition(new Position(x,y));
        if(hit("editor-parts",point)){page.getElementById("editor-parts").scrollBy(0,-dy*30);}
        else if(dy!=0)scale(dy>0?.05:-.05);
        return true;
    }
    private void scale(double amount){var current=HudLayout.placement(selected,page);HudLayout.preferences().put(selected.id,new HudLayoutPreferences.Placement(current.x(),current.y(),current.scale()+amount));}
    private void action(String action){
        switch(action){
            case "minus"->scale(-.05);case "plus"->scale(.05);
            case "reset"->HudLayout.preferences().remove(selected.id);
            case "reset-all"->HudLayout.preferences().clear();
            case "cancel"->onClose();
            case "save"->{try{HudLayout.preferences().save();saved=true;onClose();}catch(java.io.IOException failure){text("editor-hint",ArchiveTheme.tr("editor.save_failed"));}}
        }
    }
    @Override public boolean keyPressed(KeyEvent event){
        if(event.key()==InputConstants.KEY_ESCAPE){onClose();return true;}
        if(event.isConfirmation()){action("save");return true;}
        int x=event.key()==InputConstants.KEY_LEFT?-1:event.key()==InputConstants.KEY_RIGHT?1:0;
        int y=event.key()==InputConstants.KEY_UP?-1:event.key()==InputConstants.KEY_DOWN?1:0;
        if(x!=0||y!=0){var current=HudLayout.placement(selected,page);var box=HudLayout.bounds(selected,page);double step=event.hasShiftDown()?10:1;
            HudLayout.preferences().put(selected.id,new HudLayoutPreferences.Placement(current.x()+x*step/Math.max(1,page.getViewportSize().width()-box.width()),current.y()+y*step/Math.max(1,page.getViewportSize().height()-box.height()),current.scale()));return true;}
        return super.keyPressed(event);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void removed(){if(!saved)HudLayout.preferences().replace(initial);if(page!=null){page.remove();page=null;}super.removed();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public Document getLinkedDocument(){return page;}
}
