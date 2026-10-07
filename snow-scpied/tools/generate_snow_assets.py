#!/usr/bin/env python3
import json, os

ASSETS = "snow-scpied/src/main/resources/assets/snow_scpied"
DATA = "snow-scpied/src/main/resources/data/snow_scpied"

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    print("wrote", path)

# 1. Blockstates for Footprints (16 rotations)
for fp in ["decal_boots", "decal_claws", "decal_bare_feet"]:
    variants = {}
    for r in range(16):
        y_rot = int(r * 22.5) % 360
        variants[f"rotation={r}"] = {
            "model": f"snow_scpied:block/{fp}",
            "y": y_rot
        }
    write(f"{ASSETS}/blockstates/{fp}.json", {"variants": variants})

    # Flat Carpet-like Decal Model
    write(f"{ASSETS}/models/block/{fp}.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": f"snow_scpied:block/{fp}",
            "texture": f"snow_scpied:block/{fp}"
        },
        "elements": [
            {
                "from": [0, 0.05, 0],
                "to": [16, 0.1, 16],
                "faces": {
                    "up": {"texture": "#texture", "uv": [0, 0, 16, 16]},
                    "down": {"texture": "#texture", "uv": [0, 0, 16, 16]}
                }
            }
        ]
    })
    write(f"{ASSETS}/models/item/{fp}.json", {"parent": f"snow_scpied:block/{fp}"})

# 2. Blockstates for Blood Splatters (6 facings)
for blood in ["decal_blood_human", "decal_blood_acid", "decal_blood_anomalous", "decal_blood_ender"]:
    write(f"{ASSETS}/blockstates/{blood}.json", {
        "variants": {
            "facing=up": {"model": f"snow_scpied:block/{blood}"},
            "facing=down": {"model": f"snow_scpied:block/{blood}", "x": 180},
            "facing=north": {"model": f"snow_scpied:block/{blood}", "x": 90},
            "facing=south": {"model": f"snow_scpied:block/{blood}", "x": 270},
            "facing=west": {"model": f"snow_scpied:block/{blood}", "x": 90, "y": 270},
            "facing=east": {"model": f"snow_scpied:block/{blood}", "x": 90, "y": 90}
        }
    })

    write(f"{ASSETS}/models/block/{blood}.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": f"snow_scpied:block/{blood}",
            "texture": f"snow_scpied:block/{blood}"
        },
        "elements": [
            {
                "from": [0, 0.05, 0],
                "to": [16, 0.1, 16],
                "faces": {
                    "up": {"texture": "#texture", "uv": [0, 0, 16, 16]},
                    "down": {"texture": "#texture", "uv": [0, 0, 16, 16]}
                }
            }
        ]
    })
    write(f"{ASSETS}/models/item/{blood}.json", {"parent": f"snow_scpied:block/{blood}"})

# 3. Loot Tables
all_blocks = [
    "decal_boots", "decal_claws", "decal_bare_feet",
    "decal_blood_human", "decal_blood_acid", "decal_blood_anomalous", "decal_blood_ender"
]

for b in all_blocks:
    write(f"{DATA}/loot_tables/blocks/{b}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": f"snow_scpied:{b}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]
        }]
    })
