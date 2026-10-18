#version 330
#extension GL_ARB_separate_shader_objects : require

// TeamLifeBind analytic magic. Original distance fields, no particle textures,
// point sprites, vanilla particle renderer or borrowed reference shader code.
// std140, 192 bytes. Every vector is aligned to 16 bytes by the Java writer.
layout(std140) uniform MagicFrame {
    mat4 InvViewProjection;
    vec4 ViewportTime; // framebuffer width,height,time, zero-to-one clip depth
    vec4 CenterRadius; // camera-relative effect center, radius in blocks
    vec4 AxisExtent;   // normalized forward axis, axial half length in blocks
    vec4 TintTier;     // school RGB, tier 1..8
    vec4 Animation;    // age seconds, lifetime seconds, phase, mechanic
    vec4 Profile;      // CAST/TRAVEL/IMPACT/FINISH shape ordinals
    vec4 Dynamics;     // motion ordinal, stable spell seed, intensity, march steps
    vec4 Identity;     // school ordinal, original mechanic, catalogue index, reserved
};
uniform sampler2D SceneDepth;
layout(location = 0) in vec2 screenUv;
layout(location = 0) out vec4 fragColor;

const float PI = 3.141592653589793;
const float TAU = 6.283185307179586;
const int MAX_STEPS = 48;

float saturate(float x) { return clamp(x, 0.0, 1.0); }
float hash3(vec3 p) {
    p = fract(p * vec3(0.113, 0.127, 0.137));
    p += dot(p, p.zxy + 19.17);
    return fract((p.x + p.z) * p.y);
}
float noise3(vec3 p) {
    vec3 c = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(c), hash3(c + vec3(1, 0, 0)), f.x),
                   mix(hash3(c + vec3(0, 1, 0)), hash3(c + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash3(c + vec3(0, 0, 1)), hash3(c + vec3(1, 0, 1)), f.x),
                   mix(hash3(c + vec3(0, 1, 1)), hash3(c + vec3(1, 1, 1)), f.x), f.y), f.z);
}
mat2 turn(float a) { float c = cos(a), s = sin(a); return mat2(c, -s, s, c); }
float segment2(vec2 p, vec2 a, vec2 b) {
    vec2 d = b - a;
    return length(p - a - d * clamp(dot(p - a, d) / max(dot(d, d), 0.0001), 0.0, 1.0));
}
float segment3(vec3 p, vec3 a, vec3 b) {
    vec3 d = b - a;
    return length(p - a - d * clamp(dot(p - a, d) / max(dot(d, d), 0.0001), 0.0, 1.0));
}
float traceLine(float d, float width) {
    // A narrow bright core and a wider analytic halo, without a bloom texture.
    float u = max(d, 0.0) / max(width, 0.0001);
    return exp(-u * u * 2.5) + 0.14 * exp(-u * 0.65);
}
// Analytic ray-to-finite-segment distance. Unlike sample-count-dependent
// raymarch points, this gives an unbroken beam core at every camera angle.
vec2 raySegment(vec3 ro, vec3 rd, vec3 a, vec3 b) {
    vec3 span = b - a, w = ro - a;
    float aa = dot(rd, rd), bb = dot(rd, span), cc = dot(span, span);
    float dd = dot(rd, w), ee = dot(span, w);
    float denom = aa * cc - bb * bb;
    float hit = denom > 1e-8 ? (bb * ee - cc * dd) / denom : -dd / max(aa, 1e-8);
    float along = clamp((ee + bb * hit) / max(cc, 1e-8), 0.0, 1.0);
    hit = dot(a + span * along - ro, rd) / max(aa, 1e-8);
    return vec2(length(ro + rd * hit - (a + span * along)), hit);
}
float analyticRing(vec3 ro, vec3 rd, float tilt, float azimuth, float ringRadius, float width, float start, float finish) {
    ro.xz = turn(azimuth) * ro.xz; rd.xz = turn(azimuth) * rd.xz;
    ro.yz = turn(tilt) * ro.yz; rd.yz = turn(tilt) * rd.yz;
    if (abs(rd.y) < 1e-7) return 0.0;
    float hit = -ro.y / rd.y;
    if (hit < start || hit > finish) return 0.0;
    vec3 p = ro + rd * hit;
    return traceLine(abs(length(p.xz) - ringRadius), width);
}
float polygonEdge(vec2 p, float sides, float rotation) {
    float sector = TAU / sides;
    float angle = atan(p.y, p.x) + rotation;
    return abs(cos(mod(angle + sector * 0.5, sector) - sector * 0.5) * length(p) - cos(PI / sides));
}

// The twelve profiles are geometric fields, not colored copies of a circle.
float sealDistance(vec2 p, int shape, float seed) {
    float r = length(p), a = atan(p.y, p.x);
    if (shape == 0) return abs(r - 1.0);
    if (shape == 1) return polygonEdge(p, 3.0, PI * 0.5);
    if (shape == 2) return abs(max(abs(p.x), abs(p.y)) - 0.79);
    if (shape == 3) {
        float d = 10.0;
        for (int i = 0; i < 5; ++i) {
            float u = float(i) * TAU / 5.0 + PI * 0.5;
            d = min(d, segment2(p, vec2(cos(u), sin(u)), vec2(cos(u + 2.0 * TAU / 5.0), sin(u + 2.0 * TAU / 5.0))));
        }
        return d;
    }
    if (shape == 4) return min(polygonEdge(p, 3.0, PI * 0.5), polygonEdge(p, 3.0, -PI * 0.5));
    if (shape == 5) return abs(r - abs(cos(a * 3.0)));
    if (shape == 6) {
        float winding = mod(a / TAU - r * 1.7 + 1.5, 1.0) - 0.5;
        return max(abs(winding) * max(r, 0.08), r - 1.0);
    }
    if (shape == 7) return min(abs(p.x - 0.6 * sin(p.y * 5.0)), abs(p.x + 0.6 * sin(p.y * 5.0))) + max(abs(p.y) - 1.0, 0.0);
    if (shape == 8) {
        float arc = max(abs(r - 0.9), abs(a) - 1.15);
        float rays = abs(sin(a * 4.0)) * r + max(r - 0.9, 0.0);
        return min(arc, rays);
    }
    if (shape == 9) return abs(r - (0.69 + 0.27 * pow(abs(cos(a * 2.5)), 5.0)));
    if (shape == 10) {
        vec2 w = vec2(abs(p.x), p.y);
        return min(abs(length(w - vec2(0.42, 0.1)) - 0.58) + max(-w.y - 0.15, 0.0),
                   abs(w.y - 0.5 * sin(w.x * 3.0)) + max(w.x - 1.1, 0.0));
    }
    float stars = 10.0;
    for (int i = 0; i < 7; ++i) {
        float u = float(i) * TAU / 7.0 + seed * 0.07;
        vec2 c = vec2(cos(u), sin(u)) * mix(0.52, 0.94, mod(float(i), 2.0));
        float v = float(i + 1) * TAU / 7.0 + seed * 0.07;
        vec2 n = vec2(cos(v), sin(v)) * mix(0.52, 0.94, mod(float(i + 1), 2.0));
        stars = min(stars, min(length(p - c) - 0.025, segment2(p, c, n) + 0.012));
    }
    return max(stars, 0.0);
}

