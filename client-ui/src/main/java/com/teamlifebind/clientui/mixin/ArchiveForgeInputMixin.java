package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchivePresenter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
/** Forge moves these calls into its event factory; intercept after Pre and before Post. */
@Pseudo
@Mixin(targets="net.minecraftforge.client.event.ForgeEventFactoryClient",remap=false)
public abstract class ArchiveForgeInputMixin {
    @Redirect(method="onScreenMouseClicked",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
    private static boolean tlb$down(Screen screen,MouseButtonEvent event,boolean twice){return ArchivePresenter.handles(screen)?ArchivePresenter.mouse(screen,event,true,twice):screen.mouseClicked(event,twice);}
    @Redirect(method="onScreenMouseReleased",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"))
    private static boolean tlb$up(Screen screen,MouseButtonEvent event){return ArchivePresenter.handles(screen)?ArchivePresenter.mouse(screen,event,false,false):screen.mouseReleased(event);}
}
