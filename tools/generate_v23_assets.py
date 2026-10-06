#!/usr/bin/env python3
import json, os

ASSETS = "src/main/resources/assets/lightenchanted"
DATA = "src/main/resources/data/lightenchanted"

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    print("wrote", path)

# 1. Blockstates & Block Models & Item Models
# optical_mirror
write(f"{ASSETS}/blockstates/optical_mirror.json", {
    "variants": {
        "facing=down,angle=0": {"model": "lightenchanted:block/optical_mirror", "x": 180},
        "facing=down,angle=1": {"model": "lightenchanted:block/optical_mirror", "x": 180, "y": 45},
        "facing=down,angle=2": {"model": "lightenchanted:block/optical_mirror", "x": 180, "y": 90},
        "facing=down,angle=3": {"model": "lightenchanted:block/optical_mirror", "x": 180, "y": 135},
        "facing=down,angle=4": {"model": "lightenchanted:block/optical_mirror", "x": 180, "y": 180},
        "facing=down,angle=5": {"model": "lightenchanted:block/optical_mirror", "x": 180, "y": 225},
        "facing=down,angle=6": {"model": "lightenchanted:block/optical_mirror", "x": 180, "y": 270},
        "facing=down,angle=7": {"model": "lightenchanted:block/optical_mirror", "x": 180, "y": 315},
        "facing=up,angle=0": {"model": "lightenchanted:block/optical_mirror"},
        "facing=up,angle=1": {"model": "lightenchanted:block/optical_mirror", "y": 45},
        "facing=up,angle=2": {"model": "lightenchanted:block/optical_mirror", "y": 90},
        "facing=up,angle=3": {"model": "lightenchanted:block/optical_mirror", "y": 135},
        "facing=up,angle=4": {"model": "lightenchanted:block/optical_mirror", "y": 180},
        "facing=up,angle=5": {"model": "lightenchanted:block/optical_mirror", "y": 225},
        "facing=up,angle=6": {"model": "lightenchanted:block/optical_mirror", "y": 270},
        "facing=up,angle=7": {"model": "lightenchanted:block/optical_mirror", "y": 315},
        "facing=north,angle=0": {"model": "lightenchanted:block/optical_mirror", "x": 90},
        "facing=north,angle=1": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 45},
        "facing=north,angle=2": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 90},
        "facing=north,angle=3": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 135},
        "facing=north,angle=4": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 180},
        "facing=north,angle=5": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 225},
        "facing=north,angle=6": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 270},
        "facing=north,angle=7": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 315},
        "facing=south,angle=0": {"model": "lightenchanted:block/optical_mirror", "x": 270},
        "facing=south,angle=1": {"model": "lightenchanted:block/optical_mirror", "x": 270, "y": 45},
        "facing=south,angle=2": {"model": "lightenchanted:block/optical_mirror", "x": 270, "y": 90},
        "facing=south,angle=3": {"model": "lightenchanted:block/optical_mirror", "x": 270, "y": 135},
        "facing=south,angle=4": {"model": "lightenchanted:block/optical_mirror", "x": 270, "y": 180},
        "facing=south,angle=5": {"model": "lightenchanted:block/optical_mirror", "x": 270, "y": 225},
        "facing=south,angle=6": {"model": "lightenchanted:block/optical_mirror", "x": 270, "y": 270},
        "facing=south,angle=7": {"model": "lightenchanted:block/optical_mirror", "x": 270, "y": 315},
        "facing=west,angle=0": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 270},
        "facing=west,angle=1": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 315},
        "facing=west,angle=2": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 0},
        "facing=west,angle=3": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 45},
        "facing=west,angle=4": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 90},
        "facing=west,angle=5": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 135},
        "facing=west,angle=6": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 180},
        "facing=west,angle=7": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 225},
        "facing=east,angle=0": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 90},
        "facing=east,angle=1": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 135},
        "facing=east,angle=2": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 180},
        "facing=east,angle=3": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 225},
        "facing=east,angle=4": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 270},
        "facing=east,angle=5": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 315},
        "facing=east,angle=6": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 0},
        "facing=east,angle=7": {"model": "lightenchanted:block/optical_mirror", "x": 90, "y": 45}
    }
})