vec3 animatePoint(vec3 p, float time, int motion) {
    if (motion == 1) p.xz = turn(time * 0.7) * p.xz;
    if (motion == 2) p.xz = turn(time * (p.y >= 0.0 ? 0.85 : -0.85)) * p.xz;
    if (motion == 3) p /= 0.87 + 0.10 * cos(time * 2.5);
    if (motion == 4) p.y -= 0.12 * sin(time * 1.7);
    if (motion == 5) p.yz = turn(sin(time * 0.8) * 0.65) * p.yz;
    if (motion == 6) { p.xz -= vec2(cos(time), sin(time)) * 0.16; p.xz = turn(-time * 0.6) * p.xz; }
    if (motion == 7) p /= 0.78 + 0.20 * (0.5 + 0.5 * cos(time * 1.5));
    return p;
}

float glyphField(vec3 p, int shape, float time, float seed, float tier) {
    float plane = exp(-abs(p.y) * 65.0);
    float rim = traceLine(abs(length(p.xz) - 0.94), 0.009);
    float seal = traceLine(sealDistance(turn(time * 0.2) * p.xz / 0.78, shape, seed), 0.013);
    float a = atan(p.z, p.x), r = length(p.xz);
    float ticks = traceLine(abs(r - 0.86), 0.006) * pow(abs(sin(a * (12.0 + mod(seed, 5.0)))), 14.0);
    float spokes = traceLine(abs(sin(a * (3.0 + mod(seed, 4.0)))) * r, 0.008) * smoothstep(0.30, 0.48, r) * (1.0 - smoothstep(0.72, 0.77, r));
    return (rim + seal * 0.8 + ticks * 0.8 + spokes * 0.3) * plane * (0.6 + tier * 0.07);
}

// Structural identity is independent of TintTier. Each school defines a
// different solid / ribbon / branch topology, also visible in white light.
float boxDistance(vec3 p, vec3 b) {
    vec3 d = abs(p) - b;
    return length(max(d, 0.0)) + min(max(d.x, max(d.y, d.z)), 0.0);
}
float solidGlow(float d, float thickness) {
    // Keep the occupied interior readable; a very thin SDF outline would
    // alias under the bounded 16..48 step integration budget.
    return exp(-max(d, 0.0) / thickness) * (0.65 + 0.35 * exp(-abs(d) / thickness));
}
vec3 profilePoint(vec3 p, int shape, float time) {
    // Shape changes the entire event's silhouette, including travel and the
    // terminal phases. It is not confined to the floor seal decoration.
    float angle = atan(p.z, p.x), radial = length(p.xz);
    if (shape == 1) p.xz *= 1.0 + 0.25 * cos(angle * 3.0);
    if (shape == 2) p.xz *= 0.72 + 0.32 * max(abs(cos(angle)), abs(sin(angle)));
    if (shape == 3) p.xz *= 1.05 + 0.24 * cos(angle * 5.0);
    if (shape == 4) p.xz *= 0.98 + 0.15 * cos(angle * 6.0);
    if (shape == 5) p.xz *= 1.08 + 0.33 * cos(angle * 3.0);
    if (shape == 6) p.xz = turn(p.y * 1.8 + radial * 1.2) * p.xz;
    if (shape == 7) { p.x -= 0.19 * sin(p.y * 6.0); p.z += 0.19 * cos(p.y * 6.0); }
    if (shape == 8) { p.y *= 1.8; p.z *= 0.72; }
    if (shape == 9) { p.y -= 0.18 * cos(angle * 5.0); p.xz *= 1.10; }
    if (shape == 10) { p.x *= 0.63; p.z *= 1.55; p.y += abs(p.x) * 0.35; }
    if (shape == 11) { p.xz = turn(p.y > 0.0 ? 0.6 : -0.6) * p.xz; p.y *= 1.25; }
    return p;
}

