#!/usr/bin/env python3
import os
import struct
import zlib
import math

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
    print("wrote", path)

def gen_camera_tex(path, base_rgb, dark_rgb, accent_rgb):
    width, height = 32, 32
    pixels = []
    for y in range(height):
        row = []
        for x in range(width):
            # Panel lines
            is_panel = (x % 8 == 0) or (y % 8 == 0) or (x == 0 or x == 31 or y == 0 or y == 31)
            # Corner bolts
            is_bolt = (x in (2, 29) and y in (2, 29))

            # Lens area: centered around (16, 16)
            dx = x - 15.5
            dy = y - 15.5
            dist = math.sqrt(dx*dx + dy*dy)

            if is_bolt:
                px = (20, 20, 25, 255)
            elif dist < 3.5:
                # Lens pupil & reflection
                if -2 <= dx <= 0 and -2 <= dy <= 0:
                    px = (140, 200, 255, 255) # glare
                else:
                    px = (10, 15, 20, 255) # deep pupil
            elif dist < 6.5:
                px = (25, 35, 45, 255) # lens glass
            elif dist < 8.0:
                px = (60, 70, 80, 255) # lens outer bezel ring
            elif 13 <= x <= 18 and 3 <= y <= 6:
                # Top LED status cluster
                px = accent_rgb + (255,)
            elif is_panel:
                px = dark_rgb + (255,)
            else:
                # Slight metallic noise gradient
                noise = int((math.sin(x * 12.3 + y * 7.7) * 0.5 + 0.5) * 8)
                r = min(255, max(0, base_rgb[0] + noise))
                g = min(255, max(0, base_rgb[1] + noise))
                b = min(255, max(0, base_rgb[2] + noise))
                px = (r, g, b, 255)

            row.append(px)
        pixels.append(row)
    write_png(path, width, height, pixels)

# HCZ Camera (Dark Gunmetal)
gen_camera_tex("0-7-9/src/main/resources/assets/zero_seven_nine/textures/block/hcz_camera.png",
               (38, 42, 48), (22, 25, 30), (0, 229, 255))

# LCZ Camera (Light Composite Grey)
gen_camera_tex("0-7-9/src/main/resources/assets/zero_seven_nine/textures/block/lcz_camera.png",
               (170, 175, 180), (120, 125, 130), (0, 229, 255))

# EZ Camera (Clean White/Steel)
gen_camera_tex("0-7-9/src/main/resources/assets/zero_seven_nine/textures/block/ez_camera.png",
               (230, 235, 240), (180, 190, 200), (0, 229, 255))

# Room Boundary Selector Tool (16x16)
def gen_room_selector_icon(path):
    pixels = []
    for y in range(16):
        row = []
        for x in range(16):
            if (x == 2 and y == 14) or (x == 3 and y == 13) or (x == 4 and y == 12):
                row.append((40, 45, 52, 255)) # handle
            elif 5 <= x <= 10 and 6 <= y <= 11:
                if x == 7 and y == 8:
                    row.append((0, 255, 200, 255)) # HUD screen dot
                else:
                    row.append((52, 152, 219, 255)) # body
            elif (x == 11 and y == 5) or (x == 12 and y == 4) or (x == 13 and y == 3):
                row.append((231, 76, 60, 255)) # laser emitter tip
            elif x == 14 and y == 2:
                row.append((255, 50, 50, 255)) # bright laser dot
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, 16, 16, pixels)

gen_room_selector_icon("0-7-9/src/main/resources/assets/zero_seven_nine/textures/item/room_selector.png")

# Tactical Map Tablet Item (16x16)
def gen_map_tablet_icon(path):
    pixels = []
    for y in range(16):
        row = []
        for x in range(16):
            if (x in (2, 13) and 1 <= y <= 14) or (y in (1, 14) and 2 <= x <= 13):
                row.append((45, 52, 62, 255)) # tablet frame
            elif 3 <= x <= 12 and 2 <= y <= 13:
                # Screen
                if (5 <= x <= 7 and 4 <= y <= 6):
                    row.append((0, 229, 255, 255)) # cyan node
                elif (8 <= x <= 10 and 8 <= y <= 10):
                    row.append((46, 204, 113, 255)) # green current node
                elif (x == 7 and 6 <= y <= 8) or (y == 8 and 7 <= x <= 8):
                    row.append((0, 180, 220, 255)) # link line
                else:
                    row.append((10, 25, 40, 255)) # screen background
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(path, 16, 16, pixels)

gen_map_tablet_icon("0-7-9/src/main/resources/assets/zero_seven_nine/textures/item/map_tablet.png")