write(f"{ASSETS}/models/block/optical_mirror.json", {
    "parent": "minecraft:block/block",
    "textures": {
        "particle": "lightenchanted:block/optical_mirror",
        "texture": "lightenchanted:block/optical_mirror"
    },
    "elements": [
        {"from": [1, 0, 1], "to": [15, 2, 15], "faces": {"down": {"texture": "#texture", "cullface": "down"}, "up": {"texture": "#texture"}, "north": {"texture": "#texture"}, "south": {"texture": "#texture"}, "west": {"texture": "#texture"}, "east": {"texture": "#texture"}}},
        {"from": [2, 2, 2], "to": [14, 5, 14], "faces": {"up": {"texture": "#texture"}, "north": {"texture": "#texture"}, "south": {"texture": "#texture"}, "west": {"texture": "#texture"}, "east": {"texture": "#texture"}}}
    ]
})
write(f"{ASSETS}/models/item/optical_mirror.json", {"parent": "lightenchanted:block/optical_mirror"})

# photoreceptor
write(f"{ASSETS}/blockstates/photoreceptor.json", {
    "variants": {
        "facing=north": {"model": "lightenchanted:block/photoreceptor"},
        "facing=south": {"model": "lightenchanted:block/photoreceptor", "y": 180},
        "facing=west": {"model": "lightenchanted:block/photoreceptor", "y": 270},
        "facing=east": {"model": "lightenchanted:block/photoreceptor", "y": 90},
        "facing=up": {"model": "lightenchanted:block/photoreceptor", "x": 270},
        "facing=down": {"model": "lightenchanted:block/photoreceptor", "x": 90}
    }
})
write(f"{ASSETS}/models/block/photoreceptor.json", {
    "parent": "minecraft:block/cube_all",
    "textures": {"all": "lightenchanted:block/photoreceptor"}
})
write(f"{ASSETS}/models/item/photoreceptor.json", {"parent": "lightenchanted:block/photoreceptor"})

# haze_machine
write(f"{ASSETS}/blockstates/haze_machine.json", {
    "variants": {
        "facing=north": {"model": "lightenchanted:block/haze_machine"},
        "facing=south": {"model": "lightenchanted:block/haze_machine", "y": 180},
        "facing=west": {"model": "lightenchanted:block/haze_machine", "y": 270},
        "facing=east": {"model": "lightenchanted:block/haze_machine", "y": 90}
    }
})
write(f"{ASSETS}/models/block/haze_machine.json", {
    "parent": "minecraft:block/cube_all",
    "textures": {"all": "lightenchanted:block/haze_machine"}
})
write(f"{ASSETS}/models/item/haze_machine.json", {"parent": "lightenchanted:block/haze_machine"})

# industrial_floodlight
write(f"{ASSETS}/blockstates/industrial_floodlight.json", {"variants": {"": {"model": "lightenchanted:block/industrial_floodlight"}}})
write(f"{ASSETS}/models/block/industrial_floodlight.json", {
    "parent": "minecraft:block/cube_all",
    "textures": {"all": "lightenchanted:block/industrial_floodlight"}
})
write(f"{ASSETS}/models/item/industrial_floodlight.json", {"parent": "lightenchanted:block/industrial_floodlight"})

# fresnel_spotlight
write(f"{ASSETS}/blockstates/fresnel_spotlight.json", {"variants": {"": {"model": "lightenchanted:block/fresnel_spotlight"}}})
write(f"{ASSETS}/models/block/fresnel_spotlight.json", {
    "parent": "minecraft:block/cube_all",
    "textures": {"all": "lightenchanted:block/fresnel_spotlight"}
})
write(f"{ASSETS}/models/item/fresnel_spotlight.json", {"parent": "lightenchanted:block/fresnel_spotlight"})

# laser_projector
write(f"{ASSETS}/blockstates/laser_projector.json", {"variants": {"": {"model": "lightenchanted:block/laser_projector"}}})
write(f"{ASSETS}/models/block/laser_projector.json", {
    "parent": "minecraft:block/cube_all",
    "textures": {"all": "lightenchanted:block/laser_projector"}
})
write(f"{ASSETS}/models/item/laser_projector.json", {"parent": "lightenchanted:block/laser_projector"})

# light_grate
write(f"{ASSETS}/blockstates/light_grate.json", {"variants": {"": {"model": "lightenchanted:block/light_grate"}}})
write(f"{ASSETS}/models/block/light_grate.json", {
    "parent": "minecraft:block/cube_all",
    "textures": {"all": "lightenchanted:block/light_grate"}
})
write(f"{ASSETS}/models/item/light_grate.json", {"parent": "lightenchanted:block/light_grate"})

