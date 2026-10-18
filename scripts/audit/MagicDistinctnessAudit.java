import com.teamlifebind.common.*;
import java.util.*;

/** Measures delivered configuration and real Tracker activations; fingerprints are not acceptance criteria. */
public final class MagicDistinctnessAudit {
    public static void main(String[] args) {
        List<SpellDefinition> ordinary = SpellCatalogue.all().stream().filter(s -> !s.superTier()).toList();
        Set<MagicMechanic> instant = Set.of(MagicMechanic.BEAM, MagicMechanic.CONE, MagicMechanic.CHAIN, MagicMechanic.DASH);
        for (MagicMechanic mechanic : MagicMechanic.values()) {
            List<SpellDefinition> family = ordinary.stream().filter(s -> s.primaryMechanic() == mechanic).toList();
            System.out.println("FAMILY\t" + mechanic + "\t" + family.size() + "\t"
                + family.stream().map(s -> s.behaviour().visual().travel()).distinct().count() + "\t"
                + family.stream().map(s -> s.behaviour().visual().motion()).distinct().count() + "\t"
                + family.stream().map(s -> List.of(s.range(), s.radius(), s.damage(), (double)s.castTicks(), (double)s.cooldownTicks())).distinct().count());
        }
        int suppressed = 0, checkedTargets = 0, globalIntervals = 0, pulse = 0, mobBlind = 0;
        for (SpellDefinition spell : ordinary) {
            boolean playerOnly = false;
            for (SpellBehaviour.Rule rule : spell.behaviour().rules()) {
                if (rule.event() == SpellBehaviour.Event.PULSE) pulse++;
                if (rule.actions().stream().anyMatch(a -> a == SpellBehaviour.Action.SILENCE || a == SpellBehaviour.Action.BURN_MANA)) playerOnly = true;
                if (rule.event() != SpellBehaviour.Event.EVERY_HIT || !instant.contains(spell.primaryMechanic())) continue;
                // Self rewards and anchor bursts intentionally share a cast-wide interval.
                if (rule.target() != SpellBehaviour.Target.HIT && rule.target() != SpellBehaviour.Target.LAST_HIT) {
                    globalIntervals++;
                    continue;
                }
                checkedTargets++;
                SpellBehaviour.Rule inert = new SpellBehaviour.Rule(SpellBehaviour.Event.FINISH, SpellBehaviour.Condition.ALWAYS,
                    SpellBehaviour.Target.CASTER, List.of(SpellBehaviour.Action.CLEANSE), 1, 0, 0, 0.1);
                SpellBehaviour.Tracker tracker = new SpellBehaviour.Plan(List.of(rule, inert), List.of(), spell.behaviour().visual()).tracker();
                int applied = 0;
                for (int i = 0; i < 4; i++) {
                    SpellBehaviour.HitContext context = new SpellBehaviour.HitContext(new UUID(0, i + 1), 1,
                        rule.condition() == SpellBehaviour.Condition.WOUNDED ? .2 : 1,
                        rule.condition() != SpellBehaviour.Condition.AIRBORNE,
                        rule.condition() == SpellBehaviour.Condition.WET,
                        rule.condition() == SpellBehaviour.Condition.BURNING,
                        rule.condition() == SpellBehaviour.Condition.FAR ? 8 : 2);
                    if (!tracker.hit(context).isEmpty()) applied++;
                }
                if (applied < Math.min(4, rule.limit())) {
                    suppressed++;
                    System.out.println("SUPPRESSED\t" + spell.id() + "\t" + rule.target() + "\t" + applied + "\t" + Math.min(4, rule.limit()));
                }
            }
            if (playerOnly) mobBlind++;
        }
        System.out.println("METRIC\tordinary_count\t" + ordinary.size());
        System.out.println("METRIC\tordinary_pulse_rules\t" + pulse);
        System.out.println("METRIC\tordinary_spells_with_player_only_actions\t" + mobBlind);
        System.out.println("METRIC\tsame_tick_target_rules_checked\t" + checkedTargets);
        System.out.println("METRIC\tsame_tick_global_interval_rules\t" + globalIntervals);
        System.out.println("METRIC\tsame_tick_target_rules_suppressed\t" + suppressed);
    }
}
