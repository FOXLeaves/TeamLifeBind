#version 120
uniform sampler2D gtexture;
varying vec2 textureCoordinate;
varying vec4 vertexColor;
/* DRAWBUFFERS:0 */
void main() { gl_FragData[0] = texture2D(gtexture, textureCoordinate) * vertexColor; }
