package com.teamlifebind.clientui;

import com.mojang.blaze3d.platform.InputConstants;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.layout.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import java.nio.file.*;
import java.util.*;

/** Only enabled by the loopback test runner. Uses its own server/world and real container packets. */
final class ArchiveE2E {
    private static final Path OUT=Path.of(System.getProperty("teamlifebind.archive.e2e.output","tlb-archive-e2e"));
    private static final List<String> evidence=new ArrayList<>();
    private static int ticks,step,entered;
    private static boolean connecting,ready,done;
    private static int navigation,navigationEntered;
    private static String navigationTrace="";
    private static boolean backupHandled;
    private static BlockPos origin;
    private static volatile boolean captureDone;
    private static volatile Throwable captureFailure;
    private static String capture;
    private static int recipePhase;
    private static int hotbarPhase,hotbarTarget;
    private static Object backdropLevel;
    private static long worldBackdropSubmissions;
    private static int meterWaits;
    private static RenderProbe renderProbe;
    private static ChatProbe chatProbe;
    private static ArchiveInteractionProbe interactionProbe;
    static void tick(){
        if(done)return;
        try{
            var mc=Minecraft.getInstance();ticks++;
            if(mc.level!=backdropLevel){backdropLevel=mc.level;worldBackdropSubmissions=ArchiveBackdrop.submissions();}
            else if(mc.level!=null)require(ArchiveBackdrop.submissions()==worldBackdropSubmissions,"Front-end background was drawn inside a game world");
            if(ticks>12000)throw new IllegalStateException("Loopback UI test timeout");
            if(!connecting){
                traceNavigation(mc);
                if(!navigate(mc))return;
                connecting=true;return;
            }
            if(mc.player==null||mc.level==null)return;
            if(!ready){
                if(mc.gui.screen()!=null)return; // World-entry objects exist before terrain loading has finished.
                if(!Files.exists(OUT.resolve("ready.txt")))Files.writeString(OUT.resolve("ready.txt"),mc.player.getName().getString());
                if(!Files.exists(OUT.resolve("prepared.txt")))return;
                var expected=new BlockPos(0,100,0);
                if(!mc.player.blockPosition().equals(expected)||!mc.player.getMainHandItem().isEmpty()
                    ||!mc.level.getBlockState(expected.offset(1,0,0)).is(net.minecraft.world.level.block.Blocks.CHEST))return;
                origin=expected;ready=true;entered=ticks;return;
            }
            if(capture!=null){if(!captureDone)return;if(captureFailure!=null)throw new IllegalStateException("E2E screenshot failed",captureFailure);capture=null;next();return;}
            if(ticks-entered<25)return;
            if(Boolean.getBoolean("teamlifebind.archive.chat.only")){
                if(chatProbe==null)chatProbe=new ChatProbe();
                if(!chatProbe.tick())return;
                if(!System.getProperty("teamlifebind.render.compat.test","").isEmpty()){
                    if(renderProbe==null)renderProbe=new RenderProbe();
                    if(!renderProbe.tick())return;
                }
                finish(null);return;
            }
            if(Boolean.getBoolean("teamlifebind.archive.interaction.only")){
                if(chatProbe==null)chatProbe=new ChatProbe();
                if(!chatProbe.tick())return;
                if(interactionProbe==null)interactionProbe=new ArchiveInteractionProbe(OUT,evidence);
                if(interactionProbe.tick())finish(null);return;
            }
            if(Boolean.getBoolean("teamlifebind.render.compat.only")){
                if(renderProbe==null)renderProbe=new RenderProbe();
                if(renderProbe.tick())finish(null);
                return;
            }
            switch(step){
                case 0->{mc.player.closeContainer();use(1,0);next();}
                case 1->{require(mc.player.containerMenu instanceof ChestMenu,"Real chest did not open: player="+mc.player.blockPosition()+" origin="+origin+" target="+mc.level.getBlockState(origin.offset(1,0,0))+" screen="+(mc.gui.screen()==null?"none":mc.gui.screen().getClass().getSimpleName()));require(ArchivePresenter.document()!=null,"Chest is not an HTML layout");snapshot("world-chest");}
                case 2->{click(0,InputConstants.MOUSE_BUTTON_LEFT,0);next();}
                case 3->{require(mc.player.containerMenu.getCarried().is(Items.DIAMOND)&&mc.player.containerMenu.getCarried().getCount()==16,"Pickup did not survive server acknowledgement: carried="+mc.player.containerMenu.getCarried()+" slot0="+mc.player.containerMenu.getSlot(0).getItem());click(27,InputConstants.MOUSE_BUTTON_LEFT,0);next();}
                case 4->{require(mc.player.containerMenu.getCarried().isEmpty()&&mc.player.containerMenu.getSlot(27).getItem().getCount()==16,"Placement did not reach the server");click(27,InputConstants.MOUSE_BUTTON_LEFT,InputConstants.MOD_SHIFT);next();}
                case 5->{require(mc.player.containerMenu.getSlot(0).getItem().is(Items.DIAMOND)&&mc.player.containerMenu.getSlot(0).getItem().getCount()==16,"Shift transfer did not reach the server");click(0,InputConstants.MOUSE_BUTTON_RIGHT,0);next();}
                case 6->{require(mc.player.containerMenu.getCarried().getCount()==8,"Right click did not split the real stack");click(27,InputConstants.MOUSE_BUTTON_RIGHT,0);next();}
                case 7->{
                    var menu=mc.player.containerMenu;
                    if(hotbarPhase==0){
                        require(menu.getCarried().getCount()==7&&menu.getSlot(27).getItem().getCount()==1,"Right-click placement was not authoritative");
                        hotbarTarget=menu.slots.stream().filter(s->s.container instanceof net.minecraft.world.entity.player.Inventory&&s.getContainerSlot()<9&&s.getItem().isEmpty()).findFirst().orElseThrow().index;
                        click(hotbarTarget,InputConstants.MOUSE_BUTTON_LEFT,0);hotbarPhase=1;entered=ticks;return;
                    }
                    if(hotbarPhase==1){
                        require(menu.getCarried().isEmpty()&&menu.getSlot(hotbarTarget).getItem().getCount()==7,"Independent hotbar placement did not reach the server");
                        click(hotbarTarget,InputConstants.MOUSE_BUTTON_LEFT,0);hotbarPhase=2;entered=ticks;return;
                    }
                    if(hotbarPhase==2){
                        require(menu.getCarried().getCount()==7&&menu.getSlot(hotbarTarget).getItem().isEmpty(),"Independent hotbar pickup lost native slot identity");
                        click(hotbarTarget,InputConstants.MOUSE_BUTTON_LEFT,0);hotbarPhase=3;entered=ticks;return;
                    }
                    require(menu.getCarried().isEmpty()&&menu.getSlot(hotbarTarget).getItem().getCount()==7,"Hotbar return placement did not survive server acknowledgement");
                    evidence.add("PASS real chest pickup, placement, shift-transfer, split and single-item placement after server acknowledgement");
                    evidence.add("PASS independent single-row hotbar pickup and placement through original native slot IDs after server acknowledgement");mc.player.closeContainer();next();
                }
                case 8->{snapshot("world-hud");}
                case 9->{use(2,0);next();}
                case 10->{
                    require(mc.gui.screen() instanceof MenuAccess<?>,"Crafting table did not open");
                    if(recipePhase==0){
                        var doc=ArchivePresenter.document();try(var ignored=Document.withContext(doc)){
                            var button=doc.querySelectorAll(".control").stream().filter(e->e.querySelector(".control-label")!=null&&e.querySelector(".control-label").getTextContent().equals(ArchiveTheme.tr("work.recipes"))).findFirst().orElseThrow();nativeClick(doc,button);
                        }recipePhase=1;entered=ticks;return;
                    }
                    if(recipePhase==1){
                        var model=ArchiveModel.read(mc.gui.screen());require(model.controls.stream().anyMatch(c->c.source() instanceof net.minecraft.client.gui.screens.recipebook.RecipeButton),"Opening the named recipe book lost its native recipe choices");
                        require(model.controls.stream().filter(c->c.kind().equals("recipe-toggle")).count()==1,"Recipe categories duplicated the book toggle");
                        var doc=ArchivePresenter.document();nativeClick(doc,doc.querySelector(".recipe-filter"));recipePhase=2;entered=ticks;return;
                    }
                    if(recipePhase==2){
                        var doc=ArchivePresenter.document();var oak=new net.minecraft.world.item.ItemStack(Items.OAK_PLANKS).getHoverName().getString();var button=doc.querySelectorAll(".recipe-entry").stream().filter(e->e.getTextContent().contains(oak)).findFirst().orElseThrow(()->new AssertionError("Craftable recipe grid does not contain oak planks"));
                        var scroller=doc.querySelector(".work-area");var box=button.getBoundingClientRect();var frame=scroller.getBoundingClientRect();
                        evidence.add("CRAFTING_TRACE entry="+box.left+","+box.top+","+box.width+","+box.height+" viewport="+frame.top+".."+frame.bottom+" logs="+mc.player.getInventory().countItem(Items.OAK_LOG));
                        scroller.scrollTo(0,scroller.getScrollTop()+box.top-frame.top-12);var cards=doc.getElementById("recipe-cards");cards.scrollTo(0,cards.getScrollTop()+box.top-cards.getBoundingClientRect().top-4);recipePhase=6;entered=ticks;return;
                    }
                    if(recipePhase==6){var doc=ArchivePresenter.document();var oak=new net.minecraft.world.item.ItemStack(Items.OAK_PLANKS).getHoverName().getString();var button=doc.querySelectorAll(".recipe-entry").stream().filter(e->e.getTextContent().contains(oak)).findFirst().orElseThrow();AuiInkSelfTest.readbackFrame(mc.gameRenderer.mainRenderTarget(),OUT.resolve("crafting-recipes.png"));nativeClick(doc,button);recipePhase=3;entered=ticks;return;}
                    if(recipePhase==3){require(mc.player.containerMenu.getSlot(0).getItem().is(Items.OAK_PLANKS),"Recipe request did not fill the server's real crafting result");click(0,InputConstants.MOUSE_BUTTON_LEFT,0);recipePhase=4;entered=ticks;return;}
                    if(recipePhase==4){
                        require(mc.player.containerMenu.getCarried().is(Items.OAK_PLANKS)&&mc.player.containerMenu.getCarried().getCount()==4,"Actual recipe output was not server acknowledged");
                        var doc=ArchivePresenter.document();nativeClick(doc,doc.querySelector(".recipe-toggle"));
                        evidence.add("PASS CRAFTING_RECIPE one toggle; native categories and craftable filter; real recipe request, autofill, ingredient consumption and four-plank output");recipePhase=5;entered=ticks;return;
                    }
                    snapshot("world-crafting");
                }
                case 11->{mc.player.closeContainer();use(3,0);next();}
                case 12->{require(mc.gui.screen() instanceof MenuAccess<?>,"Anvil did not open");snapshot("world-anvil");}
                case 13->{mc.player.closeContainer();use(1,1);next();}
                case 14->{require(mc.gui.screen() instanceof MenuAccess<?>,"Enchanting table did not open");snapshot("world-enchanting");}
                case 15->{mc.player.closeContainer();use(2,1);next();}
                case 16->{snapshot("world-loom");}
                case 17->{mc.player.closeContainer();use(3,1);next();}
                case 18->{snapshot("world-stonecutter");}
                case 19->{mc.player.closeContainer();use(1,2);next();}
                case 20->{snapshot("world-furnace");}
                case 21->{
                    if(!Files.exists(OUT.resolve("brewing-prepared.txt"))){Files.writeString(OUT.resolve("brewing-request.txt"),"Prepare live brewing fixture");return;}
                    mc.player.closeContainer();use(2,2);next();
                }
                case 22->{snapshot("world-brewing");}
                case 23->{mc.player.closeContainer();use(3,2);next();}
                case 24->{snapshot("world-smithing");}
                case 25->{mc.player.closeContainer();mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));next();}
                case 26->{require(mc.gui.screen() instanceof MenuAccess<?>,"Inventory did not open");snapshot("world-inventory");}
                case 27->{mc.player.closeContainer();mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("",false));next();}
                case 28->{
                    var document=ArchivePresenter.document();require(document!=null,"Chat did not use HTML");try(var ignored=Document.withContext(document)){var input=document.querySelector(".native-input");require(input!=null,"Native chat input was omitted");input.setValue("archive-ui-loopback-message");input.dispatchEvent(new com.teamlifebind.aui.event.Event(input,"input",true));}
                    require(((com.teamlifebind.clientui.mixin.ArchiveChatScreenAccess)mc.gui.screen()).tlb$input().getValue().equals("archive-ui-loopback-message"),"Chat HTML input lost native validation");snapshot("world-chat");
                }
                case 29->{ArchivePresenter.key(mc.gui.screen(),new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN,0,0));next();}
                case 30->{require(mc.gui.screen()==null,"Chat confirmation did not return to the world");require(ArchiveOverlays.messages(true).stream().anyMatch(message->message.content().getString().contains("archive-ui-loopback-message")),"Chat message was not echoed by the loopback server");evidence.add("PASS HTML chat input, native validation, native Enter and server echo");mc.gui.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(mc.getConnection().getAdvancements()));next();}
                case 31->{require(ArchiveModel.read(mc.gui.screen()).controls.stream().anyMatch(c->c.source() instanceof ArchiveSpecials.AdvancementEntry),"Advancement cards missing");snapshot("world-advancements");}
                case 32->{mc.gui.setScreen(null);mc.gui.hud.getTabList().setVisible(true);mc.options.keyPlayerList.setDown(true);next();}
                case 33->{snapshot("world-roster");}
                case 34->{mc.options.keyPlayerList.setDown(false);mc.gui.hud.getTabList().setVisible(false);next();}
                case 35->{mc.player.closeContainer();use(-1,0);next();}
                case 36->{snapshot("world-grindstone");}
                case 37->{mc.player.closeContainer();use(-2,0);next();}
                case 38->{snapshot("world-cartography");}
                case 39->{mc.player.closeContainer();use(-1,1);next();}
                case 40->{snapshot("world-beacon");}
                case 41->{mc.player.closeContainer();use(-2,1);next();}
                case 42->{snapshot("world-crafter");}
                case 43->{mc.player.closeContainer();use(-1,2);next();}
                case 44->{
                    require(mc.gui.screen() instanceof MenuAccess<?> access&&access.getMenu() instanceof net.minecraft.world.inventory.LecternMenu,"Lectern did not open");
                    var doc=ArchivePresenter.document();require(doc.getElementById("prose").getTextContent().contains("TLB reading fixture"),"Lectern page content is missing");
                    var article=doc.getElementById("prose").getBoundingClientRect();require(article.height>12,"Lectern page was hidden by workstation layout");snapshot("world-lectern");
                }
                case 45->{mc.player.closeContainer();mc.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));next();}
                case 46->{snapshot("world-pause");}
                case 47->{Document.refreshAll();next();}
                case 48->{
                    var doc=ArchivePresenter.document();require(doc!=null&&!doc.getElementById("page-title").getTextContent().isBlank(),"Resource refresh lost the pause heading");
                    snapshot("world-pause-reload");
                }
                case 49->{
                    ArchivePresenter.key(mc.gui.screen(),new net.minecraft.client.input.KeyEvent(InputConstants.KEY_ESCAPE,0,0));
                    require(mc.gui.screen()==null,"Escape waited for an exit animation before returning control to the game");
                    require(AuiScreenTransitions.active(),"Escape skipped the UI-only exit transition");
                    evidence.add("PASS native Escape closes immediately and retains a "+AuiInkTransition.CLOSE_MILLIS+"ms UI-only exit transition");next();
                }
                case 50->{snapshot("world-hud-reload");}
                default->{
                    if(chatProbe==null)chatProbe=new ChatProbe();
                    if(!chatProbe.tick())return;
                    if(interactionProbe==null)interactionProbe=new ArchiveInteractionProbe(OUT,evidence);
                    if(!interactionProbe.tick())return;
                    if(!System.getProperty("teamlifebind.render.compat.test","").isEmpty()){
                        if(renderProbe==null)renderProbe=new RenderProbe();
                        if(!renderProbe.tick())return;
                    }
                    finish(null);
                }
            }
        }catch(Throwable failure){finish(failure);}
    }
    /** Actual overlay, loaded fonts, native chat history, resizing and resource refresh. */
    private static final class ChatProbe {
        int phase,began;
        int width=1920,height=1080;
        final String full="长消息完整记录："+"bydbawyuigdawbydhawuhdawhewqie1wadiuysabydbaawyubgdawb中文😀".repeat(40);
        void advance(){phase++;began=ticks;}
        boolean tick()throws Exception{
            var mc=Minecraft.getInstance();
            if(phase==0){
                resize(mc,1920,1080);
                mc.gui.hud.getChat().clearMessages(true);
                for(String text:List.of("聊天布局测试 · 六条短消息", "物资点信息已同步", "队伍成员已准备", "魔力恢复完成", "前往下一处补给点", "最新消息：准备出发"))
                    mc.gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(text));
                advance();return false;
            }
            if(phase==6){mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("/help",false));advance();return false;}
            if(phase==7){
                var doc=ArchivePresenter.document();if(doc==null||!AuiPageReadiness.ready(doc))return false;
                try(var ignored=Document.withContext(doc)){
                    var input=(com.teamlifebind.aui.element.AbstractText)doc.querySelector(".native-input");
                    require(input.getCursor()==5,"Chat prefix opened with the cursor at the beginning");
                    input.insertText("x");require(input.getValue().equals("/helpx"),"First typed character was prepended");
                    input.setSelectionRange(2,2);input.insertText("Z");require(input.getValue().equals("/hZelpx")&&input.getCursor()==3,"Manual caret editing was forced to the end");
                    var nativeInput=((com.teamlifebind.clientui.mixin.ArchiveChatScreenAccess)mc.gui.screen()).tlb$input();
                    require(nativeInput.getValue().equals(input.getValue())&&nativeInput.getCursorPosition()==3,"Native command suggestions lost the HTML caret");
                }
                mc.gui.hud.getChat().addRecentChat("/help history-fixture");
                mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("你好😀",false));advance();return false;
            }
            if(phase==8){
                var doc=ArchivePresenter.document();if(doc==null||!AuiPageReadiness.ready(doc))return false;
                try(var ignored=Document.withContext(doc)){
                    var input=(com.teamlifebind.aui.element.AbstractText)doc.querySelector(".native-input");
                    require(input.getCursor()=="你好😀".length(),"Unicode chat prefix cursor is not at the end");input.insertText("!");
                    require(input.getValue().equals("你好😀!"),"Unicode typing did not append");
                    ArchivePresenter.key(mc.gui.screen(),new net.minecraft.client.input.KeyEvent(InputConstants.KEY_UP,org.lwjgl.sdl.SDLKeycode.SDLK_UP,0));
                    require(input.getValue().equals("/help history-fixture")&&input.getCursor()==input.getValue().length(),"Chat history did not restore its end caret: "+input.getValue()+" cursor="+input.getCursor());
                }
                ArchivePresenter.key(mc.gui.screen(),new net.minecraft.client.input.KeyEvent(InputConstants.KEY_ESCAPE,0,0));
                evidence.add("PASS CHAT_CARET prefix, Unicode append, manual middle editing and native command history");advance();return false;
            }
            if(ticks-began<20)return false;
            if(phase==1){check(false);shot("readable");resize(mc,1152,720);advance();return false;}
            if(phase==2){check(false);shot("compact");resize(mc,720,480);mc.gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(full));advance();return false;}
            if(phase==3){check(true);shot("long-small");Document.refreshAll();advance();return false;}
            if(phase==4){
                var doc=ArchiveOverlays.document();if(doc==null||!AuiPageReadiness.ready(doc))return false;
                check(true);shot("reload");resize(mc,1920,1080);
                mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("",false));advance();return false;
            }
            if(phase==5){
                var doc=ArchivePresenter.document();if(doc==null||!AuiPageReadiness.ready(doc))return false;
                require(ArchiveOverlays.messages(true).stream().anyMatch(message->message.content().getString().equals(full)),"HUD preview destroyed the full native message");
                require(doc.querySelectorAll(".conversation-line").stream().anyMatch(line->line.getTextContent().equals(full)),"Full native message is not available in the HTML chat history");
                shot("history");ArchivePresenter.key(mc.gui.screen(),new net.minecraft.client.input.KeyEvent(InputConstants.KEY_ESCAPE,0,0));
                require(mc.gui.screen()==null,"Chat cannot close immediately");
                evidence.add("PASS CHAT_PREVIEW real fonts; short/long messages; resize; resource refresh; whole cards; complete native history");
                advance();return false;
            }
            return true;
        }
        private void resize(Minecraft mc,int width,int height){
            this.width=width;this.height=height;
            require(org.lwjgl.sdl.SDLVideo.SDL_RestoreWindow(mc.getWindow().handle()),"Cannot restore isolated test window before resizing");
            mc.getWindow().setWindowed(width,height);mc.resizeGui();
        }
        private void shot(String name)throws Exception{AuiInkSelfTest.readbackFrame(Minecraft.getInstance().gameRenderer.mainRenderTarget(),OUT.resolve("chat-"+name+".png"));}
        private void check(boolean shortened){
            var window=Minecraft.getInstance().getWindow();
            require(window.getScreenWidth()==width&&window.getScreenHeight()==height,"Test window did not actually resize to "+width+"x"+height+": "+window.getScreenWidth()+"x"+window.getScreenHeight());
            var doc=ArchiveOverlays.document();require(doc!=null&&AuiPageReadiness.ready(doc),"HUD chat/fonts have not settled");
            try(var ignored=Document.withContext(doc)){
                var frame=doc.getElementById("hud-chat").getBoundingClientRect();var rows=doc.querySelectorAll(".chat-line");
                require(!rows.isEmpty(),"HUD chat vanished");double bottom=frame.top;
                var status=doc.getElementById("sidebar");
                if(!status.getAttribute("style").contains("display:none")){
                    var box=status.getBoundingClientRect();
                    require(frame.right<=box.left||frame.left>=box.right||frame.bottom<=box.top||frame.top>=box.bottom,"HUD chat and the field-status card overlap");
                }
                for(var row:rows){var box=row.getBoundingClientRect();require(box.top>=bottom-.1&&box.bottom<=frame.bottom+.1&&box.left>=frame.left&&box.right<=frame.right&&box.height>=35,"HUD message was cropped, shrunk or overlapped");bottom=box.bottom;}
                if(shortened)require(rows.getLast().getTextContent().endsWith("…")&&rows.getLast().getTextContent().startsWith("长消息完整记录："),"Oversized chat did not keep a readable preview");
                else require(rows.size()>1&&rows.getLast().getTextContent().equals("最新消息：准备出发"),"Recent short messages disappeared");
            }
        }
    }
    private static void traceNavigation(Minecraft mc){
        var screen=mc.gui.screen();var doc=ArchivePresenter.document();var focused=doc==null?null:doc.getFocusedElement();
        String state="stage="+navigation+" screen="+(screen==null?"none":screen.getClass().getSimpleName())+" htmlFocus="+(focused==null?"none":focused.tagName+"#"+focused.getAttribute("id"))+" nativeFocus="+(screen==null||screen.getFocused()==null?"none":screen.getFocused().getClass().getSimpleName());
        if(!state.equals(navigationTrace)){navigationTrace=state;System.out.println("[TLB navigation fixture] "+state);}
    }
    /** Enter a real local save, then enter the loopback server through the actual HTML selectors. */
    private static boolean navigate(Minecraft mc) throws Exception {
        if(ticks<70||mc.gui.overlay()!=null)return false;
        if(navigation==0){
            Files.createDirectories(OUT);mc.options.pauseOnLostFocus=false;
            mc.options.skipMultiplayerWarning=true;
            mc.getWindow().setWindowed(1920,1080);mc.options.guiScale().set(2);mc.resizeGui();
            var servers=new net.minecraft.client.multiplayer.ServerList(mc);servers.load();
            if(servers.get("127.0.0.1:25587")==null){servers.add(new ServerData("Archive UI isolated test","127.0.0.1:25587",ServerData.Type.OTHER),false);servers.save();}
            mc.gui.setScreen(new TitleScreen());navigation=1;navigationEntered=ticks;return false;
        }
        if(navigation==1&&ticks-navigationEntered>25&&nativeTitleAction("menu.singleplayer")){navigation=2;navigationEntered=ticks;return false;}
        if(navigation==2&&ticks-navigationEntered>25&&ArchivePresenter.document()!=null){
            var doc=ArchivePresenter.document();try(var ignored=Document.withContext(doc)){var row=doc.querySelector(".entry-row");if(row==null)return false;nativeClick(doc,row);}
            navigation=3;navigationEntered=ticks;return false;
        }
        if(navigation==3&&ticks-navigationEntered>2){
            nativeConfirm(mc);navigation=4;return false;
        }
        if(navigation==4&&mc.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen&&!backupHandled){
            if(nativeAction("selectWorld.backupJoinConfirmButton")){backupHandled=true;evidence.add("PASS isolated experimental-world confirmation through original Create Backup and Load action");}return false;
        }
        if(navigation==4&&mc.level!=null&&mc.player!=null){
            require(mc.getSingleplayerServer()!=null,"World selector did not open the isolated single-player save");
            evidence.add("PASS single-player: actual main-menu button, asynchronous world discovery, HTML row selection, native Enter and actual local-world entry");
            mc.disconnect(new TitleScreen(),false);navigation=5;navigationEntered=ticks;return false;
        }
        if(navigation==5&&ticks-navigationEntered>25&&nativeTitleAction("menu.multiplayer")){navigation=6;navigationEntered=ticks;return false;}
        if(navigation==6&&ticks-navigationEntered>25&&ArchivePresenter.document()!=null){
            var doc=ArchivePresenter.document();try(var ignored=Document.withContext(doc)){var row=doc.querySelector(".entry-row");if(row==null)return false;nativeClick(doc,row);}
            navigation=7;navigationEntered=ticks;return false;
        }
        if(navigation==7&&ticks-navigationEntered>2){
            nativeConfirm(mc);navigation=8;return false;
        }
        if(navigation==8&&mc.level!=null&&mc.player!=null&&mc.getSingleplayerServer()==null){evidence.add("PASS multiplayer: actual main-menu button, real saved-server list, HTML row selection, native Enter and successful loopback-server connection");return true;}
        return false;
    }
    private static boolean nativeTitleAction(String key){
        return Minecraft.getInstance().gui.screen() instanceof TitleScreen&&nativeAction(key);
    }
    private static boolean nativeAction(String key){
        var mc=Minecraft.getInstance();var doc=ArchivePresenter.document();if(mc.gui.screen()==null||doc==null||!AuiPageReadiness.ready(doc)||AuiScreenTransitions.active())return false;
        var control=ArchiveModel.read(mc.gui.screen()).controls.stream().filter(c->c.source() instanceof net.minecraft.client.gui.components.AbstractWidget w&&ArchiveNavigation.keys(w.getMessage()).contains(key)).findFirst().orElseThrow();
        require(control.active(),"Native UI action is disabled: "+key);
        try(var ignored=Document.withContext(doc)){for(var row:doc.querySelectorAll(".control")){var label=row.querySelector(".control-label");if(label!=null&&label.getTextContent().equals(control.label())){nativeClick(doc,row);return true;}}}
        return false;
    }
    private static void nativeConfirm(Minecraft mc){
        var key=new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN,0,0);
        mc.keyboardHandler.keyPress(mc.getWindow().handle(),InputConstants.PRESS,key);
        mc.keyboardHandler.keyPress(mc.getWindow().handle(),InputConstants.RELEASE,key);
    }
    private static void nativeClick(Document doc,com.teamlifebind.aui.init.Element element){
        var mc=Minecraft.getInstance();var box=element.getBoundingClientRect();var point=doc.documentToScreenPosition(new Position((box.left+box.right)/2,(box.top+box.bottom)/2));var window=mc.getWindow();
        mc.mouseHandler.onMove(window.handle(),point.x*window.getScreenWidth()/window.getGuiScaledWidth(),point.y*window.getScreenHeight()/window.getGuiScaledHeight(),0,0);
        mc.mouseHandler.onButton(window.handle(),new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0),InputConstants.PRESS);
        mc.mouseHandler.onButton(window.handle(),new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0),InputConstants.RELEASE);
    }
    private static void next(){step++;entered=ticks;}
    private static void use(int x,int z){var mc=Minecraft.getInstance();BlockPos pos=origin.offset(x,0,z);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false));}
    private static void click(int index,int button,int modifiers){
        var mc=Minecraft.getInstance();var document=ArchivePresenter.document();require(document!=null,"HTML container document missing");
        try(var ignored=Document.withContext(document)){
            var box=document.getElementById("inventory-slot-"+index).getBoundingClientRect();
            var point=document.documentToScreenPosition(new Position((box.left+box.right)/2,(box.top+box.bottom)/2));
            var event=new MouseButtonEvent(point.x,point.y,new MouseButtonInfo(button,modifiers));
            ArchivePresenter.mouse(mc.gui.screen(),event,true,false);ArchivePresenter.mouse(mc.gui.screen(),event,false,false);
        }
    }
    private static void snapshot(String name){
        if(!metersPresented(name)&&++meterWaits<=20)return;
        checkWorkspace(name);
        meterWaits=0;
        var mc=Minecraft.getInstance();capture=name;captureDone=false;captureFailure=null;
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{try(image){image.writeToFile(OUT.resolve(name+".png"));evidence.add("PASS "+name+".png");}catch(Throwable e){captureFailure=e;}captureDone=true;});
    }
    /** Native packets arrive before the next HTML render, including the 100% -> 0% recipe rollover. */
    private static boolean metersPresented(String name){
        if(!(Minecraft.getInstance().gui.screen() instanceof MenuAccess<?> access))return true;
        var meters=ArchiveWorkstation.meters(access.getMenu());if(meters.isEmpty())return true;
        var doc=ArchivePresenter.document();if(doc==null||!AuiPageReadiness.ready(doc))return false;
        try(var ignored=Document.withContext(doc)){
            for(var meter:meters){
                var row=doc.getElementById("meter-"+meter.key());if(row==null)return false;
                var track=row.querySelector(".process-track").getBoundingClientRect();var fill=row.querySelector(".process-fill").getBoundingClientRect();
                double shown=track.width>0?fill.width/track.width:0,expected=Math.clamp(meter.fraction(),0,1);
                if(track.width<=20||Math.abs(shown-expected)>=.09){
                    if(meterWaits==0)evidence.add("WAIT rendered progress: "+name+" "+meter.key()+" shown="+shown+" native="+expected);
                    return false;
                }
            }return true;
        }
    }
    private static void checkWorkspace(String name){
        var screen=Minecraft.getInstance().gui.screen();if(!(screen instanceof MenuAccess<?> access))return;
        var menu=access.getMenu();var profile=ArchiveWorkstation.profile(menu);var doc=ArchivePresenter.document();
        require(doc!=null,"Missing workstation HTML: "+name);
        try(var ignored=Document.withContext(doc)){
            require(profile.key().equals(doc.body.getAttribute("data-workstation")),"Wrong workspace profile: "+name);
            require(!doc.getElementById("work-instructions").getTextContent().isBlank(),"Workstation purpose is missing: "+name);
            if(menu.slots.stream().anyMatch(s->s.container instanceof net.minecraft.world.entity.player.Inventory)){
                var work=doc.querySelector(".work-area").getBoundingClientRect();var pack=doc.querySelector(".pack-area").getBoundingClientRect();var pane=doc.getElementById("control-scroll").getBoundingClientRect();
                require(pack.width>100&&work.right<=pack.left+1&&pack.right<=pane.right+1,"Workspace pushed the backpack offscreen: "+name);
                ArchiveSelfTest.checkHotbar(doc,menu);
            }
            for(var zone:profile.zones()){
                var host=doc.querySelector(".zone-"+zone.key());require(host!=null,"Missing role "+zone.key()+": "+name);
                require(!host.querySelector(".zone-title").getTextContent().startsWith("ui.teamlifebind."),"Untranslated workspace role: "+zone.key());
                for(int index:zone.slots())if(menu.getSlot(index).isActive())require(doc.getElementById("inventory-slot-"+index)!=null,"A role lost its native slot: "+name+" #"+index);
            }
            for(var meter:ArchiveWorkstation.meters(menu)){
                var row=doc.getElementById("meter-"+meter.key());require(row!=null,"Real progress meter missing: "+name);
                var track=row.querySelector(".process-track").getBoundingClientRect();var fill=row.querySelector(".process-fill").getBoundingClientRect();
                require(track.width>20&&Math.abs(fill.width/track.width-Math.clamp(meter.fraction(),0,1))<.09,"Progress does not reflect native menu data: "+name+" "+meter.key()+" shown="+(fill.width/track.width)+" native="+meter.fraction());
            }
            if(menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu furnace)require(furnace.isLit()&&furnace.getLitProgress()>0,"Furnace test did not exercise real burning fuel");
            if(menu instanceof net.minecraft.world.inventory.BrewingStandMenu brewing)require(brewing.getBrewingTicks()>0&&brewing.getFuel()>0,"Brewing test did not exercise a live recipe");
        }
        evidence.add("PASS workspace "+profile.key()+": purpose, named slot roles, native slot identity, single-row hotbar 1-9 and live progress");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    /** Real loaded mods, a real server-owned wall and the actual render hook; no replacement pipeline. */
    private static final class RenderProbe {
        private final boolean iris=System.getProperty("teamlifebind.render.compat.test").equals("iris");
        private Class<?> renderer;
        private java.lang.reflect.Constructor<?> packet;
        private java.lang.reflect.Method accept;
        private int phase,mode,began,start=ticks;
        private boolean oldHide,emitting,behind;
        private long frames;
        private int[] baseline;
        private String name(){return iris?"iris-"+(mode==1?"on":"off")+"-"+mode:"sodium";}
        private Object field(String name)throws Exception{var f=renderer.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
        private void clear()throws Exception{emitting=false;renderer.getMethod("clear").invoke(null);}
        private void emit()throws Exception{
            var mc=Minecraft.getInstance();double z=mc.player.getZ()+(behind?7:2);
            var value=packet.newInstance(256L,new UUID(8137,9121),mc.level.dimension().identifier(),com.teamlifebind.common.SpellCatalogue.all().get(13).id(),
                0,1,13,11F,200,mc.player.getX(),mc.player.getEyeY(),z,mc.player.getX(),mc.player.getEyeY(),z,.7F);
            accept.invoke(null,value);
        }
        private void mode()throws Exception{
            clear();
            if(iris){
                var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");var instance=api.getMethod("getInstance").invoke(null);
                var config=api.getMethod("getConfig").invoke(instance);
                Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply",boolean.class).invoke(config,mode==1);
            }
            began=ticks;phase=2;
        }
        boolean tick()throws Exception{
            require(ticks-start<1400,"Render compatibility fixture timed out at "+phase+" "+name());
            var mc=Minecraft.getInstance();
            if(emitting)emit();
            if(phase==0){
                Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod");
                for(String platform:List.of("fabric","neoforge"))try{renderer=Class.forName("com.teamlifebind."+platform+".MagicShaderRenderer");break;}catch(ClassNotFoundException ignored){}
                require(renderer!=null,"Native magic renderer missing");
                var type=Class.forName(renderer.getPackageName()+".MagicScenePayload");packet=type.getConstructors()[0];accept=renderer.getMethod("accept",type);
                oldHide=mc.gui.hud.isHidden();if(!oldHide)mc.gui.hud.toggle();mc.gui.setScreen(null);mc.player.setYRot(0);mc.player.setXRot(0);
                Files.writeString(OUT.resolve("compat-wall-request.txt"),"Place the real foreground wall");phase=1;return false;
            }
            if(phase==1){if(!Files.exists(OUT.resolve("compat-wall-ready.txt")))return false;mode();return false;}
            if(phase==2){
                if(ticks-began<20||ClientRenderCompat.shaderPackInUse()!=(iris&&mode==1))return false;
                baseline=AuiInkSelfTest.readbackFrame(mc.gameRenderer.mainRenderTarget(),OUT.resolve("compat-"+name()+"-baseline.png"));
                frames=((Number)field("compatibilityFrames")).longValue();behind=false;emitting=true;emit();phase=3;return false;
            }
            if(phase==3){
                if(((Number)field("compatibilityFrames")).longValue()<frames+3)return false;
                require(!(boolean)field("failed"),"World magic renderer disabled itself");
                require(radiance()>1000,"Real world magic radiance vanished with "+name());
                int[] visible=AuiInkSelfTest.readbackFrame(mc.gameRenderer.mainRenderTarget(),OUT.resolve("compat-"+name()+"-visible.png"));
                int width=mc.gameRenderer.mainRenderTarget().width,height=mc.gameRenderer.mainRenderTarget().height,changed=0;
                for(int y=height/4;y<height*3/4;y++)for(int x=width/3;x<width*2/3;x++){
                    int a=baseline[y*width+x],b=visible[y*width+x];
                    int delta=Math.abs((a&255)-(b&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a>>16&255)-(b>>16&255));
                    if(delta>36)changed++;
                }
                require(changed>25,"Magic was lost between the native world render hook and the final shader-pack image: "+name()+" changed="+changed);
                evidence.add("PASS render "+name()+" visibleMagicPixels="+changed+" capturedDepthMode="+field("compatibilityDepth"));
                behind=true;frames=((Number)field("compatibilityFrames")).longValue();phase=4;return false;
            }
            if(phase==4){
                if(((Number)field("compatibilityFrames")).longValue()<frames+3)return false;
                long hidden=radiance();
                AuiInkSelfTest.readbackFrame(mc.gameRenderer.mainRenderTarget(),OUT.resolve("compat-"+name()+"-occluded.png"));
                require(hidden==0,"Real server wall does not occlude magic with "+name()+": radiance="+hidden);
                evidence.add("PASS render "+name()+" serverWall=occluded radiance=0 freshGpuFrames=true");
                if(iris&&++mode<3){mode();return false;}
                clear();if(mc.gui.hud.isHidden()!=oldHide)mc.gui.hud.toggle();
                evidence.add("PASS RENDER_COMPAT_WORLD "+(iris?"Sodium+Iris; actual shader pack off/on/off, magic stays visible and walls occlude on every switch":"Sodium; actual world hook, visible magic and authoritative wall occlusion"));
                return true;
            }
            return false;
        }
        private long radiance()throws Exception{
            var target=(com.mojang.blaze3d.pipeline.TextureTarget)field("effectTarget");require(target!=null,"World magic was never submitted");
            int bytes=target.width*target.height*8;var device=com.mojang.blaze3d.systems.RenderSystem.getDevice();
            try(var buffer=device.createBuffer(()->"TLB compatibility radiance",com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_COPY_DST|com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_MAP_READ,bytes)){
                var encoder=device.createCommandEncoder();encoder.copyTextureToBuffer(target.getColorTexture(),buffer,0,()->{},0);
                try(var fence=encoder.createFence()){encoder.submit();require(fence.awaitCompletion(5_000_000_000L),"Compatibility GPU readback timed out");}
                try(var mapped=buffer.map(true,false)){long energy=0;for(int i=0;i<bytes;i++)if(i%8<6)energy+=Byte.toUnsignedInt(mapped.data().get(i));return energy;}
            }
        }
    }
    private static void finish(Throwable failure){done=true;if(failure!=null){failure.printStackTrace();evidence.add("FAIL "+failure);}else evidence.add("PASS loopback server UI and "+(Boolean.getBoolean("teamlifebind.archive.chat.only")?"native chat history":"inventory protocol")+"; isolated world only");try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.txt"),String.join("\n",evidence)+"\n");}catch(Exception e){e.printStackTrace();}Minecraft.getInstance().stop();}
}
