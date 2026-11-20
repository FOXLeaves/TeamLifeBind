package com.teamlifebind.forge.mixin;

import com.teamlifebind.forge.NativeMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MenuScreens.class)
public abstract class NativeMenuMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void teamlifebind$nativeMenu(MenuType<?> type, Minecraft client, int id, Component title, CallbackInfo callback) {
        if (NativeMenuScreen.open(type, client, id, title)) callback.cancel();
    }
}
