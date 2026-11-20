import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;
import com.teamlifebind.aui.layout.Size;
import com.teamlifebind.aui.parser.HTML;
import com.teamlifebind.clientui.ArchiveChatFeed;
import java.nio.file.*;
import java.util.*;

/** Exercise the shipping overlay styles with the long, unbroken messages from the reported clipping. */
public final class ArchiveChatLayoutProbe {
    public static void main(String[] args)throws Exception {
        Path assets=Path.of(args[0]);Size.setRootFontOverride(16.0);
        String html=Files.readString(assets.resolve("overlays.html"));
        for(String css:List.of("overlays.css","corners.css","motion.css"))
            html=html.replace("<link rel=\"stylesheet\" href=\""+css+"\">","<style>"+Files.readString(assets.resolve(css)).replaceAll("@font-face\\s*\\{[^}]*}","")+"</style>");
        for(int[] size:new int[][]{{1920,1080},{1152,720},{720,480},{576,384}}){
            String path="chat-layout-"+size[0]+".html";
            HTML.putTemple(path,html.replace("mode=browser,user-scalable=false","mode=fixed,width="+size[0]+",height="+size[1]+",scale=1,user-scalable=false"));
            var doc=Document.create(path);
            try(var ignored=Document.withContext(doc)){
                var host=doc.getElementById("hud-chat");host.setAttribute("style","opacity:1");
                var status=doc.getElementById("sidebar");status.setAttribute("style","opacity:1");
                for(int i=0;i<20;i++){
                    var line=Element.init(doc.createElement("div"));line.setAttribute("class","sidebar-line");line.setTextContent("状态 "+i);status.appendChild(line);
                }
                for(String kind:List.of("short","mixed","unbroken","multiline","unicode")){
                    var messages=new ArrayList<String>();
                    for(int i=0;i<6;i++)messages.add("消息 "+i+" "+switch(kind){
                        case "short"->"队伍成员已准备";
                        case "unbroken"->"bydbawyuigdawbydhawuhdawhewqie1wadiuysabydbaawyubgdawb".repeat(20);
                        case "multiline"->"第一行\n第二行\n第三行\n".repeat(20);
                        case "unicode"->"中文😀𠮷abcdefg".repeat(200);
                        default->"bydbawyuigdawbydhawuhdawhewqie1wadiuysabydbaawyubgdawb ".repeat(4);
                    });
                    ArchiveChatFeed.update(doc,host,messages);
                    doc.flushPendingStyleUpdates();doc.commitRenderState();
                    var frame=host.getBoundingClientRect();var rows=doc.querySelectorAll(".chat-line");
                    var statusBox=status.getBoundingClientRect();
                    if(statusBox.bottom>size[1]-220||statusBox.left<frame.right)throw new AssertionError("Status card overlaps chat or the lower HUD: "+size[0]+"x"+size[1]+" status="+statusBox.left+","+statusBox.top+".."+statusBox.right+","+statusBox.bottom+" chatRight="+frame.right);
                    if(rows.isEmpty()||!rows.getLast().getTextContent().startsWith("消息 0"))throw new AssertionError("Newest chat message was lost");
                    if(frame.top<16||frame.left<16||frame.right>size[0]-16||frame.bottom>size[1]-16)throw new AssertionError("Chat leaves the viewport safe area");
                    double lastBottom=frame.top;
                    for(var row:rows){var box=row.getBoundingClientRect();String text=row.getTextContent();
                        if(box.top<frame.top-.1||box.bottom>frame.bottom+.1||box.left<frame.left-.1||box.right>frame.right+.1||box.top<lastBottom-.1)
                            throw new AssertionError("HUD chat cuts/overlaps a message: "+size[0]+" frame="+frame.top+".."+frame.bottom+" message="+box.top+".."+box.bottom);
                        if(box.height<35||box.width<150)throw new AssertionError("Chat shrinks below a readable size");
                        for(int n=0;n<text.length();n++)if(Character.isSurrogate(text.charAt(n))){
                            if(!Character.isHighSurrogate(text.charAt(n))||n+1>=text.length()||!Character.isLowSurrogate(text.charAt(++n)))throw new AssertionError("Chat preview split a Unicode code point");
                        }
                        lastBottom=box.bottom;
                    }
                    if(kind.equals("short")&&rows.size()<2)throw new AssertionError("Compact preview unnecessarily discards short messages");
                    if(!kind.equals("short")&&!kind.equals("mixed")&&!rows.getLast().getTextContent().endsWith("…"))throw new AssertionError("Long message preview was clipped without an ellipsis");
                    if(!messages.getFirst().startsWith(rows.getLast().getTextContent().replaceAll("…$","")))throw new AssertionError("Chat preview changed its message text");
                    System.out.println("ARCHIVE_CHAT_LAYOUT_PASS "+size[0]+"x"+size[1]+" "+kind+" completeRows="+rows.size());
                }
            }finally{doc.remove();}
        }
    }
}
