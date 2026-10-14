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

def generate_elevator_button_texture_16x16():
    W, H = 16, 16
    pixels = bytearray(W * H * 4)

    DARK_FRAME = (30, 33, 38, 255)
    LIGHT_EDGE = (65, 72, 82, 255)
    BOLT = (160, 168, 180, 255)
    SCREEN_BG = (10, 12, 15, 255)
    CYAN_GLOW = (70, 195, 255, 255)
    CYAN_SOFT = (35, 120, 180, 255)
    CYAN_BRIGHT = (190, 245, 255, 255)
    WHITE_GLOW = (245, 252, 255, 255)

    def set_px(x, y, color):
        if 0 <= x < W and 0 <= y < H:
            idx = (y * W + x) * 4
            pixels[idx:idx+4] = bytes(color)

    # 1. Fill base frame
    for y in range(H):
        for x in range(W):
            # Chamfered corners
            if (x == 0 and (y == 0 or y == 15)) or (x == 15 and (y == 0 or y == 15)):
                set_px(x, y, (0, 0, 0, 0)) # transparent corner
            else:
                set_px(x, y, DARK_FRAME)

    # 2. Beveled borders
    for x in range(1, 15):
        set_px(x, 0, LIGHT_EDGE)
        set_px(x, 15, (18, 20, 24, 255))
    for y in range(1, 15):
        set_px(0, y, LIGHT_EDGE)
        set_px(15, y, (18, 20, 24, 255))

    # 3. Corner Bolts
    set_px(1, 1, BOLT)
    set_px(14, 1, BOLT)
    set_px(1, 14, BOLT)
    set_px(14, 14, BOLT)

    # 4. Central Screen (Dark Display)
    for y in range(3, 13):
        for x in range(3, 13):
            set_px(x, y, SCREEN_BG)

    # 5. Top Glowing Cyan Bar
    for x in range(4, 12):
        set_px(x, 2, CYAN_BRIGHT)

    # 6. Bottom Glowing Cyan Bar
    for x in range(4, 12):
        set_px(x, 13, CYAN_BRIGHT)

    # 7. Elevator Shaft Schematic Diagram
    # Top Cabin (filled square at (7..9, 4..6))
    for y in range(4, 7):
        for x in range(7, 10):
            set_px(x, y, CYAN_GLOW)
    set_px(8, 5, WHITE_GLOW)

    # Connecting vertical bus lines
    set_px(9, 7, CYAN_SOFT)
    set_px(9, 8, CYAN_SOFT)
    set_px(8, 8, CYAN_SOFT)
    set_px(7, 8, CYAN_SOFT)
    set_px(7, 9, CYAN_SOFT)

    # Bottom Cabin (outline square at (5..8, 9..11))
    for x in range(5, 8):
        set_px(x, 9, CYAN_GLOW)
        set_px(x, 11, CYAN_GLOW)
    set_px(5, 10, CYAN_GLOW)
    set_px(7, 10, CYAN_GLOW)

    # 8. Right side tab
    set_px(15, 6, LIGHT_EDGE)
    set_px(15, 7, BOLT)
    set_px(15, 8, BOLT)
    set_px(15, 9, LIGHT_EDGE)

    return create_png(W, H, pixels)

if __name__ == '__main__':
    base_tex_dir = 'slc/src/main/resources/assets/slc/textures'
    os.makedirs(f'{base_tex_dir}/block', exist_ok=True)
    os.makedirs(f'{base_tex_dir}/item', exist_ok=True)

    tex = generate_elevator_button_texture_16x16()
    with open(f'{base_tex_dir}/block/elevator_button.png', 'wb') as f:
        f.write(tex)
    with open(f'{base_tex_dir}/item/elevator_button.png', 'wb') as f:
        f.write(tex)

    print("Crisp 16x16 elevator texture generated.")
