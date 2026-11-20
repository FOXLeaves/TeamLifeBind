#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 uiUv;
layout(location = 0) out vec4 fragColor;
uniform sampler2D PresentUi;
uniform sampler2D PreviousUi;
layout(std140) uniform InkFrame {
    vec4 Screen;   // physical width, height, progress, clock
    vec4 Region;   // normalized GUI bounds, top-down
    vec4 Motion;   // horizontal, direction, open/contents/close, previous valid
};

float lattice(vec2 p) {
    p = fract(p * vec2(0.1031, 0.1137));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}
float field(vec2 p) {
    vec2 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(lattice(cell), lattice(cell + vec2(1, 0)), f.x),
               mix(lattice(cell + vec2(0, 1)), lattice(cell + 1.0), f.x), f.y);
}
float flowing(vec2 p, float clock) {
    vec2 drift = vec2(clock * 0.17, -clock * 0.11);
    vec2 bend = vec2(field(p * 0.63 + drift), field(p * 0.71 - drift + 8.4));
    p += (bend - 0.5) * 2.4;
    return field(p) * 0.56 + field(p * 2.07 + 3.9) * 0.29 + field(p * 4.13 - drift) * 0.15;
}

void main() {
    vec4 incoming = texture(PresentUi, uiUv);
    vec2 at = vec2(uiUv.x, 1.0 - uiUv.y);
    if (at.x < Region.x || at.y < Region.y || at.x > Region.z || at.y > Region.w) {
        fragColor = incoming;
        return;
    }
    vec2 span = max(Region.zw - Region.xy, vec2(0.0001));
    vec2 q = (at - Region.xy) / span;
    vec4 outgoing = Motion.w > 0.5 ? texture(PreviousUi, uiUv) : vec4(0);
    if (Motion.z < 1.5) outgoing = vec4(0); // opening from transparency
    if (Motion.z > 2.5) incoming = vec4(0); // closing to transparency
    float raw = clamp(Screen.z, 0.0, 1.0);
    if (raw < 0.00001) { fragColor = outgoing; return; }
    if (raw > 0.99999) { fragColor = incoming; return; }

    float aspect = Screen.x * span.x / max(1.0, Screen.y * span.y);
    vec2 scaled = q * vec2(aspect, 1.0);
    float swell = flowing(scaled * 5.4, Screen.w);
    float ripple = field(scaled * 37.0 + vec2(Screen.w * .25, -Screen.w * .18));
    float axis = mix(q.y, q.x, Motion.x);
    if (Motion.y < 0.0) axis = 1.0 - axis;
    float side = mix(q.x, q.y, Motion.x);
    float crest = axis + (swell - .5) * .32 + (ripple - .5) * .035
        + sin(side * 15.0 + Screen.w * 1.7) * .018;
    float eased = raw * raw * (3.0 - 2.0 * raw);
    float d = mix(-.23, 1.23, eased) - crest;
    float aa = max(fwidth(d) * 1.6, 0.0004);
    float blend = smoothstep(-aa, aa, d);
    float proximity = exp(-abs(d) * 22.0);
    float envelope = smoothstep(0.0, .12, raw) * (1.0 - smoothstep(.88, 1.0, raw));

    // Derivatives extract the old/new UI contours without reading world colour.
    vec3 a = outgoing.rgb / max(outgoing.a, .001);
    vec3 b = incoming.rgb / max(incoming.a, .001);
    float edgeA = min(1.0, fwidth(dot(a, vec3(.24, .67, .09))) * 4.5);
    float edgeB = min(1.0, fwidth(dot(b, vec3(.24, .67, .09))) * 4.5);
    float etched = proximity * envelope * .68;
    vec3 silver = vec3(.81, .87, .94);
    outgoing.rgb = mix(outgoing.rgb, silver * edgeA * outgoing.a, etched);
    incoming.rgb = mix(incoming.rgb, silver * edgeB * incoming.a, etched);
    vec4 ui = mix(outgoing, incoming, blend);
    float line = exp(-abs(d) / max(aa * 2.1, .0015)) * envelope;
    float coverage = max(outgoing.a, incoming.a);
    ui.rgb += silver * line * coverage * .28;
    // Both inputs are premultiplied. Never turn empty UI pixels into a dark backdrop.
    ui.a = clamp(ui.a, 0.0, 1.0);
    ui.rgb = min(ui.rgb, vec3(ui.a));
    fragColor = ui;
}
