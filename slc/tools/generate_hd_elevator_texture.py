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


# ---------------- PALETTE (sampled from user's exact reference screenshot) ----------------
FACE       = (14, 16, 20, 255)
SCREEN     = (9, 11, 15, 255)        # recessed dark screen behind the live diagram
BEVEL_HI   = (58, 62, 70, 255)
BEVEL_MID  = (42, 46, 53, 255)
BEVEL_LO   = (24, 27, 32, 255)
CORNER_CUT = (26, 28, 33, 255)
BLUE       = (64, 118, 204, 255)


def _px(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        i = (y * 16 + x) * 4
        px[i:i+4] = bytes(c)


def draw_plate(f):
    for y in range(16):
        for x in range(16):
            _px(f, x, y, FACE)
    # Screen recess
    for y in range(3, 13):
        for x in range(1, 15):
            _px(f, x, y, SCREEN)
    # Bevel frame
    for x in range(16):
        _px(f, x, 0, BEVEL_HI)
        _px(f, x, 15, BEVEL_LO)
    for y in range(16):
        _px(f, 0, y, BEVEL_MID)
        _px(f, 15, y, BEVEL_MID)
    # Chamfered corners
    for (x, y) in [(0, 0), (1, 0), (0, 1),
                   (15, 0), (14, 0), (15, 1),
                   (0, 15), (1, 15), (0, 14),
                   (15, 15), (14, 15), (15, 14)]:
        _px(f, x, y, CORNER_CUT)
    # Blue light bars (top & bottom)
    for x in range(3, 13):
        _px(f, x, 2, BLUE)
        _px(f, x, 13, BLUE)


def draw_static_diagram(f):
    """Inventory icon only: static route + hollow top node + solid box at bottom."""
    # Right-angle route: top node -> right -> down -> left -> bottom node
    _px(f, 9, 4, BLUE)
    _px(f, 10, 4, BLUE)
    for y in range(5, 11):
        _px(f, 10, y, BLUE)
    _px(f, 9, 10, BLUE)
    # Hollow top node (4x4 outline)
    for x in range(5, 9):
        _px(f, x, 3, BLUE)
        _px(f, x, 6, BLUE)
    for y in range(3, 7):
        _px(f, 5, y, BLUE)
        _px(f, 8, y, BLUE)
    # Solid box at the bottom node (lift position)
    for y in range(9, 13):
        for x in range(5, 9):
            _px(f, x, y, BLUE)


if __name__ == '__main__':
    base = 'slc/src/main/resources/assets/slc/textures'
    os.makedirs(f'{base}/block', exist_ok=True)
    os.makedirs(f'{base}/item', exist_ok=True)

    block_f = bytearray(16 * 16 * 4)
    draw_plate(block_f)
    with open(f'{base}/block/elevator_button.png', 'wb') as fp:
        fp.write(create_png(16, 16, block_f))

    item_f = bytearray(16 * 16 * 4)
    draw_plate(item_f)
    draw_static_diagram(item_f)
    with open(f'{base}/item/elevator_button.png', 'wb') as fp:
        fp.write(create_png(16, 16, item_f))

    print("Static panel textures written (block = plate+bars, item = +static diagram). BER draws the live diagram in-world.")
