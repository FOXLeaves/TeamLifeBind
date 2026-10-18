package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import static com.teamlifebind.common.SpellBehaviour.Action.*;
import static com.teamlifebind.common.SpellBehaviour.Condition.*;
import static com.teamlifebind.common.SpellBehaviour.Event.*;
import static com.teamlifebind.common.SpellBehaviour.Target.*;

/** Checks actual rule transitions and sampled moving paths, rather than trusting catalogue labels. */
public final class SpellBehaviourRegressionTest {
    private static int assertions;
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final SpellGeometry.Profile VISUAL = new SpellGeometry.Profile(SpellGeometry.Shape.CIRCLE,
        SpellGeometry.Shape.SPIRAL, SpellGeometry.Shape.ROSE, SpellGeometry.Shape.WINGS, SpellGeometry.Motion.SPIN);
    private SpellBehaviourRegressionTest() { }

    public static int run() {
        assertions = 0;
        hitIntervals(); definitions(); triggerTransitions(); conditions(); visualPaths(); boundedState();
        System.out.println("Distinct spell triggers, timed rituals and moving geometry passed (" + assertions + " assertions).");
        return assertions;
    }

    private static void definitions() {
        equal(300, SpellBehaviour.all().size());
        Set<String> structures = new HashSet<>();
        Set<String> sampledTraces = new HashSet<>();
        Set<String> executedTraces = new HashSet<>();
        for (SpellDefinition spell : SpellCatalogue.all()) {
            SpellBehaviour.Plan plan = spell.behaviour();
            truth(plan != null); truth(structures.add(plan.fingerprint()));
            equal(plan.fingerprint(), spell.mechanicFingerprint());
            truth(spell.zhDescription().contains(plan.zhDescription()));
            truth(spell.enDescription().contains(plan.enDescription()));
            truth(sampledTraces.add(visualTrace(plan.visual())));
            truth(executedTraces.add(behaviourTrace(plan)));
            equal(spell.superTier() ? spell.mechanics().size() : 0, plan.stages().size());
            if (spell.superTier()) {
                equal(spell.mechanics(), plan.stages().stream().map(SpellBehaviour.Stage::mechanic).toList());
                truth(plan.stages().stream().mapToInt(SpellBehaviour.Stage::durationTicks).sum() <= 400);
                for (SpellBehaviour.Stage stage : plan.stages()) {
                    int minimum = switch (stage.mechanic()) { case METEOR -> 26; case MARK -> 36;
                        case ECHO -> 32; case CHANNEL, ORBIT, SHIELD -> 40; case WAVE -> 25; default -> 1; };
                    truth(stage.durationTicks() >= minimum);
                }
            }
            for (SpellBehaviour.Rule rule : plan.rules()) {
                truth(!rule.actions().isEmpty());
                if (!spell.superTier() && Set.of(MagicMechanic.BOLT, MagicMechanic.BEAM, MagicMechanic.CONE,
                        MagicMechanic.NOVA, MagicMechanic.CHAIN, MagicMechanic.WAVE, MagicMechanic.METEOR,
                        MagicMechanic.MINE, MagicMechanic.DASH, MagicMechanic.BLINK, MagicMechanic.MARK,
                        MagicMechanic.DRAIN).contains(spell.primaryMechanic())) {
                    truth(rule.event() != REPEAT_HIT);
                }
                if (rule.actions().stream().anyMatch(SpellBehaviour::beneficial)) equal(CASTER, rule.target());
                // Every configured rule is reachable with world states that fulfil its documented condition.
                SpellBehaviour.Plan isolated = new SpellBehaviour.Plan(List.of(rule,
                    new SpellBehaviour.Rule(FINISH, ALWAYS, CASTER, List.of(CLEANSE), 1, 0, 0, 0.1)), List.of(), VISUAL);
                SpellBehaviour.Tracker tracker = isolated.tracker();
                SpellBehaviour.HitContext hit = matchingHit(rule.condition(), 0);
                List<SpellBehaviour.Activation> result = tracker.hit(hit);
                if (rule.event() == REPEAT_HIT) result = tracker.hit(matchingHit(rule.condition(), Math.max(1, rule.intervalTicks())));
                if (rule.event() == PULSE) result = tracker.pulse(Math.max(1, rule.intervalTicks()));
                if (rule.event() == FINISH) result = tracker.finish(400);
                for (SpellBehaviour.Action action : rule.actions()) truth(result.stream().anyMatch(a -> a.action() == action && a.target() == rule.target()));
            }
        }
        equal(null, SpellBehaviour.byId(null)); equal(null, SpellBehaviour.byId("unknown"));
        fails(() -> SpellBehaviour.all().clear());
    }

