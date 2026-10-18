
#include "/lib/options.glsl"
#include "/lib/shadow.glsl"

uniform sampler2D texture;
uniform sampler2D lightmap;
uniform sampler2D shadowtex0;
uniform sampler2D shadowtex1;
uniform sampler2D shadowcolor0;
uniform float frameTimeCounter;

varying vec2 leTexcoord;
varying vec2 leLightcoord;
varying vec4 leColor;
varying vec3 leNormal;
varying vec3 leViewPos;
varying vec3 leShadowCoord;
varying float leNdotL;
varying float leMaterial;

vec3 leEmitterEmission(float id) {
    if (id > 20000.5 && id < 20001.5) return vec3(1.0, 0.72, 0.36) * LE_EMITTER_BOOST;
    if (id > 20001.5 && id < 20002.5) return vec3(1.0, 0.82, 0.48) * LE_EMITTER_BOOST;
    if (id > 20002.5 && id < 20003.5) return vec3(0.56, 0.78, 1.00) * LE_EMITTER_BOOST;
    if (id > 20003.5 && id < 20004.5) return vec3(0.45, 0.84, 0.92) * (LE_EMITTER_BOOST * 0.35);
    return vec3(0.0);
}

void main() {
    vec4 base = texture2D(texture, leTexcoord) * leColor;
    if (base.a < 0.10) discard;

    vec2 lm = leLightcoord;
    float sunFacing = max(leNdotL, 0.0);
    float sunVisibility = leSoftShadow(shadowtex1, leShadowCoord, sunFacing, gl_FragCoord.xy + frameTimeCounter);
    sunVisibility = mix(1.0 - LE_SHADOW_STRENGTH, 1.0, sunVisibility);

    #if LE_COLORED_SHADOWS == 1
    if (texture2D(shadowtex0, leShadowCoord.xy).r < leShadowCoord.z - LE_SHADOW_BIAS) {
        vec4 stained = texture2D(shadowcolor0, leShadowCoord.xy);
        base.rgb *= mix(vec3(1.0), max(stained.rgb, vec3(0.10)), stained.a * (1.0 - lm.x));
    }
    #endif

    // The lightmap supplies block light and ambient world light. Direct sunlight receives the soft map result.
    vec3 worldLight = texture2D(lightmap, vec2(lm.x, lm.y * sunVisibility)).rgb;
    float hemispheric = 0.55 + 0.45 * max(leNormal.y, 0.0);
    vec3 lit = base.rgb * worldLight * hemispheric;
    lit += leEmitterEmission(leMaterial);

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(lit, base.a);
}