// A school's signature body in a normalized box. Axial events use q.xzy so
// the y spine follows their actual beam or projectile direction. Field events
// use world-up p; no camera-facing sprite or particle emitter is involved.
vec2 schoolField(vec3 p, int school, float time, float life, int shape, bool axial) {
    p = profilePoint(p, shape, time);
    float r = length(p.xz), a = atan(p.z, p.x), d = 10.0, accent = 0.0;
    float t = p.y;
    if (school == 0) { // FIRE: three pointed, asymmetrical rising flame tongues
        for (int i = 0; i < 3; ++i) {
            float n = float(i) - 1.0, height = 0.90 - abs(n) * 0.16;
            float x = p.x - n * 0.27 - 0.09 * sin(t * 6.0 - time * 4.0 + n);
            float width = max(0.015, (height - t) * 0.22);
            float tongue = max(length(vec2(x, p.z * 1.5)) - width, max(-0.82 - t, t - height));
            d = min(d, tongue);
        }
        accent = solidGlow(abs(p.z) + abs(p.x - sin(t * 7.0 - time * 6.0) * 0.13) - 0.02, 0.025);
    } else if (school == 1) { // WATER: broad, separated sinusoidal water ribbons
        for (int i = 0; i < 3; ++i) {
            float n = float(i) - 1.0;
            float wave = sin(t * 4.5 + time * 2.2 + n * 2.1);
            vec3 v = vec3(p.x - wave * 0.34, t, p.z - n * 0.28);
            d = min(d, boxDistance(v, vec3(0.065, 0.87, 0.11)));
        }
    } else if (school == 2) { // ICE: five faceted spear crystals of unequal height
        for (int i = 0; i < 5; ++i) {
            float n = float(i), u = n * TAU / 5.0;
            vec3 v = p - vec3(cos(u) * 0.43, sin(u * 2.0) * 0.12, sin(u) * 0.43);
            v.xz = turn(u) * v.xz;
            float tip = 0.13 * max(0.0, (abs(v.y) - 0.42) / 0.45);
            float diamond = (abs(v.x) + abs(v.z)) * 0.707 - 0.13 + tip;
            d = min(d, max(diamond, abs(v.y) - (0.87 - mod(n, 2.0) * 0.16)));
        }
    } else if (school == 3) { // WIND: three swept crescent blades, open on one side
        for (int i = 0; i < 3; ++i) {
            float h = float(i) * 0.50 - 0.50;
            vec3 v = p - vec3(0, h, 0); v.xz = turn(time * 1.8 + float(i) * 1.7) * v.xz;
            float crescent = max(abs(length(v.xz) - 0.62) - 0.045, -(v.x + 0.12));
            d = min(d, max(crescent, abs(v.y - v.z * 0.23) - 0.045));
        }
    } else if (school == 4) { // EARTH: staggered heavy slabs with square gaps
        for (int i = 0; i < 4; ++i) {
            float n = float(i), height = n * 0.45 - 0.68;
            vec3 v = p - vec3(mod(n, 2.0) * 0.32 - 0.16, height, 0);
            v.xz = turn((n - 1.5) * 0.22) * v.xz;
            d = min(d, boxDistance(v, vec3(0.51 - n * 0.05, 0.11, 0.32)));
        }
    } else if (school == 5) { // LIGHTNING: connected angular bolt and two forks
        for (int i = 0; i < 7; ++i) {
            float n = float(i), j = n + 1.0;
            vec3 a0 = vec3(sin(n * 3.3) * 0.33, n * 0.25 - 0.88, cos(n * 2.2) * 0.15);
            vec3 b0 = vec3(sin(j * 3.3) * 0.33, j * 0.25 - 0.88, cos(j * 2.2) * 0.15);
            d = min(d, segment3(p, a0, b0) - 0.035);
            if (i == 2 || i == 4) d = min(d, segment3(p, a0, a0 + vec3(i == 2 ? 0.45 : -0.46, 0.35, 0.20)) - 0.022);
        }
    } else if (school == 6) { // LIGHT: paired feather wings around a central halo
        vec3 v = vec3(abs(p.x), p.y, p.z);
        for (int i = 0; i < 5; ++i) {
            float n = float(i), x = 0.15 + n * 0.15;
            d = min(d, segment3(v, vec3(0.06, -0.35, 0), vec3(x, 0.73 - n * 0.18, -n * 0.045)) - 0.025);
        }
        d = min(d, length(vec2(length(p.xy - vec2(0, 0.55)) - 0.22, p.z)) - 0.018);
    } else if (school == 7) { // SHADOW: tall torn fissure, serrated split edges
        float tear = 0.14 + 0.20 * abs(sin(t * 8.0 + sin(t * 3.0)));
        d = max(abs(abs(p.x) - tear) - 0.035, max(abs(p.z) - 0.10, abs(t) - 0.9));
        d = min(d, max(abs(p.x + 0.15 + t * 0.32) - 0.025, max(abs(p.z - 0.31) - 0.05, abs(t + 0.18) - 0.55)));
    } else if (school == 8) { // ARCANE: nested diamond prisms and floating rune tiles
        float diamond = abs(p.x) + abs(p.y) * 0.65 + abs(p.z) - 0.58;
        d = abs(diamond) * 0.58 - 0.023;
        vec3 v = p; v.xz = turn(time * -0.7) * v.xz;
        d = min(d, abs(abs(v.x) + abs(v.z) - 0.77) * 0.707 + abs(v.y) - 0.035);
        for (int i = 0; i < 4; ++i) {
            float u = float(i) * PI * 0.5 + time * 0.5;
            vec3 tile = p - vec3(cos(u) * 0.66, sin(time + u) * 0.35, sin(u) * 0.66);
            tile.xz = turn(u) * tile.xz;
            d = min(d, abs(boxDistance(tile, vec3(0.10, 0.16, 0.025))) - 0.016);
        }
    } else if (school == 9) { // NATURE: branching vine and six broad pointed leaves
        vec3 stem = vec3(sin(t * 4.0 + time * 0.3) * 0.12, t, cos(t * 4.0) * 0.10);
        d = max(length(p.xz - stem.xz) - 0.045, abs(t) - 0.88);
        for (int i = 0; i < 6; ++i) {
            float n = float(i), h = n * 0.27 - 0.67, side = mod(n, 2.0) * 2.0 - 1.0;
            vec3 leaf = p - vec3(side * 0.30, h + 0.13, 0);
            leaf.xy = turn(side * -0.55) * leaf.xy;
            float blade = abs(leaf.x) / 0.30 + abs(leaf.y) / 0.12 + abs(leaf.z) / 0.07 - 1.0;
            d = min(d, blade * 0.07);
            d = min(d, segment3(p, vec3(0,h,0), vec3(side * 0.53,h + 0.29,0)) - 0.025);
        }
    } else if (school == 10) { // GRAVITY: oblique accretion discs around a dark hole
        vec3 v = p; v.yz = turn(0.60) * v.yz;
        float disk = max(abs(v.y) - 0.032, max(0.32 - length(v.xz), length(v.xz) - 0.88));
        vec3 w = p; w.xy = turn(-0.75) * w.xy;
        d = min(disk, length(vec2(length(w.xz) - 0.55, w.y)) - 0.027);
        accent = exp(-abs(length(p) - 0.27) * 70.0) * 0.45;
    } else if (school == 11) { // TIME: two counter-rotating cog wheels and clock hands
        for (int i = 0; i < 2; ++i) {
            vec3 v = p; v.y += i == 0 ? 0.35 : -0.35;
            v.xz = turn(time * (i == 0 ? 0.55 : -0.55)) * v.xz;
            float u = atan(v.z, v.x), radial = length(v.xz), teeth = 0.62 + 0.09 * step(0.0, cos(u * 12.0));
            float gear = max(abs(radial - teeth) - 0.045, abs(v.y) - 0.055);
            d = min(d, gear);
            d = min(d, segment3(v, vec3(0), vec3(cos(time) * 0.52, 0, sin(time) * 0.52)) - 0.023);
            d = min(d, segment3(v, vec3(0), vec3(cos(time * 0.2) * 0.38, 0, sin(time * 0.2) * 0.38)) - 0.032);
        }
    } else if (school == 12) { // BLOOD: thick pulsing artery and alternating bifurcations
        float pulse = 0.065 + 0.018 * sin(time * 6.0 - t * 5.0);
        d = max(length(p.xz - vec2(0.10 * sin(t * 5.0), 0.04 * cos(t * 3.0))) - pulse, abs(t) - 0.88);
        for (int i = 0; i < 4; ++i) {
            float n = float(i), h = n * 0.40 - 0.65, side = mod(n, 2.0) * 2.0 - 1.0;
            vec3 elbow = vec3(side * 0.37, h + 0.15, 0.13);
            d = min(d, segment3(p, vec3(0,h,0), elbow) - pulse * 0.68);
            d = min(d, segment3(p, elbow, elbow + vec3(side * 0.13,0.30,-0.16)) - pulse * 0.40);
        }
    } else { // SPIRIT: hollow soul heads with continuous curled, tapering tails
        for (int i = 0; i < 3; ++i) {
            float n = float(i), offset = n * 0.48 - 0.48;
            vec3 v = p - vec3(offset, 0.37 + 0.14 * sin(time * 1.2 + n), sin(n * 2.0) * 0.2);
            float head = abs(length(v * vec3(1.0,0.8,1.0)) - 0.17) - 0.025;
            float tailY = clamp(-v.y, 0.0, 0.95);
            vec2 curl = vec2(sin(tailY * 5.0 + n) * tailY * 0.20, cos(tailY * 5.0 + n) * tailY * 0.12);
            float tail = max(length(v.xz - curl) - max(0.01,0.10 - tailY * 0.09), max(v.y + 0.10,-0.95 - v.y));
            d = min(d, min(head, tail));
        }
    }
    float boundary = 1.0 - smoothstep(0.94, 1.10, max(abs(p.y), max(abs(p.x), abs(p.z))));
    return vec2(solidGlow(d, 0.035) * 1.6, exp(-abs(d) * 55.0) * 0.22 + accent * 0.14) * boundary;
}

