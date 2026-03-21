package com.teamlifebind.forge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.MapCodec;
import com.teamlifebind.common.HealthPreset;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TeamLifeBindForge.MOD_ID)
public final class TeamLifeBindForge {

    public static final String MOD_ID = "teamlifebind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);
    private static final TeamLifeBindForgeManager MANAGER = new TeamLifeBindForgeManager();
    public static final RegistryObject<Item> TEAM_BED_ITEM = ITEMS.register(
        "team_bed",
        () -> new BedItem(Blocks.WHITE_BED, new Item.Properties().setId(teamBedKey()).stacksTo(2)) {
            @Override
            public void onCraftedBy(ItemStack stack, Player player) {
                super.onCraftedBy(stack, player);
                TeamLifeBindForge.manager().bindCraftedTeamBed(stack, player.getUUID());
            }
        }
    );

    private final TeamLifeBindForgeManager manager = MANAGER;

    public TeamLifeBindForge(FMLJavaModLoadingContext context) {
        ITEMS.register(context.getModBusGroup());
        CHUNK_GENERATORS.register(context.getModBusGroup());
        CHUNK_GENERATORS.register("round_seeded_noise", () -> RoundSeededNoiseChunkGenerator.CODEC);
        RegisterCommandsEvent.BUS.addListener(this::onRegisterCommands);
        ServerStartedEvent.BUS.addListener(this::onServerStarted);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onLogin);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(this::onLogout);
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(this::onChangedDimension);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(this::onRightClickBlock);
        PlayerInteractEvent.RightClickItem.BUS.addListener(this::onRightClickItem);
        BlockEvent.EntityPlaceEvent.BUS.addListener(this::onEntityPlace);
        BlockEvent.BreakEvent.BUS.addListener(this::onBreak);
        LivingDeathEvent.BUS.addListener(this::onLivingDeath);
        LivingDropsEvent.BUS.addListener(this::onLivingDrops);
        ItemTossEvent.BUS.addListener(this::onItemToss);
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(this::onRespawn);
        TickEvent.ServerTickEvent.Post.BUS.addListener(manager::onServerTick);
        AttackEntityEvent.BUS.addListener(manager::onAttackEntity);
        LivingAttackEvent.BUS.addListener(manager::onLivingAttack);
    }

    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("tlb")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
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
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
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
        );
    }

    private int handleReady(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(manager.text("command.player_only")));
            return 0;
        }
        manager.markReady(player);
        return 1;
    }

    private int handleUnready(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
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
            "command.help.norespawn",
            "command.help.norespawn_toggle",
            "command.help.norespawn_add",
            "command.help.norespawn_remove",
            "command.help.norespawn_clear"
        )) {
            source.sendSuccess(() -> Component.literal(manager.text(key)), false);
        }
    }

    public void onServerStarted(ServerStartedEvent event) {
        manager.onServerStarted(event.getServer());
    }

    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            manager.onPlayerJoin(player);
        }
    }

    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            manager.onPlayerLeave(player.getUUID(), player.level().getServer());
        }
    }

    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            manager.onPlayerChangedDimension(player, event.getFrom(), event.getTo());
        }
    }

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        manager.onRightClickBlock(event, TEAM_BED_ITEM.get());
    }

    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!manager.isLobbyMenuItem(event.getItemStack())) {
            return;
        }
        manager.openLobbyMenu(player);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        manager.onEntityPlace(event);
    }

    public void onBreak(BlockEvent.BreakEvent event) {
        manager.onBreak(event);
    }

    public void onLivingDeath(LivingDeathEvent event) {
        manager.onLivingDeath(event);
    }

    public void onLivingDrops(LivingDropsEvent event) {
        manager.onLivingDrops(event);
    }

    public void onItemToss(ItemTossEvent event) {
        manager.onItemToss(event);
    }

    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        manager.onRespawn(event);
    }

    private static ResourceKey<Item> teamBedKey() {
        return ITEMS.key("team_bed");
    }

    static TeamLifeBindForgeManager manager() {
        return MANAGER;
    }
}
