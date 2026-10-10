package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.client.handler.ClientCameraHandler;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class Scp079CameraOverlay {
    private static final ResourceLocation SCANLINES = new ResourceLocation(ZeroSevenNine.MOD_ID, "textures/gui/scanlines.png");

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        // 1. Draw Analog CRT Curved Bezel / Fisheye Vignette & Scanlines
        renderCrtFisheyeVignette(graphics, width, height, mc);

        // 2. Center Reticle (Clean reticle without optical zoom text)
        int cx = width / 2;
        int cy = height / 2;
        int cyanGlow = 0xFF00E5FF;

        // Reticle brackets
        graphics.fill(cx - 16, cy - 16, cx - 10, cy - 15, cyanGlow);
        graphics.fill(cx - 16, cy - 16, cx - 15, cy - 10, cyanGlow);

        graphics.fill(cx + 10, cy - 16, cx + 16, cy - 15, cyanGlow);
        graphics.fill(cx + 15, cy - 16, cx + 16, cy - 10, cyanGlow);

        graphics.fill(cx - 16, cy + 15, cx - 10, cy + 16, cyanGlow);
        graphics.fill(cx - 16, cy + 10, cx - 15, cy + 16, cyanGlow);

        graphics.fill(cx + 10, cy + 15, cx + 16, cy + 16, cyanGlow);
        graphics.fill(cx + 15, cy + 10, cx + 16, cy + 16, cyanGlow);

        // Center dot
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFE0F7FA);

        // 3. Top-Left Panel: Room Information & Lifeform Status (Monochrome)
        renderTopLeftPanel(graphics, font, mc);

        // 4. Top-Right Panel: WASD Camera Jumps & Ability Shortcuts (Monochrome)
        renderTopRightPanel(graphics, font, width);

        // 5. Bottom-Left Panel: Live Event Log Feed (Monochrome)
        renderBottomLeftLog(graphics, font, height);

        // 6. Bottom-Right Panel: Tier Badge, AP Bar, EXP Bar (Monochrome)
        renderBottomRightResources(graphics, font, width, height);
    }

    private static void renderCrtFisheyeVignette(GuiGraphics graphics, int width, int height, Minecraft mc) {
        // CRT Scanline raster layer
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0f, 0.9f, 1.0f, 0.10f);
        graphics.blit(SCANLINES, 0, 0, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Subtle CRT rolling scan line
        long time = mc.level != null ? mc.level.getGameTime() : 0;
        int rollY = (int) ((time * 3) % Math.max(1, height));
        graphics.fill(0, rollY, width, rollY + 3, 0x1A00E5FF);

        // Outer CRT Vignette & Rounded Bezel Corners (Fisheye Glass Simulation)
        int bezel = 0xEE040A10;
        int glassFrame = 0xAA00E5FF;

        // Top & Bottom Border Bezel
        graphics.fill(0, 0, width, 6, bezel);
        graphics.fill(0, height - 6, width, height, bezel);
        graphics.fill(0, 0, 6, height, bezel);
        graphics.fill(width - 6, 0, width, height, bezel);

        // Inner glowing border wireframe
        graphics.fill(8, 8, width - 8, 9, 0x4400E5FF);
        graphics.fill(8, height - 9, width - 8, height - 8, 0x4400E5FF);
        graphics.fill(8, 8, 9, height - 8, 0x4400E5FF);
        graphics.fill(width - 9, 8, width - 8, height - 8, 0x4400E5FF);

        // Rounded CRT corner cuts
        graphics.fill(0, 0, 16, 16, bezel);
        graphics.fill(width - 16, 0, width, 16, bezel);
        graphics.fill(0, height - 16, 16, height, bezel);
        graphics.fill(width - 16, height - 16, width, height, bezel);

        graphics.fill(12, 12, 18, 14, glassFrame);
        graphics.fill(12, 12, 14, 18, glassFrame);

        graphics.fill(width - 18, 12, width - 12, 14, glassFrame);
        graphics.fill(width - 14, 12, width - 12, 18, glassFrame);

        graphics.fill(12, height - 14, 18, height - 12, glassFrame);
        graphics.fill(12, height - 18, 14, height - 12, glassFrame);

        graphics.fill(width - 18, height - 14, width - 12, height - 12, glassFrame);
        graphics.fill(width - 14, height - 18, width - 12, height - 12, glassFrame);
    }

    private static void renderTopLeftPanel(GuiGraphics graphics, Font font, Minecraft mc) {
        graphics.fill(14, 14, 215, 88, 0xCC061018);
        graphics.fill(14, 14, 16, 88, 0xFF00E5FF);
        graphics.fill(14, 14, 215, 15, 0x6600E5FF);

        CameraBlockEntity cam = FacilityNetworkManager.getCamera(ClientCameraHandler.activeCameraPos);
        String roomName = cam != null ? cam.getCameraName() : "HCZ - UNKNOWN CHAMBER";
        String zone = cam != null ? cam.getZone().getCode() : "HCZ";

        graphics.drawString(font, "● LIVE FEED [" + zone + "]", 22, 20, 0xFFE0F7FA, true);
        graphics.drawString(font, roomName.toUpperCase(), 22, 31, 0xFFE0F7FA, true);

        // Count lifeforms in room boundary or radius
        int totalLifeforms = 0;
        int scps = 0;
        int humans = 0;
        if (mc.level != null && ClientCameraHandler.activeCameraPos != null) {
            BlockPos cp = ClientCameraHandler.activeCameraPos;
            AABB searchBox;
            if (cam != null && cam.getMinBound() != null && cam.getMaxBound() != null) {
                searchBox = new AABB(cam.getMinBound(), cam.getMaxBound()).inflate(0.5);
            } else {
                searchBox = new AABB(cp).inflate(24.0);
            }

            List<LivingEntity> list = mc.level.getEntitiesOfClass(LivingEntity.class, searchBox);
            for (LivingEntity e : list) {
                if (e instanceof Player p && !p.isSpectator()) {
                    totalLifeforms++;
                    humans++;
                } else if (e instanceof Monster) {
                    totalLifeforms++;
                    scps++;
                }
            }
        }

        graphics.drawString(font, "TOTAL LIFEFORMS: " + totalLifeforms, 22, 46, 0xFFE0F7FA, false);
        graphics.drawString(font, "TARGETS / HUMANS: " + humans, 22, 57, 0xAA80DEEA, false);
        graphics.drawString(font, "ACTIVE SCPS: " + scps, 22, 68, 0xAA80DEEA, false);
    }

    private static void renderTopRightPanel(GuiGraphics graphics, Font font, int width) {
        int rx = width - 185;
        graphics.fill(rx, 14, width - 14, 88, 0xCC061018);
        graphics.fill(width - 16, 14, width - 14, 88, 0xFF00E5FF);
        graphics.fill(rx, 14, width - 14, 15, 0x6600E5FF);

        graphics.drawString(font, "SURVEILLANCE ROUTING", rx + 10, 20, 0xFFE0F7FA, true);

        // WASD key badges
        renderKeyBadge(graphics, font, rx + 65, 32, "W");
        renderKeyBadge(graphics, font, rx + 45, 48, "A");
        renderKeyBadge(graphics, font, rx + 65, 48, "S");
        renderKeyBadge(graphics, font, rx + 85, 48, "D");

        graphics.drawString(font, "2 AP PER JUMP", rx + 108, 42, 0xAA80DEEA, false);

        // Ability shortcuts
        String fTxt = ClientCameraHandler.tier >= 2 ? "[F] BLACKOUT (40 AP)" : "[F] BLACKOUT (TIER 2)";
        String gTxt = ClientCameraHandler.tier >= 3 ? "[G] LOCKDOWN (60 AP)" : "[G] LOCKDOWN (TIER 3)";
        graphics.drawString(font, fTxt, rx + 10, 64, 0xAA80DEEA, false);
        graphics.drawString(font, gTxt, rx + 10, 75, 0xAA80DEEA, false);
    }

    private static void renderKeyBadge(GuiGraphics graphics, Font font, int x, int y, String key) {
        graphics.fill(x, y, x + 16, y + 14, 0xDD091522);
        graphics.fill(x, y, x + 16, y + 1, 0xFF00E5FF);
        graphics.drawString(font, key, x + 4, y + 3, 0xFFE0F7FA, false);
    }

    private static void renderBottomLeftLog(GuiGraphics graphics, Font font, int height) {
        int ly = height - 100;
        graphics.fill(14, ly, 230, height - 14, 0xCC061018);
        graphics.fill(14, ly, 16, height - 14, 0xFF00E5FF);
        graphics.fill(14, ly, 230, ly + 1, 0x6600E5FF);

        graphics.drawString(font, "SYSTEM EVENT FEED", 22, ly + 6, 0xFFE0F7FA, true);

        List<Scp079Session.LogEntry> list = ClientCameraHandler.logs;
        int maxShow = Math.min(5, list.size());
        for (int i = 0; i < maxShow; i++) {
            Scp079Session.LogEntry entry = list.get(list.size() - 1 - i);
            graphics.drawString(font, "> " + entry.message, 22, ly + 20 + (i * 12), 0xAA80DEEA, false);
        }
    }

    private static void renderBottomRightResources(GuiGraphics graphics, Font font, int width, int height) {
        int rx = width - 215;
        int ry = height - 100;

        graphics.fill(rx, ry, width - 14, height - 14, 0xCC061018);
        graphics.fill(width - 16, ry, width - 14, height - 14, 0xFF00E5FF);
        graphics.fill(rx, ry, width - 14, ry + 1, 0x6600E5FF);

        // Tier Badge
        graphics.drawString(font, "ACCESS TIER " + ClientCameraHandler.tier + " / 5", rx + 10, ry + 6, 0xFFE0F7FA, true);

        // AP Bar
        String apTxt = String.format("AUXILIARY POWER: %.0f / %.0f", ClientCameraHandler.ap, ClientCameraHandler.maxAp);
        graphics.drawString(font, apTxt, rx + 10, ry + 20, 0xAA80DEEA, false);

        int barW = 180;
        int barH = 10;
        int barX = rx + 10;
        int barY = ry + 32;

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xEE091522);
        float apRatio = Math.max(0.0f, Math.min(1.0f, ClientCameraHandler.ap / ClientCameraHandler.maxAp));
        int fillW = (int) (barW * apRatio);
        graphics.fill(barX, barY, barX + fillW, barY + barH, 0xFF00E5FF);

        // EXP Bar
        String expTxt = String.format("EXP: %d / %d", ClientCameraHandler.exp, ClientCameraHandler.nextExp);
        graphics.drawString(font, expTxt, rx + 10, ry + 48, 0xAA80DEEA, false);

        int expBarY = ry + 60;
        graphics.fill(barX, expBarY, barX + barW, expBarY + 6, 0xEE091522);
        float expRatio = Math.max(0.0f, Math.min(1.0f, (float) ClientCameraHandler.exp / ClientCameraHandler.nextExp));
        int expFillW = (int) (barW * expRatio);
        graphics.fill(barX, expBarY, barX + expFillW, expBarY + 6, 0xFFE0F7FA);

        // Tactical Map Shortcut
        graphics.drawString(font, "[TAB] TACTICAL SCHEMATIC", rx + 10, ry + 72, 0xFFE0F7FA, true);
    }
}
