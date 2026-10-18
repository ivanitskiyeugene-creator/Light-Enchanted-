
#include "/lib/options.glsl"

uniform sampler2D gcolor;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform vec3 sunPosition;
uniform float viewWidth;
uniform float viewHeight;

varying vec2 leUv;

vec3 leViewPosition(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = gbufferProjectionInverse * clip;
    return view.xyz / max(view.w, 0.00001);
}

vec2 leProject(vec3 viewPos) {
    vec4 clip = gbufferProjection * vec4(viewPos, 1.0);
    return clip.xy / max(clip.w, 0.00001) * 0.5 + 0.5;
}

float leDepth(vec2 uv) { return texture2D(depthtex0, clamp(uv, vec2(0.001), vec2(0.999))).r; }

// Screen-space ray march toward the primary light. Its static interleaving is frame-stable:
// no frame-index noise is injected, so camera-idle scenes cannot shimmer.
float leContactShadow(vec3 origin, vec2 uv) {
    #if LE_CONTACT_STEPS == 0
    return 1.0;
    #else
    vec3 towardLight = normalize(sunPosition);
    float jitter = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    float stepLength = LE_CONTACT_LENGTH / float(LE_CONTACT_STEPS);
    for (int i = 1; i <= LE_CONTACT_STEPS; ++i) {
        vec3 probe = origin + towardLight * (float(i) - jitter) * stepLength;
        vec2 probeUv = leProject(probe);
        if (probeUv.x <= 0.002 || probeUv.x >= 0.998 || probeUv.y <= 0.002 || probeUv.y >= 0.998) break;
        vec3 scene = leViewPosition(probeUv, leDepth(probeUv));
        // A scene point significantly closer to the camera than the lightward probe blocks the ray.
        if (length(scene) + 0.055 < length(probe)) return 0.0;
    }
    return 1.0;
    #endif
}

float leGtao(vec3 center, vec2 uv) {
    #if LE_GTAO_SAMPLES == 0
    return 1.0;
    #else
    float radius = LE_GTAO_RADIUS / max(abs(center.z), 0.25);
    float occ = 0.0;
    float rotation = fract(sin(dot(gl_FragCoord.xy, vec2(41.13, 289.71))) * 19017.71);
    for (int i = 0; i < LE_GTAO_SAMPLES; ++i) {
        float a = 6.2831853 * (float(i) / float(LE_GTAO_SAMPLES) + rotation);
        vec2 dir = vec2(cos(a), sin(a));
        float ring = (0.35 + 0.65 * float(i) / float(max(LE_GTAO_SAMPLES - 1, 1))) * radius;
        vec3 samplePos = leViewPosition(uv + dir * ring, leDepth(uv + dir * ring));
        float delta = length(samplePos - center);
        float nearer = step(length(samplePos), length(center) - 0.025);
        occ += nearer * smoothstep(LE_GTAO_RADIUS, 0.0, delta);
    }
    return clamp(1.0 - occ / float(LE_GTAO_SAMPLES) * 0.62, 0.35, 1.0);
    #endif
}

// Integrates a small number of view-ray samples. Each sample reuses the occlusion trace,
// providing depth-aware shafts rather than a radial screen-space flare.
vec3 leVolumetricFog(vec3 endPoint) {
    #if LE_VOLUMETRIC_QUALITY == 0
    return vec3(0.0);
    #else
        #if LE_VOLUMETRIC_QUALITY == 1
        const int steps = 4;
        #else
        const int steps = 8;
        #endif
    vec3 lightDir = normalize(sunPosition);
    vec3 viewDir = normalize(-endPoint);
    float phase = pow(max(dot(viewDir, lightDir), 0.0), 10.0);
    float transmission = 0.0;
    for (int i = 1; i <= steps; ++i) {
        vec3 probe = endPoint * (float(i) / float(steps));
        transmission += leContactShadow(probe, leProject(probe));
    }
    float distanceFog = 1.0 - exp(-length(endPoint) * 0.018);
    return vec3(1.0, 0.80, 0.56) * phase * transmission / float(steps) * distanceFog * 0.19;
    #endif
}

// A conservative single-bounce screen-space reflection reserved for the Cinematic preset.
// It only samples hits visible in the current depth buffer, avoiding fake off-screen reflections.
vec3 leScreenReflection(vec3 origin) {
    #if LE_SSR_STEPS == 0
    return vec3(0.0);
    #else
    vec3 normal = normalize(cross(dFdx(origin), dFdy(origin)));
    if (dot(normal, origin) > 0.0) normal = -normal;
    vec3 direction = reflect(normalize(origin), normal);
    if (direction.z > -0.015) return vec3(0.0);
    for (int i = 1; i <= LE_SSR_STEPS; ++i) {
        vec3 probe = origin + direction * (float(i) * 0.34);
        vec2 probeUv = leProject(probe);
        if (probeUv.x <= 0.002 || probeUv.x >= 0.998 || probeUv.y <= 0.002 || probeUv.y >= 0.998) break;
        vec3 scene = leViewPosition(probeUv, leDepth(probeUv));
        if (length(scene - probe) < 0.32 && length(scene) < length(probe) + 0.24) {
            float fresnel = pow(1.0 - max(dot(normalize(-origin), normal), 0.0), 5.0);
            return texture2D(gcolor, probeUv).rgb * fresnel * LE_SSR_STRENGTH;
        }
    }
    return vec3(0.0);
    #endif
}

vec3 leAces(vec3 x) {
    float a = 2.51; float b = 0.03; float c = 2.43; float d = 0.59; float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 color = texture2D(gcolor, leUv).rgb;
    float depth = leDepth(leUv);
    if (depth < 0.99999) {
        vec3 viewPos = leViewPosition(leUv, depth);
        float contact = leContactShadow(viewPos, leUv);
        float ao = leGtao(viewPos, leUv);
        color *= mix(0.68, 1.0, contact) * ao;
        color += leVolumetricFog(viewPos);
        color += leScreenReflection(viewPos);
    }

    color *= LE_EXPOSURE;
    color = leAces(color);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, LE_SATURATION);
    color = (color - 0.5) * LE_CONTRAST + 0.5;
    float vignette = dot(leUv - 0.5, leUv - 0.5) * LE_VIGNETTE * 3.0;
    color *= 1.0 - vignette;

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(clamp(color, 0.0, 1.0), 1.0);
}
