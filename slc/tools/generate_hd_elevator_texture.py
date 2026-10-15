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


FRAMES = 8

# ---------------- PALETTE (sampled 1:1 from the SCP:SL panel screenshot) ----------------
PLATE        = (8, 10, 14, 255)      # pure near-black panel face
RIM_CYAN     = (110, 230, 215, 255)  # thin cyan glow rim (active panel edge)
RIM_DARK     = (26, 30, 36, 255)     # panel edge when powered down

STRIP_CORE   = (250, 255, 255, 255)  # white-hot strip core
STRIP_HALO   = (110, 238, 222, 255)  # cyan bloom row around the core

LINE_CYAN    = (0, 228, 214, 255)    # zigzag route lines / hollow node outline
NODE_SOLID   = (215, 255, 248, 255)  # filled "elevator is here" square
NODE_SOLID_D = (70, 205, 185, 255)   # dim phase of the pulse

OFF_STRIP    = (62, 66, 74, 255)     # powered-down strip
OFF_STRIP_HI = (80, 84, 92, 255)
OFF_LINE     = (58, 62, 70, 255)     # dead zigzag lines

RED_HOT      = (255, 72, 60, 255)    # red "elevator NOT here" warning square
RED_DEAD     = (60, 16, 14, 255)     # red blink off-phase


def _px(pixels, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        i = (y * 16 + x) * 4
        pixels[i:i+4] = bytes(c)


def _lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def draw_base(frame, rim_color):
    for y in range(16):
        for x in range(16):
            on_edge = x == 0 or x == 15 or y == 0 or y == 15
            _px(frame, x, y, rim_color if on_edge else PLATE)


def draw_strips(frame, k, off):
    # Top strip: halo row y=2, core row y=3 ; Bottom strip: core y=12, halo y=13
    if not off:
        halo = _lerp(STRIP_HALO, STRIP_CORE, 0.25 * k)
        core = _lerp(STRIP_HALO, STRIP_CORE, 0.75 + 0.25 * k)
    else:
        halo, core = OFF_STRIP, OFF_STRIP_HI
    for x in range(2, 14):
        _px(frame, x, 2, halo)
        _px(frame, x, 3, core)
        _px(frame, x, 12, core)
        _px(frame, x, 13, halo)


def hollow_node(frame, x0, y0, color):
    for x in range(x0, x0 + 3):
        _px(frame, x, y0, color)
        _px(frame, x, y0 + 2, color)
    _px(frame, x0, y0 + 1, color)
    _px(frame, x0 + 2, y0 + 1, color)


def solid_node(frame, x0, y0, color):
    for y in range(y0, y0 + 3):
        for x in range(x0, x0 + 3):
            _px(frame, x, y, color)


def draw_active(frame, k):
    line = LINE_CYAN
    # 45-degree diagonals, top hollow -> mid filled
    _px(frame, 7, 7, line)
    _px(frame, 6, 8, line)
    # mid filled -> bottom hollow
    _px(frame, 6, 10, line)
    _px(frame, 7, 11, line)
    # Nodes: top-right hollow, bottom-right hollow, mid-left SOLID (pulsing)
    hollow_node(frame, 8, 4, line)
    hollow_node(frame, 8, 10, line)
    solid_node(frame, 3, 7, _lerp(NODE_SOLID_D, NODE_SOLID, k))


def draw_off(frame, k):
    # Dead gray zigzag with hollow squares
    _px(frame, 7, 7, OFF_LINE)
    _px(frame, 6, 8, OFF_LINE)
    _px(frame, 6, 10, OFF_LINE)
    _px(frame, 7, 11, OFF_LINE)
    hollow_node(frame, 8, 10, OFF_LINE)
    hollow_node(frame, 3, 7, OFF_LINE)
    # Red solid square at the TOP node position, blinking
    solid_node(frame, 8, 4, _lerp(RED_DEAD, RED_HOT, k))


def gen_strip(active):
    pixels = bytearray(16 * (16 * FRAMES) * 4)
    for f in range(FRAMES):
        frame = bytearray(16 * 16 * 4)
        t = 2.0 * math.pi * f / FRAMES
        if active:
            k = 0.5 + 0.5 * math.sin(t)          # smooth breathing
            draw_base(frame, _lerp((70, 170, 160, 255), RIM_CYAN, k))
            draw_strips(frame, k, off=False)
            draw_active(frame, k)
        else:
            k = 1.0 if math.sin(t) > -0.2 else 0.0   # hard SL-style blink
            draw_base(frame, RIM_DARK)
            draw_strips(frame, k, off=True)
            draw_off(frame, k)
        for y in range(16):
            for x in range(16):
                s = (y * 16 + x) * 4
                d = ((f * 16 + y) * 16 + x) * 4
                pixels[d:d+4] = frame[s:s+4]
    return pixels


def gen_item_icon():
    frame = bytearray(16 * 16 * 4)
    draw_base(frame, RIM_CYAN)
    draw_strips(frame, 1.0, off=False)
    draw_active(frame, 1.0)
    return frame


if __name__ == '__main__':
    base = 'slc/src/main/resources/assets/slc/textures'
    os.makedirs(f'{base}/block', exist_ok=True)
    os.makedirs(f'{base}/item', exist_ok=True)

    mcmeta = '{\n  "animation": {\n    "frametime": 3,\n    "interpolate": true\n  }\n}\n'

    for name, active in (('elevator_button', True), ('elevator_button_off', False)):
        with open(f'{base}/block/{name}.png', 'wb') as f:
            f.write(create_png(16, 16 * FRAMES, gen_strip(active)))
        with open(f'{base}/block/{name}.png.mcmeta', 'w') as f:
            f.write(mcmeta)

    with open(f'{base}/item/elevator_button.png', 'wb') as f:
        f.write(create_png(16, 16, gen_item_icon()))

    print("1:1 SL panel textures regenerated (cyan rim, white-hot strips, pulsing solid node / blinking red node).")
