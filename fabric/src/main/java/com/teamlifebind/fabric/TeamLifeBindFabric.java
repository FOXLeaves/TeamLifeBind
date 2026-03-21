package com.teamlifebind.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teamlifebind.common.HealthPreset;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.item.BedItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TeamLifeBindFabric implements ModInitializer {

    public static final String MOD_ID = "teamlifebind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Identifier TEAM_BED_ID = id("team_bed");
    private static final RegistryKey<Item> TEAM_BED_KEY = RegistryKey.of(RegistryKeys.ITEM, TEAM_BED_ID);
    private static final TeamLifeBindFabricManager MANAGER = new TeamLifeBindFabricManager();
    public static final Item TEAM_BED_ITEM = new BedItem(Blocks.WHITE_BED, new Item.Settings().registryKey(TEAM_BED_KEY).maxCount(2)) {
        @Override
        public void onCraftByPlayer(ItemStack stack, PlayerEntity player) {
            super.onCraftByPlayer(stack, player);
            TeamLifeBindFabric.manager().bindCraftedTeamBed(stack, player.getUuid());
        }
    };

    private final TeamLifeBindFabricManager manager = MANAGER;

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, TEAM_BED_ID, TEAM_BED_ITEM);
        Registry.register(Registries.CHUNK_GENERATOR, id("round_seeded_noise"), RoundSeededNoiseChunkGenerator.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("tlb")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player != null) {
                            manager.openLobbyMenu(player);
                            return 1;
                        }
                        sendCommandHelp(ctx.getSource());
                        return 1;
                    })
                    .then(CommandManager.literal("help").executes(ctx -> {
                        sendCommandHelp(ctx.getSource());
                        return 1;
                    }))
                    .then(CommandManager.literal("menu").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) {
                            ctx.getSource().sendError(Text.literal(manager.text("command.player_only")));
                            return 0;
                        }
                        manager.openLobbyMenu(player);
                        return 1;
                    }))
                    .then(CommandManager.literal("ready").executes(ctx -> handleReady(ctx.getSource())))
                    .then(CommandManager.literal("unready").executes(ctx -> handleUnready(ctx.getSource())))
                    .then(CommandManager.literal("start").executes(ctx -> {
                        ctx.getSource().sendFeedback(() -> Text.literal(manager.start(ctx.getSource().getServer())), true);
                        return 1;
                    }))
                    .then(CommandManager.literal("stop").executes(ctx -> {
                        ctx.getSource().sendFeedback(() -> Text.literal(manager.stop(ctx.getSource().getServer())), true);
                        return 1;
                    }))
                    .then(CommandManager.literal("status").executes(ctx -> {
                        ctx.getSource().sendFeedback(() -> Text.literal(manager.status(ctx.getSource().getServer())), false);
                        return 1;
                    }))
                    .then(
                        CommandManager.literal("teams")
                            .then(CommandManager.argument("count", IntegerArgumentType.integer(2, 32)).executes(ctx -> {
                                int count = IntegerArgumentType.getInteger(ctx, "count");
                                manager.setTeamCount(count);
                                ctx.getSource().sendFeedback(() -> Text.literal(manager.text("command.team_count.updated", count)), true);
                                return 1;
                            }))
                    )
                    .then(
                        CommandManager.literal("health")
                            .then(CommandManager.argument("preset", StringArgumentType.word()).executes(ctx -> {
                                HealthPreset preset = HealthPreset.fromString(StringArgumentType.getString(ctx, "preset"));
                                manager.setHealthPreset(preset);
                                ctx.getSource().sendFeedback(
                                    () -> Text.literal(manager.text("command.health.updated", manager.text("health_preset." + preset.name()))),
                                    true
                                );
                                return 1;
                            }))
                    )
                    .then(
                        CommandManager.literal("norespawn")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(() -> Text.literal(manager.noRespawnStatus()), false);
                                return 1;
                            })
                            .then(CommandManager.literal("on").executes(ctx -> {
                                manager.setNoRespawnEnabled(true);
                                ctx.getSource().sendFeedback(() -> Text.literal(manager.text("command.norespawn.enabled")), true);
                                return 1;
                            }))
                            .then(CommandManager.literal("off").executes(ctx -> {
                                manager.setNoRespawnEnabled(false);
                                ctx.getSource().sendFeedback(() -> Text.literal(manager.text("command.norespawn.disabled")), true);
                                return 1;
                            }))
                            .then(CommandManager.literal("clear").executes(ctx -> {
                                manager.clearNoRespawnDimensions();
                                ctx.getSource().sendFeedback(() -> Text.literal(manager.text("command.norespawn.cleared")), true);
                                return 1;
                            }))
                            .then(
                                CommandManager.literal("add")
                                    .then(CommandManager.argument("dimension", StringArgumentType.word()).executes(ctx -> {
                                        String dimension = StringArgumentType.getString(ctx, "dimension");
                                        if (!manager.addNoRespawnDimension(dimension)) {
                                            ctx.getSource().sendError(Text.literal(manager.text("command.norespawn.invalid_dimension")));
                                            return 0;
                                        }
                                        ctx.getSource().sendFeedback(() -> Text.literal(manager.text("command.norespawn.added", dimension)), true);
                                        return 1;
                                    }))
                            )
                            .then(
                                CommandManager.literal("remove")
                                    .then(CommandManager.argument("dimension", StringArgumentType.word()).executes(ctx -> {
                                        String dimension = StringArgumentType.getString(ctx, "dimension");
                                        if (!manager.removeNoRespawnDimension(dimension)) {
                                            ctx.getSource().sendError(Text.literal(manager.text("command.norespawn.not_found")));
                                            return 0;
                                        }
                                        ctx.getSource().sendFeedback(() -> Text.literal(manager.text("command.norespawn.removed", dimension)), true);
                                        return 1;
                                    }))
                            )
                    )
            );
        });

        ServerLifecycleEvents.SERVER_STARTED.register(manager::onServerStarted);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> manager.onPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> manager.onPlayerLeave(handler.player.getUuid(), server));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
            manager.onPlayerWorldChange(player, origin.getRegistryKey(), destination.getRegistryKey())
        );

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity attacker) || !(entity instanceof ServerPlayerEntity target)) {
                return ActionResult.PASS;
            }
            if (!manager.shouldCancelFriendlyFire(attacker, target)) {
                return ActionResult.PASS;
            }
            manager.notifyFriendlyFireBlocked(attacker);
            return ActionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            return manager.onUseBlock(serverPlayer, hand, hitResult);
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            if (!manager.isLobbyMenuItem(serverPlayer.getStackInHand(hand))) {
                return ActionResult.PASS;
            }
            manager.openLobbyMenu(serverPlayer);
            return ActionResult.SUCCESS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient() || !(world instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
                return;
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            manager.onBlockBreak(serverPlayer, serverWorld, pos, state);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity.getEntityWorld().isClient()) {
                return;
            }
            manager.onLivingDeath(entity, damageSource);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, amount) -> {
            if (entity.getEntityWorld().isClient()) {
                return true;
            }
            if (!(entity instanceof ServerPlayerEntity target)) {
                return true;
            }
            if (!(damageSource.getAttacker() instanceof ServerPlayerEntity attacker)) {
                return true;
            }
            if (!manager.shouldCancelFriendlyFire(attacker, target)) {
                return true;
            }
            manager.notifyFriendlyFireBlocked(attacker);
            return false;
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> manager.onRespawn(newPlayer));
        ServerEntityEvents.ENTITY_LOAD.register(manager::onEntityLoad);
        ServerTickEvents.END_SERVER_TICK.register(manager::onServerTick);

        LOGGER.info("TeamLifeBind Fabric loaded.");
    }

    static TeamLifeBindFabricManager manager() {
        return MANAGER;
    }

    private int handleReady(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal(manager.text("command.player_only")));
            return 0;
        }
        manager.markReady(player);
        return 1;
    }

    private int handleUnready(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal(manager.text("command.player_only")));
            return 0;
        }
        manager.unready(player);
        return 1;
    }

    private void sendCommandHelp(ServerCommandSource source) {
        for (String key : List.of(
            "command.help.title",
            "command.help.help",
            "command.help.menu",
            "command.help.ready",
            "command.help.unready",
            "command.help.start",
            "command.help.stop",
            "command.help.status",
            "command.help.teams",
            "command.help.health",
            "command.help.norespawn",
            "command.help.norespawn_toggle",
            "command.help.norespawn_add",
            "command.help.norespawn_remove",
            "command.help.norespawn_clear"
        )) {
            source.sendFeedback(() -> Text.literal(manager.text(key)), false);
        }
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
