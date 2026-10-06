#!/usr/bin/env python3
"""Generates all mod PNG textures for Light Enchanted v2.3.0.
"""
import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "lightenchanted", "textures")


def write_png(path, width, height, pixels):
    """pixels: rows of (r, g, b, a) tuples, written as an 8-bit RGBA PNG."""
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


def smoothstep(edge0, edge1, x):
    t = clamp((x - edge0) / (edge1 - edge0))
    return t * t * (3.0 - 2.0 * t)


def hash01(n):
    n = (n * 2654435761) & 0xFFFFFFFF
    n ^= n >> 13
    n = (n * 1274126177) & 0xFFFFFFFF
    n ^= n >> 16
    return n / 0x100000000


def gen_optical_mirror(path, size=16):
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            is_edge = (x == 0 or x == size - 1 or y == 0 or y == size - 1)
            is_inner_edge = (x == 1 or x == size - 2 or y == 1 or y == size - 2)
            noise = hash01(x * 19 + y * 43)

            if is_edge:
                r, g, b = 0.22, 0.24, 0.28
            elif is_inner_edge:
                r, g, b = 0.38, 0.42, 0.48
            else:
                # Glass mirror reflection streak
                diag = (x + y) / (2.0 * size)
                streak = smoothstep(0.40, 0.50, diag) * smoothstep(0.60, 0.50, diag)
                base = 0.55 + 0.15 * noise
                r = base * 0.75 + streak * 0.45
                g = base * 0.90 + streak * 0.50
                b = base * 1.05 + streak * 0.55

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_photoreceptor(path, size=16):
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            d = math.hypot(x - c, y - c) / c
            noise = hash01(x * 23 + y * 37)
            base = 0.25 + 0.05 * noise
            r, g, b = base * 0.9, base * 0.9, base * 0.95

            if x == 0 or x == size - 1 or y == 0 or y == size - 1:
                r *= 0.6; g *= 0.6; b *= 0.6

            # Optical sensor center diode
            if d < 0.65:
                lens = smoothstep(0.65, 0.0, d)
                r = 0.85 * lens + 0.15
                g = 0.25 * lens + 0.10
                b = 0.20 * lens + 0.10

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_haze_machine(path, size=16):
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            noise = hash01(x * 11 + y * 53)
            base = 0.28 + 0.08 * noise
            r, g, b = base * 0.92, base * 0.95, base * 1.0

            # Outer casing border
            if x == 0 or x == size - 1 or y == 0 or y == size - 1:
                r *= 0.65; g *= 0.65; b *= 0.65

            # Exhaust vent slats
            if 3 <= y <= 9 and 3 <= x <= 12:
                if y % 2 == 1:
                    r, g, b = 0.10, 0.11, 0.13
                else:
                    r, g, b = 0.45, 0.48, 0.52

            # Status LEDs
            if y == 12 and x in (4, 7, 10):
                if x == 4: r, g, b = 0.1, 0.9, 0.2 # Green power
                elif x == 7: r, g, b = 0.2, 0.6, 1.0 # Blue pump
                else: r, g, b = 0.9, 0.7, 0.1 # Yellow fluid

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_industrial_floodlight(path, size=16):
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            noise = hash01(x * 31 + y * 13)
            base = 0.24 + 0.05 * noise
            r, g, b = base, base, base

            # Heavy steel frame
            if x <= 1 or x >= size - 2 or y <= 1 or y >= size - 2:
                r, g, b = 0.18, 0.20, 0.23
            else:
                # 3x2 Halogen reflector grid
                gx = (x - 2) % 4
                gy = (y - 2) % 6
                if gx == 0 or gy == 0:
                    r, g, b = 0.35, 0.38, 0.42
                else:
                    r, g, b = 0.95, 0.90, 0.70

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_fresnel_spotlight(path, size=16):
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            d = math.hypot(x - c, y - c) / c
            base = 0.18 + 0.04 * hash01(x * 17 + y * 71)
            r, g, b = base, base, base

            # Ribbed concentric Fresnel rings
            if d < 0.75:
                ring = math.sin(d * 18.0) * 0.5 + 0.5
                r = 0.50 + 0.45 * ring
                g = 0.55 + 0.40 * ring
                b = 0.65 + 0.35 * ring

            # Barn door hinges
            if (x in (1, size - 2) and y in (3, 4, size - 5, size - 4)) or (y in (1, size - 2) and x in (3, 4, size - 5, size - 4)):
                r, g, b = 0.45, 0.45, 0.50

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_laser_projector(path, size=16):
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            d = math.hypot(x - c, y - c) / c
            noise = hash01(x * 47 + y * 29)
            base = 0.22 + 0.05 * noise
            r, g, b = base * 1.1, base * 0.9, base * 0.9

            if x == 0 or x == size - 1 or y == 0 or y == size - 1:
                r *= 0.6; g *= 0.6; b *= 0.6

            # Crystal laser aperture
            if d < 0.45:
                core = smoothstep(0.45, 0.0, d)
                r = 0.95 * core + 0.2
                g = 0.20 * core + 0.05
                b = 0.30 * core + 0.05

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_light_grate(path, size=16):
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            # Glowing backlight diffuser background
            r, g, b = 0.88, 0.92, 0.98

            # Steel diamond grating mesh over top
            is_mesh = (x % 3 == 0 or y % 3 == 0 or (x + y) % 4 == 0)
            if is_mesh:
                r, g, b = 0.25, 0.27, 0.30

            if x == 0 or x == size - 1 or y == 0 or y == size - 1:
                r, g, b = 0.18, 0.20, 0.22

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_fluorescent_tube(path, size=16):
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            noise = hash01(x * 37 + y * 19)
            base = 0.26 + 0.04 * noise
            r, g, b = base * 0.95, base * 0.95, base * 1.0

            # End caps
            if y < 2 or y >= size - 2:
                r, g, b = 0.32, 0.35, 0.40
            # Double glowing neon tubes
            elif 4 <= x <= 6 or 9 <= x <= 11:
                r, g, b = 0.92, 0.96, 1.0

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def main():
    bdir = os.path.join(ROOT, "block")
    idir = os.path.join(ROOT, "item")

    gen_optical_mirror(os.path.join(bdir, "optical_mirror.png"))
    gen_optical_mirror(os.path.join(idir, "optical_mirror.png"))

    gen_photoreceptor(os.path.join(bdir, "photoreceptor.png"))
    gen_photoreceptor(os.path.join(idir, "photoreceptor.png"))

    gen_haze_machine(os.path.join(bdir, "haze_machine.png"))
    gen_haze_machine(os.path.join(idir, "haze_machine.png"))

    gen_industrial_floodlight(os.path.join(bdir, "industrial_floodlight.png"))
    gen_industrial_floodlight(os.path.join(idir, "industrial_floodlight.png"))

    gen_fresnel_spotlight(os.path.join(bdir, "fresnel_spotlight.png"))
    gen_fresnel_spotlight(os.path.join(idir, "fresnel_spotlight.png"))

    gen_laser_projector(os.path.join(bdir, "laser_projector.png"))
    gen_laser_projector(os.path.join(idir, "laser_projector.png"))

    gen_light_grate(os.path.join(bdir, "light_grate.png"))
    gen_light_grate(os.path.join(idir, "light_grate.png"))

    gen_fluorescent_tube(os.path.join(bdir, "fluorescent_tube.png"))
    gen_fluorescent_tube(os.path.join(idir, "fluorescent_tube.png"))

if __name__ == "__main__":
    main()
