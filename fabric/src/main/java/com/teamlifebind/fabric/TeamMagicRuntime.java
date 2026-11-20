package com.teamlifebind.fabric;

import com.teamlifebind.common.MagicMechanic;
import com.teamlifebind.common.MagicSchool;
import com.teamlifebind.common.SpellDefinition;
import com.teamlifebind.common.SpellBehaviour;
import com.teamlifebind.common.SpellGeometry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded, non-destructive world operators. Every hit uses the ordinary attributed damage path. */
final class TeamMagicRuntime {
    private static final int MAX_EFFECTS = 128, MAX_TARGETS = 64, MAX_SCENES_PER_TICK = 96;
    private final TeamMagicController controller;
    private final List<Effect> effects = new ArrayList<>();
    private final Map<UUID, Status> statuses = new HashMap<>();
    private final List<SpellRun> runs = new ArrayList<>();
    private final List<QueuedAction> queued = new ArrayList<>();
    private final Map<UUID, PreparingScene> preparing = new HashMap<>();
    private int sceneBudget = MAX_SCENES_PER_TICK;
    private long nextVisualId;
    private long tick;

    TeamMagicRuntime(TeamMagicController controller) { this.controller = controller; }
    void clear() {
        Set<UUID> casters = new HashSet<>(preparing.keySet());
        for (SpellRun run : runs) casters.add(run.caster);
        for (UUID caster : casters) cancel(caster);
        effects.clear(); statuses.clear(); runs.clear(); queued.clear(); preparing.clear();
    }
    void cancel(UUID player) {
        PreparingScene cast = preparing.remove(player);
        ServerLevel level = cast == null ? null : cast.level;
        for (SpellRun run : runs) if (run.caster.equals(player)) { level = run.level; run.cancelled = true; }
        if (level != null) control(level, player, 0, MagicScenePayload.CLEAR_CASTER);
        effects.removeIf(effect -> effect.caster.equals(player));
        runs.removeIf(run -> run.caster.equals(player)); queued.removeIf(action -> action.run.caster.equals(player));
    }
    boolean cancelChannel(UUID player) {
        Set<SpellRun> interrupted = new HashSet<>();
        for (Effect effect : effects) if (effect.caster.equals(player) && effect.mechanic == MagicMechanic.CHANNEL) interrupted.add(effect.run);
        boolean removed = effects.removeIf(effect -> interrupted.contains(effect.run));
        for (SpellRun run : interrupted) { run.cancelled = true; queued.removeIf(action -> action.run == run); removeRunScenes(run); }
        return removed;
    }
    int effectCount() { return effects.size() + queued.size(); }
    boolean canLaunch(SpellDefinition spell) { return effects.size() + spell.mechanics().size() <= MAX_EFFECTS && runs.size() < MAX_EFFECTS && queued.size() < 256; }

    void previewCast(ServerPlayer caster, SpellDefinition spell, int age) {
        if (spell == null || !controller.allowed(caster)) return;
        PreparingScene cast = preparing.get(caster.getUUID());
        if (cast == null || cast.level != caster.level() || !cast.spell.equals(spell.id())) {
            cancelPreparing(caster.getUUID());
            if (preparing.size() >= MAX_EFFECTS) return;
            cast = new PreparingScene(nextSceneBlock(), caster.level(), spell.id());
            preparing.put(caster.getUUID(), cast);
        }
        Vec3 center = caster.position().add(0, 0.12, 0);
        scene(cast.level, caster.getUUID(), cast.id, spell, SpellGeometry.Phase.CAST, spell.mechanics().getFirst(),
                center, center.add(caster.getLookAngle()), age, Math.min(600, Math.max(age + 8, spell.castTicks())), Math.min(2.8, spell.radius()));
    }

    void cancelPreparing(UUID caster) {
        PreparingScene cast = preparing.remove(caster);
        if (cast != null) control(cast.level, caster, cast.id, MagicScenePayload.REMOVE);
    }

    private long nextSceneBlock() { return ++nextVisualId * 256; }

