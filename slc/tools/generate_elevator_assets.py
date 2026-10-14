import struct
import zlib
import os

def create_png(width, height, pixel_data):
    def chunk(chunk_type, data):
        return struct.pack('>I', len(data)) + chunk_type + data + struct.pack('>I', zlib.crc32(chunk_type + data) & 0xffffffff)

    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)  # filter type 0
        for x in range(width):
            idx = (y * width + x) * 4
            raw_data.extend(pixel_data[idx:idx+4])

    compressed = zlib.compress(bytes(raw_data))
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', compressed) + chunk(b'IEND', b'')

def generate_elevator_button_texture():
    W, H = 32, 32
    pixels = bytearray(W * H * 4)

    # Palette
    DARK_FRAME = (38, 41, 46, 255)
    LIGHT_EDGE = (70, 75, 84, 255)
    BOLT = (140, 145, 155, 255)
    SCREEN_BG = (12, 14, 18, 255)
    CYAN_GLOW = (80, 200, 255, 255)
    CYAN_SOFT = (40, 140, 200, 255)
    CYAN_BRIGHT = (180, 240, 255, 255)
    WHITE_GLOW = (240, 250, 255, 255)

    def set_px(x, y, color):
        if 0 <= x < W and 0 <= y < H:
            idx = (y * W + x) * 4
            pixels[idx:idx+4] = bytes(color)

    # 1. Fill base frame
    for y in range(H):
        for x in range(W):
            # Chamfered corners
            if (x < 3 and y < 3) or (x > 28 and y < 3) or (x < 3 and y > 28) or (x > 28 and y > 28):
                continue
            set_px(x, y, DARK_FRAME)

    # 2. Beveled inner shadow / border
    for x in range(3, 29):
        set_px(x, 1, LIGHT_EDGE)
        set_px(x, 30, (20, 22, 26, 255))
    for y in range(3, 29):
        set_px(1, y, LIGHT_EDGE)
        set_px(30, y, (20, 22, 26, 255))

    # 3. Corner Bolts
    set_px(4, 4, BOLT)
    set_px(27, 4, BOLT)
    set_px(4, 27, BOLT)
    set_px(27, 27, BOLT)

    # 4. Central Black Screen
    for y in range(6, 26):
        for x in range(6, 26):
            set_px(x, y, SCREEN_BG)

    # 5. Top Neon Cyan Light Bar
    for x in range(9, 23):
        set_px(x, 4, CYAN_SOFT)
        set_px(x, 5, CYAN_BRIGHT)

    # 6. Bottom Neon Cyan Light Bar
    for x in range(9, 23):
        set_px(x, 26, CYAN_BRIGHT)
        set_px(x, 27, CYAN_SOFT)

    # 7. Elevator Shaft Schematic Diagram on Central Screen (Matches SL screenshot 1:1)
    # Top Cabin Node (filled square at (12..16, 9..12))
    for y in range(9, 13):
        for x in range(12, 17):
            set_px(x, y, CYAN_GLOW)
    for y in range(10, 12):
        for x in range(13, 16):
            set_px(x, y, WHITE_GLOW)

    # Connecting bus lines (dual line path)
    for y in range(13, 19):
        set_px(16, y, CYAN_GLOW)
        set_px(18, y, CYAN_GLOW)
    for x in range(12, 17):
        set_px(x, 18, CYAN_GLOW)
    for y in range(19, 21):
        set_px(12, y, CYAN_GLOW)

    # Bottom Cabin Node (outline square at (10..14, 20..23))
    for x in range(10, 15):
        set_px(x, 20, CYAN_GLOW)
        set_px(x, 23, CYAN_GLOW)
    for y in range(21, 23):
        set_px(10, y, CYAN_GLOW)
        set_px(14, y, CYAN_GLOW)

    # 8. Right side tab
    for y in range(12, 20):
        for x in range(28, 31):
            set_px(x, y, LIGHT_EDGE)
    set_px(29, 15, BOLT)
    set_px(29, 17, BOLT)

    return create_png(W, H, pixels)

def generate_tool_textures():
    W, H = 16, 16

    # Wand Texture
    wand_px = bytearray(W * H * 4)
    def set_wand(x, y, c):
        if 0 <= x < W and 0 <= y < H:
            idx = (y * W + x) * 4
            wand_px[idx:idx+4] = bytes(c)

    # Diagonal sci-fi tactical wand with holographic emitter tip
    HANDLE = (50, 55, 65, 255)
    GRIP = (30, 32, 38, 255)
    EMITTER = (240, 160, 40, 255)
    BEAM = (255, 220, 100, 255)

    for i in range(1, 10):
        set_wand(i, 15 - i, HANDLE)
        set_wand(i - 1, 15 - i, GRIP)

    set_wand(9, 6, EMITTER)
    set_wand(10, 5, EMITTER)
    set_wand(11, 4, BEAM)
    set_wand(12, 3, BEAM)
    set_wand(13, 2, (255, 255, 200, 255))
    set_wand(14, 1, (255, 255, 255, 255))

    wand_png = create_png(W, H, wand_px)

    # Linker Tool Texture
    linker_px = bytearray(W * H * 4)
    def set_linker(x, y, c):
        if 0 <= x < W and 0 <= y < H:
            idx = (y * W + x) * 4
            linker_px[idx:idx+4] = bytes(c)

    CYAN = (60, 180, 240, 255)
    DARK = (35, 40, 48, 255)
    BRIGHT_C = (160, 230, 255, 255)

    for y in range(4, 13):
        for x in range(3, 13):
            set_linker(x, y, DARK)

    for x in range(4, 12):
        set_linker(x, 5, CYAN)
        set_linker(x, 11, CYAN)
    for y in range(6, 11):
        set_linker(4, y, CYAN)
        set_linker(11, y, CYAN)

    set_linker(7, 7, BRIGHT_C)
    set_linker(8, 7, BRIGHT_C)
    set_linker(7, 8, BRIGHT_C)
    set_linker(8, 8, BRIGHT_C)
    set_linker(7, 2, CYAN)
    set_linker(8, 2, CYAN)
    set_linker(7, 3, BRIGHT_C)
    set_linker(8, 3, BRIGHT_C)

    linker_png = create_png(W, H, linker_px)

    return wand_png, linker_png

if __name__ == '__main__':
    base_tex_dir = 'slc/src/main/resources/assets/slc/textures'
    os.makedirs(f'{base_tex_dir}/block', exist_ok=True)
    os.makedirs(f'{base_tex_dir}/item', exist_ok=True)

    btn_png = generate_elevator_button_texture()
    with open(f'{base_tex_dir}/block/elevator_button.png', 'wb') as f:
        f.write(btn_png)
    with open(f'{base_tex_dir}/item/elevator_button.png', 'wb') as f:
        f.write(btn_png)

    wand_png, linker_png = generate_tool_textures()
    with open(f'{base_tex_dir}/item/spatial_marker_wand.png', 'wb') as f:
        f.write(wand_png)
    with open(f'{base_tex_dir}/item/elevator_linker.png', 'wb') as f:
        f.write(linker_png)

    print("Elevator textures generated successfully.")
