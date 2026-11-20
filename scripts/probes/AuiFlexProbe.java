import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;
import com.teamlifebind.aui.layout.Size;
import com.teamlifebind.aui.parser.HTML;
import java.util.*;
import java.nio.file.*;

/** Intrinsic measurement of the actual nested flex pattern, with cold layout caches. */
public final class AuiFlexProbe {
    public static void main(String[] args) throws Exception{
        System.setProperty("aui.test.viewport.width","1152");System.setProperty("aui.test.viewport.height","720");
        Size.setRootFontOverride(16.0);
        HTML.putTemple("flex-regression.html","""
            <html><head><style>
            *{box-sizing:border-box}body{margin:0;width:1152px;height:720px;font-size:14px}
            main{position:fixed;left:22vw;right:31vw;top:15vh;bottom:11vh;display:flex;flex-direction:column;min-width:0}
            .scroll{flex:1;min-height:0;overflow-y:auto}.controls{display:flex;flex-direction:column;gap:10px}
            button{display:flex;align-items:center;gap:18px;min-height:68px;width:100%;padding:17px 22px}
            .copy{flex:1;min-width:0;display:flex;flex-direction:column;gap:7px}
            .label{font-size:20px;line-height:1.35}.detail{font-size:11px;line-height:1.6;white-space:nowrap;overflow:hidden}
            </style></head><body><main><h1>Controls</h1><section class="scroll"><div class="controls">
            <button><span>01</span><div class="copy"><div class="label">Mouse controls</div><div class="detail">Rebind controls and change sensitivity</div></div><span>›</span></button>
            <button><span>02</span><div class="copy"><div class="label">Auto jump</div><div class="detail">Enabled</div></div><span>›</span></button>
            </div></section></main></body></html>
            """);
        if(args.length>0){
            Path assets=Path.of(args[0]);String html=Files.readString(assets.resolve("interface.html"));
            String css=Files.readString(assets.resolve("interface.css")).replaceAll("@font-face\\{[^}]*}","");
            html=html.replace("<link rel=\"stylesheet\" href=\"interface.css\">","<style>"+css+"</style>").replace("<body>","<body data-family=\"settings\" data-container=\"false\">");
            HTML.putTemple("flex-regression.html",html);
        }
        var document=Document.create("flex-regression.html");
        try(var ignored=Document.withContext(document)){
            if(args.length>0){
                document.getElementById("page-title").setTextContent("Controls / 控制设置");
                document.getElementById("control-count").setTextContent("06");document.getElementById("back").setTextContent("返回 / ESC");document.getElementById("filter").setAttribute("placeholder","搜索设置、物品或名称...");
                var host=document.getElementById("controls");
                for(int i=0;i<24;i++){
                    var button=Element.init(document.createElement("button"));button.setAttribute("class","control"+(i==0?" selected":""));
                    var number=Element.init(document.createElement("span"));number.setAttribute("class","control-number");number.setTextContent("01");button.appendChild(number);
                    var copy=Element.init(document.createElement("div"));copy.setAttribute("class","control-copy");
                    var label=Element.init(document.createElement("div"));label.setAttribute("class","control-label");label.setTextContent("Mouse controls / 自动跳跃");copy.appendChild(label);button.appendChild(copy);var arrow=Element.init(document.createElement("span"));arrow.setAttribute("class","control-arrow");arrow.setTextContent("›");button.appendChild(arrow);host.appendChild(button);
                }
            }
            var elements=document.querySelectorAll("*");
            int checks=0;
            for(int pass=0;pass<8;pass++)for(var e:document.querySelectorAll(args.length>0?"*":".copy")){
                for(var element:elements)element.getLayout().size.clear();
                Size intrinsic;
                try{intrinsic=pass%4==0?Size.of(e):pass%4==1?Size.minContent(e):pass%4==2?Size.natural(e):Size.naturalAtContentWidth(e,190);}catch(StackOverflowError cycle){throw new AssertionError("Cyclic intrinsic measurement mode="+(pass%4)+" element="+e.tagName+"#"+e.getAttribute("id")+"."+e.getAttribute("class"),cycle);}
                if(!Double.isFinite(intrinsic.width())||!Double.isFinite(intrinsic.height()))throw new AssertionError("Invalid intrinsic flex extent: "+intrinsic);
                checks++;
            }
            System.out.println("AUI_FLEX_LAYOUT_PASS coldIntrinsicMeasurements="+checks);
        }finally{document.remove();}
    }
}