    private static SpellBehaviour.HitContext matchingHit(SpellBehaviour.Condition condition, int age) {
        return new SpellBehaviour.HitContext(TARGET, age, condition == WOUNDED ? 0.2 : 1,
            condition != AIRBORNE, condition == WET, condition == BURNING, condition == FAR ? 8 : 2);
    }

    private static SpellBehaviour.HitContext targetHit(int target, int age) {
        return new SpellBehaviour.HitContext(new UUID(0, target), age, 1, true, false, false, 2);
    }

    /** Native cone/beam/chain adapters can accept several different enemies in one server tick. */
    private static void hitIntervals() {
        for (SpellBehaviour.Target target : List.of(HIT, LAST_HIT)) {
            SpellBehaviour.Rule rule = new SpellBehaviour.Rule(EVERY_HIT, ALWAYS, target, List.of(SLOW), 4, 10, 0, 1);
            SpellBehaviour.Tracker tracker = isolated(rule).tracker();
            equal(List.of(SLOW), actions(tracker.hit(targetHit(1, 1))));
            // Target 1's interval must not suppress target 2 in the same cone/beam.
            List<SpellBehaviour.Activation> second = tracker.hit(targetHit(2, 1));
            equal(List.of(SLOW), actions(second)); equal(new UUID(0, 2), second.getFirst().targetId());
            equal(List.of(), tracker.hit(targetHit(1, 1)));
            equal(List.of(), tracker.hit(targetHit(1, 10)));
            equal(List.of(SLOW), actions(tracker.hit(targetHit(1, 11))));
            equal(List.of(SLOW), actions(tracker.hit(targetHit(3, 11))));
            // Four activations belong to the whole cast, not to each target.
            equal(List.of(), tracker.hit(targetHit(4, 11)));
            equal(List.of(), tracker.hit(targetHit(2, 21)));
            truth(isolated(rule).zhDescription().contains("同一目标"));

            SpellBehaviour.Tracker repeated = isolated(new SpellBehaviour.Rule(REPEAT_HIT, ALWAYS,
                target, List.of(ROOT), 4, 10, 0, 1)).tracker();
            equal(List.of(), repeated.hit(targetHit(1, 0)));
            equal(List.of(), repeated.hit(targetHit(2, 0)));
            equal(List.of(ROOT), actions(repeated.hit(targetHit(1, 1))));
            equal(List.of(ROOT), actions(repeated.hit(targetHit(2, 1))));
            equal(List.of(), repeated.hit(targetHit(1, 10)));
            equal(List.of(ROOT), actions(repeated.hit(targetHit(1, 11))));
            equal(List.of(ROOT), actions(repeated.hit(targetHit(2, 11))));
            equal(List.of(), repeated.hit(targetHit(1, 21)));
        }
        // Self rewards and area effects keep one interval, even when several enemies are hit.
        for (SpellBehaviour.Target target : List.of(CASTER, ANCHOR)) {
            SpellBehaviour.Action action = target == CASTER ? RESTORE_MANA : SPLASH_DAMAGE;
            for (SpellBehaviour.Event event : List.of(EVERY_HIT, REPEAT_HIT)) {
                SpellBehaviour.Rule rule = new SpellBehaviour.Rule(event, ALWAYS, target, List.of(action), 4, 10, 0, 1);
                SpellBehaviour.Tracker tracker = isolated(rule).tracker();
                if (event == REPEAT_HIT) { tracker.hit(targetHit(1, 0)); tracker.hit(targetHit(2, 0)); }
                equal(List.of(action), actions(tracker.hit(targetHit(1, 1))));
                equal(List.of(), tracker.hit(targetHit(2, 1)));
                equal(List.of(), tracker.hit(targetHit(2, 10)));
                equal(List.of(action), actions(tracker.hit(targetHit(2, 11))));
                truth(isolated(rule).zhDescription().contains("整次施法共用间隔"));
            }
        }
        SpellBehaviour.Tracker pulses = isolated(new SpellBehaviour.Rule(PULSE, ALWAYS, LAST_HIT,
            List.of(WEAKEN), 4, 10, 0, 1)).tracker();
        pulses.hit(targetHit(1, 0));
        equal(List.of(WEAKEN), actions(pulses.pulse(10)));
        pulses.hit(targetHit(2, 10));
        equal(List.of(), pulses.pulse(10));
        equal(List.of(), pulses.pulse(11));
        equal(List.of(WEAKEN), actions(pulses.pulse(20)));
    }