vec2 deliveredIdentity(vec3 p, vec3 q, float stretch, int mechanic, int phase, int school, int shape, float time, float life) {
    bool axial = mechanic == 0 || mechanic == 1 || mechanic == 2 || mechanic == 5 || mechanic == 8 || mechanic == 10 || mechanic == 16 || mechanic == 17;
    vec3 v = axial ? vec3(q.x, q.z / max(stretch,0.02), q.y) : p;
    float amplitude = 1.0;
    if (mechanic == 0) { v.y *= 1.65; v.xz *= 1.35; } // compact projectile
    if (mechanic == 2) v.xz /= max(0.15, (v.y + 1.1) * 0.5); // opening cone
    if (mechanic == 3) { v /= 0.28 + life * 0.78; amplitude *= smoothstep(0.15,0.40,length(v)); }
    if (mechanic == 4 || mechanic == 14) {
        // A bounded nearest-cell transform places three *school-specific*
        // satellites; it does not instantiate a separate shader for each one.
        float angle = atan(v.z,v.x) - time * (mechanic == 4 ? 0.7 : -0.5);
        float sector = floor((angle + PI / 3.0) / (TAU / 3.0)) * (TAU / 3.0);
        float u = sector + time * (mechanic == 4 ? 0.7 : -0.5);
        v -= vec3(cos(u)*0.56, mechanic == 14 ? 0.12+sin(u+time)*0.12 : 0.0, sin(u)*0.56);
        v *= mechanic == 4 ? 2.8 : 2.3;
    }
    if (mechanic == 5) { v.x -= sin(floor(v.y * 6.0) * 2.9) * 0.12; v.z *= 1.5; }
    if (mechanic == 6) { v.y *= 4.0; v.xz /= 0.25 + life * 0.8; }
    if (mechanic == 7) { v.xz = turn(v.y*3.0-time*1.7)*v.xz; v.xz /= max(0.25,0.55+v.y*0.30); }
    if (mechanic == 8) { v.y = (v.y - 0.35)*1.8; v.xz *= 1.5; }
    if (mechanic == 9) { v *= 1.35; v.y -= 0.18; }
    if (mechanic == 10) { v.z *= 3.0; v.x -= sin(v.y*2.7)*0.22; }
    if (mechanic == 11) { v = vec3(q.x,q.y,q.z*5.0); v.xz = turn(time*0.35)*v.xz; }
    if (mechanic == 12) v = vec3(q.x,q.y,q.z*7.0);
    if (mechanic == 15) v = vec3(q.x*1.1,q.y*1.1,q.z*4.0);
    if (mechanic == 16) { v.xz = turn(v.y*4.0+time*2.0)*v.xz; v.xz /= 0.40+0.18*(v.y+1.0); }
    if (mechanic == 17) { v.xz = turn(time*0.6)*v.xz; v.xz *= 1.50; }
    if (mechanic == 18) { float shell = length(v); v *= (0.45 + 0.28 * floor(shell*3.0)) / max(shell,0.1); }
    if (mechanic == 19) {
        // A colossal central signature with a crown / rune foundation below.
        v.y = (v.y - 0.20)*1.35; v.xz *= 0.85;
    }
    if (phase == 2) v /= 0.50 + life * 0.65;
    if (phase == 3) v /= max(0.25,1.0-life*0.65);
    vec2 signature = schoolField(v,school,time,life,shape,axial);
    if (phase == 3) amplitude *= 1.0-life;
    return signature * amplitude;
}


