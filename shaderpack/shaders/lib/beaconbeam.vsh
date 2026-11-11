// Light Enchanted beam vertex stage.
// Kept independent from the terrain gbuffer so the beam can remain emissive and
// use its own depth-stable UVs, even when the rest of the pack changes lighting.
varying vec2 leBeamUv;
varying vec4 leBeamColor;
varying vec3 leBeamViewPos;

void main() {
    leBeamUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    leBeamColor = gl_Color;

    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    leBeamViewPos = viewPos.xyz;
    gl_Position = gl_ProjectionMatrix * viewPos;
}
