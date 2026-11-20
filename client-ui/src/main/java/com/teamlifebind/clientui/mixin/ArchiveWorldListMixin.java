package com.teamlifebind.clientui.mixin;

import com.teamlifebind.clientui.ArchiveRefreshable;
import java.util.List;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** World discovery is normally polled inside native painting, which HTML replaces. */
@Mixin(WorldSelectionList.class)
public abstract class ArchiveWorldListMixin implements ArchiveRefreshable {
    @Invoker("pollLevelsIgnoreErrors") public abstract List<LevelSummary> tlb$pollLevels();
    @Invoker("handleNewLevels") public abstract void tlb$publishLevels(List<LevelSummary> levels);
    @Accessor("currentlyDisplayedLevels") public abstract List<LevelSummary> tlb$displayedLevels();
    @Override public void tlb$refreshArchiveData() {
        var levels=tlb$pollLevels();
        if(levels!=tlb$displayedLevels())tlb$publishLevels(levels);
    }
}
