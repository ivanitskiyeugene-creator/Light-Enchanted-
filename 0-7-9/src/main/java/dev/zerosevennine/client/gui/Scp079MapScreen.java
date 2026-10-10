package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.client.handler.ClientCameraHandler;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.network.C2SInteractDevicePacket;
import dev.zerosevennine.network.C2SSwitchCameraPacket;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public class Scp079MapScreen extends Screen {
    private static final ResourceLocation SCANLINES = new ResourceLocation(ZeroSevenNine.MOD_ID, "textures/gui/scanlines.png");

    private FacilityZone selectedZone = FacilityZone.HCZ;

    public Scp079MapScreen() {
        super(Component.literal("Facility Surveillance Schematic"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE && ClientCameraHandler.tier >= 4) {
            // Breach Scanner ability
            ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(ClientCameraHandler.activeCameraPos, C2SInteractDevicePacket.Action.PING));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark translucent background
        graphics.fill(0, 0, width, height, 0xDD070D14);

        // CRT Scanline effect
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0f, 0.8f, 1.0f, 0.08f);
        graphics.blit(SCANLINES, 0, 0, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Font font = this.font;

        // Top Header
        graphics.fill(20, 15, width - 20, 45, 0x99101E2E);
        graphics.fill(20, 15, width - 20, 17, 0xFF00E5FF);
        graphics.drawString(font, "SITE-02 TACTICAL SURVEILLANCE MAP // SCP-079 INTERFACE", 30, 24, 0x00E5FF, true);

        // Sidebar: Zone Selectors
        renderZoneSidebar(graphics, font, mouseX, mouseY);

        // Main Grid Schematic Area
        renderMapGrid(graphics, font, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderZoneSidebar(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int sx = 20;
        int sy = 55;
        int sw = 130;
        int sh = height - 75;

        graphics.fill(sx, sy, sx + sw, sy + sh, 0x880E1722);
        graphics.fill(sx, sy, sx + 2, sy + sh, 0xFF00E5FF);

        graphics.drawString(font, "FACILITY ZONES", sx + 10, sy + 10, 0xBDC3C7, false);

        int btnY = sy + 30;
        for (FacilityZone z : FacilityZone.values()) {
            boolean isSel = (z == selectedZone);
            int bgColor = isSel ? 0xFF00E5FF : 0x442C3E50;
            int txtColor = isSel ? 0xFF0A1118 : 0xECF0F1;

            graphics.fill(sx + 10, btnY, sx + sw - 10, btnY + 22, bgColor);
            graphics.drawString(font, z.getCode() + " (" + z.name() + ")", sx + 16, btnY + 7, txtColor, isSel);
            btnY += 28;
        }

        // Tier 4 Breach Scanner Status
        int scanY = sy + sh - 45;
        graphics.fill(sx + 10, scanY, sx + sw - 10, scanY + 35, 0x661A252F);
        String scanTitle = ClientCameraHandler.tier >= 4 ? "§aSCANNER READY" : "§7SCANNER (TIER 4)";
        graphics.drawString(font, scanTitle, sx + 14, scanY + 6, 0xFFFFFF, false);
        graphics.drawString(font, "[SPACE] TO SCAN", sx + 14, scanY + 18, 0x7F8C8D, false);
    }

    private void renderMapGrid(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int gx = 160;
        int gy = 55;
        int gw = width - 180;
        int gh = height - 75;

        graphics.fill(gx, gy, gx + gw, gy + gh, 0x66080E16);

        // Grid lines
        for (int x = gx; x < gx + gw; x += 40) {
            graphics.fill(x, gy, x + 1, gy + gh, 0x1500E5FF);
        }
        for (int y = gy; y < gy + gh; y += 40) {
            graphics.fill(gx, y, gx + gw, y + 1, 0x1500E5FF);
        }

        int centerX = gx + gw / 2;
        int centerY = gy + gh / 2;

        Map<BlockPos, FacilityNetworkManager.RoomNode> rooms = FacilityNetworkManager.getRooms();

        // If no rooms registered yet, show placeholder grid layout
        if (rooms.isEmpty()) {
            renderSampleGrid(graphics, font, centerX, centerY, mouseX, mouseY);
            return;
        }

        for (FacilityNetworkManager.RoomNode room : rooms.values()) {
            if (room.zone != selectedZone) continue;

            int nodeX = centerX + (room.gridX * 60) - 25;
            int nodeY = centerY + (room.gridY * 60) - 20;

            boolean isHovered = mouseX >= nodeX && mouseX <= nodeX + 50 && mouseY >= nodeY && mouseY <= nodeY + 40;
            boolean isCurrent = false;

            int bgCol = isCurrent ? 0xFF2ECC71 : (isHovered ? 0xFF3498DB : 0xAA1C2833);
            graphics.fill(nodeX, nodeY, nodeX + 50, nodeY + 40, bgCol);
            graphics.fill(nodeX, nodeY, nodeX + 50, nodeY + 2, 0xFF00E5FF);

            graphics.drawString(font, room.roomName.substring(0, Math.min(8, room.roomName.length())), nodeX + 4, nodeY + 8, 0xFFFFFF, false);

            if (room.hasGenerator) {
                graphics.drawString(font, "⚡ GEN", nodeX + 4, nodeY + 22, 0xF39C12, false);
            }
        }
    }

    private void renderSampleGrid(GuiGraphics graphics, Font font, int cx, int cy, int mouseX, int mouseY) {
        String[][] demoRooms = {
                {"GATE A", "HEAVY HALL", "AIRLOCK", "SCP-096"},
                {"TESLA", "INTERCOM", "ALPHA WARHEAD", "SCP-914"},
                {"HCZ ELEV", "CHECKPOINT", "SCP-106", "GATE B"}
        };

        for (int row = -1; row <= 1; row++) {
            for (int col = -2; col <= 1; col++) {
                int nodeX = cx + (col * 70);
                int nodeY = cy + (row * 50);
                String name = demoRooms[row + 1][col + 2];

                boolean isCurrent = (row == 0 && col == -1);
                boolean isHovered = mouseX >= nodeX && mouseX <= nodeX + 60 && mouseY >= nodeY && mouseY <= nodeY + 36;

                int bgCol = isCurrent ? 0xDD27AE60 : (isHovered ? 0xDD2980B9 : 0xBB1A252F);
                int borderCol = isCurrent ? 0xFF2ECC71 : (isHovered ? 0xFF00E5FF : 0xFF34495E);

                graphics.fill(nodeX, nodeY, nodeX + 60, nodeY + 36, bgCol);
                graphics.fill(nodeX, nodeY, nodeX + 60, nodeY + 2, borderCol);

                graphics.drawString(font, name, nodeX + 4, nodeY + 6, 0xFFFFFF, false);

                if (isCurrent) {
                    graphics.drawString(font, "● ACTIVE", nodeX + 4, nodeY + 20, 0x2ECC71, false);
                } else {
                    graphics.drawString(font, "2 AP", nodeX + 4, nodeY + 20, 0x00E5FF, false);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Check Zone clicks
            int sx = 20;
            int sy = 55;
            int sw = 130;
            int btnY = sy + 30;
            for (FacilityZone z : FacilityZone.values()) {
                if (mouseX >= sx + 10 && mouseX <= sx + sw - 10 && mouseY >= btnY && mouseY <= btnY + 22) {
                    selectedZone = z;
                    return true;
                }
                btnY += 28;
            }

            // Check room camera click
            for (Map.Entry<BlockPos, FacilityNetworkManager.RoomNode> entry : FacilityNetworkManager.getRooms().entrySet()) {
                if (entry.getValue().zone == selectedZone) {
                    ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(entry.getKey()));
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
