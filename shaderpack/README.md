# Light Enchanted 3.1 Hybrid Renderer

This is the high-end half of Light Enchanted 3.1 for **Minecraft 1.20.1 Forge**.

## Installation

1. Install **Oculus** and its required performance backend (Embeddium/Rubidium as appropriate for your Oculus build).
2. Place `lightenchanted-shaders-<version>.zip` in `.minecraft/shaderpacks`.
3. Select **Light Enchanted Hybrid Renderer** in the Oculus shader menu.
4. Select the `Low`, `Balanced`, or `Cinematic` profile in *Shader Pack Settings*.

The Light Enchanted mod still works without Oculus. Its existing emitter renderer remains the fallback path; this pack is the GPU deferred enhancement path.

## Current 3.1 renderer

- Layered beacon-beam pass with a broad atmospheric halo, a travelling inner shimmer, and a white-hot core.
- Depth-tested beam geometry: walls, floors, entities and block entities correctly occlude the volume instead of producing a flat billboard.
- Java fallback per-ray occlusion for terrain, grates, fences, custom fan blades and living entities.
- Soft downstream beam silhouettes for players, mobs and spinning fan blades, projected onto the next receiver surface.
- Hardware shadow-map direct shadows for terrain, cutout geometry, entities and block entities.
- Profile-controlled PCF soft penumbrae, depth-reconstructed contact shadows and GTAO.
- Emissive bright-pass bloom around beam cores and impact pools, plus depth-occluded volumetric fog.
- Cinematic-only conservative screen-space reflection tracing, HDR-style ACES response and vignette.
- Distinct Low / Balanced / Cinematic budgets.

The pack intentionally targets the portable Oculus 1.20.1 shader pipeline and does not require Iris-only compute/SSBO extensions. Without the pack, the same layered core, ray occlusion and projected-shadow path remains available in the built-in Forge renderer.
