package dev.slc.client.gui;

import dev.slc.client.ClientRoundCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD feedback for the disarmer system (pure visuals, no chat spam):
 *  1) Tied victim: soft desaturated edge vignette + a small handcuff glyph above the hotbar.
 *  2) Acting rescuer: a circular progress ring around the crosshair while holding
 *     the use key ("E") on a tied player. The server enforces the real timing;
 *     this ring mirrors it for feedback.
 */
public class DisarmedHudOverlay implements IGuiOverlay {
    public static final DisarmedHudOverlay INSTANCE = new DisarmedHudOverlay();

    private static final int UNTIE_TICKS = 60; // ~3 s ring mirror
    private static int holdTicks = 0;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ClientRoundCache.isRoundActive()) {
            holdTicks = 0;
            return;
        }

        if (ClientRoundCache.isLocalTied()) {
            renderTiedVignette(graphics, screenWidth, screenHeight);
            renderCuffGlyph(graphics, mc, screenWidth, screenHeight);
            holdTicks = 0;
            return;
        }

        // Am I freeing someone? (crosshair on tied player + use key held + close enough)
        boolean freeing = false;
        if (mc.crosshairPickEntity instanceof Player target
                && ClientRoundCache.isPlayerTied(target.getUUID())
                && mc.player.distanceTo(target) <= 2.5
                && mc.options.keyUse.isDown()) {
            freeing = true;
        }

        if (freeing) {
            holdTicks++;
        } else {
            holdTicks = 0;
        }

        if (holdTicks > 0) {
            float progress = Math.min(1.0f, holdTicks / (float) UNTIE_TICKS);
            renderProgressRing(graphics, screenWidth / 2, screenHeight / 2, progress);
        }
    }

    private void renderTiedVignette(GuiGraphics graphics, int w, int h) {
        // Soft gray restraint frame on screen edges
        int b = 18;
        graphics.fillGradient(0, 0, w, b, 0x90000000, 0x00000000);
        graphics.fillGradient(0, h - b, w, h, 0x00000000, 0x90000000);
        graphics.fillGradient(0, 0, b, h, 0x70000000, 0x00000000);
        graphics.fillGradient(w - b, 0, w, h, 0x00000000, 0x70000000);
    }

    private void renderCuffGlyph(GuiGraphics graphics, Minecraft mc, int w, int h) {
        // Minimal handcuff glyph: two interlocked rounded squares above the crosshair
        int cx = w / 2;
        int cy = h / 2 + 14;
        int c = 0xCC9AA0A6;
        // link A
        ringRect(graphics, cx - 7, cy, 7, 9, c);
        // link B
        ringRect(graphics, cx + 1, cy, 7, 9, c);
        // chain stub between links
        graphics.fill(cx - 1, cy + 4, cx + 2, cy + 6, 0xCC9AA0A6);
    }

    private static void ringRect(GuiGraphics gg, int x, int y, int w, int h, int color) {
        gg.fill(x, y, x + w, y + 1, color);
        gg.fill(x, y + h - 1, x + w, y + h, color);
        gg.fill(x, y, x + 1, y + h, color);
        gg.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void renderProgressRing(GuiGraphics graphics, int cx, int cy, float progress) {
        int segments = 14;
        int lit = (int) (segments * progress);
        double radius = 14.0;
        for (int i = 0; i < segments; i++) {
            double angle = -Math.PI / 2.0 + (2.0 * Math.PI * i / segments);
            int px = cx + (int) Math.round(Math.cos(angle) * radius) - 1;
            int py = cy + (int) Math.round(Math.sin(angle) * radius) - 1;
            int color = i < lit ? 0xFFFFFFFF : 0x55BBBBBB;
            graphics.fill(px, py, px + 2, py + 2, color);
        }
    }
}
