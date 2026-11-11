// Light Enchanted beam fragment stage.
// The Java renderer supplies a layered shell and a white-hot core; this stage
// adds the last bit of volumetric response without making the beam a flat quad.
uniform sampler2D texture;
uniform float frameTimeCounter;

varying vec2 leBeamUv;
varying vec4 leBeamColor;
varying vec3 leBeamViewPos;

void main() {
    vec4 texel = texture2D(texture, leBeamUv);
    vec2 radialUv = leBeamUv - vec2(0.5);
    float radial = clamp(1.0 - length(radialUv) * 2.05, 0.0, 1.0);
    float core = pow(radial, 3.2);

    // A very low-amplitude travelling modulation gives long beams a living medium
    // while remaining stable enough not to look like noisy transparency sorting.
    float shimmer = 0.965 + 0.035 * sin(frameTimeCounter * 2.6 + leBeamViewPos.y * 0.8);
    float alpha = texel.a * leBeamColor.a * mix(0.72, 1.0, radial) * shimmer;
    if (alpha < 0.004) discard;

    vec3 beamColor = max(leBeamColor.rgb, vec3(0.001));
    vec3 emission = beamColor * (0.92 + core * 1.35);
    emission += vec3(1.0) * core * 0.24;

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(emission, alpha);
}
