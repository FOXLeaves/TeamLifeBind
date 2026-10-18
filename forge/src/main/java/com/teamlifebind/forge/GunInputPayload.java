package com.teamlifebind.forge;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Current-key state only; the server chooses the weapon profile, ammo, cadence and damage. */
public record GunInputPayload(UUID serial, boolean firing, boolean aiming) implements CustomPacketPayload {
    public static final UUID CANCEL_SERIAL = new UUID(0L, 0L);
    public static final GunInputPayload CANCEL = new GunInputPayload(CANCEL_SERIAL, false, false);
    public static final Type<GunInputPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("teamlifebind", "gun_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunInputPayload> CODEC = new StreamCodec<FriendlyByteBuf, GunInputPayload>() {
        @Override public GunInputPayload decode(FriendlyByteBuf buffer) {
            return new GunInputPayload(buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean());
        }
        @Override public void encode(FriendlyByteBuf buffer, GunInputPayload value) {
            buffer.writeUUID(value.serial()); buffer.writeBoolean(value.firing()); buffer.writeBoolean(value.aiming());
        }
    }.cast();
    @Override public Type<GunInputPayload> type() { return TYPE; }
}
