package com.teamlifebind.clientui.mixin;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(BookViewScreen.class)
public interface ArchiveBookAccess {
    @Accessor("bookAccess") BookViewScreen.BookAccess tlb$book();
    @Accessor("currentPage") int tlb$page();
    @Invoker("handleClickEvent") boolean tlb$link(ClickEvent event);
}
