package com.teamlifebind.clientui;

import java.util.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Small presentation decisions based on native identities, never translated display strings. */
final class ArchiveNavigation {
    static final List<String> SETTING_GROUPS=List.of("display","controls","interface","other");
    private ArchiveNavigation(){}
    static String view(Screen screen){
        return switch(screen.getClass().getSimpleName()){
            case "OptionsScreen"->"settings-home";
            case "SelectWorldScreen"->"worlds";
            case "JoinMultiplayerScreen"->"servers";
            case "LanguageSelectScreen"->"languages";
            case "CreateWorldScreen"->"create-world";
            case "PauseScreen","PauseFixture"->"pause";
            default->screen instanceof net.minecraft.client.gui.screens.options.OptionsSubScreen?"settings":screen.getClass().getSimpleName().matches(".*(Confirm|Alert|Error|Message|Notice).*Screen")?"dialog":"default";
        };
    }
    static Set<String> keys(Component value){
        var result=new LinkedHashSet<String>();
        if(value.getContents() instanceof TranslatableContents text){result.add(text.getKey());for(var argument:text.getArgs())if(argument instanceof Component nested)result.addAll(keys(nested));}
        for(var sibling:value.getSiblings())result.addAll(keys(sibling));return result;
    }
    static boolean titlePrimary(ArchiveModel.Control control){
        return !journeyAction(control).isEmpty();
    }
    static String journeyAction(ArchiveModel.Control control) {
        if (!(control.source() instanceof AbstractWidget widget)) return "";
        for (String key : keys(widget.getMessage())) {
            switch (key) {
                case "menu.singleplayer": return "singleplayer";
                case "menu.multiplayer": return "multiplayer";
                case "menu.online": return "realms";
                default: break;
            }
        }
        return "";
    }
    static boolean dock(String view,ArchiveModel.Control control){
        if(!control.kind().equals("action"))return false;
        if(view.equals("dialog"))return control.list()==null;
        if(view.equals("worlds")||view.equals("servers"))return control.list()==null;
        return control.source() instanceof AbstractWidget widget&&keys(widget.getMessage()).stream().anyMatch(k->Set.of("gui.done","gui.cancel","gui.back","selectWorld.create","menu.returnToMenu","menu.disconnect","menu.quit").contains(k));
    }
    static String priority(ArchiveModel.Control control){
        if(control.source() instanceof AbstractWidget widget){var keys=keys(widget.getMessage());
            if(keys.stream().anyMatch(k->Set.of("menu.returnToMenu","menu.disconnect","menu.quit").contains(k)))return "danger";
            if(keys.stream().anyMatch(k->Set.of("menu.returnToGame","selectWorld.create","selectWorld.select","selectServer.select").contains(k)))return "primary";
        }return "standard";
    }
    static String group(ArchiveModel.Control control){
        if(!(control.source() instanceof AbstractWidget widget))return "other";
        var keys=keys(widget.getMessage());
        if(keys.stream().anyMatch(k->k.contains("video")||k.contains("fov")||k.contains("graphics")||k.contains("fullscreen")))return "display";
        if(keys.stream().anyMatch(k->k.contains("sound")||k.contains("controls")||k.contains("mouse")||k.contains("keybind")))return "controls";
        if(keys.stream().anyMatch(k->k.contains("skin")||k.contains("language")||k.contains("chat")||k.contains("accessibility")||k.contains("resourcepack")))return "interface";
        return "other";
    }
}
