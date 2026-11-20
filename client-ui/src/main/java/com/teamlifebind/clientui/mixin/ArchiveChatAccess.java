package com.teamlifebind.clientui.mixin;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;
import java.util.function.Predicate;
@Mixin(ChatComponent.class)
public interface ArchiveChatAccess {
    @Accessor("allMessages") List<GuiMessage> tlb$messages();
    @Accessor("visibleMessageFilter") Predicate<GuiMessage> tlb$filter();
}
