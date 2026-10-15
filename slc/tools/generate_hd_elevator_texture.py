import struct
import zlib
import os
import math

def create_png(width, height, pixel_data):
    def chunk(chunk_type, data):
        return struct.pack('>I', len(data)) + chunk_type + data + struct.pack('>I', zlib.crc32(chunk_type + data) & 0xffffffff)

    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)
        for x in range(width):
            idx = (y * width + x) * 4
            raw_data.extend(pixel_data[idx:idx+4])

    compressed = zlib.compress(bytes(raw_data))
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', compressed) + chunk(b'IEND', b'')


FRAME_COUNT = 8
W_FRAME = 16
H_FRAME = 16

# ---------------- PALETTE (1:1 with SCP:SL reference screenshot) ----------------
PLATE       = (22, 26, 32, 255)      # inner dark face
FRAME       = (30, 35, 41, 255)      # outer bezel base
BEVEL_HI    = (74, 82, 92, 255)      # top/left bevel highlight
BEVEL_LO    = (19, 22, 27, 255)      # bottom/right bevel shadow
TAB_METAL   = (96, 106, 116, 255)    # small top-right corner tab

# ACTIVE (cabin at this floor) - bright mint/cyan glow
STRIP_CORE   = (230, 255, 246, 255)  # white-mint bar core
STRIP_HALO   = (92, 224, 190, 255)   # cyan halo row
STRIP_BLEED  = (24, 62, 55, 255)     # faint glow bleed onto plate
LINE_GLOW    = (74, 226, 196, 255)   # zigzag route lines
LINE_BLEED   = (26, 84, 74, 255)     # line glow bleed
NODE_DIM     = (110, 235, 205, 255)  # filled node (dimmer pulse end)
NODE_BRIGHT  = (245, 255, 250, 255)  # filled node (bright pulse end)
NODE_BLEED   = (30, 95, 84, 255)

# OFF (cabin on the other floor / moving) - dark + red warning blink
OFF_STRIP_A  = (40, 45, 51, 255)
OFF_STRIP_B  = (34, 39, 45, 255)
OFF_LINE     = (62, 68, 75, 255)
RED_BRIGHT   = (255, 82, 70, 255)
RED_DIM      = (92, 32, 29, 255)
RED_BLEED    = (66, 22, 21, 255)


