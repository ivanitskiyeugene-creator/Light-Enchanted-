varying vec2 leUv;
void main() {
    gl_Position = ftransform();
    leUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
}
