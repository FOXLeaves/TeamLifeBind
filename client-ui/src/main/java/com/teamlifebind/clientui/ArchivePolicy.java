package com.teamlifebind.clientui;

import java.lang.reflect.Modifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Remove the optional Friends feature at both its visible controls and navigation boundary. */
public final class ArchivePolicy {
    private ArchivePolicy(){}
    public static boolean blocked(Screen screen){return screen!=null&&screen.getClass().getName().startsWith("net.minecraft.client.gui.screens.friends.");}
    static boolean friendText(Component value){
        if(value.getContents() instanceof TranslatableContents text){
            String key=text.getKey();
            if(key.startsWith("gui.friends.")||key.startsWith("options.friendsList")||key.equals("options.allowFriendRequests")||key.equals("options.online.friends.header")||key.equals("key.friends"))return true;
            for(var argument:text.getArgs())if(argument instanceof Component nested&&friendText(nested))return true;
        }
        return value.getSiblings().stream().anyMatch(ArchivePolicy::friendText);
    }
    static boolean hidden(Object value){
        if(value==null)return false;
        String name=value.getClass().getName();
        if(value instanceof net.minecraft.client.gui.components.FriendsButton||name.startsWith("net.minecraft.client.gui.screens.friends."))return true;
        if(value instanceof AbstractWidget widget&&friendText(widget.getMessage()))return true;
        if(name.contains("KeyBindsList")||name.contains("OptionsList"))for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())for(var field:type.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers()))continue;
            try{field.setAccessible(true);if(field.get(value) instanceof KeyMapping key&&key.getName().equals("key.friends"))return true;}catch(ReflectiveOperationException|RuntimeException ignored){}
        }
        return false;
    }
    public static void stripFriends(Screen screen){
        for(var child:screen.children())if(child instanceof AbstractWidget widget&&hidden(widget)){widget.visible=false;widget.active=false;}
    }
}
