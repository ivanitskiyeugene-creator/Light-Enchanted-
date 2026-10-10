#!/usr/bin/env python3
"""Generates all PNG textures and icons for 0-7-9:
- HCZ Camera texture (Dark Gunmetal)
- LCZ Camera texture (Light Grey Composite)
- EZ Camera texture (Off-White Clean)
- Facility Map Node block texture
- Screwdriver configurator tool icon
- GUI & Marker Icons (Camera, Door, Tesla, Speaker, Elevator, Scanlines)
"""
import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "zero_seven_nine", "textures")


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


def gen_camera_texture(path, base_color_rgb=(45, 48, 54), size=64):
    """Camera texture atlas: Body panels, sun visor, mounting arm, lens and LED ring."""
    pixels = []
    br, bg, bb = base_color_rgb
    for y in range(size):
        row = []
        for x in range(size):
            # Noise grain
            noise = ((x * 37 + y * 59) % 23) / 23.0 * 0.08 - 0.04
            r = int(clamp(br / 255.0 + noise) * 255)
            g = int(clamp(bg / 255.0 + noise) * 255)
            b = int(clamp(bb / 255.0 + noise) * 255)
            a = 255

            # Top quadrant: Sun visor / Hood panel (slightly darker shade)
            if y < 16:
                r = int(r * 0.88); g = int(g * 0.88); b = int(b * 0.88)

            # Bottom-left: Lens dark glass & LED Ring (X: 0..32, Y: 32..64)
            if x < 32 and y >= 32:
                lx = (x - 16) / 16.0
                ly = (y - 48) / 16.0
                ld = math.hypot(lx, ly)
                if ld < 0.9:
                    # Dark optical lens glass
                    r, g, b = 15, 18, 22
                    if ld < 0.4:
                        r, g, b = 8, 10, 14
                # LED Ring cluster (8 circular dots)
                for i in range(8):
                    ang = i * (math.pi / 4.0)
                    dot_x = math.cos(ang) * 0.65
                    dot_y = math.sin(ang) * 0.65
                    if math.hypot(lx - dot_x, ly - dot_y) < 0.12:
                        # Red LED cluster (default idle)
                        r, g, b = 255, 30, 35

            # Bottom-right: Blue LED state (Active 079 state) (X: 32..64, Y: 32..64)
            elif x >= 32 and y >= 32:
                lx = (x - 48) / 16.0
                ly = (y - 48) / 16.0
                ld = math.hypot(lx, ly)
                if ld < 0.9:
                    r, g, b = 12, 16, 24
                    if ld < 0.4:
                        r, g, b = 6, 8, 16
                for i in range(8):
                    ang = i * (math.pi / 4.0)
                    dot_x = math.cos(ang) * 0.65
                    dot_y = math.sin(ang) * 0.65
                    if math.hypot(lx - dot_x, ly - dot_y) < 0.12:
                        # Bright Cyan/Blue LED cluster
                        r, g, b = 0, 230, 255

            row.append((r, g, b, a))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_facility_map_node(path, size=16):
    """Map Node block texture: Technical server interface with blinking status LEDs."""
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            base = 0.22 if (x in (0, 15) or y in (0, 15)) else 0.15
            r, g, b = int(base * 255), int(base * 265), int(base * 280)

            # Central server screen
            if 3 <= x <= 12 and 3 <= y <= 8:
                r, g, b = 10, 35, 60
                if (x + y) % 3 == 0:
                    r, g, b = 20, 80, 140

            # Status LEDs
            if 10 <= y <= 12 and x in (4, 7, 10):
                if x == 4:
                    r, g, b = 0, 255, 120 # Green online LED
                elif x == 7:
                    r, g, b = 0, 180, 255 # Blue network LED
                else:
                    r, g, b = 255, 160, 0 # Orange sync LED

            row.append((r, g, b, 255))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_screwdriver_icon(path, size=16):
    """Screwdriver configurator tool icon."""
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            # Diagonal screwdriver shaft & handle
            if x + y in (15, 16) and x >= 4 and x <= 12:
                if x < 8:
                    # Metal shaft / tip
                    row.append((210, 220, 235, 255))
                else:
                    # Grip handle (Black & Orange containment rubber)
                    if (x + y) % 2 == 0:
                        row.append((240, 130, 20, 255))
                    else:
                        row.append((30, 32, 38, 255))
            elif x in (2, 3) and y in (13, 14) and x + y == 16:
                # Flathead tip
                row.append((170, 180, 195, 255))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_camera_icon(path, size=32):
    """Image 2 reference: Clean white camera silhouette inside circular ring."""
    pixels = []
    c = size * 0.5
    for y in range(size):
        row = []
        for x in range(size):
            d = math.hypot(x - c, y - c)
            in_ring = abs(d - (size * 0.44)) < 1.3

            # Camera body tilted ~ -35 degrees
            nx = (x - c)
            ny = (y - c)
            # Rotate by 35 deg
            rx = nx * math.cos(0.6) - ny * math.sin(0.6)
            ry = nx * math.sin(0.6) + ny * math.cos(0.6)

            in_body = (-9 <= rx <= 9 and -4 <= ry <= 4)
            in_hood = (7 <= rx <= 10 and -5.5 <= ry <= 5.5)
            in_base = (-12 <= rx <= -9 and -2 <= ry <= 2)
            in_mount = (abs(nx - (-5)) < 1.5 and -2 <= ny <= 7) or (abs(ny - 7) < 1.5 and -11 <= nx <= -4)

            if in_ring or in_body or in_hood or in_base or in_mount:
                row.append((255, 255, 255, 255))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_door_icon(path, size=32):
    """Door marker icon (Square frame with inner panel)."""
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            is_border = (6 <= x <= 25 and 4 <= y <= 27) and (x in (6, 7, 24, 25) or y in (4, 5, 26, 27))
            is_handle = (x in (20, 21) and 15 <= y <= 17)
            if is_border or is_handle:
                row.append((0, 210, 255, 255))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_tesla_icon(path, size=32):
    """Tesla gate lightning bolt marker icon."""
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            # Lightning bolt geometry
            in_bolt = (
                (14 <= x <= 18 and 4 <= y <= 13) or
                (11 <= x <= 21 and 13 <= y <= 16) or
                (13 <= x <= 17 and 16 <= y <= 27)
            )
            if in_bolt:
                row.append((0, 230, 255, 255))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_speaker_icon(path, size=32):
    """Speaker / Intercom megaphone icon."""
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            nx = x - 12
            ny = y - 16
            in_box = (0 <= nx <= 5 and abs(ny) <= 4)
            in_cone = (5 <= nx <= 12 and abs(ny) <= (nx - 1))
            in_wave = (14 <= nx <= 16 and abs(ny) <= 7 and abs(math.hypot(nx - 8, ny) - 7) < 1.3)
            if in_box or in_cone or in_wave:
                row.append((0, 230, 255, 255))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, size, size, pixels)