    private static SpellBehaviour.Plan isolated(SpellBehaviour.Rule rule) {
        return new SpellBehaviour.Plan(List.of(rule,
            new SpellBehaviour.Rule(FINISH, ALWAYS, CASTER, List.of(CLEANSE), 1, 0, 0, 0.1)), List.of(), VISUAL);
    }

    private static void triggerTransitions() {
        SpellBehaviour.Plan plan = new SpellBehaviour.Plan(List.of(
            new SpellBehaviour.Rule(FIRST_HIT, ALWAYS, HIT, List.of(IGNITE), 1, 0, 0, 1),
            new SpellBehaviour.Rule(REPEAT_HIT, ALWAYS, HIT, List.of(SLAM), 2, 10, 0, 1),
            new SpellBehaviour.Rule(PULSE, ALWAYS, CASTER, List.of(RESTORE_MANA), 2, 20, 0, 1),
            new SpellBehaviour.Rule(FINISH, ALWAYS, LAST_HIT, List.of(ECHO_DAMAGE), 1, 0, 12, 1)), List.of(), VISUAL);
        SpellBehaviour.Tracker tracker = plan.tracker();
        equal(List.of(IGNITE), actions(tracker.hit(matchingHit(ALWAYS, 0))));
        equal(List.of(SLAM), actions(tracker.hit(matchingHit(ALWAYS, 1))));
        equal(List.of(), actions(tracker.hit(matchingHit(ALWAYS, 5))));
        equal(List.of(SLAM), actions(tracker.hit(matchingHit(ALWAYS, 11))));
        equal(List.of(), actions(tracker.hit(matchingHit(ALWAYS, 21))));
        equal(List.of(RESTORE_MANA), actions(tracker.pulse(20)));
        equal(List.of(), actions(tracker.pulse(20))); equal(List.of(), actions(tracker.pulse(10)));
        equal(List.of(RESTORE_MANA), actions(tracker.pulse(40)));
        equal(List.of(), actions(tracker.pulse(60)));
        List<SpellBehaviour.Activation> finale = tracker.finish(80);
        equal(List.of(ECHO_DAMAGE), actions(finale)); equal(TARGET, finale.getFirst().targetId()); equal(12, finale.getFirst().delayTicks());
        truth(tracker.finished()); equal(List.of(), tracker.finish(80)); equal(List.of(), tracker.pulse(100));
        equal(List.of(), tracker.hit(matchingHit(ALWAYS, 100)));
        // First hit belongs to the entire ritual, including later stages and different enemies.
        SpellBehaviour.Tracker ritual = SpellBehaviour.byId("apocalypse_lotus").tracker();
        truth(!ritual.hit(matchingHit(ALWAYS, 1)).isEmpty());
        equal(List.of(), ritual.hit(new SpellBehaviour.HitContext(UUID.randomUUID(), 80, 1, true, false, false, 2)));
        SpellBehaviour.Rule base = new SpellBehaviour.Rule(FIRST_HIT, ALWAYS, HIT, List.of(IGNITE), 1, 0, 0, 0.5);
        SpellBehaviour.Rule numbers = new SpellBehaviour.Rule(FIRST_HIT, ALWAYS, HIT, List.of(IGNITE), 8, 40, 80, 1.5);
        equal(base.fingerprint(), numbers.fingerprint());
        fails(() -> new SpellBehaviour.Rule(FINISH, ALWAYS, HIT, List.of(IGNITE), 1, 0, 0, 1));
        fails(() -> new SpellBehaviour.Rule(FIRST_HIT, ALWAYS, ANCHOR, List.of(HEAL), 1, 0, 0, 1));
    }

