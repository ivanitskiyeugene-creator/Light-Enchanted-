import struct
import zlib
import os

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


FRAMES = 8  # 4 frames: box at bottom node (lift down) -> 4 frames: box at top node (lift up)

# ---------------- PALETTE (sampled from user's exact reference screenshot) ----------------
FACE       = (14, 16, 20, 255)       # recessed black inner face
BEVEL_HI   = (58, 62, 70, 255)       # top bevel edge
BEVEL_MID  = (42, 46, 53, 255)       # left/right bevel edge
BEVEL_LO   = (24, 27, 32, 255)       # bottom bevel edge
CORNER_CUT = (26, 28, 33, 255)       # chamfered corner shadow
BLUE       = (64, 118, 204, 255)     # flat azure blue for strips + route + solid box


def _px(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        i = (y * 16 + x) * 4
        px[i:i+4] = bytes(c)


def draw_base(f):
    for y in range(16):
        for x in range(16):
            _px(f, x, y, FACE)
    # Outer bevel frame (1 px ring)
    for x in range(16):
        _px(f, x, 0, BEVEL_HI)
        _px(f, x, 15, BEVEL_LO)
    for y in range(16):
        _px(f, 0, y, BEVEL_MID)
        _px(f, 15, y, BEVEL_MID)
    # Chamfered corners (3-px staircase cuts)
    for (cx, cy) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        pass
    cuts = [(0, 0), (1, 0), (0, 1),
            (15, 0), (14, 0), (15, 1),
            (0, 15), (1, 15), (0, 14),
            (15, 15), (14, 15), (15, 14)]
    for (x, y) in cuts:
        _px(f, x, y, CORNER_CUT)


def draw_strips(f):
    # Top and bottom blue light bars (flat, 1 row, inset from bevel)
    for x in range(3, 13):
        _px(f, x, 2, BLUE)
        _px(f, x, 13, BLUE)


def hollow_node(f, x0, y0):
    # 4x4 outline (1 px thick), hollow center
    for x in range(x0, x0 + 4):
        _px(f, x, y0, BLUE)
        _px(f, x, y0 + 3, BLUE)
    for y in range(y0, y0 + 4):
        _px(f, x0, y, BLUE)
        _px(f, x0 + 3, y, BLUE)


def solid_box(f, x0, y0):
    for y in range(y0, y0 + 4):
        for x in range(x0, x0 + 4):
            _px(f, x, y, BLUE)


def draw_route(f):
    # Right-angle route on the right side: top node -> right -> down -> left -> bottom node
    _px(f, 9, 4, BLUE)   # horizontal out of top node
    _px(f, 10, 4, BLUE)
    for y in range(5, 11):  # vertical drop x=10
        _px(f, 10, y, BLUE)
    _px(f, 9, 10, BLUE)  # horizontal into bottom node


def gen_strip():
    pixels = bytearray(16 * (16 * FRAMES) * 4)
    for fi in range(FRAMES):
        f = bytearray(16 * 16 * 4)
        draw_base(f)
        draw_strips(f)
        draw_route(f)
        # Hollow nodes at BOTH ends (top & bottom)
        hollow_node(f, 5, 3)
        hollow_node(f, 5, 9)
        # The "where is the lift" FILLED BOX alternates between nodes
        if fi < FRAMES // 2:
            solid_box(f, 5, 9)   # lift at bottom floor
        else:
            solid_box(f, 5, 3)   # lift at top floor
        for y in range(16):
            for x in range(16):
                s = (y * 16 + x) * 4
                d = ((fi * 16 + y) * 16 + x) * 4
                pixels[d:d+4] = f[s:s+4]
    return pixels


def gen_item_icon():
    f = bytearray(16 * 16 * 4)
    draw_base(f)
    draw_strips(f)
    draw_route(f)
    hollow_node(f, 5, 3)
    solid_box(f, 5, 9)
    return f


def write_animated(mcmeta):
    return '{\n  "animation": {\n    "frametime": 6,\n    "interpolate": false\n  }\n}\n'


if __name__ == '__main__':
    base = 'slc/src/main/resources/assets/slc/textures'
    os.makedirs(f'{base}/block', exist_ok=True)
    os.makedirs(f'{base}/item', exist_ok=True)

    with open(f'{base}/block/elevator_button.png', 'wb') as fp:
        fp.write(create_png(16, 16 * FRAMES, gen_strip()))
    with open(f'{base}/block/elevator_button.png.mcmeta', 'w') as fp:
        fp.write('{\n  "animation": {\n    "frametime": 6,\n    "interpolate": false\n  }\n}\n')

    with open(f'{base}/item/elevator_button.png', 'wb') as fp:
        fp.write(create_png(16, 16, gen_item_icon()))

    print("Exact-reference elevator panel texture generated (blue bars, right-angle route, animating solid box).")
