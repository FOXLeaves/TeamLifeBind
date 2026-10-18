package com.teamlifebind.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Native books learn through item use; casting is a separate server-authorized key intent. */
public final class TeamMagicItem extends Item {
    public enum Kind {
        SPELL_BOOK("spell_book"), GRIMOIRE("arcane_grimoire"), MANA_POTION("mana_potion"),
        GREATER_MANA_POTION("greater_mana_potion"), MANA_APPLE("mana_apple"), AMNESIA("amnesia_tome");
        private final String id;
        Kind(String id) { this.id = id; }
        public String id() { return id; }
    }
    private final Kind kind;
    public TeamMagicItem(Properties properties, Kind kind) {
        super(properties.stacksTo(kind == Kind.GRIMOIRE || kind == Kind.SPELL_BOOK || kind == Kind.AMNESIA ? 1 : 16));
        this.kind = kind;
    }
    public Kind kind() { return kind; }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.CONSUME;
        return player instanceof ServerPlayer serverPlayer
                && TeamLifeBindFabric.manager().magic().useItem(serverPlayer, player.getItemInHand(hand), kind)
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
}
