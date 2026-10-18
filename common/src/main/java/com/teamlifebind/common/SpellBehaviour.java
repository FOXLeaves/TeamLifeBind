package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-owned trigger rules. The world adapter executes only the returned activations. */
public final class SpellBehaviour {
    public enum Event { FIRST_HIT, EVERY_HIT, REPEAT_HIT, PULSE, FINISH }
    public enum Condition { ALWAYS, HAS_HIT, WOUNDED, AIRBORNE, GROUNDED, WET, BURNING, CLOSE, FAR }
    public enum Target { HIT, CASTER, ANCHOR, LAST_HIT }
    public enum Action {
        IGNITE("点燃", "ignite"), DOUSE("浸湿熄火", "douse"), CHILL("凝霜", "chill"),
        SLOW("减速", "slow"), ROOT("缠根", "root"), WEAKEN("虚弱", "weaken"),
        BLIND("遮蔽视野", "blind"), REVEAL("显形", "reveal"), LEVITATE("悬浮", "levitate"),
        PUSH("击退", "push"), PULL("牵引", "pull"), LAUNCH("挑空", "launch"),
        SLAM("击落", "slam"), POISON("中毒", "poison"), WITHER("流血凋零", "wither"),
        SILENCE("打断吟唱", "interrupt casting"), BURN_MANA("消耗目标魔力", "burn enemy mana"),
        RESTORE_MANA("回收魔力", "restore mana"), HEAL("回复生命", "heal"),
        ABSORB("吸收护盾", "gain absorption"), RESET_VELOCITY("截断惯性", "halt velocity"),
        CLEANSE("净化负面效果", "cleanse"), ECHO_DAMAGE("延迟追击", "delayed strike"),
        SPLASH_DAMAGE("局部爆发", "area burst");
        private final String zh;
        private final String en;
        Action(String zh, String en) { this.zh = zh; this.en = en; }
        public String zh() { return zh; }
        public String en() { return en; }
    }

    /** Limits are per cast. Hit-target intervals are per target; rewards, areas and lifecycle intervals are per cast. */
    public record Rule(Event event, Condition condition, Target target, List<Action> actions,
                       int limit, int intervalTicks, int delayTicks, double power) {
        public Rule {
            Objects.requireNonNull(event); Objects.requireNonNull(condition); Objects.requireNonNull(target);
            actions = List.copyOf(actions);
            if (actions.isEmpty() || actions.size() > 4 || limit < 1 || limit > 16
                || intervalTicks < 0 || delayTicks < 0 || delayTicks > 100
                || !Double.isFinite(power) || power <= 0 || power > 2) throw new IllegalArgumentException("Invalid spell rule");
            if ((event == Event.PULSE || event == Event.FINISH) && target == Target.HIT)
                throw new IllegalArgumentException("A lifecycle rule cannot reference an absent current hit");
            if (target != Target.CASTER && actions.stream().anyMatch(SpellBehaviour::beneficial))
                throw new IllegalArgumentException("Beneficial actions require the caster target");
        }
        /** Balance numbers and identifiers deliberately do not establish distinct mechanics. */
        public String fingerprint() { return event + "/" + condition + "/" + target + "/" + actions; }
        private boolean perTargetInterval() {
            return (event == Event.EVERY_HIT || event == Event.REPEAT_HIT)
                && (target == Target.HIT || target == Target.LAST_HIT);
        }
    }

    /** Explicit duration and opening/closing actions for a named super-tier ritual stage. */
    public record Stage(MagicMechanic mechanic, int durationTicks, List<Action> opening, List<Action> closing) {
        public Stage {
            Objects.requireNonNull(mechanic);
            opening = List.copyOf(opening); closing = List.copyOf(closing);
            if (durationTicks < 1 || durationTicks > 240 || opening.size() > 3 || closing.size() > 3)
                throw new IllegalArgumentException("Invalid ritual stage");
        }
        public String fingerprint() { return mechanic + "/" + opening + "/" + closing; }
    }

