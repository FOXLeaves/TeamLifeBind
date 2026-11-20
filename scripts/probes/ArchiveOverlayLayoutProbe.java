package com.teamlifebind.clientui;
import com.teamlifebind.aui.init.*;
import com.teamlifebind.aui.layout.Size;
import com.teamlifebind.aui.parser.HTML;
import java.nio.file.*;
import java.util.*;

/** Actual styles and allocator, with long status panels and narrow windows. */
public final class ArchiveOverlayLayoutProbe {
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(args[0]);var field=HudLayout.class.getDeclaredField("preferences");field.setAccessible(true);
        field.set(null,new HudLayoutPreferences(Path.of(args[1]).resolve("overlay-layout-fixture.properties")));
        Size.setRootFontOverride(16.0);String html=Files.readString(assets.resolve("overlays.html"));
        for(String name:List.of("overlays.css","corners.css","motion.css"))html=html.replace("<link rel=\"stylesheet\" href=\""+name+"\">","<style>"+Files.readString(assets.resolve(name)).replaceAll("@font-face\\s*\\{[^}]*}","")+"</style>");
        for(int[] size:new int[][]{{1920,1080},{1152,720},{720,480},{576,384}}){
            String path="overlay-regions-"+size[0]+".html";HTML.putTemple(path,html.replace("mode=browser,user-scalable=false","mode=fixed,width="+size[0]+",height="+size[1]+",scale=1,user-scalable=false"));var doc=Document.create(path);
            try(var ignored=Document.withContext(doc)){
                for(String id:List.of("notifications","sidebar","bosses"))doc.getElementById(id).setAttribute("style","");
                doc.getElementById("notifications").appendChild(node(doc,"section","notification","通知标题 · 物资说明"));
                var status=doc.getElementById("sidebar");status.appendChild(node(doc,"h2","sidebar-title","TLB 对局"));
                for(int i=0;i<15;i++)status.appendChild(node(doc,"div","sidebar-line","队伍状态 "+i));
                doc.getElementById("bosses").appendChild(node(doc,"div","boss","首领进度"));
                doc.flushPendingStyleUpdates();doc.commitRenderState();
                ArchiveOverlayLayout.apply(doc,true,true,false,new ArchiveHud.Rail(28,size[1]-130));doc.flushPendingStyleUpdates();doc.commitRenderState();
                var a=doc.getElementById("notifications").getBoundingClientRect();var b=status.getBoundingClientRect();
                if(!(a.right<=b.left||a.left>=b.right||a.bottom<=b.top||a.top>=b.bottom))throw new AssertionError("Notification/status overlap at "+size[0]);
                if(a.left<0||a.right>size[0]+1||b.right>size[0]+1||b.bottom>size[1]-130+1)throw new AssertionError("Overlay exceeds its allocated region at "+size[0]+" statusBottom="+b.bottom);
                System.out.println("OVERLAY_REGIONS_PASS "+size[0]+" notifications/status/boss/vitals separated");
            }finally{doc.remove();}
        }
        var queue=new ArchiveNoticeQueue();Object one=new Object(),two=new Object();queue.offer(one,"第一条",0);queue.offer(one,"第一条",0);queue.offer(two,"第二条",0);
        if(!queue.current(0).equals("第一条")||!queue.current(3_999_999_999L).equals("第一条")||!queue.current(4_000_000_000L).equals("第二条")||!queue.current(8_000_000_000L).isEmpty())throw new AssertionError("Notice queue loses, duplicates or stacks messages");
        queue.offer(one,"更新内容",9_000_000_000L);if(!queue.current(9_000_000_000L).equals("更新内容"))throw new AssertionError("Updated native toast was ignored");
        System.out.println("NOTICE_QUEUE_PASS identity deduplication; sequential display; changed content; expiry");
    }
    private static Element node(Document doc,String tag,String css,String text){var node=Element.init(doc.createElement(tag));node.setAttribute("class",css);node.setTextContent(text);return node;}
}
