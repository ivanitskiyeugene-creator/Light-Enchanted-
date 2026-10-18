# Light Enchanted Hybrid Renderer

This is the high-end half of Light Enchanted 3.0 for **Minecraft 1.20.1 Forge**.

## Installation

1. Install **Oculus** and its required performance backend (Embeddium/Rubidium as appropriate for your Oculus build).
2. Place `lightenchanted-shaders-<version>.zip` in `.minecraft/shaderpacks`.
3. Select **Light Enchanted Hybrid Renderer** in the Oculus shader menu.
4. Select the `Low`, `Balanced`, or `Cinematic` profile in *Shader Pack Settings*.

The Light Enchanted mod still works without Oculus. Its existing emitter renderer remains the fallback path; this pack is the GPU deferred enhancement path.

## Current 3.0 renderer

- Hardware shadow-map direct shadows for terrain, cutout geometry, entities and block entities.
- Profile-controlled PCF soft penumbrae.
- Depth-reconstructed contact-shadow tracing and GTAO in the composite pass, plus depth-occluded volumetric fog.
- Cinematic-only conservative screen-space reflection tracing, plus HDR-style ACES colour response and vignette.
- Native recognition of Light Enchanted emitters as emissive scene materials.
- Distinct Low / Balanced / Cinematic budgets.

The pack intentionally targets the portable Oculus 1.20.1 shader pipeline: the first release does not require the newer Iris-only compute/SSBO extensions. The next tracing stage can use those extensions opportunistically rather than making the fallback unavailable.
