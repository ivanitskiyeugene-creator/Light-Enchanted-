# Light Enchanted 3.0 hybrid rendering contract

## Why this is two renderers

Oculus takes ownership of Minecraft's shader program graph while a shader pack is enabled. A Forge mod cannot safely replace those programs and also remain compatible with arbitrary shader packs. Light Enchanted therefore keeps its game logic and Java beam renderer independent, and ships a first-party Oculus/Iris shader pack as the high-end renderer.

| Client configuration | Runtime path |
| --- | --- |
| Forge without Oculus/Iris | Existing Light Enchanted Java fallback: configurable beam geometry, gameplay ray hits, decorative dust/flares. |
| Oculus/Iris with another shader pack | The fallback remains deliberately available; no hard dependency or renderer takeover occurs. |
| Oculus with `lightenchanted-shaders-*.zip` selected | First-party GPU hybrid path: shadow map PCF, depth contact rays, GTAO, depth-occluded volumetric fog, conservative SSR (Cinematic), ACES response and emissive LE materials. |

`HybridRendererSupport` only probes optional APIs reflectively. It never links against Oculus classes, and a missing, changed, or incompatible API always falls back safely.

## Quality budgets

| Preset | Shadow map / PCF | Contact / AO | Volumetric fog | SSR |
| --- | --- | --- | --- | --- |
| **Low** | 1024, 5-tap | Disabled / 2 samples | Off | Off |
| **Balanced** | 2048, 9-tap | 6 / 6 samples | 4 view-ray samples | Off |
| **Cinematic** | 4096, 13-tap | 12 / 12 samples | 8 view-ray samples | 12-step conservative trace |

All profile knobs remain exposed in Oculus' Shader Pack Settings. The depth-trace jitter is spatially interleaved but not frame-indexed, which makes a stationary camera stable instead of flickering in the absence of a history buffer.

## Current and follow-up boundaries

The 3.0 base pack is intentionally portable to the Oculus 1.20.1 standard shader-pack pipeline: it does not require Iris-only compute images or SSBOs. It is a real GPU deferred/screen-space path, but screen-space rays necessarily cannot see occluders outside the current depth buffer.

The next enhanced stage is an **optional** voxel clipmap/colored-light extension. It can use compute/SSBO features where the loader exposes them, but it must retain this shadow-map and screen-space path on Oculus-only hardware. Dynamic per-emitter colour and shape will enter that extension through an explicit light registry/bridge; it must not be faked by raising the old CPU ray count.
