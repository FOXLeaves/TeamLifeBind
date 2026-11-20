#version 120
varying vec2 textureCoordinate;
void main() {
    gl_Position = ftransform();
    textureCoordinate = gl_MultiTexCoord0.xy;
}
