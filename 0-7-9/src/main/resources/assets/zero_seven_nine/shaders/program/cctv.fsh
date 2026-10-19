#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
out vec4 fragColor;

uniform vec2 InSize;
uniform vec2 OutSize;
uniform float Time;

float hash12(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    // Aspect-corrected lens coordinates. Slightly stronger than the previous CCTV pass,
    // while still leaving the outer scene legible during camera operation.
    vec2 centeredUv = texCoord - 0.5;
    float aspect = OutSize.x / OutSize.y;
    vec2 lensUv = vec2(centeredUv.x * aspect, centeredUv.y);
    float radiusSquared = dot(lensUv, lensUv);

    float distortion = 0.235;
    vec2 distortedLensUv = lensUv * (1.0 + distortion * radiusSquared);
    vec2 distortedUv = vec2(distortedLensUv.x / aspect, distortedLensUv.y) + 0.5;

    // Intermittent horizontal analogue tracking error. It is extremely short-lived and only
    // visible on occasional rows, not a disruptive full-frame glitch.
    float row = floor(distortedUv.y * OutSize.y * 0.5);
    float rowSignal = hash12(vec2(row, floor(Time * 10.0)));
    float trackingJitter = step(0.987, rowSignal) * (rowSignal - 0.987) * 1.35;
    distortedUv.x += trackingJitter;

    // The black/blue bezel at the curved optical boundary sells the physical monitor surface.
    if (distortedUv.x < 0.0 || distortedUv.x > 1.0 || distortedUv.y < 0.0 || distortedUv.y > 1.0) {
        fragColor = vec4(0.004, 0.012, 0.026, 1.0);
        return;
    }

    // Colour separation increases naturally toward the edge of the dome lens.
    float chroma = 0.0072 * radiusSquared + trackingJitter * 0.25;
    vec2 redUv = (distortedUv - 0.5) * (1.0 + chroma) + 0.5;
    vec2 blueUv = (distortedUv - 0.5) * (1.0 - chroma) + 0.5;
    vec3 color = vec3(
        texture(DiffuseSampler, clamp(redUv, 0.0, 1.0)).r,
        texture(DiffuseSampler, distortedUv).g,
        texture(DiffuseSampler, clamp(blueUv, 0.0, 1.0)).b
    );

    // Denser scanlines plus a subtle phosphor grille. The intensification is deliberately small
    // so doors, players and device markers remain usable in the live surveillance feed.
    float scanline = sin(distortedUv.y * OutSize.y * 3.14159265) * 0.5 + 0.5;
    float scanlineFactor = 0.835 + 0.165 * scanline;
    float phosphorGrille = 0.985 + 0.015 * sin(distortedUv.x * OutSize.x * 1.04719755);
    color *= scanlineFactor * phosphorGrille;

    // Rolling refresh bar shared in spirit with the UI monitor composite.
    float rollPosition = mod(Time * 0.24, 1.0);
    float rollDistance = abs(distortedUv.y - rollPosition);
    float rollGlow = 1.0 - smoothstep(0.0, 0.075, rollDistance);
    color += vec3(0.004, 0.016, 0.023) * rollGlow;

    // Cold phosphor grade with a modest surveillance contrast lift.
    color = pow(max(color, 0.0), vec3(0.93));
    color = (color - 0.5) * 1.075 + 0.5;
    color *= vec3(0.90, 1.035, 1.075);

    // More present but still fine sensor grain, quantised per screen row for an analogue feel.
    float grain = hash12(floor(distortedUv * OutSize * 0.72) + vec2(Time * 17.0, Time * 7.0));
    color += (grain - 0.5) * 0.022;

    // A corrected, deeper corner vignette with no reversed smoothstep edge ordering.
    float edgeDistance = length(lensUv);
    float vignette = 1.0 - smoothstep(0.36, 0.78, edgeDistance);
    color *= mix(0.62, 1.0, vignette);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
