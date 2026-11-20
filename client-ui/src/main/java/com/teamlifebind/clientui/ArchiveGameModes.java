package com.teamlifebind.clientui;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Project the existing debug switcher; native F4 cycling, F3 release and permission checks remain in charge. */
final class ArchiveGameModes {
    // ItemStack icons are only safe after the client has bound its data components.
    private static final class NativeModes {
    static final Field CURRENT,NAME,STACK;
    static final Object[] MODES;
    static{
        try{
            CURRENT=GameModeSwitcherScreen.class.getDeclaredField("currentlyHovered");CURRENT.setAccessible(true);
            Class<?> icon=CURRENT.getType();MODES=icon.getEnumConstants();
            NAME=icon.getDeclaredField("name");NAME.setAccessible(true);STACK=icon.getDeclaredField("renderStack");STACK.setAccessible(true);
        }catch(ReflectiveOperationException problem){throw new ExceptionInInitializerError(problem);}
    }}
    record Choice(Screen owner,int index){
        void activate(){if(Minecraft.getInstance().gui.screen()==owner)try{NativeModes.CURRENT.set(owner,NativeModes.MODES[index]);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
        ItemStack icon(){try{return ((ItemStack)NativeModes.STACK.get(NativeModes.MODES[index])).copy();}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    }
    private ArchiveGameModes(){}
    static void append(Screen screen,List<ArchiveModel.Control> controls){
        if(!(screen instanceof GameModeSwitcherScreen))return;
        controls.clear();
        for(int i=0;i<NativeModes.MODES.length;i++)try{controls.add(new ArchiveModel.Control(new Choice(screen,i),null,"gamemode",((Component)NativeModes.NAME.get(NativeModes.MODES[i])).getString(),"",true));}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    static boolean selected(ArchiveModel.Control control){
        if(!(control.source() instanceof Choice choice))return false;
        try{return NativeModes.CURRENT.get(choice.owner())==NativeModes.MODES[choice.index()];}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
}
