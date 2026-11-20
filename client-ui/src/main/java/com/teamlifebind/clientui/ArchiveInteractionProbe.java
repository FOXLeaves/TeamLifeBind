package com.teamlifebind.clientui;

import com.mojang.blaze3d.platform.InputConstants;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.init.Element;
import com.teamlifebind.aui.layout.Position;
import com.teamlifebind.aui.screen.AuiLinkedScreen;
import com.teamlifebind.common.NativeHud;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.input.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

/** Opt-in loopback checks: real selection packets, server menus and the shipping GPU/DOM paths. */
final class ArchiveInteractionProbe {
    private final Path out;
    private final List<String> evidence;
    private final Map<String,HudLayoutPreferences.Placement> original=HudLayout.preferences().snapshot();
    private final String platform=System.getProperty("teamlifebind.archive.platform","fabric");
    private final GunWorkshopProbe workshop = new GunWorkshopProbe();
    private int phase,age,modeIndex,campaignIndex;
    private long sigilFrames;
    private int[] firstSigil;
    private InputConstants.Key originalWheel;
    private Map<String,HudLayoutPreferences.Placement> edited;
    private boolean editorScrolled;
    private final GameType[] modes={GameType.CREATIVE,GameType.SURVIVAL,GameType.ADVENTURE,GameType.SPECTATOR};
    private final String[] campaigns={"SUR","PVE","RG","PVP"};
    ArchiveInteractionProbe(Path out,List<String> evidence){this.out=out;this.evidence=evidence;}
    private void next(){phase++;age=0;}
    boolean tick()throws Exception{
        if (GunWorkshopProbe.ENABLED) return workshop.tick(this, out, evidence);
        var mc=Minecraft.getInstance();if(++age<20)return false;
        switch(phase){
            case 0->{
                mc.gui.setScreen(null);HudLayout.preferences().clear();
                request("magic", "execute as $PLAYER run tlb magic test fire_bolt 1","execute as $PLAYER run tlb magic test ice_beam 2","execute as $PLAYER run tlb magic test arcane_bolt 3","clear $PLAYER");next();
            }
            case 1->{
                if(!ack("magic"))return false;
                require(model().slots().stream().allMatch(NativeHud.Slot::learned),"Server did not teach three independent spells");
                require(mc.player.getMainHandItem().isEmpty(),"Fixture is not empty-handed");openWheel();next();
            }
            case 2->{
                var doc=linked();if(!ready(doc))return false;
                require(MagicSigilRenderer.presentations()>0&&MagicSigilRenderer.lastTarget()!=null,"3D spell preview was not rendered by the GPU");
                sigilFrames=MagicSigilRenderer.presentations();firstSigil=AuiInkSelfTest.readback(MagicSigilRenderer.lastTarget(),out.resolve("interaction-sigil-a.png"));
                require(Arrays.stream(firstSigil).filter(p->(p>>>24)>20).count()>100,"3D preview is transparent/empty");
                shot("spell-picker");click(doc,doc.getElementById("magic-slot-1"));require(mc.gui.screen()==null,"Left-click did not commit the spell card");next();
            }
            case 3->{
                require(mc.gui.screen()==null&&selected()==1,"Mouse spell selection did not survive server acknowledgement");
                var hud=(Document)field(ArchiveHud.class,null,"page");require(hud!=null&&hud.body.getAttribute("data-magic").equals("true"),"Learned magic HUD is hidden without a grimoire");
                require(hud.getElementById("arcana-slot-2")!=null,"Persistent HUD lost its third cooldown slot");
                openWheel();next();
            }
            case 4->{if(!ready(linked()))return false;mc.gui.screen().keyPressed(key(InputConstants.KEY_3));next();}
            case 5->{
                var doc=linked();require(doc.body.getAttribute("data-selected").equals("2"),"Stationary mouse overrode keyboard spell selection");
                int[] second=AuiInkSelfTest.readback(MagicSigilRenderer.lastTarget(),out.resolve("interaction-sigil-b.png"));
                require(MagicSigilRenderer.presentations()>sigilFrames&&!Arrays.equals(firstSigil,second),"3D preview does not animate/change with selected magic");
                var binding=wheel();originalWheel=(InputConstants.Key)field(KeyMapping.class,binding,"key");binding.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_P));KeyMapping.resetMapping();
                require(!mc.gui.screen().keyReleased(key(InputConstants.KEY_G))&&mc.gui.screen()!=null,"Old spell key committed after rebinding");
                mc.gui.screen().keyReleased(key(InputConstants.KEY_P));next();
            }
            case 6->{
                require(mc.gui.screen()==null&&selected()==2,"Rebound spell key did not commit the third spell");
                wheel().setKey(originalWheel);KeyMapping.resetMapping();originalWheel=null;
                evidence.add("PASS MAGIC_SELECTION actual mouse + number keys + rebound release; three server-acknowledged slots; empty-hand persistent mana/CD; animated GPU 3D preview");
                mc.gui.setScreen(new GameModeSwitcherScreen());next();
            }
            case 7->{
                var doc=ArchivePresenter.document();if(!ready(doc))return false;
                var controls=doc.querySelectorAll(".control");require(controls.size()==4,"Debug game-mode popup is not four cards");
                if(modeIndex==0)shot("gamemode");click(doc,controls.get(modeIndex));
                require(ArchiveGameModes.selected(ArchiveModel.read(mc.gui.screen()).controls.get(modeIndex)),"Game-mode card click selected the wrong native icon: "+doc.querySelector(".surface").getBoundingClientRect()+" transition="+AuiScreenTransitions.active());
                if(modeIndex==0){ArchivePresenter.key(mc.gui.screen(),key(InputConstants.KEY_F4));ArchivePresenter.key(mc.gui.screen(),key(InputConstants.KEY_F4));ArchivePresenter.key(mc.gui.screen(),key(InputConstants.KEY_F4));ArchivePresenter.key(mc.gui.screen(),key(InputConstants.KEY_F4));}
                mc.gui.screen().keyReleased(key(InputConstants.KEY_F3));next();
            }
            case 8->{
                require(mc.gui.screen()==null&&mc.gameMode.getPlayerMode()==modes[modeIndex],"F3 release did not produce authoritative game mode "+modes[modeIndex]);
                if(++modeIndex<4){mc.gui.setScreen(new GameModeSwitcherScreen());phase=7;age=0;return false;}
                request("survival","gamemode survival $PLAYER");evidence.add("PASS GAMEMODE custom four-card popup; native F4 cycling; native F3 release; four server-confirmed modes");next();
            }
            case 9->{if(!ack("survival")||mc.gameMode.getPlayerMode()!=GameType.SURVIVAL)return false;press(InputConstants.KEY_F8);next();}
            case 10->{
                require(mc.gui.screen() instanceof HudEditorScreen,"F8 did not open the HUD editor");var doc=linked();if(!ready(doc))return false;
                var canvas=doc.getElementById("editor-handles").getBoundingClientRect();require(canvas.width>=doc.getViewportSize().width()-1&&canvas.height>=doc.getViewportSize().height()-1,"HUD editing handles have no visible canvas");
                click(doc,doc.getElementById("edit-part-HOTBAR"));
                var box=doc.getElementById("edit-handle-HOTBAR").getBoundingClientRect();var from=doc.documentToGuiPosition(new Position(box.right-12,box.top+12));
                var down=new MouseButtonEvent(from.x,from.y,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));mc.gui.screen().mouseClicked(down,false);
                mc.gui.screen().mouseDragged(new MouseButtonEvent(from.x+40,from.y-35,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0)),40,-35);mc.gui.screen().mouseReleased(down);
                mc.gui.screen().mouseScrolled(from.x,from.y,0,1);
                var placement=HudLayout.preferences().get("quickdock");require(placement!=null&&placement.scale()>1,"Individual HUD drag/scale was not applied");
                require(HudLayout.preferences().get("arcana")==null,"Editing the hotbar also moved/resized magic");
                edited=HudLayout.preferences().snapshot();shot("hud-editor");mc.gui.screen().keyPressed(key(InputConstants.KEY_RETURN));next();
            }
            case 11->{
                require(mc.gui.screen()==null,"HUD save did not return to the world");
                var file=mc.gameDirectory.toPath().resolve("config/teamlifebind-hud-layout.properties");require(new HudLayoutPreferences(file).snapshot().equals(edited),"Saved HUD positions did not survive preference reload");
                press(InputConstants.KEY_F8);org.lwjgl.sdl.SDLVideo.SDL_RestoreWindow(mc.getWindow().handle());mc.getWindow().setWindowed(720,480);mc.resizeGui();next();
            }
            case 12->{
                var doc=linked();if(!ready(doc))return false;
                var list=doc.getElementById("editor-parts");var box=list.getBoundingClientRect();var point=doc.documentToGuiPosition(new Position(box.left+10,box.top+10));
                if(!editorScrolled){mc.gui.screen().mouseScrolled(point.x,point.y,0,-20);editorScrolled=true;age=0;return false;}
                if(list.scrollHeight-list.getClientHeight()>1)require(list.getScrollTop()>0,"Compact HUD component list cannot scroll");
                else require(doc.getElementById("edit-part-COUNTDOWN").getBoundingClientRect().bottom<=box.bottom+1,"Compact HUD component list clips its last item");
                click(doc,doc.getElementById("edit-part-COUNTDOWN"));mc.gui.screen().mouseScrolled(mc.getWindow().getGuiScaledWidth()-10,20,0,1);
                require(HudLayout.preferences().get("countdown")!=null,"Compact editor cannot select and scale its last component");
                mc.gui.screen().keyPressed(key(InputConstants.KEY_LEFT));mc.gui.screen().keyPressed(key(InputConstants.KEY_ESCAPE));
                require(HudLayout.preferences().snapshot().equals(edited),"Cancel did not roll back the editing transaction");
                require(AuiScreenTransitions.active(),"HUD editor skipped its closing transition");
                HudLayout.preferences().replace(original);HudLayout.preferences().save();
                mc.getWindow().setWindowed(1920,1080);mc.resizeGui();
                evidence.add("PASS HUD_EDITOR F8 entry; native drag and individual scale; save/reload; cancel rollback; close animation; independent components");
                request("campaign","execute as $PLAYER run tlb campaign");next();
            }
            case 13->{
                if(!ack("campaign")||!(mc.gui.screen() instanceof MenuAccess<?>))return false;
                var doc=linked();if(!ready(doc))return false;click(doc,doc.getElementById("slot-4"));next();
            }
            case 14->{
                var doc=linked();if(!ready(doc))return false;
                require(actions().stream().filter(a->a.startsWith("play:")).count()==4,"Mode picker does not offer all four modes");click(doc,doc.getElementById("slot-"+(10+campaignIndex*2)));next();
            }
            case 15->{
                var found=actions();var expected=switch(campaigns[campaignIndex]){case "SUR"->Set.of("play");case "PVE"->Set.of("play","pve");case "RG"->Set.of("play","pve","lives");default->Set.of("play","mode","dragon","objectives","border");};
                require(found.equals(expected),"Settings unrelated to "+campaigns[campaignIndex]+" remain visible: "+found);shot("campaign-"+campaigns[campaignIndex].toLowerCase(Locale.ROOT));
                if(++campaignIndex<4){phase=13;age=0;return false;}
                mc.player.closeContainer();evidence.add("PASS CAMPAIGN real server menu packets: explicit four-mode picker; SUR/PVE/RG/PVP-specific settings");
                request("countdown","title $PLAYER actionbar {\"translate\":\"tlb.ui.countdown\",\"fallback\":\"\",\"with\":[3,3,\"合作生存 SUR\"]}");next();
            }
            case 16->{
                if(!ack("countdown"))return false;var doc=ArchiveOverlays.document();if(!ready(doc))return false;
                var host=doc.getElementById("countdown");require(host.getTextContent().contains("3")&&!host.getAttribute("style").contains("display:none"),"Server action-bar countdown did not become the custom UI");shot("countdown");next();
            }
            case 17->{
                if(age<40)return false;var host=ArchiveOverlays.document().getElementById("countdown");require(host.getAttribute("style").contains("display:none"),"Canceled/expired countdown remained stuck onscreen");
                evidence.add("PASS COUNTDOWN semantic server packet; real digit/progress UI; expires when the server stops sending");
                for(int i=0;i<3;i++)SystemToast.add(mc.gui.toastManager(),new SystemToast.SystemToastId(),Component.literal("通知 "+(i+1)),Component.literal("状态面板应始终可读 · 队列测试"));
                request("status","scoreboard players set 状态一 tlb_ui_fixture 1","scoreboard players set 状态二 tlb_ui_fixture 2","scoreboard players set 状态三 tlb_ui_fixture 3","scoreboard players set 状态四 tlb_ui_fixture 4","scoreboard players set 状态五 tlb_ui_fixture 5","scoreboard players set 状态六 tlb_ui_fixture 6","scoreboard players set 状态七 tlb_ui_fixture 7","scoreboard players set 状态八 tlb_ui_fixture 8");next();
            }
            case 18->{
                if(!ack("status"))return false;var doc=ArchiveOverlays.document();if(!ready(doc))return false;
                var a=doc.getElementById("notifications").getBoundingClientRect();var b=doc.getElementById("sidebar").getBoundingClientRect();
                require(doc.querySelectorAll(".notification").size()==1&&!doc.getElementById("notifications").getAttribute("style").contains("display:none"),"Notifications stacked or vanished instead of queuing");
                require(a.right<=b.left||a.left>=b.right||a.bottom<=b.top||a.top>=b.bottom,"Notifications overlap the match status");
                shot("notifications");evidence.add("PASS NOTIFICATION_LAYOUT real native toast queue; one card; no overlap with eight-line server status + boss bar");
                request("stop-test","execute as $PLAYER run tlb magic teststop");next();
            }
            default->{return ack("stop-test");}
        }
        return false;
    }
    private static KeyEvent key(int key){return new KeyEvent(key,org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyFromScancode(key,(short)0,false),0);}
    private void press(int key){var mc=Minecraft.getInstance();mc.keyboardHandler.keyPress(mc.getWindow().handle(),InputConstants.PRESS,key(key));mc.keyboardHandler.keyPress(mc.getWindow().handle(),InputConstants.RELEASE,key(key));}
    private Class<?> type(String suffix)throws ClassNotFoundException{return Class.forName("com.teamlifebind."+platform+"."+suffix);}
    private void openWheel()throws Exception{Minecraft.getInstance().gui.setScreen((Screen)type("MagicWheelScreen").getConstructor().newInstance());}
    private NativeHud.Magic model()throws Exception{var method=type("MagicClientPresentation").getDeclaredMethod("hudModel");method.setAccessible(true);return (NativeHud.Magic)method.invoke(null);}
    private int selected()throws Exception{return (int)type("MagicClientPresentation").getMethod("selected").invoke(null);}
    private KeyMapping wheel()throws Exception{return (KeyMapping)field(type("TeamMagicClient"),null,"WHEEL");}
    private static Object field(Class<?> owner,Object target,String name)throws Exception{var field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private Document linked(){return Minecraft.getInstance().gui.screen() instanceof AuiLinkedScreen screen?screen.getLinkedDocument():null;}
    private static boolean ready(Document document){return document!=null&&AuiPageReadiness.ready(document);}
    void request(String id,String... commands)throws Exception{Files.writeString(out.resolve("interaction-request.json"),new com.google.gson.Gson().toJson(Map.of("id",id,"commands",commands)));}
    private boolean ack(String id)throws Exception{return Files.exists(out.resolve("interaction-ack.txt"))&&Files.readString(out.resolve("interaction-ack.txt")).equals(id);}
    private static Set<String> actions(){
        var menu=((MenuAccess<?>)Minecraft.getInstance().gui.screen()).getMenu();var result=new HashSet<String>();
        for(var slot:menu.slots){var data=slot.getItem().get(DataComponents.CUSTOM_DATA);if(data!=null){String action=data.copyTag().getStringOr("tlb_campaign_action","");if(!action.isEmpty())result.add(action);}}return result;
    }
    private static void click(Document doc,Element element){
        require(element!=null,"Requested HTML control is missing");var mc=Minecraft.getInstance();
        try(var ignored=Document.withContext(doc)){var box=element.getBoundingClientRect();var point=doc.documentToGuiPosition(new Position((box.left+box.right)/2,(box.top+box.bottom)/2));var window=mc.getWindow();
            mc.mouseHandler.onMove(window.handle(),point.x*window.getScreenWidth()/window.getGuiScaledWidth(),point.y*window.getScreenHeight()/window.getGuiScaledHeight(),0,0);
            mc.mouseHandler.onButton(window.handle(),new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0),InputConstants.PRESS);
            mc.mouseHandler.onButton(window.handle(),new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0),InputConstants.RELEASE);}
    }
    private void shot(String name)throws Exception{AuiInkSelfTest.readbackFrame(Minecraft.getInstance().gameRenderer.mainRenderTarget(),out.resolve("interaction-"+name+".png"));}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
