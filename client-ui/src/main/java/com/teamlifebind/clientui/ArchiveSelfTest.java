package com.teamlifebind.clientui;

import com.mojang.blaze3d.platform.InputConstants;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.screen.AuiLinkedScreen;
import com.teamlifebind.common.NativeHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Explicitly enabled, isolated full-client coverage. Does not create/open a world or connect. */
final class ArchiveSelfTest {
    private static final boolean TITLE_ONLY=Boolean.getBoolean("teamlifebind.archive.titleOnly");
    private static final String[] KINDS=TITLE_ONLY?new String[]{"title"}:java.util.stream.Stream.concat(Boolean.getBoolean("teamlifebind.render.compat.optionsOnly")?java.util.stream.Stream.of("title","options","video"):java.util.stream.Stream.of("title","pause","options","video","online","sound","mouse","controls","keybinds","accessibility","chat-options","skin","language","worlds","servers","create-game","create-world","create-more","confirm","book","widgets","widgets-reload","chest27","chest54","hopper","dispenser","magic","loading"),System.getProperty("teamlifebind.render.compat.test","").equals("iris")?java.util.stream.Stream.of("iris-options"):java.util.stream.Stream.empty()).toArray(String[]::new);
    private static final int[][] SIZES={{1920,1080,2},{1440,900,2},{900,600,3}};
    private static final boolean MOTION_ONLY=Boolean.getBoolean("teamlifebind.archive.motionOnly");
    private static final Path OUTPUT=Path.of(System.getProperty(MOTION_ONLY?"teamlifebind.archive.motionOutput":"teamlifebind.archive.output","tlb-archive-selftest"));
    private static final List<String> results=new ArrayList<>();
    private static int ticks,ready,stage,opened;
    private static Screen first,current;
    private static net.minecraft.client.gui.screens.worldselection.CreateWorldScreen wizard;
    private static int wizardStep;
    private static EditBox wizardName,wizardSeed;
    private static boolean started,done,capturing;
    private static volatile boolean captured;
    private static volatile Throwable captureError;
    private static ArchiveLoadingProbe loadingProbe;
    private static boolean loadingChecked;
    private static boolean pageReloaded;
    private static ArchiveScreenMotionProbe motionProbe;
    private static boolean motionChecked;
    private static ArchiveBackdropProbe backdropProbe;
    private static boolean backdropChecked;
    static void tick() {
        if(done)return;
        try {
            var mc=Minecraft.getInstance();ticks++;
            require(ticks<6000,"Archive client test timeout");
            if(!started) {
                if(mc.gui.overlay()!=null||mc.gui.screen()==null||ticks<30){ready=0;return;}
                if(first!=mc.gui.screen()){first=mc.gui.screen();ready=0;}
                if(++ready<35)return;
                Files.createDirectories(OUTPUT);reloadCallbacks();started=true;open();return;
            }
            if(capturing) {
                if(!captured)return;if(captureError!=null)throw new IllegalStateException("Capture failed",captureError);
                if(kind().equals("loading")&&!loadingChecked){
                    if(loadingProbe==null)loadingProbe=new ArchiveLoadingProbe(OUTPUT,value->((LoadingFixture)current).reveal=value,results);
                    if(!loadingProbe.tick())return;loadingChecked=true;loadingProbe=null;
                }
                capturing=false;check();
                mc.gui.setScreen(null);ArchiveChrome.close();ArchivePresenter.close();
                require(Document.getAll().isEmpty(),"Closed screen leaked a DOM document");
                stage++;if(stage==KINDS.length*SIZES.length){finish(null);return;}open();return;
            }
            if(current==null){
                if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen created){wizard=created;current=created;opened=ticks;}
                else return;
            }
            if(kind().equals("widgets-reload")&&!pageReloaded&&ticks-opened>=8){Document.refreshAll();pageReloaded=true;opened=ticks;return;}
            if(ticks-opened<35)return;
            if(stage==0&&!motionChecked){
                if(!backdropChecked){if(backdropProbe==null)backdropProbe=new ArchiveBackdropProbe(OUTPUT,results);if(!backdropProbe.tick())return;backdropProbe=null;backdropChecked=true;opened=ticks;return;}
                if(motionProbe==null)motionProbe=new ArchiveScreenMotionProbe(current,OUTPUT,results);
                if(!motionProbe.tick())return;
                motionProbe=null;motionChecked=true;opened=ticks;return;
            }
            require(mc.gui.screen()==current,"A fixture unexpectedly changed screen");
            if(MOTION_ONLY){finish(null);return;}
            require(!AuiScreenTransitions.active(),"Screen liquid transition failed to complete for "+kind());
            if(kind().startsWith("create-")&&!clickWizardTabs())return;
            captured=false;capturing=true;
            Path png=OUTPUT.resolve(new String[]{"wide","medium","compact"}[stage/KINDS.length]+"-"+kind()+".png");
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{
                try(image){int detail=0;for(int c:image.getPixels())if((c>>16&255)>100&&(c>>8&255)>90)detail++;
                    require(detail>200,"UI is empty: "+kind());image.writeToFile(png);results.add("PASS "+png.getFileName()+" visiblePixels="+detail);
                }catch(Throwable failure){captureError=failure;}captured=true;
            });
        }catch(Throwable failure){finish(failure);}
    }
    private static String kind(){return KINDS[stage%KINDS.length];}
    private static void open() throws Exception {
        var mc=Minecraft.getInstance();int[] size=SIZES[stage/KINDS.length];
        mc.getWindow().setWindowed(size[0],size[1]);mc.options.guiScale().set(size[2]);mc.resizeGui();
        current=switch(kind()) {
            case "title"->new TitleScreen();case "pause"->new PauseFixture();
            case "options"->new OptionsScreen(new TitleScreen(),mc.options);
            case "video"->System.getProperty("teamlifebind.render.compat.test","").isEmpty()?new net.minecraft.client.gui.screens.options.VideoSettingsScreen(new TitleScreen(),mc,mc.options):(Screen)Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen").getMethod("createScreen",Screen.class).invoke(null,new TitleScreen());
            case "iris-options"->{var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");yield (Screen)api.getMethod("openMainIrisScreenObj",Object.class).invoke(api.getMethod("getInstance").invoke(null),new TitleScreen());}
            case "online","sound","mouse","accessibility","chat-options","skin"->options(switch(kind()){
                case "online"->"OnlineOptionsScreen";case "sound"->"SoundOptionsScreen";case "mouse"->"MouseSettingsScreen";
                case "accessibility"->"AccessibilityOptionsScreen";case "chat-options"->"ChatOptionsScreen";default->"SkinCustomizationScreen";});
            case "keybinds"->new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(new TitleScreen(),mc.options);
            case "controls"->new ControlsScreen(new TitleScreen(),mc.options);
            case "create-game"->{wizard=null;net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(mc,()->mc.gui.setScreen(new TitleScreen()));yield null;}
            case "create-world","create-more"->wizard;
            case "confirm"->new ConfirmScreen(confirmed->{},Component.literal("删除前确认"),Component.literal("这是一个隔离的确认界面测试，不会删除任何存档。"));
            case "worlds"->new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(new TitleScreen());
            case "servers"->{
                var servers=new net.minecraft.client.multiplayer.ServerList(mc);servers.load();
                if(servers.get("127.0.0.1:25586")==null){servers.add(new net.minecraft.client.multiplayer.ServerData("本地测试服务器 / LOCAL", "127.0.0.1:25586",net.minecraft.client.multiplayer.ServerData.Type.OTHER),false);servers.save();}
                yield new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(new TitleScreen());
            }
            case "language"->(Screen)Class.forName("net.minecraft.client.gui.screens.options.LanguageSelectScreen")
                .getConstructor(Screen.class,net.minecraft.client.Options.class,net.minecraft.client.resources.language.LanguageManager.class)
                .newInstance(new TitleScreen(),mc.options,mc.getLanguageManager());
            case "book"->new BookViewScreen();case "widgets","widgets-reload"->new WidgetFixture();
            case "chest27"->new StorageFixture(27);case "chest54"->new StorageFixture(54);
            case "hopper"->new StorageFixture(5);case "dispenser"->new StorageFixture(9);
            case "magic"->new MagicFixture();default->new LoadingFixture();
        };
        if(current!=null)mc.gui.setScreen(current);
        opened=ticks;pageReloaded=false;wizardStep=0;
    }
    /** Use the same window mouse callbacks as a player; directly selecting a native tab hid this regression. */
    private static boolean clickWizardTabs(){
        var doc=ArchivePresenter.document();if(doc==null||!AuiPageReadiness.ready(doc))return false;
        int[] route=kind().equals("create-game")?new int[]{1,2,0}:new int[]{kind().equals("create-world")?1:2};
        var bar=current.children().stream().filter(net.minecraft.client.gui.components.tabs.TabNavigationBar.class::isInstance).map(net.minecraft.client.gui.components.tabs.TabNavigationBar.class::cast).findFirst().orElseThrow();
        var nativeTabs=bar.children().stream().filter(TabButton.class::isInstance).map(TabButton.class::cast).toList();
        try(var ignored=Document.withContext(doc)){
            var tabs=doc.querySelectorAll(".tab-action");require(tabs.size()==3,"Projected wizard tabs missing");
            if(wizardStep>0){
                int expected=route[wizardStep-1];
                require(nativeTabs.get(expected).isSelected(),"Real HTML mouse click did not switch native wizard tab: "+expected);
                require(tabs.get(expected).getAttribute("class").contains(" selected"),"Selected wizard tab did not refresh in HTML");
            }
            if(kind().equals("create-game")){
                if(wizardStep==0)wizardName=writeWizardInput(doc,"Archive tab check");
                if(wizardStep==1)wizardSeed=writeWizardInput(doc,"31415926");
                if(wizardStep==route.length){
                    require(wizardName.getValue().equals("Archive tab check")&&wizardSeed.getValue().equals("31415926"),"Wizard navigation lost world name or seed");
                    require(doc.querySelector(".native-input").getValue().equals("Archive tab check"),"Returning to Game lost the edited world name");
                }
            }else if(kind().equals("create-world")&&wizardStep==route.length){
                require(doc.querySelector(".native-input").getValue().equals("31415926"),"Returning to World lost the edited seed");
            }
            if(wizardStep==route.length){results.add("PASS WIZARD_MOUSE "+kind()+" real window mouse events, native selection, HTML state and preserved name/seed");return true;}
            var box=tabs.get(route[wizardStep]).getBoundingClientRect();
            var point=doc.documentToScreenPosition(new com.teamlifebind.aui.layout.Position((box.left+box.right)/2,(box.top+box.bottom)/2));
            var mc=Minecraft.getInstance();var window=mc.getWindow();
            mc.mouseHandler.onMove(window.handle(),point.x*window.getScreenWidth()/window.getGuiScaledWidth(),point.y*window.getScreenHeight()/window.getGuiScaledHeight(),0,0);
            mc.mouseHandler.onButton(window.handle(),new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0),InputConstants.PRESS);
            mc.mouseHandler.onButton(window.handle(),new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0),InputConstants.RELEASE);
            wizardStep++;opened=ticks;return false;
        }
    }
    private static EditBox writeWizardInput(Document doc,String value){
        var edit=ArchiveModel.read(current).controls.stream().map(ArchiveModel.Control::source).filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
        var input=doc.querySelector(".native-input");require(input!=null,"Wizard field missing");
        input.setValue(value);input.dispatchEvent(new com.teamlifebind.aui.event.Event(input,"input",true));
        require(edit.getValue().equals(value),"HTML wizard input did not update native world settings");return edit;
    }
    private static Screen options(String name)throws Exception{return (Screen)Class.forName("net.minecraft.client.gui.screens.options."+name).getConstructor(Screen.class,net.minecraft.client.Options.class).newInstance(new TitleScreen(),Minecraft.getInstance().options);}
    private static void check() {
        if(ArchivePresenter.document()!=null){
            try(var ignored=Document.withContext(ArchivePresenter.document())){
                var doc=ArchivePresenter.document();var box=doc.querySelector(".surface").getBoundingClientRect();var view=doc.getViewportSize();
                require(box.left>=0&&box.top>=0&&box.right<=view.width()+1&&box.bottom<=view.height()+1,"HTML surface exceeds viewport");
                require(doc.getElementById("page-title").getBoundingClientRect().height>12,"HTML hierarchy is missing");
                require(doc.getElementById("filter")==null,"Removed global search is still present");
            }
        }
        if(current instanceof StorageFixture fixture)fixture.check();
        if(current instanceof WidgetFixture fixture)fixture.check();
        if(kind().equals("widgets-reload"))results.add("PASS document reload rebinds headers, DOM actions, native widgets and validated input");
        if(List.of("video","online","sound","mouse","keybinds","accessibility","chat-options","skin").contains(kind())&&current.getClass().getName().startsWith("net.minecraft.")){
            var model=ArchiveModel.read(current);require(!model.controls.isEmpty(),"Native settings are empty: "+kind());
            require(model.controls.stream().noneMatch(c->c.label().equals(ArchiveTheme.tr("control.entry"))),"Generic entry placeholder leaked into "+kind());
            require(model.controls.stream().anyMatch(c->c.source() instanceof AbstractWidget),"Native named settings widgets were lost: "+kind());
            results.add("PASS native "+kind()+" labels, live values and child widgets; no generic placeholder entries");
        }
        if(current.getClass().getName().startsWith("net.caffeinemc.")||current.getClass().getName().startsWith("net.irisshaders.")){
            require(!ArchivePresenter.handles(current),"TLB intercepted external renderer options");
            require(!ArchiveChrome.active(),"COMPAT REPRO: TLB masthead overlays the Sodium/Iris settings and its controls");
            require(!current.children().isEmpty(),"External renderer settings lost their controls");
            results.add("PASS RENDERER_OPTIONS "+kind()+" original controls, no TLB overlay or input interception");
        }
        if(kind().startsWith("create-")){
            var doc=ArchivePresenter.document();try(var ignored=Document.withContext(doc)){
                require(doc.getElementById("tabs").querySelectorAll(".tab-action").size()==3,"World-creation tabs were flattened into content rows");
                require(doc.getElementById("action-dock").querySelectorAll(".control").size()==2,"Create/cancel actions are not fixed outside scroll");
                var tabs=doc.getElementById("tabs").querySelectorAll(".tab-action");var a=tabs.get(0).getBoundingClientRect();var b=tabs.get(1).getBoundingClientRect();
                require(b.left>=a.right-1&&Math.abs(a.top-b.top)<2,"World-creation tabs overlap or stack");
                require(doc.getElementById("filter")==null,"World wizard has an irrelevant search field");
            }results.add("PASS "+kind()+" native wizard controls, horizontal tabs and fixed create/cancel actions");
        }
        if(kind().equals("options")){
            var doc=ArchivePresenter.document();try(var ignored=Document.withContext(doc)){
                var rows=doc.getElementById("controls").querySelectorAll(".control");
                require(rows.size()>6,"Options were lost during grouping");
                for(int i=1;i<rows.size();i++){var a=rows.get(i-1).getBoundingClientRect();var b=rows.get(i).getBoundingClientRect();require(Math.abs(a.left-b.left)<2&&b.top>=a.bottom-1,"Settings are mixed into columns");}
                require(doc.getElementById("action-dock").querySelectorAll(".control").size()>0,"Done action scrolls away");
                require(!com.teamlifebind.aui.style.Interaction.isUserSelectable(rows.getFirst()),"Menu text remains selectable");
                require(com.teamlifebind.aui.style.Interaction.isUserSelectable(doc.getElementById("filter")),"Search editing was disabled");
            }
        }
        if(kind().equals("worlds")||kind().equals("servers")){
            var doc=ArchivePresenter.document();try(var ignored=Document.withContext(doc)){
                var row=doc.querySelector(".entry-row");require(row!=null,"Native asynchronous journey list never loaded");
                row.dispatchEvent(new com.teamlifebind.aui.event.Event(row,"click",true));
                var selection=current.children().stream().filter(AbstractSelectionList.class::isInstance).map(AbstractSelectionList.class::cast).findFirst().orElseThrow();
                require(selection.getSelected()!=null,"HTML row did not select the native world/server");
                require(doc.getElementById("action-dock").querySelectorAll(".control").size()>=3,"Journey actions missing from fixed action bar");
                require(ArchiveModel.read(current).controls.stream().anyMatch(c->c.kind().equals("action")&&c.active()&&c.source() instanceof AbstractWidget w&&ArchiveNavigation.keys(w.getMessage()).stream().anyMatch(k->k.equals("selectWorld.select")||k.equals("selectServer.select"))),"Select world/server did not enable the native Play/Join action");
                results.add("PASS "+kind()+" asynchronous native entries, native row selection and enabled Play/Join callback");
            }
        }
        if(kind().equals("title")){
            require(ArchiveModel.read(current).controls.stream().noneMatch(c->ArchivePolicy.hidden(c.source())),"Friends entry retained on title");
            var doc = ArchivePresenter.document();
            try (var ignored = Document.withContext(doc)) {
                var pane = doc.getElementById("control-scroll").getBoundingClientRect();
                for (String id : List.of("singleplayer", "multiplayer", "realms")) {
                    var action = doc.querySelector("[data-journey=\"" + id + "\"]");
                    require(action != null, "Missing title action " + id);
                    var box = action.getBoundingClientRect();
                    require(box.top >= pane.top - 1 && box.bottom <= pane.bottom + 1, "Title action requires scrolling: " + id);
                }
                var rail = doc.querySelector(".rail").getBoundingClientRect();
                require(rail.top >= pane.bottom - 1, "Title utilities overlap the journey pane");
            }
            var before=mcScreen();Minecraft.getInstance().gui.setScreen(new net.minecraft.client.gui.screens.friends.FriendsOverlayScreen(current));
            require(mcScreen()==before,"Friends screen remains reachable through navigation");
        }
        if(kind().equals("title")||kind().equals("pause")) {
            var buttons=current.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(b->b.visible).toList();
            require(!buttons.isEmpty(),"Main actions missing");int last=-1;
            for(var b:buttons)require(b.getX()>=0&&b.getRight()<=current.width&&b.getY()>=0&&b.getBottom()<=current.height,"Action exceeds viewport");
            for(int i=0;i<buttons.size();i++)for(int j=i+1;j<buttons.size();j++){
                var a=buttons.get(i);var b=buttons.get(j);require(a.getRight()<=b.getX()||b.getRight()<=a.getX()||a.getBottom()<=b.getY()||b.getBottom()<=a.getY(),"Main actions overlap");
            }
        }
        if(current instanceof MagicFixture fixture) {
            try(var ignored=Document.withContext(fixture.page.document())) {
                var doc=fixture.page.document();
                require(doc.querySelectorAll(".spell-memory").size()==3,"Spell memory slots missing");
                var previous=doc.getElementById("magic-slot-0").getBoundingClientRect();
                for(int i=1;i<3;i++){var next=doc.getElementById("magic-slot-"+i).getBoundingClientRect();require(next.left>=previous.right-1&&next.width>80,"Spell memories overlap");previous=next;}
                require(previous.right<=doc.getViewportSize().width(),"Spell cards exceed viewport");
                require(doc.getElementById("magic-value").getTextContent().equals("73 / 100"),"Live mana text incorrect");
            }
        }
    }
    private static void reloadCallbacks() {
        var mc=Minecraft.getInstance();
        for(boolean failed:new boolean[]{false,true}) {
            var expected=new IllegalStateException("intentional resource test failure");
            var future=failed?CompletableFuture.failedFuture(expected):CompletableFuture.completedFuture(null);
            ReloadInstance reload=new ReloadInstance(){public CompletableFuture<?> done(){return future;}public float getActualProgress(){return failed?.25F:1;}};
            var result=new AtomicReference<Optional<Throwable>>();
            var overlay=new LoadingOverlay(mc,reload,result::set,false);overlay.tick();
            require(result.get()!=null&&result.get().isPresent()==failed,"Resource completion/failure callback was lost");
        }
        results.add("PASS resource reload completion and exception propagation; lifecycle stays owned by LoadingOverlay.tick");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void finish(Throwable failure){
        if(failure==null)try{
            require(AuiPageReadiness.blockedFrames()>0&&AuiPageReadiness.paintedFrames()>0&&ArchiveLoading.deferredTextFrames()>0,"Cold-start loading/native-font and HTML-font barriers were never exercised");
            results.add("PASS COLD_FONT_BARRIER deferredFrames="+AuiPageReadiness.blockedFrames()+" readyPaints="+AuiPageReadiness.paintedFrames()+" loadingTextFramesDeferred="+ArchiveLoading.deferredTextFrames()+"; native startup and PIP documents wait for font publication");
        }catch(Throwable problem){failure=problem;}
        done=true;if(failure==null)results.add("PASS all "+(MOTION_ONLY?1:KINDS.length*SIZES.length)+" archive UI scenarios; no worlds opened");else{failure.printStackTrace();results.add("FAIL "+failure);}
        try{Files.createDirectories(OUTPUT);Files.writeString(OUTPUT.resolve("result.txt"),String.join("\n",results)+"\n");}catch(Exception e){e.printStackTrace();}
        Minecraft.getInstance().stop();
    }
    private static Screen mcScreen(){return Minecraft.getInstance().gui.screen();}
    private static final class StorageFixture extends Screen implements net.minecraft.client.gui.screens.inventory.MenuAccess<AbstractContainerMenu> {
        private final int size;
        private AbstractContainerMenu menu;
        private int[][] before;
        StorageFixture(int size){super(Component.literal("物资档案 / SUPPLIES"));this.size=size;}
        @Override protected void init(){
            var inv=new Inventory(null,new EntityEquipment());
            menu=switch(size){case 5->new HopperMenu(51,inv);case 9->new DispenserMenu(52,inv);case 54->ChestMenu.sixRows(53,inv);default->ChestMenu.threeRows(54,inv);};
            for(int i=0;i<size;i+=3){
                var item=i%2==0?Items.DIAMOND:Items.ENCHANTED_GOLDEN_APPLE;
                String id=i%2==0?"minecraft:diamond":"minecraft:enchanted_golden_apple";
                var components=net.minecraft.core.component.DataComponentMap.builder().addAll(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS)
                    .set(net.minecraft.core.component.DataComponents.ITEM_MODEL,net.minecraft.resources.Identifier.parse(id))
                    .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64)
                    .set(net.minecraft.core.component.DataComponents.ITEM_NAME,Component.literal(i%2==0?"钻石":"附魔金苹果")).build();
                menu.getSlot(i).set(new ItemStack(net.minecraft.core.Holder.direct(item,components),Math.min(64,i+1)));
            }
            before=new int[menu.slots.size()][2];for(int i=0;i<before.length;i++){before[i][0]=menu.getSlot(i).x;before[i][1]=menu.getSlot(i).y;}
        }
        @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float dt){}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){}
        @Override public AbstractContainerMenu getMenu(){return menu;}
        void check(){
            require(menu.slots.size()==size+36,"Storage slot mapping changed");
            for(int i=0;i<menu.slots.size();i++)for(int j=i+1;j<menu.slots.size();j++){
                var a=menu.getSlot(i);var b=menu.getSlot(j);require(Math.abs(a.x-b.x)>=16||Math.abs(a.y-b.y)>=16,"Reflow overlaps slot hit boxes");
            }
            try(var ignored=Document.withContext(ArchivePresenter.document())){
                var doc=ArchivePresenter.document();require(doc.querySelectorAll(".slot").size()==size+36,"HTML lost native slot identities");
                var first=doc.getElementById("inventory-slot-0").getBoundingClientRect();require(first.width>20&&first.height>20,"HTML slot is too small");
                var work=doc.querySelector(".work-area").getBoundingClientRect();require(work.height>=first.height-1,"Workspace was collapsed by the inventory pane");
                var pane=doc.getElementById("control-scroll").getBoundingClientRect();require(first.top<pane.bottom&&first.bottom>pane.top,"First supply slot is clipped outside the interaction pane");
                checkHotbar(doc,menu);
            }
        }
        @Override public void removed(){for(int i=0;i<before.length;i++)require(menu.getSlot(i).x==before[i][0]&&menu.getSlot(i).y==before[i][1],"Native slot coordinates were mutated");}
    }
    static void checkHotbar(Document doc,AbstractContainerMenu menu){
        var main=doc.getElementById("pack-main");var hotbar=doc.getElementById("pack-hotbar");
        require(main.querySelectorAll(".slot").size()==27,"Backpack must contain 27 main slots");
        var slots=hotbar.querySelectorAll(".slot");require(slots.size()==9,"Hotbar must contain all nine native slots");
        var bounds=hotbar.getBoundingClientRect();double top=slots.getFirst().getBoundingClientRect().top,right=bounds.left;
        for(int i=0;i<9;i++){
            var element=slots.get(i);var slot=menu.slots.stream().filter(s->("inventory-slot-"+s.index).equals(element.getAttribute("id"))).findFirst().orElseThrow();
            var box=element.getBoundingClientRect();
            require(slot.container instanceof Inventory&&slot.getContainerSlot()==i,"Hotbar native item identity/order changed: "+i);
            require(element.querySelector(".slot-number").getTextContent().equals(String.valueOf(i+1)),"Hotbar key label is incorrect");
            require(Math.abs(box.top-top)<1&&box.left>=right-1&&box.right<=bounds.right+1,"Hotbar 1-9 wrapped, overlapped or exceeded its pane");right=box.right;
        }
    }
    private static final class MagicFixture extends Screen implements AuiLinkedScreen {
        private AuiMagicPage page;private int selected;
        MagicFixture(){super(Component.literal("奥术编织"));}
        @Override protected void init(){page=new AuiMagicPage();}
        @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float dt){}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
            var model=new NativeHud.Magic("", "", "", "",73,100,0,List.of(
                new NativeHud.Slot("火焰风暴","准备就绪",ArchiveTheme.GOLD,4,45,0,100,true,true),
                new NativeHud.Slot("星辉裁决","冷却 3.2s",ArchiveTheme.VIOLET,7,80,64,160,true,false),
                new NativeHud.Slot("空白记忆","等待学习",ArchiveTheme.MUTED,0,0,0,0,false,false)),0,0);
            selected=page.render(g,x,y,model,selected,"G 松开确认 · 1 / 2 / 3 选择 · V 施法");
        }
        @Override public Document getLinkedDocument(){return page==null?null:page.document();}
        @Override public void removed(){if(page!=null){page.close();page=null;}}
    }
    private static final class LoadingFixture extends Screen implements AuiLinkedScreen {
        float reveal;
        LoadingFixture(){super(Component.literal("世界加载"));}
        @Override public Document getLinkedDocument(){return null;}
        @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float dt){}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){ArchiveLoading.draw(g,.63F,reveal,"正在编织世界","同步地形与世界数据",true);}
    }
    private static final class WidgetFixture extends Screen {
        Button button;EditBox edit;CycleButton<Boolean> cycle;AbstractSliderButton slider;Checkbox checkbox;int clicks;
        WidgetFixture(){super(Component.literal("交互控件 / CONTROLS"));}
        @Override protected void init(){
            int x=width/2-100,y=Math.max(48,height/2-68);
            button=addRenderableWidget(Button.builder(Component.literal("确认选择 / CONTINUE"),b->clicks++).bounds(x,y,200,22).build());
            cycle=addRenderableWidget(CycleButton.onOffBuilder(false).create(x,y+28,200,22,Component.literal("动态视觉")));
            edit=addRenderableWidget(new EditBox(font,x,y+56,200,22,Component.literal("输入名称")));edit.setValue("探索者 / Explorer");
            slider=addRenderableWidget(new AbstractSliderButton(x,y+84,200,20,Component.literal("动效强度"),.65){protected void updateMessage(){}protected void applyValue(){}});
            checkbox=addRenderableWidget(Checkbox.builder(Component.literal("显示操作提示"),font).pos(x,y+110).selected(true).build());
        }
        void check(){
            var down=new MouseButtonEvent(button.getX()+5,button.getY()+5,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));button.mouseClicked(down,false);button.mouseReleased(down);require(clicks==1,"Themed button did not preserve click");
            cycle.onPress(new KeyEvent(InputConstants.KEY_RETURN,0,0));require(cycle.getValue(),"Cycle setting did not change");
            checkbox.onPress(new KeyEvent(InputConstants.KEY_RETURN,0,0));require(!checkbox.selected(),"Checkbox did not change");
            edit.setFocused(true);edit.setCursorPosition(edit.getValue().length());edit.charTyped(new CharacterEvent('X'));require(edit.getValue().endsWith("X"),"Text input was lost");
            slider.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT,0,0));
            try(var ignored=Document.withContext(ArchivePresenter.document())){
                var doc=ArchivePresenter.document();int oldClicks=clicks;var action=doc.getElementById("native-control-1");action.dispatchEvent(new com.teamlifebind.aui.event.Event(action,"click",true));
                require(clicks==oldClicks+1,"HTML button did not reach native callback");
                var input=doc.querySelector(".native-input");input.setValue("新旅程 / Web UI");input.dispatchEvent(new com.teamlifebind.aui.event.Event(input,"input",true));require(edit.getValue().equals("新旅程 / Web UI"),"HTML text was not validated through native input: native="+edit.getValue()+" dom="+input.getValue());
                var box=action.getBoundingClientRect();var point=doc.documentToScreenPosition(new com.teamlifebind.aui.layout.Position((box.left+box.right)/2,(box.top+box.bottom)/2));
                var mc=Minecraft.getInstance();var window=mc.getWindow();int before=clicks;
                mc.mouseHandler.onMove(window.handle(),point.x*window.getScreenWidth()/window.getGuiScaledWidth(),point.y*window.getScreenHeight()/window.getGuiScaledHeight(),0,0);
                mc.mouseHandler.onButton(window.handle(),new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0),InputConstants.PRESS);
                mc.mouseHandler.onButton(window.handle(),new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0),InputConstants.RELEASE);
                require(clicks==before+1,"Native MouseHandler did not reach the HTML action exactly once");
                doc.clearFocus();mc.keyboardHandler.keyPress(window.handle(),InputConstants.PRESS,new KeyEvent(InputConstants.KEY_RETURN,0,0));mc.keyboardHandler.keyPress(window.handle(),InputConstants.RELEASE,new KeyEvent(InputConstants.KEY_RETURN,0,0));
                require(clicks==before+2,"Native KeyboardHandler did not reach the HTML action exactly once");
                input=doc.querySelector(".native-input");input.focus();String beforeInput=edit.getValue();mc.keyboardHandler.charTyped(window.handle(),new CharacterEvent('Z'));
                require(edit.getValue().length()==beforeInput.length()+1&&edit.getValue().replace("Z","").equals(beforeInput),"Native character dispatch did not reach the HTML/native input bridge: "+edit.getValue());
                var track=doc.querySelector(".range-track").getBoundingClientRect();
                var sliderPoint=doc.documentToScreenPosition(new com.teamlifebind.aui.layout.Position(track.left+track.width*.25,(track.top+track.bottom)/2));
                mc.mouseHandler.onMove(window.handle(),sliderPoint.x*window.getScreenWidth()/window.getGuiScaledWidth(),sliderPoint.y*window.getScreenHeight()/window.getGuiScaledHeight(),0,0);
                mc.mouseHandler.onButton(window.handle(),new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0),InputConstants.PRESS);
                mc.mouseHandler.onButton(window.handle(),new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0),InputConstants.RELEASE);
                var access=(com.teamlifebind.clientui.mixin.ArchiveSliderAccess)slider;
                require(Math.abs(access.tlb$value()-.25)<.035,"HTML slider uses the row width instead of its visible track: "+access.tlb$value());
                double sliderBefore=access.tlb$value();
                mc.keyboardHandler.keyPress(window.handle(),InputConstants.PRESS,new KeyEvent(InputConstants.KEY_RIGHT,0,0));mc.keyboardHandler.keyPress(window.handle(),InputConstants.RELEASE,new KeyEvent(InputConstants.KEY_RIGHT,0,0));
                require(access.tlb$value()>sliderBefore,"Focused HTML slider ignored the native right-arrow action");
                results.add("PASS native slider pointer at 25 percent and right-arrow adjustment through HTML/native bridge");
                results.add("PASS native mouse, key and character dispatch to HTML; exactly-once callbacks");
            }
        }
    }
    /** The native PauseScreen requires a connected player even during init. No world is opened for this fixture. */
    private static final class PauseFixture extends Screen {
        PauseFixture(){super(Component.literal("旅途休憩"));}
        @Override protected void init(){
            for(String key:List.of("menu.returnToGame","gui.advancements","gui.stats","menu.sendFeedback","menu.reportBugs","menu.playerReporting","menu.options","menu.worldOptions","menu.returnToMenu"))addRenderableWidget(Button.builder(Component.translatable(key),b->{}).bounds(0,0,160,20).build());
            ArchiveTheme.layoutActions(this);
        }
    }
}
