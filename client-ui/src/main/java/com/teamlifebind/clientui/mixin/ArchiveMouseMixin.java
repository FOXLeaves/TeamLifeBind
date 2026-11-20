package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchivePresenter;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(MouseHandler.class)
public abstract class ArchiveMouseMixin {
    @Redirect(method="onButton",require=0,at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
    private boolean tlb$down(Screen screen,MouseButtonEvent event,boolean twice){return ArchivePresenter.handles(screen)?ArchivePresenter.mouse(screen,event,true,twice):screen.mouseClicked(event,twice);}
    @Redirect(method="onButton",require=0,at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"))
    private boolean tlb$up(Screen screen,MouseButtonEvent event){return ArchivePresenter.handles(screen)?ArchivePresenter.mouse(screen,event,false,false):screen.mouseReleased(event);}
    @Redirect(method="onScroll",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z"))
    private boolean tlb$scroll(Screen screen,double x,double y,double dx,double dy){return ArchivePresenter.handles(screen)?ArchivePresenter.scroll(screen,x,y,dx,dy):screen.mouseScrolled(x,y,dx,dy);}
    @Redirect(method="handleAccumulatedMovement",require=0,at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"))
    private boolean tlb$drag(Screen screen,MouseButtonEvent event,double dx,double dy){return ArchivePresenter.handles(screen)?ArchivePresenter.drag(screen,event,dx,dy):screen.mouseDragged(event,dx,dy);}
}
