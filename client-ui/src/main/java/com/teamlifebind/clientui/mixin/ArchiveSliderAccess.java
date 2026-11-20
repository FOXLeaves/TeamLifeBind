package com.teamlifebind.clientui.mixin;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(AbstractSliderButton.class)
public interface ArchiveSliderAccess {
    @Accessor("value") double tlb$value();
    @Invoker("setValue") void tlb$value(double value);
}
