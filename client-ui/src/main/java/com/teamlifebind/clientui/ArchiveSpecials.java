package com.teamlifebind.clientui;

import com.mojang.blaze3d.platform.InputConstants;
import com.teamlifebind.clientui.mixin.ArchiveMerchantAccess;
import com.teamlifebind.clientui.mixin.ArchiveSignAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import java.util.*;

/** Controls that stock screens draw as hit regions instead of registering widgets. */
final class ArchiveSpecials {
    enum HudEditorAction { INSTANCE;
        void activate(){var mc=Minecraft.getInstance();if(mc.level!=null)mc.gui.setScreen(new HudEditorScreen(mc.gui.screen()));}
    }
    record AdvancementEntry(Screen owner,net.minecraft.advancements.AdvancementHolder holder){
        ItemStack icon(){return holder.value().display().map(d->d.icon().create()).orElse(ItemStack.EMPTY);}
    }
    record CreativeTab(Screen owner,net.minecraft.world.item.CreativeModeTab tab){
        void activate(){if(Minecraft.getInstance().gui.screen()==owner)((com.teamlifebind.clientui.mixin.ArchiveCreativeAccess)owner).tlb$tab(tab);}
    }
    record Choice(Screen owner,int index,String label,String detail,ItemStack icon,boolean available){
        @Override public boolean equals(Object other){return other instanceof Choice c&&c.owner==owner&&c.index==index;}
        @Override public int hashCode(){return System.identityHashCode(owner)*31+index;}
        void activate(){
            var mc=Minecraft.getInstance();if(mc.gui.screen()!=owner||mc.player==null||!(owner instanceof MenuAccess<?> access)||mc.player.containerMenu!=access.getMenu())return;
            var menu=access.getMenu();
            if(menu instanceof MerchantMenu trade&&owner instanceof MerchantScreen){
                if(index<0||index>=trade.getOffers().size()||trade.getOffers().get(index).isOutOfStock())return;
                var source=(ArchiveMerchantAccess)owner;source.tlb$offer(index);source.tlb$confirmOffer();
            }else if(mc.gameMode!=null&&menu.clickMenuButton(mc.player,index))mc.gameMode.handleInventoryButtonClick(menu.containerId,index);
        }
    }
    record SignLine(Screen owner,int index){
        String value(){return ((ArchiveSignAccess)owner).tlb$messages()[index];}
        void set(String value){
            if(Minecraft.getInstance().gui.screen()!=owner)return;
            ((ArchiveSignAccess)owner).tlb$line(index);
            owner.keyPressed(new KeyEvent(InputConstants.KEY_A,0,InputConstants.MOD_CONTROL));
            value.codePoints().forEach(cp->owner.charTyped(new CharacterEvent(cp)));
        }
    }
    static void append(Screen owner,List<ArchiveModel.Control> result){
        ArchiveGameModes.append(owner,result);
        if(owner instanceof net.minecraft.client.gui.screens.PauseScreen&&Minecraft.getInstance().level!=null)
            result.add(new ArchiveModel.Control(HudEditorAction.INSTANCE,null,"action",ArchiveTheme.tr("editor.title"),ArchiveTheme.tr("editor.hint"),true));
        if(owner instanceof net.minecraft.client.gui.screens.advancements.AdvancementsScreen&&Minecraft.getInstance().getConnection()!=null){
            var advancements=Minecraft.getInstance().getConnection().getAdvancements();
            for(var entry:advancements.tree().nodes())entry.advancement().display().ifPresent(display->{
                var progress=advancements.progress().get(entry.holder());
                String state=progress==null?"0%":Math.round(progress.getPercent()*100)+"%";
                String parent=entry.parent()==null?"":entry.parent().advancement().display().map(d->d.title().getString()+" › ").orElse("");
                result.add(new ArchiveModel.Control(new AdvancementEntry(owner,entry.holder()),null,"achievement",display.title().getString(),parent+state+"\n"+display.description().getString(),true));
            });
        }
        if(owner instanceof CreativeModeInventoryScreen)for(var tab:net.minecraft.world.item.CreativeModeTabs.tabs())if(tab.shouldDisplay()&&tab.getType()!=net.minecraft.world.item.CreativeModeTab.Type.SEARCH)result.add(new ArchiveModel.Control(new CreativeTab(owner,tab),null,"choice",tab.getDisplayName().getString(),"",true));
        if(owner instanceof AbstractSignEditScreen)for(int i=0;i<4;i++)result.add(new ArchiveModel.Control(new SignLine(owner,i),null,"input",ArchiveTheme.tr("control.line",i+1),"",true));
        if(!(owner instanceof MenuAccess<?> access))return;
        var menu=access.getMenu();
        if(menu instanceof MerchantMenu trade){
            result.removeIf(c->c.source().getClass().getSimpleName().equals("TradeOfferButton"));
            for(int i=0;i<trade.getOffers().size();i++){
                var offer=trade.getOffers().get(i);var output=offer.getResult();var cost=offer.getCostA();
                String detail=cost.getCount()+" × "+cost.getHoverName().getString();
                if(!offer.getCostB().isEmpty())detail+=" + "+offer.getCostB().getCount()+" × "+offer.getCostB().getHoverName().getString();
                add(result,new Choice(owner,i,output.getHoverName().getString(),detail,output,!offer.isOutOfStock()));
            }
        }else if(menu instanceof EnchantmentMenu enchant){
            for(int i=0;i<3;i++)add(result,new Choice(owner,i,ArchiveTheme.tr("control.enchant",i+1),ArchiveTheme.tr("control.level",enchant.costs[i]),ItemStack.EMPTY,enchant.costs[i]>0));
        }else if(menu instanceof StonecutterMenu cutter){
            var mc=Minecraft.getInstance();var recipes=cutter.getVisibleRecipes().entries();
            for(int i=0;i<recipes.size();i++){
                ItemStack icon=mc.level==null?ItemStack.EMPTY:recipes.get(i).recipe().optionDisplay().resolveForFirstStack(SlotDisplayContext.fromLevel(mc.level));
                add(result,new Choice(owner,i,icon.isEmpty()?ArchiveTheme.tr("control.recipe",i+1):icon.getHoverName().getString(),"",icon,cutter.hasInputItem()));
            }
        }else if(menu instanceof LoomMenu loom){
            var patterns=loom.getSelectablePatterns();for(int i=0;i<patterns.size();i++){
                var pattern=patterns.get(i).value();String key=pattern.translationKey()+".black",name=Component.translatable(key).getString();
                if(name.equals(key))name=pattern.assetId().getPath().replace('_',' ');
                add(result,new Choice(owner,i,name,ArchiveTheme.tr("control.pattern"),ItemStack.EMPTY,true));
            }
        }
    }
    private static void add(List<ArchiveModel.Control> result,Choice choice){result.add(new ArchiveModel.Control(choice,null,"choice",choice.label(),choice.detail(),choice.available()));}
}
