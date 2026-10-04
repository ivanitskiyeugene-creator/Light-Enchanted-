#!/usr/bin/env python3
"""Generates the mod's PNG textures without any third-party dependencies.

Usage: python3 tools/generate_textures.py
Outputs into src/main/resources/assets/lightenchanted/textures/.
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
    with open(path, "wb") as fh:
        fh.write(png)
    print("wrote", os.path.relpath(path))


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def smoothstep(edge0, edge1, x):
    t = clamp((x - edge0) / (edge1 - edge0))
    return t * t * (3.0 - 2.0 * t)


def hash01(n):
    """Deterministic pseudo-random in [0, 1)."""
    n = (n * 2654435761) & 0xFFFFFFFF
    n ^= n >> 13
    n = (n * 1274126177) & 0xFFFFFFFF
    n ^= n >> 16
    return n / 0x100000000


def gen_block_texture(path, size=16):
    """Emitter block face: dark frame with a glowing lens in the middle."""
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            d = math.hypot(x - c, y - c) / c
            # dark navy frame with a slightly beveled outer edge
            edge = 1.0 if x in (0, size - 1) or y in (0, size - 1) else 0.0
            base = 0.06 + 0.05 * hash01(x * 31 + y * 7)
            r, g, b = base * 0.6, base * 0.8, base * 1.3
            if edge:
                r += 0.10
                g += 0.12
                b += 0.16
            # glowing lens: white core -> cyan halo
            lens = smoothstep(1.05, 0.15, d)
            core = smoothstep(0.45, 0.0, d)
            r += lens * 0.45 + core * 0.55
            g += lens * 0.60 + core * 0.55
            b += lens * 0.80 + core * 0.55
            # thin light cross through the center
            if abs(x - c) < 0.7 or abs(y - c) < 0.7:
                r += lens * 0.20
                g += lens * 0.25
                b += lens * 0.30
            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_beam_texture(path, size=64):
    """Beam gradient: soft horizontal edges, subtle vertical energy streaks.

    Alpha controls the glow falloff; RGB stays near-white so the render
    can tint it to any configured color.
    """
    pixels = []
    for y in range(size):
        row = []
        v = y / (size - 1)
        for x in range(size):
            u = x / (size - 1)
            edge = math.pow(math.sin(math.pi * u), 1.6)          # soft side falloff
            streak = 0.78 + 0.22 * hash01(x * 17 + 3)            # per-column energy
            shimmer = 0.90 + 0.10 * math.sin(v * 20.0 + hash01(x) * 6.2831)
            a = clamp(edge * streak * shimmer)
            brightness = 0.82 + 0.18 * edge
            row.append((int(brightness * 255), int(brightness * 255), int(brightness * 255),
                        int(a * 255)))
        pixels.append(row)
    write_png(path, size, size, pixels)


def main():
    block_dir = os.path.join(ROOT, "block")
    entity_dir = os.path.join(ROOT, "entity")
    os.makedirs(block_dir, exist_ok=True)
    os.makedirs(entity_dir, exist_ok=True)
    gen_block_texture(os.path.join(block_dir, "light_emitter.png"))
    gen_beam_texture(os.path.join(entity_dir, "beam.png"))


if __name__ == "__main__":
    main()
