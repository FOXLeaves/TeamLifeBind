package com.teamlifebind.clientui;

import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;
import java.util.ArrayList;
import java.util.List;

/** A compact HUD preview. Native chat remains the owner of the complete message history. */
public final class ArchiveChatFeed {
    private ArchiveChatFeed(){}

    public static void update(Document document,Element host,List<String> newestFirst){
        host.setTextContent("");
        var rows=new ArrayList<Element>();
        for(int i=Math.min(6,newestFirst.size())-1;i>=0;i--){
            var row=Element.init(document.createElement("p"));
            row.setAttribute("class",i==0?"chat-line chat-latest":"chat-line");
            String text=newestFirst.get(i);
            int count=text.codePointCount(0,text.length());
            row.setTextContent(count>1024?prefix(text,1024)+"…":text);
            host.appendChild(row);rows.add(row);
        }
        if(rows.isEmpty())return;
        // Discard complete old cards instead of letting flex/overflow cut through their text.
        while(rows.size()>1&&!fits(document,host,rows.getFirst()))host.removeChild(rows.removeFirst());
        if(fits(document,host,rows.getFirst()))return;
        // A single oversized message gets an explicit preview, split only at Unicode code points.
        var last=rows.getFirst();String text=last.getTextContent();
        int low=0,high=text.codePointCount(0,text.length());
        while(low<high){
            int middle=(low+high+1)/2;last.setTextContent(prefix(text,middle)+"…");
            if(fits(document,host,last))low=middle;else high=middle-1;
        }
        last.setTextContent(prefix(text,low)+"…");
        document.flushPendingStyleUpdates();document.commitRenderState();
    }

    private static String prefix(String text,int points){return text.substring(0,text.offsetByCodePoints(0,points));}
    private static boolean fits(Document document,Element host,Element row){
        document.flushPendingStyleUpdates();document.commitRenderState();
        var frame=host.getBoundingClientRect();var box=row.getBoundingClientRect();
        return box.top>=frame.top+1&&box.bottom<=frame.bottom-1&&box.left>=frame.left&&box.right<=frame.right;
    }
}