    public record Plan(List<Rule> rules, List<Stage> stages, SpellGeometry.Profile visual) {
        public Plan {
            rules = List.copyOf(rules); stages = List.copyOf(stages); Objects.requireNonNull(visual);
            if (rules.size() < 2 || rules.size() > 6 || stages.size() > 5)
                throw new IllegalArgumentException("Invalid spell behaviour plan");
        }
        public Tracker tracker() { return new Tracker(this); }
        public String fingerprint() {
            return rules.stream().map(Rule::fingerprint).toList() + "|" + stages.stream().map(Stage::fingerprint).toList();
        }
        public String zhDescription() { return describe(true); }
        public String enDescription() { return describe(false); }
        private String describe(boolean zh) {
            List<String> descriptions = new ArrayList<>();
            for (Rule rule : rules) {
                String event = switch (rule.event()) {
                    case FIRST_HIT -> zh ? "首次命中" : "On the first hit";
                    case EVERY_HIT -> zh ? "命中时" : "On a hit";
                    case REPEAT_HIT -> zh ? "重复命中同一目标" : "When hitting the same target again";
                    case PULSE -> zh ? "持续期间（按末次命中状态判断）" : "During the effect (using the last hit snapshot)";
                    case FINISH -> zh ? "效果结束（按末次命中状态判断）" : "When the effect ends (using the last hit snapshot)";
                };
                String condition = switch (rule.condition()) {
                    case ALWAYS -> "";
                    case HAS_HIT -> zh ? "且曾实际命中敌人" : " after actually striking an enemy";
                    case WOUNDED -> zh ? "且目标生命低于一半" : " against a target below half health";
                    case AIRBORNE -> zh ? "且目标悬空" : " against an airborne target";
                    case GROUNDED -> zh ? "且目标着地" : " against a grounded target";
                    case WET -> zh ? "且目标潮湿" : " against a wet target";
                    case BURNING -> zh ? "且目标燃烧" : " against a burning target";
                    case CLOSE -> zh ? "且目标在六格内" : " within six blocks";
                    case FAR -> zh ? "且目标在六格外" : " beyond six blocks";
                };
                String target = switch (rule.target()) {
                    case HIT -> zh ? "对命中者" : "to the struck target";
                    case CASTER -> zh ? "对自身" : "to the caster";
                    case ANCHOR -> zh ? "在记录的落点" : "at the recorded position";
                    case LAST_HIT -> zh ? "对最后命中者" : "to the last struck target";
                };
                String actions = String.join(zh ? "、" : ", ", rule.actions().stream().map(a -> zh ? a.zh() : a.en()).toList());
                descriptions.add(event + condition + (zh ? "：" : ": ") + target + (zh ? "施加" : " apply ") + actions
                    + (rule.delayTicks() > 0 ? (zh ? "（延迟" : " (after ") + rule.delayTicks() / 20.0 + (zh ? "秒）" : " seconds)") : "")
                    + (rule.limit() > 1 ? (zh ? "，整次施法最多" : ", at most ") + rule.limit() + (zh ? "次" : " times per cast") : "")
                    + (rule.intervalTicks() > 0 ? (rule.perTargetInterval()
                        ? (zh ? "，同一目标间隔至少" : ", at least this interval per target: ")
                        : (zh ? "，整次施法共用间隔至少" : ", a shared interval per cast of at least "))
                        + rule.intervalTicks() / 20.0 + (zh ? "秒" : " seconds") : ""));
            }
            return String.join(zh ? "；" : "; ", descriptions) + (zh ? "。" : ".");
        }
    }

