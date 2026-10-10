package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
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
            // Toggle Breach Scanner (Halves AP regen while active)
            ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(ClientCameraHandler.activeCameraPos, C2SInteractDevicePacket.Action.BREACH_SCANNER));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark translucent surveillance background
        graphics.fill(0, 0, width, height, 0xEE070D14);

        // CRT Scanline effect
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0f, 0.8f, 1.0f, 0.08f);
        graphics.blit(SCANLINES, 0, 0, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Font font = this.font;

        // Determine current active zone based on player's current camera
        CameraBlockEntity currentCam = FacilityNetworkManager.getCamera(ClientCameraHandler.activeCameraPos);
        FacilityZone currentZone = currentCam != null ? currentCam.getZone() : FacilityZone.HCZ;

        // Top Header: Full width single zone banner
        graphics.fill(20, 15, width - 20, 48, 0x99101E2E);
        graphics.fill(20, 15, width - 20, 17, 0xFF00E5FF);
        graphics.drawString(font, "SITE-02 TACTICAL SCHEMATIC // " + currentZone.getDisplayName().toUpperCase() + " [" + currentZone.getCode() + "]", 30, 24, 0x00E5FF, true);

        // Subheader status
        String apStatus = String.format("AP: %.0f / %.0f (Regen: +%.1f/s)", ClientCameraHandler.ap, ClientCameraHandler.maxAp, ClientCameraHandler.apRegen);
        graphics.drawString(font, apStatus, width - 220, 24, 0xBDC3C7, false);

        // Full Screen Schematic Grid Area for Current Zone
        renderActiveZoneGrid(graphics, font, currentZone, mouseX, mouseY);

        // Bottom Bar: Breach Scanner Status & Instructions
        renderBottomStatusBar(graphics, font);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderActiveZoneGrid(GuiGraphics graphics, Font font, FacilityZone currentZone, int mouseX, int mouseY) {
        int gx = 25;
        int gy = 55;
        int gw = width - 50;
        int gh = height - 100;

        graphics.fill(gx, gy, gx + gw, gy + gh, 0x66080E16);

        // Vector grid background
        for (int x = gx; x < gx + gw; x += 40) {
            graphics.fill(x, gy, x + 1, gy + gh, 0x1500E5FF);
        }
        for (int y = gy; y < gy + gh; y += 40) {
            graphics.fill(gx, y, gx + gw, y + 1, 0x1500E5FF);
        }

        int centerX = gx + gw / 2;
        int centerY = gy + gh / 2;

        Map<BlockPos, FacilityNetworkManager.RoomNode> rooms = FacilityNetworkManager.getRooms();

        // If no custom rooms registered yet, show authentic default zone layout
        if (rooms.isEmpty()) {
            renderSampleZoneLayout(graphics, font, currentZone, centerX, centerY, mouseX, mouseY);
            return;
        }

        for (Map.Entry<BlockPos, FacilityNetworkManager.RoomNode> entry : rooms.entrySet()) {
            BlockPos roomPos = entry.getKey();
            FacilityNetworkManager.RoomNode room = entry.getValue();

            // Strictly render only rooms belonging to current active zone
            if (room.zone != currentZone) continue;

            int nodeX = centerX + (room.gridX * 75) - 32;
            int nodeY = centerY + (room.gridY * 55) - 22;
            int nodeW = 64;
            int nodeH = 44;

            boolean isHovered = mouseX >= nodeX && mouseX <= nodeX + nodeW && mouseY >= nodeY && mouseY <= nodeY + nodeH;
            boolean isCurrent = roomPos.equals(ClientCameraHandler.activeCameraPos);
            boolean isElevatorOrCheckpoint = (room.roomType == FacilityMapNodeBlockEntity.RoomType.ELEVATOR ||
                                              room.roomType == FacilityMapNodeBlockEntity.RoomType.CHECKPOINT);

            // Entire map is clearly visible in blue/cyan from start
            int bgCol = isCurrent ? 0xFF27AE60 : (isHovered ? 0xFF2980B9 : 0xDD1B2631);
            int borderCol = isCurrent ? 0xFF2ECC71 : (isHovered ? 0xFF00E5FF : 0xFF34495E);

            if (isElevatorOrCheckpoint && !isCurrent) {
                bgCol = isHovered ? 0xFFD35400 : 0xDD7E5109;
                borderCol = isHovered ? 0xFFF39C12 : 0xFFE67E22;
            }

            graphics.fill(nodeX, nodeY, nodeX + nodeW, nodeY + nodeH, bgCol);
            graphics.fill(nodeX, nodeY, nodeX + nodeW, nodeY + 2, borderCol);

            // Room Name
            String shortName = room.roomName.length() > 9 ? room.roomName.substring(0, 9) : room.roomName;
            graphics.drawString(font, shortName, nodeX + 4, nodeY + 6, 0xFFFFFF, false);

            if (isCurrent) {
                graphics.drawString(font, "● ACTIVE", nodeX + 4, nodeY + 22, 0x2ECC71, false);
            } else if (isElevatorOrCheckpoint) {
                String dest = room.targetZone != null ? room.targetZone.getCode() : "TRANS";
                graphics.drawString(font, "➔ " + dest + " [10 AP]", nodeX + 4, nodeY + 22, 0xF39C12, false);
            } else {
                graphics.drawString(font, "FREE", nodeX + 4, nodeY + 22, 0x00E5FF, false);
            }

            if (room.hasGenerator) {
                int genCol = room.isGeneratorBooting ? 0xE74C3C : 0xF39C12;
                graphics.drawString(font, "⚡ GEN", nodeX + 4, nodeY + 32, genCol, false);
            }
        }
    }

    private void renderSampleZoneLayout(GuiGraphics graphics, Font font, FacilityZone zone, int cx, int cy, int mouseX, int mouseY) {
        // Authentic layouts tailored to each zone
        String[][] layout;
        if (zone == FacilityZone.HCZ) {
            layout = new String[][]{
                    {"ELEV A (➔LCZ)", "HEAVY HALL", "AIRLOCK", "SCP-096"},
                    {"TESLA GATE", "INTERCOM", "ALPHA WARHEAD", "SCP-914"},
                    {"CHECKPOINT (➔EZ)", "SCP-049", "SCP-106", "ELEV B (➔LCZ)"}
            };
        } else if (zone == FacilityZone.LCZ) {
            layout = new String[][]{
                    {"ELEV A (➔HCZ)", "LCZ HALLWAY", "GR-18", "PC-15"},
                    {"SCP-914", "AIRLOCK", "SCP-330", "SCP-173"},
                    {"ELEV B (➔HCZ)", "LCZ CHECKPOINT", "WC-00", "ARMORY"}
            };
        } else if (zone == FacilityZone.EZ) {
            layout = new String[][]{
                    {"CHECKPOINT (➔HCZ)", "OFFICES", "INTERCOM", "GATE A (➔SURF)"},
                    {"SERVER ROOM", "OFFICE HALL", "COLLIDER", "GATE B (➔SURF)"},
                    {"EVAC SHELTER", "EZ CHECKPOINT", "LOADING BAY", "SECURITY"}
            };
        } else {
            layout = new String[][]{
                    {"GATE A EXIT", "SURFACE ROAD", "HELIPAD", "WARHEAD ROOM"},
                    {"GATE B EXIT", "TUNNEL", "MTF SPAWN", "CHAOS SPAWN"}
            };
        }

        int rows = layout.length;
        int cols = layout[0].length;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int nodeX = cx + ((col - (cols / 2)) * 82) - 38;
                int nodeY = cy + ((row - (rows / 2)) * 60) - 24;
                int nodeW = 76;
                int nodeH = 48;

                String name = layout[row][col];
                boolean isTransition = name.contains("➔");
                boolean isCurrent = (row == 0 && col == 1);
                boolean isHovered = mouseX >= nodeX && mouseX <= nodeX + nodeW && mouseY >= nodeY && mouseY <= nodeY + nodeH;

                int bgCol = isCurrent ? 0xFF27AE60 : (isHovered ? 0xFF2980B9 : 0xDD1B2631);
                int borderCol = isCurrent ? 0xFF2ECC71 : (isHovered ? 0xFF00E5FF : 0xFF34495E);

                if (isTransition && !isCurrent) {
                    bgCol = isHovered ? 0xFFD35400 : 0xDD7E5109;
                    borderCol = isHovered ? 0xFFF39C12 : 0xFFE67E22;
                }

                graphics.fill(nodeX, nodeY, nodeX + nodeW, nodeY + nodeH, bgCol);
                graphics.fill(nodeX, nodeY, nodeX + nodeW, nodeY + 2, borderCol);

                graphics.drawString(font, name.length() > 11 ? name.substring(0, 11) : name, nodeX + 4, nodeY + 6, 0xFFFFFF, false);

                if (isCurrent) {
                    graphics.drawString(font, "● ACTIVE", nodeX + 4, nodeY + 22, 0x2ECC71, false);
                } else if (isTransition) {
                    graphics.drawString(font, "10 AP (JUMP)", nodeX + 4, nodeY + 22, 0xF39C12, false);
                } else {
                    graphics.drawString(font, "FREE", nodeX + 4, nodeY + 22, 0x00E5FF, false);
                }

                if (name.contains("TESLA") || name.contains("WARHEAD")) {
                    graphics.drawString(font, "⚡ POWER", nodeX + 4, nodeY + 34, 0xF39C12, false);
                }
            }
        }
    }

    private void renderBottomStatusBar(GuiGraphics graphics, Font font) {
        int by = height - 40;
        graphics.fill(25, by, width - 25, by + 30, 0x99101E2E);
        graphics.fill(25, by, width - 25, by + 2, 0xFF00E5FF);

        // Breach Scanner Indicator
        boolean scannerOn = ClientCameraHandler.breachScannerActive;
        int scannerCol = scannerOn ? 0xFF2ECC71 : (ClientCameraHandler.tier >= 4 ? 0xFFF39C12 : 0xFF7F8C8D);
        String scannerTxt = scannerOn ? "BREACH SCANNER: [ACTIVE (-50% AP REGEN)]" :
                (ClientCameraHandler.tier >= 4 ? "BREACH SCANNER: [STANDBY] - PRESS [SPACE] TO TOGGLE" : "BREACH SCANNER: [LOCKED - REQUIRES TIER 4]");

        graphics.drawString(font, scannerTxt, 35, by + 10, scannerCol, true);

        String helpTxt = "CLICK ROOM: FREE  |  CLICK ELEVATOR/CHECKPOINT: 10 AP  |  [TAB] RETURN";
        graphics.drawString(font, helpTxt, width - font.width(helpTxt) - 35, by + 10, 0xBDC3C7, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            CameraBlockEntity currentCam = FacilityNetworkManager.getCamera(ClientCameraHandler.activeCameraPos);
            FacilityZone currentZone = currentCam != null ? currentCam.getZone() : FacilityZone.HCZ;

            // Check if clicking any room in the network
            for (Map.Entry<BlockPos, FacilityNetworkManager.RoomNode> entry : FacilityNetworkManager.getRooms().entrySet()) {
                FacilityNetworkManager.RoomNode node = entry.getValue();

                if (node.zone == currentZone) {
                    ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(entry.getKey()));
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
