package com.teamlifebind.clientui;
import com.teamlifebind.aui.resource.async.style.StyleHandle;
import java.util.UUID;
/** Deliberately keep a real stylesheet handle pending while its decoded fonts are unpublished. */
public final class ArchiveReadinessProbe {
    public static void main(String[] args){
        check(!ArchiveLoading.textReady(),"Initial loading overlay can draw text before native fonts exist");
        ArchiveLoading.resourceFontsReady();check(ArchiveLoading.textReady(),"Completed font resources never unlock loading text");
        var handle=new StyleHandle(UUID.randomUUID(),1);
        handle.queueTask(StyleHandle.TaskKind.CSS);
        check(!AuiPageReadiness.settled(handle),"unstyled first frame was allowed");
        handle.queueTask(StyleHandle.TaskKind.FONT);
        handle.observeTaskCompletion(StyleHandle.TaskKind.CSS);handle.completeTask(false);
        check(!AuiPageReadiness.settled(handle),"CSS loaded but fonts still pending: text was allowed");
        handle.observeTaskCompletion(StyleHandle.TaskKind.FONT);handle.completeTask(false);
        check(AuiPageReadiness.settled(handle),"published fonts did not unlock the page");
        handle.queueTask(StyleHandle.TaskKind.CSS);
        check(!AuiPageReadiness.settled(handle),"resource reload bypassed the font/CSS gate");
        handle.observeTaskCompletion(StyleHandle.TaskKind.CSS);handle.completeTask(false);
        check(AuiPageReadiness.settled(handle),"resource reload never unlocked");
        System.out.println("ARCHIVE_COLD_FONT_GATE_PASS delayed CSS, delayed font publication and resource reload");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
