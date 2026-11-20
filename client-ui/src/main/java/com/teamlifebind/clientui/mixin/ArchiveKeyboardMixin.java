package com.teamlifebind.clientui.mixin;
import com.teamlifebind.clientui.ArchivePresenter;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(KeyboardHandler.class)
public abstract class ArchiveKeyboardMixin {
    @Redirect(method="keyPress",require=0,at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"))
    private boolean tlb$key(Screen screen,KeyEvent event){return ArchivePresenter.handles(screen)?ArchivePresenter.key(screen,event):screen.keyPressed(event);}
    @Redirect(method="charTyped",require=0,at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;charTyped(Lnet/minecraft/client/input/CharacterEvent;)Z"))
    private boolean tlb$character(Screen screen,CharacterEvent event){return ArchivePresenter.handles(screen)?ArchivePresenter.character(screen,event):screen.charTyped(event);}
}
