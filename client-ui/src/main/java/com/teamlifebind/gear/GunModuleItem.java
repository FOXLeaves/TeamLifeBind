package com.teamlifebind.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public final class GunModuleItem extends Item {
    public interface Mount {}
    public static final String TOOL = "rune_spanner";
    private final GunModule module;

    /** A null module represents the reusable removal tool. */
    public GunModuleItem(Properties properties, GunModule module) {
        super(properties.overrideDescription("item.teamlifebind.gun." + (module == null ? TOOL : module.id())));
        this.module = module;
    }

    private static ItemStack stack(GunModule module) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("teamlifebind", module.id())));
    }

    public static ItemStack randomPart(RandomGenerator random) {
        GunModule[] parts = GunModule.values();
        return stack(parts[random.nextInt(parts.length)]);
    }

    public static void appendSockets(net.minecraft.nbt.CompoundTag tag, List<Component> lore) {
        lore.add(Component.translatable("ui.teamlifebind.gun.fit_hint"));
        for (var socket : GunModule.Socket.values()) {
            var part = GunModule.installed(tag, socket);
            if (part != null) lore.add(Component.translatable("ui.teamlifebind.gun.installed", stack(part).getHoverName()));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("ui.teamlifebind.gun." + (module == null ? TOOL : module.id())));
        tooltip.accept(Component.translatable("ui.teamlifebind.gun.fit_hint"));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack gun = player.getOffhandItem();
        if (!(gun.getItem() instanceof Mount)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.SUCCESS;
        if (server.isSpectator()) return InteractionResult.FAIL;
        CustomData custom = gun.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return InteractionResult.FAIL;
        var tag = custom.copyTag();
        if (tag.getStringOr("tlb_gun_serial", "").isEmpty()) return InteractionResult.FAIL;
        List<ItemStack> returns = new ArrayList<>();
        if (module == null) {
            for (var socket : GunModule.Socket.values()) {
                var old = GunModule.installed(tag, socket);
                if (old != null) returns.add(stack(old));
                tag.remove(socket.key());
            }
            if (returns.isEmpty()) return feedback(server, "empty");
        } else {
            var old = GunModule.installed(tag, module.socket);
            if (old == module) return feedback(server, "same");
            if (old != null) returns.add(stack(old));
            tag.putString(module.socket.key(), module.id());
        }
        tag.remove("tlb_gun_locale");
        gun.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        for (ItemStack old : returns) server.getInventory().placeItemBackInInventory(old, Prediction.SERVER_ONLY);
        if (module == null) return feedback(server, "removed");
        // Return the old part while the active slot is occupied. Vanilla clears a consumed
        // hand after use() returns, which would erase a refund placed in that empty slot.
        player.getItemInHand(hand).shrink(1);
        server.sendSystemMessage(Component.translatable("ui.teamlifebind.gun.installed", stack(module).getHoverName()), true);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult feedback(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("ui.teamlifebind.gun." + key), true);
        return InteractionResult.SUCCESS;
    }
}
