package com.teamlifebind.clientui;

import com.teamlifebind.aui.client.gui.ApricityGuiLayers;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;
import com.teamlifebind.aui.render.ScreenDocumentCache;
import com.teamlifebind.clientui.mixin.ArchiveChatAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.BossEvent;
import net.minecraft.world.scores.*;
import java.lang.reflect.*;
import java.util.*;

/** Read-only presentation of native chat filters, boss events, tab ordering and toast lifecycles. */
public final class ArchiveOverlays {
    private static Document page;
    private static final Map<String,String> signatures=new HashMap<>();
    private static final Map<String,Long> touched=new HashMap<>();
    private static long generation=-1;
    private static final ArchiveNoticeQueue notices=new ArchiveNoticeQueue();
    private static List<String> chatPreview=List.of();
    private static String chatPacked="";
    private ArchiveOverlays(){}
    static Document document(){return page;}
    private static boolean enabled(){return ArchiveTheme.enabled()&&!Minecraft.getInstance().gui.hud.isHidden();}
    private static Element node(String tag,String css,String text){var e=Element.init(page.createElement(tag));e.setAttribute("class",css);if(!text.isBlank())e.setTextContent(text);return e;}
    private static void ensure(){
        if(page==null){page=AuiClient.open("teamlifebind/overlays.html");generation=-1;}
        if(generation!=page.getRefreshGeneration()){generation=page.getRefreshGeneration();signatures.clear();touched.clear();chatPacked="";page.body.setAttribute("data-motion",AuiMenuSettings.motion()?"on":"off");}
    }
    private static Element update(String id,String signature){
        return update(id,signature,"opacity:1");
    }
    private static Element update(String id,String signature,String style){
        ensure();touched.put(id,System.nanoTime());var host=page.getElementById(id);host.setAttribute("style",style);
        if(signature.equals(signatures.put(id,signature)))return null;host.setTextContent("");return host;
    }
    private static void submit(GuiGraphicsExtractor g){if(page!=null){
        page.applyViewport(false);HudLayout.apply(page);long now=System.nanoTime();
        ArchiveOverlayLayout.apply(page,now-touched.getOrDefault("notifications",0L)<330_000_000L,now-touched.getOrDefault("sidebar",0L)<330_000_000L,now-touched.getOrDefault("hud-chat",0L)<150_000_000L,ArchiveHud.rightRail(page.getViewportSize().height()));
        if(!chatPreview.isEmpty()&&now-touched.getOrDefault("hud-chat",0L)<150_000_000L&&AuiPageReadiness.ready(page)){
            var host=page.getElementById("hud-chat");var box=host.getBoundingClientRect();String signature=chatPreview+":"+box.width+":"+box.height;
            if(!signature.equals(chatPacked)||host.getChildElementCount()==0){chatPacked=signature;ArchiveChatFeed.update(page,host,chatPreview);}
        }
        HudLayout.apply(page);ScreenDocumentCache.attach(page);ApricityGuiLayers.submitUi(g);
    }}
    static void tick(){
        if(page==null)return;ensure();long now=System.nanoTime();
        page.body.setAttribute("data-screen",Minecraft.getInstance().gui.screen()==null?"false":"true");
        page.body.setAttribute("data-motion",AuiMenuSettings.motion()?"on":"off");
        for(String id:List.of("bosses","roster","notifications","hud-chat","sidebar","countdown")){
            long age=now-touched.getOrDefault(id,0L);
            if(age>150_000_000L)page.getElementById(id).setAttribute("style",AuiMenuSettings.motion()&&age<330_000_000L?"opacity:0":"display:none");
        }
        if(!ArchiveTheme.enabled()||touched.values().stream().allMatch(at->now-at>500_000_000L)){page.remove();page=null;signatures.clear();touched.clear();}
    }
    public static boolean boss(GuiGraphicsExtractor g,Collection<? extends BossEvent> events){
        if(!enabled())return false;if(events.isEmpty())return true;
        String signature=events.stream().map(e->e.getName().getString()+":"+Math.round(e.getProgress()*10000)+e.getColor()).reduce("",String::concat);
        ensure();
        try(var ignored=Document.withContext(page)){var host=update("bosses",signature);if(host!=null)for(var event:events){
            var row=node("div","boss","");row.appendChild(node("span","boss-mark","◇"));
            var copy=node("div","boss-copy","");copy.appendChild(node("div","boss-name",event.getName().getString()));
            var track=node("div","boss-track","");var fill=node("div","boss-fill","");fill.setAttribute("style","width:"+Math.clamp(event.getProgress()*100,0,100)+"%");track.appendChild(fill);copy.appendChild(track);row.appendChild(copy);
            row.appendChild(node("span","boss-value",Math.round(event.getProgress()*100)+"%"));host.appendChild(row);
        }submit(g);}return true;
    }
    public static void countdown(GuiGraphicsExtractor graphics,int seconds,float progress,String mode){
        if(!enabled())return;ensure();
        try(var ignored=Document.withContext(page)){
            var host=update("countdown",seconds+":"+mode);
            if(host!=null){host.appendChild(node("div","round-kicker",ArchiveTheme.tr("countdown.heading")));host.appendChild(node("div","round-mode",mode));
                host.appendChild(node("div","round-number",seconds==0?ArchiveTheme.tr("countdown.ready"):Integer.toString(seconds)));
                var track=node("div","round-track","");var fill=node("div","round-fill","");fill.setAttribute("id","round-fill");track.appendChild(fill);host.appendChild(track);
                host.appendChild(node("div","round-hint",ArchiveTheme.tr("countdown.hint")));
            }
            page.getElementById("round-fill").setInlineStyleProperty("width",Math.round(progress*100)+"%");submit(graphics);
        }
    }
    public static boolean roster(GuiGraphicsExtractor g,PlayerTabOverlay source,List<PlayerInfo> players,Component header,Component footer,Scoreboard scoreboard,Objective objective){
        if(!enabled())return false;
        var lines=new ArrayList<String>();for(var info:players){var score=objective==null?null:scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(info.getProfile()),objective);lines.add(source.getNameForDisplay(info).getString()+"\t"+(score==null?"":score.value())+"\t"+info.getLatency()+" ms");}
        String signature=(header==null?"":header.getString())+lines+(footer==null?"":footer.getString());
        ensure();try(var ignored=Document.withContext(page)){var host=update("roster",signature);if(host!=null){host.appendChild(node("div","overlay-kicker","FIELD ROSTER / "+players.size()));if(header!=null)host.appendChild(node("h2","roster-title",header.getString()));
            for(String line:lines){var row=node("div","roster-row","");String[] parts=line.split("\t",-1);row.appendChild(node("span","roster-name",parts[0]));row.appendChild(node("span","roster-score",parts[1]));row.appendChild(node("span","roster-ping",parts[2]));host.appendChild(row);}
            if(footer!=null)host.appendChild(node("p","roster-footer",footer.getString()));
        }submit(g);}return true;
    }
    public static List<GuiMessage> messages(boolean history){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.chatVisibility().get().name().equals("HIDDEN"))return List.of();
        var source=(ArchiveChatAccess)mc.gui.hud.getChat();return source.tlb$messages().stream().filter(source.tlb$filter()).filter(message->history||mc.gui.hud.getGuiTicks()-message.addedTime()<180).limit(history?100:6).toList();
    }
    public static boolean chat(GuiGraphicsExtractor g){
        if(!enabled())return false;if(Minecraft.getInstance().gui.screen()!=null)return true;
        var messages=messages(false);if(messages.isEmpty())return true;
        ensure();page.applyViewport(false);
        boolean ready=AuiPageReadiness.ready(page);
        double height=page.getViewportSize().height();
        double bottom=Math.min(ArchiveHud.chatBottom(height),Math.max(96,height-140));
        double available=Math.min(220,Math.min(height*.28,Math.max(44,height-bottom-96)));
        try(var ignored=Document.withContext(page)){
            var host=update("hud-chat",messages+":"+page.getViewportVersion()+":"+ready+":"+bottom,
                "opacity:1;bottom:"+bottom+"px;height:"+available+"px");
            chatPreview=messages.stream().map(message->message.content().getString()).toList();
            submit(g);
        }return true;
    }
    public static void sidebar(GuiGraphicsExtractor g,String title,List<com.teamlifebind.common.NativeHud.Line> lines){
        if(!enabled()||Minecraft.getInstance().gui.screen()!=null&&!(Minecraft.getInstance().gui.screen() instanceof HudEditorScreen))return;
        ensure();try(var ignored=Document.withContext(page)){var host=update("sidebar",title+lines);if(host!=null){host.appendChild(node("div","overlay-kicker","FIELD STATUS"));host.appendChild(node("h2","sidebar-title",title));for(var line:lines){var row=node("div","sidebar-line",line.text());int color=ArchiveTheme.hudColor(line.color());row.setAttribute("style","color:#"+String.format("%06x",color&0xffffff));host.appendChild(row);}}submit(g);}
    }
    public static boolean scoreboard(GuiGraphicsExtractor g,Objective objective){
        if(!enabled())return false;var lines=objective.getScoreboard().listPlayerScores(objective).stream().filter(entry->!entry.isHidden()).sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner,String.CASE_INSENSITIVE_ORDER)).limit(15).map(entry->new com.teamlifebind.common.NativeHud.Line((entry.display()==null?entry.ownerName():entry.display()).getString()+"   "+entry.value(),ArchiveTheme.SILVER)).toList();sidebar(g,objective.getDisplayName().getString(),lines);return true;
    }
    public static boolean toasts(GuiGraphicsExtractor g,List<?> instances){
        if(!enabled())return false;long now=System.nanoTime();
        for(Object instance:instances)try{var method=instance.getClass().getDeclaredMethod("getToast");method.setAccessible(true);notices.offer(instance,toastText(method.invoke(instance)),now);}catch(ReflectiveOperationException ignored){}
        String message=notices.current(now);if(message.isEmpty())return true;
        ensure();try(var ignored=Document.withContext(page)){
            var host=update("notifications",message);
            if(host!=null){var card=node("section","notification","");card.appendChild(node("span","notification-mark","✦"));
                var copy=node("div","notification-copy","");String[] lines=message.split("\\n",2);
                copy.appendChild(node("div","notification-title",lines[0]));if(lines.length>1)copy.appendChild(node("div","notification-text",lines[1].replace('\n',' ')));
                card.appendChild(copy);host.appendChild(card);
            }submit(g);
        }return true;
    }
    private static String toastText(Object toast){
        var result=new LinkedHashSet<String>();for(Class<?> type=toast.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers()))continue;try{field.setAccessible(true);Object value=field.get(toast);
                if(value instanceof Component text)result.add(text.getString());
                if(value instanceof Iterable<?> values)for(Object part:values)if(part instanceof FormattedCharSequence text)result.add(plain(text));
                if(value instanceof net.minecraft.advancements.AdvancementHolder holder)holder.value().display().ifPresent(display->{result.add(display.type().getDisplayName().getString());result.add(display.title().getString());});
            }catch(ReflectiveOperationException|RuntimeException ignored){}
        }
        if(result.isEmpty()&&toast instanceof net.minecraft.client.gui.components.toasts.RecipeToast){result.add(Component.translatable("recipe.toast.title").getString());result.add(Component.translatable("recipe.toast.description").getString());}
        return result.isEmpty()?ArchiveTheme.tr("control.notification"):String.join("\n",result);
    }
    static String plain(FormattedCharSequence text){var result=new StringBuilder();text.accept((i,style,cp)->{result.appendCodePoint(cp);return true;});return result.toString();}
    static void appendMessage(Document document,Element host,Component message,Screen owner){
        var paragraph=Element.init(document.createElement("p"));paragraph.setAttribute("class","conversation-line");
        message.visit((style,part)->{var span=Element.init(document.createElement(style.getClickEvent()==null?"span":"button"));span.setTextContent(part);span.setAttribute("class",style.getClickEvent()==null?"chat-run":"chat-link");
            if(style.getColor()!=null)span.setAttribute("style","color:#"+String.format("%06x",style.getColor().getValue()));
            if(style.getClickEvent()!=null)span.addEventListener("click",event->ClickBridge.click(style.getClickEvent(),owner));paragraph.appendChild(span);return Optional.empty();
        },Style.EMPTY);host.appendChild(paragraph);
    }
    private static final class ClickBridge extends Screen {
        private ClickBridge(){super(Component.empty());}
        static void click(net.minecraft.network.chat.ClickEvent event,Screen owner){if(Minecraft.getInstance().gui.screen()==owner)defaultHandleGameClickEvent(event,Minecraft.getInstance(),owner);}
    }
}