// Return emissive density and a little white-hot core separately. Coordinates
// use radius-normalized world space; q is aligned with the event's true axis.
vec2 magicField(vec3 p, vec3 q, float time, float life, int phase, int mechanic, int shape, float seed, float tier, float stretch, int school) {
    float r = length(p.xz), ball = length(p), around = atan(p.z, p.x);
    float radial = length(q.xy), angle = atan(q.y, q.x);
    float axial = q.z / max(stretch, 0.02);
    float cap = 1.0 - smoothstep(0.86, 1.0, abs(axial));
    float body = 0.0, core = 0.0;
    // The ultra-thin floor seal is integrated analytically once in main().
    // Sampling it in fixed volume steps would shimmer at grazing angles.
    float glyph = 0.0;

    if (phase == 0) {
        float rise = smoothstep(0.0, 0.7, life);
        float halo = traceLine(abs(ball - (0.25 + life * 0.35)), 0.018) * 0.22;
        float shafts = traceLine(abs(r - 0.70), 0.025) * pow(abs(cos(around * (4.0 + mod(seed, 5.0)) + time)), 18.0) * exp(-abs(p.y - 0.22) * 3.5);
        vec2 signature = schoolField(p / (0.42 + rise * 0.30), school, time, life, shape, false);
        return vec2((glyph * (0.35 + rise) + halo + shafts * rise) * (0.6 + life * 0.4), glyph * 0.18) * 0.30 + signature * (0.25 + rise * 0.45);
    }

    if (mechanic == 0) { // bolt: hot capsule with a braided wake
        core = traceLine(segment3(q, vec3(0, 0, -stretch * 0.25), vec3(0, 0, stretch * 0.42)), 0.07);
        float winding = angle - q.z * 15.0 + time * 8.0;
        body = core * 0.08 + exp(-radial * 5.0) * cap * 0.05;
    } else if (mechanic == 1) { // beam: continuous volume, moving accelerator hoops
        core = traceLine(radial, 0.045) * cap;
        float hoops = traceLine(abs(radial - 0.22), 0.025) * pow(abs(sin(q.z * 10.0 - time * 7.0)), 12.0);
        float coil = traceLine(abs(radial - 0.12 - 0.035 * sin(q.z * 14.0 - time * 9.0)), 0.018);
        body = exp(-radial * 5.0) * cap * 0.08;
        core = 0.0; // continuous hot core and coils are integrated analytically
    } else if (mechanic == 2) { // cone: expanding spiral horn
        float coneRadius = (axial + 1.0) * 0.45 + 0.03;
        float cone = traceLine(abs(radial - coneRadius), 0.035);
        body = cone * (0.4 + 0.6 * pow(abs(sin(angle * 4.0 + q.z * 8.0 - time * 5.0)), 4.0)) * cap;
        core = traceLine(radial, 0.04) * cap * 0.2;
    } else if (mechanic == 3) { // nova: expanding shell, equatorial shock lens
        float shell = 0.18 + life * 0.78;
        body = exp(-abs(ball - shell) * 14.0) * 0.05;
        body += traceLine(length(vec2(r - shell, p.y * 3.0)), 0.028) * 1.5;
        core = exp(-ball * 16.0) * (1.0 - life);
    } else if (mechanic == 4) { // orbit: gyroscopic intersecting rings
        vec3 u = p; u.yz = turn(0.8 + time * 0.6) * u.yz;
        body = exp(-ball * 3.0) * 0.035;
        u = p; u.xy = turn(-0.8 + time * 0.4) * u.xy;
        body += exp(-ball * 4.0) * 0.02;
        core = traceLine(ball, 0.12);
    } else if (mechanic == 5) { // chain: branched angular plasma
        float cell = floor(q.z * 8.0 - time * 5.0);
        float f = fract(q.z * 8.0 - time * 5.0);
        vec2 a = vec2(hash3(vec3(cell, seed, 1)), hash3(vec3(cell, seed, 2))) - 0.5;
        vec2 b = vec2(hash3(vec3(cell + 1.0, seed, 1)), hash3(vec3(cell + 1.0, seed, 2))) - 0.5;
        float d = length(q.xy - mix(a, b, f) * 0.65);
        core = traceLine(d, 0.07) * cap * 0.15;
        body = exp(-radial * 4.0) * cap * 0.06;
    } else if (mechanic == 6) { // wave: multiple advancing toroidal fronts
        float front = 0.16 + life * 0.78;
        body = traceLine(length(vec2(r - front, p.y * 2.5)), 0.035);
        body += traceLine(length(vec2(r - front * 0.7, (p.y - 0.08) * 3.0)), 0.022) * 0.6;
        body *= 0.5 + 0.5 * pow(abs(cos(around * 8.0)), 2.0);
    } else if (mechanic == 7) { // vortex: a spiral funnel, not a flat disc
        float height = p.y * 0.5 + 0.5;
        float funnel = 0.18 + height * height * 0.65;
        float shell = exp(-abs(r - funnel) * 7.0);
        body = shell * (0.10 + 0.10 * cos(around * 3.0 + p.y * 8.0 - time * 5.0));
        core = traceLine(r, 0.045) * (1.0 - smoothstep(0.6, 1.0, abs(p.y)));
    } else if (mechanic == 8) { // meteor: solid-feeling ember core and tapered tail
        float rough = noise3(q * 9.0 + seed) * 0.055;
        core = traceLine(abs(length(q - vec3(0, 0, stretch * 0.35)) - 0.18 - rough), 0.045);
        float tail = traceLine(radial, max(0.025, (axial + 1.1) * 0.12));
        body = core * 1.8 + tail * cap * (0.4 + noise3(q * 6.0 - vec3(0, 0, time * 5.0)) * 0.7);
    } else if (mechanic == 9) { // mine: rotating layered seals with a suspended trigger lens
        body = glyph + traceLine(abs(ball - 0.22), 0.018) * (0.4 + 0.3 * sin(time * 4.0));
        body += traceLine(abs(r - 0.63), 0.012) * exp(-abs(p.y - 0.17) * 40.0);
    } else if (mechanic == 10) { // dash: swept blade and parallel speed ribbons
        core = traceLine(length(vec2(q.x, q.y * 2.5)), 0.035) * cap;
        body = exp(-radial * 6.0) * cap * 0.08;
        core = 0.0;
    } else if (mechanic == 11) { // blink: upright aperture with a rotating iris
        float gate = traceLine(length(vec2(length(q.xy) - 0.70, q.z * 3.0)), 0.025);
        float iris = traceLine(sealDistance(turn(time) * q.xy / 0.65, shape, seed), 0.017) * exp(-abs(q.z) * 45.0);
        body = gate + iris * 0.7;
        core = exp(-radial * 6.0 - abs(q.z) * 30.0) * (1.0 - life) * 0.4;
    } else if (mechanic == 12) { // wall: luminous planar lattice, bounded to the real wall
        float plane = exp(-abs(q.z) * 80.0) * (1.0 - smoothstep(0.80, 0.98, max(abs(q.x), abs(q.y))));
        vec2 lattice = abs(fract(q.xy * vec2(5.0, 4.0) + vec2(0, time * 0.1)) - 0.5);
        body = plane * (traceLine(min(lattice.x, lattice.y), 0.025) + 0.10);
        body += traceLine(abs(max(abs(q.x), abs(q.y)) - 0.84), 0.015) * exp(-abs(q.z) * 40.0);
    } else if (mechanic == 13) { // shield: spherical shell, hexagonal interference veins
        // Its crisp shell is integrated analytically in main; only the soft
        // inner interference volume is marched, so thin surfaces never flicker.
        body = exp(-abs(ball - 0.78) * 12.0) * 0.035 * (0.5 + 0.5 * sin(time * 2.0 + p.y * 8.0));
    } else if (mechanic == 14) { // summon: orbiting winged cores tethered to their seal
        for (int i = 0; i < 3; ++i) {
            float a = float(i) * TAU / 3.0 + time;
            vec3 c = vec3(cos(a) * 0.52, 0.20 + sin(time * 2.0 + a) * 0.2, sin(a) * 0.52);
            vec3 v = p - c;
            core += traceLine(length(v), 0.055);
            body += traceLine(abs(v.y - abs(v.x) * 0.5 * sin(time * 6.0 + a)), 0.018) * exp(-length(v.xz) * 10.0);
            body += traceLine(segment3(p, c, vec3(0, -0.15, 0)), 0.009) * 0.35;
        }
        body += glyph * 0.45;
    } else if (mechanic == 15) { // mark: hovering target seal and closing brackets
        body = traceLine(sealDistance(turn(-time * 0.4) * q.xy / 0.72, shape, seed), 0.017) * exp(-abs(q.z) * 55.0);
        float bracket = abs(max(abs(q.x), abs(q.y)) - (0.85 - life * 0.1));
        body += traceLine(bracket, 0.016) * step(0.40, min(abs(q.x), abs(q.y))) * exp(-abs(q.z) * 45.0);
    } else if (mechanic == 16) { // drain: inward moving double helix
        float spine = angle - q.z * 11.0 + time * 6.0;
        float helix = traceLine(abs(radial - 0.22), 0.028) * pow(abs(sin(spine)), 10.0);
        body = exp(-radial * 6.0) * cap * 0.06;
        core = 0.0;
    } else if (mechanic == 17) { // channel: levitating rings around a vertical light shaft
        core = traceLine(radial, 0.06) * cap;
        float rings = pow(abs(cos(q.z * 12.0 - time * 4.0)), 20.0);
        body = exp(-radial * 4.0) * cap * 0.07;
        core = 0.0;
    } else if (mechanic == 18) { // echo: three temporally offset nested shells
        for (int i = 0; i < 3; ++i) {
            float progress = fract(life + float(i) / 3.0);
            body += traceLine(abs(ball - (0.15 + progress * 0.76)), 0.018) * (1.0 - progress) * (1.0 - float(i) * 0.2);
        }
        body += glyph * 0.25;
    } else { // domain: contained celestial dome, star lattice and rising pillars
        body = exp(-abs(ball - 0.72) * 5.0) * smoothstep(-0.02, 0.20, p.y) * 0.03;
    }

    // Super-tier effects receive a second armillary shell and a true 3D star
    // field inside the event volume. Seed changes its topology for each spell.
    if (tier > 7.5) {
        vec3 u = p; u.xy = turn(time * 0.22 + seed) * u.xy;
        float armillary = exp(-length(vec2(length(u.xz) - 0.91, u.y)) * 6.0) * 0.025;
        u = p; u.yz = turn(time * -0.3 + 0.8) * u.yz;
        armillary += exp(-length(vec2(length(u.xz) - 0.78, u.y)) * 6.0) * 0.025;
        body += armillary;
    }
    if (phase == 2) {
        float shock = traceLine(length(vec2(r - (0.12 + life * 0.85), p.y * 3.0)), 0.028) * (1.0 - life);
        body += shock * 1.6 + glyph * 0.35;
        core += exp(-ball * 12.0) * pow(1.0 - life, 3.0) * 1.3;
    }
    if (phase == 3) body = body * (1.0 - life) + glyph * pow(1.0 - life, 2.0) * 0.35;
    bool elongated = phase == 1 && (mechanic == 0 || mechanic == 1 || mechanic == 2 || mechanic == 5 || mechanic == 8 || mechanic == 10 || mechanic == 16 || mechanic == 17);
    vec2 signature = deliveredIdentity(p, q, stretch, mechanic, phase, school, shape, time, life);
    // Operator supplies delivery bounds; school supplies the dominant material
    // topology. Common beam/ball templates must not drown out that identity.
    body = body * 0.16 + signature.x;
    core = core * 0.10 + signature.y;
    if (phase == 3) { body *= 1.0 - life; core *= 1.0 - life; }
    float edge = elongated ? max(max(abs(q.x), abs(q.y)), abs(axial)) : max(max(abs(p.x), abs(p.y)), abs(p.z));
    float boundary = 1.0 - smoothstep(0.94, 1.06, edge);
    return max(vec2(body, core) * boundary, vec2(0.0));
}

