import com.teamlifebind.aui.init.*;
import com.teamlifebind.aui.parser.HTML;
import com.teamlifebind.aui.layout.Size;
import java.nio.file.*;
import java.util.*;

/** Full furnace text and actual role layout, including the two live-progress rows. */
public final class ArchiveWorkspaceLayoutProbe {
    private static Element node(Document d,String tag,String css,String text){var e=Element.init(d.createElement(tag));e.setAttribute("class",css);if(!text.isEmpty())e.setTextContent(text);return e;}
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(args[0]);Size.setRootFontOverride(16.0);String html=Files.readString(assets.resolve("interface.html"));
        for(String css:List.of("interface.css","navigation.css","corners.css","motion.css","title.css"))html=html.replace("<link rel=\"stylesheet\" href=\""+css+"\">","<style>"+Files.readString(assets.resolve(css)).replaceAll("@font-face\\s*\\{[^}]*}","")+"</style>");
        for(int[] size:new int[][]{{1920,1080},{1152,720},{720,480}}){
            String path="workspace-"+size[0]+".html";HTML.putTemple(path,html.replace("mode=browser,user-scalable=false","mode=fixed,width="+size[0]+",height="+size[1]+",scale=1,user-scalable=false"));var d=Document.create(path);
            try(var ignored=Document.withContext(d)){
                for(var entry:Map.of("data-family","workshop","data-container","true","data-has-pack","true","data-workstation","furnace","data-search","true").entrySet())d.body.setAttribute(entry.getKey(),entry.getValue());
                d.getElementById("page-title").setTextContent("熔炉");d.getElementById("work-label").setTextContent("烧炼流程");d.getElementById("pack-label").setTextContent("随身行囊");
                d.getElementById("work-instructions").setTextContent("原料区放入待烧炼物品，燃料区放入煤炭等燃料；完成后从绿色区域取出产物。");
                var control=node(d,"button","control","按钮");d.getElementById("controls").appendChild(control);
                for(String label:List.of("待烧炼原料","燃料 · 煤炭等","成品 · 从此取出")){var zone=node(d,"section","workspace-zone","");zone.appendChild(node(d,"div","zone-title",label));var grid=node(d,"div","zone-slots","");grid.appendChild(node(d,"div","slot",""));zone.appendChild(grid);d.getElementById("work-slots").appendChild(zone);}
                for(String label:List.of("烧炼进度","当前燃料余量")){var row=node(d,"div","process","");row.appendChild(node(d,"span","process-label",label));row.appendChild(node(d,"span","process-value","75%"));var track=node(d,"div","process-track","");track.appendChild(node(d,"div","process-fill",""));row.appendChild(track);d.getElementById("work-progress").appendChild(row);}
                var main=d.getElementById("pack-main");var hotbar=d.getElementById("pack-hotbar");
                if(main==null)main=d.getElementById("pack-slots");if(hotbar==null)hotbar=d.getElementById("pack-slots");
                var quick=new ArrayList<Element>();
                for(int i=0;i<36;i++){var slot=node(d,"div","slot","");if(i>=27){slot.appendChild(node(d,"span","slot-number",""+(i-26)));quick.add(slot);} (i<27?main:hotbar).appendChild(slot);}
                d.flushPendingStyleUpdates();d.commitRenderState();
                var a=d.querySelector(".work-area").getBoundingClientRect();var b=d.querySelector(".pack-area").getBoundingClientRect();var pane=d.getElementById("control-scroll").getBoundingClientRect();
                if(a.width<100||b.width<100||a.right>b.left+1||b.right>pane.right+1)throw new AssertionError("Furnace workspace hides or pushes the backpack outside the viewport: "+size[0]+" work="+a.left+".."+a.right+" pack="+b.left+".."+b.right+" paneRight="+pane.right);
                var firstQuick=quick.getFirst().getBoundingClientRect();
                for(int i=1;i<quick.size();i++){var previous=quick.get(i-1).getBoundingClientRect();var current=quick.get(i).getBoundingClientRect();if(Math.abs(current.top-firstQuick.top)>1||current.left<previous.right-.1||current.right>b.right+1)throw new AssertionError("Hotbar 1-9 wraps across rows or overlaps: "+size[0]+" key="+(i+1)+" firstTop="+firstQuick.top+" currentTop="+current.top);}
                System.out.println("ARCHIVE_HOTBAR_ROW_PASS "+size[0]+"x"+size[1]+" keys=1..9 singleRow=true");
                System.out.println("ARCHIVE_WORKSPACE_LAYOUT_PASS "+size[0]+"x"+size[1]+" workWidth="+a.width+" packWidth="+b.width);
            }finally{d.remove();}
        }
    }
}
