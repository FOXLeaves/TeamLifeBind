package com.teamlifebind.neoforge;

import com.teamlifebind.gear.GunModule;
import com.teamlifebind.gear.GunModuleItem;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.MapCodec;
import com.teamlifebind.common.HealthPreset;
import com.teamlifebind.common.GunProfile;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TeamLifeBindNeoForge.MOD_ID)
public final class TeamLifeBindNeoForge {

    public static final String MOD_ID = "teamlifebind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);
    public static final DeferredRegister.Items ITEM_REGISTRY = ITEMS;
    private static final TeamLifeBindNeoForgeManager MANAGER = new TeamLifeBindNeoForgeManager();
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> TEAM_BED_ITEM = ITEMS.registerItem(
        "team_bed",
        properties -> new BlockItem(Blocks.BED.white(), properties.stacksTo(2)) {
            @Override
            public void onCraftedBy(@NonNull ItemStack stack, @NonNull Player player) {
                super.onCraftedBy(stack, player);
                TeamLifeBindNeoForge.manager().bindCraftedTeamBed(stack, player.getUUID());
            }
        }
    );
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> TRACKING_WHEEL_ITEM = ITEMS.registerItem(
        "tracking_wheel",
        Item::new
    );
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> DEATH_EXEMPTION_TOTEM_ITEM = ITEMS.registerItem(
        "death_exemption_totem",
        properties -> new Item(properties) {
            @Override
            public void onCraftedBy(@NonNull ItemStack stack, @NonNull Player player) {
                super.onCraftedBy(stack, player);
                if (player instanceof ServerPlayer serverPlayer) {
                    TeamLifeBindNeoForge.manager().refreshTotemLore(stack, serverPlayer);
                }
            }
        }
    );
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> RESONANCE_WHISTLE_ITEM = ITEMS.registerItem("resonance_whistle", properties -> new Item(properties.stacksTo(1)) {
        @Override
        public void onCraftedBy(ItemStack stack, Player player) {
            super.onCraftedBy(stack, player);
            if (player instanceof ServerPlayer serverPlayer) TeamLifeBindNeoForge.manager().refreshUtilityLore(stack, serverPlayer);
        }
    });
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> EMERGENCY_BANDAGE_ITEM = ITEMS.registerItem("emergency_bandage", properties -> new Item(properties) {
        @Override
        public void onCraftedBy(ItemStack stack, Player player) {
            super.onCraftedBy(stack, player);
            if (player instanceof ServerPlayer serverPlayer) TeamLifeBindNeoForge.manager().refreshUtilityLore(stack, serverPlayer);
        }
    });
    public static final net.neoforged.neoforge.registries.DeferredItem<Item> LIFE_CURSE_POTION_ITEM = ITEMS.registerItem(
        "life_curse_potion",
        properties -> new Item(
            properties
                .stacksTo(1)
                .usingConvertsTo(Items.GLASS_BOTTLE)
                .component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
                .component(DataComponents.POTION_CONTENTS, new PotionContents(Optional.of(Potions.WATER), Optional.of(0x541212), List.of(), Optional.empty()))
        ) {
            @Override
            public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
                ItemStack result = super.finishUsingItem(stack, level, livingEntity);
                if (!level.isClientSide() && livingEntity instanceof ServerPlayer player) {
                    TeamLifeBindNeoForge.manager().handleLifeCursePotionConsumed(player);
                }
                return result;
            }
        }
    );

    public static final net.neoforged.neoforge.registries.DeferredItem<Item> SILENT_SIGIL_ITEM = ITEMS.registerItem("silent_sigil", properties -> new Item(properties) {
        @Override
        public void onCraftedBy(ItemStack stack, Player player) {
            super.onCraftedBy(stack, player);
            if (player instanceof ServerPlayer serverPlayer) TeamLifeBindNeoForge.manager().refreshCampaignLore(stack, serverPlayer);
        }
    });

    public static final net.neoforged.neoforge.registries.DeferredItem<Item> SCOUT_ROCKET_ITEM = ITEMS.registerItem("scout_rocket", properties -> new Item(properties) {
        @Override
        public void onCraftedBy(ItemStack stack, Player player) {
            super.onCraftedBy(stack, player);
            if (player instanceof ServerPlayer serverPlayer) TeamLifeBindNeoForge.manager().refreshCampaignLore(stack, serverPlayer);
        }
    });

    private static final Map<TeamMagicItem.Kind, net.neoforged.neoforge.registries.DeferredItem<Item>> MAGIC_ITEMS = registerMagicItems();
    private static Map<TeamMagicItem.Kind, net.neoforged.neoforge.registries.DeferredItem<Item>> registerMagicItems() {
        Map<TeamMagicItem.Kind, net.neoforged.neoforge.registries.DeferredItem<Item>> items = new EnumMap<>(TeamMagicItem.Kind.class);
        for (TeamMagicItem.Kind kind : TeamMagicItem.Kind.values()) items.put(kind, ITEMS.registerItem(kind.id(),
                properties -> new TeamMagicItem(properties, kind)));
        return Map.copyOf(items);
    }
    public static Item magicItem(TeamMagicItem.Kind kind) { return MAGIC_ITEMS.get(kind).get(); }

    private static final Map<GunProfile, net.neoforged.neoforge.registries.DeferredItem<Item>> GUN_ITEMS = registerGunItems();
    private static Map<GunProfile, net.neoforged.neoforge.registries.DeferredItem<Item>> registerGunItems() {
        for (GunModule module : GunModule.values()) registerModule(module.id(), module);
        registerModule(GunModuleItem.TOOL, null);
        Map<GunProfile, net.neoforged.neoforge.registries.DeferredItem<Item>> items = new EnumMap<>(GunProfile.class);
        for (GunProfile profile : GunProfile.values()) items.put(profile, ITEMS.registerItem(profile.id(),
                properties -> new TeamGunItem(properties, profile)));
        return Map.copyOf(items);
    }
    private static void registerModule(String id, GunModule module) {
        ITEMS.registerItem(id, properties -> new GunModuleItem(properties, module));
    }
    public static Item gunItem(GunProfile profile) { return GUN_ITEMS.get(profile).get(); }

    private final TeamLifeBindNeoForgeManager manager = MANAGER;

    public TeamLifeBindNeoForge() {
        IEventBus modEventBus = Objects.requireNonNull(ModLoadingContext.get().getActiveContainer().getEventBus(), "modEventBus");
        modEventBus.addListener(TeamGunNetwork::register);
        modEventBus.addListener(TeamMagicNetwork::register);
        modEventBus.addListener(WorldVfxNetwork::register);
        ITEM_REGISTRY.register(modEventBus);
        CHUNK_GENERATORS.register(modEventBus);
        CHUNK_GENERATORS.register("round_seeded_noise", () -> RoundSeededNoiseChunkGenerator.CODEC);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
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
                    .then(Commands.literal("rules").executes(ctx -> {
                        manager.sendRules(ctx.getSource());
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
                                    .then(Commands.literal("dev").requires(TeamLifeBindNeoForge::hasAdminPermission).executes(ctx -> handleDevMenu(ctx.getSource()))
                        .then(Commands.literal("magic").executes(ctx -> { ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0; manager.openMagicDevCategories(player); return 1; })
                            .then(Commands.argument("page", IntegerArgumentType.integer(1, 7)).executes(ctx -> { ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0; manager.openMagicDevMenu(player, IntegerArgumentType.getInteger(ctx, "page")); return 1; }))))

                    .then(Commands.literal("magic").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0;
                        manager.magicList(player); return 1;
                    }).then(Commands.literal("list").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0;
                        manager.magicList(player); return 1;
                    })).then(Commands.literal("select").then(Commands.argument("slot", IntegerArgumentType.integer(1, 3)).executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0;
                        manager.magicInput(player, MagicInputPayload.SELECT, IntegerArgumentType.getInteger(ctx, "slot") - 1); return 1;
                    }))).then(Commands.literal("cast").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0;
                        manager.magicInput(player, MagicInputPayload.CAST, manager.magic().state().snapshot(player.getUUID(), manager.magicTick()).selectedSlot()); return 1;
                    })).then(Commands.literal("focus").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.magic().recoverFocus(player) ? 1 : 0;
                    })).then(Commands.literal("forget").requires(TeamLifeBindNeoForge::hasAdminPermission).executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.magic().forgetSelected(player) ? 1 : 0;
                    })).then(Commands.literal("teststop").requires(TeamLifeBindNeoForge::hasAdminPermission).executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.magic().stopDeveloperTest(player) ? 1 : 0;
                    })).then(Commands.literal("test").requires(TeamLifeBindNeoForge::hasAdminPermission).executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0; manager.openMagicDevCategories(player); return 1;
                    }).then(Commands.literal("stop").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.magic().stopDeveloperTest(player) ? 1 : 0;
                    })).then(Commands.argument("id", StringArgumentType.word()).suggests((ctx, builder) ->
                        net.minecraft.commands.SharedSuggestionProvider.suggest(com.teamlifebind.common.SpellCatalogue.all().stream().map(com.teamlifebind.common.SpellDefinition::id), builder))
                        .executes(ctx -> { ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.magic().testSpell(player, StringArgumentType.getString(ctx, "id"), manager.magic().state().snapshot(player.getUUID(), manager.magicTick()).selectedSlot()) ? 1 : 0; })
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 3)).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.magic().testSpell(player, StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "slot") - 1) ? 1 : 0;
                        })))).then(Commands.literal("give").requires(TeamLifeBindNeoForge::hasAdminPermission)
                        .then(Commands.argument("id", StringArgumentType.word()).suggests((ctx, builder) ->
                            net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(
                                com.teamlifebind.common.SpellCatalogue.all().stream().map(com.teamlifebind.common.SpellDefinition::id),
                                java.util.Arrays.stream(TeamMagicItem.Kind.values()).filter(kind -> kind != TeamMagicItem.Kind.SPELL_BOOK).map(TeamMagicItem.Kind::id)), builder))
                        .executes(ctx -> { ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.giveMagicDeveloperItem(player, StringArgumentType.getString(ctx, "id")) ? 1 : 0; }))))

                .then(Commands.literal("ready").executes(ctx -> handleReady(ctx.getSource())))
                .then(Commands.literal("unready").executes(ctx -> handleUnready(ctx.getSource())))
                .then(Commands.literal("start").requires(TeamLifeBindNeoForge::hasAdminPermission).executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(manager.start(ctx.getSource().getServer())), true);
                    return 1;
                }))
                .then(Commands.literal("campaign").requires(TeamLifeBindNeoForge::hasAdminPermission).executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        if (player == null) { ctx.getSource().sendFailure(Component.literal(manager.text("command.player_only"))); return 0; }
                        manager.openCampaignMenu(player); return 1;
                    }))
                    .then(Commands.literal("rg").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.rogueCommand(player, 0) ? 1 : 0;
                    }).then(Commands.argument("choice", IntegerArgumentType.integer(1, 3)).executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.rogueCommand(player, IntegerArgumentType.getInteger(ctx, "choice")) ? 1 : 0;
                    })))
                    .then(Commands.literal("pve").requires(TeamLifeBindNeoForge::hasAdminPermission)
                    .then(Commands.argument("value", StringArgumentType.word()).suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("easy", "normal", "hard"), builder)).executes(ctx -> {
                        String result = manager.configureCampaign(ctx.getSource(), "pve", StringArgumentType.getString(ctx, "value"));
                        ctx.getSource().sendSuccess(() -> Component.literal(result), true); return 1;
                    })))
                    .then(Commands.literal("mode").requires(TeamLifeBindNeoForge::hasAdminPermission)
                    .then(Commands.argument("value", StringArgumentType.word()).suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("sur", "pve", "rg", "pvp", "DRAGON_HUNT", "LIMITED_LIVES", "RANDOM_GRIMOIRE"), builder)).executes(ctx -> {
                        String result = manager.configureCampaign(ctx.getSource(), "mode", StringArgumentType.getString(ctx, "value"));
                        ctx.getSource().sendSuccess(() -> Component.literal(result), true); return 1;
                    })))
                .then(Commands.literal("dragon").requires(TeamLifeBindNeoForge::hasAdminPermission)
                    .then(Commands.argument("value", StringArgumentType.word()).executes(ctx -> {
                        String result = manager.configureCampaign(ctx.getSource(), "dragon", StringArgumentType.getString(ctx, "value"));
                        ctx.getSource().sendSuccess(() -> Component.literal(result), true); return 1;
                    })))
                .then(Commands.literal("objectives").requires(TeamLifeBindNeoForge::hasAdminPermission)
                    .then(Commands.argument("value", StringArgumentType.word()).executes(ctx -> {
                        String result = manager.configureCampaign(ctx.getSource(), "objectives", StringArgumentType.getString(ctx, "value"));
                        ctx.getSource().sendSuccess(() -> Component.literal(result), true); return 1;
                    })))
                .then(Commands.literal("border").requires(TeamLifeBindNeoForge::hasAdminPermission)
                    .then(Commands.argument("value", StringArgumentType.word()).executes(ctx -> {
                        String result = manager.configureCampaign(ctx.getSource(), "border", StringArgumentType.getString(ctx, "value"));
                        ctx.getSource().sendSuccess(() -> Component.literal(result), true); return 1;
                    })))
                .then(Commands.literal("stop").requires(TeamLifeBindNeoForge::hasAdminPermission).executes(ctx -> {
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
                    Commands.literal("teams").requires(TeamLifeBindNeoForge::hasAdminPermission)
                        .then(Commands.argument("count", IntegerArgumentType.integer(2, 32)).executes(ctx -> {
                            int count = IntegerArgumentType.getInteger(ctx, "count");
                            manager.setTeamCount(count);
                            ctx.getSource().sendSuccess(() -> Component.literal(manager.text("command.team_count.updated", count)), true);
                            return 1;
                        }))
                )
                .then(
                    Commands.literal("health").requires(TeamLifeBindNeoForge::hasAdminPermission)
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
                    Commands.literal("healthsync").requires(TeamLifeBindNeoForge::hasAdminPermission)
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
                    Commands.literal("norespawn").requires(TeamLifeBindNeoForge::hasAdminPermission)
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
            "command.help.rules",
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

    @SubscribeEvent
    public void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) { manager.clearCampaignState(); }

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
    @SuppressWarnings("resource")
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
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        InteractionResult utilityResult = manager.useUtilityItem(player, event.getHand());
        if (utilityResult != InteractionResult.PASS) {
            event.setCancellationResult(utilityResult);
            event.setCanceled(true);
            return;
        }
        if (manager.isTeamModeVoteItem(event.getItemStack())) {
            manager.toggleTeamModeVote(player);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        if (!manager.isLobbyMenuItem(event.getItemStack())) {
            return;
        }
        manager.openLobbyMenu(player);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        manager.onAttackEntity(event);
    }

    @SubscribeEvent
    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        manager.onEntityPlace(event);
    }

    @SubscribeEvent
    public void onBreak(BreakBlockEvent event) {
        manager.onBreak(event);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        manager.onLivingDeath(event);
    }

    @SubscribeEvent
    public void onLivingUseTotem(LivingUseTotemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTotem().is(Items.TOTEM_OF_UNDYING)
                && manager.shouldBlockVanillaTotem(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        manager.onLivingDrops(event);
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        manager.onItemToss(event);
    }

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        manager.onLivingIncomingDamage(event);
    }

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        manager.onLivingDamagePre(event);
    }

    @SubscribeEvent
    public void onLivingDamagePost(LivingDamageEvent.Post event) {
        manager.onLivingDamagePost(event);
    }

    @SubscribeEvent
    public void onLivingItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        manager.onLivingItemUseFinish(event);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        manager.onRespawn(event);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        manager.onServerTick(event);
    }

    public static boolean isLockedSupply(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return !level.isClientSide() && MANAGER.lockedSupply(level, pos);
    }

    public static void recordCampaignDamage(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) { MANAGER.recordCampaignDamage(player, source, amount); }
    public static double pveProjectileDamageScale(net.minecraft.world.entity.Entity shooter) { return MANAGER.pveProjectileDamageScale(shooter); }
    public static void recordCampaignAttack(ServerPlayer player, net.minecraft.world.entity.Entity target) { MANAGER.recordCampaignAttack(player, target); }
    public static void recordCampaignDragonDamage(net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon, net.minecraft.world.damagesource.DamageSource source, float before, float actual) { MANAGER.recordCampaignDragonDamage(dragon, source, before, actual); }
    public static boolean reloadHeldGun(ServerPlayer player) { return MANAGER.reloadHeldGun(player); }
    public static boolean acceptGunInput(ServerPlayer player, GunInputPayload payload) { return MANAGER.acceptGunInput(player, payload); }
    public static boolean hasHeldGun(ServerPlayer player) { return TeamGunController.profile(player.getMainHandItem()) != null; }
    public static void interruptUtilityDamage(ServerPlayer player) { MANAGER.interruptUtilityDamage(player); }
    public static void interruptUtility(ServerPlayer player) { MANAGER.interruptUtility(player); }
    public static void interruptUtilitySlot(ServerPlayer player) { MANAGER.interruptUtilitySlot(player); }
    public static void interruptUtilityHandSwap(ServerPlayer player) { MANAGER.interruptUtilityHandSwap(player); }

    public static TeamMagicController magic() { return MANAGER.magic(); }

    static TeamLifeBindNeoForgeManager manager() {
        return MANAGER;
    }
}
