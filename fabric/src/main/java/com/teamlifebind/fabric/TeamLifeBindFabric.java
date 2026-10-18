package com.teamlifebind.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teamlifebind.common.HealthPreset;
import com.teamlifebind.common.GunProfile;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TeamLifeBindFabric implements ModInitializer {

    public static final String MOD_ID = "teamlifebind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Identifier TEAM_BED_ID = id("team_bed");
    private static final Identifier TRACKING_WHEEL_ID = id("tracking_wheel");
    private static final Identifier DEATH_EXEMPTION_TOTEM_ID = id("death_exemption_totem");
    private static final Identifier LIFE_CURSE_POTION_ID = id("life_curse_potion");
    private static final ResourceKey<Item> TEAM_BED_KEY = ResourceKey.create(Registries.ITEM, TEAM_BED_ID);
    private static final ResourceKey<Item> TRACKING_WHEEL_KEY = ResourceKey.create(Registries.ITEM, TRACKING_WHEEL_ID);
    private static final ResourceKey<Item> DEATH_EXEMPTION_TOTEM_KEY = ResourceKey.create(Registries.ITEM, DEATH_EXEMPTION_TOTEM_ID);
    private static final ResourceKey<Item> LIFE_CURSE_POTION_KEY = ResourceKey.create(Registries.ITEM, LIFE_CURSE_POTION_ID);
    private static final TeamLifeBindFabricManager MANAGER = new TeamLifeBindFabricManager();
    public static final Item TEAM_BED_ITEM = new BlockItem(Blocks.BED.white(), new Item.Properties().setId(TEAM_BED_KEY).stacksTo(2)) {
        @Override
        public void onCraftedBy(ItemStack stack, Player player) {
            super.onCraftedBy(stack, player);
            TeamLifeBindFabric.manager().bindCraftedTeamBed(stack, player.getUUID());
        }
    };
    public static final Item TRACKING_WHEEL_ITEM = new Item(new Item.Properties().setId(TRACKING_WHEEL_KEY));
    public static final Item DEATH_EXEMPTION_TOTEM_ITEM = new Item(new Item.Properties().setId(DEATH_EXEMPTION_TOTEM_KEY)) {
        @Override
        public void onCraftedBy(ItemStack stack, Player player) {
            super.onCraftedBy(stack, player);
            if (player instanceof ServerPlayer serverPlayer) {
                TeamLifeBindFabric.manager().refreshTotemLore(stack, serverPlayer);
            }
        }
    };
    public static final Item RESONANCE_WHISTLE_ITEM = new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("resonance_whistle"))).stacksTo(1)) {
        @Override
        public void onCraftedBy(ItemStack stack, Player player) {
            super.onCraftedBy(stack, player);
            if (player instanceof ServerPlayer serverPlayer) TeamLifeBindFabric.manager().refreshUtilityLore(stack, serverPlayer);
        }
    };
    public static final Item EMERGENCY_BANDAGE_ITEM = new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("emergency_bandage")))) {
        @Override
        public void onCraftedBy(ItemStack stack, Player player) {
            super.onCraftedBy(stack, player);
            if (player instanceof ServerPlayer serverPlayer) TeamLifeBindFabric.manager().refreshUtilityLore(stack, serverPlayer);
        }
    };
    public static final Item LIFE_CURSE_POTION_ITEM = new Item(
        new Item.Properties()
            .setId(LIFE_CURSE_POTION_KEY)
            .stacksTo(1)
            .usingConvertsTo(Items.GLASS_BOTTLE)
            .component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
            .component(DataComponents.POTION_CONTENTS, new PotionContents(Optional.of(Potions.WATER), Optional.of(0x541212), List.of(), Optional.empty()))
    ) {
        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
            ItemStack result = super.finishUsingItem(stack, level, livingEntity);
            if (!level.isClientSide() && livingEntity instanceof ServerPlayer player) {
                TeamLifeBindFabric.manager().handleLifeCursePotionConsumed(player);
            }
            return result;
        }
    };

    public static final Item SILENT_SIGIL_ITEM = new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("silent_sigil")))) {
        @Override
        public void onCraftedBy(ItemStack stack, Player player) {
            super.onCraftedBy(stack, player);
            if (player instanceof ServerPlayer serverPlayer) TeamLifeBindFabric.manager().refreshCampaignLore(stack, serverPlayer);
        }
    };

    public static final Item SCOUT_ROCKET_ITEM = new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("scout_rocket")))) {
        @Override
        public void onCraftedBy(ItemStack stack, Player player) {
            super.onCraftedBy(stack, player);
            if (player instanceof ServerPlayer serverPlayer) TeamLifeBindFabric.manager().refreshCampaignLore(stack, serverPlayer);
        }
    };

    private static final Map<TeamMagicItem.Kind, Item> MAGIC_ITEMS = registerMagicItems();
    private static Map<TeamMagicItem.Kind, Item> registerMagicItems() {
        Map<TeamMagicItem.Kind, Item> items = new EnumMap<>(TeamMagicItem.Kind.class);
        for (TeamMagicItem.Kind kind : TeamMagicItem.Kind.values()) items.put(kind,
                new TeamMagicItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(kind.id()))), kind));
        return Map.copyOf(items);
    }
    public static Item magicItem(TeamMagicItem.Kind kind) { return MAGIC_ITEMS.get(kind); }

    private static final Map<GunProfile, Item> GUN_ITEMS = registerGunItems();
    private static Map<GunProfile, Item> registerGunItems() {
        Map<GunProfile, Item> items = new EnumMap<>(GunProfile.class);
        for (GunProfile profile : GunProfile.values()) items.put(profile,
                new TeamGunItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(profile.id()))), profile));
        return Map.copyOf(items);
    }
    public static Item gunItem(GunProfile profile) { return GUN_ITEMS.get(profile); }

    private final TeamLifeBindFabricManager manager = MANAGER;

    @Override
    public void onInitialize() {
        TeamGunNetwork.register();
        TeamMagicNetwork.register();
        Registry.register(BuiltInRegistries.ITEM, TEAM_BED_ID, TEAM_BED_ITEM);
        Registry.register(BuiltInRegistries.ITEM, TRACKING_WHEEL_ID, TRACKING_WHEEL_ITEM);
        Registry.register(BuiltInRegistries.ITEM, DEATH_EXEMPTION_TOTEM_ID, DEATH_EXEMPTION_TOTEM_ITEM);
        Registry.register(BuiltInRegistries.ITEM, LIFE_CURSE_POTION_ID, LIFE_CURSE_POTION_ITEM);
        Registry.register(BuiltInRegistries.ITEM, id("resonance_whistle"), RESONANCE_WHISTLE_ITEM);
        Registry.register(BuiltInRegistries.ITEM, id("emergency_bandage"), EMERGENCY_BANDAGE_ITEM);
        Registry.register(BuiltInRegistries.ITEM, id("scout_rocket"), SCOUT_ROCKET_ITEM);
        Registry.register(BuiltInRegistries.ITEM, id("silent_sigil"), SILENT_SIGIL_ITEM);
        MAGIC_ITEMS.forEach((kind, item) -> Registry.register(BuiltInRegistries.ITEM, id(kind.id()), item));
        GUN_ITEMS.forEach((profile, item) -> Registry.register(BuiltInRegistries.ITEM, id(profile.id()), item));
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
                    .then(Commands.literal("rules").executes(ctx -> {
                        manager.sendRules(ctx.getSource());
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
                    .then(Commands.literal("dev").requires(TeamLifeBindFabric::hasAdminPermission).executes(ctx -> handleDevMenu(ctx.getSource()))
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
                    })).then(Commands.literal("forget").requires(TeamLifeBindFabric::hasAdminPermission).executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.magic().forgetSelected(player) ? 1 : 0;
                    })).then(Commands.literal("test").requires(TeamLifeBindFabric::hasAdminPermission).executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0; manager.openMagicDevCategories(player); return 1;
                    }).then(Commands.literal("stop").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.magic().stopDeveloperTest(player) ? 1 : 0;
                    })).then(Commands.argument("id", StringArgumentType.word()).suggests((ctx, builder) ->
                        net.minecraft.commands.SharedSuggestionProvider.suggest(com.teamlifebind.common.SpellCatalogue.all().stream().map(com.teamlifebind.common.SpellDefinition::id), builder))
                        .executes(ctx -> { ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.magic().testSpell(player, StringArgumentType.getString(ctx, "id"), manager.magic().state().snapshot(player.getUUID(), manager.magicTick()).selectedSlot()) ? 1 : 0; })
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 3)).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.magic().testSpell(player, StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "slot") - 1) ? 1 : 0;
                        })))).then(Commands.literal("give").requires(TeamLifeBindFabric::hasAdminPermission)
                        .then(Commands.argument("id", StringArgumentType.word()).suggests((ctx, builder) ->
                            net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(
                                com.teamlifebind.common.SpellCatalogue.all().stream().map(com.teamlifebind.common.SpellDefinition::id),
                                java.util.Arrays.stream(TeamMagicItem.Kind.values()).filter(kind -> kind != TeamMagicItem.Kind.SPELL_BOOK).map(TeamMagicItem.Kind::id)), builder))
                        .executes(ctx -> { ServerPlayer player = ctx.getSource().getPlayer(); return player != null && manager.giveMagicDeveloperItem(player, StringArgumentType.getString(ctx, "id")) ? 1 : 0; }))))

                    .then(Commands.literal("ready").executes(ctx -> handleReady(ctx.getSource())))
                    .then(Commands.literal("unready").executes(ctx -> handleUnready(ctx.getSource())))
                    .then(Commands.literal("start").requires(TeamLifeBindFabric::hasAdminPermission).executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(manager.start(ctx.getSource().getServer())), true);
                        return 1;
                    }))
                    .then(Commands.literal("campaign").requires(TeamLifeBindFabric::hasAdminPermission).executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        if (player == null) { ctx.getSource().sendFailure(Component.literal(manager.text("command.player_only"))); return 0; }
                        manager.openCampaignMenu(player); return 1;
                    }))
                    .then(Commands.literal("mode").requires(TeamLifeBindFabric::hasAdminPermission)
                    .then(Commands.argument("value", StringArgumentType.word()).executes(ctx -> {
                        String result = manager.configureCampaign(ctx.getSource(), "mode", StringArgumentType.getString(ctx, "value"));
                        ctx.getSource().sendSuccess(() -> Component.literal(result), true); return 1;
                    })))
                .then(Commands.literal("dragon").requires(TeamLifeBindFabric::hasAdminPermission)
                    .then(Commands.argument("value", StringArgumentType.word()).executes(ctx -> {
                        String result = manager.configureCampaign(ctx.getSource(), "dragon", StringArgumentType.getString(ctx, "value"));
                        ctx.getSource().sendSuccess(() -> Component.literal(result), true); return 1;
                    })))
                .then(Commands.literal("objectives").requires(TeamLifeBindFabric::hasAdminPermission)
                    .then(Commands.argument("value", StringArgumentType.word()).executes(ctx -> {
                        String result = manager.configureCampaign(ctx.getSource(), "objectives", StringArgumentType.getString(ctx, "value"));
                        ctx.getSource().sendSuccess(() -> Component.literal(result), true); return 1;
                    })))
                .then(Commands.literal("border").requires(TeamLifeBindFabric::hasAdminPermission)
                    .then(Commands.argument("value", StringArgumentType.word()).executes(ctx -> {
                        String result = manager.configureCampaign(ctx.getSource(), "border", StringArgumentType.getString(ctx, "value"));
                        ctx.getSource().sendSuccess(() -> Component.literal(result), true); return 1;
                    })))
                .then(Commands.literal("stop").requires(TeamLifeBindFabric::hasAdminPermission).executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(manager.stop(ctx.getSource().getServer())), true);
                        return 1;
                    }))
                    .then(Commands.literal("status").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        String status = player == null
                            ? manager.status(ctx.getSource().getServer())
                            : manager.status(player, ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(() -> Component.literal(status), false);
                        return 1;
                    }))
                    .then(
                        Commands.literal("teams").requires(TeamLifeBindFabric::hasAdminPermission)
                            .then(Commands.argument("count", IntegerArgumentType.integer(2, 32)).executes(ctx -> {
                                int count = IntegerArgumentType.getInteger(ctx, "count");
                                manager.setTeamCount(count);
                                ctx.getSource().sendSuccess(() -> Component.literal(manager.text("command.team_count.updated", count)), true);
                                return 1;
                            }))
                    )
                    .then(
                        Commands.literal("health").requires(TeamLifeBindFabric::hasAdminPermission)
                            .then(Commands.argument("preset", StringArgumentType.word()).executes(ctx -> {
                                HealthPreset preset = HealthPreset.fromString(StringArgumentType.getString(ctx, "preset"));
                                manager.setHealthPreset(preset);
                                ServerPlayer player = ctx.getSource().getPlayer();
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
                        Commands.literal("healthsync").requires(TeamLifeBindFabric::hasAdminPermission)
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayer();
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
                        Commands.literal("norespawn").requires(TeamLifeBindFabric::hasAdminPermission)
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayer();
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
            )
        );

        ServerLifecycleEvents.SERVER_STARTED.register(manager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> manager.clearCampaignState());

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
            if (TeamGunController.profile(serverPlayer.getItemInHand(hand)) != null) return InteractionResult.PASS;
            InteractionResult utilityResult = manager.useUtilityItem(serverPlayer, hand);
            if (utilityResult != InteractionResult.PASS) return utilityResult;
            ItemStack heldStack = serverPlayer.getItemInHand(hand);
            if (heldStack.is(net.minecraft.world.item.Items.MILK_BUCKET)) {
                manager.trackMilkBucketUse(serverPlayer, hand);
            }
            if (manager.isTeamModeVoteItem(heldStack)) {
                manager.toggleTeamModeVote(serverPlayer);
                return InteractionResult.SUCCESS;
            }
            if (!manager.isLobbyMenuItem(heldStack)) {
                return InteractionResult.PASS;
            }
            manager.openLobbyMenu(serverPlayer);
            return InteractionResult.SUCCESS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, blockState, blockEntity) -> !manager.lockedSupply(world, pos));
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
                return true;
            }
            if (!manager.shouldCancelFriendlyFire(attacker, target)) {
                return true;
            }
            manager.notifyFriendlyFireBlocked(attacker);
            return false;
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity.level().isClientSide()) {
                return true;
            }
            if (!(entity instanceof ServerPlayer target)) {
                return true;
            }
            return !manager.tryUseTeamTotem(target, damageAmount);
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, damageSource, baseDamageTaken, damageTaken, blocked) -> {
            if (entity.level().isClientSide() || !(entity instanceof ServerPlayer target) || blocked || damageTaken <= 0.0F) {
                return;
            }
            if (damageSource.getEntity() instanceof ServerPlayer attacker) {
                manager.handleLifeCurseAttack(attacker, target, damageTaken);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> manager.onRespawn(newPlayer));
        ServerEntityEvents.ENTITY_LOAD.register(manager::onEntityLoad);
        ServerTickEvents.END_SERVER_TICK.register(manager::onServerTick);

        LOGGER.info("TeamLifeBind Fabric loaded.");
    }

    public static boolean isLockedSupply(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return !level.isClientSide() && MANAGER.lockedSupply(level, pos);
    }

    public static void recordCampaignDamage(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) { MANAGER.recordCampaignDamage(player, source, amount); }
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

    static TeamLifeBindFabricManager manager() {
        return MANAGER;
    }

    public static boolean tryUseTeamTotemAtDeath(ServerPlayer player) {
        return MANAGER.tryUseTeamTotem(player, Float.MAX_VALUE);
    }

    public static boolean shouldBlockVanillaTotem(ServerPlayer player) {
        return MANAGER.shouldBlockVanillaTotem(player);
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

    private int handleDevMenu(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
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
        ServerPlayer player = source.getPlayer();
        if (player == null) {
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
        ServerPlayer player = source.getPlayer();
        if (player == null) {
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
        ServerPlayer player = source.getPlayer();
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

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
