package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchivePresenter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="net.minecraftforge.client.ForgeHooksClient",remap=false)
public abstract class ArchiveForgeHooksMixin {
    @Redirect(method="onScreenKeyPressed",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"))
    private static boolean tlb$key(Screen screen,KeyEvent event){return ArchivePresenter.handles(screen)?ArchivePresenter.key(screen,event):screen.keyPressed(event);}
    @Redirect(method="onScreenCharTyped",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;charTyped(Lnet/minecraft/client/input/CharacterEvent;)Z"))
    private static boolean tlb$character(Screen screen,CharacterEvent event){return ArchivePresenter.handles(screen)?ArchivePresenter.character(screen,event):screen.charTyped(event);}
    @Redirect(method="onScreenMouseDrag",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"))
    private static boolean tlb$drag(Screen screen,MouseButtonEvent event,double dx,double dy){return ArchivePresenter.handles(screen)?ArchivePresenter.drag(screen,event,dx,dy):screen.mouseDragged(event,dx,dy);}
}
