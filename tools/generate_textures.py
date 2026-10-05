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
            edge = 1.0 if x in (0, size - 1) or y in (0, size - 1) else 0.0
            base = 0.06 + 0.05 * hash01(x * 31 + y * 7)
            r, g, b = base * 0.6, base * 0.8, base * 1.3
            if edge:
                r += 0.10
                g += 0.12
                b += 0.16
            lens = smoothstep(1.05, 0.15, d)
            core = smoothstep(0.45, 0.0, d)
            r += lens * 0.45 + core * 0.55
            g += lens * 0.60 + core * 0.55
            b += lens * 0.80 + core * 0.55
            if abs(x - c) < 0.7 or abs(y - c) < 0.7:
                r += lens * 0.20
                g += lens * 0.25
                b += lens * 0.30
            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_beam_texture(path, size=128):
    pixels = []
    for y in range(size):
        row = []
        v = y / (size - 1.0)
        fade_top = smoothstep(0.0, 0.05, v)
        fade_bottom = smoothstep(1.0, 0.82, v)
        v_fade = fade_top * fade_bottom

        for x in range(size):
            u = x / (size - 1.0)
            center_dist = abs(u - 0.5) * 2.0
            core = math.exp(-4.5 * center_dist * center_dist)
            halo = math.pow(math.cos(center_dist * (math.pi / 2.0)), 1.8) * 0.45
            edge = core * 0.75 + halo * 0.25

            streak1 = 0.90 + 0.10 * hash01(int(x * 3.7 + 11))
            streak2 = 0.93 + 0.07 * math.sin(v * 24.0 + hash01(x * 7) * 6.28)
            streak3 = 0.95 + 0.05 * math.cos(v * 48.0 + u * 12.0)
            turbulence = streak1 * streak2 * streak3

            a = clamp(edge * turbulence * v_fade)
            brightness = 0.88 + 0.12 * core
            row.append((int(brightness * 255), int(brightness * 255), int(brightness * 255),
                        int(a * 255)))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_ghost_item_texture(path, size=16):
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            r = g = b = 0.0
            a = 0.0
            on_edge = x in (0, 1, size - 2, size - 1) or y in (0, 1, size - 2, size - 1)
            dash = ((x + y) // 2) % 2 == 0
            if on_edge and dash:
                r, g, b, a = 0.45, 0.75, 1.0, 0.9
            d = math.hypot(x - c, y - c) / c
            lens = smoothstep(0.75, 0.1, d)
            core = smoothstep(0.35, 0.0, d)
            if lens > 0.02:
                r = max(r, lens * 0.45 + core * 0.55)
                g = max(g, lens * 0.60 + core * 0.55)
                b = max(b, lens * 0.85 + core * 0.55)
                a = 1.0
            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255),
                        int(clamp(a) * 255)))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_tuner_texture(path, size=16):
    def dist_to_segment(px, py, x0, y0, x1, y1):
        vx, vy = x1 - x0, y1 - y0
        wx, wy = px - x0, py - y0
        c1 = vx * wx + vy * wy
        c2 = vx * vx + vy * vy
        t = 0.0 if c2 == 0 else clamp(c1 / c2)
        return math.hypot(px - (x0 + t * vx), py - (y0 + t * vy))

    hx0, hy0, hx1, hy1 = 3.5, 12.5, 9.0, 7.0
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            px, py = x + 0.5, y + 0.5
            r = g = b = a = 0.0

            hd = dist_to_segment(px, py, hx0, hy0, hx1, hy1)
            if hd < 0.9:
                shade = 0.55 + 0.35 * (1.0 - hd / 0.9)
                r, g, b, a = shade * 0.62, shade * 0.62, shade * 0.70, 1.0

            bd = dist_to_segment(px, py, 4.4, 11.6, 5.6, 10.4)
            if bd < 1.1:
                r, g, b, a = 0.75, 0.12, 0.10, 1.0

            tipd = math.hypot(px - 11.5, py - 4.5)
            if tipd < 2.6:
                core = smoothstep(1.0, 0.0, tipd)
                glow = smoothstep(2.6, 1.0, tipd)
                r = 0.45 * glow + 0.55 * core
                g = 0.20 * glow + 0.45 * core
                b = 0.85 * glow + 0.90 * core
                a = 1.0

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255),
                        int(clamp(a) * 255)))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_industrial_fan_casing(path, size=32):
    """SCP SL HCZ Heavy Containment Fan Casing: Dark gunmetal steel with yellow hazard stripes and rivets."""
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            # Base dark heavy containment steel plate
            noise = hash01(x * 73 + y * 19)
            base = 0.16 + 0.05 * noise
            r, g, b = base * 0.9, base * 0.95, base * 1.05

            # Outer border bevel
            is_border = (x < 2 or x >= size - 2 or y < 2 or y >= size - 2)
            if is_border:
                r *= 0.7
                g *= 0.7
                b *= 0.7

            # Heavy steel rivets in corners
            for cx, cy in [(2, 2), (size - 3, 2), (2, size - 3), (size - 3, size - 3)]:
                if math.hypot(x - cx, y - cy) < 1.4:
                    r, g, b = 0.45, 0.48, 0.52

            # Industrial hazard warning stripes along the outer 4px rim
            is_rim = (x < 4 or x >= size - 4 or y < 4 or y >= size - 4) and not is_border
            if is_rim:
                stripe = ((x + y) // 3) % 2
                if stripe == 0:
                    r, g, b = 0.85, 0.72, 0.08  # Warning Yellow
                else:
                    r, g, b = 0.12, 0.12, 0.14  # Charcoal Black

            # Circular ventilation opening bevel
            d = math.hypot(x - c, y - c)
            if d < (size * 0.44):
                inner_shade = 0.14 + 0.04 * hash01(x * 13 + y * 41)
                r, g, b = inner_shade * 0.9, inner_shade * 0.95, inner_shade * 1.05
            if abs(d - (size * 0.44)) < 1.0:
                r, g, b = 0.35, 0.38, 0.42

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_industrial_fan_blade(path, size=32):
    """Aerodynamic heavy steel fan blade texture."""
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            noise = hash01(x * 17 + y * 29)
            base = 0.28 + 0.08 * noise
            # Brushed steel highlights along blade curvature
            spec = smoothstep(0.0, 1.0, (x / size)) * 0.15
            r = base * 0.92 + spec
            g = base * 0.96 + spec
            b = base * 1.05 + spec
            # Edge highlight
            if x == 0 or x == size - 1 or y == 0 or y == size - 1:
                r *= 0.6
                g *= 0.6
                b *= 0.6
            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_industrial_fan_grate(path, size=32):
    """Heavy industrial steel rebar safety grating."""
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            is_bar_x = (x % 6 == 0 or x % 6 == 1)
            is_bar_y = (y % 6 == 0 or y % 6 == 1)
            if is_bar_x or is_bar_y:
                noise = hash01(x * 37 + y * 13)
                shade = 0.32 + 0.08 * noise
                r, g, b = shade * 0.95, shade * 0.98, shade * 1.05
                a = 255
            else:
                r = g = b = a = 0
            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), a))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_light_truss(path, size=16):
    """Galvanized stage/industrial modular scaffolding truss."""
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            is_chords = (x in (0, 1, 14, 15) and y in (0, 1, 14, 15)) or \
                        (x in (0, 1, 14, 15)) or (y in (0, 1, 14, 15))
            is_diag = abs(x - y) <= 1 or abs(x - (15 - y)) <= 1
            if is_chords or is_diag:
                noise = hash01(x * 47 + y * 53)
                shade = 0.45 + 0.12 * noise
                r, g, b = shade * 0.95, shade * 0.98, shade * 1.05
                a = 255
            else:
                r = g = b = a = 0
            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), a))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_recessed_downlight(path, size=16):
    """Clean flush-mounted architectural ceiling downlight."""
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            d = math.hypot(x - c, y - c) / c
            # Outer white/metal ceiling bezel
            if d > 0.85:
                r, g, b = 0.78, 0.80, 0.82
            elif d > 0.70:
                r, g, b = 0.30, 0.32, 0.35  # Black inner step
            else:
                # Glowing downlight lens
                core = smoothstep(0.70, 0.0, d)
                r = 0.92 + 0.08 * core
                g = 0.94 + 0.06 * core
                b = 0.98 + 0.02 * core
            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def main():
    block_dir = os.path.join(ROOT, "block")
    entity_dir = os.path.join(ROOT, "entity")
    item_dir = os.path.join(ROOT, "item")
    os.makedirs(block_dir, exist_ok=True)
    os.makedirs(entity_dir, exist_ok=True)
    os.makedirs(item_dir, exist_ok=True)

    gen_block_texture(os.path.join(block_dir, "light_emitter.png"))
    gen_beam_texture(os.path.join(entity_dir, "beam.png"))
    gen_tuner_texture(os.path.join(item_dir, "beam_tuner.png"))
    gen_ghost_item_texture(os.path.join(item_dir, "light_emitter_creative.png"))

    # v2.2.0 New Textures
    gen_industrial_fan_casing(os.path.join(block_dir, "industrial_fan_casing.png"))
    gen_industrial_fan_blade(os.path.join(block_dir, "industrial_fan_blade.png"))
    gen_industrial_fan_grate(os.path.join(block_dir, "industrial_fan_grate.png"))
    gen_light_truss(os.path.join(block_dir, "light_truss.png"))
    gen_recessed_downlight(os.path.join(block_dir, "recessed_downlight.png"))

    # Item icons
    gen_industrial_fan_casing(os.path.join(item_dir, "industrial_fan.png"))
    gen_light_truss(os.path.join(item_dir, "light_truss.png"))
    gen_recessed_downlight(os.path.join(item_dir, "recessed_downlight.png"))


if __name__ == "__main__":
    main()
