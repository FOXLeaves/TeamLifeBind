package com.teamlifebind.paper;

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
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Twenty bounded world operators; school interactions occur only after accepted ordinary damage. */
final class TeamMagicRuntime {
    private static final int MAX_EFFECTS = 128, MAX_TARGETS = 64, MAX_PARTICLES = 1200;
    private final TeamMagicController controller;
    private final List<Effect> effects = new ArrayList<>();
    private final List<CastContext> casts = new ArrayList<>();
    private final List<ScheduledAction> scheduled = new ArrayList<>();
    private final Map<UUID, Status> statuses = new HashMap<>();
    private final Map<UUID, EnderDragonPart> struckParts = new HashMap<>();
    private int particleBudget;
    private long tick;

    TeamMagicRuntime(TeamMagicController controller) { this.controller = controller; }
    void clear() { effects.clear(); casts.clear(); scheduled.clear(); statuses.clear(); struckParts.clear(); }
    void cancel(UUID player) { effects.removeIf(effect -> effect.caster.equals(player)); casts.removeIf(context -> context.caster.equals(player)); scheduled.removeIf(action -> action.context.caster.equals(player)); }
    void cancelChannels(UUID player) {
        Set<CastContext> interrupted = new HashSet<>();
        for (Effect effect : effects) if (effect.caster.equals(player) && effect.mechanic == MagicMechanic.CHANNEL) interrupted.add(effect.context);
        for (CastContext context : interrupted) cancelContext(context);
    }
    private void cancelContext(CastContext context) {
        context.cancelled = true;
        effects.removeIf(effect -> effect.context == context); casts.remove(context);
        scheduled.removeIf(action -> action.context == context);
    }
    int effectCount() { return effects.size() + scheduled.size(); }
    boolean canLaunch(SpellDefinition spell) { return casts.size() < MAX_EFFECTS && effects.size() + spell.mechanics().size() <= MAX_EFFECTS; }
    void preparing(Player caster, SpellDefinition spell) {
        circle(caster.getWorld(), spell, pos(caster).add(0, 0.1, 0), 0.7 + spell.tier() * 0.08, 12 + spell.tier() * 2, tick * 0.17);
        geometry(caster.getWorld(), spell, SpellGeometry.Phase.CAST, pos(caster).add(0, 0.15, 0), look(caster), tick, 0.9 + spell.tier() * 0.09);
    }

