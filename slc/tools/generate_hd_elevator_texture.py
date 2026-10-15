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

def generate_exact_sl_button():
    W, H = 16, 16
    pixels = bytearray(W * H * 4)

    # Palette matching the left reference in screenshot 1:1
    FRAME_DARK  = (28, 32, 38, 255)
    FRAME_LIGHT = (55, 62, 72, 255)
    FRAME_BEVEL = (80, 90, 102, 255)
    RIVET_BRIGHT= (225, 235, 245, 255)
    RIVET_SHADOW= (140, 150, 165, 255)
    SCREEN_BG   = (10, 12, 16, 255)

    # Bright cyan/mint neon glow matching left image
    NEON_MINT   = (165, 245, 215, 255)
    NEON_CYAN   = (0, 225, 255, 255)
    NEON_CORE   = (240, 255, 255, 255)
    NEON_DEEP   = (20, 160, 210, 255)

    def set_px(x, y, color):
        if 0 <= x < W and 0 <= y < H:
            idx = (y * W + x) * 4
            pixels[idx:idx+4] = bytes(color)

    # 1. Base dark frame with beveled edges
    for y in range(H):
        for x in range(W):
            if (x == 0 and (y == 0 or y == 15)) or (x == 15 and (y == 0 or y == 15)):
                continue
            set_px(x, y, FRAME_DARK)

    # Top/Left highlight bevel
    for x in range(1, 15):
        set_px(x, 0, FRAME_BEVEL)
    for y in range(1, 15):
        set_px(0, y, FRAME_BEVEL)

    # Bottom/Right shadow bevel
    for x in range(1, 15):
        set_px(x, 15, (16, 18, 22, 255))
    for y in range(1, 15):
        set_px(15, y, (16, 18, 22, 255))

    # 2. Four Bright Corner Rivets
    set_px(1, 1, RIVET_BRIGHT)
    set_px(2, 1, RIVET_SHADOW)
    set_px(1, 2, RIVET_SHADOW)

    set_px(14, 1, RIVET_BRIGHT)
    set_px(13, 1, RIVET_SHADOW)
    set_px(14, 2, RIVET_SHADOW)

    set_px(1, 14, RIVET_BRIGHT)
    set_px(2, 14, RIVET_SHADOW)
    set_px(1, 13, RIVET_SHADOW)

    set_px(14, 14, RIVET_BRIGHT)
    set_px(13, 14, RIVET_SHADOW)
    set_px(14, 13, RIVET_SHADOW)

    # 3. Inner Screen Background
    for y in range(3, 13):
        for x in range(2, 14):
            set_px(x, y, SCREEN_BG)

    # 4. Top Neon Light Strip (Bright Mint/Cyan)
    for x in range(3, 13):
        set_px(x, 2, NEON_CYAN)
    for x in range(4, 12):
        set_px(x, 2, NEON_MINT)

    # 5. Bottom Neon Light Strip (Bright Mint/Cyan)
    for x in range(3, 13):
        set_px(x, 13, NEON_CYAN)
    for x in range(4, 12):
        set_px(x, 13, NEON_MINT)

    # 6. Elevator Schematic Diagram (Matching Left Reference 1:1)
    # Top Cabin Node (Bright filled square)
    for y in range(4, 7):
        for x in range(7, 10):
            set_px(x, y, NEON_CYAN)
    set_px(8, 5, NEON_CORE)
    set_px(7, 5, NEON_MINT)
    set_px(9, 5, NEON_MINT)

    # Connecting vertical bus lines
    set_px(9, 7, NEON_CYAN)
    set_px(9, 8, NEON_CYAN)
    set_px(8, 8, NEON_CYAN)
    set_px(7, 8, NEON_CYAN)
    set_px(7, 9, NEON_CYAN)

    # Bottom Cabin Node (Bright hollow outline square)
    for x in range(5, 9):
        set_px(x, 9, NEON_CYAN)
        set_px(x, 11, NEON_CYAN)
    set_px(5, 10, NEON_CYAN)
    set_px(8, 10, NEON_CYAN)

    # 7. Right Side Tab
    set_px(15, 6, FRAME_BEVEL)
    set_px(15, 7, RIVET_BRIGHT)
    set_px(15, 8, RIVET_SHADOW)
    set_px(15, 9, FRAME_BEVEL)

    return create_png(W, H, pixels)

if __name__ == '__main__':
    base_tex_dir = 'slc/src/main/resources/assets/slc/textures'
    os.makedirs(f'{base_tex_dir}/block', exist_ok=True)
    os.makedirs(f'{base_tex_dir}/item', exist_ok=True)

    tex = generate_exact_sl_button()
    with open(f'{base_tex_dir}/block/elevator_button.png', 'wb') as f:
        f.write(tex)
    with open(f'{base_tex_dir}/item/elevator_button.png', 'wb') as f:
        f.write(tex)

    print("Exact SL button texture generated.")