def gen_scanlines_texture(path, size=128):
    """CRT scanline & vignette overlay texture."""
    pixels = []
    c = size * 0.5
    for y in range(size):
        row = []
        for x in range(size):
            # Horizontal scanline
            line = 0.0 if (y % 3 == 0) else 0.12
            # Vignette falloff
            dx = (x - c) / c
            dy = (y - c) / c
            vignette = math.pow(dx * dx + dy * dy, 1.8) * 0.45
            alpha = int(clamp(line + vignette) * 140)
            row.append((10, 18, 30, alpha))
        pixels.append(row)
    write_png(path, size, size, pixels)


def main():
    bdir = os.path.join(ROOT, "block")
    idir = os.path.join(ROOT, "item")
    gdir = os.path.join(ROOT, "gui")

    # 3 Camera models (HCZ dark gunmetal, LCZ composite grey, EZ clean white)
    gen_camera_texture(os.path.join(bdir, "hcz_camera.png"), (32, 35, 40))
    gen_camera_texture(os.path.join(bdir, "lcz_camera.png"), (140, 145, 155))
    gen_camera_texture(os.path.join(bdir, "ez_camera.png"), (220, 224, 230))

    gen_facility_map_node(os.path.join(bdir, "facility_map_node.png"))
    gen_screwdriver_icon(os.path.join(idir, "screwdriver.png"))

    # GUI Marker Icons
    gen_camera_icon(os.path.join(gdir, "camera_icon.png"))
    gen_door_icon(os.path.join(gdir, "door_icon.png"))
    gen_tesla_icon(os.path.join(gdir, "tesla_icon.png"))
    gen_speaker_icon(os.path.join(gdir, "speaker_icon.png"))
    gen_scanlines_texture(os.path.join(gdir, "scanlines.png"))

if __name__ == "__main__":
    main()
