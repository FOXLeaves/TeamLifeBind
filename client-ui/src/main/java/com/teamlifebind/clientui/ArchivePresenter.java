package com.teamlifebind.clientui;

import com.mojang.blaze3d.platform.InputConstants;
import com.teamlifebind.aui.client.gui.ApricityGuiLayers;
import com.teamlifebind.aui.element.Item;
import com.teamlifebind.aui.event.MouseEvent;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;
import com.teamlifebind.aui.layout.Position;
import com.teamlifebind.aui.render.Operation;
import com.teamlifebind.aui.render.ScreenDocumentCache;
import com.teamlifebind.clientui.mixin.ArchiveContainerAccess;
import com.teamlifebind.clientui.mixin.ArchiveSliderAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** HTML owns the layout and input surface. Existing screens own lifecycle, validation and actions. */
public final class ArchivePresenter {
    private static Page active;
    private static long closedAt;
    private ArchivePresenter() {}
    public static boolean supports(Screen screen){
        return ArchiveTheme.enabled()&&screen!=null&&!ArchiveTheme.owned(screen)
            &&(screen.getClass().getName().startsWith("net.minecraft.")||screen.getClass().getName().startsWith("com.teamlifebind.clientui."))
            &&!(screen instanceof ProgressScreen)&&!(screen instanceof LevelLoadingScreen)&&!(screen instanceof ConnectScreen);
    }
    public static boolean draw(Screen screen,GuiGraphicsExtractor g,int x,int y){
        if(!supports(screen))return false;
        if(active==null||active.owner!=screen){close();ArchiveChrome.close();active=new Page(screen);}
        if(screen!=Minecraft.getInstance().gui.screen()){close();return true;}active.render(g,x,y);return true;
    }
    public static void tick(){if(active!=null&&(active.owner!=Minecraft.getInstance().gui.screen()||!supports(active.owner)))close();}
    public static void close(){if(active!=null){active.ink.finish();active.document.remove();active=null;closedAt=System.nanoTime();}}
    public static AuiInkTransition.Frame inkFrame(){return active==null?AuiInkTransition.Frame.IDLE:active.ink.frame(AuiPageReadiness.ready(active.document));}
    static boolean hasActivePage(){return active!=null;}
    private static Page forInput(Screen screen){return active!=null&&active.owner==screen&&supports(screen)?active:null;}
    public static boolean mouse(Screen screen,MouseButtonEvent event,boolean down,boolean twice){var page=forInput(screen);return page!=null&&page.mouse(event,down,twice);}
    public static boolean scroll(Screen screen,double x,double y,double dx,double dy){var page=forInput(screen);return page!=null&&page.scroll(x,y,dx,dy);}
    public static boolean key(Screen screen,KeyEvent key){var page=forInput(screen);return page!=null&&page.key(key);}
    public static boolean character(Screen screen,CharacterEvent key){var page=forInput(screen);return page!=null&&page.character(key);}
    public static boolean drag(Screen screen,MouseButtonEvent event,double dx,double dy){var page=forInput(screen);return page!=null&&page.drag(event,dx,dy);}
    public static boolean handles(Screen screen){return forInput(screen)!=null;}
    static Document document(){return active==null?null:active.document;}

