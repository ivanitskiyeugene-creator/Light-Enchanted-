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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class Scp079CameraOverlay {
    private static final ResourceLocation SCANLINES = new ResourceLocation(ZeroSevenNine.MOD_ID, "textures/gui/scanlines.png");

    // Authentic SCP:SL Monochrome Palette: Ice-White / Pale Cyan
    private static final int TEXT_COLOR = 0xFFE0F7FA;
    private static final int TEXT_DIM = 0x5580DEEA;
    private static final int FRAME_COLOR = 0xFFE0F7FA;
    private static final int BG_COLOR = 0x88000000;
    private static final int BAR_FILL_COLOR = 0xCCE0F7FA;

    private static final Random NOISE_RANDOM = new Random();

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        // 1. Inter-Zone Telephone Transition Screen
        if (ClientCameraHandler.zoneTransitionTicks > 0) {
            renderInterZoneTransition(graphics, font, width, height, mc);
            return;
        }

        // 2. Camera Noise / Static Glitch Burst
        if (ClientCameraHandler.switchStaticTicks > 0) {
            renderStaticNoiseGlitch(graphics, font, width, height);
        }

        // 3. CRT Scanlines & Subtle Corner Vignette
        renderCrtOverlay(graphics, width, height, mc);

        // 4. Technical Screen Corner Brackets [┌ ┐ └ ┘]
        renderScreenCornerBrackets(graphics, width, height);

        // 5. Center Minimalist Reticle
        renderCenterReticle(graphics, width, height);

        // 6. Top-Left Header: Room Name & WASD Dynamic Camera Routing List
        renderTopLeftRouting(graphics, font, mc);

        // 7. Top-Right Action List: Map, Ping, Speaker, Blackout, Lockdown
        renderTopRightActions(graphics, font, width);

        // 8. Bottom-Right Resource Panel: Access Tier, EXP Bar, Auxiliary Power Bar
        renderBottomRightResources(graphics, font, width, height);

        // 9. Bottom-Left Ephemeral Notifications (fading to 0 alpha after 3-5 seconds)
        renderBottomLeftNotifications(graphics, font, height);
    }

    private static void renderInterZoneTransition(GuiGraphics graphics, Font font, int width, int height, Minecraft mc) {
        // Deep Black Background
        graphics.fill(0, 0, width, height, 0xFF020712);

        // Scanlines
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0f, 0.9f, 1.0f, 0.15f);
        graphics.blit(SCANLINES, 0, 0, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Static Noise Dots
        for (int i = 0; i < 180; i++) {
            int nx = NOISE_RANDOM.nextInt(width);
            int ny = NOISE_RANDOM.nextInt(height);
            int nCol = (NOISE_RANDOM.nextBoolean()) ? 0x4400E5FF : 0x33E0F7FA;
            graphics.fill(nx, ny, nx + 2, ny + 1, nCol);
        }

        renderScreenCornerBrackets(graphics, width, height);

        // Centered Inter-Zone Box
        int boxW = 320;
        int boxH = 120;
        int boxX = (width - boxW) / 2;
        int boxY = (height - boxH) / 2;

        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xEE010816);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF00E5FF);
        graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0xFF00E5FF);
        graphics.fill(boxX, boxY, boxX + 1, boxY + boxH, 0xFF00E5FF);
        graphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0xFF00E5FF);

        String title = "[ ROUTING INTER-ZONE OPTICAL LINK ]";
        graphics.drawString(font, title, boxX + (boxW - font.width(title)) / 2, boxY + 10, 0xFF00E5FF, true);

        String srcTxt = "SOURCE ZONE : " + ClientCameraHandler.sourceZone.name();
        graphics.drawString(font, srcTxt, boxX + 16, boxY + 28, 0xFFE0F7FA, false);

        String dstTxt = "TARGET ZONE : " + ClientCameraHandler.targetZone.name();
        graphics.drawString(font, dstTxt, boxX + 16, boxY + 40, 0xFFE0F7FA, false);

        String destRoom = "DESTINATION : " + ClientCameraHandler.transitionTargetRoom.toUpperCase();
        graphics.drawString(font, destRoom, boxX + 16, boxY + 52, 0xFF81D4FA, false);

        int remaining = ClientCameraHandler.zoneTransitionTicks;
        int total = ClientCameraHandler.MAX_ZONE_TRANSITION_TICKS;
        float progress = 1.0f - ((float) remaining / (float) total);
        int percent = (int) (progress * 100);

        String dialTxt = "CARRIER FREQ: 350 / 440 Hz (DIALING... " + percent + "%)";
        graphics.drawString(font, dialTxt, boxX + 16, boxY + 68, 0xFF00FF88, false);

        // Progress Bar
        int pBarW = boxW - 32;
        int pBarX = boxX + 16;
        int pBarY = boxY + 80;
        int fillW = (int) (pBarW * progress);

        graphics.fill(pBarX, pBarY, pBarX + pBarW, pBarY + 8, 0xFF001528);
        if (fillW > 0) {
            graphics.fill(pBarX, pBarY, pBarX + fillW, pBarY + 8, 0xFF00E5FF);
        }
        graphics.fill(pBarX, pBarY, pBarX + pBarW, pBarY + 1, 0xFF00E5FF);
        graphics.fill(pBarX, pBarY + 7, pBarX + pBarW, pBarY + 8, 0xFF00E5FF);
        graphics.fill(pBarX, pBarY, pBarX + 1, pBarY + 8, 0xFF00E5FF);
        graphics.fill(pBarX + pBarW - 1, pBarY, pBarX + pBarW, pBarY + 8, 0xFF00E5FF);

        String statusTxt = "SYNCHRONIZING SURVEILLANCE FEED TELEMETRY...";
        graphics.drawString(font, statusTxt, boxX + (boxW - font.width(statusTxt)) / 2, boxY + 98, 0xFF5580DE, false);
    }

    private static void renderStaticNoiseGlitch(GuiGraphics graphics, Font font, int width, int height) {
        // High density random pixel static fuzz
        for (int i = 0; i < 400; i++) {
            int rx = NOISE_RANDOM.nextInt(width);
            int ry = NOISE_RANDOM.nextInt(height);
            int rw = 1 + NOISE_RANDOM.nextInt(6);
            int rh = 1 + NOISE_RANDOM.nextInt(2);
            int col = (NOISE_RANDOM.nextBoolean()) ? 0x88FFFFFF : 0x8800E5FF;
            graphics.fill(rx, ry, rx + rw, ry + rh, col);
        }

        // Glitch scanline horizontal bars
        for (int b = 0; b < 4; b++) {
            int by = NOISE_RANDOM.nextInt(height);
            int bh = 2 + NOISE_RANDOM.nextInt(4);
            graphics.fill(0, by, width, by + bh, 0x4400E5FF);
        }

        // Signal Acquisition Prompt
        String badge = "[ SIGNAL ACQUISITION // FEED SYNC... ]";
        int bw = font.width(badge) + 12;
        int bx = (width - bw) / 2;
        int by = height / 2 + 30;

        graphics.fill(bx, by, bx + bw, by + 14, 0xCC000000);
        graphics.fill(bx, by, bx + bw, by + 1, 0xFF00E5FF);
        graphics.fill(bx, by + 13, bx + bw, by + 14, 0xFF00E5FF);
        graphics.drawString(font, badge, bx + 6, by + 3, 0xFFFFFFFF, true);
    }

    private static void renderCrtOverlay(GuiGraphics graphics, int width, int height, Minecraft mc) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0f, 0.9f, 1.0f, 0.08f);
        graphics.blit(SCANLINES, 0, 0, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Subtle rolling scan line
        long time = mc.level != null ? mc.level.getGameTime() : 0;
        int rollY = (int) ((time * 2) % Math.max(1, height));
        graphics.fill(0, rollY, width, rollY + 2, 0x1200E5FF);
    }

    private static void renderScreenCornerBrackets(GuiGraphics graphics, int width, int height) {
        int pad = 22;
        int arm = 26;
        int thick = 2;

        // Top-Left ┌
        graphics.fill(pad, pad, pad + arm, pad + thick, FRAME_COLOR);
        graphics.fill(pad, pad, pad + thick, pad + arm, FRAME_COLOR);

        // Top-Right ┐
        graphics.fill(width - pad - arm, pad, width - pad, pad + thick, FRAME_COLOR);
        graphics.fill(width - pad - thick, pad, width - pad, pad + arm, FRAME_COLOR);

        // Bottom-Left └
        graphics.fill(pad, height - pad - thick, pad + arm, height - pad, FRAME_COLOR);
        graphics.fill(pad, height - pad - arm, pad + thick, height - pad, FRAME_COLOR);

        // Bottom-Right ┘
        graphics.fill(width - pad - arm, height - pad - thick, width - pad, height - pad, FRAME_COLOR);
        graphics.fill(width - pad - thick, height - pad - arm, width - pad, height - pad, FRAME_COLOR);
    }

    private static void renderCenterReticle(GuiGraphics graphics, int width, int height) {
        int cx = width / 2;
        int cy = height / 2;

        // Subtle reticle brackets
        graphics.fill(cx - 10, cy - 10, cx - 5, cy - 9, FRAME_COLOR);
        graphics.fill(cx - 10, cy - 10, cx - 9, cy - 5, FRAME_COLOR);

        graphics.fill(cx + 5, cy - 10, cx + 10, cy - 9, FRAME_COLOR);
        graphics.fill(cx + 9, cy - 10, cx + 10, cy - 5, FRAME_COLOR);

        graphics.fill(cx - 10, cy + 9, cx - 5, cy + 10, FRAME_COLOR);
        graphics.fill(cx - 10, cy + 5, cx - 9, cy + 10, FRAME_COLOR);

        graphics.fill(cx + 5, cy + 9, cx + 10, cy + 10, FRAME_COLOR);
        graphics.fill(cx + 9, cy + 5, cx + 10, cy + 10, FRAME_COLOR);

        // Center dot
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, TEXT_COLOR);
    }

    private static void renderTopLeftRouting(GuiGraphics graphics, Font font, Minecraft mc) {
        CameraBlockEntity cam = FacilityNetworkManager.getCamera(ClientCameraHandler.activeCameraPos);
        if (cam == null && mc.level != null && mc.level.getBlockEntity(ClientCameraHandler.activeCameraPos) instanceof CameraBlockEntity cbe) {
            cam = cbe;
        }

        String roomName = cam != null ? cam.getCameraName() : "FACILITY FEED";
        int lx = 32;
        int ly = 28;

        // Large Room Title
        graphics.drawString(font, roomName.toUpperCase(), lx, ly, TEXT_COLOR, true);

        // Dynamic WASD Neighbor routing list calculated automatically from facility graph
        Map<Direction, BlockPos> neighbors = FacilityNetworkManager.getDynamicWasdMap(ClientCameraHandler.activeCameraPos);

        BlockPos northTarget = neighbors.get(Direction.NORTH);
        BlockPos southTarget = neighbors.get(Direction.SOUTH);
        BlockPos westTarget = neighbors.get(Direction.WEST);
        BlockPos eastTarget = neighbors.get(Direction.EAST);

        renderWasdEntry(graphics, font, lx, ly + 16, "W", northTarget);
        renderWasdEntry(graphics, font, lx, ly + 28, "S", southTarget);
        renderWasdEntry(graphics, font, lx, ly + 40, "A", westTarget);
        renderWasdEntry(graphics, font, lx, ly + 52, "D", eastTarget);
    }

    private static void renderWasdEntry(GuiGraphics graphics, Font font, int x, int y, String key, BlockPos targetPos) {
        // Draw Key Box
        renderKeyBox(graphics, font, x, y, key, 12, 10);

        // Target Room Label
        String targetName = null;
        if (targetPos != null) {
            CameraBlockEntity targetCam = FacilityNetworkManager.getCamera(targetPos);
            if (targetCam != null) {
                targetName = targetCam.getCameraName();
            }
            FacilityNetworkManager.RoomNode targetRoom = FacilityNetworkManager.findRoomForCamera(targetPos);
            if (targetRoom != null && targetRoom.roomName != null && !targetRoom.roomName.isEmpty()) {
                targetName = targetRoom.roomName;
            }
            if (targetName == null) {
                targetName = "CONNECTED ROOM";
            }
        }

        if (targetName != null) {
            graphics.drawString(font, "GO TO: " + targetName.toUpperCase(), x + 16, y + 1, TEXT_COLOR, true);
        } else {
            graphics.drawString(font, "NO CAMERA", x + 16, y + 1, TEXT_DIM, false);
        }
    }

    private static void renderTopRightActions(GuiGraphics graphics, Font font, int width) {
        int rightMargin = width - 32;
        int ry = 28;

        renderActionRow(graphics, font, rightMargin, ry, "OPEN FACILITY MAP", "Tab", 24);
        renderActionRow(graphics, font, rightMargin, ry + 12, "PING LOCATION (0)", "E", 12);
        renderActionRow(graphics, font, rightMargin, ry + 24, "USE SPEAKER", "V", 12);

        String blackoutLabel = ClientCameraHandler.tier >= 2 ? "BLACKOUT (40)" : "BLACKOUT (LOCKED)";
        renderActionRow(graphics, font, rightMargin, ry + 36, blackoutLabel, "F", 12);

        String lockdownLabel = ClientCameraHandler.tier >= 3 ? "LOCKDOWN (100)" : "LOCKDOWN (LOCKED)";
        renderActionRow(graphics, font, rightMargin, ry + 48, lockdownLabel, "G", 12);
    }

    private static void renderActionRow(GuiGraphics graphics, Font font, int rightX, int y, String label, String key, int keyBoxW) {
        int keyX = rightX - keyBoxW;
        renderKeyBox(graphics, font, keyX, y, key, keyBoxW, 10);

        int labelW = font.width(label);
        graphics.drawString(font, label, keyX - labelW - 6, y + 1, TEXT_COLOR, true);
    }

    private static void renderKeyBox(GuiGraphics graphics, Font font, int x, int y, String text, int w, int h) {
        graphics.fill(x, y, x + w, y + h, BG_COLOR);
        graphics.fill(x, y, x + w, y + 1, FRAME_COLOR);
        graphics.fill(x, y + h - 1, x + w, y + h, FRAME_COLOR);
        graphics.fill(x, y, x + 1, y + h, FRAME_COLOR);
        graphics.fill(x + w - 1, y, x + w, y + h, FRAME_COLOR);

        int tw = font.width(text);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - 8) / 2;
        graphics.drawString(font, text, tx, ty, TEXT_COLOR, false);
    }

    private static void renderBottomRightResources(GuiGraphics graphics, Font font, int width, int height) {
        int barW = 142;
        int barH = 10;
        int rx = width - 32 - barW;
        int ry = height - 52;

        // 1. Access Tier Title
        String tierTxt = "ACCESS TIER " + ClientCameraHandler.tier;
        graphics.drawString(font, tierTxt, rx + (barW - font.width(tierTxt)) / 2, ry - 22, TEXT_COLOR, true);

        // 2. EXP Progress Bar
        String expTxt = "EXP " + ClientCameraHandler.exp + " / " + ClientCameraHandler.nextExp;
        renderProgressBar(graphics, font, rx, ry - 11, barW, barH, (float) ClientCameraHandler.exp / Math.max(1, ClientCameraHandler.nextExp), expTxt);

        // 3. Auxiliary Power Title
        String apTitle = "AUXILIARY POWER (AP)";
        graphics.drawString(font, apTitle, rx + (barW - font.width(apTitle)) / 2, ry + 2, TEXT_COLOR, true);

        // 4. Auxiliary Power Bar
        String apTxt = (int) ClientCameraHandler.ap + " / " + (int) ClientCameraHandler.maxAp;
        renderProgressBar(graphics, font, rx, ry + 13, barW, barH, ClientCameraHandler.ap / Math.max(1.0f, ClientCameraHandler.maxAp), apTxt);
    }

    private static void renderBottomLeftNotifications(GuiGraphics graphics, Font font, int height) {
        long now = System.currentTimeMillis();
        List<Scp079Session.LogEntry> logs = ClientCameraHandler.logs;
        if (logs.isEmpty()) return;

        int ly = height - 28;
        int lx = 32;
        int drawn = 0;

        for (int i = logs.size() - 1; i >= 0 && drawn < 5; i--) {
            Scp079Session.LogEntry e = logs.get(i);
            long age = now - e.timestampMs;
            if (age > 4500) continue; // Expired after 4.5 seconds

            float alpha = 1.0f;
            if (age > 3200) {
                alpha = 1.0f - ((float) (age - 3200) / 1300.0f);
            }
            int a = (int) (Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
            if (a <= 5) continue;

            int col = (a << 24) | (e.colorRgb & 0x00FFFFFF);
            graphics.drawString(font, "> " + e.message, lx, ly - (drawn * 11), col, false);
            drawn++;
        }
    }

    private static void renderProgressBar(GuiGraphics graphics, Font font, int x, int y, int w, int h, float ratio, String centerText) {
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));

        // Dark Background
        graphics.fill(x, y, x + w, y + h, BG_COLOR);

        // Filled Portion
        int fillW = (int) ((w - 2) * ratio);
        if (fillW > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, BAR_FILL_COLOR);
        }

        // Crisp 1px Outline Frame
        graphics.fill(x, y, x + w, y + 1, FRAME_COLOR);
        graphics.fill(x, y + h - 1, x + w, y + h, FRAME_COLOR);
        graphics.fill(x, y, x + 1, y + h, FRAME_COLOR);
        graphics.fill(x + w - 1, y, x + w, y + h, FRAME_COLOR);

        // Center Text
        int tw = font.width(centerText);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - 8) / 2;
        graphics.drawString(font, centerText, tx, ty, TEXT_COLOR, true);
    }
}
