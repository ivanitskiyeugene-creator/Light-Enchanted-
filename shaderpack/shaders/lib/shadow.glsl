// Included after lib/options.glsl by the G-buffer material programs.
const int shadowMapResolution = 2048; // [1024 2048 4096]
const float shadowDistance = 128.0; // [64.0 80.0 96.0 128.0 160.0]
const bool shadowtex0Nearest = true;
const bool shadowtex1Nearest = true;
const bool shadowtex0Mipmap = false;
const bool shadowtex1Mipmap = false;

float leHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

vec2 leRotate(vec2 v, float a) {
    float s = sin(a), c = cos(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

float leShadowRaw(sampler2D depthMap, vec3 shadowCoord, vec2 offset) {
    float mapDepth = texture2D(depthMap, shadowCoord.xy + offset).r;
    return step(shadowCoord.z - LE_SHADOW_BIAS, mapDepth);
}

float leSoftShadow(sampler2D opaqueDepth, vec3 shadowCoord, float ndotl, vec2 noiseSeed) {
    if (shadowCoord.x <= 0.001 || shadowCoord.x >= 0.999 || shadowCoord.y <= 0.001 || shadowCoord.y >= 0.999 || shadowCoord.z <= 0.0 || shadowCoord.z >= 1.0) return 1.0;
    float texel = 1.0 / float(shadowMapResolution);
    float penumbra = mix(0.7, 2.6, 1.0 - clamp(ndotl, 0.0, 1.0));
    float angle = leHash(noiseSeed) * 6.2831853;
    float sum = leShadowRaw(opaqueDepth, shadowCoord, vec2(0.0));

    #if LE_SHADOW_SAMPLES >= 1
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2( 1.0,  0.0), angle) * texel * penumbra);
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2(-1.0,  0.0), angle) * texel * penumbra);
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2( 0.0,  1.0), angle) * texel * penumbra);
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2( 0.0, -1.0), angle) * texel * penumbra);
    #endif
    #if LE_SHADOW_SAMPLES >= 2
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2( 0.707,  0.707), angle) * texel * penumbra);
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2(-0.707,  0.707), angle) * texel * penumbra);
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2( 0.707, -0.707), angle) * texel * penumbra);
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2(-0.707, -0.707), angle) * texel * penumbra);
    #endif
    #if LE_SHADOW_SAMPLES >= 3
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2( 1.75,  0.35), angle) * texel * penumbra);
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2(-1.75, -0.35), angle) * texel * penumbra);
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2( 0.35, -1.75), angle) * texel * penumbra);
    sum += leShadowRaw(opaqueDepth, shadowCoord, leRotate(vec2(-0.35,  1.75), angle) * texel * penumbra);
    #endif
    #if LE_SHADOW_SAMPLES == 0
    return sum;
    #elif LE_SHADOW_SAMPLES == 1
    return sum * 0.2;
    #elif LE_SHADOW_SAMPLES == 2
    return sum / 9.0;
    #else
    return sum / 13.0;
    #endif
}
