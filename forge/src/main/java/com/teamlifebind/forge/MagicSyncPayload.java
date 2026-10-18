package com.teamlifebind.forge;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded state owned by the server. Names and power are resolved from the shared catalogue. */
public record MagicSyncPayload(float mana, int maximum, int selected, int castRemaining,
                               List<String> learned, List<Integer> cooldowns) implements CustomPacketPayload {
    public MagicSyncPayload { learned = List.copyOf(learned); cooldowns = List.copyOf(cooldowns); }
    public static final Type<MagicSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("teamlifebind", "magic_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MagicSyncPayload> CODEC = new StreamCodec<FriendlyByteBuf, MagicSyncPayload>() {
        @Override public MagicSyncPayload decode(FriendlyByteBuf b) {
            float mana = b.readFloat(); int max = b.readVarInt(), selected = b.readVarInt(), cast = b.readVarInt(), size = b.readVarInt();
            if (size < 0 || size > 3 || !Float.isFinite(mana) || mana < 0 || max < 1 || max > 300 || mana > max || selected < 0 || selected > 2 || cast < 0 || cast > 12000)
                throw new IllegalArgumentException("Invalid magic state");
            var spells = new java.util.ArrayList<String>(); var cooldowns = new java.util.ArrayList<Integer>();
            for (int i = 0; i < size; i++) { spells.add(b.readUtf(80)); int cool = b.readVarInt(); if (cool < 0 || cool > 12000) throw new IllegalArgumentException("Invalid cooldown"); cooldowns.add(cool); }
            return new MagicSyncPayload(mana, max, selected, cast, spells, cooldowns);
        }
        @Override public void encode(FriendlyByteBuf b, MagicSyncPayload v) {
            b.writeFloat(v.mana()); b.writeVarInt(v.maximum()); b.writeVarInt(v.selected()); b.writeVarInt(v.castRemaining()); b.writeVarInt(v.learned().size());
            for (int i = 0; i < v.learned().size(); i++) { b.writeUtf(v.learned().get(i), 80); b.writeVarInt(v.cooldowns().get(i)); }
        }
    }.cast();
    @Override public Type<MagicSyncPayload> type() { return TYPE; }
}
