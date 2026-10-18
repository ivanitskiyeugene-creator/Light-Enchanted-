varying vec2 leTexcoord;
varying vec4 leColor;
void main() {
    leTexcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    leColor = gl_Color;
    gl_Position = ftransform();
}
