#!/usr/bin/env python3
"""Generates all PNG textures and decals for Snow - SCPied:
- Boots tread footprint decal
- Bare feet footprint decal
- SCP creature claws / pawprint decal
- Hooves footprint decal
- Blood splatters (crimson, acid green, black, ender purple)
- Snow crust overlay texture
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


def gen_boots_footprint(path, size=32):
    """Deep military boot sole tread with grip ribs and heel indent."""
    pixels = []
    for y in range(size):
        row = []
        ny = (y - size * 0.5) / (size * 0.5)
        for x in range(size):
            nx = (x - size * 0.5) / (size * 0.5)

            # Boot shape profile
            in_heel = (ny > 0.4 and ny < 0.85 and abs(nx) < 0.40)
            in_forefoot = (ny > -0.85 and ny < 0.25 and abs(nx) < (0.45 - ny * 0.08))
            in_bridge = (ny >= 0.25 and ny <= 0.4 and abs(nx) < 0.25)

            if in_heel or in_forefoot or in_bridge:
                # Tread grooves
                is_tread_groove = (y % 4 in (0, 1)) and not in_bridge
                depth = 0.85 if is_tread_groove else 0.55
                alpha = int(depth * 255)
                # Shadow indentation color (dark compressed snow)
                row.append((130, 145, 165, alpha))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_scp_claws_footprint(path, size=32):
    """SCP-939 / Beast 3-clawed footprint with central palm pad."""
    pixels = []
    c = size * 0.5
    for y in range(size):
        row = []
        for x in range(size):
            d_pad = math.hypot(x - c, y - (c + 4))
            in_pad = d_pad < 5.5

            # 3 sharp claws
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
    """Dynamic organic blood splatter droplet mask."""
    pixels = []
    c = size * 0.5
    for y in range(size):
        row = []
        for x in range(size):
            dist = math.hypot(x - c, y - c)
            angle = math.atan2(y - c, x - c)

            # Organic blob radius variation
            noise_r = 7.0 + 3.0 * math.sin(angle * 5.0) + 2.0 * math.cos(angle * 3.0)

            # Secondary micro droplets
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


def main():
    edir = os.path.join(ROOT, "entity", "snow")
    gen_boots_footprint(os.path.join(edir, "footprint_boots.png"))
    gen_scp_claws_footprint(os.path.join(edir, "footprint_claws.png"))
    gen_blood_splatter(os.path.join(edir, "blood_human.png"), (180, 20, 25))
    gen_blood_splatter(os.path.join(edir, "blood_acid.png"), (60, 210, 40))
    gen_blood_splatter(os.path.join(edir, "blood_anomalous.png"), (25, 25, 30))
    gen_blood_splatter(os.path.join(edir, "blood_ender.png"), (170, 45, 215))

if __name__ == "__main__":
    main()