    void launch(Player caster, SpellDefinition spell) {
        Vec direction = look(caster).normalize(); Vec origin = eye(caster);
        Vec target = trace(caster.getWorld(), caster, origin, origin.add(direction.scale(spell.range())));
        LivingEntity aimed = firstHit(caster, origin, target, 0.3);
        if (aimed != null) target = pos(aimed);
        CastContext context = new CastContext(caster, spell, target, direction, tick); casts.add(context);
        geometry(caster.getWorld(), spell, SpellGeometry.Phase.CAST, pos(caster).add(0, 0.2, 0), direction, 0, Math.min(3, spell.radius()));
        circle(caster.getWorld(), spell, pos(caster).add(0, 0.1, 0), Math.min(3, spell.radius()), 20 + spell.tier() * 3, 0);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL,
            0.7F + spell.tier() * 0.08F, 1.45F - spell.tier() * 0.07F);
        if (spell.school() == MagicSchool.LIGHT) {
            caster.removePotionEffect(PotionEffectType.POISON); caster.removePotionEffect(PotionEffectType.WEAKNESS);
            caster.removePotionEffect(PotionEffectType.BLINDNESS); caster.removePotionEffect(PotionEffectType.WITHER);
        }
        if (spell.school() == MagicSchool.SHADOW) addEffect(caster, PotionEffectType.INVISIBILITY, 20 + spell.tier() * 8, 0);
        if (spell.school() == MagicSchool.EARTH) addEffect(caster, PotionEffectType.RESISTANCE, 40 + spell.tier() * 10, 0);
        if (spell.school() == MagicSchool.BLOOD && caster.getHealth() > 2)
            caster.damage(Math.min(2, caster.getHealth() - 1), DamageSource.builder(DamageType.MAGIC).build());
        int stageDelay = 0;
        for (int i = 0; i < spell.mechanics().size() && effects.size() < MAX_EFFECTS; i++) {
            MagicMechanic mechanic = spell.mechanics().get(i);
            Effect effect = new Effect(caster, spell, mechanic, origin, direction, target, stageDelay, i, context);
            effects.add(effect); context.effects.add(effect);
            stageDelay += !context.plan.stages().isEmpty() ? context.plan.stages().get(i).durationTicks() : switch (mechanic) {
                case METEOR -> 26; case MARK -> 36; case ECHO -> 32; case CHANNEL -> 40;
                case BOLT -> Math.min(60, (int) Math.ceil(spell.range() / (1.5 + spell.tier() * 0.06)) + 2);
                default -> 20;
            };
        }
    }

    void tick(long now) {
        tick = now; particleBudget = MAX_PARTICLES; struckParts.clear();
        statuses.entrySet().removeIf(entry -> entry.getValue().lastSeen + 240 < now);
        for (Effect effect : List.copyOf(effects)) {
            if (!effects.contains(effect)) continue;
            Player caster = Bukkit.getPlayer(effect.caster);
            if (caster == null || caster.getWorld() != effect.level || !controller.allowed(caster)) { effects.remove(effect); continue; }
            if (effect.delay-- > 0) { circle(effect.level, effect.spell, effect.origin, 0.5 + effect.delay * 0.025, 8, now * 0.1); continue; }
            if (effect.age++ >= lifetime(effect)) { effects.remove(effect); continue; }
            if (effect.age == 1) {
                effect.opened = true;
                if (!effect.context.plan.stages().isEmpty()) stageActions(effect.context, effect.context.plan.stages().get(effect.stage).opening());
            }
            Vec visualCenter = switch (effect.mechanic) { case DOMAIN, VORTEX, METEOR, MINE, WALL, SUMMON -> effect.target; case SHIELD, ORBIT -> pos(caster).add(0, 1, 0); default -> effect.origin; };
            geometry(effect.level, effect.spell, SpellGeometry.Phase.TRAVEL, visualCenter, effect.direction, now - effect.context.startedAt, Math.max(0.35, effect.spell.radius() * 0.25));
            operate(caster, effect);
        }
        for (CastContext context : List.copyOf(casts)) {
            Player caster = Bukkit.getPlayer(context.caster);
            if (caster == null || !controller.allowed(caster) || caster.getWorld() != context.level) { cancel(context.caster); continue; }
            int age = (int) Math.max(0, Math.min(Integer.MAX_VALUE, now - context.startedAt));
            queue(context, context.tracker.pulse(age), context.stageAnchor);
            for (Effect effect : context.effects) if (effect.opened && !effect.closed && !effects.contains(effect)) {
                effect.closed = true;
                if (!context.plan.stages().isEmpty()) stageActions(context, context.plan.stages().get(effect.stage).closing());
            }
            if (context.effects.stream().noneMatch(effects::contains)) {
                Vec finishPoint = context.lastHitPoint == null ? context.stageAnchor : context.lastHitPoint;
                queue(context, context.tracker.finish(age), finishPoint);
                geometry(context.level, context.spell, SpellGeometry.Phase.FINISH, finishPoint, context.direction, age, Math.min(5, context.spell.radius()));
                casts.remove(context);
            }
        }
        int processed = 0;
        for (ScheduledAction action : List.copyOf(scheduled)) {
            if (action.readyAt > now) continue;
            if (++processed > 128) break;
            if (scheduled.remove(action)) execute(action);
        }
    }

    private int lifetime(Effect e) {
        if (!e.context.plan.stages().isEmpty()) return e.context.plan.stages().get(e.stage).durationTicks();
        return switch (e.mechanic) {
            case BOLT -> Math.min(80, (int) Math.ceil(e.spell.range() / 1.5) + 2);
            case BEAM, CONE, CHAIN, DASH, BLINK, DRAIN -> 1; case NOVA -> 8;
            case METEOR -> 26; case MARK -> 36; case ECHO -> 32;
            case WAVE -> 25; case CHANNEL -> 40;
            case ORBIT, VORTEX, WALL, SHIELD, SUMMON, DOMAIN -> 60 + e.spell.tier() * 10;
            case MINE -> 200;
        };
    }

    private void operate(Player caster, Effect e) {
        SpellDefinition s = e.spell;
        int age = e.age;
        double radius = Math.min(14, s.radius());
        if (age > 1 && switch (e.mechanic) { case BEAM, CONE, CHAIN, DASH, BLINK, DRAIN -> true; default -> false; }) return;
        if ((e.mechanic == MagicMechanic.WAVE && age > 25) || (e.mechanic == MagicMechanic.METEOR && age > 26) || (e.mechanic == MagicMechanic.NOVA && age > 8)) return;
        switch (e.mechanic) {
            case BOLT -> {
                if (s.school() == MagicSchool.SPIRIT) {
                    LivingEntity seeking = nearest(caster, e.origin.add(e.direction.scale(4)), 5, Set.of());
                    if (seeking != null) e.direction = e.direction.scale(0.85).add(center(seeking).subtract(e.origin).normalize().scale(0.15)).normalize();
                }
                Vec next = e.origin.add(e.direction.scale(1.5 + s.tier() * 0.06));
                Vec end = trace(e.level, caster, e.origin, next);
                LivingEntity target = firstHit(caster, e.origin, end, 0.28);
                if (target != null) { hit(caster, e, target, 1, 0); burst(e, center(target)); effects.remove(e); }
                else if (end.distanceToSqr(next) > 0.001 || e.travel >= s.range()) { burst(e, end); effects.remove(e); }
                else { beam(e, e.origin, end, 9); e.travel += e.origin.distanceTo(end); e.origin = end; }
            }
            case BEAM -> {
                Vec end = trace(e.level, caster, eye(caster), eye(caster).add(look(caster).scale(s.range())));
                beam(e, eye(caster), end, 24 + s.tier() * 3);
                for (LivingEntity target : targets(caster, eye(caster).add(end).scale(0.5), eye(caster).distanceTo(end) / 2 + 2))
                    if (intersection(target, eye(caster), end, 0.45) != null) hit(caster, e, target, 1, 0);
            }
            case CONE -> {
                for (LivingEntity target : targets(caster, pos(caster), Math.min(s.range(), 16))) {
                    Vec delta = center(target).subtract(eye(caster));
                    if (delta.lengthSqr() > 0.001 && delta.normalize().dot(e.direction) > 0.68 && visible(caster, eye(caster), center(target))) hit(caster, e, target, 1, 0);
                }
                for (int i = 0; i < 16; i++) {
                    double angle = (i / 15.0 - 0.5) * 1.3; Vec right = new Vec(-e.direction.z, 0, e.direction.x).normalize();
                    beam(e, e.origin, trace(e.level, caster, e.origin, e.origin.add(e.direction.scale(Math.min(s.range(), 16) * Math.cos(angle))).add(right.scale(Math.min(s.range(), 16) * Math.sin(angle)))), 8);
                }
            }
            case NOVA -> {
                double front = radius * Math.min(age, 8) / 8;
                Vec center = s.superTier() && e.stage > 0 ? e.target.add(0, 0.25, 0) : e.casterStart.add(0, 0.25, 0);
                circle(e.level, s, center, front, 24 + s.tier() * 3, age * 0.1);
                for (LivingEntity target : targets(caster, center, front + 0.7)) if (!e.hits.containsKey(target.getUniqueId()) && visible(caster, center, center(target)) && hit(caster, e, target, 1, 0) > 0)
                    impulse(target, (pos(target).distanceToSqr(center) < 0.001 ? e.direction : pos(target).subtract(center).normalize()).scale(0.4).add(0, 0.12, 0));
            }
            case ORBIT -> {
                for (int i = 0; i < 3 + s.tier() / 2; i++) {
                    double angle = age * 0.18 + i * Math.PI * 2 / (3 + s.tier() / 2);
                    Vec orb = pos(caster).add(Math.cos(angle) * radius, 1.2 + Math.sin(angle * 2) * 0.5, Math.sin(angle) * radius);
                    particle(e.level, s, orb, 1.2F); area(caster, e, orb, 1.25, 0.3, 15);
                }
            }
            case CHAIN -> {
                Vec previous = e.origin; Set<UUID> visited = new HashSet<>();
                for (int hop = 0; hop < 3 + s.tier() / 2; hop++) {
                    LivingEntity nearest = hop == 0 ? firstHit(caster, e.origin, e.target, 0.7) : nearest(caster, previous, radius + 3, visited);
                    if (nearest == null) break; visited.add(nearest.getUniqueId());
                    Vec end = center(nearest); beam(e, previous, end, 18); hit(caster, e, nearest, Math.pow(0.85, hop), 0); previous = end;
                }
            }
            case WAVE -> {
                double span = Math.min(s.range(), 25);
                Vec waveStart = s.superTier() && e.stage > 0 ? e.target.subtract(e.direction.scale(span * 0.5)) : e.casterStart;
                Vec center = waveStart.add(e.direction.scale(age * span / 25));
                Vec next = trace(e.level, caster, waveStart.add(0, 0.5, 0), center.add(0, 0.5, 0));
                if (next.distanceToSqr(center.add(0, 0.5, 0)) > 0.1) { effects.remove(e); break; }
                circle(e.level, s, center, radius, 18, age * 0.1);
                for (LivingEntity target : targets(caster, center, radius)) if (!e.hits.containsKey(target.getUniqueId()) && Math.abs(pos(target).subtract(center).dot(e.direction)) < 1.0 && visible(caster, center.add(0, 0.5, 0), center(target)) && hit(caster, e, target, 1, 0) > 0)
                    impulse(target, e.direction.scale(0.35).add(0, 0.1, 0));
            }
            case VORTEX -> {
                circle(e.level, s, e.target, radius, 20, age * 0.15);
                for (LivingEntity target : targets(caster, e.target, radius)) {
                    if (!visible(caster, e.target, center(target))) continue;
                    impulse(target, e.target.subtract(pos(target)).normalize().scale(0.22).add(0, 0.025, 0));
                    hit(caster, e, target, 0.18, 12);
                }
            }
            case METEOR -> {
                circle(e.level, s, e.target, radius, 24 + s.tier() * 4, -age * 0.09);
                Vec meteor = e.target.add(0, (26 - age) * 0.55 + 0.3, 0); burst(e, meteor);
                if (age == 26) { area(caster, e, e.target, radius, 1.5, 0); burst(e, e.target); }
            }
            case MINE -> {
                if (age % 4 == 0) circle(e.level, s, e.target.add(0, 0.06, 0), radius * 0.45, 10, 0);
                if (age > 10 && !targets(caster, e.target, Math.max(1.5, radius * 0.45)).isEmpty()) { area(caster, e, e.target, radius, 1.25, 0); burst(e, e.target); effects.remove(e); }
            }
            case DASH -> {
                Vec start = pos(caster); Vec destination = safeDestination(caster, start, e.direction, Math.min(s.range(), 10), false);
                if (destination != null) { beam(e, start.add(0, 0.5, 0), destination.add(0, 0.5, 0), 26); for (LivingEntity target : targets(caster, start.add(destination).scale(0.5), start.distanceTo(destination) / 2 + radius)) if (intersection(target, start.add(0, 0.8, 0), destination.add(0, 0.8, 0), radius * 0.35) != null) hit(caster, e, target, 1, 0); teleport(caster, destination); }
            }
            case BLINK -> {
                Vec destination = safeDestination(caster, pos(caster), e.direction, Math.min(s.range(), 24), true);
                if (destination != null) { burst(e, pos(caster).add(0, 1, 0)); area(caster, e, pos(caster), radius, 0.65, 1); teleport(caster, destination); burst(e, destination.add(0, 1, 0)); area(caster, e, destination, radius, 0.65, 1); }
            }
            case WALL -> {
                Vec right = new Vec(-e.direction.z, 0, e.direction.x).normalize();
                for (int i = -6; i <= 6; i++) { Vec point = e.target.add(right.scale(i * radius / 6)); particle(e.level, s, point.add(0, age % 8 * 0.35, 0), 1.5F); }
                for (LivingEntity target : targets(caster, e.target, radius + 1)) {
                    Vec offset = pos(target).subtract(e.target);
                    if (Math.abs(offset.dot(e.direction)) < 1 && Math.abs(offset.dot(right)) < radius) {
                        impulse(target, e.direction.scale(offset.dot(e.direction) >= 0 ? 0.3 : -0.3));
                        addEffect(target, PotionEffectType.SLOWNESS, 25, 2); hit(caster, e, target, 0.15, 20);
                    }
                }
            }
            case SHIELD -> {
                if (age == 1) { addEffect(caster, PotionEffectType.ABSORPTION, lifetime(e), Math.min(3, s.tier() / 2)); addEffect(caster, PotionEffectType.RESISTANCE, lifetime(e), 0); e.lastWard = caster.getHealth() + caster.getAbsorptionAmount(); }
                if (age % 4 == 0) for (int i = 0; i < 3; i++) circle(e.level, s, pos(caster).add(0, 0.4 + i * 0.45, 0), 1.0, 12, age * 0.05);
                double ward = caster.getHealth() + caster.getAbsorptionAmount();
                var source = caster.getLastDamageCause();
                if (ward + 0.01F < e.lastWard && source instanceof org.bukkit.event.entity.EntityDamageByEntityEvent attack
                    && attack.getDamageSource().getCausingEntity() instanceof LivingEntity attacker && legal(caster, attacker)
                    && pos(attacker).distanceToSqr(pos(caster)) <= radius * radius) area(caster, e, pos(caster), radius, 0.5, 10);
                e.lastWard = ward;
            }
            case SUMMON -> {
                for (int i = 0; i < 2 + s.tier() / 2; i++) {
                    LivingEntity prey = nearest(caster, e.target, Math.min(s.range(), 20), Set.of());
                    double angle = age * 0.12 + i * 2.1; Vec wisp = e.target.add(Math.cos(angle) * 1.5, 1 + Math.sin(age * 0.08 + i), Math.sin(angle) * 1.5);
                    particle(e.level, s, wisp, 1.5F);
                    if (prey != null && age % 12 == i % 12) { beam(e, wisp, center(prey), 16); hit(caster, e, prey, 0.3, 10); }
                }
            }
            case MARK -> {
                if (age == 1) { LivingEntity marked = firstHit(caster, e.origin, e.target, 0.8); if (marked != null) e.marked = marked.getUniqueId(); }
                Entity marked = e.marked == null ? null : Bukkit.getEntity(e.marked);
                if (!(marked instanceof LivingEntity target) || !legal(caster, target) || pos(target).distanceToSqr(pos(caster)) > s.range() * s.range()) { effects.remove(e); break; }
                circle(e.level, s, pos(target).add(0, 1, 0), 0.8, 10, age * 0.2);
                if (age == 36) { hit(caster, e, target, 1.5, 0); area(caster, e, pos(target), radius, 0.5, 0); burst(e, pos(target)); }
            }
            case DRAIN -> {
                LivingEntity target = firstHit(caster, e.origin, e.target, 0.6);
                if (target != null) { beam(e, e.origin, center(target), 30); double actual = hit(caster, e, target, 0.85, 0); if (actual > 0) { heal(caster, Math.min(6, actual * 0.2)); if (target instanceof Player player) controller.drainMana(caster, player, 8 + s.tier() * 3); } }
            }
            case CHANNEL -> {
                if (age == 1) e.channelStart = pos(caster);
                if (!controller.holdsFocus(caster) || pos(caster).distanceToSqr(e.channelStart) > 9) { cancelContext(e.context); break; }
                if (age % 10 == 0 && !controller.spendChannelMana(caster, 2 + s.tier())) { cancelContext(e.context); break; }
                Vec end = trace(e.level, caster, eye(caster), eye(caster).add(look(caster).scale(s.range())));
                beam(e, eye(caster), end, 26); LivingEntity target = firstHit(caster, eye(caster), end, 0.7);
                if (target != null) hit(caster, e, target, 0.2, 8);
            }
            case ECHO -> {
                if (age == 1) { LivingEntity target = firstHit(caster, e.origin, e.target, 0.8); if (target == null) { effects.remove(e); break; } e.echo = pos(target); beam(e, e.origin, center(target), 25); hit(caster, e, target, 0.65, 0); }
                if (e.echo != null && age % 3 == 0) circle(e.level, s, e.echo.add(0, 0.1, 0), radius, 12, age * 0.1);
                if (e.echo != null && (age == 16 || age == 32)) { area(caster, e, e.echo, radius, 0.6, 10); burst(e, e.echo); }
            }
            case DOMAIN -> {
                circle(e.level, s, e.target, radius, 26 + s.tier() * 4, age * 0.06);
                for (int i = 0; i < 6; i++) { double angle = age * 0.1 + i * Math.PI / 3; particle(e.level, s, e.target.add(Math.cos(angle) * radius, 0.5 + (age % 20) * 0.08, Math.sin(angle) * radius), 1.2F); }
                area(caster, e, e.target, radius, 0.15, 15);
            }
        }
    }

    private double hit(Player caster, Effect e, LivingEntity target, double multiplier, int interval) {
        UUID id = target.getUniqueId();
        if (!legal(caster, target) || e.hits.getOrDefault(id, Long.MIN_VALUE / 2) + interval > tick) return 0;
        e.hits.put(id, tick);
        if (statuses.size() >= 2048 && !statuses.containsKey(id)) return 0;
        Status status = statuses.computeIfAbsent(id, ignored -> new Status()); status.lastSeen = tick;
        SpellBehaviour.HitContext hitContext = new SpellBehaviour.HitContext(id, (int) Math.max(0, Math.min(Integer.MAX_VALUE, tick - e.context.startedAt)), Math.max(0, Math.min(1, target.getHealth() / maxHealth(target))), target.isOnGround(), status.wetUntil > tick || target.isInWater(), target.getFireTicks() > 0, pos(target).distanceTo(pos(caster)));
        double amount = e.spell.damage() * multiplier * (1 + Math.min(3, status.arcaneStacks) * 0.08);
        int duration = 30 + e.spell.tier() * 10;
        switch (e.spell.school()) {
            case FIRE -> { if (target.getFireTicks() > 0) amount *= 1.2; }
            case ICE -> { if (target.getFreezeTicks() > 80) amount *= 1.3; }
            case EARTH -> { if (target.getVelocity().getY() > 0) amount *= 1.15; }
            case LIGHTNING -> { if (status.wetUntil > tick || target.isInWater()) amount *= 1.35; }
            case LIGHT -> { if (undead(target)) amount *= 1.35; }
            case SHADOW -> { if (look(target).dot(pos(caster).subtract(pos(target)).normalize()) < -0.3) amount *= 1.2; }
            case GRAVITY -> { if (target.getVelocity().getY() < -0.25) amount *= 1.2; }
            case TIME -> { if (status.lastTimeHit + 60 > tick) amount += status.timeDamage * 0.25; }
            default -> { }
        }
        double actual = damage(caster, target, amount);
        if (actual <= 0) return 0;
        switch (e.spell.school()) {
            case FIRE -> target.setFireTicks(Math.max(target.getFireTicks(), duration));
            case WATER -> { target.setFireTicks(0); status.wetUntil = tick + duration * 2; addEffect(target, PotionEffectType.SLOWNESS, duration, 0); }
            case ICE -> { target.setFreezeTicks(target.getFreezeTicks() > 80 ? 0 : Math.min(300, target.getFreezeTicks() + (status.wetUntil > tick ? 150 : 70))); addEffect(target, PotionEffectType.SLOWNESS, duration, status.wetUntil > tick ? 2 : 1); }
            case WIND -> { impulse(target, pos(target).subtract(pos(caster)).normalize().scale(0.35).add(0, 0.2, 0)); effects.removeIf(other -> other != e && (other.mechanic == MagicMechanic.DOMAIN || other.mechanic == MagicMechanic.VORTEX) && other.level == e.level && other.target.distanceToSqr(pos(target)) < 9); }
            case EARTH -> { addEffect(target, PotionEffectType.SLOWNESS, duration, 2); if (target.getVelocity().getY() > 0) impulse(target, new Vec(0, -0.5, 0)); }
            case LIGHTNING -> {
                addEffect(target, PotionEffectType.SLOWNESS, 15, 3);
                if ((status.wetUntil > tick || target.isInWater()) && e.arcDepth == 0) {
                    e.arcDepth++;
                    for (LivingEntity nearby : targets(caster, pos(target), 3)) if (nearby != target) {
                        beam(e, center(target), center(nearby), 12); hit(caster, e, nearby, multiplier * 0.4, Math.max(1, interval));
                    }
                    e.arcDepth--;
                }
            }
            case LIGHT -> addEffect(target, PotionEffectType.GLOWING, duration, 0);
            case SHADOW -> addEffect(target, PotionEffectType.BLINDNESS, duration, 0);
            case ARCANE -> { target.setAbsorptionAmount(Math.max(0, target.getAbsorptionAmount() - (2 + e.spell.tier()))); status.arcaneStacks = Math.min(3, status.arcaneStacks + 1); if (target instanceof Player player) controller.interruptCasting(player); }
            case NATURE -> { addEffect(target, PotionEffectType.POISON, duration, 0); addEffect(target, PotionEffectType.SLOWNESS, duration, 2); if (target.getHealth() < maxHealth(target) * 0.75) heal(caster, Math.min(1.5, actual * 0.08)); }
            case GRAVITY -> {
                Vec anchor = switch (e.mechanic) { case VORTEX, METEOR, MINE, WALL, SUMMON, DOMAIN -> e.target; default -> pos(caster); };
                if (e.mechanic != MagicMechanic.NOVA) impulse(target, anchor.subtract(pos(target)).normalize().scale(0.25).add(0, -0.15, 0));
            }
            case TIME -> { addEffect(target, PotionEffectType.SLOWNESS, duration, 1); addEffect(target, PotionEffectType.MINING_FATIGUE, duration, 0); status.lastTimeHit = tick; status.timeDamage = amount; controller.accelerate(caster, 2); }
            case BLOOD -> { addEffect(target, PotionEffectType.WITHER, duration, 0); heal(caster, Math.min(3, actual * 0.16)); }
            case SPIRIT -> { target.removePotionEffect(PotionEffectType.INVISIBILITY); addEffect(target, PotionEffectType.GLOWING, duration, 0); addEffect(target, PotionEffectType.WEAKNESS, duration, 0); }
        }
        e.context.lastHitPoint = pos(target);
        queue(e.context, e.context.tracker.hit(hitContext), e.context.lastHitPoint);
        geometry(e.level, e.spell, SpellGeometry.Phase.IMPACT, center(target), e.direction, hitContext.age(), Math.min(3, e.spell.radius()));
        return actual;
    }

    private double damage(Player caster, LivingEntity target, double amount) {
        if (amount <= 0 || !legal(caster, target)) return 0;
        double before = target.getHealth() + target.getAbsorptionAmount();
        EnderDragonPart part = struckParts.remove(target.getUniqueId());
        if (target instanceof EnderDragon dragon && part == null) part = dragon.getParts().stream().filter(EnderDragonPart.class::isInstance).map(EnderDragonPart.class::cast).filter(ServerGunDamageCompat::isHead).findFirst().orElse(null);
        if (part != null) ServerGunDamageCompat.magicDamage(part, caster, amount);
        else target.damage(amount, DamageSource.builder(DamageType.INDIRECT_MAGIC).withDirectEntity(caster).withCausingEntity(caster).build());
        return Math.max(0, before - target.getHealth() - target.getAbsorptionAmount());
    }
    private void stageActions(CastContext context, List<SpellBehaviour.Action> actions) {
        queue(context, actions.stream().map(action -> new SpellBehaviour.Activation(action,
                beneficial(action) ? SpellBehaviour.Target.CASTER : SpellBehaviour.Target.ANCHOR, null, 0.8, 0)).toList(), context.stageAnchor);
    }
    private void queue(CastContext context, List<SpellBehaviour.Activation> actions, Vec point) {
        if (context.cancelled) return;
        for (SpellBehaviour.Activation action : actions) {
            if (scheduled.size() >= 256) break;
            scheduled.add(new ScheduledAction(context, action, point, tick + action.delayTicks()));
        }
    }
    private void execute(ScheduledAction scheduledAction) {
        CastContext context = scheduledAction.context; SpellBehaviour.Activation action = scheduledAction.activation;
        if (context.cancelled) return;
        Player caster = Bukkit.getPlayer(context.caster);
        if (caster == null || caster.getWorld() != context.level || !controller.allowed(caster)) return;
        if (action.target() == SpellBehaviour.Target.CASTER) { applyAction(caster, context, action, caster, scheduledAction.anchor); return; }
        if (action.target() == SpellBehaviour.Target.ANCHOR) {
            if (beneficial(action.action())) { applyAction(caster, context, action, caster, scheduledAction.anchor); return; }
            double radius = Math.min(8, context.spell.radius());
            for (LivingEntity target : targets(caster, scheduledAction.anchor, radius)) if (visible(caster, scheduledAction.anchor.add(0, 0.3, 0), center(target))) applyAction(caster, context, action, target, scheduledAction.anchor);
            return;
        }
        Entity found = action.targetId() == null ? null : Bukkit.getEntity(action.targetId());
        if (found instanceof LivingEntity target && legal(caster, target) && pos(target).distanceToSqr(pos(caster)) <= Math.pow(context.spell.range(), 2) && visible(caster, eye(caster), center(target))) {
            if (action.action() == SpellBehaviour.Action.SPLASH_DAMAGE) {
                for (LivingEntity nearby : targets(caster, scheduledAction.anchor, Math.min(8, context.spell.radius())))
                    if (visible(caster, scheduledAction.anchor.add(0, 0.3, 0), center(nearby)))
                        applyAction(caster, context, action, nearby, scheduledAction.anchor);
            } else applyAction(caster, context, action, target, scheduledAction.anchor);
        }
    }
    private static boolean beneficial(SpellBehaviour.Action action) { return switch (action) { case RESTORE_MANA, HEAL, ABSORB, CLEANSE -> true; default -> false; }; }
    private void applyAction(Player caster, CastContext context, SpellBehaviour.Activation activation, LivingEntity target, Vec anchor) {
        SpellBehaviour.Action action = activation.action();
        double power = activation.power();
        int duration = Math.min(100, Math.max(1, (int) Math.round(40 * power)));
        Status status = statuses.get(target.getUniqueId());
        if (status == null && statuses.size() < 2048) { status = new Status(); statuses.put(target.getUniqueId(), status); }
        if (status != null) status.lastSeen = tick;
        switch (action) {
            case IGNITE -> target.setFireTicks(Math.max(target.getFireTicks(), duration));
            case DOUSE -> { target.setFireTicks(0); if (status != null) status.wetUntil = Math.max(status.wetUntil, tick + duration); }
            case CHILL -> { target.setFreezeTicks(Math.min(300, target.getFreezeTicks() + duration)); addEffect(target, PotionEffectType.SLOWNESS, duration, 1); }
            case SLOW -> addEffect(target, PotionEffectType.SLOWNESS, duration, 0);
            case ROOT -> { addEffect(target, PotionEffectType.SLOWNESS, duration, 4); target.setVelocity(new Vector(0, target.getVelocity().getY(), 0)); }
            case WEAKEN -> addEffect(target, PotionEffectType.WEAKNESS, duration, 0);
            case BLIND -> addEffect(target, PotionEffectType.BLINDNESS, duration, 0);
            case REVEAL -> { target.removePotionEffect(PotionEffectType.INVISIBILITY); addEffect(target, PotionEffectType.GLOWING, duration, 0); }
            case LEVITATE -> addEffect(target, PotionEffectType.LEVITATION, duration, 0);
            case PUSH -> { Vec origin = activation.target() == SpellBehaviour.Target.ANCHOR ? anchor : pos(caster); Vec direction = pos(target).subtract(origin); impulse(target, (direction.lengthSqr() < 0.001 ? context.direction : direction.normalize()).scale(0.3 * power)); }
            case PULL -> { Vec direction = anchor.subtract(pos(target)); if (direction.lengthSqr() < 0.001) direction = pos(caster).subtract(pos(target)); impulse(target, direction.normalize().scale(0.3 * power)); }
            case LAUNCH -> impulse(target, new Vec(0, 0.3 * power, 0));
            case SLAM -> impulse(target, new Vec(0, -0.3 * power, 0));
            case POISON -> addEffect(target, PotionEffectType.POISON, duration, 0);
            case WITHER -> addEffect(target, PotionEffectType.WITHER, duration, 0);
            case SILENCE -> { if (target instanceof Player player) controller.interruptCasting(player); }
            case BURN_MANA -> { if (target instanceof Player player) controller.state().drainMana(player.getUniqueId(), (int) Math.ceil(4 * power), tick); }
            case RESTORE_MANA -> { if (target instanceof Player player) controller.state().restoreMana(player.getUniqueId(), (int) Math.ceil(4 * power), tick); }
            case HEAL -> heal(target, power);
            case ABSORB -> addEffect(target, PotionEffectType.ABSORPTION, duration, 0);
            case RESET_VELOCITY -> target.setVelocity(new Vector());
            case CLEANSE -> { target.removePotionEffect(PotionEffectType.POISON); target.removePotionEffect(PotionEffectType.WEAKNESS); target.removePotionEffect(PotionEffectType.BLINDNESS); target.removePotionEffect(PotionEffectType.WITHER); target.removePotionEffect(PotionEffectType.SLOWNESS); target.removePotionEffect(PotionEffectType.MINING_FATIGUE); }
            case ECHO_DAMAGE, SPLASH_DAMAGE -> damage(caster, target, context.spell.damage() * 0.15 * power);
        }
    }

    private void area(Player caster, Effect e, Vec center, double radius, double multiplier, int interval) {
        for (LivingEntity target : targets(caster, center, radius)) if (visible(caster, center.add(0, 0.25, 0), center(target))) hit(caster, e, target, multiplier, interval);
    }
    private List<LivingEntity> targets(Player caster, Vec center, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : caster.getWorld().getNearbyEntities(BoundingBox.of(center.vector(), center.vector()).expand(radius))) {
            if (entity instanceof LivingEntity living && legal(caster, living) && center(living).distanceToSqr(center) <= radius * radius) result.add(living);
            if (result.size() >= MAX_TARGETS) break;
        }
        return result;
    }
    private boolean legal(Player caster, LivingEntity target) { return target != caster && !target.isDead() && target.isValid() && target.getWorld() == caster.getWorld() && controller.allowed(caster) && (!(target instanceof Player player) || controller.pvpAllowed(caster, player)); }
    private LivingEntity nearest(Player caster, Vec center, double radius, Set<UUID> skip) {
        LivingEntity nearest = null; double distance = radius * radius;
        for (LivingEntity candidate : targets(caster, center, radius)) { double next = pos(candidate).distanceToSqr(center); if (!skip.contains(candidate.getUniqueId()) && next < distance && visible(caster, center, center(candidate))) { distance = next; nearest = candidate; } }
        return nearest;
    }
    private LivingEntity firstHit(Player caster, Vec start, Vec end, double width) {
        LivingEntity nearest = null; EnderDragonPart struck = null; double distance = start.distanceToSqr(end);
        BoundingBox query = BoundingBox.of(start.vector(), end.vector()).expand(width + 2);
        int examined = 0;
        for (Entity candidate : caster.getWorld().getNearbyEntities(query)) {
            if (++examined > MAX_TARGETS * 4) break;
            if (!(candidate instanceof LivingEntity target) || !legal(caster, target)) continue;
            if (target instanceof EnderDragon dragon) {
                for (var candidatePart : dragon.getParts()) if (candidatePart instanceof EnderDragonPart part) { Vec point = intersection(part, start, end, width); if (point != null && start.distanceToSqr(point) <= distance) { distance = start.distanceToSqr(point); nearest = dragon; struck = part; } }
            } else { Vec point = intersection(target, start, end, width); if (point != null && start.distanceToSqr(point) <= distance) { distance = start.distanceToSqr(point); nearest = target; struck = null; } }
        }
        if (nearest != null && struck != null) struckParts.put(nearest.getUniqueId(), struck);
        return nearest;
    }
    private static Vec intersection(Entity target, Vec start, Vec end, double width) {
        Vec delta = end.subtract(start); double length = delta.length();
        if (length < 1E-8) return target.getBoundingBox().clone().expand(width).contains(start.vector()) ? start : null;
        var result = target.getBoundingBox().clone().expand(width).rayTrace(start.vector(), delta.normalize().vector(), length);
        return result == null ? null : Vec.of(result.getHitPosition());
    }
    private static Vec trace(World world, Player caster, Vec start, Vec end) {
        Vec delta = end.subtract(start); if (delta.lengthSqr() < 1E-8) return end;
        var wall = world.rayTraceBlocks(start.location(world), delta.normalize().vector(), delta.length(), FluidCollisionMode.NEVER, true);
        return wall == null ? end : Vec.of(wall.getHitPosition());
    }
    private static boolean visible(Player caster, Vec start, Vec end) { return trace(caster.getWorld(), caster, start, end).distanceToSqr(end) < 0.05; }
    private static void impulse(LivingEntity target, Vec velocity) { Vector combined = target.getVelocity().add(velocity.vector()); if (combined.lengthSquared() > 4) combined.normalize().multiply(2); target.setVelocity(combined); }
    private Vec safeDestination(Player caster, Vec start, Vec direction, double distance, boolean standing) {
        World world = caster.getWorld(); Vec end = trace(world, caster, eye(caster), eye(caster).add(direction.scale(distance)));
        double clear = Math.max(0, Math.min(distance, end.distanceTo(eye(caster)) - 0.7));
        for (double step = clear; step > 0.5; step -= 0.5) {
            Vec candidate = start.add(direction.scale(step)); Location loc = candidate.location(world);
            if (loc.getY() < world.getMinHeight() + 1 || loc.getY() + 2 >= world.getMaxHeight() || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4) || !world.getWorldBorder().isInside(loc)) continue;
            BoundingBox body = caster.getBoundingBox().clone().shift(candidate.subtract(start).vector());
            boolean blocked = false;
            for (int x = (int) Math.floor(body.getMinX()); x <= (int) Math.floor(body.getMaxX()); x++)
                for (int y = (int) Math.floor(body.getMinY()); y <= (int) Math.floor(body.getMaxY()); y++)
                    for (int z = (int) Math.floor(body.getMinZ()); z <= (int) Math.floor(body.getMaxZ()); z++)
                        if (world.getBlockAt(x, y, z).getCollisionShape().overlaps(body)) blocked = true;
            if (!blocked && (!standing || !world.getBlockAt(loc.clone().subtract(0, 0.2, 0)).isPassable())) return candidate;
        }
        return null;
    }
    private static void teleport(Player caster, Vec point) { Location location = point.location(caster.getWorld()); location.setYaw(caster.getLocation().getYaw()); location.setPitch(caster.getLocation().getPitch()); caster.teleport(location, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN); }
    private static Vec pos(Entity entity) { return Vec.of(entity.getLocation().toVector()); }
    private static Vec eye(LivingEntity entity) { return Vec.of(entity.getEyeLocation().toVector()); }
    private static Vec look(LivingEntity entity) { return Vec.of(entity.getEyeLocation().getDirection()); }
    private static Vec center(Entity entity) { return Vec.of(entity.getBoundingBox().getCenter()); }
    private static double maxHealth(LivingEntity entity) { var attribute = entity.getAttribute(Attribute.MAX_HEALTH); return attribute == null ? 20 : attribute.getValue(); }
    private static void heal(LivingEntity entity, double amount) { if (!entity.isDead() && amount > 0) entity.setHealth(Math.min(maxHealth(entity), entity.getHealth() + amount)); }
    private static void addEffect(LivingEntity entity, PotionEffectType type, int ticks, int amplifier) { entity.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true)); }
    private static boolean undead(LivingEntity entity) { return switch (entity.getType().name()) { case "ZOMBIE", "HUSK", "DROWNED", "ZOMBIFIED_PIGLIN", "SKELETON", "WITHER_SKELETON", "STRAY", "BOGGED", "PHANTOM", "WITHER", "ZOGLIN", "SKELETON_HORSE", "ZOMBIE_HORSE" -> true; default -> false; }; }
    private void geometry(World world, SpellDefinition spell, SpellGeometry.Phase phase, Vec anchor, Vec direction, double age, double radius) {
        SpellBehaviour.Plan plan = SpellBehaviour.byId(spell.id()); if (plan == null) return;
        Vec forward = new Vec(direction.x, 0, direction.z).normalize(); if (forward.lengthSqr() < 0.01) forward = new Vec(0, 0, 1);
        Vec right = new Vec(-forward.z, 0, forward.x);
        int layers = spell.superTier() ? 3 : spell.tier() >= 4 ? 2 : 1;
        for (int layer = 0; layer < layers && particleBudget > 0; layer++) {
            int index = 0;
            for (SpellGeometry.Point point : SpellGeometry.sample(plan.visual(), phase, age + layer * 5, radius * (1 + layer * 0.2), spell.superTier() ? 64 : 20 + spell.tier() * 4)) {
                if (particleBudget <= 0) break;
                double angle = layer * Math.PI / 3;
                Vec at = anchor.add(right.scale(point.x() * Math.cos(angle) - point.z() * Math.sin(angle))).add(0, point.y() + layer * 0.22, 0).add(forward.scale(point.x() * Math.sin(angle) + point.z() * Math.cos(angle)));
                particle(world, spell, at, 0.65F + spell.tier() * 0.08F);
                if (index++ % 6 == 0 && particleBudget > 0) { particleBudget--; world.spawnParticle(accent(spell.school()), at.location(world), 1, 0, 0, 0, 0, null, false); }
            }
        }
    }
    private static Particle accent(MagicSchool school) { return switch (school) {
        case FIRE -> Particle.FLAME; case WATER -> Particle.SPLASH; case ICE -> Particle.SNOWFLAKE;
        case WIND -> Particle.CLOUD; case EARTH -> Particle.CRIT; case LIGHTNING -> Particle.ELECTRIC_SPARK;
        case LIGHT -> Particle.GLOW; case SHADOW -> Particle.SMOKE; case ARCANE -> Particle.ENCHANT;
        case NATURE -> Particle.HAPPY_VILLAGER; case GRAVITY -> Particle.PORTAL; case TIME -> Particle.REVERSE_PORTAL;
        case BLOOD -> Particle.DAMAGE_INDICATOR; case SPIRIT -> Particle.SOUL_FIRE_FLAME;
    }; }
    private void beam(Effect e, Vec start, Vec end, int steps) { for (int i = 0; i <= steps && particleBudget > 0; i++) { Vec point = start.add(end.subtract(start).scale(i / (double) steps)); particle(e.level, e.spell, point, 0.7F + e.spell.tier() * 0.08F); if (e.spell.tier() >= 6 && i % 3 == 0) particle(e.level, e.spell, point.add(Math.sin(i + tick * 0.1) * 0.18, Math.cos(i + tick * 0.1) * 0.18, 0), 0.6F); } }
    private void circle(World level, SpellDefinition spell, Vec center, double radius, int steps, double phase) { for (int i = 0; i < steps && particleBudget > 0; i++) { double angle = Math.PI * 2 * i / steps + phase; particle(level, spell, center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), 0.8F + spell.tier() * 0.08F); } }
    private void burst(Effect e, Vec center) { for (int ring = 0; ring < (e.spell.superTier() ? 4 : 1 + e.spell.tier() / 3); ring++) circle(e.level, e.spell, center.add(0, ring * 0.2, 0), 0.2 + ring * 0.3, 10 + e.spell.tier() * 2, tick * 0.1); }
    private void particle(World level, SpellDefinition spell, Vec point, float size) {
        if (particleBudget <= 0) return; particleBudget--;
        level.spawnParticle(Particle.DUST, point.location(level), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(color(spell.school())), size), false);
        if (spell.superTier() && particleBudget > 0 && (particleBudget & 7) == 0) { particleBudget--; level.spawnParticle(Particle.END_ROD, point.location(level), 1, 0, 0, 0, 0, null, false); }
    }
    static int color(MagicSchool school) { return switch (school) { case FIRE -> 0xFF6235; case WATER -> 0x43B9FF; case ICE -> 0xC0F9FF; case WIND -> 0x95FFDE; case EARTH -> 0xDDBA76; case LIGHTNING -> 0xCFA2FF; case LIGHT -> 0xFFF1A9; case SHADOW -> 0x874CDD; case ARCANE -> 0xF57AFF; case NATURE -> 0x69E66B; case GRAVITY -> 0x8D79FF; case TIME -> 0x68FFF0; case BLOOD -> 0xDF385B; case SPIRIT -> 0x91CDF5; }; }
    private static final class Status { long wetUntil, lastSeen, lastTimeHit; int arcaneStacks; double timeDamage; }
    private static final class Effect {
        final UUID caster; final World level; final SpellDefinition spell; final MagicMechanic mechanic; final Vec target, casterStart; final int stage;
        Vec direction; final CastContext context; boolean opened, closed;
        final Map<UUID, Long> hits = new HashMap<>(); Vec origin, echo, channelStart; UUID marked; int age, delay, arcDepth; double travel, lastWard;
        Effect(Player caster, SpellDefinition spell, MagicMechanic mechanic, Vec origin, Vec direction, Vec target, int delay, int stage, CastContext context) { this.caster = caster.getUniqueId(); this.level = caster.getWorld(); this.spell = spell; this.mechanic = mechanic; this.origin = origin; this.direction = direction; this.target = target; this.delay = delay; this.stage = stage; this.context = context; this.casterStart = pos(caster); this.channelStart = casterStart; }
    }
    private static final class CastContext {
        final UUID caster; final World level; final SpellDefinition spell; final SpellBehaviour.Plan plan; final SpellBehaviour.Tracker tracker;
        final Vec direction, stageAnchor; final long startedAt; final List<Effect> effects = new ArrayList<>(); Vec lastHitPoint; boolean cancelled;
        CastContext(Player caster, SpellDefinition spell, Vec anchor, Vec direction, long tick) { this.caster = caster.getUniqueId(); this.level = caster.getWorld(); this.spell = spell; this.plan = SpellBehaviour.byId(spell.id()); this.tracker = plan.tracker(); this.stageAnchor = anchor; this.direction = direction; this.startedAt = tick; }
    }
    private record ScheduledAction(CastContext context, SpellBehaviour.Activation activation, Vec anchor, long readyAt) { }
    private record Vec(double x, double y, double z) {
        static Vec of(Vector value) { return new Vec(value.getX(), value.getY(), value.getZ()); }
        Vector vector() { return new Vector(x, y, z); }
        Location location(World world) { return new Location(world, x, y, z); }
        Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
        Vec add(double dx, double dy, double dz) { return new Vec(x + dx, y + dy, z + dz); }
        Vec subtract(Vec other) { return new Vec(x - other.x, y - other.y, z - other.z); }
        Vec scale(double factor) { return new Vec(x * factor, y * factor, z * factor); }
        double lengthSqr() { return x * x + y * y + z * z; }
        double length() { return Math.sqrt(lengthSqr()); }
        Vec normalize() { double size = length(); return size < 1E-8 ? new Vec(0, 0, 0) : scale(1 / size); }
        double dot(Vec other) { return x * other.x + y * other.y + z * other.z; }
        double distanceToSqr(Vec other) { return subtract(other).lengthSqr(); }
        double distanceTo(Vec other) { return subtract(other).length(); }
    }
}
