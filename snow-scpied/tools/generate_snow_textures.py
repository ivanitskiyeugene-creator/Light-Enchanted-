#!/usr/bin/env python3
"""Generates all PNG textures and decals for Snow - SCPied v1.3.1:
- Boots tread footprint decal
- Fading snowy trail decal on dry ground
- Bare feet footprint decal
- SCP creature claws / pawprint decal
- Hooves footprint decal
- Blood splatters (crimson, acid green, black, ender purple)
- Snowy legs overlay
- 3D Volumetric isotropic sparkling snow mantle texture
- Snow Sprayer tool
"""
import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "snow_scpied", "textures")


def write_png(path, width, height, pixels):
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("4B", *px) for px in row)
        for row in pixels
    )

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        out += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return out

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)
    print("wrote", os.path.relpath(path))


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def gen_boots_footprint(path, size=32, is_snow_trail=False):
    pixels = []
    for y in range(size):
        row = []
        ny = (y - size * 0.5) / (size * 0.5)
        for x in range(size):
            nx = (x - size * 0.5) / (size * 0.5)

            in_heel = (ny > 0.4 and ny < 0.85 and abs(nx) < 0.40)
            in_forefoot = (ny > -0.85 and ny < 0.25 and abs(nx) < (0.45 - ny * 0.08))
            in_bridge = (ny >= 0.25 and ny <= 0.4 and abs(nx) < 0.25)

            if in_heel or in_forefoot or in_bridge:
                is_tread_groove = (y % 4 in (0, 1)) and not in_bridge
                if is_snow_trail:
                    depth = 0.90 if is_tread_groove else 0.60
                    alpha = int(depth * 240)
                    row.append((240, 248, 255, alpha))
                else:
                    depth = 0.95 if is_tread_groove else 0.65
                    alpha = int(depth * 255)
                    row.append((120, 138, 160, alpha))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_bare_feet(path, size=32):
    pixels = []
    c = size * 0.5
    for y in range(size):
        row = []
        ny = (y - size * 0.5) / (size * 0.5)
        for x in range(size):
            nx = (x - size * 0.5) / (size * 0.5)

            in_heel = (ny > 0.35 and ny < 0.8 and abs(nx) < 0.32)
            in_sole = (ny > -0.45 and ny <= 0.35 and abs(nx) < (0.38 - ny * 0.05))

            in_toes = False
            for toe_i, (tx, ty, tr) in enumerate([(-0.25, -0.75, 0.10), (-0.12, -0.82, 0.09), (0.0, -0.80, 0.08), (0.12, -0.75, 0.07), (0.24, -0.68, 0.06)]):
                if math.hypot(nx - tx, ny - ty) < tr:
                    in_toes = True

            if in_heel or in_sole or in_toes:
                row.append((125, 142, 165, 230))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_scp_claws_footprint(path, size=32):
    pixels = []
    c = size * 0.5
    for y in range(size):
        row = []
        for x in range(size):
            d_pad = math.hypot(x - c, y - (c + 4))
            in_pad = d_pad < 5.5

            claw1 = math.hypot(x - (c - 7), y - (c - 5)) < 3.2
            claw2 = math.hypot(x - c, y - (c - 8)) < 3.5
            claw3 = math.hypot(x - (c + 7), y - (c - 5)) < 3.2

            if in_pad or claw1 or claw2 or claw3:
                alpha = 220 if in_pad else 255
                row.append((100, 115, 138, alpha))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_blood_splatter(path, color_rgb=(180, 20, 25), size=32):
    pixels = []
    c = size * 0.5
    for y in range(size):
        row = []
        for x in range(size):
            dist = math.hypot(x - c, y - c)
            angle = math.atan2(y - c, x - c)

            noise_r = 7.0 + 3.0 * math.sin(angle * 5.0) + 2.0 * math.cos(angle * 3.0)

            is_droplet = (
                math.hypot(x - (c + 8), y - (c - 6)) < 2.0 or
                math.hypot(x - (c - 9), y - (c + 5)) < 1.8 or
                math.hypot(x - (c + 4), y - (c + 9)) < 2.2
            )

            if dist < noise_r or is_droplet:
                falloff = clamp(1.0 - (dist / noise_r) * 0.3) if dist < noise_r else 0.9
                r, g, b = color_rgb
                row.append((r, g, b, int(falloff * 230)))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_snowy_legs(path, size=32):
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            noise = (math.sin(x * 0.8) + math.cos(y * 0.9)) * 0.5 + 0.5
            if y > size * 0.4:
                alpha = int(clamp(noise * 0.7 + (y / size) * 0.4) * 230)
                row.append((240, 248, 255, alpha))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_snow_crust(path, size=64):
    """Uniform seamless isotropic procedural snow crust texture with crystal sparkles."""
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            n1 = (math.sin(x * 0.35) * math.cos(y * 0.35)) * 0.03
            n2 = (math.sin((x + y) * 0.7) * math.cos((x - y) * 0.7)) * 0.02
            sparkle = 0.05 if ((x * 19 + y * 37 + 7) % 17 == 0) else 0.0

            base = 0.95 + n1 + n2 + sparkle
            r = int(clamp(base * 0.97) * 255)
            g = int(clamp(base * 0.99) * 255)
            b = int(clamp(base * 1.02) * 255)
            row.append((r, g, b, 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_snow_sprayer(path, size=16):
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            if x in (7, 8) and y < 8:
                row.append((180, 190, 205, 255))
            elif (x >= 5 and x <= 10) and (y >= 8 and y <= 14):
                row.append((70, 160, 240, 255))
            elif x in (6, 7, 8, 9) and y == 2:
                row.append((240, 250, 255, 255))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def main():
    edir = os.path.join(ROOT, "entity", "snow")
    bdir = os.path.join(ROOT, "block")
    idir = os.path.join(ROOT, "item")

    gen_boots_footprint(os.path.join(edir, "footprint_boots.png"), 32, False)
    gen_boots_footprint(os.path.join(edir, "footprint_snowy_trail.png"), 32, True)
    gen_bare_feet(os.path.join(edir, "footprint_bare.png"))
    gen_scp_claws_footprint(os.path.join(edir, "footprint_claws.png"))
    gen_blood_splatter(os.path.join(edir, "blood_human.png"), (180, 20, 25))
    gen_blood_splatter(os.path.join(edir, "blood_acid.png"), (60, 210, 40))
    gen_blood_splatter(os.path.join(edir, "blood_anomalous.png"), (25, 25, 30))
    gen_blood_splatter(os.path.join(edir, "blood_ender.png"), (170, 45, 215))
    gen_snowy_legs(os.path.join(edir, "snowy_legs_overlay.png"))

    gen_boots_footprint(os.path.join(bdir, "decal_boots.png"))
    gen_bare_feet(os.path.join(bdir, "decal_bare_feet.png"))
    gen_scp_claws_footprint(os.path.join(bdir, "decal_claws.png"))
    gen_blood_splatter(os.path.join(bdir, "decal_blood_human.png"), (180, 20, 25))
    gen_blood_splatter(os.path.join(bdir, "decal_blood_acid.png"), (60, 210, 40))
    gen_blood_splatter(os.path.join(bdir, "decal_blood_anomalous.png"), (25, 25, 30))
    gen_blood_splatter(os.path.join(bdir, "decal_blood_ender.png"), (170, 45, 215))
    gen_snow_crust(os.path.join(bdir, "snow_crust_overlay.png"))
    gen_snow_sprayer(os.path.join(idir, "snow_sprayer.png"))

if __name__ == "__main__":
    main()
