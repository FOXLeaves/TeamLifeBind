package com.teamlifebind.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teamlifebind.common.HealthPreset;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TeamLifeBindFabric implements ModInitializer {

    public static final String MOD_ID = "teamlifebind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Identifier TEAM_BED_ID = id("team_bed");
    private static final ResourceKey<Item> TEAM_BED_KEY = ResourceKey.create(Registries.ITEM, TEAM_BED_ID);
    private static final TeamLifeBindFabricManager MANAGER = new TeamLifeBindFabricManager();
    public static final Item TEAM_BED_ITEM = new BedItem(Blocks.WHITE_BED, new Item.Properties().setId(TEAM_BED_KEY).stacksTo(2)) {
        @Override
        public void onCraftedBy(ItemStack stack, Player player) {
            super.onCraftedBy(stack, player);
            TeamLifeBindFabric.manager().bindCraftedTeamBed(stack, player.getUUID());
        }
    };

    private final TeamLifeBindFabricManager manager = MANAGER;

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.ITEM, TEAM_BED_ID, TEAM_BED_ITEM);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("round_seeded_noise"), RoundSeededNoiseChunkGenerator.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("tlb")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        if (player != null) {
                            manager.openLobbyMenu(player);
                            return 1;
                        }
                        sendCommandHelp(ctx.getSource());
                        return 1;
                    })
                    .then(Commands.literal("help").executes(ctx -> {
                        sendCommandHelp(ctx.getSource());
                        return 1;
                    }))
                    .then(Commands.literal("menu").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        if (player == null) {
                            ctx.getSource().sendFailure(Component.literal(manager.text("command.player_only")));
                            return 0;
                        }
                        manager.openLobbyMenu(player);
                        return 1;
                    }))
                    .then(Commands.literal("ready").executes(ctx -> handleReady(ctx.getSource())))
                    .then(Commands.literal("unready").executes(ctx -> handleUnready(ctx.getSource())))
                    .then(Commands.literal("start").executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(manager.start(ctx.getSource().getServer())), true);
                        return 1;
                    }))
                    .then(Commands.literal("stop").executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(manager.stop(ctx.getSource().getServer())), true);
                        return 1;
                    }))
                    .then(Commands.literal("status").executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(manager.status(ctx.getSource().getServer())), false);
                        return 1;
                    }))
                    .then(
                        Commands.literal("teams")
                            .then(Commands.argument("count", IntegerArgumentType.integer(2, 32)).executes(ctx -> {
                                int count = IntegerArgumentType.getInteger(ctx, "count");
                                manager.setTeamCount(count);
                                ctx.getSource().sendSuccess(() -> Component.literal(manager.text("command.team_count.updated", count)), true);
                                return 1;
                            }))
                    )
                    .then(
                        Commands.literal("health")
                            .then(Commands.argument("preset", StringArgumentType.word()).executes(ctx -> {
                                HealthPreset preset = HealthPreset.fromString(StringArgumentType.getString(ctx, "preset"));
                                manager.setHealthPreset(preset);
                                ctx.getSource().sendSuccess(
                                    () -> Component.literal(manager.text("command.health.updated", manager.text("health_preset." + preset.name()))),
                                    true
                                );
                                return 1;
                            }))
                    )
                    .then(
                        Commands.literal("healthsync")
                            .executes(ctx -> {
                                ctx.getSource().sendSuccess(() -> Component.literal(manager.healthSyncStatus()), false);
                                return 1;
                            })
                            .then(Commands.literal("on").executes(ctx -> {
                                manager.setHealthSyncEnabled(true);
                                ctx.getSource().sendSuccess(() -> Component.literal(manager.text("command.healthsync.enabled")), true);
                                return 1;
                            }))
                            .then(Commands.literal("off").executes(ctx -> {
                                manager.setHealthSyncEnabled(false);
                                ctx.getSource().sendSuccess(() -> Component.literal(manager.text("command.healthsync.disabled")), true);
                                return 1;
                            }))
                    )
                    .then(
                        Commands.literal("norespawn")
                            .executes(ctx -> {
                                ctx.getSource().sendSuccess(() -> Component.literal(manager.noRespawnStatus()), false);
                                return 1;
                            })
                            .then(Commands.literal("on").executes(ctx -> {
                                manager.setNoRespawnEnabled(true);
                                ctx.getSource().sendSuccess(() -> Component.literal(manager.text("command.norespawn.enabled")), true);
                                return 1;
                            }))
                            .then(Commands.literal("off").executes(ctx -> {
                                manager.setNoRespawnEnabled(false);
                                ctx.getSource().sendSuccess(() -> Component.literal(manager.text("command.norespawn.disabled")), true);
                                return 1;
                            }))
                            .then(Commands.literal("clear").executes(ctx -> {
                                manager.clearNoRespawnDimensions();
                                ctx.getSource().sendSuccess(() -> Component.literal(manager.text("command.norespawn.cleared")), true);
                                return 1;
                            }))
                            .then(
                                Commands.literal("add")
                                    .then(Commands.argument("dimension", StringArgumentType.word()).executes(ctx -> {
                                        String dimension = StringArgumentType.getString(ctx, "dimension");
                                        if (!manager.addNoRespawnDimension(dimension)) {
                                            ctx.getSource().sendFailure(Component.literal(manager.text("command.norespawn.invalid_dimension")));
                                            return 0;
                                        }
                                        ctx.getSource().sendSuccess(() -> Component.literal(manager.text("command.norespawn.added", dimension)), true);
                                        return 1;
                                    }))
                            )
                            .then(
                                Commands.literal("remove")
                                    .then(Commands.argument("dimension", StringArgumentType.word()).executes(ctx -> {
                                        String dimension = StringArgumentType.getString(ctx, "dimension");
                                        if (!manager.removeNoRespawnDimension(dimension)) {
                                            ctx.getSource().sendFailure(Component.literal(manager.text("command.norespawn.not_found")));
                                            return 0;
                                        }
                                        ctx.getSource().sendSuccess(() -> Component.literal(manager.text("command.norespawn.removed", dimension)), true);
                                        return 1;
                                    }))
                            )
                    )
            )
        );

        ServerLifecycleEvents.SERVER_STARTED.register(manager::onServerStarted);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> manager.onPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> manager.onPlayerLeave(handler.player.getUUID(), server));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
            manager.onPlayerWorldChange(player, origin.dimension(), destination.dimension())
        );

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer attacker) || !(entity instanceof ServerPlayer target)) {
                return InteractionResult.PASS;
            }
            if (!manager.shouldCancelFriendlyFire(attacker, target)) {
                return InteractionResult.PASS;
            }
            manager.notifyFriendlyFireBlocked(attacker);
            return InteractionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            return manager.onUseBlock(serverPlayer, hand, hitResult);
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            ItemStack heldStack = serverPlayer.getItemInHand(hand);
            if (heldStack.is(net.minecraft.world.item.Items.MILK_BUCKET)) {
                manager.trackMilkBucketUse(serverPlayer, hand);
            }
            if (!manager.isLobbyMenuItem(heldStack)) {
                return InteractionResult.PASS;
            }
            manager.openLobbyMenu(serverPlayer);
            return InteractionResult.SUCCESS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(world instanceof net.minecraft.server.level.ServerLevel serverWorld)) {
                return;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            manager.onBlockBreak(serverPlayer, serverWorld, pos, state);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity.level().isClientSide()) {
                return;
            }
            manager.onLivingDeath(entity, damageSource);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, amount) -> {
            if (entity.level().isClientSide()) {
                return true;
            }
            if (!(entity instanceof ServerPlayer target)) {
                return true;
            }
            if (!(damageSource.getEntity() instanceof ServerPlayer attacker)) {
                return !manager.tryUseTeamTotem(target, amount);
            }
            if (!manager.shouldCancelFriendlyFire(attacker, target)) {
                return !manager.tryUseTeamTotem(target, amount);
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

    private int handleReady(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(manager.text("command.player_only")));
            return 0;
        }
        manager.markReady(player);
        return 1;
    }

    private int handleUnready(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(manager.text("command.player_only")));
            return 0;
        }
        manager.unready(player);
        return 1;
    }

    private void sendCommandHelp(CommandSourceStack source) {
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
            "command.help.healthsync",
            "command.help.healthsync_toggle",
            "command.help.norespawn",
            "command.help.norespawn_toggle",
            "command.help.norespawn_add",
            "command.help.norespawn_remove",
            "command.help.norespawn_clear"
        )) {
            source.sendSuccess(() -> Component.literal(manager.text(key)), false);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
