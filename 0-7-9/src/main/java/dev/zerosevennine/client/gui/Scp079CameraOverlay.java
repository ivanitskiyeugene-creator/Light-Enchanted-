package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.client.handler.ClientCameraHandler;
import dev.zerosevennine.facility.FacilityNetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class Scp079CameraOverlay {
    private static final ResourceLocation SCANLINES = new ResourceLocation(ZeroSevenNine.MOD_ID, "textures/gui/scanlines.png");

    // Authentic SCP:SL Monochrome Palette: Ice-White / Pale Cyan
    private static final int TEXT_COLOR = 0xFFE0F7FA;
    private static final int TEXT_DIM = 0x5580DEEA;
    private static final int FRAME_COLOR = 0xFFE0F7FA;
    private static final int BG_COLOR = 0x88000000;
    private static final int BAR_FILL_COLOR = 0xCCE0F7FA;

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        // 1. CRT Scanlines & Subtle Corner Vignette
        renderCrtOverlay(graphics, width, height, mc);

        // 2. Technical Screen Corner Brackets [┌ ┐ └ ┘]
        renderScreenCornerBrackets(graphics, width, height);

        // 3. Center Minimalist Reticle
        renderCenterReticle(graphics, width, height);

        // 4. Top-Left Header: Room Name & WASD Camera Routing List
        renderTopLeftRouting(graphics, font, mc);

        // 5. Top-Right Action List: Map, Ping, Speaker, Blackout, Lockdown
        renderTopRightActions(graphics, font, width);

        // 6. Bottom-Right Resource Panel: Access Tier, EXP Bar, Auxiliary Power Bar
        renderBottomRightResources(graphics, font, width, height);
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

        String roomName = cam != null ? cam.getCameraName() : "HCZ THREE-WAY";
        int lx = 32;
        int ly = 28;

        // Large Room Title
        graphics.drawString(font, roomName.toUpperCase(), lx, ly, TEXT_COLOR, true);

        // WASD Neighbor routing list
        Map<Direction, BlockPos> neighbors = cam != null ? cam.getWasdNeighbors() : Map.of();

        renderWasdEntry(graphics, font, lx, ly + 16, "W", neighbors.get(Direction.NORTH));
        renderWasdEntry(graphics, font, lx, ly + 28, "S", neighbors.get(Direction.SOUTH));
        renderWasdEntry(graphics, font, lx, ly + 40, "A", neighbors.get(Direction.WEST));
        renderWasdEntry(graphics, font, lx, ly + 52, "D", neighbors.get(Direction.EAST));
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
            } else if (FacilityNetworkManager.getRooms().containsKey(targetPos)) {
                targetName = FacilityNetworkManager.getRooms().get(targetPos).roomName;
            } else {
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
        String apTitle = "AUXILIARY POWER";
        graphics.drawString(font, apTitle, rx + (barW - font.width(apTitle)) / 2, ry + 2, TEXT_COLOR, true);

        // 4. Auxiliary Power Bar
        String apTxt = (int) ClientCameraHandler.ap + " / " + (int) ClientCameraHandler.maxAp;
        renderProgressBar(graphics, font, rx, ry + 13, barW, barH, ClientCameraHandler.ap / Math.max(1.0f, ClientCameraHandler.maxAp), apTxt);
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
