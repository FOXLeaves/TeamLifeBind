package com.teamlifebind.clientui.mixin;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AbstractSignEditScreen.class)
public interface ArchiveSignAccess {
    @Accessor("messages") String[] tlb$messages();
    @Accessor("line") void tlb$line(int line);
}
