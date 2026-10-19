package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.config.Scp079Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Final GUI-stage companion to the CCTV post chain.
 *
 * <p>The world post chain is evaluated before Minecraft draws screens and HUD overlays.  This
 * pass deliberately runs after the SCP-079 HUD/map has been drawn so the operator interface is
 * presented through the same surveillance monitor, instead of looking like clean UI floating on
 * top of a processed camera feed.</p>
 */
public final class Scp079MonitorFx {
    private Scp079MonitorFx() {
    }

    public static boolean isEnabled() {
        return Scp079Config.COMMON == null || Scp079Config.COMMON.enableCctvShader.get();
    }

    /**
     * Draws a light-weight phosphor, scanline, refresh and edge-darkening composite over an
     * already-rendered SCP-079 screen. This is procedural on purpose: it remains sharp at every
     * GUI scale and does not allocate a framebuffer every frame.
     */
    public static void renderFinalComposite(GuiGraphics graphics, int width, int height) {
        if (!isEnabled() || width <= 0 || height <= 0) {
            return;
        }

        float strength = Scp079Config.COMMON == null
                ? 0.72f
                : Scp079Config.COMMON.monitorInterfaceStrength.get().floatValue();
        long time = getVisualTime();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // A faint cold monitor tint binds the text, schematics and camera image into one surface.
        graphics.fill(0, 0, width, height, scaleAlpha(0x1300182C, strength));

        // Fine alternating scanlines. The moving phase keeps them from becoming a static texture.
        int scanOffset = (int) (time & 3L);
        for (int y = scanOffset; y < height; y += 4) {
            graphics.fill(0, y, width, Math.min(height, y + 1), scaleAlpha(0x2400010A, strength));
        }

        // Barely visible blue phosphor triads; restrained enough to preserve readable map labels.
        int phosphorOffset = (int) ((time >> 2) % 9L);
        for (int x = phosphorOffset; x < width; x += 9) {
            graphics.fill(x, 0, Math.min(width, x + 1), height, scaleAlpha(0x0900D8FF, strength));
        }

        // Slow CRT refresh sweep, intentionally shared with both the tactical map and HUD.
        int rollY = (int) ((time * 2L) % Math.max(1, height + 36)) - 18;
        for (int offset = -14; offset <= 14; offset++) {
            int y = rollY + offset;
            if (y < 0 || y >= height) {
                continue;
            }
            float falloff = 1.0f - Math.abs(offset) / 15.0f;
            graphics.fill(0, y, width, y + 1,
                    scaleAlpha(0x2600DFFF, strength * falloff));
        }

        // Sparse deterministic sensor noise prevents the HUD from looking disconnected from the feed.
        long state = (time / 2L) ^ ((long) width << 20) ^ height;
        int samples = Math.max(18, (width * height) / 18000);
        for (int i = 0; i < samples; i++) {
            state = nextNoise(state);
            int x = (int) Math.floorMod(state, width);
            state = nextNoise(state);
            int y = (int) Math.floorMod(state, height);
            boolean bright = (state & 8L) == 0L;
            int color = bright ? 0x30C8FAFF : 0x2200A6DD;
            graphics.fill(x, y, Math.min(width, x + 1 + (int) (state & 1L)), Math.min(height, y + 1),
                    scaleAlpha(color, strength));
        }

        // Screen-space vignette using small layered edge bands, not a texture or extra render target.
        int edge = Math.max(8, Math.min(width, height) / 18);
        for (int i = 0; i < edge; i += 2) {
            float edgeAmount = (edge - i) / (float) edge;
            int alpha = Math.round(18.0f * edgeAmount * edgeAmount * strength);
            int color = alpha << 24;
            graphics.fill(i, i, width - i, Math.min(height - i, i + 2), color);
            graphics.fill(i, Math.max(i, height - i - 2), width - i, height - i, color);
            graphics.fill(i, i, Math.min(width - i, i + 2), height - i, color);
            graphics.fill(Math.max(i, width - i - 2), i, width - i, height - i, color);
        }

        RenderSystem.disableBlend();
    }

    private static long getVisualTime() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            return minecraft.level.getGameTime();
        }
        return System.currentTimeMillis() / 50L;
    }

    private static long nextNoise(long state) {
        return state * 6364136223846793005L + 1442695040888963407L;
    }

    private static int scaleAlpha(int argb, float strength) {
        int alpha = (argb >>> 24) & 0xFF;
        int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * strength)));
        return (scaledAlpha << 24) | (argb & 0x00FFFFFF);
    }
}
