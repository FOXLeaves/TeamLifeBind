package com.teamlifebind.clientui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** A server-authored semantic action-bar component, not a parser of translated chat strings. */
public final class ArchiveCountdown {
    public static final String KEY="tlb.ui.countdown";
    private ArchiveCountdown(){}
    private static TranslatableContents find(Component message){
        if(message.getContents() instanceof TranslatableContents contents&&contents.getKey().equals(KEY))return contents;
        for(var child:message.getSiblings()){var found=find(child);if(found!=null)return found;}return null;
    }
    public static boolean draw(GuiGraphicsExtractor graphics,Component message,int ticks,float partialTick){
        if(!ArchiveTheme.enabled()||message==null||ticks<=0)return false;
        var contents=find(message);if(contents==null)return false;Object[] args=contents.getArgs();
        if(args.length<3||!(args[0] instanceof Number remaining)||!(args[1] instanceof Number total))return false;
        int seconds=remaining.intValue(),duration=total.intValue();if(seconds<0||seconds>60||duration<1||duration>60)return false;
        float elapsed=(60-ticks+partialTick)/20F;
        if(elapsed>1.2F)return true;
        String mode=args[2] instanceof Component text?text.getString():String.valueOf(args[2]);
        ArchiveOverlays.countdown(graphics,seconds,Math.clamp((seconds-elapsed)/(float)duration,0,1),mode);return true;
    }
}
