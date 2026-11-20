package com.teamlifebind.clientui.mixin;

import com.teamlifebind.clientui.ArchiveRefreshable;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Keep the original asynchronous server status/ping flow while using HTML rows. */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class ArchiveServerEntryMixin implements ArchiveRefreshable {
    @Invoker("refreshStatus") public abstract void tlb$refreshStatus();
    @Override public void tlb$refreshArchiveData(){tlb$refreshStatus();}
}
