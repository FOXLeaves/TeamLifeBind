package com.teamlifebind.clientui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/** Preserve native announcements in a bounded visual queue instead of stacking them over the status HUD. */
final class ArchiveNoticeQueue {
    private record Notice(String text,long added){}
    private final Map<Object,String> seen=new WeakHashMap<>();
    private final Deque<Notice> waiting=new ArrayDeque<>();
    private Notice visible;
    private long until;
    void offer(Object identity,String text,long now){
        if(text.isBlank()||text.equals(seen.put(identity,text)))return;
        if(waiting.size()>=32)waiting.removeFirst();waiting.addLast(new Notice(text,now));
    }
    String current(long now){
        if(visible!=null&&now>=until)visible=null;
        while(visible==null&&!waiting.isEmpty()){
            var next=waiting.removeFirst();if(now-next.added()>30_000_000_000L)continue;
            visible=next;until=now+4_000_000_000L;
        }
        return visible==null?"":visible.text();
    }
}
