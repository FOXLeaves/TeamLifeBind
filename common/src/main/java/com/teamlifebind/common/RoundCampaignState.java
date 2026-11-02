package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Round-local competition rules. Adapters own entities, inventories, dimensions and effects;
 * this class authorizes each wipe, reconnect penalty, objective and reward exactly once.
 * Every tick argument is the same monotonically advancing server tick, never wall-clock time.
 */
public final class RoundCampaignState {
    public static final long COMBAT_TICKS = 200L;
    public static final long RECONNECT_GRACE_TICKS = 600L;
    public static final long DRAGON_DAMAGE_WINDOW_TICKS = 1_200L;
    public static final long SILENCE_TICKS = 400L;
    public static final long SILENCE_COOLDOWN_TICKS = 1_800L;
    public static final long SCOUT_TICKS = 100L;
    public static final long SCOUT_COOLDOWN_TICKS = 900L;
    public static final double SCOUT_RADIUS = 48.0D;
    public static final double CONTROL_POINT_RADIUS = 6.0D;
    public static final double CONTROL_POINT_HEIGHT = 4.0D;
    public static final long CONTROL_CAPTURE_TICKS = 900L;
    public static final long CONTROL_REWARD_PERIOD_TICKS = 6_000L;
    public static final long BORDER_WARNING_TICKS = 28_800L;
    public static final long BORDER_START_TICKS = 30_000L;
    public static final long BORDER_SHRINK_TICKS = 12_000L;
    public static final double BORDER_START_RADIUS = 750.0D;
    public static final double BORDER_FINAL_RADIUS = 250.0D;

    private CampaignRules rules = CampaignRules.defaults();
    private Map<UUID, Integer> assignments = Map.of();
    private final Map<Integer, Integer> credits = new HashMap<>();
    private final Set<Integer> eliminated = new HashSet<>();
    private final Map<Integer, Long> lastWipes = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();
    private final Map<UUID, Disconnect> disconnected = new HashMap<>();
    private final Set<UUID> connected = new HashSet<>();
    private final Map<Integer, Long> whollyOfflineAt = new HashMap<>();
    private final Set<Integer> forfeitTeams = new HashSet<>();
    private final Set<UUID> pendingRespawns = new HashSet<>();
    private final Map<UUID, List<DragonDamage>> dragonDamage = new HashMap<>();
    private final Map<Integer, Long> silencedAt = new HashMap<>();
    private final Set<Integer> pendingPenaltyTeams = new HashSet<>();
    private final TeamTotemCooldown silenceCooldown = new TeamTotemCooldown();
    private final TeamTotemCooldown scoutCooldown = new TeamTotemCooldown();
    private final Map<Integer, ScoutPulse> scouts = new HashMap<>();
    private final Map<Integer, Set<StageTask>> tasks = new HashMap<>();
    private final Set<Integer> rewardedThisCycle = new HashSet<>();
    private boolean active;
    private long startTick;
    private long eliminationTick = Long.MIN_VALUE;
    private Victory result;
    private long controlTick;
    private long controlCycle = -1L;
    private Integer controlTeam;
    private long controlProgress;
    private boolean controlContested;
    private boolean controlPresent;
    private boolean borderWarned;

