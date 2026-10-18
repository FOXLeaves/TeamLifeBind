package com.teamlifebind.common;

import java.util.List;
import java.util.Objects;

/** Immutable bilingual gameplay contract. Mechanic order is significant for composite spells. */
public record SpellDefinition(String id, String zhName, String enName, MagicSchool school,
                              int tier, int manaCost, int cooldownTicks, int castTicks,
                              double range, double radius, double damage,
                              List<MagicMechanic> mechanics,
                              String zhDescription, String enDescription) {
    public SpellDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(zhName);
        Objects.requireNonNull(enName);
        Objects.requireNonNull(school);
        Objects.requireNonNull(zhDescription);
        Objects.requireNonNull(enDescription);
        mechanics = List.copyOf(mechanics);
        if (!id.matches("[a-z][a-z0-9_]*") || zhName.isBlank() || enName.isBlank()
            || zhDescription.isBlank() || enDescription.isBlank() || tier < 1 || tier > 8
            || manaCost < 1 || manaCost > 300 || cooldownTicks < 1 || castTicks < 0
            || !Double.isFinite(range) || range <= 0 || !Double.isFinite(radius) || radius <= 0
            || !Double.isFinite(damage) || damage < 0 || mechanics.isEmpty()
            || mechanics.size() > 5 || mechanics.stream().distinct().count() != mechanics.size()) {
            throw new IllegalArgumentException("Invalid spell definition: " + id);
        }
    }

    public MagicMechanic primaryMechanic() { return mechanics.getFirst(); }
    public boolean superTier() { return tier == 8; }

    public SpellBehaviour.Plan behaviour() { return SpellBehaviour.byId(id); }

    /** Distinct trigger/action structure without names, IDs, schools, colours or balance numbers. */
    public String mechanicFingerprint() {
        SpellBehaviour.Plan plan = behaviour();
        return plan == null ? mechanics.toString() : plan.fingerprint();
    }
}
