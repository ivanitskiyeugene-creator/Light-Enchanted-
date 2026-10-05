# Light Enchanted — CurseForge & Modrinth Description

> **Ready-to-use description template. Copy and paste into CurseForge / Modrinth mod page description.**

---

# 🌟 Light Enchanted: Cinematic Beams, Spotlights & Micro-RT Shadows

**Light Enchanted** brings stunning volumetric lighting, aimable spotlights, and real-time shadow-casting god rays to **Minecraft 1.20.1 (Forge)**.

Whether you're building an atmospheric bunker, a futuristic sci-fi laboratory, a medieval cathedral with god rays streaming through stained glass, or a stage with rotating spotlights — Light Enchanted gives you complete creative control.

---

## 📸 Key Features

* 🔦 **Aim Anywhere (3D Vector Aiming):** No more boring vertical-only beacons. Point your beam down, sideways, diagonally, or straight through narrow windows using the **Beam Tuner** item or crosshair targeting up to 128 blocks away.
* 🌫️ **Micro-RT Shadows & Volumetric God Rays:** Built-in geometric micro-raytracer scans obstacles in front of the lens in real time. Metal grates, ventilation fans, fences, chains, and **custom 3D modded models** slice the light cone into dense individual volumetric shafts, casting sharp projected silhouettes on floors and walls!
* 🎨 **Live In-Game Editor GUI:** Right-click the emitter with an empty hand to tweak colors with RGB sliders, HEX input (`#FFB347`), or smooth **Rainbow** cycling. Adjust cone spread, width, height, pulse frequency, and continuous rotation.
* 💡 **Real Block Lighting at Target (Light Level 15):** The beam genuinely illuminates its destination! Invisible light level 15 is automatically placed on the target surface, lighting up terrain, mobs, and players.
* 👻 **Creative Invisible Emitter:** Perfect for mapmakers and builders. Walk-through, invisible in-game, with per-axis **±8 block XYZ origin offset sliders** — hide the block inside walls, chandeliers, or ceilings while the beam emits from any point.
* 🌈 **Shader & Performance Friendly:** Built on Minecraft's native `beaconBeam` rendering pass for 100% out-of-the-box compatibility with **Iris, Oculus, OptiFine**, and popular shaders (**Complementary, BSL, SEUS, AstraLex**). Distance falloff ensures beams dissolve gracefully into the night sky.

---

## 🕹️ Controls & Quickstart

1. **Place the Light Emitter:** It glows and emits a default spotlight facing down.
2. **Open Editor:** Right-click with an empty hand to customize colors, shape, and effects in real-time.
3. **Aim the Beam:** 
   - Hold the **Beam Tuner** item, right-click the emitter to link it.
   - Right-click any block or click into the air in the distance (up to 128 blocks) to set the beam's destination point!
   - *Shift + Right-click air* with tuner to clear binding.
   - *Shift + Right-click emitter* to quickly toggle the beam on/off.

---

## 📐 6 Beam Shapes

* **Spotlight (Cone):** Narrow at the lens, expanding to a wide circle at the target. Ideal for cinema/stage lights and ceiling vents.
* **Laser (Cylinder):** Constant-radius concentrated energy beam.
* **Classic Column:** Square pillar reminiscent of beacon beams.
* **Helix:** Dual spiraling light ribbons.
* **Sheet:** Flat curtain/blade of light.
* **Star:** Quad cross-intersecting planes.

---

## 🛠️ Crafting Recipes

* **Light Emitter:** Glass surrounding Glowstone + Iron Ingots + Amethyst Shard.
* **Beam Tuner:** Diagonal Amethyst Shard + Iron Ingot.
* **Creative Emitter:** `/give @s lightenchanted:light_emitter_creative`

---

## 📦 Requirements & Installation

* **Minecraft:** 1.20.1
* **Mod Loader:** Forge 47.2.0+
* Drop `lightenchanted-x.x.x.jar` into your `.minecraft/mods` folder!
* Client and Server compatible (multiplayer fully supported).
