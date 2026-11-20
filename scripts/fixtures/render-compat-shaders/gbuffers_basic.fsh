#version 120
varying vec4 vertexColor;
/* DRAWBUFFERS:0 */
void main() { gl_FragData[0] = vertexColor; }
