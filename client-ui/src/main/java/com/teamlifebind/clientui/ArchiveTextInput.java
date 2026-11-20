package com.teamlifebind.clientui;

import com.teamlifebind.aui.element.AbstractText;
import com.teamlifebind.aui.init.Element;
import net.minecraft.client.gui.components.EditBox;

/** Synchronize editable text and its cursor once on creation, then on genuine edits. */
final class ArchiveTextInput {
    private ArchiveTextInput(){}
    static void initialize(Element input,EditBox source){
        input.setValue(source.getValue());
        if(input instanceof AbstractText text){int cursor=Math.clamp(source.getCursorPosition(),0,input.getValue().length());text.setSelectionRange(cursor,cursor);}
    }
    static void changed(Element input,EditBox source){
        int cursor=input instanceof AbstractText text?text.getCursor():input.getValue().length();
        int anchor=input instanceof AbstractText text?(cursor==text.getSelectionStart()?text.getSelectionEnd():text.getSelectionStart()):cursor;
        source.setValue(input.getValue());source.setCursorPosition(cursor);source.setHighlightPos(anchor);
        if(!input.getValue().equals(source.getValue())){
            input.setValue(source.getValue());
            if(input instanceof AbstractText text)text.setSelectionRange(Math.min(anchor,input.getValue().length()),Math.min(cursor,input.getValue().length()));
        }
    }
}