    private static void conditions() {
        for (SpellBehaviour.Condition condition : SpellBehaviour.Condition.values()) {
            SpellBehaviour.Plan plan = new SpellBehaviour.Plan(List.of(
                new SpellBehaviour.Rule(EVERY_HIT, condition, HIT, List.of(WEAKEN), 16, 0, 0, 1),
                new SpellBehaviour.Rule(FINISH, ALWAYS, CASTER, List.of(CLEANSE), 1, 0, 0, 1)), List.of(), VISUAL);
            equal(List.of(WEAKEN), actions(plan.tracker().hit(matchingHit(condition, 0))));
            SpellBehaviour.HitContext miss = new SpellBehaviour.HitContext(TARGET, 0, 1,
                condition == AIRBORNE, false, false, condition == CLOSE ? 7 : 2);
            if (condition != ALWAYS && condition != HAS_HIT) equal(List.of(), actions(plan.tracker().hit(miss)));
        }
        // A miss cannot award the end-of-siphon benefit: an accepted hit must exist first.
        var missedDrain = SpellBehaviour.byId("fire_drain").tracker();
        equal(List.of(), missedDrain.finish(1));
        var landedDrain = SpellBehaviour.byId("fire_drain").tracker();
        landedDrain.hit(matchingHit(ALWAYS, 0));
        truth(actions(landedDrain.finish(1)).contains(ABSORB));
        // A failed condition does not burn that rule's activation budget.
        SpellBehaviour.Plan plan = new SpellBehaviour.Plan(List.of(
            new SpellBehaviour.Rule(EVERY_HIT, WET, HIT, List.of(CHILL), 1, 0, 0, 1),
            new SpellBehaviour.Rule(FINISH, ALWAYS, CASTER, List.of(CLEANSE), 1, 0, 0, 1)), List.of(), VISUAL);
        SpellBehaviour.Tracker tracker = plan.tracker();
        equal(List.of(), tracker.hit(matchingHit(ALWAYS, 0)));
        equal(List.of(CHILL), actions(tracker.hit(matchingHit(WET, 1))));
        equal(List.of(), tracker.hit(matchingHit(WET, 2)));
    }

