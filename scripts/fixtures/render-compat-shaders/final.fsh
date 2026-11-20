#version 120
uniform sampler2D colortex0;
varying vec2 textureCoordinate;
// Original fixture: a visible post-process verifies that the shader pack really renders.
void main() {
    vec3 color = texture2D(colortex0, textureCoordinate).rgb;
    gl_FragColor = vec4(mix(color, color.bgr, 0.2) * 0.85 + vec3(0.02, 0.03, 0.04), 1.0);
}
