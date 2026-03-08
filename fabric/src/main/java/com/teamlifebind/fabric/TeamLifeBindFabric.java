package com.teamlifebind.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teamlifebind.common.HealthPreset;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.item.BedItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TeamLifeBindFabric implements ModInitializer {

    public static final String MOD_ID = "teamlifebind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Item TEAM_BED_ITEM = new BedItem(Blocks.WHITE_BED, new Item.Settings().maxCount(1));

    private final TeamLifeBindFabricManager manager = new TeamLifeBindFabricManager();

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, id("team_bed"), TEAM_BED_ITEM);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("tlb")
                    .executes(ctx -> {
                        sendCommandHelp(ctx.getSource());
                        return 1;
                    })
                    .then(CommandManager.literal("help").executes(ctx -> {
                        sendCommandHelp(ctx.getSource());
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
                                ctx.getSource().sendFeedback(() -> Text.literal("[TLB] team-count set to " + count), true);
                                return 1;
                            }))
                    )
                    .then(
                        CommandManager.literal("health")
                            .then(CommandManager.argument("preset", StringArgumentType.word()).executes(ctx -> {
                                String raw = StringArgumentType.getString(ctx, "preset");
                                HealthPreset preset = HealthPreset.fromString(raw);
                                manager.setHealthPreset(preset);
                                ctx.getSource().sendFeedback(() -> Text.literal("[TLB] health-preset set to " + preset.name()), true);
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
                                ctx.getSource().sendFeedback(() -> Text.literal("[TLB] norespawn enabled."), true);
                                return 1;
                            }))
                            .then(CommandManager.literal("off").executes(ctx -> {
                                manager.setNoRespawnEnabled(false);
                                ctx.getSource().sendFeedback(() -> Text.literal("[TLB] norespawn disabled."), true);
                                return 1;
                            }))
                            .then(CommandManager.literal("clear").executes(ctx -> {
                                manager.clearNoRespawnDimensions();
                                ctx.getSource().sendFeedback(() -> Text.literal("[TLB] norespawn blocked dimensions cleared."), true);
                                return 1;
                            }))
                            .then(
                                CommandManager.literal("add")
                                    .then(CommandManager.argument("dimension", StringArgumentType.word()).executes(ctx -> {
                                        String dimension = StringArgumentType.getString(ctx, "dimension");
                                        if (!manager.addNoRespawnDimension(dimension)) {
                                            ctx.getSource().sendError(Text.literal("[TLB] Invalid dimension id. Use namespace:path."));
                                            return 0;
                                        }
                                        ctx.getSource().sendFeedback(() -> Text.literal("[TLB] Added norespawn dimension: " + dimension), true);
                                        return 1;
                                    }))
                            )
                            .then(
                                CommandManager.literal("remove")
                                    .then(CommandManager.argument("dimension", StringArgumentType.word()).executes(ctx -> {
                                        String dimension = StringArgumentType.getString(ctx, "dimension");
                                        if (!manager.removeNoRespawnDimension(dimension)) {
                                            ctx.getSource().sendError(Text.literal("[TLB] Dimension not found or invalid."));
                                            return 0;
                                        }
                                        ctx.getSource().sendFeedback(() -> Text.literal("[TLB] Removed norespawn dimension: " + dimension), true);
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

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            return manager.onUseBlock(serverPlayer, hand, hitResult);
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

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> manager.onRespawn(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(manager::onServerTick);

        LOGGER.info("TeamLifeBind Fabric loaded.");
    }

    private int handleReady(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[TLB] \u8be5\u6307\u4ee4\u4ec5\u73a9\u5bb6\u53ef\u7528\u3002"));
            return 0;
        }
        manager.markReady(player);
        return 1;
    }

    private int handleUnready(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("[TLB] \u8be5\u6307\u4ee4\u4ec5\u73a9\u5bb6\u53ef\u7528\u3002"));
            return 0;
        }
        manager.unready(player);
        return 1;
    }

    private void sendCommandHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("[TLB] \u6307\u4ee4\u8bf4\u660e\uff08\u9ed8\u8ba4\u4e2d\u6587\uff09"), false);
        source.sendFeedback(() -> Text.literal("/tlb help - \u67e5\u770b\u6240\u6709\u6307\u4ee4\u8bf4\u660e"), false);
        source.sendFeedback(() -> Text.literal("/tlb ready - \u73a9\u5bb6\u51c6\u5907\u5f00\u59cb\u6bd4\u8d5b"), false);
        source.sendFeedback(() -> Text.literal("/tlb unready - \u53d6\u6d88\u51c6\u5907"), false);
        source.sendFeedback(() -> Text.literal("/tlb start - \u7ba1\u7406\u5458\u5f3a\u5236\u5f00\u59cb\u6bd4\u8d5b"), false);
        source.sendFeedback(() -> Text.literal("/tlb stop - \u7ed3\u675f\u5f53\u524d\u6bd4\u8d5b"), false);
        source.sendFeedback(() -> Text.literal("/tlb status - \u67e5\u770b\u6bd4\u8d5b\u72b6\u6001"), false);
        source.sendFeedback(() -> Text.literal("/tlb teams <2-32> - \u8bbe\u7f6e\u961f\u4f0d\u6570\u91cf"), false);
        source.sendFeedback(() -> Text.literal("/tlb health <ONE_HEART|HALF_ROW|ONE_ROW> - \u8bbe\u7f6e\u8840\u91cf\u9884\u8bbe"), false);
        source.sendFeedback(() -> Text.literal("/tlb norespawn - \u67e5\u770b\u6b7b\u4ea1\u4e0d\u53ef\u590d\u6d3b\u673a\u5236\u72b6\u6001"), false);
        source.sendFeedback(() -> Text.literal("/tlb norespawn on|off - \u5f00\u542f\u6216\u5173\u95ed\u8be5\u673a\u5236"), false);
        source.sendFeedback(() -> Text.literal("/tlb norespawn add <namespace:path> - \u6dfb\u52a0\u4e0d\u53ef\u590d\u6d3b\u7ef4\u5ea6"), false);
        source.sendFeedback(() -> Text.literal("/tlb norespawn remove <namespace:path> - \u79fb\u9664\u4e0d\u53ef\u590d\u6d3b\u7ef4\u5ea6"), false);
        source.sendFeedback(() -> Text.literal("/tlb norespawn clear - \u6e05\u7a7a\u4e0d\u53ef\u590d\u6d3b\u7ef4\u5ea6\u5217\u8868"), false);
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
