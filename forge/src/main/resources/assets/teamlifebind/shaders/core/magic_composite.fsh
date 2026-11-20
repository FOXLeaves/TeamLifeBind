#version 330
#extension GL_ARB_separate_shader_objects : require

// Only the independent emissive buffer blooms. Vanilla sky, snow and torches
// are never a bloom source. The game color attachment is not sampled, avoiding
// undefined read/write feedback on the main framebuffer.
layout(std140) uniform MagicFrame {
    mat4 InvViewProjection;
    vec4 ViewportTime;
    vec4 CenterRadius;
    vec4 AxisExtent;
    vec4 TintTier;
    vec4 Animation;
    vec4 Profile;
    vec4 Dynamics;
    vec4 Identity;
};
uniform sampler2D MagicColor;
uniform sampler2D SceneDepth;
layout(location = 0) in vec2 screenUv;
layout(location = 0) out vec4 fragColor;

float worldDistance(vec2 uv) {
    float d = texture(SceneDepth, uv).r;
    if (ViewportTime.w < 1.5 ? d <= 0.0000001 : d >= 0.9999999) return 1e6; // clear / sky
    vec4 p = InvViewProjection * vec4(uv * 2.0 - 1.0, mod(ViewportTime.w, 2.0) > 0.5 ? d : d * 2.0 - 1.0, 1.0);
    return abs(p.w) > 1e-7 ? length(p.xyz / p.w) : 1e6;
}
vec3 bloomTap(vec2 uv, float distanceHere, float scale) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec3(0.0);
    float distanceThere = worldDistance(uv);
    // Conservative bilateral depth rejection: a foreground wall cannot be
    // painted by an effect visible only on the distant side of its silhouette.
    float tolerance = max(0.35 + scale * 0.10, min(distanceHere, distanceThere) * 0.018);
    float accept = 1.0 - smoothstep(tolerance, tolerance * 2.0, abs(distanceHere - distanceThere));
    return texture(MagicColor, uv).rgb * accept;
}
void main() {
    vec2 uv = screenUv;
    vec2 pixel = 1.0 / vec2(textureSize(MagicColor, 0));
    float distanceHere = worldDistance(uv);
    vec3 center = texture(MagicColor, uv).rgb;
    vec3 glow = vec3(0.0);
    // Three spatial scales in twelve taps. This bound remains constant even
    // when many simultaneous spells share the emissive target.
    for (int ring = 0; ring < 3; ++ring) {
        float size = ring == 0 ? 1.5 : (ring == 1 ? 4.5 : 11.0);
        float weight = ring == 0 ? 0.10 : (ring == 1 ? 0.065 : 0.035);
        vec2 offset = pixel * size;
        glow += bloomTap(uv + vec2(offset.x, offset.y), distanceHere, size) * weight;
        glow += bloomTap(uv + vec2(-offset.x, offset.y), distanceHere, size) * weight;
        glow += bloomTap(uv + vec2(offset.x, -offset.y), distanceHere, size) * weight;
        glow += bloomTap(uv - offset, distanceHere, size) * weight;
    }
    // Four restrained anamorphic rays are driven by genuinely hot magic only.
    vec2 ray = pixel * vec2(18.0, 3.0);
    vec3 streak = bloomTap(uv + vec2(ray.x, 0), distanceHere, 18.0)
                + bloomTap(uv - vec2(ray.x, 0), distanceHere, 18.0)
                + bloomTap(uv + vec2(0, ray.y), distanceHere, 3.0)
                + bloomTap(uv - vec2(0, ray.y), distanceHere, 3.0);
    glow += max(streak - vec3(0.28), vec3(0.0)) * 0.022;
    vec3 linearGlow = center * 0.88 + glow * 0.52;
    // Soft highlight compression is applied exactly once, after accumulating
    // every effect. It keeps violet/gold/cyan readable in a dense spell fight.
    vec3 displayGlow = vec3(1.0) - exp(-min(linearGlow, vec3(16.0)) * 0.95);
    if (max(displayGlow.r, max(displayGlow.g, displayGlow.b)) < 0.0005) discard;
    fragColor = vec4(displayGlow, 0.0);
}