# fluorescent_tube
write(f"{ASSETS}/blockstates/fluorescent_tube.json", {
    "variants": {
        "axis=y": {"model": "lightenchanted:block/fluorescent_tube"},
        "axis=z": {"model": "lightenchanted:block/fluorescent_tube", "x": 90},
        "axis=x": {"model": "lightenchanted:block/fluorescent_tube", "x": 90, "y": 90}
    }
})
write(f"{ASSETS}/models/block/fluorescent_tube.json", {
    "parent": "minecraft:block/cube_all",
    "textures": {"all": "lightenchanted:block/fluorescent_tube"}
})
write(f"{ASSETS}/models/item/fluorescent_tube.json", {"parent": "lightenchanted:block/fluorescent_tube"})

# 2. Loot Tables
simple_blocks = [
    "optical_mirror", "photoreceptor", "haze_machine",
    "industrial_floodlight", "fresnel_spotlight", "laser_projector",
    "light_grate", "fluorescent_tube"
]

for b in simple_blocks:
    write(f"{DATA}/loot_tables/blocks/{b}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": f"lightenchanted:{b}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]
        }]
    })

# 3. Recipes
write(f"{DATA}/recipes/optical_mirror.json", {
    "type": "minecraft:crafting_shaped",
    "pattern": ["GGG", "III", "SSS"],
    "key": {
        "G": {"item": "minecraft:glass_pane"},
        "I": {"item": "minecraft:iron_ingot"},
        "S": {"item": "minecraft:smooth_stone_slab"}
    },
    "result": {"item": "lightenchanted:optical_mirror", "count": 2}
})

write(f"{DATA}/recipes/photoreceptor.json", {
    "type": "minecraft:crafting_shaped",
    "pattern": ["QQQ", "RDR", "III"],
    "key": {
        "Q": {"item": "minecraft:quartz"},
        "R": {"item": "minecraft:redstone"},
        "D": {"item": "minecraft:daylight_detector"},
        "I": {"item": "minecraft:iron_ingot"}
    },
    "result": {"item": "lightenchanted:photoreceptor", "count": 1}
})

write(f"{DATA}/recipes/haze_machine.json", {
    "type": "minecraft:crafting_shaped",
    "pattern": ["III", "ICI", "IRI"],
    "key": {
        "I": {"item": "minecraft:iron_ingot"},
        "C": {"item": "minecraft:campfire"},
        "R": {"item": "minecraft:redstone"}
    },
    "result": {"item": "lightenchanted:haze_machine", "count": 1}
})

write(f"{DATA}/recipes/industrial_floodlight.json", {
    "type": "minecraft:crafting_shaped",
    "pattern": ["III", "GLG", "IRI"],
    "key": {
        "I": {"item": "minecraft:iron_ingot"},
        "G": {"item": "minecraft:glowstone_dust"},
        "L": {"item": "lightenchanted:light_emitter"},
        "R": {"item": "minecraft:redstone"}
    },
    "result": {"item": "lightenchanted:industrial_floodlight", "count": 1}
})

write(f"{DATA}/recipes/fresnel_spotlight.json", {
    "type": "minecraft:crafting_shaped",
    "pattern": ["III", "QLQ", " I "],
    "key": {
        "I": {"item": "minecraft:iron_ingot"},
        "Q": {"item": "minecraft:quartz"},
        "L": {"item": "lightenchanted:light_emitter"}
    },
    "result": {"item": "lightenchanted:fresnel_spotlight", "count": 1}
})

write(f"{DATA}/recipes/laser_projector.json", {
    "type": "minecraft:crafting_shaped",
    "pattern": ["CCC", "RDR", "III"],
    "key": {
        "C": {"item": "minecraft:copper_ingot"},
        "R": {"item": "minecraft:redstone"},
        "D": {"item": "minecraft:diamond"},
        "I": {"item": "minecraft:iron_ingot"}
    },
    "result": {"item": "lightenchanted:laser_projector", "count": 1}
})

write(f"{DATA}/recipes/light_grate.json", {
    "type": "minecraft:crafting_shaped",
    "pattern": ["BBB", "GLG", "III"],
    "key": {
        "B": {"item": "minecraft:iron_bars"},
        "G": {"item": "minecraft:glowstone_dust"},
        "L": {"item": "minecraft:sea_lantern"},
        "I": {"item": "minecraft:iron_ingot"}
    },
    "result": {"item": "lightenchanted:light_grate", "count": 4}
})

write(f"{DATA}/recipes/fluorescent_tube.json", {
    "type": "minecraft:crafting_shaped",
    "pattern": [" I ", "GGG", " I "],
    "key": {
        "I": {"item": "minecraft:iron_nugget"},
        "G": {"item": "minecraft:sea_lantern"}
    },
    "result": {"item": "lightenchanted:fluorescent_tube", "count": 2}
})