    void launch(ServerPlayer caster, SpellDefinition spell) {
        Vec3 direction = caster.getLookAngle().normalize();
        Vec3 origin = caster.getEyePosition();
        Vec3 target = trace(caster.level(), caster, origin, origin.add(direction.scale(spell.range())));
        LivingEntity aimed = aimedTarget(caster, origin, target);
        if (aimed != null) target = aimed.position();
        SpellBehaviour.Plan plan = SpellBehaviour.byId(spell.id());
        if (plan == null || !canLaunch(spell)) return;
        SpellRun run = new SpellRun(caster, spell, plan, target, direction, tick, nextSceneBlock());
        runs.add(run);
        cancelPreparing(caster.getUUID());
        transientScene(run, SpellGeometry.Phase.CAST, spell.mechanics().getFirst(), caster.position().add(0, 0.12, 0), direction, Math.min(2.8, spell.radius()));
        caster.level().playSound(null, caster.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS,
                0.7F + spell.tier() * 0.08F, 1.45F - spell.tier() * 0.07F);
        if (spell.school() == MagicSchool.LIGHT) {
            caster.removeEffect(MobEffects.POISON); caster.removeEffect(MobEffects.WEAKNESS);
            caster.removeEffect(MobEffects.BLINDNESS); caster.removeEffect(MobEffects.WITHER);
        }
        if (spell.school() == MagicSchool.SHADOW) caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 + spell.tier() * 8, 0));
        if (spell.school() == MagicSchool.EARTH) caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40 + spell.tier() * 10, 0));
        if (spell.school() == MagicSchool.BLOOD && caster.getHealth() > 2) caster.hurtServer(caster.level(), caster.damageSources().magic(), Math.min(2F, caster.getHealth() - 1F));
        int delay = 0;
        for (int i = 0; i < spell.mechanics().size() && effects.size() < MAX_EFFECTS; i++) {
            MagicMechanic mechanic = spell.mechanics().get(i);
            Effect effect = new Effect(caster, spell, mechanic, origin, direction, target, delay, i, run);
            effects.add(effect);
            delay += effect.stage == null ? switch (mechanic) { case METEOR -> 26; case MARK -> 36; case ECHO -> 32; case CHANNEL -> 40; case BOLT -> Math.min(60, (int) Math.ceil(spell.range() / (1.5 + spell.tier() * 0.06)) + 2); default -> 20; } : effect.stage.durationTicks();
        }
    }

    void tick(MinecraftServer server, long now) {
        tick = now; sceneBudget = MAX_SCENES_PER_TICK;
        statuses.entrySet().removeIf(entry -> entry.getValue().lastSeen + 240 < now);
        for (SpellRun run : List.copyOf(runs)) {
            ServerPlayer caster = server.getPlayerList().getPlayer(run.caster);
            if (caster == null || caster.level() != run.level || !controller.allowed(caster)) { cancel(run.caster); continue; }
            if (!run.finished && !run.cancelled) enqueue(run, run.tracker.pulse(run.age(now)), run.anchor);
        }
        for (Effect effect : List.copyOf(effects)) {
            if (!effects.contains(effect)) continue;
            ServerPlayer caster = server.getPlayerList().getPlayer(effect.caster);
            if (caster == null || caster.level() != effect.level || !controller.allowed(caster)) { effects.remove(effect); continue; }
            if (effect.delay-- > 0) continue;
            if (effect.age++ >= lifetime(effect)) { closeStage(effect); effects.remove(effect); continue; }
            effect.beamSlot = 0;
            if (effect.age == 1 && effect.stage != null) stageActions(effect, effect.stage.opening());
            boolean oneShot = switch (effect.mechanic) { case BEAM, CONE, CHAIN, DASH, BLINK, DRAIN -> true; default -> false; };
            if ((!oneShot || effect.age == 1) && (effect.mechanic != MagicMechanic.WAVE || effect.age <= 25)) operate(caster, effect);
            if (!effects.contains(effect)) closeStage(effect);
            if (effects.contains(effect) && (effect.age == 1 || effect.age % 3 == 0)) travelScene(caster, effect);
        }
        for (SpellRun run : List.copyOf(runs)) {
            if (!run.finished && effects.stream().noneMatch(effect -> effect.run == run)) {
                run.finished = true;
                if (!run.cancelled) {
                    enqueue(run, run.tracker.finish(run.age(now)), run.lastPoint == null ? run.anchor : run.lastPoint);
                    transientScene(run, SpellGeometry.Phase.FINISH, run.spell.mechanics().getLast(),
                            run.lastPoint == null ? run.anchor : run.lastPoint, run.direction, Math.min(5, run.spell.radius()));
                }
            }
        }
        int budget = 128;
        for (QueuedAction action : List.copyOf(queued)) {
            if (action.due > now) continue;
            if (budget-- <= 0) break;
            if (!queued.remove(action)) continue;
            ServerPlayer caster = server.getPlayerList().getPlayer(action.run.caster);
            if (caster != null && caster.level() == action.run.level && controller.allowed(caster)) execute(caster, action);
        }
        runs.removeIf(run -> run.finished && queued.stream().noneMatch(action -> action.run == run));
    }

    private int lifetime(Effect e) {
        if (e.stage != null) return e.stage.durationTicks();
        return switch (e.mechanic) {
            case BOLT -> Math.min(80, (int) Math.ceil(e.spell.range() / 1.5) + 2);
            case BEAM, CONE, CHAIN, DASH, BLINK, DRAIN -> 1; case NOVA -> 8;
            case METEOR -> 26; case MARK -> 36; case ECHO -> 32;
            case WAVE -> 25; case CHANNEL -> 40;
            case ORBIT, VORTEX, WALL, SHIELD, SUMMON, DOMAIN -> 60 + e.spell.tier() * 10;
            case MINE -> 200;
        };
    }

    private void operate(ServerPlayer caster, Effect e) {
        SpellDefinition s = e.spell;
        int age = e.age;
        double radius = Math.min(14, s.radius());
        switch (e.mechanic) {
            case BOLT -> {
                if (s.school() == MagicSchool.SPIRIT) {
                    LivingEntity seeking = nearest(caster, e.origin.add(e.direction.scale(4)), 5, Set.of());
                    if (seeking != null) e.direction = e.direction.scale(0.85).add(seeking.getBoundingBox().getCenter().subtract(e.origin).normalize().scale(0.15)).normalize();
                }
                Vec3 next = e.origin.add(e.direction.scale(1.5 + s.tier() * 0.06));
                Vec3 end = trace(e.level, caster, e.origin, next);
                LivingEntity target = firstHit(caster, e, e.origin, end, 0.28);
                if (target != null) { hit(caster, e, target, 1, 0); burst(e, target.getBoundingBox().getCenter()); effects.remove(e); }
                else if (end.distanceToSqr(next) > 0.001 || e.travel >= s.range()) { burst(e, end); effects.remove(e); }
                else { pathVisual(e, e.origin, end); e.travel += e.origin.distanceTo(end); e.origin = end; }
            }
            case BEAM -> {
                Vec3 end = trace(e.level, caster, caster.getEyePosition(), caster.getEyePosition().add(caster.getLookAngle().scale(s.range())));
                pathVisual(e, caster.getEyePosition(), end);
                for (LivingEntity target : targets(caster, caster.getEyePosition().add(end).scale(0.5), caster.getEyePosition().distanceTo(end) / 2 + 2)) {
                    Entity contact = pathContact(target, caster.getEyePosition(), end, 0.45);
                    if (contact != null) { e.struckParts.put(target.getUUID(), contact); hit(caster, e, target, 1, 0); }
                }
            }
            case CONE -> {
                for (LivingEntity target : targets(caster, caster.position(), Math.min(s.range(), 16))) {
                    Vec3 delta = target.getBoundingBox().getCenter().subtract(caster.getEyePosition());
                    if (delta.lengthSqr() > 0.001 && delta.normalize().dot(e.direction) > 0.68 && visible(caster, caster.getEyePosition(), target.getBoundingBox().getCenter())) hit(caster, e, target, 1, 0);
                }
            }
            case NOVA -> {
                double front = radius * age / 8;
                Vec3 base = s.superTier() && e.stageIndex > 0 ? e.target : e.casterStart;
                Vec3 center = base.add(0, 0.5, 0);
                for (LivingEntity target : targets(caster, center, front + 0.7)) if (!e.hits.containsKey(target.getUUID()) && visible(caster, center, target.getBoundingBox().getCenter()) && hit(caster, e, target, 1, 0) > 0) { Vec3 radial = target.position().subtract(base); impulse(target, (radial.lengthSqr() < 0.01 ? e.direction : radial.normalize()).scale(0.4).add(0, 0.12, 0)); }
            }
            case ORBIT -> {
                for (int i = 0; i < 3 + s.tier() / 2; i++) {
                    double angle = age * 0.18 + i * Math.PI * 2 / (3 + s.tier() / 2);
                    Vec3 orb = caster.position().add(Math.cos(angle) * radius, 1.2 + Math.sin(angle * 2) * 0.5, Math.sin(angle) * radius);
                    area(caster, e, orb, 1.25, 0.3, 15);
                }
            }
            case CHAIN -> {
                Vec3 previous = e.origin; Set<UUID> visited = new HashSet<>();
                for (int hop = 0; hop < 3 + s.tier() / 2; hop++) {
                    LivingEntity nearest = hop == 0 ? firstHit(caster, e, e.origin, e.target, 0.7) : nearest(caster, previous, radius + 3, visited);
                    if (nearest == null) break; visited.add(nearest.getUUID());
                    Vec3 end = nearest.getBoundingBox().getCenter(); beamScene(e, previous, end, MagicMechanic.CHAIN); hit(caster, e, nearest, Math.pow(0.85, hop), 0); previous = end;
                }
            }
            case WAVE -> {
                double waveRange = Math.min(s.range(), 25);
                Vec3 base = s.superTier() && e.stageIndex > 0 ? e.target.subtract(e.direction.scale(waveRange / 2)) : e.casterStart;
                Vec3 center = base.add(e.direction.scale(age * waveRange / 25));
                Vec3 next = trace(e.level, caster, base.add(0, 0.5, 0), center.add(0, 0.5, 0));
                if (next.distanceToSqr(center.add(0, 0.5, 0)) > 0.1) { effects.remove(e); break; }
                for (LivingEntity target : targets(caster, center, radius)) if (!e.hits.containsKey(target.getUUID()) && Math.abs(target.position().subtract(center).dot(e.direction)) < 1.0 && visible(caster, center.add(0, 0.5, 0), target.getBoundingBox().getCenter()) && hit(caster, e, target, 1, 0) > 0) impulse(target, e.direction.scale(0.35).add(0, 0.1, 0));
            }
            case VORTEX -> {
                for (LivingEntity target : targets(caster, e.target, radius)) {
                    if (!visible(caster, e.target, target.getBoundingBox().getCenter())) continue;
                    impulse(target, e.target.subtract(target.position()).normalize().scale(0.22).add(0, 0.025, 0));
                    hit(caster, e, target, 0.18, 12);
                }
            }
            case METEOR -> {
                if (age == 26) { area(caster, e, e.target, radius, 1.5, 0); burst(e, e.target); }
            }
            case MINE -> {
                if (age > 10 && !targets(caster, e.target, Math.max(1.5, radius * 0.45)).isEmpty()) { area(caster, e, e.target, radius, 1.25, 0); burst(e, e.target); effects.remove(e); }
            }
            case DASH -> {
                Vec3 start = caster.position(); Vec3 destination = safeDestination(caster, start, e.direction, Math.min(s.range(), 10), false);
                if (destination != null) { beamScene(e, start.add(0, 0.5, 0), destination.add(0, 0.5, 0), MagicMechanic.DASH); for (LivingEntity target : targets(caster, start.add(destination).scale(0.5), start.distanceTo(destination) / 2 + radius)) if (target.getBoundingBox().inflate(radius * 0.35).clip(start.add(0, 0.8, 0), destination.add(0, 0.8, 0)).isPresent()) hit(caster, e, target, 1, 0); caster.teleportTo(destination.x, destination.y, destination.z); }
            }
            case BLINK -> {
                Vec3 destination = safeDestination(caster, caster.position(), e.direction, Math.min(s.range(), 24), true);
                if (destination != null) { burst(e, caster.position().add(0, 1, 0)); area(caster, e, caster.position(), radius, 0.65, 1); caster.teleportTo(destination.x, destination.y, destination.z); burst(e, destination.add(0, 1, 0)); area(caster, e, destination, radius, 0.65, 1); }
            }
            case WALL -> {
                Vec3 right = new Vec3(-e.direction.z, 0, e.direction.x).normalize();
                for (LivingEntity target : targets(caster, e.target, radius + 1)) {
                    Vec3 offset = target.position().subtract(e.target);
                    if (Math.abs(offset.dot(e.direction)) < 1 && Math.abs(offset.dot(right)) < radius) {
                        impulse(target, e.direction.scale(offset.dot(e.direction) >= 0 ? 0.3 : -0.3));
                        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 2)); hit(caster, e, target, 0.15, 20);
                    }
                }
            }
            case SHIELD -> {
                if (age == 1) { caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, lifetime(e), Math.min(3, s.tier() / 2))); caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, lifetime(e), 0)); e.lastWard = caster.getHealth() + caster.getAbsorptionAmount(); }
                float ward = caster.getHealth() + caster.getAbsorptionAmount();
                var source = caster.getLastDamageSource();
                if (ward + 0.01F < e.lastWard && source != null && source.getEntity() instanceof LivingEntity attacker && legal(caster, attacker) && attacker.position().distanceToSqr(caster.position()) <= radius * radius) area(caster, e, caster.position(), radius, 0.5, 10);
                e.lastWard = ward;
            }
            case SUMMON -> {
                for (int i = 0; i < 2 + s.tier() / 2; i++) {
                    LivingEntity prey = nearest(caster, e.target, Math.min(s.range(), 20), Set.of());
                    double angle = age * 0.12 + i * 2.1; Vec3 wisp = e.target.add(Math.cos(angle) * 1.5, 1 + Math.sin(age * 0.08 + i), Math.sin(angle) * 1.5);
                    if (prey != null && age % 12 == i % 12) { beamScene(e, wisp, prey.getBoundingBox().getCenter(), MagicMechanic.CHAIN); hit(caster, e, prey, 0.3, 10); }
                }
            }
            case MARK -> {
                if (age == 1) { LivingEntity marked = firstHit(caster, e, e.origin, e.target, 0.8); if (marked != null) e.marked = marked.getUUID(); }
                Entity marked = e.marked == null ? null : e.level.getEntity(e.marked);
                if (!(marked instanceof LivingEntity target) || !legal(caster, target) || target.position().distanceToSqr(caster.position()) > s.range() * s.range()) { effects.remove(e); break; }
                if (age == 36) { hit(caster, e, target, 1.5, 0); area(caster, e, target.position(), radius, 0.5, 0); burst(e, target.position()); }
            }
            case DRAIN -> {
                LivingEntity target = firstHit(caster, e, e.origin, e.target, 0.6);
                if (target != null) { pathVisual(e, e.origin, target.getBoundingBox().getCenter()); double actual = hit(caster, e, target, 0.85, 0); if (actual > 0) { caster.heal((float) Math.min(6, actual * 0.2)); if (target instanceof ServerPlayer player) controller.drainMana(caster, player, 8 + s.tier() * 3); } }
            }
            case CHANNEL -> {
                if (age == 1) e.channelStart = caster.position();
                if (caster.position().distanceToSqr(e.channelStart) > 9) { cancelChannel(caster.getUUID()); break; }
                if (age % 10 == 0 && !controller.spendChannelMana(caster, 2 + s.tier())) { cancelChannel(caster.getUUID()); break; }
                Vec3 end = trace(e.level, caster, caster.getEyePosition(), caster.getEyePosition().add(caster.getLookAngle().scale(s.range())));
                pathVisual(e, caster.getEyePosition(), end); LivingEntity target = firstHit(caster, e, caster.getEyePosition(), end, 0.7);
                if (target != null) hit(caster, e, target, 0.2, 8);
            }
            case ECHO -> {
                if (age == 1) { LivingEntity target = firstHit(caster, e, e.origin, e.target, 0.8); if (target == null) { effects.remove(e); break; } e.echo = target.position(); beamScene(e, e.origin, target.getBoundingBox().getCenter(), MagicMechanic.CHAIN); hit(caster, e, target, 0.65, 0); }
                if (e.echo != null && (age == 16 || age == 32)) { area(caster, e, e.echo, radius, 0.6, 10); burst(e, e.echo); }
            }
            case DOMAIN -> {
                area(caster, e, e.target, radius, 0.15, 15);
            }
        }
    }

    private double hit(ServerPlayer caster, Effect e, LivingEntity target, double multiplier, int interval) {
        if (!legal(caster, target) || e.hits.getOrDefault(target.getUUID(), Long.MIN_VALUE / 2) + interval > tick) return 0;
        e.hits.put(target.getUUID(), tick);
        if (statuses.size() >= 2048 && !statuses.containsKey(target.getUUID())) return 0;
        Status status = statuses.computeIfAbsent(target.getUUID(), ignored -> new Status()); status.lastSeen = tick;
        SpellBehaviour.HitContext context = new SpellBehaviour.HitContext(target.getUUID(), e.run.age(tick),
                Math.max(0, Math.min(1, target.getHealth() / target.getMaxHealth())), target.onGround(),
                status.wetUntil > tick || target.isInWater(), target.isOnFire(), caster.position().distanceTo(target.position()));
        double amount = e.spell.damage() * multiplier * (1 + Math.min(3, status.arcaneStacks) * 0.08);
        int duration = 30 + e.spell.tier() * 10;
        // Read interactions before damage. Mutations are applied only after an accepted real hit.
        if (e.spell.school() == MagicSchool.FIRE && target.isOnFire()) amount *= 1.2;
        if (e.spell.school() == MagicSchool.ICE && target.getTicksFrozen() > 80) amount *= 1.3;
        if (e.spell.school() == MagicSchool.EARTH && target.getDeltaMovement().y > 0) amount *= 1.15;
        if (e.spell.school() == MagicSchool.LIGHTNING && (status.wetUntil > tick || target.isInWater())) amount *= 1.35;
        if (e.spell.school() == MagicSchool.LIGHT && target.isInvertedHealAndHarm()) amount *= 1.35;
        if (e.spell.school() == MagicSchool.SHADOW && target.getLookAngle().dot(caster.position().subtract(target.position()).normalize()) < -0.3) amount *= 1.2;
        if (e.spell.school() == MagicSchool.GRAVITY && target.getDeltaMovement().y < -0.25) amount *= 1.2;
        if (e.spell.school() == MagicSchool.TIME && status.lastTimeHit + 60 > tick) amount += status.timeDamage * 0.25;
        double actual = 0;
        if (amount > 0 && legal(caster, target)) {
            double before = target.getHealth() + target.getAbsorptionAmount();
            controller.recordAttack(caster, target);
            Entity contact = e.struckParts.remove(target.getUUID());
            if (contact == null) contact = target instanceof EnderDragon dragon ? dragon.head : target;
            boolean accepted = contact.hurtServer(e.level, caster.damageSources().indirectMagic(caster, caster), (float) amount);
            if (accepted) actual = Math.max(0, before - target.getHealth() - target.getAbsorptionAmount());
        }
        if (actual <= 0) return 0;
        switch (e.spell.school()) {
            case FIRE -> target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), duration));
            case WATER -> { target.clearFire(); status.wetUntil = tick + duration * 2; target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 0)); }
            case ICE -> { if (target.getTicksFrozen() > 80) target.setTicksFrozen(0); else target.setTicksFrozen(Math.min(300, target.getTicksFrozen() + (status.wetUntil > tick ? 150 : 70))); target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, status.wetUntil > tick ? 2 : 1)); }
            case WIND -> { impulse(target, target.position().subtract(caster.position()).normalize().scale(0.35).add(0, 0.2, 0)); effects.removeIf(other -> {
                boolean removed = other != e && (other.mechanic == MagicMechanic.DOMAIN || other.mechanic == MagicMechanic.VORTEX)
                        && other.level == e.level && other.target.distanceToSqr(target.position()) < 9;
                if (removed) control(other.level, other.caster, other.run.visualBase + 1 + other.stageIndex, MagicScenePayload.REMOVE);
                return removed;
            }); }
            case EARTH -> { target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 2)); if (target.getDeltaMovement().y > 0) impulse(target, new Vec3(0, -0.5, 0)); }
            case LIGHTNING -> { target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, 3)); if ((status.wetUntil > tick || target.isInWater()) && e.arcDepth == 0) { e.arcDepth++; for (LivingEntity nearby : targets(caster, target.position(), 3)) if (nearby != target) { beamScene(e, target.getBoundingBox().getCenter(), nearby.getBoundingBox().getCenter(), MagicMechanic.CHAIN); hit(caster, e, nearby, multiplier * 0.4, Math.max(1, interval)); } e.arcDepth--; } }
            case LIGHT -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0));
            case SHADOW -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0));
            case ARCANE -> { target.setAbsorptionAmount(Math.max(0, target.getAbsorptionAmount() - (float) (2 + e.spell.tier()))); status.arcaneStacks = Math.min(3, status.arcaneStacks + 1); if (target instanceof ServerPlayer player) controller.interrupt(player); }
            case NATURE -> { target.addEffect(new MobEffectInstance(MobEffects.POISON, duration, 0)); target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 2)); }
            case GRAVITY -> {
                Vec3 gravityCenter = switch (e.mechanic) { case VORTEX, METEOR, MINE, WALL, SUMMON, DOMAIN -> e.target; default -> caster.position(); };
                if (e.mechanic != MagicMechanic.NOVA) impulse(target, gravityCenter.subtract(target.position()).normalize().scale(0.25).add(0, -0.15, 0));
            }
            case TIME -> { target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 1)); target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, duration, 0)); status.lastTimeHit = tick; status.timeDamage = amount; controller.accelerate(caster, 2); }
            case BLOOD -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, duration, 0));
            case SPIRIT -> { target.removeEffect(MobEffects.INVISIBILITY); target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0)); target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0)); }
        }
        if (actual > 0 && e.spell.school() == MagicSchool.NATURE && target.getHealth() < target.getMaxHealth() * 0.75) caster.heal((float) Math.min(1.5, actual * 0.08));
        if (actual > 0 && e.spell.school() == MagicSchool.BLOOD) caster.heal((float) Math.min(3, actual * 0.16));
        {
            e.run.lastPoint = target.position();
            enqueue(e.run, e.run.tracker.hit(context), target.position());
            transientScene(e.run, SpellGeometry.Phase.IMPACT, e.mechanic, target.getBoundingBox().getCenter(), e.direction, Math.min(3, e.spell.radius()));
        }
        return actual;
    }

    private void area(ServerPlayer caster, Effect e, Vec3 center, double radius, double multiplier, int interval) {
        for (LivingEntity target : targets(caster, center, radius)) if (visible(caster, center.add(0, 0.25, 0), target.getBoundingBox().getCenter())) hit(caster, e, target, multiplier, interval);
    }
    private List<LivingEntity> targets(ServerPlayer caster, Vec3 center, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : caster.level().getEntities(caster, new AABB(center, center).inflate(radius), target -> target instanceof LivingEntity && target.isAlive() && !target.isSpectator())) {
            if (entity instanceof LivingEntity living && legal(caster, living) && living.getBoundingBox().getCenter().distanceToSqr(center) <= radius * radius) result.add(living);
            if (result.size() >= MAX_TARGETS) break;
        }
        return result;
    }
    private boolean legal(ServerPlayer caster, LivingEntity target) { return target != caster && target.isAlive() && !target.isSpectator() && target.level() == caster.level() && controller.allowed(caster) && (!(target instanceof ServerPlayer player) || controller.pvpAllowed(caster, player)); }
    private LivingEntity nearest(ServerPlayer caster, Vec3 center, double radius, Set<UUID> skip) {
        LivingEntity nearest = null; double distance = radius * radius;
        for (LivingEntity candidate : targets(caster, center, radius)) { double next = candidate.position().distanceToSqr(center); if (!skip.contains(candidate.getUUID()) && next < distance && visible(caster, center, candidate.getBoundingBox().getCenter())) { distance = next; nearest = candidate; } }
        return nearest;
    }
    private LivingEntity firstHit(ServerPlayer caster, Effect effect, Vec3 start, Vec3 end, double width) {
        LivingEntity nearest = null; double distance = start.distanceToSqr(end);
        Entity nearestContact = null; int checked = 0;
        for (Entity candidate : caster.level().getEntities(caster, new AABB(start, end).inflate(width + 2), target -> target instanceof LivingEntity && target.isAlive())) {
            if (checked++ >= MAX_TARGETS) break;
            if (!(candidate instanceof LivingEntity target) || !legal(caster, target)) continue;
            Entity contact = pathContact(target, start, end, width);
            Vec3 point = contact == null ? null : contact.getBoundingBox().inflate(width).clip(start, end).orElse(null);
            if (point != null && start.distanceToSqr(point) < distance) { distance = start.distanceToSqr(point); nearest = target; nearestContact = contact; }
        }
        if (nearest != null) effect.struckParts.put(nearest.getUUID(), nearestContact);
        return nearest;
    }
    private LivingEntity aimedTarget(ServerPlayer caster, Vec3 start, Vec3 end) {
        LivingEntity selected = null; double distance = start.distanceToSqr(end); int checked = 0;
        for (Entity candidate : caster.level().getEntities(caster, new AABB(start, end).inflate(2), target -> target instanceof LivingEntity && target.isAlive())) {
            if (checked++ >= MAX_TARGETS) break;
            if (!(candidate instanceof LivingEntity target) || !legal(caster, target)) continue;
            Entity contact = pathContact(target, start, end, 0.35); Vec3 point = contact == null ? null : contact.getBoundingBox().inflate(0.35).clip(start, end).orElse(null);
            if (point != null && start.distanceToSqr(point) < distance) { selected = target; distance = start.distanceToSqr(point); }
        }
        return selected;
    }
    private static Entity pathContact(LivingEntity owner, Vec3 start, Vec3 end, double width) {
        if (!(owner instanceof EnderDragon dragon)) return owner.getBoundingBox().inflate(width).clip(start, end).isPresent() ? owner : null;
        EnderDragonPart selected = null; double closest = Double.POSITIVE_INFINITY;
        for (EnderDragonPart part : dragon.getSubEntities()) { Vec3 point = part.getBoundingBox().inflate(width).clip(start, end).orElse(null); if (point != null && start.distanceToSqr(point) < closest) { closest = start.distanceToSqr(point); selected = part; } }
        return selected;
    }
    private static Vec3 trace(ServerLevel level, ServerPlayer caster, Vec3 start, Vec3 end) { HitResult wall = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)); return wall.getType() == HitResult.Type.MISS ? end : wall.getLocation(); }
    private static boolean visible(ServerPlayer caster, Vec3 start, Vec3 end) { return trace(caster.level(), caster, start, end).distanceToSqr(end) < 0.05; }
    private static void impulse(LivingEntity target, Vec3 velocity) { target.setDeltaMovement(target.getDeltaMovement().add(velocity)); target.syncVelocity = true; }
    private Vec3 safeDestination(ServerPlayer caster, Vec3 start, Vec3 direction, double distance, boolean standing) {
        Vec3 end = trace(caster.level(), caster, caster.getEyePosition(), caster.getEyePosition().add(direction.scale(distance)));
        double clear = Math.max(0, Math.min(distance, end.distanceTo(caster.getEyePosition()) - 0.7));
        if (!standing) {
            Vec3 safe = null;
            for (double step = 0.25; step <= clear; step += 0.25) {
                Vec3 candidate = start.add(direction.scale(step)); AABB body = caster.getBoundingBox().move(candidate.subtract(start));
                if (candidate.y < caster.level().getMinY() + 1 || candidate.y + caster.getBbHeight() > caster.level().getMaxY() || !caster.level().hasChunkAt(net.minecraft.core.BlockPos.containing(candidate)) || !caster.level().getWorldBorder().isWithinBounds(body) || !caster.level().noCollision(caster, body)) break;
                safe = candidate;
            }
            return safe;
        }
        for (double step = clear; step > 0.5; step -= 0.5) {
            Vec3 candidate = start.add(direction.scale(step));
            AABB body = caster.getBoundingBox().move(candidate.subtract(start));
            if (candidate.y < caster.level().getMinY() + 1 || candidate.y + caster.getBbHeight() > caster.level().getMaxY() || !caster.level().hasChunkAt(net.minecraft.core.BlockPos.containing(candidate)) || !caster.level().getWorldBorder().isWithinBounds(body) || !caster.level().noCollision(caster, body)) continue;
            if (!standing || !caster.level().noCollision(caster, body.move(0, -0.6, 0))) return candidate;
        }
        return null;
    }

    private void enqueue(SpellRun run, List<SpellBehaviour.Activation> activations, Vec3 point) {
        if (run.cancelled) return;
        for (SpellBehaviour.Activation activation : activations) {
            if (queued.size() >= 256) break;
            queued.add(new QueuedAction(run, activation, point, tick + activation.delayTicks()));
        }
    }

    private void stageActions(Effect effect, List<SpellBehaviour.Action> actions) {
        List<SpellBehaviour.Activation> values = new ArrayList<>();
        for (SpellBehaviour.Action action : actions) {
            boolean buff = action == SpellBehaviour.Action.HEAL || action == SpellBehaviour.Action.ABSORB
                    || action == SpellBehaviour.Action.RESTORE_MANA || action == SpellBehaviour.Action.CLEANSE;
            values.add(new SpellBehaviour.Activation(action, buff ? SpellBehaviour.Target.CASTER : SpellBehaviour.Target.ANCHOR, null, 0.8, 0));
        }
        enqueue(effect.run, values, effect.target);
    }

    private void closeStage(Effect effect) {
        if (effect.closed) return;
        effect.closed = true;
        if (lifetime(effect) > 1) control(effect.level, effect.caster, effect.run.visualBase + 1 + effect.stageIndex, MagicScenePayload.REMOVE);
        if (effect.stage != null && !effect.run.cancelled) stageActions(effect, effect.stage.closing());
    }

    private void execute(ServerPlayer caster, QueuedAction queuedAction) {
        SpellRun run = queuedAction.run;
        if (run.cancelled) return;
        SpellBehaviour.Activation action = queuedAction.activation;
        Vec3 center = queuedAction.point;
        if (action.target() == SpellBehaviour.Target.CASTER) applyAction(caster, caster, run, action, center);
        else if (action.target() == SpellBehaviour.Target.ANCHOR) {
            for (LivingEntity enemy : targets(caster, center, Math.min(8, run.spell.radius()))) {
                if (visible(caster, center.add(0, 0.3, 0), enemy.getBoundingBox().getCenter())) applyAction(caster, enemy, run, action, center);
            }
        } else {
            Entity entity = action.targetId() == null ? null : run.level.getEntity(action.targetId());
            if (entity instanceof LivingEntity enemy && legal(caster, enemy)
                    && caster.position().distanceToSqr(enemy.position()) <= run.spell.range() * run.spell.range()
                    && visible(caster, caster.getEyePosition(), enemy.getBoundingBox().getCenter())) {
                if (action.action() == SpellBehaviour.Action.SPLASH_DAMAGE) {
                    for (LivingEntity neighbor : targets(caster, center, Math.min(8, run.spell.radius())))
                        if (visible(caster, center.add(0, 0.3, 0), neighbor.getBoundingBox().getCenter())) applyAction(caster, neighbor, run, action, center);
                } else applyAction(caster, enemy, run, action, center);
            }
        }
    }

    private void applyAction(ServerPlayer caster, LivingEntity target, SpellRun run, SpellBehaviour.Activation activation, Vec3 center) {
        if (target != caster && !legal(caster, target)) return;
        double power = activation.power();
        int duration = Math.min(100, Math.max(1, (int) Math.round(40 * power)));
        switch (activation.action()) {
            case IGNITE -> target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), duration));
            case DOUSE -> {
                target.clearFire();
                if (statuses.size() < 2048 || statuses.containsKey(target.getUUID())) {
                    Status status = statuses.computeIfAbsent(target.getUUID(), ignored -> new Status());
                    status.wetUntil = Math.max(status.wetUntil, tick + duration); status.lastSeen = tick;
                }
            }
            case CHILL -> { target.setTicksFrozen(Math.min(300, target.getTicksFrozen() + duration)); target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 1)); }
            case SLOW -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 0));
            case ROOT -> { Vec3 velocity = target.getDeltaMovement(); target.setDeltaMovement(0, velocity.y, 0); target.syncVelocity = true; target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 4)); }
            case WEAKEN -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0));
            case BLIND -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0));
            case REVEAL -> { target.removeEffect(MobEffects.INVISIBILITY); target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0)); }
            case LEVITATE -> target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, duration, 0));
            case PUSH -> { Vec3 origin = activation.target() == SpellBehaviour.Target.ANCHOR ? center : caster.position(); Vec3 push = target.position().subtract(origin); impulse(target, (push.lengthSqr() < 0.01 ? run.direction : push.normalize()).scale(0.3 * power)); }
            case PULL -> { Vec3 pull = center.subtract(target.position()); if (pull.lengthSqr() < 0.01) pull = caster.position().subtract(target.position()); impulse(target, pull.normalize().scale(0.3 * power)); }
            case LAUNCH -> impulse(target, new Vec3(0, 0.3 * power, 0));
            case SLAM -> impulse(target, new Vec3(0, -0.3 * power, 0));
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, duration, 0));
            case WITHER -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, duration, 0));
            case SILENCE -> { if (target instanceof ServerPlayer player) controller.interruptCasting(player); }
            case BURN_MANA -> { if (target instanceof ServerPlayer player) { controller.state().drainMana(player.getUUID(), (int) Math.ceil(4 * power), tick); controller.sync(player); } }
            case RESTORE_MANA -> { controller.state().restoreMana(caster.getUUID(), (int) Math.ceil(4 * power), tick); controller.sync(caster); }
            case HEAL -> caster.heal((float) power);
            case ABSORB -> caster.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 0));
            case RESET_VELOCITY -> { target.setDeltaMovement(Vec3.ZERO); target.syncVelocity = true; }
            case CLEANSE -> { caster.removeEffect(MobEffects.POISON); caster.removeEffect(MobEffects.WEAKNESS); caster.removeEffect(MobEffects.BLINDNESS); caster.removeEffect(MobEffects.WITHER); caster.removeEffect(MobEffects.SLOWNESS); caster.removeEffect(MobEffects.MINING_FATIGUE); }
            case ECHO_DAMAGE, SPLASH_DAMAGE -> {
                if (target == caster) break;
                controller.recordAttack(caster, target);
                Entity contact = target instanceof EnderDragon dragon ? dragon.head : target;
                contact.hurtServer(run.level, caster.damageSources().indirectMagic(caster, caster), (float) (run.spell.damage() * 0.15 * power));
            }
        }
        transientScene(run, SpellGeometry.Phase.IMPACT, run.spell.mechanics().getFirst(), target.getBoundingBox().getCenter(), run.direction, Math.min(8, 0.6 + power));
    }

    /** One scene describes a whole GPU volume; no per-point rendering data leaves the server. */
    private void scene(ServerLevel level, UUID caster, long id, SpellDefinition spell, SpellGeometry.Phase phase,
            MagicMechanic mechanic, Vec3 start, Vec3 end, int age, int duration, double radius) {
        if (sceneBudget-- <= 0) return;
        start = boundedVisualPoint(start); end = boundedVisualPoint(end);
        if (start == null || end == null || !Double.isFinite(radius)) {
            control(level, caster, id, MagicScenePayload.REMOVE);
            return;
        }
        MagicScenePayload packet = new MagicScenePayload(id, caster, level.dimension().identifier(), spell.id(),
                MagicScenePayload.UPSERT, phase.ordinal(), mechanic.ordinal(), Math.min(600, Math.max(0, age)),
                Math.min(600, Math.max(1, duration)), start.x, start.y, start.z, end.x, end.y, end.z,
                (float) Math.clamp(radius, 0.15, 32));
        sendScene(level, packet, start, end);
    }

    private static Vec3 boundedVisualPoint(Vec3 point) {
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return null;
        // A legal caster may aim past the world border. Cosmetic bounds must never abort combat.
        return new Vec3(Math.clamp(point.x, -30_000_000, 30_000_000), Math.clamp(point.y, -30_000_000, 30_000_000),
                Math.clamp(point.z, -30_000_000, 30_000_000));
    }

    private void sendScene(ServerLevel level, MagicScenePayload packet, Vec3 start, Vec3 end) {
        Vec3 center = start.add(end).scale(0.5);
        double reach = 128 + start.distanceTo(end) * 0.5;
        for (ServerPlayer observer : level.getServer().getPlayerList().getPlayers())
            if (observer.level() == level && (packet.operation() != MagicScenePayload.UPSERT
                    || observer.position().distanceToSqr(center) <= reach * reach)) TeamMagicNetwork.sendScene(observer, packet);
    }

    private void control(ServerLevel level, UUID caster, long id, int operation) {
        sendScene(level, new MagicScenePayload(id, caster, level.dimension().identifier(), "", operation,
                0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0), Vec3.ZERO, Vec3.ZERO);
    }

    private void removeRunScenes(SpellRun run) {
        control(run.level, run.caster, run.visualBase, MagicScenePayload.REMOVE_RUN);
    }

    private void travelScene(ServerPlayer caster, Effect e) {
        Vec3 start = e.target.add(0, 0.2, 0), end;
        double radius = Math.min(14, e.spell.radius());
        switch (e.mechanic) {
            case BOLT -> { start = e.origin; radius = Math.min(1.2, radius * 0.3); }
            case CONE -> { start = e.origin; radius = Math.min(5, radius); }
            case ORBIT, SHIELD -> { start = caster.position().add(0, 1, 0); if (e.mechanic == MagicMechanic.SHIELD) radius = 1.7; }
            case NOVA -> start = (e.spell.superTier() && e.stageIndex > 0 ? e.target : e.casterStart).add(0, 0.2, 0);
            case WAVE -> {
                double range = Math.min(e.spell.range(), 25);
                Vec3 base = e.spell.superTier() && e.stageIndex > 0 ? e.target.subtract(e.direction.scale(range / 2)) : e.casterStart;
                start = base.add(e.direction.scale(e.age * range / 25)).add(0, 0.2, 0);
            }
            case METEOR -> start = e.target.add(0, Math.max(0, 26 - e.age) * 0.55 + 0.3, 0);
            case MARK -> { Entity marked = e.marked == null ? null : e.level.getEntity(e.marked); if (marked != null) start = marked.position().add(0, 1, 0); }
            case ECHO -> { if (e.echo != null) start = e.echo.add(0, 0.2, 0); }
            default -> { }
        }
        end = start.add(e.mechanic == MagicMechanic.METEOR ? new Vec3(0, -1, 0) : e.direction);
        if (e.mechanic == MagicMechanic.BOLT && e.visualStart != null && e.visualEnd != null) {
            start = e.visualStart; end = e.visualEnd;
        } else if (e.mechanic == MagicMechanic.BEAM || e.mechanic == MagicMechanic.CHANNEL || e.mechanic == MagicMechanic.DRAIN) {
            start = e.visualStart == null ? e.origin : e.visualStart;
            end = e.visualEnd == null ? e.target : e.visualEnd;
            radius = Math.min(1.2, 0.3 + e.spell.tier() * 0.1);
        } else if (e.mechanic == MagicMechanic.CONE) end = trace(e.level, caster, start, start.add(e.direction.scale(Math.min(16, e.spell.range()))));
        scene(e.level, e.caster, e.run.visualBase + 1 + e.stageIndex, e.spell, SpellGeometry.Phase.TRAVEL,
                e.mechanic, start, end, e.age, Math.max(8, lifetime(e)), radius);
    }

    private void transientScene(SpellRun run, SpellGeometry.Phase phase, MagicMechanic mechanic, Vec3 center, Vec3 direction, double radius) {
        long id = run.visualBase + 32 + (run.visualCursor++ & 7);
        scene(run.level, run.caster, id, run.spell, phase, mechanic, center, center.add(direction), 0,
                phase == SpellGeometry.Phase.FINISH ? 16 : 10, radius);
    }

    private void pathVisual(Effect e, Vec3 start, Vec3 end) {
        e.visualStart = start; e.visualEnd = end;
    }

    private void beamScene(Effect e, Vec3 start, Vec3 end, MagicMechanic mechanic) {
        long id = e.run.visualBase + 64 + e.stageIndex * 16 + (e.beamSlot++ & 7);
        scene(e.level, e.caster, id, e.spell, SpellGeometry.Phase.TRAVEL, mechanic,
                start, end, 0, 10, Math.min(1.2, 0.3 + e.spell.tier() * 0.1));
    }

    private void burst(Effect e, Vec3 center) {
        transientScene(e.run, SpellGeometry.Phase.IMPACT, e.mechanic, center, e.direction, Math.min(14, e.spell.radius()));
    }

    private record PreparingScene(long id, ServerLevel level, String spell) { }
    static int color(MagicSchool school) { return switch (school) { case FIRE -> 0xFF6235; case WATER -> 0x43B9FF; case ICE -> 0xC0F9FF; case WIND -> 0x95FFDE; case EARTH -> 0xDDBA76; case LIGHTNING -> 0xCFA2FF; case LIGHT -> 0xFFF1A9; case SHADOW -> 0x874CDD; case ARCANE -> 0xF57AFF; case NATURE -> 0x69E66B; case GRAVITY -> 0x8D79FF; case TIME -> 0x68FFF0; case BLOOD -> 0xDF385B; case SPIRIT -> 0x91CDF5; }; }
    private static final class Status { long wetUntil, lastSeen, lastTimeHit; int arcaneStacks; double timeDamage; }
    private static final class SpellRun {
        final UUID caster; final ServerLevel level; final SpellDefinition spell; final SpellBehaviour.Plan plan;
        final SpellBehaviour.Tracker tracker; final Vec3 anchor, direction; final long started, visualBase; int visualCursor;
        Vec3 lastPoint; boolean finished, cancelled;
        SpellRun(ServerPlayer caster, SpellDefinition spell, SpellBehaviour.Plan plan, Vec3 anchor, Vec3 direction, long started, long visualBase) {
            this.caster = caster.getUUID(); this.level = caster.level(); this.spell = spell; this.plan = plan;
            this.tracker = plan.tracker(); this.anchor = anchor; this.direction = direction; this.started = started; this.visualBase = visualBase;
        }
        int age(long tick) { return (int) Math.min(12000, Math.max(0, tick - started)); }
    }
    private record QueuedAction(SpellRun run, SpellBehaviour.Activation activation, Vec3 point, long due) { }
    private static final class Effect {
        final UUID caster; final ServerLevel level; final SpellDefinition spell; final MagicMechanic mechanic; final Vec3 target, casterStart; final int stageIndex;
        final Map<UUID, Long> hits = new HashMap<>(); final Map<UUID, Entity> struckParts = new HashMap<>(); Vec3 origin, echo, direction, channelStart, visualStart, visualEnd; UUID marked; int age, delay, arcDepth, beamSlot; double travel; float lastWard;
        final SpellRun run; final SpellBehaviour.Stage stage; boolean closed;
        Effect(ServerPlayer caster, SpellDefinition spell, MagicMechanic mechanic, Vec3 origin, Vec3 direction, Vec3 target, int delay, int stageIndex, SpellRun run) { this.caster = caster.getUUID(); this.level = caster.level(); this.spell = spell; this.mechanic = mechanic; this.origin = origin; this.direction = direction; this.target = target; this.delay = delay; this.casterStart = caster.position(); this.stageIndex = stageIndex; this.run = run; this.stage = run.plan.stages().isEmpty() ? null : run.plan.stages().get(stageIndex); }
    }
}