    private static void visualPaths() {
        for (SpellGeometry.Shape shape : SpellGeometry.Shape.values()) {
            for (SpellGeometry.Motion motion : SpellGeometry.Motion.values()) {
                SpellGeometry.Profile profile = new SpellGeometry.Profile(shape, shape, shape, shape, motion);
                List<SpellGeometry.Point> points = SpellGeometry.sample(profile, SpellGeometry.Phase.CAST, 17, 4, 1000);
                equal(64, points.size());
                for (SpellGeometry.Point point : points) {
                    truth(Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z()));
                    truth(Math.abs(point.x()) <= 8 && Math.abs(point.y()) <= 10 && Math.abs(point.z()) <= 8);
                }
                if (motion != SpellGeometry.Motion.STILL)
                    truth(!SpellGeometry.sample(profile, SpellGeometry.Phase.CAST, 0, 4, 32)
                        .equals(SpellGeometry.sample(profile, SpellGeometry.Phase.CAST, 17, 4, 32)));
            }
        }
        equal(List.of(), SpellGeometry.sample(VISUAL, SpellGeometry.Phase.CAST, 0, 1, -1));
        fails(() -> SpellGeometry.sample(VISUAL, SpellGeometry.Phase.CAST, Double.NaN, 1, 10));
        fails(() -> SpellGeometry.sample(VISUAL, SpellGeometry.Phase.CAST, 0, -1, 10));
        fails(() -> SpellGeometry.sample(VISUAL, SpellGeometry.Phase.CAST, 0, 1, 10).clear());
    }

    private static String visualTrace(SpellGeometry.Profile profile) {
        StringBuilder result = new StringBuilder();
        for (SpellGeometry.Phase phase : SpellGeometry.Phase.values()) for (int age : new int[] {0, 9})
            for (SpellGeometry.Point p : SpellGeometry.sample(profile, phase, age, 2, 24))
                result.append(String.format(Locale.ROOT, "%.4f,%.4f,%.4f;", p.x(), p.y(), p.z()));
        return result.toString();
    }

    private static String behaviourTrace(SpellBehaviour.Plan plan) {
        StringBuilder result = new StringBuilder();
        // A miss is an observable lifecycle too: a successful-siphon reward must differ
        // from an unconditional movement/ward finale even when both can finish after hits.
        SpellBehaviour.Tracker missed = plan.tracker();
        result.append("miss:").append(missed.pulse(0).stream().map(a -> a.action() + "/" + a.target()).toList());
        result.append(missed.finish(100).stream().map(a -> a.action() + "/" + a.target()).toList());
        // Separate casts exercise each physical condition and lifecycle; omit all scalar powers/delays.
        for (SpellBehaviour.Condition condition : SpellBehaviour.Condition.values()) {
            SpellBehaviour.Tracker tracker = plan.tracker();
            result.append(condition).append(':');
            for (int i = 0; i < 5; i++) {
                List<SpellBehaviour.Activation> actual = tracker.hit(matchingHit(condition, i * 20));
                result.append(actual.stream().map(a -> a.action() + "/" + a.target()).toList());
                result.append(tracker.pulse(i * 20).stream().map(a -> a.action() + "/" + a.target()).toList());
            }
            result.append(tracker.finish(100).stream().map(a -> a.action() + "/" + a.target()).toList());
        }
        // The world executes ritual openers/closers even where they don't require an earlier hit.
        result.append(plan.stages().stream().map(s -> s.mechanic() + "/" + s.opening() + "/" + s.closing()).toList());
        return result.toString();
    }

    private static void boundedState() {
        SpellBehaviour.Tracker tracker = SpellBehaviour.byId("fire_bolt").tracker();
        for (int i = 0; i < 1000; i++) tracker.hit(new SpellBehaviour.HitContext(new UUID(1, i), i, 1, true, false, false, 2));
        equal(128, tracker.trackedTargets());
        equal(List.of(), tracker.hit(new SpellBehaviour.HitContext(new UUID(2, 1), 1001, 1, true, false, false, 2)));
        fails(() -> new SpellBehaviour.HitContext(TARGET, Integer.MAX_VALUE, 1, true, false, false, 1));
        equal(List.of(), tracker.pulse(Integer.MAX_VALUE));
        // Targets whose condition fails still occupy the same bounded hit/cooldown table.
        SpellBehaviour.Tracker perTarget = isolated(new SpellBehaviour.Rule(EVERY_HIT, WET, HIT,
            List.of(CHILL), 4, 10, 0, 1)).tracker();
        for (int i = 1; i <= 1000; i++) equal(List.of(), perTarget.hit(targetHit(i, 0)));
        equal(128, perTarget.trackedTargets());
        equal(List.of(), perTarget.hit(new SpellBehaviour.HitContext(new UUID(0, 129), 1, 1, true, true, false, 2)));
        equal(List.of(CHILL), actions(perTarget.hit(new SpellBehaviour.HitContext(new UUID(0, 1), 1, 1, true, true, false, 2))));
        equal(List.of(CHILL), actions(perTarget.hit(new SpellBehaviour.HitContext(new UUID(0, 128), 1, 1, true, true, false, 2))));
        equal(128, perTarget.trackedTargets());
    }
    private static List<SpellBehaviour.Action> actions(List<SpellBehaviour.Activation> result) { return result.stream().map(SpellBehaviour.Activation::action).toList(); }
    private static void truth(boolean condition) { assertions++; if (!condition) throw new AssertionError("Behaviour assertion " + assertions); }
    private static void equal(Object expected, Object actual) { assertions++; if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual + " (" + assertions + ")"); }
    private static void fails(Runnable task) { assertions++; try { task.run(); } catch (IllegalArgumentException | UnsupportedOperationException expected) { return; } throw new AssertionError("Expected rejection"); }
}
