package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.client.handler.ClientCameraHandler;
import dev.zerosevennine.facility.DeviceType;
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
    private static final ResourceLocation DOOR_ICON = new ResourceLocation(ZeroSevenNine.MOD_ID, "textures/gui/door_icon.png");
    private static final ResourceLocation TESLA_ICON = new ResourceLocation(ZeroSevenNine.MOD_ID, "textures/gui/tesla_icon.png");

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        // 1. Draw CRT Scanlines texture overlay
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0f, 0.9f, 1.0f, 0.12f);
        graphics.blit(SCANLINES, 0, 0, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 2. Center Reticle & Zoom Indicator
        int cx = width / 2;
        int cy = height / 2;

        // Reticle brackets
        int rColor = 0x8800E5FF;
        graphics.fill(cx - 15, cy - 15, cx - 10, cy - 14, rColor);
        graphics.fill(cx - 15, cy - 15, cx - 14, cy - 10, rColor);

        graphics.fill(cx + 10, cy - 15, cx + 15, cy - 14, rColor);
        graphics.fill(cx + 14, cy - 15, cx + 15, cy - 10, rColor);

        graphics.fill(cx - 15, cy + 14, cx - 10, cy + 15, rColor);
        graphics.fill(cx - 15, cy + 10, cx - 14, cy + 15, rColor);

        graphics.fill(cx + 10, cy + 14, cx + 15, cy + 15, rColor);
        graphics.fill(cx + 14, cy + 10, cx + 15, cy + 10, rColor);

        // Center dot
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF00E5FF);

        if (ClientCameraHandler.isZoomed) {
            String zoomTxt = "[OPTICAL ZOOM 2.8X]";
            graphics.drawString(font, zoomTxt, cx - (font.width(zoomTxt) / 2), cy + 22, 0x00E5FF, true);
        }

        // 3. Top-Left Panel: Room Information & Lifeform Status
        renderTopLeftPanel(graphics, font, mc);

        // 4. Top-Right Panel: WASD Camera Jumps & Ability Shortcuts
        renderTopRightPanel(graphics, font, width);

        // 5. Bottom-Left Panel: Live Event Log Feed
        renderBottomLeftLog(graphics, font, height);

        // 6. Bottom-Right Panel: Tier Badge, AP Bar, EXP Bar
        renderBottomRightResources(graphics, font, width, height);
    }

    private static void renderTopLeftPanel(GuiGraphics graphics, Font font, Minecraft mc) {
        // Background card
        graphics.fill(12, 12, 210, 85, 0x990A1118);
        graphics.fill(12, 12, 14, 85, 0xFF00E5FF);

        CameraBlockEntity cam = FacilityNetworkManager.getCamera(ClientCameraHandler.activeCameraPos);
        String roomName = cam != null ? cam.getCameraName() : "HCZ - UNKNOWN CHAMBER";
        String zone = cam != null ? cam.getZone().getCode() : "HCZ";

        graphics.drawString(font, "● LIVE FEED [" + zone + "]", 20, 18, 0xFF3333, true);
        graphics.drawString(font, roomName.toUpperCase(), 20, 29, 0xFFFFFF, true);

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

        graphics.drawString(font, "TOTAL LIFEFORMS: " + totalLifeforms, 20, 43, 0x00E5FF, false);
        graphics.drawString(font, "TARGETS / HUMANS: " + humans, 20, 54, 0xBDC3C7, false);
        graphics.drawString(font, "ACTIVE SCPS: " + scps, 20, 65, 0xE74C3C, false);
    }

    private static void renderTopRightPanel(GuiGraphics graphics, Font font, int width) {
        int rx = width - 180;
        graphics.fill(rx, 12, width - 12, 85, 0x990A1118);
        graphics.fill(width - 14, 12, width - 12, 85, 0xFF00E5FF);

        graphics.drawString(font, "SURVEILLANCE ROUTING", rx + 10, 18, 0x00E5FF, true);

        // WASD key badges
        renderKeyBadge(graphics, font, rx + 65, 30, "W");
        renderKeyBadge(graphics, font, rx + 45, 48, "A");
        renderKeyBadge(graphics, font, rx + 65, 48, "S");
        renderKeyBadge(graphics, font, rx + 85, 48, "D");
        graphics.drawString(font, "2 AP", rx + 115, 42, 0x00E5FF, false);

        // Abilities
        int tier = ClientCameraHandler.tier;
        int blackoutCol = tier >= 2 ? 0xF39C12 : 0x555555;
        int lockdownCol = tier >= 3 ? 0xE74C3C : 0x555555;

        graphics.drawString(font, "[F] BLACKOUT (40 AP)", rx + 10, 68, blackoutCol, false);
    }

    private static void renderKeyBadge(GuiGraphics graphics, Font font, int x, int y, String key) {
        graphics.fill(x, y, x + 16, y + 16, 0xCC1A252F);
        graphics.fill(x, y, x + 16, y + 1, 0xFF00E5FF);
        graphics.drawString(font, key, x + 4, y + 4, 0xFFFFFF, false);
    }

    private static void renderBottomLeftLog(GuiGraphics graphics, Font font, int height) {
        int by = height - 120;
        graphics.fill(12, by, 220, height - 12, 0x880A1118);
        graphics.fill(12, by, 14, height - 12, 0xFF2ECC71);

        graphics.drawString(font, "SYSTEM EVENT FEED", 20, by + 6, 0x2ECC71, true);

        int lineY = by + 20;
        List<Scp079Session.LogEntry> logs = ClientCameraHandler.logs;
        for (int i = Math.max(0, logs.size() - 6); i < logs.size(); i++) {
            Scp079Session.LogEntry entry = logs.get(i);
            graphics.drawString(font, "> " + entry.message, 20, lineY, entry.colorRgb, false);
            lineY += 12;
        }
    }

    private static void renderBottomRightResources(GuiGraphics graphics, Font font, int width, int height) {
        int bx = width - 230;
        int by = height - 95;

        // Background card
        graphics.fill(bx, by, width - 12, height - 12, 0xCC0A1118);
        graphics.fill(width - 14, by, width - 12, height - 12, 0xFF00E5FF);

        // Tier Badge
        String tierStr = "ACCESS TIER " + ClientCameraHandler.tier;
        graphics.drawString(font, tierStr, bx + 12, by + 8, 0x00E5FF, true);

        if (ClientCameraHandler.breachScannerActive) {
            graphics.drawString(font, "● SCANNER ON (-50%)", bx + 115, by + 8, 0x2ECC71, true);
        }

        // AP Progress Bar
        float apPct = Math.min(1.0f, Math.max(0.0f, ClientCameraHandler.ap / Math.max(1.0f, ClientCameraHandler.maxAp)));
        int barW = 190;
        int barH = 10;
        int barX = bx + 12;
        int barY = by + 24;

        graphics.drawString(font, String.format("AUXILIARY POWER: %.0f / %.0f AP (+%.1f/s)",
                ClientCameraHandler.ap, ClientCameraHandler.maxAp, ClientCameraHandler.apRegen), barX, barY - 9, 0xBDC3C7, false);

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF1C2833);
        graphics.fill(barX, barY, barX + (int)(barW * apPct), barY + barH, 0xFF00E5FF);

        // EXP Progress Bar
        float expPct = Math.min(1.0f, Math.max(0.0f, (float)ClientCameraHandler.exp / Math.max(1.0f, (float)ClientCameraHandler.nextExp)));
        int expBarY = by + 50;

        graphics.drawString(font, "EXP: " + ClientCameraHandler.exp + " / " + ClientCameraHandler.nextExp, barX, expBarY - 9, 0x2ECC71, false);
        graphics.fill(barX, expBarY, barX + barW, expBarY + 6, 0xFF1C2833);
        graphics.fill(barX, expBarY, barX + (int)(barW * expPct), expBarY + 6, 0xFF2ECC71);

        graphics.drawString(font, "[TAB] TACTICAL MAP", bx + 12, by + 68, 0xF39C12, false);
    }
}
