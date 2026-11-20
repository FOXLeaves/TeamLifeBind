package com.teamlifebind.clientui.mixin;
import net.minecraft.client.gui.components.CommandSuggestions;
import com.mojang.brigadier.suggestion.Suggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.concurrent.CompletableFuture;
@Mixin(CommandSuggestions.class)
public interface ArchiveSuggestionsAccess {
    @Accessor("pendingSuggestions") CompletableFuture<Suggestions> tlb$pending();
}