def draw_base(pixels, off=False):
    def px(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            i = (y * 16 + x) * 4
            pixels[i:i+4] = bytes(c)

    # Outer frame
    for y in range(16):
        for x in range(16):
            px(x, y, FRAME)
    for x in range(1, 15):
        px(x, 0, BEVEL_HI)
    for y in range(1, 15):
        px(0, y, BEVEL_HI)
    for x in range(1, 15):
        px(x, 15, BEVEL_LO)
    for y in range(1, 15):
        px(15, y, BEVEL_LO)
    # Top-right corner metal tab (reference detail)
    px(14, 0, TAB_METAL)
    px(15, 0, TAB_METAL)
    px(15, 1, TAB_METAL)

    # Inner plate
    for y in range(1, 15):
        for x in range(1, 15):
            px(x, y, PLATE)

    # ---- Top light strip (y=2..3) ----
    # ---- Bottom light strip (y=13..14) ----

def draw_strips(pixels, k=1.0, off=False):
    def px(x, y, c):
        i = (y * 16 + x) * 4
        pixels[i:i+4] = bytes(c)

    def lerp(a, b, t):
        return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

    for row_top, row_core, row_bleed_a, row_bleed_b in ((2, 3, 1, 4), (14, 13, 15, 12)):
        if off:
            halo, core = OFF_STRIP_B, OFF_STRIP_A
        else:
            halo = lerp(STRIP_HALO, tuple(int(v * 1.06) if i < 3 else v for i, v in enumerate(STRIP_HALO)), k)
            core = STRIP_CORE
        for x in range(2, 14):
            px(x, row_top, halo)
            px(x, row_core, core)
        # Rounded end caps (dimmer)
        ph = lerp(halo, PLATE, 0.45) if not off else lerp(OFF_STRIP_B, PLATE, 0.35)
        pc = lerp(core, PLATE, 0.45) if not off else lerp(OFF_STRIP_A, PLATE, 0.35)
        px(2, row_top, ph); px(13, row_top, ph)
        px(2, row_core, pc); px(13, row_core, pc)
        # Glow bleed only when active
        if not off:
            bleed = lerp(STRIP_BLEED, PLATE, 1.0 - k)
            if row_bleed_a == 1 or row_bleed_a == 15:
                pass  # frame row, keep clean
            else:
                for x in range(3, 13):
                    px(x, row_bleed_a, bleed)
            for x in range(3, 13):
                px(x, row_bleed_b, lerp(STRIP_BLEED, PLATE, 1.0 - k))


def hollow_node(pixels, x0, y0, color):
    def px(x, y, c):
        i = (y * 16 + x) * 4
        pixels[i:i+4] = bytes(c)
    for x in range(x0, x0 + 3):
        px(x, y0, color)
        px(x, y0 + 2, color)
    px(x0, y0 + 1, color)
    px(x0 + 2, y0 + 1, color)

def filled_node(pixels, x0, y0, color, bleed):
    def px(x, y, c):
        i = (y * 16 + x) * 4
        pixels[i:i+4] = bytes(c)
    for dy in (-1, 3):
        for x in range(x0, x0 + 3):
            px(x, y0 + dy, bleed)
    for dx in (-1, 3):
        for y in range(y0, y0 + 3):
            px(x0 + dx, y, bleed)
    for y in range(y0, y0 + 3):
        for x in range(x0, x0 + 3):
            px(x, y, color)

def draw_route(pixels, off=False):
    def px(x, y, c):
        i = (y * 16 + x) * 4
        pixels[i:i+4] = bytes(c)
    line = OFF_LINE if off else LINE_GLOW
    # Staircase A: top node -> bottom of filled (10,7),(9,8),(8,8),(7,9)
    for (x, y) in ((10, 7), (9, 8), (8, 8), (7, 9)):
        px(x, y, line)
    # Staircase B: filled bottom -> top of bottom node (7,10),(8,11),(9,11),(10,12)
    for (x, y) in ((7, 10), (8, 11), (9, 11), (10, 12)):
        px(x, y, line)
    # Hollow nodes
    hollow_node(pixels, 11, 4, line)    # top node
    hollow_node(pixels, 11, 10, line)   # bottom node


def gen_frames(active=True):
    """Generate FRAME_COUNT frames stacked vertically."""
    all_pixels = bytearray(16 * (16 * FRAME_COUNT) * 4)
    for f in range(FRAME_COUNT):
        phase = 2.0 * math.pi * f / FRAME_COUNT
        k = 0.5 + 0.5 * math.sin(phase)
        frame = bytearray(16 * 16 * 4)
        draw_base(frame, off=not active)
        draw_strips(frame, k=0.85 + 0.15 * k, off=not active)
        draw_route(frame, off=not active)
        def lerp(a, b, t):
            return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))
        if active:
            # Pulsing filled node at middle-left (YOU ARE HERE / cabin here)
            filled_node(frame, 4, 7, lerp(NODE_DIM, NODE_BRIGHT, k), NODE_BLEED)
        else:
            # Blinking red warning node at top (cabin is up there / moving)
            filled_node(frame, 11, 4, lerp(RED_DIM, RED_BRIGHT, k), lerp(PLATE, RED_BLEED, k))
        # copy into strip
        for y in range(16):
            for x in range(16):
                s = (y * 16 + x) * 4
                d = ((f * 16 + y) * 16 + x) * 4
                all_pixels[d:d+4] = frame[s:s+4]
    return all_pixels


def gen_item_icon():
    """Static 16x16 icon for inventory (active look, mid pulse)."""
    frame = bytearray(16 * 16 * 4)
    draw_base(frame)
    draw_strips(frame, k=1.0)
    draw_route(frame)
    filled_node(frame, 4, 7, NODE_BRIGHT, NODE_BLEED)
    return frame


if __name__ == '__main__':
    base_tex_dir = 'slc/src/main/resources/assets/slc/textures'
    os.makedirs(f'{base_tex_dir}/block', exist_ok=True)
    os.makedirs(f'{base_tex_dir}/item', exist_ok=True)

    with open(f'{base_tex_dir}/block/elevator_button.png', 'wb') as f:
        f.write(create_png(16, 16 * FRAME_COUNT, gen_frames(active=True)))
    with open(f'{base_tex_dir}/block/elevator_button.png.mcmeta', 'w') as f:
        f.write('{\n  "animation": {\n    "frametime": 4,\n    "interpolate": true\n  }\n}\n')

    with open(f'{base_tex_dir}/block/elevator_button_off.png', 'wb') as f:
        f.write(create_png(16, 16 * FRAME_COUNT, gen_frames(active=False)))
    with open(f'{base_tex_dir}/block/elevator_button_off.png.mcmeta', 'w') as f:
        f.write('{\n  "animation": {\n    "frametime": 4,\n    "interpolate": true\n  }\n}\n')

    with open(f'{base_tex_dir}/item/elevator_button.png', 'wb') as f:
        f.write(create_png(16, 16, gen_item_icon()))

    print("SL elevator panel textures generated: ACTIVE (pulsing), OFF (red blink), static item icon.")
