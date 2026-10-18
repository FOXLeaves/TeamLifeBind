package com.teamlifebind.common;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.teamlifebind.common.SpellBehaviour.Action.*;

/** Samples actual paths and scheduled activations; metadata uniqueness alone is insufficient. */
public final class SpellIdentityRegressionTest {
    private static int assertions;
    private SpellIdentityRegressionTest() { }

    public static int run() {
        assertions = 0;
        for (MagicMechanic mechanic : MagicMechanic.values()) {
            Set<List<SpellGeometry.Point>> paths = new HashSet<>();
            Set<SpellGeometry.Motion> motions = new HashSet<>();
            for (SpellDefinition spell : SpellCatalogue.all()) {
                if (spell.superTier() || spell.primaryMechanic() != mechanic) continue;
                var visual = spell.behaviour().visual();
                paths.add(SpellGeometry.sample(visual, SpellGeometry.Phase.TRAVEL, 17, 2, 32));
                motions.add(visual.motion());
            }
            check(paths.size() >= 9, mechanic + " must not share one travel path across schools");
            check(motions.size() >= 6, mechanic + " needs distinct movement across schools");
        }
        List<List<SpellBehaviour.Action>> domainActions = List.of(
            List.of(IGNITE, SPLASH_DAMAGE), List.of(DOUSE, PUSH), List.of(CHILL, ROOT),
            List.of(LAUNCH, PUSH), List.of(RESET_VELOCITY, SLAM), List.of(REVEAL, SILENCE),
            List.of(REVEAL, WEAKEN), List.of(BLIND, WEAKEN), List.of(BURN_MANA, SPLASH_DAMAGE),
            List.of(ROOT, POISON), List.of(PULL, SLAM), List.of(RESET_VELOCITY, SLOW),
            List.of(WITHER, WEAKEN), List.of(LEVITATE, REVEAL));
        Set<List<SpellBehaviour.Action>> emitted = new HashSet<>();
        for (MagicSchool school : MagicSchool.values()) {
            SpellDefinition spell = SpellCatalogue.byId(school.name().toLowerCase(java.util.Locale.ROOT) + "_domain");
            var tracker = spell.behaviour().tracker();
            List<SpellBehaviour.Action> expected = domainActions.get(school.ordinal());
            // Fields activate without requiring the caster to land an earlier hit.
            for (int age : new int[] {0, 40, 80}) {
                var pulse = tracker.pulse(age);
                check(pulse.stream().map(SpellBehaviour.Activation::action).toList().equals(expected), spell.id() + " pulse " + age);
                check(pulse.stream().allMatch(a -> a.target() == SpellBehaviour.Target.ANCHOR), "field targets its area");
                check(tracker.pulse(age).isEmpty(), "same tick cannot duplicate a field pulse");
                check(tracker.pulse(age + 1).isEmpty(), "field pulses respect the interval");
            }
            check(tracker.pulse(120).isEmpty(), "field has a cast-wide three-pulse budget");
            tracker.finish(130);
            check(tracker.pulse(160).isEmpty(), "finished field cannot resume pulsing");
            check(emitted.add(expected), "each school field applies a different combination");
        }
        System.out.println("School paths and active domain cycles passed (" + assertions + " assertions).");
        return assertions;
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
