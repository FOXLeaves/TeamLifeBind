package com.teamlifebind.neoforge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teamlifebind.common.HealthPreset;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TeamLifeBindNeoForge.MOD_ID)
public final class TeamLifeBindNeoForge {

    public static final String MOD_ID = "teamlifebind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister.Items ITEM_REGISTRY = ITEMS;
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> TEAM_BED_ITEM = ITEMS.register(
        "team_bed",
        () -> new BedItem(Blocks.WHITE_BED, new Item.Properties().stacksTo(1))
    );

    private final TeamLifeBindNeoForgeManager manager = new TeamLifeBindNeoForgeManager();

    public TeamLifeBindNeoForge() {
        IEventBus modEventBus = ModLoadingContext.get().getActiveContainer().getEventBus();
        ITEM_REGISTRY.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("tlb")
                .executes(ctx -> {
                    sendCommandHelp(ctx.getSource());
                    return 1;
                })
                .then(Commands.literal("help").executes(ctx -> {
                    sendCommandHelp(ctx.getSource());
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
                            ctx.getSource().sendSuccess(() -> Component.literal("[TLB] team-count set to " + count), true);
                            return 1;
                        }))
                )
                .then(
                    Commands.literal("health")
                        .then(Commands.argument("preset", StringArgumentType.word()).executes(ctx -> {
                            HealthPreset preset = HealthPreset.fromString(StringArgumentType.getString(ctx, "preset"));
                            manager.setHealthPreset(preset);
                            ctx.getSource().sendSuccess(() -> Component.literal("[TLB] health-preset set to " + preset.name()), true);
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
                            ctx.getSource().sendSuccess(() -> Component.literal("[TLB] norespawn enabled."), true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            manager.setNoRespawnEnabled(false);
                            ctx.getSource().sendSuccess(() -> Component.literal("[TLB] norespawn disabled."), true);
                            return 1;
                        }))
                        .then(Commands.literal("clear").executes(ctx -> {
                            manager.clearNoRespawnDimensions();
                            ctx.getSource().sendSuccess(() -> Component.literal("[TLB] norespawn blocked dimensions cleared."), true);
                            return 1;
                        }))
                        .then(
                            Commands.literal("add")
                                .then(Commands.argument("dimension", StringArgumentType.word()).executes(ctx -> {
                                    String dimension = StringArgumentType.getString(ctx, "dimension");
                                    if (!manager.addNoRespawnDimension(dimension)) {
                                        ctx.getSource().sendFailure(Component.literal("[TLB] Invalid dimension id. Use namespace:path."));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("[TLB] Added norespawn dimension: " + dimension), true);
                                    return 1;
                                }))
                        )
                        .then(
                            Commands.literal("remove")
                                .then(Commands.argument("dimension", StringArgumentType.word()).executes(ctx -> {
                                    String dimension = StringArgumentType.getString(ctx, "dimension");
                                    if (!manager.removeNoRespawnDimension(dimension)) {
                                        ctx.getSource().sendFailure(Component.literal("[TLB] Dimension not found or invalid."));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("[TLB] Removed norespawn dimension: " + dimension), true);
                                    return 1;
                                }))
                        )
                )
        );
    }

    private int handleReady(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[TLB] \u8be5\u6307\u4ee4\u4ec5\u73a9\u5bb6\u53ef\u7528\u3002"));
            return 0;
        }
        manager.markReady(player);
        return 1;
    }

    private int handleUnready(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[TLB] \u8be5\u6307\u4ee4\u4ec5\u73a9\u5bb6\u53ef\u7528\u3002"));
            return 0;
        }
        manager.unready(player);
        return 1;
    }

    private void sendCommandHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[TLB] \u6307\u4ee4\u8bf4\u660e\uff08\u9ed8\u8ba4\u4e2d\u6587\uff09"), false);
        source.sendSuccess(() -> Component.literal("/tlb help - \u67e5\u770b\u6240\u6709\u6307\u4ee4\u8bf4\u660e"), false);
        source.sendSuccess(() -> Component.literal("/tlb ready - \u73a9\u5bb6\u51c6\u5907\u5f00\u59cb\u6bd4\u8d5b"), false);
        source.sendSuccess(() -> Component.literal("/tlb unready - \u53d6\u6d88\u51c6\u5907"), false);
        source.sendSuccess(() -> Component.literal("/tlb start - \u7ba1\u7406\u5458\u5f3a\u5236\u5f00\u59cb\u6bd4\u8d5b"), false);
        source.sendSuccess(() -> Component.literal("/tlb stop - \u7ed3\u675f\u5f53\u524d\u6bd4\u8d5b"), false);
        source.sendSuccess(() -> Component.literal("/tlb status - \u67e5\u770b\u6bd4\u8d5b\u72b6\u6001"), false);
        source.sendSuccess(() -> Component.literal("/tlb teams <2-32> - \u8bbe\u7f6e\u961f\u4f0d\u6570\u91cf"), false);
        source.sendSuccess(() -> Component.literal("/tlb health <ONE_HEART|HALF_ROW|ONE_ROW> - \u8bbe\u7f6e\u8840\u91cf\u9884\u8bbe"), false);
        source.sendSuccess(() -> Component.literal("/tlb norespawn - \u67e5\u770b\u6b7b\u4ea1\u4e0d\u53ef\u590d\u6d3b\u673a\u5236\u72b6\u6001"), false);
        source.sendSuccess(() -> Component.literal("/tlb norespawn on|off - \u5f00\u542f\u6216\u5173\u95ed\u8be5\u673a\u5236"), false);
        source.sendSuccess(() -> Component.literal("/tlb norespawn add <namespace:path> - \u6dfb\u52a0\u4e0d\u53ef\u590d\u6d3b\u7ef4\u5ea6"), false);
        source.sendSuccess(() -> Component.literal("/tlb norespawn remove <namespace:path> - \u79fb\u9664\u4e0d\u53ef\u590d\u6d3b\u7ef4\u5ea6"), false);
        source.sendSuccess(() -> Component.literal("/tlb norespawn clear - \u6e05\u7a7a\u4e0d\u53ef\u590d\u6d3b\u7ef4\u5ea6\u5217\u8868"), false);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        manager.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            manager.onPlayerJoin(player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            manager.onPlayerLeave(player.getUUID(), player.level().getServer());
        }
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            manager.onPlayerChangedDimension(player, event.getFrom(), event.getTo());
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        manager.onRightClickBlock(event, TEAM_BED_ITEM.get());
    }

    @SubscribeEvent
    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        manager.onEntityPlace(event);
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        manager.onBreak(event);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        manager.onLivingDeath(event);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        manager.onRespawn(event);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        manager.onServerTick(event);
    }
}
