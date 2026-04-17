package com.teamlifebind.forge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.MapCodec;
import com.teamlifebind.common.HealthPreset;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
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
import org.jspecify.annotations.NonNull;
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
            public void onCraftedBy(@NonNull ItemStack stack, @NonNull Player player) {
                super.onCraftedBy(stack, player);
                TeamLifeBindForge.manager().bindCraftedTeamBed(stack, player.getUUID());
            }
        }
    );
    public static final RegistryObject<Item> TRACKING_WHEEL_ITEM = ITEMS.register(
        "tracking_wheel",
        () -> new Item(new Item.Properties().setId(itemKey("tracking_wheel")))
    );
    public static final RegistryObject<Item> DEATH_EXEMPTION_TOTEM_ITEM = ITEMS.register(
        "death_exemption_totem",
        () -> new Item(new Item.Properties().setId(itemKey("death_exemption_totem")))
    );
    public static final RegistryObject<Item> LIFE_CURSE_POTION_ITEM = ITEMS.register(
        "life_curse_potion",
        () -> new Item(
            new Item.Properties()
                .setId(itemKey("life_curse_potion"))
                .stacksTo(1)
                .usingConvertsTo(Items.GLASS_BOTTLE)
                .component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
                .component(DataComponents.POTION_CONTENTS, new PotionContents(Optional.of(Potions.WATER), Optional.of(0x541212), List.of(), Optional.empty()))
        ) {
            @Override
            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                ItemStack result = super.finishUsingItem(stack, level, livingEntity);
                if (!level.isClientSide() && livingEntity instanceof ServerPlayer player) {
                    TeamLifeBindForge.manager().handleLifeCursePotionConsumed(player);
                }
                return result;
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
        LivingDamageEvent.BUS.addListener(manager::onLivingDamage);
        LivingEntityUseItemEvent.Finish.BUS.addListener(manager::onLivingItemUseFinish);
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
                .then(
                    Commands.literal("language")
                        .executes(ctx -> handleLanguage(ctx.getSource(), null))
                        .then(Commands.argument("code", StringArgumentType.word()).executes(ctx ->
                            handleLanguage(ctx.getSource(), StringArgumentType.getString(ctx, "code"))
                        ))
                )
                .then(
                    Commands.literal("lang")
                        .executes(ctx -> handleLanguage(ctx.getSource(), null))
                        .then(Commands.argument("code", StringArgumentType.word()).executes(ctx ->
                            handleLanguage(ctx.getSource(), StringArgumentType.getString(ctx, "code"))
                        ))
                )
                .then(
                    Commands.literal("zd")
                        .executes(ctx -> handleZd(ctx.getSource(), null))
                        .then(Commands.argument("id", StringArgumentType.word()).executes(ctx ->
                            handleZd(ctx.getSource(), StringArgumentType.getString(ctx, "id"))
                        ))
                )
                .then(Commands.literal("dev").requires(TeamLifeBindForge::hasAdminPermission).executes(ctx -> handleDevMenu(ctx.getSource())))
                .then(Commands.literal("ready").executes(ctx -> handleReady(ctx.getSource())))
                .then(Commands.literal("unready").executes(ctx -> handleUnready(ctx.getSource())))
                .then(Commands.literal("start").requires(TeamLifeBindForge::hasAdminPermission).executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(manager.start(ctx.getSource().getServer())), true);
                    return 1;
                }))
                .then(Commands.literal("stop").requires(TeamLifeBindForge::hasAdminPermission).executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(manager.stop(ctx.getSource().getServer())), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer sourcePlayer ? sourcePlayer : null;
                    String status = player == null
                        ? manager.status(ctx.getSource().getServer())
                        : manager.status(player, ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal(status), false);
                    return 1;
                }))
                .then(
                    Commands.literal("teams").requires(TeamLifeBindForge::hasAdminPermission)
                        .then(Commands.argument("count", IntegerArgumentType.integer(2, 32)).executes(ctx -> {
                            int count = IntegerArgumentType.getInteger(ctx, "count");
                            manager.setTeamCount(count);
                            ctx.getSource().sendSuccess(() -> Component.literal(manager.text("command.team_count.updated", count)), true);
                            return 1;
                        }))
                )
                .then(
                    Commands.literal("health").requires(TeamLifeBindForge::hasAdminPermission)
                        .then(Commands.argument("preset", StringArgumentType.word()).executes(ctx -> {
                            HealthPreset preset = HealthPreset.fromString(StringArgumentType.getString(ctx, "preset"));
                            manager.setHealthPreset(preset);
                            ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer sourcePlayer ? sourcePlayer : null;
                            String presetLabel = player == null
                                ? manager.text("health_preset." + preset.name())
                                : manager.text(player, "health_preset." + preset.name());
                            ctx.getSource().sendSuccess(
                                () -> Component.literal(
                                    player == null
                                        ? manager.text("command.health.updated", presetLabel)
                                        : manager.text(player, "command.health.updated", presetLabel)
                                ),
                                true
                            );
                            return 1;
                        }))
                )
                .then(
                    Commands.literal("healthsync").requires(TeamLifeBindForge::hasAdminPermission)
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer sourcePlayer ? sourcePlayer : null;
                            String status = player == null ? manager.healthSyncStatus() : manager.healthSyncStatus(player);
                            ctx.getSource().sendSuccess(() -> Component.literal(status), false);
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
                    Commands.literal("norespawn").requires(TeamLifeBindForge::hasAdminPermission)
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer sourcePlayer ? sourcePlayer : null;
                            String status = player == null ? manager.noRespawnStatus() : manager.noRespawnStatus(player);
                            ctx.getSource().sendSuccess(() -> Component.literal(status), false);
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

    private int handleDevMenu(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(manager.text("command.player_only")));
            return 0;
        }
        if (!hasAdminPermission(source)) {
            source.sendFailure(Component.literal(manager.text("command.no_permission")));
            return 0;
        }
        manager.openDevMenu(player);
        return 1;
    }

    private int handleLanguage(CommandSourceStack source, String languageCode) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(manager.text("command.player_only")));
            return 0;
        }
        if (languageCode == null) {
            source.sendSuccess(() -> Component.literal(manager.languageStatusText(player)), false);
            source.sendSuccess(() -> Component.literal(manager.text(player, "command.usage.language")), false);
            return 1;
        }
        if (!manager.setPlayerLanguageCode(player, languageCode)) {
            source.sendFailure(Component.literal(manager.text(player, "command.language.invalid", manager.availableLanguageSummary())));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(manager.text(player, "command.language.updated", manager.effectiveLanguageCode(player))), false);
        return 1;
    }

    private int handleZd(CommandSourceStack source, String joinId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(manager.text("command.player_only")));
            return 0;
        }
        if (joinId == null) {
            source.sendSuccess(() -> Component.literal(manager.teamJoinStatusText(player)), false);
            source.sendSuccess(() -> Component.literal(manager.text(player, "command.usage.zd")), false);
            return 1;
        }
        if ("clear".equalsIgnoreCase(joinId)) {
            manager.clearPendingPartyJoinId(player);
            return 1;
        }
        return manager.setPendingPartyJoinId(player, joinId) ? 1 : 0;
    }

    private void sendCommandHelp(CommandSourceStack source) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer sourcePlayer ? sourcePlayer : null;
        for (String key : List.of(
            "command.help.title",
            "command.help.help",
            "command.help.menu",
            "command.help.language",
            "command.help.language_set",
            "command.help.zd",
            "command.help.zd_set",
            "command.help.ready",
            "command.help.unready",
            "command.help.status"
        )) {
            String message = player == null ? manager.text(key) : manager.text(player, key);
            source.sendSuccess(() -> Component.literal(message), false);
        }
        if (!hasAdminPermission(source)) {
            return;
        }
        for (String key : List.of(
            "command.help.start",
            "command.help.stop",
            "command.help.teams",
            "command.help.health",
            "command.help.healthsync",
            "command.help.healthsync_toggle",
            "command.help.norespawn",
            "command.help.norespawn_toggle",
            "command.help.norespawn_add",
            "command.help.norespawn_remove",
            "command.help.norespawn_clear",
            "command.help.dev"
        )) {
            String message = player == null ? manager.text(key) : manager.text(player, key);
            source.sendSuccess(() -> Component.literal(message), false);
        }
    }

    private static boolean hasAdminPermission(CommandSourceStack source) {
        if (source == null) {
            return false;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        }
        return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
            || player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    public void onServerStarted(ServerStartedEvent event) {
        manager.onServerStarted(event.getServer());
    }

    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            manager.onPlayerJoin(player);
        }
    }

    @SuppressWarnings("resource")
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
        if (manager.isTeamModeVoteItem(event.getItemStack())) {
            manager.toggleTeamModeVote(player);
            event.setCancellationResult(InteractionResult.SUCCESS);
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
        return itemKey("team_bed");
    }

    private static ResourceKey<Item> itemKey(String path) {
        return ITEMS.key(path);
    }

    static TeamLifeBindForgeManager manager() {
        return MANAGER;
    }
}
