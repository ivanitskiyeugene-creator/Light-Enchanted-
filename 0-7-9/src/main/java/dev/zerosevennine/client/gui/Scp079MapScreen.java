package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.client.handler.ClientCameraHandler;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.network.C2SInteractDevicePacket;
import dev.zerosevennine.network.C2SRequestMapSyncPacket;
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

    private float animProgress = 0.0f;
    private boolean isClosing = false;

    private double panX = 0.0;
    private double panY = 0.0;
    private double lastMouseX = -1.0;
    private double lastMouseY = -1.0;
    private boolean isDragging = false;

    private BlockPos focusedRoomPos = null;
    private FacilityZone selectedZone = FacilityZone.HCZ;

    public Scp079MapScreen() {
        super(Component.literal("Facility Surveillance Schematic"));
    }

    @Override
    protected void init() {
        super.init();
        animProgress = 0.0f;
        isClosing = false;
        lastMouseX = -1.0;
        lastMouseY = -1.0;
        isDragging = false;

        // Determine current zone from active camera
        CameraBlockEntity currentCam = FacilityNetworkManager.getCamera(ClientCameraHandler.activeCameraPos);
        if (currentCam != null) {
            selectedZone = currentCam.getZone();
        } else {
            selectedZone = FacilityZone.HCZ;
        }

        // Request live facility map layout from server
        ModNetwork.CHANNEL.sendToServer(new C2SRequestMapSyncPacket());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!isClosing) {
            isClosing = true;
            return;
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        // Number keys 1-4 for quick zone tab selection
        if (keyCode == GLFW.GLFW_KEY_1) {
            selectedZone = FacilityZone.LCZ;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_2) {
            selectedZone = FacilityZone.HCZ;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_3) {
            selectedZone = FacilityZone.EZ;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_4) {
            selectedZone = FacilityZone.SURFACE;
            return true;
        }

        // Space / Enter: Jump to currently focused room under reticle
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            if (focusedRoomPos != null) {
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(focusedRoomPos));
                this.onClose();
                return true;
            } else if (ClientCameraHandler.tier >= 4 && keyCode == GLFW.GLFW_KEY_SPACE) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(ClientCameraHandler.activeCameraPos, C2SInteractDevicePacket.Action.BREACH_SCANNER));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        panX += dragX;
        panY += dragY;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // 1. Check Zone selection tab clicks in header
            int tabY = 14;
            int tabStartX = 320;
            FacilityZone[] zones = FacilityZone.values();
            for (int i = 0; i < zones.length; i++) {
                int tx = tabStartX + (i * 70);
                if (mouseX >= tx && mouseX <= tx + 62 && mouseY >= tabY && mouseY <= tabY + 22) {
                    selectedZone = zones[i];
                    return true;
                }
            }

            // 2. Direct click on any room cell on the tactical vector map
            int cx = width / 2;
            int cy = height / 2;
            Map<BlockPos, FacilityNetworkManager.RoomNode> rooms = FacilityNetworkManager.getRooms();

            for (Map.Entry<BlockPos, FacilityNetworkManager.RoomNode> entry : rooms.entrySet()) {
                FacilityNetworkManager.RoomNode room = entry.getValue();
                if (room.zone != selectedZone) continue;

                int nodeW = 54;
                int nodeH = 38;
                int nodeX = cx + (int) panX + (room.gridX * 72) - (nodeW / 2);
                int nodeY = cy + (int) panY + (room.gridY * 54) - (nodeH / 2);

                if (mouseX >= nodeX && mouseX <= nodeX + nodeW && mouseY >= nodeY && mouseY <= nodeY + nodeH) {
                    ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(entry.getKey()));
                    this.onClose();
                    return true;
                }
            }

            // 3. Fallback: jump to room centered under reticle
            if (focusedRoomPos != null) {
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(focusedRoomPos));
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Update CRT TV turn-on / turn-off animation
        if (!isClosing) {
            animProgress = Math.min(1.0f, animProgress + 0.16f);
        } else {
            animProgress = Math.max(0.0f, animProgress - 0.16f);
            if (animProgress <= 0.0f) {
                super.onClose();
                return;
            }
        }

        int cx = width / 2;
        int cy = height / 2;

        // Dark background fill
        graphics.fill(0, 0, width, height, 0xFF040A10);

        // Old CRT TV Turn-on / Turn-off horizontal laser line & vertical expansion
        if (animProgress < 0.20f) {
            graphics.fill(0, cy - 2, width, cy + 2, 0xEE00E5FF);
            graphics.fill(cx - 60, cy - 3, cx + 60, cy + 3, 0xFFE0F7FA);
            graphics.fill(cx - 20, cy - 4, cx + 20, cy + 4, 0xFFFFFFFF);
            return;
        }

        float vRatio = (animProgress - 0.20f) / 0.80f;
        int clipH = (int) (height * vRatio);
        int topY = cy - (clipH / 2);
        int bottomY = cy + (clipH / 2);

        // Scanlines overlay
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0f, 0.9f, 1.0f, 0.10f);
        graphics.blit(SCANLINES, 0, topY, 0, 0, width, clipH, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Font font = this.font;

        // Top Header Bar
        renderHeader(graphics, font, mouseX, mouseY, topY);

        // Render Vector Schematic Grid with Center Reticle & Panning
        renderVectorMap(graphics, font, mouseX, mouseY, cx, cy, topY, bottomY);

        // Fixed Reticle Locked at Screen Center
        renderCenterFixedReticle(graphics, font, cx, cy);

        // Bottom Status Bar
        renderBottomStatusBar(graphics, font, bottomY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics, Font font, int mouseX, int mouseY, int topY) {
        int hY = topY + 10;
        int hH = 32;

        graphics.fill(20, hY, width - 20, hY + hH, 0xCC061018);
        graphics.fill(20, hY, width - 20, hY + 2, 0xFF00E5FF);

        String title = "SITE-02 TACTICAL SCHEMATIC // TIER " + ClientCameraHandler.tier;
        graphics.drawString(font, title, 30, hY + 12, 0xFFE0F7FA, true);

        // Zone Tabs ([LCZ], [HCZ], [EZ], [SURFACE])
        int tabStartX = 320;
        FacilityZone[] zones = FacilityZone.values();
        for (int i = 0; i < zones.length; i++) {
            FacilityZone z = zones[i];
            int tx = tabStartX + (i * 70);
            boolean isSel = (z == selectedZone);
            boolean isHov = (mouseX >= tx && mouseX <= tx + 62 && mouseY >= hY + 6 && mouseY <= hY + 26);

            int tabBg = isSel ? 0xFF00E5FF : (isHov ? 0xAA0B2233 : 0x88061018);
            int tabTxt = isSel ? 0xFF040A10 : (isHov ? 0xFFFFFFFF : 0xAA80DEEA);

            graphics.fill(tx, hY + 6, tx + 62, hY + 26, tabBg);
            graphics.fill(tx, hY + 6, tx + 62, hY + 7, isSel ? 0xFFFFFFFF : 0xFF00E5FF);

            String zCode = "[" + z.getCode() + "]";
            int tw = font.width(zCode);
            graphics.drawString(font, zCode, tx + (31 - tw / 2), hY + 12, tabTxt, false);
        }

        // AP Counter
        String apStatus = String.format("AP: %.0f/%.0f (+%.1f/s)", ClientCameraHandler.ap, ClientCameraHandler.maxAp, ClientCameraHandler.apRegen);
        graphics.drawString(font, apStatus, width - font.width(apStatus) - 30, hY + 12, 0xAA80DEEA, false);
    }

    private void renderCenterFixedReticle(GuiGraphics graphics, Font font, int cx, int cy) {
        int reticleCol = 0xFF00E5FF;

        // Reticle brackets around screen center
        graphics.fill(cx - 18, cy - 18, cx - 12, cy - 17, reticleCol);
        graphics.fill(cx - 18, cy - 18, cx - 17, cy - 12, reticleCol);

        graphics.fill(cx + 12, cy - 18, cx + 18, cy - 17, reticleCol);
        graphics.fill(cx + 17, cy - 18, cx + 18, cy - 12, reticleCol);

        graphics.fill(cx - 18, cy + 17, cx - 12, cy + 18, reticleCol);
        graphics.fill(cx - 18, cy + 12, cx - 17, cy + 18, reticleCol);

        graphics.fill(cx + 12, cy + 17, cx + 18, cy + 18, reticleCol);
        graphics.fill(cx + 17, cy + 12, cx + 18, cy + 18, reticleCol);

        // Center dot
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFE0F7FA);

        // Target prompt below center reticle
        if (focusedRoomPos != null) {
            String prompt = "[LMB / SPACE] SWITCH CAMERA";
            graphics.drawString(font, prompt, cx - (font.width(prompt) / 2), cy + 24, 0xFFE0F7FA, true);
        }
    }

    private void renderVectorMap(GuiGraphics graphics, Font font, int mouseX, int mouseY, int cx, int cy, int topY, int bottomY) {
        // Background coordinate grid lines
        int gridSpacing = 48;
        int gridOffsetX = ((int) panX) % gridSpacing;
        int gridOffsetY = ((int) panY) % gridSpacing;

        for (int x = gridOffsetX; x < width; x += gridSpacing) {
            graphics.fill(x, topY + 46, x + 1, bottomY - 38, 0x1800E5FF);
        }
        for (int y = topY + 46 + gridOffsetY; y < bottomY - 38; y += gridSpacing) {
            graphics.fill(20, y, width - 20, y + 1, 0x1800E5FF);
        }

        Map<BlockPos, FacilityNetworkManager.RoomNode> rooms = FacilityNetworkManager.getRooms();
        focusedRoomPos = null;
        double closestDistSq = 36.0 * 36.0;

        int activeCount = 0;
        for (Map.Entry<BlockPos, FacilityNetworkManager.RoomNode> entry : rooms.entrySet()) {
            BlockPos roomPos = entry.getKey();
            FacilityNetworkManager.RoomNode room = entry.getValue();

            if (room.zone != selectedZone) continue;
            activeCount++;

            int nodeW = 54;
            int nodeH = 38;
            int nodeX = cx + (int) panX + (room.gridX * 72) - (nodeW / 2);
            int nodeY = cy + (int) panY + (room.gridY * 54) - (nodeH / 2);

            int cellCenterX = nodeX + (nodeW / 2);
            int cellCenterY = nodeY + (nodeH / 2);

            // Check if focused by center reticle or hovered by mouse cursor
            double distSq = (cellCenterX - cx) * (cellCenterX - cx) + (cellCenterY - cy) * (cellCenterY - cy);
            boolean isMouseHover = (mouseX >= nodeX && mouseX <= nodeX + nodeW && mouseY >= nodeY && mouseY <= nodeY + nodeH);
            boolean isReticleFocus = distSq <= closestDistSq;

            boolean isFocused = isMouseHover || isReticleFocus;
            if (isFocused) {
                closestDistSq = distSq;
                focusedRoomPos = roomPos;
            }

            boolean isCurrent = roomPos.equals(ClientCameraHandler.activeCameraPos);
            boolean isTransition = (room.roomType == FacilityMapNodeBlockEntity.RoomType.ELEVATOR ||
                                    room.roomType == FacilityMapNodeBlockEntity.RoomType.CHECKPOINT);

            renderGeometricCell(graphics, font, nodeX, nodeY, nodeW, nodeH, room.roomName, isCurrent, isTransition, isFocused, room.hasGenerator);
        }

        if (activeCount == 0) {
            String emptyMsg = "NO SURVEILLANCE CAMERAS IN " + selectedZone.getCode() + " // SELECT ANOTHER ZONE (1-4)";
            graphics.drawString(font, emptyMsg, cx - (font.width(emptyMsg) / 2), cy - 6, 0xAA80DEEA, false);
        }
    }

    private void renderGeometricCell(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                     String roomName, boolean isCurrent, boolean isTransition, boolean isFocused, boolean hasGenerator) {
        int bgCol = isCurrent ? 0x8800E5FF : (isFocused ? 0xBB0D2636 : 0xDD061018);
        int frameCol = isFocused ? 0xFFFFFFFF : (isCurrent ? 0xFFE0F7FA : 0xAA00E5FF);

        graphics.fill(x, y, x + w, y + h, bgCol);

        // Vector outline
        graphics.fill(x, y, x + w, y + 1, frameCol);
        graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
        graphics.fill(x, y, x + 1, y + h, frameCol);
        graphics.fill(x + w - 1, y, x + w, y + h, frameCol);

        int midX = x + (w / 2);
        int midY = y + (h / 2);

        // Room Name / Code
        if (roomName != null && !roomName.isEmpty()) {
            String shortName = roomName.length() > 8 ? roomName.substring(0, 8) : roomName;
            int tw = font.width(shortName);
            graphics.drawString(font, shortName, midX - (tw / 2), y + 4, isFocused ? 0xFFFFFFFF : 0xFFE0F7FA, false);
        }

        // Geometric Status Icons
        if (isCurrent) {
            // Active Camera Blinking Dot
            graphics.fill(midX - 4, midY + 4, midX + 4, midY + 12, 0xFF00E5FF);
            graphics.fill(midX - 2, midY + 6, midX + 2, midY + 10, 0xFF040A10);
        } else if (isTransition) {
            // Elevator / Airlock Symbol
            graphics.fill(midX - 8, midY + 4, midX - 6, midY + 12, frameCol);
            graphics.fill(midX + 6, midY + 4, midX + 8, midY + 12, frameCol);
            graphics.fill(midX - 4, midY + 7, midX + 4, midY + 9, frameCol);
        } else {
            // Standard Camera Point
            graphics.fill(midX - 2, midY + 6, midX + 2, midY + 10, frameCol);
        }

        if (hasGenerator) {
            graphics.drawString(font, "⚡", x + w - 10, y + 2, 0xFFF39C12, false);
        }

        if (isFocused) {
            // Glowing Focus Diamond Brackets
            graphics.fill(x - 4, y - 4, x, y - 3, 0xFFFFFFFF);
            graphics.fill(x - 4, y - 4, x - 3, y, 0xFFFFFFFF);

            graphics.fill(x + w, y - 4, x + w + 4, y - 3, 0xFFFFFFFF);
            graphics.fill(x + w + 3, y - 4, x + w + 4, y, 0xFFFFFFFF);

            graphics.fill(x - 4, y + h + 3, x, y + h + 4, 0xFFFFFFFF);
            graphics.fill(x - 4, y + h, x - 3, y + h + 4, 0xFFFFFFFF);

            graphics.fill(x + w, y + h + 3, x + w + 4, y + h + 4, 0xFFFFFFFF);
            graphics.fill(x + w + 3, y + h, x + w + 4, y + h + 4, 0xFFFFFFFF);
        }
    }

    private void renderBottomStatusBar(GuiGraphics graphics, Font font, int bottomY) {
        int by = bottomY - 32;
        graphics.fill(20, by, width - 20, by + 26, 0xCC061018);
        graphics.fill(20, by, width - 20, by + 2, 0xFF00E5FF);

        // Breach Scanner Status
        boolean scannerOn = ClientCameraHandler.breachScannerActive;
        String scannerTxt = scannerOn ? "BREACH SCANNER: [ONLINE (-50% AP REGEN)]" :
                (ClientCameraHandler.tier >= 4 ? "BREACH SCANNER: [STANDBY] - [SPACE]" : "BREACH SCANNER: [TIER 4 LOCKED]");

        graphics.drawString(font, scannerTxt, 30, by + 8, 0xFFE0F7FA, true);

        String helpTxt = "DRAG: PAN  |  [1-4]: ZONES  |  [CLICK / SPACE]: JUMP CAMERA  |  [TAB / ESC]: EXIT";
        graphics.drawString(font, helpTxt, width - font.width(helpTxt) - 30, by + 8, 0xAA80DEEA, false);
    }
}
