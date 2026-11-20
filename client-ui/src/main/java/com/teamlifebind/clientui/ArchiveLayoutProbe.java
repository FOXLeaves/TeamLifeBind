package com.teamlifebind.clientui;
import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.layout.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import java.nio.file.*;
/** Explicit stress fixture for nested intrinsic flex sizing during screen replacement and resize. */
final class ArchiveLayoutProbe {
    private static int ticks,stage,opened;
    private static Screen current;
    private static boolean done;
    static void tick(){
        if(done)return;try{
            var mc=Minecraft.getInstance();if(++ticks<60||mc.gui.overlay()!=null)return;
            if(current==null){open();return;}
            if(ticks-opened<12||ArchivePresenter.document()==null)return;
            var document=ArchivePresenter.document();
            try(var ignored=Document.withContext(document)){
                for(var element:document.querySelectorAll("*"))element.getLayout().size.clear();
                for(var element:document.querySelectorAll(".control-copy")){Size value=Size.natural(element);if(!Double.isFinite(value.height())||value.height()<=0)throw new AssertionError("Invalid control extent");}
            }
            if(++stage>=60){finish(null);return;}open();
        }catch(Throwable failure){finish(failure);}
    }
    private static void open(){var mc=Minecraft.getInstance();int[][] sizes={{1440,900},{1920,1080},{900,600}};var size=sizes[stage%3];mc.getWindow().setWindowed(size[0],size[1]);mc.options.guiScale().set(2);mc.resizeGui();current=new ControlsScreen(new TitleScreen(),mc.options);mc.gui.setScreen(current);opened=ticks;}
    private static void finish(Throwable failure){done=true;try{Path out=Path.of(System.getProperty("teamlifebind.archive.layout.output",".tools/archive-layout-result.txt"));Files.createDirectories(out.getParent());Files.writeString(out,(failure==null?"PASS 60 repeated native control-screen resizes and cold intrinsic measurements":"FAIL stage="+stage+" "+failure)+"\n");}catch(Exception e){e.printStackTrace();}if(failure!=null)failure.printStackTrace();Minecraft.getInstance().stop();}
}
