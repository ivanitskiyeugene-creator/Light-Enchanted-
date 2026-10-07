#!/usr/bin/env python3
"""Generates all mod PNG textures for Light Enchanted v2.5.0:
- SCP HCZ Industrial Fan Casing (dark containment steel, no bright grey lines)
- Ultra-Fine Industrial Protective Grille (concentric rings, radial ribs, fine wire mesh)
- 3D Aerodynamic Fan Blades (brushed titanium/steel with specular edge highlights)
- Wall Fan textures
- Moving Spotlight and Beam textures
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


def gen_industrial_fan_casing(path, size=64):
    """Heavy SCP:SL containment steel casing: dark matte gunmetal, subtle brushed grain, containment rivets."""
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            noise = hash01(x * 73 + y * 19)
            base = 0.17 + 0.03 * noise
            r, g, b = base * 0.96, base * 0.98, base * 1.02

            # Outer frame bevel (darker shadow border)
            is_outer_border = (x < 3 or x >= size - 3 or y < 3 or y >= size - 3)
            if is_outer_border:
                r *= 0.85; g *= 0.85; b *= 0.88

            # Corner reinforcement plates
            is_corner_plate = (
                (x < 14 and y < 14 and (x + y) < 18) or
                (x >= size - 14 and y < 14 and ((size - 1 - x) + y) < 18) or
                (x < 14 and y >= size - 14 and (x + (size - 1 - y)) < 18) or
                (x >= size - 14 and y >= size - 14 and ((size - 1 - x) + (size - 1 - y)) < 18)
            )
            if is_corner_plate:
                r *= 1.06; g *= 1.06; b *= 1.08

            # Containment hex bolts / heavy steel rivets
            rivet_coords = [
                (5, 5), (size - 6, 5), (5, size - 6), (size - 6, size - 6),
                (32, 4), (32, size - 5), (4, 32), (size - 5, 32),
                (16, 4), (48, 4), (16, size - 5), (48, size - 5),
                (4, 16), (4, 48), (size - 5, 16), (size - 5, 48)
            ]
            for cx, cy in rivet_coords:
                dist = math.hypot(x - cx, y - cy)
                if dist < 2.0:
                    r *= 1.25; g *= 1.25; b *= 1.30
                elif dist < 2.8:
                    r *= 0.70; g *= 0.70; b *= 0.70

            # Circular ventilation duct opening
            d = math.hypot(x - c, y - c)
            tunnel_radius = size * 0.44
            if d < tunnel_radius:
                # Inside duct tunnel shadow
                tunnel_shade = 0.12 + 0.02 * hash01(x * 37 + y * 53)
                r, g, b = tunnel_shade * 0.95, tunnel_shade * 0.98, tunnel_shade * 1.02
            elif abs(d - tunnel_radius) < 2.2:
                # Subtle duct collar rim bevel
                r *= 1.08; g *= 1.08; b *= 1.10

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_industrial_fan_blade(path, size=64):
    """High-detail aerodynamic titanium/steel fan blade: metallic brushed finish, chamfered leading & trailing edges, pitched lighting."""
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            noise = hash01(x * 31 + y * 47)
            # Brushed metal texture along blade length (x axis)
            brushed = 0.24 + 0.04 * noise
            # Camber specular highlight across chord (y axis)
            chord = y / (size - 1.0)
            camber = math.sin(chord * math.pi)
            spec = camber * 0.12

            r = brushed * 0.94 + spec
            g = brushed * 0.97 + spec
            b = brushed * 1.04 + spec

            # Beveled leading edge (y < 4) and trailing edge (y > size - 5)
            if y < 3:
                r *= 1.25; g *= 1.25; b *= 1.28
            elif y >= size - 3:
                r *= 0.72; g *= 0.72; b *= 0.75

            # Root cuff mounting section (x < 10)
            if x < 8:
                r *= 0.85; g *= 0.85; b *= 0.88
                if x in (3, 4) and y in (16, 32, 48):
                    # Mounting bolt
                    r = 0.45; g = 0.47; b = 0.50

            row.append((int(clamp(r) * 255), int(clamp(g) * 255), int(clamp(b) * 255), 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_industrial_fan_grate(path, size=128):
    """Ultra-Fine High-Res Industrial Safety Grille:
    - Sleek thin outer steel ring
    - 4 thin concentric protective wire rings
    - 8 thin radial structural spokes
    - Fine woven wire diamond/square protective mesh (crisp, delicate, pass-through).
    """
    pixels = []
    c = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            r, g, b, a = 0, 0, 0, 0

            d = math.hypot(x - c, y - c)
            max_r = size * 0.47

            if d <= max_r:
                # 1. Outer Frame Ring (1.5px thick)
                if abs(d - max_r) < 1.8:
                    r, g, b, a = 82, 88, 96, 255

                # 2. Concentric Wire Guard Rings (thin 1px circular wires at 4 radii)
                for ring_r in [size * 0.14, size * 0.25, size * 0.35, size * 0.43]:
                    if abs(d - ring_r) < 0.95:
                        r, g, b, a = 95, 102, 112, 245

                # 3. 8 Radial Support Spokes (thin 1px wires connecting center to rim)
                angle = math.atan2(y - c, x - c)
                spoke_dist = abs(math.sin(angle * 4.0) * d)
                if spoke_dist < 0.95 and d > (size * 0.08):
                    r, g, b, a = 100, 108, 118, 255

                # 4. Fine Protective Wire Mesh (thin 1px lines spaced every 12 pixels)
                mesh_grid = 12
                on_v_wire = (x % mesh_grid == 0)
                on_h_wire = (y % mesh_grid == 0)
                if on_v_wire or on_h_wire:
                    r, g, b, a = 75, 80, 88, 220
                    # Specular highlight on wire crossings
                    if on_v_wire and on_h_wire:
                        r, g, b, a = 115, 122, 132, 255

                # 5. Center Mounting Hub Collar Ring
                if abs(d - (size * 0.12)) < 1.4:
                    r, g, b, a = 90, 96, 105, 255

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