void main() {
    vec2 uv = gl_FragCoord.xy / max(ViewportTime.xy, vec2(1.0));
    bool zeroToOne = ViewportTime.w > 0.5;
    // Minecraft 26.3 uses reversed Z: the far plane / clear depth is ZERO.
    // Reconstruct through the exact (including view bob) inverse projection.
    vec4 farPoint = InvViewProjection * vec4(uv * 2.0 - 1.0, zeroToOne ? 0.0 : -1.0, 1.0);
    vec3 ray = normalize(farPoint.xyz / (abs(farPoint.w) < 1e-7 ? 1e-7 : farPoint.w));
    float rawDepth = texture(SceneDepth, uv).r;
    float sceneDistance = 1e6;
    if (rawDepth > 0.0000001) {
        vec4 scene = InvViewProjection * vec4(uv * 2.0 - 1.0, zeroToOne ? rawDepth : rawDepth * 2.0 - 1.0, 1.0);
        if (abs(scene.w) > 1e-7) sceneDistance = max(0.0, dot(scene.xyz / scene.w, ray));
    }
    vec3 axis = normalize(AxisExtent.xyz + vec3(0.0, 0.000001, 0.0));
    vec3 helper = abs(axis.y) > 0.95 ? vec3(1, 0, 0) : vec3(0, 1, 0);
    vec3 right = normalize(cross(helper, axis)), up = cross(axis, right);
    float radius = clamp(CenterRadius.w, 0.08, 64.0);
    float lengthHalf = clamp(AxisExtent.w, radius * 0.1, 96.0);
    // Rotate-aware box enclosure; pixels outside exit before expensive fields.
    // Animated profile folds and oblique planar seals may exceed an unrotated
    // unit cube; this conservative enclosure does not clip their corners.
    vec3 extent = vec3(radius * 1.7) + abs(axis) * max(0.0, lengthHalf - radius);
    vec3 safeRay = sign(ray) * max(abs(ray), vec3(1e-6));
    safeRay += vec3(equal(safeRay, vec3(0.0))) * 1e-6;
    vec3 t0 = (CenterRadius.xyz - extent) / safeRay;
    vec3 t1 = (CenterRadius.xyz + extent) / safeRay;
    vec3 enter = min(t0, t1), leave = max(t0, t1);
    float start = max(0.0, max(enter.x, max(enter.y, enter.z)));
    float finish = min(sceneDistance - 0.006, min(leave.x, min(leave.y, leave.z)));
    if (finish <= start) discard;

    int phase = clamp(int(Animation.z + 0.5), 0, 3);
    int mechanic = clamp(int(Animation.w + 0.5), 0, 19);
    int school = clamp(int(Identity.x + 0.5), 0, 13);
    int shape = clamp(int(Profile[phase] + 0.5), 0, 11);
    int motion = clamp(int(Dynamics.x + 0.5), 0, 7);
    float seed = clamp(Dynamics.y, 0.0, 2048.0);
    float tier = clamp(TintTier.w, 1.0, 8.0);
    float age = max(Animation.x, 0.0), duration = max(Animation.y, 0.05);
    float life = saturate(age / duration);
    float time = age + mod(seed * 1.618, 10.0);
    int steps = clamp(int(Dynamics.w + 0.5), 16, MAX_STEPS);
    float stepSize = (finish - start) / float(steps);
    // Midpoint integration is deterministic. Thin structural features below
    // are analytic; no stochastic point cloud is used to conceal undersampling.
    float distanceAlong = start + stepSize * 0.5;
    vec3 emission = vec3(0.0);
    float transmittance = 1.0;
    vec3 tint = clamp(TintTier.rgb, vec3(0.015), vec3(1.0));
    vec3 hot = mix(tint, vec3(1.0), 0.78);
    float exposure = clamp(Dynamics.z, 0.0, 2.0) * (0.7 + tier * 0.12);

    for (int i = 0; i < MAX_STEPS; ++i) {
        if (i >= steps || transmittance < 0.025) break;
        vec3 relative = ray * distanceAlong - CenterRadius.xyz;
        vec3 p = relative / radius;
        vec3 q = vec3(dot(relative, right), dot(relative, up), dot(relative, axis)) / radius;
        p = animatePoint(p, time, motion);
        q = animatePoint(q.xzy, time, motion).xzy;
        vec2 field = magicField(p, q, time, life, phase, mechanic, shape, seed, tier, lengthHalf / radius, school);
        float density = min(field.x, 5.0);
        float opticalStep = min(stepSize / radius, 0.25);
        float opacity = 1.0 - exp(-density * opticalStep * 5.0);
        vec3 radiance = tint * density + hot * min(field.y, 4.0) * 0.9;
        emission += transmittance * radiance * opticalStep * exposure * 3.0;
        transmittance *= 1.0 - opacity * 0.38;
        distanceAlong += stepSize;
    }
    // Ray / animated seal plane intersection. Thin runes stay crisp even when
    // the camera is far away or looks almost parallel to the ground.
    vec3 planeOrigin = animatePoint(-CenterRadius.xyz / radius, time, motion);
    vec3 planeDirection = animatePoint((ray - CenterRadius.xyz) / radius, time, motion) - planeOrigin;
    vec3 axisOrigin = vec3(dot(-CenterRadius.xyz, right), dot(-CenterRadius.xyz, up), dot(-CenterRadius.xyz, axis)) / radius;
    vec3 axisDirection = vec3(dot(ray, right), dot(ray, up), dot(ray, axis)) / radius;
    vec3 movedOrigin = animatePoint(axisOrigin.xzy, time, motion).xzy;
    vec3 movedDirection = animatePoint((axisOrigin + axisDirection).xzy, time, motion).xzy - movedOrigin;
    bool rayBeam = mechanic == 0 || mechanic == 1 || mechanic == 5 || mechanic == 10 || mechanic == 16 || mechanic == 17;
    float halfSpan = lengthHalf / radius;
    if (rayBeam && phase != 0) {
        float deliveryAccent = 0.08;
        vec2 closest = raySegment(movedOrigin, movedDirection, vec3(0, 0, -halfSpan * 0.96), vec3(0, 0, halfSpan * 0.96));
        if (closest.y >= start && closest.y <= finish) {
            float narrow = traceLine(closest.x, 0.024);
            float wide = traceLine(closest.x, 0.095);
            emission += (hot * narrow * 1.65 + tint * wide * 0.70) * exposure * deliveryAccent;
        }
        if (mechanic == 5) {
            // An actual connected six-segment arc, not sampled lightning dots.
            for (int link = 0; link < 6; ++link) {
                float u = float(link) / 6.0, v = float(link + 1) / 6.0;
                vec3 a = vec3(sin(u * 29.0 + seed), cos(u * 37.0 + seed), mix(-halfSpan, halfSpan, u));
                vec3 b = vec3(sin(v * 29.0 + seed), cos(v * 37.0 + seed), mix(-halfSpan, halfSpan, v));
                a.xy *= 0.30; b.xy *= 0.30;
                vec2 arc = raySegment(movedOrigin, movedDirection, a, b);
                if (arc.y >= start && arc.y <= finish) emission += (hot * 0.8 + tint * 0.4) * traceLine(arc.x, 0.028) * exposure * deliveryAccent;
            }
        }
        // Two cylinder intersections carry unbroken accelerator rings and
        // helical filaments. They remain continuous at low integration quality.
        float cylinderRadius = mechanic == 17 ? 0.42 : 0.22;
        float ca = dot(movedDirection.xy, movedDirection.xy);
        float cb = dot(movedOrigin.xy, movedDirection.xy);
        float cc = dot(movedOrigin.xy, movedOrigin.xy) - cylinderRadius * cylinderRadius;
        float determinant = cb * cb - ca * cc;
        if (ca > 1e-9 && determinant >= 0.0) {
            float root = sqrt(determinant);
            for (int side = 0; side < 2; ++side) {
                float hit = (-cb + (side == 0 ? -root : root)) / ca;
                vec3 point = movedOrigin + movedDirection * hit;
                if (hit >= start && hit <= finish && abs(point.z) < halfSpan * 0.97) {
                    float angle = atan(point.y, point.x);
                    float rings = pow(abs(cos(point.z * 9.0 - time * 4.0)), 24.0);
                    float winding = angle * 2.0 - point.z * 10.0 + time * (mechanic == 16 ? 6.0 : -6.0);
                    float braid = traceLine(abs(sin(winding)), 0.065);
                    float coreWeight = mechanic == 1 || mechanic == 17 ? rings * 0.8 + braid * 0.25 : braid * 0.65;
                    emission += (tint + hot * 0.25) * coreWeight * exposure * 0.65 * deliveryAccent;
                }
            }
        }
    }
    if ((mechanic == 13 || mechanic == 19 || mechanic == 3) && phase != 0) {
        float deliveryAccent = 0.10;
        float shellRadius = mechanic == 19 ? 0.88 : (mechanic == 3 ? 0.18 + life * 0.78 : 0.83);
        float a = dot(planeDirection, planeDirection);
        float b = dot(planeOrigin, planeDirection);
        float c = dot(planeOrigin, planeOrigin) - shellRadius * shellRadius;
        float discriminant = b * b - a * c;
        if (discriminant >= 0.0 && a > 1e-9) {
            float root = sqrt(discriminant);
            for (int surface = 0; surface < 2; ++surface) {
                float hit = (-b + (surface == 0 ? -root : root)) / a;
                if (hit >= start && hit <= finish) {
                    vec3 point = planeOrigin + planeDirection * hit;
                    vec3 n = normalize(point);
                    vec2 shellUv = vec2(atan(n.z, n.x), asin(clamp(n.y, -1.0, 1.0))) * vec2(6.0, 8.0);
                    float grid = min(abs(sin(shellUv.x + shellUv.y * 0.577)), min(abs(sin(shellUv.x - shellUv.y * 0.577)), abs(sin(shellUv.y * 1.154))));
                    float rim = pow(1.0 - abs(dot(n, normalize(planeDirection))), 2.0);
                    float veins = traceLine(grid, 0.035) * (0.65 + 0.2 * sin(n.y * 12.0 - time * 3.0));
                    if (mechanic == 19) {
                        float meridians = traceLine(abs(sin(atan(n.z, n.x) * 4.0)), 0.030);
                        float latitudes = traceLine(abs(sin(asin(clamp(n.y, -1.0, 1.0)) * 8.0 - time * 0.2)), 0.045);
                        if (point.y > -0.03) emission += (tint * (0.025 + meridians * 0.50 + latitudes * 0.25 + rim * 0.25) + hot * meridians * 0.18) * exposure * deliveryAccent;
                    } else if (mechanic == 3) {
                        emission += (tint * (0.08 + rim * 0.65) + hot * rim * 0.35) * exposure * (1.0 - life * 0.65) * deliveryAccent;
                    } else emission += (tint * (0.10 + veins * 0.45 + rim * 0.7) + hot * rim * 0.25) * exposure * deliveryAccent;
                }
            }
        }
    }
    if ((tier > 7.5 || mechanic == 4 || mechanic == 19) && phase != 0) {
        float ringA = analyticRing(planeOrigin, planeDirection, 0.75 + time * 0.18, time * 0.22, 0.91, 0.008, start, finish);
        float ringB = analyticRing(planeOrigin, planeDirection, -0.85 - time * 0.15, -time * 0.3 + 0.8, 0.78, 0.009, start, finish);
        float ringC = analyticRing(planeOrigin, planeDirection, 0.0, 0.0, 0.96, 0.009, start, finish);
        emission += (tint * 0.8 + hot * 0.45) * (ringA + ringB + ringC * 0.5) * exposure;
        if (mechanic == 19) {
            for (int pillar = 0; pillar < 6; ++pillar) {
                float angle = float(pillar) * TAU / 6.0 + seed * 0.07;
                vec3 foot = vec3(cos(angle) * 0.70, 0.0, sin(angle) * 0.70);
                vec3 top = foot + vec3(0, 1.05, 0);
                vec2 distanceToPillar = raySegment(planeOrigin, planeDirection, foot, top);
                if (distanceToPillar.y >= start && distanceToPillar.y <= finish) {
                    emission += (tint * traceLine(distanceToPillar.x, 0.035) * 0.25 + hot * traceLine(distanceToPillar.x, 0.010) * 0.50) * exposure;
                }
            }
        }
    }
    if (abs(planeDirection.y) > 1e-6) {
        float hit = -planeOrigin.y / planeDirection.y;
        if (hit >= start && hit <= finish) {
            vec3 sealPoint = planeOrigin + planeDirection * hit;
            float seal = glyphField(sealPoint, shape, time, seed, tier);
            float weight = phase == 0 ? (0.35 + smoothstep(0.0, 0.7, life)) : 0.0;
            if (mechanic == 9) weight += 0.85;
            if (mechanic == 14 || mechanic == 17 || mechanic == 18) weight += 0.45;
            if (mechanic == 19) weight += 0.8;
            if (tier > 7.5) weight += 0.35;
            if (phase == 2) weight += 0.35;
            if (phase == 3) weight = (weight + 0.35) * (1.0 - life);
            emission += (tint + hot * 0.18) * seal * weight * exposure * 0.55;
        }
    }
    float envelope = smoothstep(0.0, min(0.12, duration * 0.18), age) * (1.0 - smoothstep(0.80, 1.0, life));
    // The volume pass targets RGBA16F; retain highlights for the one shared
    // bloom/composite pass instead of clipping every event to display white.
    emission = min(emission * envelope, vec3(12.0));
    float alpha = max(emission.r, max(emission.g, emission.b));
    if (alpha < 0.002) discard;
    // Premultiplied color: pipeline uses ONE, ONE_MINUS_SRC_ALPHA or ONE, ONE.
    fragColor = vec4(emission, min(alpha, 0.92));
}
