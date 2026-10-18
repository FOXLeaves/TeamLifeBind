package com.teamlifebind.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded, platform-neutral animated particle paths; adapters supply origin and orientation. */
public final class SpellGeometry {
    public enum Shape { CIRCLE, TRIANGLE, SQUARE, PENTAGRAM, HEXAGRAM, ROSE, SPIRAL, DOUBLE_HELIX, FAN, CROWN, WINGS, CONSTELLATION }
    public enum Motion { STILL, SPIN, COUNTER_ROTATE, PULSE, RISE, FOLD, ORBIT, INWARD }
    public enum Phase { CAST, TRAVEL, IMPACT, FINISH }
    public record Point(double x, double y, double z) { }
    public record Profile(Shape cast, Shape travel, Shape impact, Shape finish, Motion motion) {
        public Profile { Objects.requireNonNull(cast); Objects.requireNonNull(travel); Objects.requireNonNull(impact); Objects.requireNonNull(finish); Objects.requireNonNull(motion); }
        public String fingerprint() { return cast + "/" + travel + "/" + impact + "/" + finish + "/" + motion; }
    }

    private SpellGeometry() { }

    /** Time is server ticks. Radius is clamped to 16 blocks; no call emits more than 64 points. */
    public static List<Point> sample(Profile profile, Phase phase, double time, double radius, int requestedPoints) {
        Objects.requireNonNull(profile); Objects.requireNonNull(phase);
        if (!Double.isFinite(time) || !Double.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Invalid visual sample");
        int count = Math.min(64, Math.max(0, requestedPoints));
        if (count == 0) return List.of();
        double scale = Math.min(16, radius);
        double seconds = time / 20.0;
        Shape shape = switch (phase) { case CAST -> profile.cast(); case TRAVEL -> profile.travel(); case IMPACT -> profile.impact(); case FINISH -> profile.finish(); };
        List<Point> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double progress = i / (double) count;
            double angle = progress * Math.PI * 2;
            double x; double y = 0; double z;
            switch (shape) {
                case CIRCLE -> { x = Math.cos(angle); z = Math.sin(angle); }
                case TRIANGLE -> { Point p = polygon(progress, 3, 1); x = p.x(); z = p.z(); }
                case SQUARE -> { Point p = polygon(progress, 4, 1); x = p.x(); z = p.z(); }
                case PENTAGRAM -> { Point p = polygon(progress, 5, 2); x = p.x(); z = p.z(); }
                case HEXAGRAM -> {
                    double half = (progress * 2) % 1;
                    Point p = polygon(half, 3, 1);
                    double rotation = progress < 0.5 ? 0 : Math.PI;
                    x = p.x() * Math.cos(rotation) - p.z() * Math.sin(rotation);
                    z = p.x() * Math.sin(rotation) + p.z() * Math.cos(rotation);
                }
                case ROSE -> { double r = Math.cos(3 * angle); x = r * Math.cos(angle); z = r * Math.sin(angle); }
                case SPIRAL -> { double a = angle * 2; x = progress * Math.cos(a); z = progress * Math.sin(a); y = 0.25 * progress; }
                case DOUBLE_HELIX -> {
                    double a = angle * 2 + (i % 2 == 0 ? 0 : Math.PI);
                    x = 0.6 * Math.cos(a); z = 0.6 * Math.sin(a); y = progress * 2 - 1;
                }
                case FAN -> { double a = -Math.PI / 3 + progress * Math.PI * 2 / 3; x = Math.sin(a); z = Math.cos(a); y = 0.3 * Math.sin(angle); }
                case CROWN -> { x = Math.cos(angle); z = Math.sin(angle); y = 0.2 + 0.65 * Math.abs(Math.sin(5 * angle)); }
                case WINGS -> {
                    double side = progress < 0.5 ? -1 : 1;
                    double u = (progress * 2) % 1;
                    x = side * (0.2 + 1.2 * u); z = 0.7 * Math.sin(u * Math.PI); y = 0.5 * Math.sin(u * Math.PI * 2);
                }
                case CONSTELLATION -> {
                    double star = Math.floor(progress * 7);
                    double a = star * Math.PI * 2 / 7;
                    double r = star % 2 == 0 ? 1 : 0.5;
                    x = r * Math.cos(a); z = r * Math.sin(a); y = 0.2 * Math.sin(star);
                }
                default -> throw new IllegalStateException("Unknown shape");
            }
            double rotation = 0;
            double multiplier = 1;
            switch (profile.motion()) {
                case STILL -> { }
                case SPIN -> rotation = seconds;
                case COUNTER_ROTATE -> rotation = (i % 2 == 0 ? seconds : -seconds);
                case PULSE -> multiplier = 0.75 + 0.25 * Math.cos(seconds * 3);
                case RISE -> y += (seconds * 0.5 + progress) % 1.5;
                case FOLD -> { x *= Math.cos(seconds * 2); y += z * Math.sin(seconds * 2); }
                case ORBIT -> { x += 0.35 * Math.cos(seconds * 2); z += 0.35 * Math.sin(seconds * 2); rotation = -seconds; }
                case INWARD -> multiplier = 1 - ((seconds * 0.4 + progress * 0.3) % 0.8);
            }
            double outX = (x * Math.cos(rotation) - z * Math.sin(rotation)) * multiplier * scale;
            double outZ = (x * Math.sin(rotation) + z * Math.cos(rotation)) * multiplier * scale;
            points.add(new Point(outX, y * scale, outZ));
        }
        return List.copyOf(points);
    }

    private static Point polygon(double progress, int sides, int skip) {
        double section = progress * sides;
        int edge = (int) Math.floor(section);
        double along = section - edge;
        double start = edge * skip * Math.PI * 2 / sides;
        double end = (edge + 1) * skip * Math.PI * 2 / sides;
        return new Point(Math.cos(start) * (1 - along) + Math.cos(end) * along, 0,
            Math.sin(start) * (1 - along) + Math.sin(end) * along);
    }
}
