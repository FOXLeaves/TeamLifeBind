package com.teamlifebind.clientui;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.recipebook.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import java.lang.reflect.Field;

/** Recipe navigation is not a list of generic blank image buttons. Native callbacks stay authoritative. */
final class ArchiveRecipes {
    private static final Field SELECTED=selectedField();
    private ArchiveRecipes(){}
    private static Field selectedField(){try{var field=RecipeBookTabButton.class.getDeclaredField("selected");field.setAccessible(true);return field;}catch(ReflectiveOperationException e){throw new IllegalStateException("Missing 26.3 recipe-tab state",e);}}
    static void project(Screen owner,ArchiveModel model){
        if(!(owner instanceof MenuAccess<?> access)||!(access.getMenu() instanceof RecipeBookMenu))return;
        for(int i=0;i<model.controls.size();i++){
            var c=model.controls.get(i);String kind=null,label=c.label();
            if(c.source() instanceof RecipeBookTabButton tab){
                kind="recipe-category";
                var category=tab.getCategory();String key=category instanceof RecipeBookCategory recipe?BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(recipe).getPath():"all";
                label=ArchiveTheme.tr("recipe.category."+key);
            }else if(c.source() instanceof RecipeButton)kind="recipe-entry";
            else if(model.relays.get(c.source()) instanceof RecipeBookComponent<?>){
                if(c.source() instanceof CycleButton<?>){kind="recipe-filter";label=ArchiveTheme.tr("recipe.craftable");}
                else if(c.source() instanceof ImageButton)kind="recipe-page";
            }else if(c.source() instanceof ImageButton button&&button.getMessage().getString().isBlank()){
                kind="recipe-toggle";label=ArchiveTheme.tr("work.recipes");
            }
            if(kind!=null)model.controls.set(i,new ArchiveModel.Control(c.source(),c.list(),kind,label,"",c.active()));
        }
    }
    static boolean selected(ArchiveModel.Control control){
        if(control.source() instanceof CycleButton<?> cycle&&control.kind().equals("recipe-filter"))return Boolean.TRUE.equals(cycle.getValue());
        if(control.source() instanceof RecipeBookTabButton tab)try{return SELECTED.getBoolean(tab);}catch(IllegalAccessException e){throw new IllegalStateException(e);}
        return false;
    }
    static String host(ArchiveModel.Control control){return switch(control.kind()){
        case "recipe-category"->"recipe-categories";
        case "recipe-entry"->"recipe-cards";
        case "recipe-page"->"recipe-pages";
        default->"controls";
    };}
}
