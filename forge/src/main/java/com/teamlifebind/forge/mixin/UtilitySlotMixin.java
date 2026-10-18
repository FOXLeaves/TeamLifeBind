package com.teamlifebind.forge.mixin;

import com.teamlifebind.forge.TeamLifeBindForge;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class UtilitySlotMixin {
    @Shadow public ServerPlayer player;
    // TAIL runs after the packet is transferred to the server thread and the selected slot changes.
    @Inject(method = "handleSetCarriedItem", at = @At("TAIL"))
    private void tlb$interruptChangedSlot(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        TeamLifeBindForge.interruptUtilitySlot(player);
    }
    @Inject(method = "handlePlayerAction", at = @At("RETURN"))
    private void tlb$interruptHandSwap(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                || packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) TeamLifeBindForge.interruptUtilityHandSwap(player);
        else if (packet.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) TeamLifeBindForge.interruptUtility(player);
    }
}
