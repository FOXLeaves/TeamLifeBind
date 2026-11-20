import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;
import com.teamlifebind.aui.layout.Size;
import com.teamlifebind.aui.parser.HTML;
import com.teamlifebind.aui.style.Interaction;
import java.nio.file.*;
import java.util.*;

/** No window, world or connection: use the shipping CSS parser and selection policy. */
public final class ArchiveUsabilityProbe {
    public static void main(String[] args) throws Exception {
        Path assets=Path.of(args[0]);
        Size.setRootFontOverride(16.0);
        String html=Files.readString(assets.resolve("interface.html"));
        for(String css:List.of("interface.css","navigation.css","corners.css","motion.css","title.css")) {
            String source=Files.readString(assets.resolve(css)).replaceAll("@font-face\\s*\\{[^}]*}","");
            html=html.replace("<link rel=\"stylesheet\" href=\""+css+"\">","<style>"+source+"</style>");
        }
        for(int[] size:new int[][]{{1920,1080},{1152,720},{720,480}}){
        System.setProperty("aui.test.viewport.width",""+size[0]);
        System.setProperty("aui.test.viewport.height",""+size[1]);
        String path="archive-usability-"+size[0]+".html";
        HTML.putTemple(path,html.replace("mode=browser,user-scalable=false","mode=fixed,width="+size[0]+",height="+size[1]+",scale=1,user-scalable=false"));
        var doc=Document.create(path);
        try(var ignored=Document.withContext(doc)) {
            if(doc.getViewportSize().width()!=size[0]||doc.getViewportSize().height()!=size[1])throw new AssertionError("Fixture viewport did not resize: "+doc.getViewportSize()+" requested="+size[0]+"x"+size[1]);
            doc.body.setAttribute("data-family","settings");
            doc.body.setAttribute("data-view","settings-home");
            doc.getElementById("page-title").setTextContent("Options / 选项");
            var button=Element.init(doc.createElement("button"));
            button.setAttribute("class","control");
            var label=Element.init(doc.createElement("span"));
            label.setAttribute("class","control-label");
            label.setTextContent("Music and sounds / 音乐和声音");
            button.appendChild(label);doc.getElementById("controls").appendChild(button);
            if(Interaction.isUserSelectable(label))throw new AssertionError("Menu labels can be selected and copied instead of acting only as controls");
            if(doc.getElementById("filter")!=null)throw new AssertionError("Removed menu search is still present");
            var input=Element.init(doc.createElement("input"));input.setAttribute("class","native-input");doc.getElementById("controls").appendChild(input);
            if(!Interaction.isUserSelectable(input))throw new AssertionError("Actual editable fields must retain editing and selection");
            var second=Element.init(doc.createElement("button"));second.setAttribute("class","control slider-row");
            var copy=Element.init(doc.createElement("div"));copy.setAttribute("class","control-copy");
            var caption=Element.init(doc.createElement("span"));caption.setAttribute("class","control-label");caption.setTextContent("Field of view: 90");copy.appendChild(caption);second.appendChild(copy);
            var track=Element.init(doc.createElement("div"));track.setAttribute("class","range-track");second.appendChild(track);doc.getElementById("controls").appendChild(second);
            var done=Element.init(doc.createElement("button"));done.setAttribute("class","control dock-action");done.setTextContent("Done");doc.getElementById("action-dock").appendChild(done);doc.getElementById("action-dock").setAttribute("style","display:flex");
            var firstBox=button.getBoundingClientRect();var secondBox=second.getBoundingClientRect();var dock=doc.getElementById("action-dock").getBoundingClientRect();var scroll=doc.getElementById("control-scroll").getBoundingClientRect();
            if(Math.abs(firstBox.left-secondBox.left)>1||secondBox.top<firstBox.bottom-1)throw new AssertionError("Settings rows are mixed into columns: "+size[0]);
            if(firstBox.height<35||firstBox.height>70)throw new AssertionError("Settings row is oversized or too small: "+firstBox.height+" width="+size[0]);
            if(track.getBoundingClientRect().width<99)throw new AssertionError("Slider is collapsed into the label");
            if(dock.top<scroll.bottom-1||dock.bottom>size[1]+1)throw new AssertionError("Done button is not anchored outside scrolling settings: width="+size[0]+" dock="+dock.top+".."+dock.bottom+" scrollBottom="+scroll.bottom);
            System.out.println("ARCHIVE_SELECTION_POLICY_PASS decorative text locked; editable fields retain selection");
            System.out.println("ARCHIVE_SETTINGS_LAYOUT_PASS "+size[0]+"x"+size[1]+" alignedRows=true rowHeight="+firstBox.height+" sliderWidth="+track.getBoundingClientRect().width+" fixedActions=true");
        } finally {doc.remove();}
        titleHover(html,size);
        }
    }
    private static void titleHover(String html,int[] size) {
        String path="archive-title-hover-"+size[0]+".html";
        html=html.replace("</head>","<style>body[data-family=\"title\"] .control { transition: none; }</style></head>");
        HTML.putTemple(path,html.replace("mode=browser,user-scalable=false","mode=fixed,width="+size[0]+",height="+size[1]+",scale=1,user-scalable=false"));
        var doc=Document.create(path);
        try(var ignored=Document.withContext(doc)) {
            doc.body.setAttribute("data-family","title");doc.body.setAttribute("data-view","title");
            doc.body.setAttribute("data-rail","true");doc.body.setAttribute("data-motion","on");
            doc.getElementById("page-title").setTextContent("TEAM LIFE BIND");
            doc.getElementById("page-subtitle").setTextContent("结伴启程，命运相系。选择世界，书写新的远征。");
            doc.getElementById("interface-motion").setTextContent("动效：开启");doc.getElementById("back").setAttribute("style","display:none");
            Element first=null;var actions=new ArrayList<Element>();
            for(String name:List.of("单人游戏","多人游戏","Minecraft Realms")) {
                var button=Element.init(doc.createElement("button"));button.setAttribute("class","control");
                button.setAttribute("data-journey", List.of("singleplayer","multiplayer","realms").get(actions.size()));
                var label=Element.init(doc.createElement("span"));label.setAttribute("class","control-label");label.setTextContent(name);
                button.appendChild(label);doc.getElementById("controls").appendChild(button);actions.add(button);if(first==null)first=button;
            }
            for(String name:List.of("语言…","辅助功能设置…","选项…","退出游戏","© Mojang AB 请勿二次分发！")){
                var utility=Element.init(doc.createElement("button"));utility.setAttribute("class",name.startsWith("©")?"rail-link legal-link":"rail-link");utility.setTextContent(name);doc.getElementById("rail-links").appendChild(utility);
            }
            first.setHover(true);doc.flushPendingStyleUpdates();doc.commitRenderState();
            var card=first.getBoundingClientRect();var pane=doc.getElementById("control-scroll").getBoundingClientRect();
            if(card.top<pane.top-.1||card.left<pane.left-.1||card.right>pane.right+.1||card.bottom>pane.bottom+.1)
                throw new AssertionError("Title hover clips the rounded card at its scroll boundary: "+size[0]+" cardTop="+card.top+" scrollTop="+pane.top+" cardLeft="+card.left+" scrollLeft="+pane.left);
            var chapter=doc.querySelector(".chapter").getBoundingClientRect();var utility=doc.querySelector(".rail").getBoundingClientRect();
            var header=doc.querySelector(".topbar").getBoundingClientRect();var motion=doc.getElementById("interface-motion").getBoundingClientRect();
            if(motion.top<header.top-.1||motion.bottom>header.bottom+.1)throw new AssertionError("Top-bar motion button protrudes outside its material: "+size[0]+" button="+motion.top+".."+motion.bottom+" header="+header.top+".."+header.bottom);
            if(chapter.bottom>pane.top+.1||Math.abs(chapter.left-pane.left)>.1)throw new AssertionError("Title does not sit above the journey entries: "+size[0]);
            double previousBottom=pane.top;
            for (var action : actions) {
                var rect=action.getBoundingClientRect();
                if(rect.top<previousBottom-.1||rect.bottom>pane.bottom+.1)throw new AssertionError("Title action overlaps or requires scrolling: "+size[0]+" button="+rect+" pane="+pane);
                previousBottom=rect.bottom;
            }
            if(actions.get(2).getBoundingClientRect().height>=actions.getFirst().getBoundingClientRect().height)throw new AssertionError("Realms should have a smaller entry");
            if(utility.top<pane.bottom-.1||utility.bottom>size[1])throw new AssertionError("Title utility dock overlaps its actions or leaves the viewport: "+size[0]);
            System.out.println("ARCHIVE_TITLE_HOVER_PASS "+size[0]+"x"+size[1]+" completeRoundedOutline=true topClearance="+(card.top-pane.top));
        }finally{doc.remove();}
    }
}