    /** Capture statuses before damage, then submit only after a valid, non-cancelled world hit. */
    public record HitContext(UUID targetId, int age, double healthRatio, boolean grounded,
                             boolean wet, boolean burning, double distance) {
        public HitContext {
            Objects.requireNonNull(targetId);
            if (age < 0 || age > 100_000 || !Double.isFinite(healthRatio) || healthRatio < 0 || healthRatio > 1
                || !Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid hit context");
        }
    }
    public record Activation(Action action, Target target, UUID targetId, double power, int delayTicks) { }

    /** One tracker belongs to one released spell, never to a player or an incoming client packet. */
    public static final class Tracker {
        private final Plan plan;
        private final int[] activations;
        private final int[] lastAges;
        // One bounded table owns both hit counts and target cooldowns; no second unbounded UUID cache.
        private final Map<UUID, TargetState> hits = new HashMap<>();
        private boolean finished;
        private int totalHits;
        private int lastPulse = -1;
        private HitContext lastHit;
        private Tracker(Plan plan) {
            this.plan = plan; activations = new int[plan.rules().size()]; lastAges = new int[plan.rules().size()];
            java.util.Arrays.fill(lastAges, -1);
        }
        public List<Activation> hit(HitContext context) {
            if (finished || (!hits.containsKey(context.targetId()) && hits.size() >= 128)) return List.of();
            TargetState state = hits.computeIfAbsent(context.targetId(), ignored -> new TargetState(plan.rules().size()));
            int previous = state.hits++;
            totalHits++; lastHit = context;
            return emit(context.age(), context, totalHits == 1, previous > 0, false, false);
        }
        public List<Activation> pulse(int age) {
            if (finished || age < 0 || age > 100_000 || age <= lastPulse) return List.of();
            lastPulse = age; return emit(age, lastHit, false, false, true, false);
        }
        public List<Activation> finish(int age) {
            if (finished || age < 0 || age > 100_000) return List.of();
            finished = true; return emit(age, lastHit, false, false, false, true);
        }
        public int trackedTargets() { return hits.size(); }
        public boolean finished() { return finished; }
        private List<Activation> emit(int age, HitContext context, boolean first, boolean repeat, boolean pulse, boolean finish) {
            List<Activation> result = new ArrayList<>();
            for (int i = 0; i < plan.rules().size(); i++) {
                Rule rule = plan.rules().get(i);
                boolean eventMatches = switch (rule.event()) {
                    case FIRST_HIT -> first; case EVERY_HIT -> !pulse && !finish;
                    case REPEAT_HIT -> repeat; case PULSE -> pulse && age % Math.max(1, rule.intervalTicks()) == 0;
                    case FINISH -> finish;
                };
                if (!eventMatches || activations[i] >= rule.limit() || !matches(rule.condition(), context)) continue;
                if ((rule.target() == Target.HIT || rule.target() == Target.LAST_HIT) && context == null) continue;
                if (rule.target() == Target.ANCHOR && context == null && rule.actions().contains(Action.ECHO_DAMAGE)) continue;
                int[] intervalAges = rule.perTargetInterval() ? hits.get(context.targetId()).lastAges : lastAges;
                if (intervalAges[i] >= 0 && age - intervalAges[i] < rule.intervalTicks()) continue;
                activations[i]++; intervalAges[i] = age;
                UUID targetId = context == null ? null : context.targetId();
                for (Action action : rule.actions()) result.add(new Activation(action, rule.target(), targetId, rule.power(), rule.delayTicks()));
            }
            return List.copyOf(result);
        }
        private static final class TargetState {
            private int hits;
            private final int[] lastAges;
            private TargetState(int ruleCount) {
                lastAges = new int[ruleCount];
                java.util.Arrays.fill(lastAges, -1);
            }
        }
        private static boolean matches(Condition condition, HitContext hit) {
            if (condition == Condition.ALWAYS) return true;
            if (hit == null) return false;
            return switch (condition) {
                case ALWAYS, HAS_HIT -> true; case WOUNDED -> hit.healthRatio() < 0.5;
                case AIRBORNE -> !hit.grounded(); case GROUNDED -> hit.grounded();
                case WET -> hit.wet(); case BURNING -> hit.burning();
                case CLOSE -> hit.distance() <= 6; case FAR -> hit.distance() > 6;
            };
        }
    }

    private SpellBehaviour() { }
    public static boolean beneficial(Action action) {
        return action == Action.HEAL || action == Action.ABSORB || action == Action.RESTORE_MANA || action == Action.CLEANSE;
    }
    public static Plan byId(String id) { return id == null ? null : SpellBehaviourPlans.ALL.get(id); }
    public static Map<String, Plan> all() { return SpellBehaviourPlans.ALL; }
}
