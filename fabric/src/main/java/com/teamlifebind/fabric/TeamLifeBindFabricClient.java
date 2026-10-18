package com.teamlifebind.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Client-only controls are never loaded by the dedicated server entrypoint. */
public final class TeamLifeBindFabricClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(TeamLifeBindFabric.MOD_ID, "controls"));
    private static final KeyMapping RELOAD = new KeyMapping(
        "key.teamlifebind.reload", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY);

    @Override
    public void onInitializeClient() {
        TeamMagicClient.register();
        ClientPlayNetworking.registerGlobalReceiver(GunVisualPayload.TYPE, (payload, context) ->
                context.client().execute(() -> GunClientPresentation.receive(payload)));
        KeyMappingHelper.registerKeyMapping(RELOAD);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean channel = client.getConnection() != null && ClientPlayNetworking.canSend(GunInputPayload.TYPE);
            GunClientPresentation.tick(client, channel, payload -> ClientPlayNetworking.send(payload));
            boolean requested = false;
            while (RELOAD.consumeClick()) requested = true;
            if (!requested || !channel || !GunClientPresentation.canControl(client) || !GunClientPresentation.hasInitializedGun(client)) return;
            if (ClientPlayNetworking.canSend(GunReloadPayload.TYPE)) {
                ClientPlayNetworking.send(GunReloadPayload.INSTANCE);
            }
        });
    }
}
