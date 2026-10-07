#!/usr/bin/env python3
"""Generates all mod PNG textures for Light Enchanted v2.3.0 / v2.4.0.
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


def gen_block_texture(path, size=16):
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            d = math.hypot(x - c, y - c) / c
            edge = 1.0 if x in (0, size - 1) or y in (0, size - 1) else 0.0
            base = 0.06 + 0.05 * hash01(x * 31 + y * 7)
            r, g, b = base * 0.6, base * 0.8, base * 1.3
            if edge:
                r += 0.10; g += 0.12; b += 0.16
            lens = smoothstep(1.05, 0.15, d)
            core = smoothstep(0.45, 0.0, d)
            r += lens * 0.45 + core * 0.55
            g += lens * 0.60 + core * 0.55
            b += lens * 0.80 + core * 0.55
            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_moving_spotlight(path, size=16):
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            d = math.hypot(x - c, y - c) / c
            base = 0.22 + 0.05 * hash01(x * 19 + y * 41)
            r, g, b = base * 0.9, base * 0.95, base * 1.1

            # Motor base yoke
            if y < 3:
                r, g, b = 0.32, 0.35, 0.40

            # Central optical projector lens
            if d < 0.65:
                core = smoothstep(0.65, 0.0, d)
                r = 0.45 * core + 0.2
                g = 0.75 * core + 0.2
                b = 1.00 * core + 0.3

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_beam_texture(path, size=128):
    """Silky-smooth continuous volumetric atmospheric beam texture."""
    pixels = []
    for y in range(size):
        row = []
        v = y / (size - 1.0)
        fade_top = smoothstep(0.0, 0.04, v)
        fade_bottom = smoothstep(1.0, 0.88, v)
        v_fade = fade_top * fade_bottom

        for x in range(size):
            u = x / (size - 1.0)
            center_dist = abs(u - 0.5) * 2.0
            core = math.exp(-3.2 * center_dist * center_dist)
            halo = math.pow(math.cos(center_dist * (math.pi / 2.0)), 1.5) * 0.5
            alpha_val = clamp((core * 0.70 + halo * 0.30) * v_fade)
            brightness = 0.92 + 0.08 * core
            row.append((int(brightness * 255), int(brightness * 255), int(brightness * 255), int(alpha_val * 255)))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_industrial_fan_casing(path, size=32):
    """Pure dark matte gunmetal containment steel: grey lines removed, bevels barely noticeable (just 4% lighter than base)."""
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            noise = hash01(x * 73 + y * 19)
            base = 0.18 + 0.03 * noise
            r, g, b = base * 0.95, base * 0.98, base * 1.02

            # Outer border bevel (slightly darker shadow)
            is_border = (x < 2 or x >= size - 2 or y < 2 or y >= size - 2)
            if is_border:
                r *= 0.90; g *= 0.90; b *= 0.90

            # Subtle dark steel rivets (barely lighter than base)
            for cx, cy in [(3, 3), (size - 4, 3), (3, size - 4), (size - 4, size - 4),
                           (16, 2), (16, size - 3), (2, 16), (size - 3, 16)]:
                if math.hypot(x - cx, y - cy) < 1.3:
                    r *= 1.15; g *= 1.15; b *= 1.18

            # Soft brushed metal rim bevel (only 4% lighter, no bright grey lines!)
            is_rim = (x in (3, 4, size - 5, size - 4) or y in (3, 4, size - 5, size - 4))
            if is_rim:
                r *= 1.04; g *= 1.04; b *= 1.04

            # Circular opening bevel
            d = math.hypot(x - c, y - c)
            if d < (size * 0.44):
                inner_shade = 0.14 + 0.03 * hash01(x * 13 + y * 41)
                r, g, b = inner_shade * 0.95, inner_shade * 0.98, inner_shade * 1.02
            if abs(d - (size * 0.44)) < 1.2:
                r *= 1.05; g *= 1.05; b *= 1.05

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_industrial_fan_blade(path, size=32):
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            noise = hash01(x * 17 + y * 29)
            base = 0.28 + 0.06 * noise
            spec = smoothstep(0.0, 1.0, (x / size)) * 0.12
            r = base * 0.95 + spec
            g = base * 0.98 + spec
            b = base * 1.04 + spec
            if x == 0 or x == size - 1 or y == 0 or y == size - 1:
                r *= 0.75; g *= 0.75; b *= 0.75
            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_industrial_fan_grate(path, size=32):
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            r, g, b, a = 0, 0, 0, 0
            is_crossbar = (x in (15, 16) or y in (15, 16) or x in (7, 8, 23, 24) or y in (7, 8, 23, 24))
            is_mesh = (x % 4 == 0 or y % 4 == 0)
            if is_crossbar:
                r, g, b, a = 85, 90, 98, 255
            elif is_mesh:
                r, g, b, a = 50, 55, 62, 230
            row.append((r, g, b, a))
        pixels.append(row)
    write_png(path, size, size, pixels)


def main():
    bdir = os.path.join(ROOT, "block")
    idir = os.path.join(ROOT, "item")
    edir = os.path.join(ROOT, "entity")

    gen_block_texture(os.path.join(bdir, "light_emitter.png"))
    gen_block_texture(os.path.join(idir, "light_emitter.png"))
    gen_moving_spotlight(os.path.join(bdir, "moving_spotlight.png"))
    gen_moving_spotlight(os.path.join(idir, "moving_spotlight.png"))
    gen_beam_texture(os.path.join(edir, "beam.png"))
    gen_industrial_fan_casing(os.path.join(bdir, "industrial_fan_casing.png"))
    gen_industrial_fan_casing(os.path.join(idir, "industrial_fan.png"))
    gen_industrial_fan_casing(os.path.join(bdir, "wall_fan.png"))
    gen_industrial_fan_casing(os.path.join(idir, "wall_fan.png"))
    gen_industrial_fan_blade(os.path.join(bdir, "industrial_fan_blade.png"))
    gen_industrial_fan_grate(os.path.join(bdir, "industrial_fan_grate.png"))

if __name__ == "__main__":
    main()
