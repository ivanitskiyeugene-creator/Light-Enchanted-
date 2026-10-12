#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
out vec4 fragColor;

uniform vec2 InSize;
uniform vec2 OutSize;
uniform float Time;

void main() {
    // Aspect ratio corrected UV coordinates centered at (0, 0)
    vec2 uv = texCoord - 0.5;
    float aspect = OutSize.x / OutSize.y;
    vec2 aspectUv = vec2(uv.x * aspect, uv.y);

    // Fisheye Lens Barrel Distortion (Curvature)
    float r2 = dot(aspectUv, aspectUv);
    float distortion = 0.18; // Authentic CCTV dome curvature strength
    vec2 distortedAspectUv = aspectUv * (1.0 + distortion * r2);
    vec2 distortedUv = vec2(distortedAspectUv.x / aspect, distortedAspectUv.y) + 0.5;

    // Check optical frame boundary
    if (distortedUv.x < 0.0 || distortedUv.x > 1.0 || distortedUv.y < 0.0 || distortedUv.y > 1.0) {
        fragColor = vec4(0.01, 0.02, 0.04, 1.0);
        return;
    }

    // Chromatic Aberration (Radial color fringing on lens periphery)
    float chroma = 0.005 * r2;
    vec2 rUv = (distortedUv - 0.5) * (1.0 + chroma) + 0.5;
    vec2 bUv = (distortedUv - 0.5) * (1.0 - chroma) + 0.5;

    float r = texture(DiffuseSampler, clamp(rUv, 0.0, 1.0)).r;
    float g = texture(DiffuseSampler, distortedUv).g;
    float b = texture(DiffuseSampler, clamp(bUv, 0.0, 1.0)).b;
    vec3 color = vec3(r, g, b);

    // Procedural Dynamic CCTV Scanlines (Полоски без растровой текстуры)
    float scanlineCount = OutSize.y * 0.85;
    float scanline = sin(distortedUv.y * scanlineCount * 3.14159) * 0.5 + 0.5;
    float scanlineFactor = 0.90 + 0.10 * scanline;
    color *= scanlineFactor;

    // Rolling CRT Refresh Sweep Bar (Бегущая полоса обновления)
    float rollPos = mod(Time * 0.18, 1.0);
    float rollDist = abs(distortedUv.y - rollPos);
    if (rollDist < 0.08) {
        float rollIntensity = (1.0 - rollDist / 0.08) * 0.05;
        color += vec3(rollIntensity * 0.4, rollIntensity * 0.8, rollIntensity);
    }

    // CRT Phosphor Color Grading & Slight Surveillance Contrast
    color = pow(color, vec3(0.96));
    color *= vec3(0.95, 1.03, 1.02); // Cold surveillance CRT tint

    // Subtle Sensor Noise Grain
    float noise = fract(sin(dot(distortedUv * (Time + 1.0), vec2(12.9898, 78.233))) * 43758.5453);
    color += (noise - 0.5) * 0.016;

    // Corner Vignette & Lens Edge Shadow
    float vignette = smoothstep(0.72, 0.38, length(aspectUv) * 0.95);
    color *= vignette;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