    public synchronized void start(Map<UUID, Integer> playerTeams, CampaignRules campaignRules, long tick) {
        if (playerTeams == null || playerTeams.isEmpty() || campaignRules == null
            || playerTeams.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null || entry.getValue() < 1)) {
            throw new IllegalArgumentException("A round needs non-null players, positive teams and rules");
        }
        clear();
        rules = campaignRules;
        assignments = Map.copyOf(playerTeams);
        connected.addAll(assignments.keySet());
        new HashSet<>(assignments.values()).forEach(team -> credits.put(team, rules.respawnCredits()));
        active = true;
        startTick = tick;
        controlTick = tick;
    }

    public synchronized CampaignRules rules() {
        return rules;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized Integer teamForPlayer(UUID playerId) {
        return assignments.get(playerId);
    }

    /** Persistent survival accepts newcomers without resetting a running team's tasks or cooldowns. */
    public synchronized boolean addCooperativePlayer(UUID playerId, long tick) {
        if (!active || rules.playMode() != PlayMode.SUR || playerId == null || tick < startTick
            || assignments.containsKey(playerId) || !eligible(1)) return false;
        Map<UUID, Integer> expanded = new HashMap<>(assignments);
        expanded.put(playerId, 1);
        assignments = Map.copyOf(expanded);
        connected.add(playerId);
        whollyOfflineAt.remove(1);
        return true;
    }

    public synchronized int remainingRespawnCredits(int team) {
        return credits.getOrDefault(team, 0);
    }

    public synchronized boolean isEliminated(int team) {
        return eliminated.contains(team);
    }

    public synchronized Set<Integer> eligibleTeams() {
        Set<Integer> eligible = new TreeSet<>(credits.keySet());
        eligible.removeAll(eliminated);
        return Set.copyOf(eligible);
    }

    /** Invoke after the adapter's existing team-wipe guard, once for the whole team. */
    public synchronized WipeResult applyWipe(int team, boolean permanent, long tick) {
        int remaining = remainingRespawnCredits(team);
        if (!active || !credits.containsKey(team) || eliminated.contains(team)
            || Long.valueOf(tick).equals(lastWipes.get(team))) {
            return new WipeResult(false, team, remaining, eliminated.contains(team));
        }
        lastWipes.put(team, tick);
        pendingPenaltyTeams.remove(team);
        forfeitTeams.remove(team);
        boolean exhausted = rules.limitedRespawns() && remaining == 0;
        boolean removed = permanent || exhausted;
        if (removed) {
            eliminated.add(team);
            eliminationTick = tick;
        } else if (rules.limitedRespawns()) {
            credits.put(team, --remaining);
        }
        for (Map.Entry<UUID, Integer> member : assignments.entrySet()) {
            if (member.getValue() == team && removed) {
                pendingRespawns.remove(member.getKey());
            }
            if (member.getValue() == team && disconnected.containsKey(member.getKey())) {
                if (removed) {
                    pendingRespawns.remove(member.getKey());
                } else {
                    pendingRespawns.add(member.getKey());
                }
                Disconnect offline = disconnected.get(member.getKey());
                disconnected.put(member.getKey(), new Disconnect(offline.startTick(), false, true));
            }
        }
        return new WipeResult(true, team, remaining, removed);
    }

    /** Poll after all deaths in a server tick. Nothing settles on the elimination tick itself. */
    public synchronized Victory pollVictory(long tick) {
        if (result != null || !active
            || eliminationTick == Long.MIN_VALUE || tick <= eliminationTick) {
            return result;
        }
        Set<Integer> survivors = eligibleTeams();
        if (rules.playMode().cooperative()) {
            if (survivors.isEmpty()) result = new Victory(null, true);
            return result;
        }
        if (!rules.limitedRespawns()) return null;
        if (survivors.size() > 1) {
            return null;
        }
        result = new Victory(survivors.isEmpty() ? null : survivors.iterator().next(), survivors.isEmpty());
        return result;
    }

    public synchronized void recordDamage(UUID playerId, long tick) {
        Integer team = assignments.get(playerId);
        if (active && team != null && !eliminated.contains(team)) {
            lastCombat.put(playerId, tick);
        }
    }

    public synchronized boolean inCombat(UUID playerId, long tick) {
        Long damagedAt = lastCombat.get(playerId);
        return damagedAt != null && tick >= damagedAt && elapsed(damagedAt, tick) < COMBAT_TICKS;
    }

    /** The boolean lets adapters include outgoing attacks as well as recorded incoming damage. */
    public synchronized void disconnect(UUID playerId, long tick, boolean inCombat) {
        Integer team = assignments.get(playerId);
        if (active && team != null) {
            disconnected.putIfAbsent(playerId, new Disconnect(tick, inCombat || inCombat(playerId, tick), false));
            connected.remove(playerId);
            if (connected.stream().noneMatch(member -> team.equals(assignments.get(member)))) {
                whollyOfflineAt.putIfAbsent(team, tick);
            }
        }
    }

    /** Consumes deadlines once per team; callers route these teams through their normal wipe path. */
    public synchronized Set<Integer> expiredDisconnectTeams(long tick) {
        Set<Integer> due = new TreeSet<>();
        for (Map.Entry<UUID, Disconnect> entry : disconnected.entrySet()) {
            Disconnect offline = entry.getValue();
            Integer team = assignments.get(entry.getKey());
            if (offline.penalty() && !offline.consumed() && team != null && !eliminated.contains(team)
                && elapsed(offline.startTick(), tick) >= RECONNECT_GRACE_TICKS) {
                due.add(team);
            }
        }
        if (rules.limitedRespawns()) {
            for (Map.Entry<Integer, Long> offline : whollyOfflineAt.entrySet()) {
                if (eligible(offline.getKey()) && !pendingPenaltyTeams.contains(offline.getKey())
                    && elapsed(offline.getValue(), tick) >= RECONNECT_GRACE_TICKS) {
                    due.add(offline.getKey());
                    forfeitTeams.add(offline.getKey());
                }
            }
        }
        if (!due.isEmpty()) {
            pendingPenaltyTeams.addAll(due);
            disconnected.replaceAll((playerId, offline) -> due.contains(assignments.get(playerId))
                ? new Disconnect(offline.startTick(), false, true) : offline);
        }
        return Set.copyOf(due);
    }

    /** Query the event before applyWipe: a wholly offline limited-mode team permanently forfeits. */
    public synchronized boolean disconnectForfeit(int team) {
        return forfeitTeams.contains(team);
    }

    /** A late join can discover an unpolled timeout; apply its requested wipe before restoring this player. */
    public synchronized ReconnectResult join(UUID playerId, long tick) {
        Integer team = assignments.get(playerId);
        if (!active || team == null) {
            return new ReconnectResult(null, false, false, false);
        }
        Disconnect offline = disconnected.remove(playerId);
        Long teamOfflineAt = whollyOfflineAt.get(team);
        boolean forfeitDue = rules.limitedRespawns() && teamOfflineAt != null
            && elapsed(teamOfflineAt, tick) >= RECONNECT_GRACE_TICKS && !eliminated.contains(team);
        if (forfeitDue) {
            forfeitTeams.add(team);
        }
        boolean penaltyDue = !eliminated.contains(team) && (pendingPenaltyTeams.contains(team)
            || forfeitDue
            || offline != null && offline.penalty() && !offline.consumed()
                && elapsed(offline.startTick(), tick) >= RECONNECT_GRACE_TICKS);
        boolean pending = pendingRespawns.remove(playerId);
        connected.add(playerId);
        whollyOfflineAt.remove(team);
        if (penaltyDue) {
            pendingPenaltyTeams.add(team);
            pendingRespawns.add(playerId);
            disconnected.replaceAll((member, other) -> team.equals(assignments.get(member))
                ? new Disconnect(other.startTick(), false, true) : other);
        }
        return new ReconnectResult(team, pending, eliminated.contains(team), penaltyDue);
    }

    public synchronized long reconnectGraceRemainingTicks(UUID playerId, long tick) {
        Disconnect offline = disconnected.get(playerId);
        return offline == null || !offline.penalty() || offline.consumed() ? 0L
            : remaining(offline.startTick(), RECONNECT_GRACE_TICKS, tick);
    }

    /** Call only after an adapter has actually queued the corresponding respawn. */
    public synchronized boolean acknowledgePendingRespawn(UUID playerId) {
        return pendingRespawns.remove(playerId);
    }

    /** Actual post-mitigation HP damage, capped by pre-hit dragon health; absorption does not count. */
    public synchronized void recordDragonDamage(int team, UUID dragonId, double actualDamage, double healthBefore, long tick) {
        if (!eligible(team) || dragonId == null || !Double.isFinite(actualDamage) || !Double.isFinite(healthBefore)
            || actualDamage <= 0.0D || healthBefore <= 0.0D) {
            return;
        }
        List<DragonDamage> damage = dragonDamage.computeIfAbsent(dragonId, ignored -> new ArrayList<>());
        pruneDamage(damage, tick);
        damage.add(new DragonDamage(team, Math.min(actualDamage, healthBefore), tick));
    }

    public synchronized Map<Integer, Double> dragonContributions(UUID dragonId, long tick) {
        List<DragonDamage> damage = dragonDamage.get(dragonId);
        if (damage == null) {
            return Map.of();
        }
        pruneDamage(damage, tick);
        Map<Integer, Double> totals = new HashMap<>();
        for (DragonDamage hit : damage) {
            if (eligible(hit.team()) && tick >= hit.tick()) {
                totals.merge(hit.team(), hit.damage(), Double::sum);
            }
        }
        return Map.copyOf(totals);
    }

    public synchronized Integer chooseDragonWinner(UUID dragonId, Integer lastHitTeam, long tick) {
        if (!active || rules.playMode() != PlayMode.PVP) {
            return null;
        }
        Integer validLastHit = lastHitTeam != null && eligible(lastHitTeam) ? lastHitTeam : null;
        if (rules.dragonVictoryMode() == DragonVictoryMode.LAST_HIT) {
            return validLastHit;
        }
        Map<Integer, Double> totals = dragonContributions(dragonId, tick);
        if (totals.isEmpty()) {
            return validLastHit;
        }
        return totals.keySet().stream().min(Comparator.<Integer>comparingDouble(totals::get).reversed()
            .thenComparingInt(team -> team.equals(validLastHit) ? 0 : 1).thenComparingInt(Integer::intValue)).orElse(validLastHit);
    }

    public synchronized boolean tryActivateSilence(UUID playerId, long tick) {
        Integer team = assignments.get(playerId);
        if (team == null || !eligible(team) || !silenceCooldown.tryAcquire(team, tick, SILENCE_COOLDOWN_TICKS)) {
            return false;
        }
        silencedAt.put(team, tick);
        return true;
    }

    public synchronized boolean isSilenced(UUID playerId, long tick) {
        Integer team = assignments.get(playerId);
        Long started = silencedAt.get(team);
        if (started == null || team == null || !eligible(team)) {
            return false;
        }
        if (remaining(started, SILENCE_TICKS, tick) == 0L) {
            silencedAt.remove(team);
            return false;
        }
        return true;
    }

    public synchronized long silenceCooldownRemainingTicks(int team, long tick) {
        return silenceCooldown.remainingTicks(team, tick);
    }

    public synchronized ScoutPulse tryScout(UUID playerId, String dimension, double x, double y, double z, long tick) {
        Integer team = assignments.get(playerId);
        if (team == null || !eligible(team) || dimension == null || dimension.isBlank()
            || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !scoutCooldown.tryAcquire(team, tick, SCOUT_COOLDOWN_TICKS)) {
            return null;
        }
        ScoutPulse pulse = new ScoutPulse(playerId, team, dimension, x, y, z, tick);
        scouts.put(team, pulse);
        return pulse;
    }

    public synchronized List<ScoutPulse> activeScouts(long tick) {
        scouts.values().removeIf(pulse -> !eligible(pulse.team()) || remaining(pulse.startTick(), SCOUT_TICKS, tick) == 0L);
        return List.copyOf(scouts.values());
    }

    public synchronized long scoutCooldownRemainingTicks(int team, long tick) {
        return scoutCooldown.remainingTicks(team, tick);
    }

    /** Call every server tick with teams physically inside the configured point in its dimension. */
    public synchronized ControlPointReward tickControlPoint(Set<Integer> occupyingTeams, long tick) {
        if (!active || !rules.controlPointEnabled() || tick < controlTick) {
            return null;
        }
        long delta = elapsed(controlTick, tick);
        controlTick = tick;
        long cycle = elapsed(startTick, tick) / CONTROL_REWARD_PERIOD_TICKS;
        if (cycle != controlCycle) {
            controlCycle = cycle;
            rewardedThisCycle.clear();
            controlProgress = 0L;
            delta = 0L;
        }
        Set<Integer> occupants = new HashSet<>();
        if (occupyingTeams != null) {
            occupyingTeams.stream().filter(team -> team != null && eligible(team)).forEach(occupants::add);
        }
        controlPresent = !occupants.isEmpty();
        controlContested = occupants.size() > 1;
        if (occupants.size() != 1) {
            return null;
        }
        int occupant = occupants.iterator().next();
        if (!Integer.valueOf(occupant).equals(controlTeam)) {
            controlTeam = occupant;
            controlProgress = 0L;
            delta = 0L;
        }
        controlProgress = Math.min(CONTROL_CAPTURE_TICKS, controlProgress + delta);
        if (controlProgress < CONTROL_CAPTURE_TICKS || !rewardedThisCycle.add(occupant)) {
            return null;
        }
        controlProgress = 0L;
        return new ControlPointReward(occupant, cycle);
    }

    public synchronized ControlPointProgress controlPointProgress() {
        return new ControlPointProgress(controlTeam, controlProgress, controlContested, controlPresent);
    }

    public synchronized boolean completeTask(int team, StageTask task) {
        return task != null && eligible(team) && tasks.computeIfAbsent(team, ignored -> new HashSet<>()).add(task);
    }

    public synchronized Set<StageTask> completedTasks(int team) {
        return Set.copyOf(tasks.getOrDefault(team, Set.of()));
    }

    public synchronized boolean borderWarningDue(long tick) {
        if (!active || !rules.borderEnabled() || borderWarned || elapsed(startTick, tick) < BORDER_WARNING_TICKS) {
            return false;
        }
        borderWarned = true;
        return true;
    }

    public synchronized double borderRadius(long tick) {
        long elapsed = elapsed(startTick, tick);
        if (!active || !rules.borderEnabled() || elapsed <= BORDER_START_TICKS) {
            return BORDER_START_RADIUS;
        }
        double fraction = Math.min(1.0D, (elapsed - BORDER_START_TICKS) / (double) BORDER_SHRINK_TICKS);
        return BORDER_START_RADIUS + (BORDER_FINAL_RADIUS - BORDER_START_RADIUS) * fraction;
    }

    public synchronized void clear() {
        active = false;
        assignments = Map.of();
        credits.clear();
        eliminated.clear();
        lastWipes.clear();
        lastCombat.clear();
        disconnected.clear();
        connected.clear();
        whollyOfflineAt.clear();
        forfeitTeams.clear();
        pendingRespawns.clear();
        pendingPenaltyTeams.clear();
        dragonDamage.clear();
        silencedAt.clear();
        silenceCooldown.clear();
        scoutCooldown.clear();
        scouts.clear();
        tasks.clear();
        rewardedThisCycle.clear();
        rules = CampaignRules.defaults();
        startTick = 0L;
        eliminationTick = Long.MIN_VALUE;
        result = null;
        controlTick = 0L;
        controlCycle = -1L;
        controlTeam = null;
        controlProgress = 0L;
        controlContested = false;
        controlPresent = false;
        borderWarned = false;
    }

    private boolean eligible(int team) {
        return active && credits.containsKey(team) && !eliminated.contains(team);
    }

    private static void pruneDamage(List<DragonDamage> damage, long tick) {
        damage.removeIf(hit -> tick >= hit.tick() && elapsed(hit.tick(), tick) >= DRAGON_DAMAGE_WINDOW_TICKS);
    }

    private static long elapsed(long start, long tick) {
        if (tick < start) {
            return 0L;
        }
        long difference = tick - start;
        return difference < 0L ? Long.MAX_VALUE : difference;
    }

    private static long remaining(long start, long duration, long tick) {
        return Math.max(0L, duration - Math.min(duration, elapsed(start, tick)));
    }

    public record WipeResult(boolean applied, int team, int remainingRespawnCredits, boolean eliminated) {
    }

    public record Victory(Integer winnerTeam, boolean draw) {
    }

    public record ReconnectResult(Integer team, boolean pendingRespawn, boolean locked, boolean penaltyDue) {
    }

    public record ScoutPulse(UUID playerId, int team, String dimension, double x, double y, double z, long startTick) {
    }

    public record ControlPointReward(int team, long cycle) {
    }

    public record ControlPointProgress(Integer team, long ticks, boolean contested, boolean present) {
    }

    public enum StageTask {
        CAPTURE_POINT,
        CLAIM_SUPPLY,
        ENTER_END
    }

    private record Disconnect(long startTick, boolean penalty, boolean consumed) {
    }

    private record DragonDamage(int team, double damage, long tick) {
    }
}
