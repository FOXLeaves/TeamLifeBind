package com.teamlifebind.clientui.mixin;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ChatScreen.class)
public interface ArchiveChatScreenAccess {
    @Accessor("input") EditBox tlb$input();
    @Accessor("commandSuggestions") CommandSuggestions tlb$suggestions();
}
