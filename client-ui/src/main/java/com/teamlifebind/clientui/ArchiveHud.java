package com.teamlifebind.clientui;

import com.teamlifebind.aui.client.gui.ApricityGuiLayers;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;
import com.teamlifebind.aui.element.Item;
import com.teamlifebind.aui.render.ScreenDocumentCache;
import com.teamlifebind.common.NativeHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** A single transparent, read-only HTML HUD. No simulation, packets or inventory changes. */
public final class ArchiveHud {
    private static Document page;
    private static final Map<String,ItemStack> items=new HashMap<>();
    private static long combatUpdated;
    private static long magicUpdated;
    private static long generation=-1;
    private ArchiveHud(){}
    record Rail(double top,double bottom){}
    static Rail rightRail(double viewportHeight){
        if(page==null||!visible())return new Rail(28,viewportHeight-24);
        try(var ignored=Document.withContext(page)){
            var effects=page.getElementById("effects");double top=effects.getTextContent().isBlank()?28:effects.getBoundingClientRect().bottom+10;
            return new Rail(top,page.getElementById("vitals").getBoundingClientRect().top-10);
        }
    }
    static double chatBottom(double viewportHeight){
        if(page==null)return 96;
        page.applyViewport(false);
        try(var ignored=Document.withContext(page)){
            double bottom=96;
            if(System.nanoTime()-magicUpdated<180_000_000L)bottom=Math.max(bottom,viewportHeight-page.getElementById("arcana").getBoundingClientRect().top+12);
            var combat=page.getElementById("combat");
            if(System.nanoTime()-combatUpdated<180_000_000L&&!combat.getAttribute("style").contains("display:none"))bottom=Math.max(bottom,viewportHeight-combat.getBoundingClientRect().top+12);
            return bottom;
        }
    }
    private static boolean visible(){var mc=Minecraft.getInstance();return ArchiveTheme.enabled()&&mc.player!=null&&mc.level!=null&&!mc.gui.hud.isHidden()&&(mc.gui.screen()==null||mc.gui.screen() instanceof HudEditorScreen)&&mc.gui.overlay()==null&&!mc.player.isSpectator();}
    private static void ensure(){
        if(page==null){page=AuiClient.open("teamlifebind/hud.html");generation=-1;}
        if(generation==page.getRefreshGeneration())return;
        generation=page.getRefreshGeneration();items.clear();
        try(var ignored=Document.withContext(page)){
            var bar=page.getElementById("quickbar");for(int i=0;i<9;i++){
                var slot=Element.init(page.createElement("div"));slot.setAttribute("class","quick-slot");slot.setAttribute("id","quick-"+i);
                var icon=Element.init(page.createElement("div"));icon.setAttribute("class","quick-icon");icon.setAttribute("id","quick-icon-"+i);slot.appendChild(icon);
                var count=Element.init(page.createElement("span"));count.setAttribute("class","quick-count");count.setAttribute("id","quick-count-"+i);slot.appendChild(count);
                var key=Element.init(page.createElement("span"));key.setAttribute("class","quick-key");key.setAttribute("id","quick-key-"+i);slot.appendChild(key);bar.appendChild(slot);
            }
            text("health-label",ArchiveTheme.tr("hud.health"));text("food-label",ArchiveTheme.tr("hud.food"));
        }
    }
    static void tick(){
        var mc=Minecraft.getInstance();
        if(page!=null&&(mc.player==null||mc.level==null||!ArchiveTheme.enabled())){page.remove();page=null;items.clear();return;}
        if(page!=null){page.body.setAttribute("style",visible()?"":"display:none");if(System.nanoTime()-combatUpdated>180_000_000L)attr("combat","style","display:none");if(System.nanoTime()-magicUpdated>180_000_000L){attr("arcana","style","display:none");page.body.setAttribute("data-magic","false");}}
    }
    public static boolean render(GuiGraphicsExtractor g){
        if(!visible()){if(AuiScreenTransitions.active())ApricityGuiLayers.submitUi(g);return false;}ensure();
        var mc=Minecraft.getInstance();var player=mc.player;
        try(var ignored=Document.withContext(page)){
            for(int i=0;i<9;i++){
                var item=player.getInventory().getItem(i);item("quick-icon-"+i,item);
                text("quick-count-"+i,item.getCount()>1?String.valueOf(item.getCount()):"");
                text("quick-key-"+i,mc.options.keyHotbarSlots[i].getTranslatedKeyMessage().getString());
                attr("quick-"+i,"class","quick-slot"+(player.getInventory().getSelectedSlot()==i?" selected":""));
            }
            item("offhand",player.getOffhandItem());
            attr("offhand","style",player.getOffhandItem().isEmpty()?"display:none":"");
            double health=player.getHealth(),maximum=player.getMaxHealth();
            text("health-value",Math.round(health)+(player.getAbsorptionAmount()>0?" + "+Math.round(player.getAbsorptionAmount()):"")+" / "+Math.round(maximum));
            width("health-fill",health/Math.max(1,maximum));width("food-fill",player.getFoodData().getFoodLevel()/20.0);
            text("food-value",player.getFoodData().getFoodLevel()+" / 20");
            String protection=ArchiveTheme.tr("hud.armor",player.getArmorValue())+(player.getAirSupply()<player.getMaxAirSupply()?" · "+ArchiveTheme.tr("hud.air",Math.round(player.getAirSupply()*100F/Math.max(1,player.getMaxAirSupply()))):"");
            if(player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity vehicle)protection+=" · "+vehicle.getDisplayName().getString()+" "+Math.round(vehicle.getHealth())+" / "+Math.round(vehicle.getMaxHealth());
            text("protection",protection);text("quick-name",player.getMainHandItem().isEmpty()?"":player.getMainHandItem().getHoverName().getString());
            attr("jump-track","style",player.jumpableVehicle()==null?"display:none":"");width("jump-fill",player.getJumpRidingScale());
            text("experience",player.experienceLevel>0?"LV. "+player.experienceLevel:"");width("experience-fill",player.experienceProgress);
            String effects=String.join("  ·  ",player.getActiveEffects().stream().map(e->e.getEffect().value().getDisplayName().getString()+" "+Math.max(0,e.getDuration()/20)+"s").toList());text("effects",effects);
            page.applyViewport(false);HudLayout.apply(page);ScreenDocumentCache.attach(page);ApricityGuiLayers.submitUi(g);
        }return true;
    }
    public static void magic(GuiGraphicsExtractor g,NativeHud.Magic model){
        if(!visible())return;ensure();magicUpdated=System.nanoTime();
        try(var ignored=Document.withContext(page)){
            page.body.setAttribute("data-magic","true");attr("arcana","style","");
            text("arcana-label",model.manaLabel());text("arcana-value",Math.round(model.mana())+" / "+model.maximum());
            width("arcana-fill",model.mana()/Math.max(1,model.maximum()));
            for(int i=0;i<model.slots().size();i++){
                var slot=model.slots().get(i);attr("arcana-slot-"+i,"class","arcana-spell"+(model.selected()==i?" selected":"")+(slot.learned()?"":" empty"));
                text("arcana-name-"+i,slot.name());text("arcana-state-"+i,slot.state());
                width("arcana-cooldown-"+i,slot.cooldown()/(double)Math.max(1,slot.cooldownTotal()));
            }
            attr("arcana-cast","style",model.castRemaining()>0?"":"display:none");text("arcana-cast-label",model.castLabel()+"  "+NativeHud.seconds(model.castRemaining()));width("arcana-cast-fill",1-model.castRemaining()/(double)Math.max(1,model.castTotal()));
        }
    }
    public static void weapon(GuiGraphicsExtractor g,NativeHud.Weapon model){
        if(!visible())return;ensure();combatUpdated=System.nanoTime();
        try(var ignored=Document.withContext(page)){
            attr("combat","class","combat weapon");attr("combat","style","");
            text("combat-kicker",model.mode());text("combat-name",model.name());text("combat-value",!model.confirmed()?"--":model.infinite()?"∞":model.ammo()+" / "+model.capacity());
            attr("cast","style","display:none");attr("combat","data-warning",model.warning()?"true":"false");
            text("combat-copy",model.state()+" · "+model.hint());text("combat-slots",model.infinite()?"∞ "+model.reserve():model.reserve());
            width("combat-fill",model.reloadRemaining()>0?1-model.reloadRemaining()/(double)Math.max(1,model.reloadTotal()):model.infinite()?model.heat()/100.0:model.ammo()/(double)Math.max(1,model.capacity()));
        }
    }
    private static void text(String id,String value){var e=page.getElementById(id);if(!value.equals(e.getTextContent()))e.setTextContent(value);}
    private static void attr(String id,String key,String value){var e=page.getElementById(id);if(!value.equals(e.getAttribute(key)))e.setAttribute(key,value);}
    private static void width(String id,double value){attr(id,"style","width:"+Math.clamp(value*100,0,100)+"%");}
    private static void item(String id,ItemStack value){
        var before=items.get(id);if(before!=null&&ItemStack.matches(before,value))return;items.put(id,value.copy());
        var host=page.getElementById(id);host.setTextContent("");if(!value.isEmpty()){var icon=new Item(page);var stack=value.copy();stack.setCount(1);icon.setIngredientStack(stack);icon.setAttribute("style","width:100%;height:100%");host.appendChild(icon);}
    }
}