    private static final class Page {
        final Screen owner;
        final Document document=AuiClient.open("teamlifebind/interface.html");
        final AuiInkTransition ink=new AuiInkTransition();
        final Map<Object,Element> controlElements=new LinkedHashMap<>();
        final Map<Integer,Element> slotElements=new LinkedHashMap<>();
        final Map<Integer,ItemStack> renderedStacks=new HashMap<>();
        final Map<Integer,Slot> sourceSlots=new HashMap<>();
        final List<ArchiveModel.Control> visibleControls=new ArrayList<>();
        final String view;
        ArchiveModel model;
        String signature="",family,category="all";
        String chatSignature="";
        String suggestionsSignature="";
        long scanned;
        int hovered=-1,focus;
        int windowWidth,windowHeight;
        double mouseX,mouseY;
        boolean nativePressed;
        boolean pointerSeen;
        boolean twiceClick;
        MouseButtonInfo held;
        AbstractSliderButton dragging;
        ArchiveWorkstation.Profile workspace;
        long generation=-1;
        Page(Screen owner){
            this.owner=owner;family=ArchiveTheme.family(owner);view=ArchiveNavigation.view(owner);windowWidth=owner.width;windowHeight=owner.height;
            if(AuiMenuSettings.motion()&&!AuiScreenTransitions.active())ink.startWhenReady(System.nanoTime()-closedAt<1_000_000_000L?AuiInkTransition.Mode.CONTENT:AuiInkTransition.Mode.OPEN,AuiInkTransition.Region.FULL,false,1,AuiInkTransition.CONTENT_MILLIS);
            if(menu()!=null)workspace=ArchiveWorkstation.profile(menu());
            bind();
        }
        void bind(){
            generation=document.getRefreshGeneration();signature="";chatSignature="";suggestionsSignature="";
            controlElements.clear();slotElements.clear();renderedStacks.clear();sourceSlots.clear();visibleControls.clear();
            try(var ignored=Document.withContext(document)){
                document.body.setAttribute("data-family",family);
                document.body.setAttribute("data-input",Minecraft.getInstance().getLastInputType().isKeyboard()?"keyboard":"mouse");
                document.body.setAttribute("data-view",view);
                document.body.setAttribute("data-rail",family.equals("title")||view.equals("settings-home")||workspace!=null?"true":"false");
                document.body.setAttribute("data-has-pack",menu()!=null&&menu().slots.stream().anyMatch(s->s.container instanceof Inventory)?"true":"false");
                document.body.setAttribute("data-motion",AuiMenuSettings.motion()?"on":"off");
                motionState();
                id("interface-motion").addEventListener("click",event->toggleMotion());
                document.body.setAttribute("data-container",menu()==null||menu().slots.isEmpty()?"false":"true");
                document.body.setAttribute("data-workstation",workspace==null?"none":workspace.key());
                document.body.setAttribute("data-recipe-book",menu() instanceof RecipeBookMenu?"true":"false");
                document.body.setAttribute("data-inventory",menu() instanceof InventoryMenu?"true":"false");
                document.body.setAttribute("data-chat",owner instanceof ChatScreen?"true":"false");
                document.body.setAttribute("data-creative",owner instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen?"true":"false");
                text("page-title",family.equals("title")?"TEAM LIFE BIND":owner.getTitle().getString().isBlank()?ArchiveTheme.tr("storage.inventory"):owner.getTitle().getString());
                String chapter=ArchiveTheme.tr(view.equals("worlds")||view.equals("servers")||view.equals("languages")?"section."+view:"family."+family);var chapterParts=chapter.split(" / ",2);
                text("context",chapter);text("section-name",chapter);
                text("page-subtitle",ArchiveTheme.tr("intro."+family));text("rail-name",chapterParts[0]);document.querySelector(".rail-kicker").setTextContent(chapterParts.length>1?chapterParts[1]:"FIELD ARCHIVE");
                if(family.equals("gamemode")){text("page-title",ArchiveTheme.tr("gamemode.title"));text("page-subtitle",ArchiveTheme.tr("gamemode.hint",Minecraft.getInstance().options.keyDebugSwitchGameMode.getTranslatedKeyMessage(),Minecraft.getInstance().options.keyDebugModifier.getTranslatedKeyMessage()));}
                text("back",ArchiveTheme.tr("control.back"));text("inspect-kicker",ArchiveTheme.tr("control.inspector"));
                text("interface-hint",ArchiveTheme.tr(menu()==null?"hint.screen":"hint.container"));
                id("back").addEventListener("click",event->AuiScreenTransitions.back(()->owner.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE,0,0))));
                id("back").setAttribute("style",owner.shouldCloseOnEsc()?"":"display:none");
                makeRail();refresh();
                var focused=model.controls.stream().filter(c->c.source() instanceof EditBox edit&&edit.isFocused()).findFirst();
                var wrapper=focused.isEmpty()?null:controlElements.get(focused.get().source());
                var input=wrapper==null?null:wrapper.querySelector(".native-input");
                if(input==null&&owner instanceof ChatScreen)input=document.querySelector(".native-input");
                if(input!=null)input.focus();
            }
        }
        AbstractContainerMenu menu(){return owner instanceof MenuAccess<?> access?access.getMenu():null;}
        AbstractContainerScreen<?> container(){return owner instanceof AbstractContainerScreen<?> screen?screen:null;}
        Element id(String id){return document.getElementById(id);}
        void text(String id,String value){var e=id(id);if(!value.equals(e.getTextContent()))e.setTextContent(value);}
        Element node(String tag,String css,String text){var e=document.createElement(tag);e.setAttribute("class",css);if(!text.isEmpty())e.setTextContent(text);return Element.init(e);}
        void style(Element element,String property,String value){if(!value.equals(element.getInlineStylePropertyValue(property)))element.setInlineStyleProperty(property,value);}
        void makeRail(){
            var links=id("rail-links");links.setTextContent("");
            if(family.equals("title"))return;
            if(view.equals("settings-home")){
                var groups=new ArrayList<String>();groups.add("all");groups.addAll(ArchiveNavigation.SETTING_GROUPS);
                for(String group:groups){
                    var button=node("button","rail-link"+(category.equals(group)?" selected":""),ArchiveTheme.tr("settings."+group));
                    button.addEventListener("click",event->{category=group;signature="";focus=0;id("control-scroll").scrollTo(0,0);makeRail();});links.appendChild(button);
                }return;
            }
            for(String group:menu()==null?List.of("all"):List.of("all","inventory")){
                var button=node("button","rail-link"+(group.equals("all")?" selected":""),ArchiveTheme.tr("control.group."+group));
                button.addEventListener("click",event->{
                    if(group.equals("all"))id("control-scroll").scrollTo(0,0);
                    else{var label=id("pack-label").getBoundingClientRect();var pane=id("control-scroll").getBoundingClientRect();id("control-scroll").scrollBy(0,label.top-pane.top);document.querySelector(".pack-area").scrollTo(0,0);}
                });links.appendChild(button);
            }
        }
        void refresh(){
            model=ArchiveModel.read(owner);
            if(owner!=Minecraft.getInstance().gui.screen())return;
            if(view.equals("worlds")||view.equals("servers"))for(var control:model.controls)if(control.kind().equals("entry")&&control.source() instanceof SelectableEntry&&control.list()!=null&&control.list().getSelected()==null){ArchiveModel.select(control);break;}
            StringBuilder next=new StringBuilder();for(var control:model.controls)next.append(control.source().getClass().isRecord()?control.source().hashCode():System.identityHashCode(control.source())).append(control.kind());
            next.append(model.prose).append(category);
            if(owner instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen)next.append(((com.teamlifebind.clientui.mixin.ArchiveBookAccess)owner).tlb$page());
            if(!next.toString().equals(signature)){
                signature=next.toString();id("controls").setTextContent("");id("tab-buttons").setTextContent("");id("action-dock").setTextContent("");
                for(String recipeHost:List.of("recipe-categories","recipe-cards","recipe-pages"))id(recipeHost).setTextContent("");
                id("recipe-area").setAttribute("style",model.controls.stream().anyMatch(c->c.kind().equals("recipe-category"))?"display:flex":"display:none");
                text("recipe-heading",ArchiveTheme.tr("work.recipes"));text("recipe-empty",ArchiveTheme.tr("recipe.empty"));
                id("recipe-empty").setAttribute("style",model.controls.stream().anyMatch(c->c.kind().equals("recipe-entry"))?"display:none":"");
                controlElements.clear();visibleControls.clear();if(family.equals("title"))makeRail();int index=0;
                var ordered=new ArrayList<>(model.controls);
                if(view.equals("settings-home"))ordered.sort(Comparator.comparingInt(c->ArchiveNavigation.dock(view,c)?5:ArchiveNavigation.SETTING_GROUPS.indexOf(ArchiveNavigation.group(c))));
                String lastGroup="";
                for(var control:ordered){
                    boolean dock=ArchiveNavigation.dock(view,control);
                    boolean tab=control.kind().equals("tab");
                    if((view.equals("worlds")||view.equals("servers")||view.equals("languages"))&&control.kind().equals("input"))continue;
                    if(!dock&&view.equals("settings-home")&&!category.equals("all")&&!category.equals(ArchiveNavigation.group(control)))continue;
                    if(!dock&&view.equals("settings-home")){
                        String group=ArchiveNavigation.group(control);if(!group.equals(lastGroup)){id("controls").appendChild(node("div","settings-heading",ArchiveTheme.tr("settings."+group)));lastGroup=group;}
                    }
                    if(!control.kind().equals("label"))visibleControls.add(control);
                    if(family.equals("title")&&!ArchiveNavigation.titlePrimary(control)){
                        if(control.kind().equals("action"))addRailControl(control);
                        else if(control.kind().equals("label"))id("rail-links").appendChild(node("span","title-note",control.label()));
                        else addControl(control,++index);
                    }else addControl(control,++index);
                }
                focus=Math.clamp(focus,0,Math.max(0,visibleControls.size()-1));
                id("action-dock").setAttribute("style",!family.equals("title")&&model.controls.stream().anyMatch(c->ArchiveNavigation.dock(view,c))?"display:flex":"display:none");
                id("tabs").setAttribute("style",model.controls.stream().anyMatch(c->c.kind().equals("tab"))?"display:flex":"display:none");
                id("prose").setTextContent("");
                if(menu()==null)for(String prose:model.prose)if(!prose.equals(owner.getTitle().getString())&&!prose.isBlank()&&model.controls.stream().noneMatch(c->c.label().equals(prose)))id("prose").appendChild(node("p","prose-paragraph",prose));
                if(owner instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen){
                    id("prose").setTextContent("");var access=(com.teamlifebind.clientui.mixin.ArchiveBookAccess)owner;
                    var paragraph=node("div","prose-paragraph","");
                    access.tlb$book().getPage(access.tlb$page()).visit((style,part)->{
                        var span=node(style.getClickEvent()==null?"span":"button",style.getClickEvent()==null?"book-run":"book-link",part);
                        if(style.getClickEvent()!=null)span.addEventListener("click",event->access.tlb$link(style.getClickEvent()));paragraph.appendChild(span);return Optional.empty();
                    },net.minecraft.network.chat.Style.EMPTY);id("prose").appendChild(paragraph);
                }
                if(view.equals("worlds")||view.equals("servers")||view.equals("languages"))text("control-count",ArchiveTheme.tr("count."+view,visibleControls.stream().filter(c->c.kind().equals("entry")).count()));
                else if(family.equals("settings"))text("control-count",ArchiveTheme.tr("count.settings",visibleControls.stream().filter(c->!ArchiveNavigation.dock(view,c)).count()));
                else text("control-count",String.format("%02d",index));
                if(!visibleControls.isEmpty())inspect(visibleControls.get(focus),focus);
            }
            for(var control:model.controls){
                var e=controlElements.get(control.source());if(e==null)continue;
                boolean selected=ArchiveGameModes.selected(control)||ArchiveRecipes.selected(control)||(control.source() instanceof TabButton tab?tab.isSelected():control.kind().equals("entry")&&control.list()!=null&&control.list().getSelected()==control.source());
                String base="true".equals(e.getAttribute("data-rail-action"))?"rail-link"+("true".equals(e.getAttribute("data-legal"))?" legal-link":""):controlCss(control);
                String css=base+(control.active()?"":" disabled")+(selected?" selected":"")+(visibleControls.indexOf(control)==focus?" focused":"");
                if(!css.equals(e.getAttribute("class")))e.setAttribute("class",css);
                var label=e.querySelector(".control-label");if(label==null)label=e.querySelector(".rail-caption");if(label!=null&&!label.getTextContent().equals(control.label()))label.setTextContent(control.label());
                var detail=e.querySelector(".control-detail");
                String description="true".equals(e.getAttribute("data-rail-action"))?"":description(control);
                if(detail==null&&!description.isBlank()){detail=node("div","control-detail",description);e.querySelector(".control-copy").appendChild(detail);}
                if(detail!=null){if(!detail.getTextContent().equals(description))detail.setTextContent(description);detail.setAttribute("style",description.isBlank()?"display:none":"");}
                var input=e.querySelector(".native-input");if(input!=null&&!Element.isElementFocusing(input)){
                    String value=control.source() instanceof EditBox edit?edit.getValue():control.source() instanceof MultiLineEditBox edit?edit.getValue():control.source() instanceof ArchiveSpecials.SignLine line?line.value():input.getValue();
                    if(!value.equals(input.getValue()))input.setValue(value);
                }
                if(control.source() instanceof AbstractSliderButton slider){var fill=e.querySelector(".range-fill");var thumb=e.querySelector(".range-thumb");double value=((ArchiveSliderAccess)slider).tlb$value()*100;if(fill!=null)fill.setAttribute("style","width:"+value+"%");if(thumb!=null)thumb.setAttribute("style","left:"+value+"%");}
                if(control.source() instanceof Checkbox check){var state=e.querySelector(".toggle-state");if(state!=null)state.setAttribute("class","toggle-state"+(check.selected()?" on":""));}
                if(control.kind().equals("recipe-filter")){var state=e.querySelector(".toggle-state");if(state!=null)state.setAttribute("class","toggle-state"+(selected?" on":""));}
            }
            refreshSlots();
            if(owner instanceof ChatScreen){
                var messages=ArchiveOverlays.messages(true);String nextChat=messages.toString();
                if(!nextChat.equals(chatSignature)){chatSignature=nextChat;var host=id("conversation");host.setTextContent("");var ordered=new ArrayList<>(messages);Collections.reverse(ordered);for(var message:ordered)ArchiveOverlays.appendMessage(document,host,message.content(),owner);host.scrollTo(0,host.scrollHeight);}
                var source=(com.teamlifebind.clientui.mixin.ArchiveChatScreenAccess)owner;var suggestions=source.tlb$suggestions();
                var future=((com.teamlifebind.clientui.mixin.ArchiveSuggestionsAccess)suggestions).tlb$pending();
                var values=future!=null&&future.isDone()&&!future.isCompletedExceptionally()&&!future.isCancelled()?future.getNow(null):null;
                String stamp=source.tlb$input().getValue()+":"+(values==null?"":values.getList());
                if(!stamp.equals(suggestionsSignature)){suggestionsSignature=stamp;var host=id("completions");host.setTextContent("");
                    if(values!=null)for(var suggestion:values.getList().stream().limit(8).toList()){
                        var option=node("button","completion",suggestion.getText());option.addEventListener("click",event->{
                            if(owner!=Minecraft.getInstance().gui.screen())return;source.tlb$input().setValue(suggestion.apply(source.tlb$input().getValue()));source.tlb$input().moveCursorToEnd(false);var input=document.querySelector(".native-input");if(input!=null){ArchiveTextInput.initialize(input,source.tlb$input());input.focus();}
                        });host.appendChild(option);
                    }
                }
            }
        }
        void motionState(){document.body.setAttribute("data-motion",AuiMenuSettings.motion()?"on":"off");text("interface-motion",ArchiveTheme.tr(AuiMenuSettings.motion()?"motion.on":"motion.off"));id("interface-motion").setAttribute("title",ArchiveTheme.tr("motion.hint"));}
        void toggleMotion(){AuiMenuSettings.toggleMotion();ink.finish();motionState();}
        String description(ArchiveModel.Control control){
            if(family.equals("title")&&control.source() instanceof AbstractWidget widget){
                var keys=ArchiveNavigation.keys(widget.getMessage());
                if(keys.contains("menu.singleplayer"))return ArchiveTheme.tr("journey.singleplayer_detail");
                if(keys.contains("menu.multiplayer"))return ArchiveTheme.tr("journey.multiplayer_detail");
                if(keys.contains("menu.online"))return ArchiveTheme.tr("journey.realms_detail");
            }return control.detail();
        }
        void addRailControl(ArchiveModel.Control control){
            var button=node("button","rail-link","");
            button.appendChild(node("span","rail-caption",control.label()));
            button.setAttribute("data-rail-action","true");
            button.setAttribute("data-priority",ArchiveNavigation.priority(control));
            if(control.label().startsWith("©"))button.setAttribute("data-legal","true");
            button.addEventListener("click",event->activate(control,false));
            id("rail-links").appendChild(button);
            controlElements.put(control.source(),button);
        }
        String controlCss(ArchiveModel.Control control){return "control"+(control.kind().startsWith("recipe-")?" "+control.kind():"")+(control.kind().equals("tab")?" tab-action":"")+(control.kind().equals("input")?" input-row":"")+(control.kind().equals("slider")?" slider-row":"")+(control.kind().equals("entry")?" entry-row":"")+(ArchiveNavigation.dock(view,control)?" dock-action":"");}
        void addControl(ArchiveModel.Control control,int number){
            if(control.kind().equals("label")){id("controls").appendChild(node("div","control-label-only",control.label()));return;}
            var e=node(control.kind().equals("input")?"div":"button",controlCss(control),"");
            e.setAttribute("id","native-control-"+number);e.setAttribute("tabindex","-1");
            e.setAttribute("data-priority",ArchiveNavigation.priority(control));
            if(family.equals("title"))e.setAttribute("data-journey",ArchiveNavigation.journeyAction(control));
            e.appendChild(node("span","control-number",String.format("%02d",number)));
            var copy=node("div","control-copy","");copy.appendChild(node("div","control-label",control.label()));
            String description=description(control);if(!description.isBlank())copy.appendChild(node("div","control-detail",description));e.appendChild(copy);
            if(control.source() instanceof EditBox edit){
                var input=node("input","native-input","");ArchiveTextInput.initialize(input,edit);
                input.addEventListener("focus",event->{owner.setFocused(edit);edit.setFocused(true);});
                input.addEventListener("input",event->{if(current(control)&&owner==Minecraft.getInstance().gui.screen()&&event.currentTarget instanceof Element live)ArchiveTextInput.changed(live,edit);});
                e.appendChild(input);
            }else if(control.source() instanceof MultiLineEditBox edit){
                var input=node("textarea","native-input multiline","");input.setValue(edit.getValue());
                input.addEventListener("input",event->{if(current(control)&&event.currentTarget instanceof Element live){edit.setValue(live.getValue());if(!edit.getValue().equals(live.getValue()))live.setValue(edit.getValue());}});e.appendChild(input);
            }else if(control.source() instanceof ArchiveSpecials.SignLine line){
                var input=node("input","native-input","");input.setValue(line.value());input.addEventListener("input",event->{if(event.currentTarget instanceof Element live){line.set(live.getValue());if(!line.value().equals(live.getValue()))live.setValue(line.value());}});e.appendChild(input);
            }else if(control.source() instanceof AbstractSliderButton slider){
                var track=node("div","range-track","");var fill=node("div","range-fill","");double value=((ArchiveSliderAccess)slider).tlb$value()*100;fill.setAttribute("style","width:"+value+"%");track.appendChild(fill);var thumb=node("span","range-thumb","");thumb.setAttribute("style","left:"+value+"%");track.appendChild(thumb);e.appendChild(track);
                e.addEventListener("mousedown",event->{if(current(control)&&event.currentTarget instanceof Element live)beginSlider(slider,live);});
            }else if(control.kind().equals("recipe-filter")){e.appendChild(node("span","toggle-state"+(ArchiveRecipes.selected(control)?" on":""),""));}
            else if(control.source() instanceof Checkbox checkbox){e.appendChild(node("span","toggle-state"+(checkbox.selected()?" on":""),""));}
            else {ItemStack image=control.source() instanceof ArchiveGameModes.Choice mode?mode.icon():control.source() instanceof ArchiveSpecials.Choice choice?choice.icon():control.source() instanceof ArchiveSpecials.AdvancementEntry entry?entry.icon():control.source() instanceof net.minecraft.client.gui.screens.recipebook.RecipeButton recipe?recipe.getDisplayStack():ItemStack.EMPTY;if(!image.isEmpty()){var icon=node("div","choice-icon","");icon.appendChild(item(image));e.appendChild(icon);}e.appendChild(node("span","control-arrow",control.kind().equals("entry")&&(view.equals("worlds")||view.equals("servers"))?ArchiveTheme.tr("journey.entry_hint"):"›"));}
            e.addEventListener("mouseenter",event->{var live=resolve(control);if(live!=null){focus=Math.max(0,visibleControls.indexOf(live));inspect(live,focus);}});
            e.addEventListener("click",event->activate(control,false,twiceClick));
            e.addEventListener("contextmenu",event->{event.preventDefault();activate(control,true);});
            (control.kind().equals("tab")?id("tab-buttons"):ArchiveNavigation.dock(view,control)?id("action-dock"):id(ArchiveRecipes.host(control))).appendChild(e);controlElements.put(control.source(),e);
        }
        ArchiveModel.Control resolve(ArchiveModel.Control control){return model.controls.stream().filter(c->c.source()==control.source()||c.source().equals(control.source())).findFirst().orElse(null);}
        boolean current(ArchiveModel.Control control){var live=resolve(control);return live!=null&&live.active()&&(!(live.source() instanceof AbstractWidget widget)||widget.active&&widget.visible);}
        void activate(ArchiveModel.Control control,boolean alternate){activate(control,alternate,false);}
        void activate(ArchiveModel.Control control,boolean alternate,boolean confirm){
            if(owner!=Minecraft.getInstance().gui.screen()||!current(control))return;
            control=resolve(control);
            if(control.source() instanceof ArchiveSpecials.HudEditorAction edit){edit.activate();return;}
            if(control.source() instanceof ArchiveGameModes.Choice mode){mode.activate();return;}
            ink.finish();
            if(control.kind().equals("entry")&&control.list()!=null){if(alternate){inspect(control,visibleControls.indexOf(control));return;}owner.setFocused(control.list());ArchiveModel.activateEntry(control,confirm);signature="";return;}
            if(control.source() instanceof ArchiveSpecials.AdvancementEntry){inspect(control,model.controls.indexOf(control));return;}
            if(control.kind().equals("input")){var element=controlElements.get(control.source());if(element!=null){var input=element.querySelector(".native-input");if(input!=null)input.focus();}return;}
            if(control.source() instanceof AbstractSliderButton)return;
            if(model.relays.containsKey(control.source())&&control.source() instanceof LayoutElement rect){
                var input=new MouseButtonEvent(rect.getX()+rect.getWidth()/2,rect.getY()+rect.getHeight()/2,new MouseButtonInfo(alternate?InputConstants.MOUSE_BUTTON_RIGHT:InputConstants.MOUSE_BUTTON_LEFT,0));
                var relay=model.relays.get(control.source());relay.mouseClicked(input,twiceClick);relay.mouseReleased(input);
            }
            else if(control.source() instanceof ArchiveSpecials.Choice choice)choice.activate();
            else if(control.source() instanceof ArchiveSpecials.CreativeTab tab)tab.activate();
            else if(control.source() instanceof AbstractButton button){button.playDownSound(Minecraft.getInstance().getSoundManager());button.onPress(new KeyEvent(InputConstants.KEY_RETURN,0,alternate?InputConstants.MOD_SHIFT:0));}
            else if(control.source() instanceof GuiEventListener listener){
                ArchiveModel.select(control);int x=0,y=0;if(control.source() instanceof LayoutElement rect){x=rect.getX()+rect.getWidth()/2;y=rect.getY()+rect.getHeight()/2;}
                var input=new MouseButtonEvent(x,y,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));listener.mouseClicked(input,twiceClick);listener.mouseReleased(input);
            }
            signature="";
        }
        void inspect(ArchiveModel.Control control,int index){
            var live=resolve(control);if(live!=null)control=live;
            text("inspect-title",control.label());text("inspect-copy",control.detail().isBlank()?ArchiveTheme.tr("intro."+family):control.detail());
            text("inspect-index",String.format("%02d",index+1));text("inspect-status",control.active()?ArchiveTheme.tr("control.available"):ArchiveTheme.tr("control.unavailable"));
        }
        void refreshSlots(){
            var menu=menu();if(menu==null||menu.slots.isEmpty()){id("work-label").setAttribute("style","display:none");id("pack-label").setAttribute("style","display:none");return;}
            String status=ArchiveWorkstation.status(menu);text("work-status",status);id("work-status").setAttribute("style",status.isBlank()?"display:none":"");
            text("work-label",workspace.key().equals("storage")?owner.getTitle().getString():ArchiveTheme.tr("work.title."+workspace.key()));
            text("work-instructions",ArchiveTheme.tr("work.hint."+workspace.key()));text("pack-label",ArchiveTheme.tr("storage.inventory"));
            var meters=ArchiveWorkstation.meters(menu);
            if(id("work-progress").getChildren().size()!=meters.size()){
                id("work-progress").setTextContent("");
                for(var meter:meters){var row=node("div","process","");row.setAttribute("id","meter-"+meter.key());row.appendChild(node("span","process-label",ArchiveTheme.tr("work.meter."+meter.key())));row.appendChild(node("span","process-value",""));var track=node("div","process-track","");track.appendChild(node("div","process-fill",""));row.appendChild(track);id("work-progress").appendChild(row);}
            }
            for(var meter:meters){var row=id("meter-"+meter.key());row.querySelector(".process-value").setTextContent(meter.value());row.querySelector(".process-fill").setAttribute("style","width:"+Math.clamp(meter.fraction()*100,0,100)+"%");}
            var slots=menu.slots.stream().filter(s->s.isActive()&&s.x>-100&&s.y>-100).toList();
            Set<Integer> indices=new LinkedHashSet<>();for(var s:slots)indices.add(s.index);
            if(!slotElements.keySet().equals(indices)||slots.stream().anyMatch(slot->sourceSlots.get(slot.index)!=slot)){
                hovered=-1;nativeHover(null);
                id("work-slots").setTextContent("");id("pack-main").setTextContent("");id("pack-hotbar").setTextContent("");id("gear-slots").setTextContent("");text("pack-hotbar-label",ArchiveTheme.tr("storage.hotbar"));slotElements.clear();renderedStacks.clear();sourceSlots.clear();
                var zones=new HashMap<Integer,Element>();
                for(var zone:workspace.zones()){
                    var panel=node("section","workspace-zone zone-"+zone.key(),"");panel.appendChild(node("div","zone-title",ArchiveTheme.tr("work.zone."+zone.key())));
                    var grid=node("div","zone-slots","");grid.setAttribute("style","grid-template-columns:repeat("+zone.columns()+",minmax(0,1fr))");panel.appendChild(grid);id("work-slots").appendChild(panel);
                    for(int index:zone.slots())zones.put(index,grid);
                }
                for(var slot:slots){
                    var e=node("div","slot","");e.setAttribute("id","inventory-slot-"+slot.index);
                    var icon=node("div","slot-icon","");e.appendChild(icon);e.appendChild(node("span","slot-count",""));e.appendChild(node("span","slot-number",""));
                    e.addEventListener("mouseenter",event->{hovered=slot.index;inspectSlot(slot);nativeHover(slot);});
                    e.addEventListener("mouseleave",event->{if(hovered==slot.index){hovered=-1;nativeHover(null);}});
                    e.addEventListener("mousedown",event->{if(held!=null)pressSlot(slot.index);});
                    boolean gear=menu instanceof InventoryMenu&&(slot.index>=5&&slot.index<=8||slot.index==45);
                    boolean inventory=slot.container instanceof Inventory;
                    (gear?id("gear-slots"):inventory?id(slot.getContainerSlot()<9?"pack-hotbar":"pack-main"):zones.getOrDefault(slot.index,id("work-slots"))).appendChild(e);slotElements.put(slot.index,e);
                    sourceSlots.put(slot.index,slot);
                }
            }
            text("control-count",ArchiveTheme.tr("count.slots",slots.size()));
            for(var slot:slots){
                var e=slotElements.get(slot.index);ItemStack item=slot.getItem(),before=renderedStacks.get(slot.index);
                boolean locked=menu instanceof CrafterMenu crafter&&slot.index<9&&crafter.isSlotDisabled(slot.index);
                String css="slot"+(locked?" locked":"")+(slot instanceof NonInteractiveResultSlot?" readonly":"");
                if(!css.equals(e.getAttribute("class")))e.setAttribute("class",css);
                String marker=locked?"×":slot.container instanceof Inventory&&slot.getContainerSlot()<9?String.valueOf(slot.getContainerSlot()+1):"";
                if(menu instanceof InventoryMenu&&slot.index>=5&&slot.index<=8)marker=ArchiveTheme.tr("work.gear."+slot.index);
                if(menu instanceof InventoryMenu&&slot.index==45)marker=ArchiveTheme.tr("work.gear.offhand");
                if(!marker.equals(e.querySelector(".slot-number").getTextContent()))e.querySelector(".slot-number").setTextContent(marker);
                if(before==null||!ItemStack.matches(before,item)){
                    renderedStacks.put(slot.index,item.copy());var icon=e.querySelector(".slot-icon");icon.setTextContent("");
                    if(!item.isEmpty())icon.appendChild(item(item));
                    e.querySelector(".slot-count").setTextContent(item.getCount()>1?String.valueOf(item.getCount()):"");
                }
            }
            var carried=menu.getCarried();var floating=id("carried");
            if(carried.isEmpty())floating.setTextContent("");
            else {var old=floating.getAttribute("data-item");String stamp=carried.toString();if(!stamp.equals(old)){floating.setAttribute("data-item",stamp);floating.setTextContent("");floating.appendChild(item(carried));floating.appendChild(node("span","slot-count",String.valueOf(carried.getCount())));}}
        }
        Item item(ItemStack stack){var item=new Item(document);item.setAttribute("style","width:100%;height:100%");var icon=stack.copy();icon.setCount(1);item.setIngredientStack(icon);return item;}
        void nativeHover(Slot slot){if(container()!=null)((ArchiveContainerAccess)container()).tlb$hovered(slot);}
        void inspectSlot(Slot slot){
            ItemStack item=slot.getItem();if(item.isEmpty()){
                var zone=workspace.zones().stream().filter(z->z.slots().contains(slot.index)).findFirst();
                text("inspect-title",ArchiveTheme.tr(zone.isPresent()?"work.zone."+zone.get().key():"storage.inventory"));text("inspect-copy",ArchiveTheme.tr("work.hint."+workspace.key()));text("inspect-status",ArchiveTheme.tr("work.empty"));id("inspect-icon").setAttribute("style","display:none");return;
            }
            text("inspect-title",item.getHoverName().getString());
            text("inspect-copy",String.join("\n",Screen.getTooltipFromItem(Minecraft.getInstance(),item).stream().map(Component::getString).toList()));
            text("inspect-status",ArchiveTheme.tr("storage.stack",item.getCount(),item.getMaxStackSize()));
            var icon=id("inspect-icon");icon.setAttribute("style","display:flex");icon.setTextContent("");icon.appendChild(item(item));id("inspect-index").setAttribute("style","display:none");
        }
        MouseButtonEvent nativeEvent(int index,MouseButtonInfo info){
            var access=(ArchiveContainerAccess)container();var slot=index<0?null:menu().getSlot(index);
            return new MouseButtonEvent(access.tlb$left()+(slot==null?1:slot.x+8),access.tlb$top()+(slot==null?1:slot.y+8),info);
        }
        void pressSlot(int index){
            if(container()==null||Minecraft.getInstance().player==null||Minecraft.getInstance().player.containerMenu!=menu())return;
            var slot=menu().getSlot(index);if(!ItemStack.matches(renderedStacks.getOrDefault(index,ItemStack.EMPTY),slot.getItem()))return;
            hovered=index;nativeHover(slot);container().mouseClicked(nativeEvent(index,held),twiceClick);nativePressed=true;
        }
        void render(GuiGraphicsExtractor g,int x,int y){
            try(var ignored=Document.withContext(document)){
                if(generation!=document.getRefreshGeneration())bind();
                if(AuiPageReadiness.placeholder(g,document))return;
                if(windowWidth!=owner.width||windowHeight!=owner.height){windowWidth=owner.width;windowHeight=owner.height;document.applyViewport(true);ink.finish();}
                if(family.equals("gamemode")){
                    var surface=document.querySelector(".surface");style(surface,"transform","none");style(surface,"right","auto");style(surface,"bottom","auto");
                    var box=surface.getBoundingClientRect();var viewport=document.getViewportSize();
                    style(surface,"left",Math.max(12,(viewport.width()-box.width)/2)+"px");style(surface,"top",Math.max(12,(viewport.height()-box.height)/2)+"px");
                }
                if(pointerSeen&&(x!=mouseX||y!=mouseY))document.body.setAttribute("data-input","mouse");pointerSeen=true;
                mouseX=x;mouseY=y;if(System.nanoTime()-scanned>50_000_000L){refresh();motionState();scanned=System.nanoTime();}if(owner!=Minecraft.getInstance().gui.screen())return;
                updateMotion();
                Operation.onMouseMove(new Position(x,y));
                if(dragging!=null){Element element=controlElements.get(dragging);if(element!=null)updateSlider(dragging,element);}
                var view=document.getViewportSize();id("carried").setAttribute("style","left:"+(x/(double)Math.max(1,owner.width)*view.width()+8)+"px;top:"+(y/(double)Math.max(1,owner.height)*view.height()+8)+"px");
                ScreenDocumentCache.attach(document);ApricityGuiLayers.submitOverlay(g);
                if(menu() instanceof InventoryMenu&&Minecraft.getInstance().player!=null){
                    var rect=id("avatar").getBoundingClientRect();var a=document.documentToScreenPosition(new Position(rect.left,rect.top));var b=document.documentToScreenPosition(new Position(rect.right,rect.bottom));
                    if(b.x-a.x>24&&b.y-a.y>50)net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g,(int)a.x,(int)a.y,(int)b.x,(int)b.y,(int)Math.min((b.x-a.x)*.7,(b.y-a.y)*.45),.0625F,x,y,Minecraft.getInstance().player);
                }
            }
        }
        void updateMotion(){
            for(var control:model.controls)if(control.source() instanceof TabButton tab&&tab.isSelected()){
                var element=controlElements.get(control.source());if(element==null)continue;
                var box=element.getBoundingClientRect();var host=id("tabs").getBoundingClientRect();
                if(box.width>0)id("tab-indicator").setAttribute("style","width:"+box.width+"px;transform:translateX("+(box.left-host.left)+"px)");
                break;
            }
            var scroller=id("control-scroll");double extent=scroller.scrollHeight-scroller.getClientHeight();
            boolean overflow=menu()==null&&extent>40;
            id("read-progress").setAttribute("style",overflow?"":"display:none");
            if(overflow)id("read-progress-fill").setAttribute("style","width:"+Math.clamp(scroller.getScrollTop()/extent*100,0,100)+"%");
            document.body.setAttribute("data-scrolled",scroller.getScrollTop()>2?"true":"false");
        }
        void beginSlider(AbstractSliderButton slider,Element e){
            ink.finish();document.clearFocus();e.focus();owner.setFocused(slider);
            for(int i=0;i<visibleControls.size();i++)if(visibleControls.get(i).source()==slider){focus=i;break;}
            var track=e.querySelector(".range-track");var rect=(track==null?e:track).getBoundingClientRect();
            var left=document.documentToScreenPosition(new Position(rect.left,rect.top));var right=document.documentToScreenPosition(new Position(rect.right,rect.bottom));
            if(mouseX<left.x-6||mouseX>right.x+6)return;
            dragging=slider;updateSlider(slider,e);
        }
        void updateSlider(AbstractSliderButton slider,Element e){
            var track=e.querySelector(".range-track");var rect=(track==null?e:track).getBoundingClientRect();var left=document.documentToScreenPosition(new Position(rect.left,rect.top));var right=document.documentToScreenPosition(new Position(rect.right,rect.bottom));
            ((ArchiveSliderAccess)slider).tlb$value(Math.clamp((mouseX-left.x)/Math.max(1,right.x-left.x),0,1));signature="";
        }
        boolean mouse(MouseButtonEvent event,boolean down,boolean twice){
            if(!AuiPageReadiness.ready(document)||AuiScreenTransitions.active())return true;
            if(owner instanceof net.minecraft.client.gui.screens.options.controls.KeyBindsScreen keys&&keys.selectedKey!=null){if(down)owner.mouseClicked(event,twice);else owner.mouseReleased(event);return true;}
            try(var ignored=Document.withContext(document)){
                document.body.setAttribute("data-input","mouse");mouseX=event.x();mouseY=event.y();if(down){twiceClick=twice;held=event.buttonInfo();}
                Operation.onMouseMove(new Position(event.x(),event.y()));
                int button=event.button()==InputConstants.MOUSE_BUTTON_LEFT?0:event.button()==InputConstants.MOUSE_BUTTON_RIGHT?1:2;
                var input=new MouseEvent(down?"mousedown":"mouseup",new Position(event.x(),event.y()),button);input.setTrusted(true);MouseEvent.tiggerEvent(input,document);
                if(!down){if(nativePressed&&container()!=null)container().mouseReleased(nativeEvent(hovered,event.buttonInfo()));nativePressed=false;held=null;dragging=null;}
                return true;
            }
        }
        boolean drag(MouseButtonEvent event,double dx,double dy){
            mouseX=event.x();mouseY=event.y();if(nativePressed&&container()!=null)container().mouseDragged(nativeEvent(hovered,event.buttonInfo()),dx,dy);return true;
        }
        boolean scroll(double x,double y,double dx,double dy){
            if(AuiScreenTransitions.active())return true;
            if(owner instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen){var point=document.screenToDocumentPosition(new Position(x,y));var rect=id("work-slots").getBoundingClientRect();if(point.x>=rect.left&&point.x<=rect.right&&point.y>=rect.top&&point.y<=rect.bottom){owner.mouseScrolled(x,y,dx,dy);return true;}}
            try(var ignored=Document.withContext(document)){var event=new MouseEvent("wheel",new Position(x,y));event.deltaX=-dx*50;event.deltaY=-dy*50;event.scrollDelta=event.deltaY;event.setTrusted(true);MouseEvent.tiggerEvent(event,document);return true;}
        }
        boolean character(CharacterEvent event){if(!AuiPageReadiness.ready(document))return true;try(var ignored=Document.withContext(document)){Operation.onCharTyped(event.codepoint());return true;}}
        boolean key(KeyEvent event){
            if(owner instanceof net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen)return owner.keyPressed(event);
            try(var ignored=Document.withContext(document)){
                if(!AuiPageReadiness.ready(document)){if(event.isEscape())AuiScreenTransitions.back(()->owner.keyPressed(event));return true;}
                ink.finish();
                document.body.setAttribute("data-input","keyboard");
                if(owner instanceof net.minecraft.client.gui.screens.options.controls.KeyBindsScreen keys&&keys.selectedKey!=null)return owner.keyPressed(event);
                if(event.hasControlDown()&&event.hasShiftDown()&&event.key()==InputConstants.KEY_M){toggleMotion();return true;}
                if(event.key()==InputConstants.KEY_TAB||event.key()==InputConstants.KEY_UP||event.key()==InputConstants.KEY_DOWN||event.key()==InputConstants.KEY_LEFT||event.key()==InputConstants.KEY_RIGHT)AuiScreenTransitions.finish();
                if(event.isEscape()){AuiScreenTransitions.back(()->owner.keyPressed(event));return true;}
                if(view.equals("create-world")&&event.hasControlDown()&&event.key()==InputConstants.KEY_TAB){owner.keyPressed(event);return true;}
                if(owner instanceof ChatScreen&&(event.isConfirmation()||event.key()==InputConstants.KEY_TAB||event.key()==InputConstants.KEY_UP||event.key()==InputConstants.KEY_DOWN||event.key()==InputConstants.KEY_PAGEUP||event.key()==InputConstants.KEY_PAGEDOWN)){
                    var source=(com.teamlifebind.clientui.mixin.ArchiveChatScreenAccess)owner;var input=document.querySelector(".native-input");if(input!=null)ArchiveTextInput.changed(input,source.tlb$input());owner.keyPressed(event);if(input!=null&&owner==Minecraft.getInstance().gui.screen()){ArchiveTextInput.initialize(input,source.tlb$input());input.focus();}return true;
                }
                boolean editing=document.querySelectorAll("input").stream().anyMatch(Element::isElementFocusing)||document.querySelectorAll("textarea").stream().anyMatch(Element::isElementFocusing);
                if(event.hasControlDown()&&event.key()==InputConstants.KEY_F)return true;
                if(editing){if(event.isConfirmation()&&owner.getClass().getSimpleName().equals("ChatScreen"))return owner.keyPressed(event);return Operation.handleKeyInput(event.key(),event.keycode(),InputConstants.PRESS,event.modifiers(),false,com.teamlifebind.aui.event.KeyEvent.Source.SCREEN_EVENT);}
                if((event.key()==InputConstants.KEY_LEFT||event.key()==InputConstants.KEY_RIGHT)&&!visibleControls.isEmpty()&&visibleControls.get(Math.clamp(focus,0,visibleControls.size()-1)).source() instanceof AbstractSliderButton slider){owner.setFocused(slider);if(!slider.keyPressed(event)){var access=(ArchiveSliderAccess)slider;access.tlb$value(Math.clamp(access.tlb$value()+(event.key()==InputConstants.KEY_RIGHT?1:-1)/(double)Math.max(1,slider.getWidth()-8),0,1));}return true;}
                if(menu()!=null){nativeHover(hovered<0?null:menu().getSlot(hovered));return owner.keyPressed(event);}
                if(event.isConfirmation()&&!visibleControls.isEmpty()){activate(visibleControls.get(Math.clamp(focus,0,visibleControls.size()-1)),event.hasShiftDown(),true);return true;}
                if(event.key()==InputConstants.KEY_DOWN||event.key()==InputConstants.KEY_UP||event.key()==InputConstants.KEY_TAB){
                    int step=event.key()==InputConstants.KEY_UP||event.hasShiftDown()?-1:1;
                    for(int attempt=0;attempt<Math.max(1,visibleControls.size());attempt++){focus=Math.floorMod(focus+step,Math.max(1,visibleControls.size()));if(visibleControls.isEmpty()||visibleControls.get(focus).active())break;}
                    if(!visibleControls.isEmpty()){
                        var selected=visibleControls.get(focus);inspect(selected,focus);var element=controlElements.get(selected.source());
                        if(element!=null){element.focus();if(!"true".equals(element.getAttribute("data-rail-action"))){var rect=element.getBoundingClientRect();var pane=id("control-scroll").getBoundingClientRect();if(rect.bottom>pane.bottom)id("control-scroll").scrollBy(0,rect.bottom-pane.bottom+8);else if(rect.top<pane.top)id("control-scroll").scrollBy(0,rect.top-pane.top-8);}}
                    }return true;
                }
                return owner.keyPressed(event);
            }
        }
    }
}
