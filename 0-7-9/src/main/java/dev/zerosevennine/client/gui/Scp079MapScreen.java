package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.ZeroSevenNine;
<<<<<<< HEAD
import dev.zerosevennine.blockentity.CameraBlockEntity;
=======
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.client.handler.ClientCameraHandler;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.network.C2SInteractDevicePacket;
import dev.zerosevennine.network.C2SRequestMapSyncPacket;
import dev.zerosevennine.network.C2SSwitchCameraPacket;
import dev.zerosevennine.network.ModNetwork;
<<<<<<< HEAD
=======
import net.minecraft.client.Minecraft;
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
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

    private BlockPos focusedRoomPos = null;

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

<<<<<<< HEAD
=======
        if (this.minecraft != null) {
            this.minecraft.mouseHandler.grabMouse();
        }

>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
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
<<<<<<< HEAD
=======
        if (this.minecraft != null) {
            this.minecraft.mouseHandler.grabMouse();
        }
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
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
<<<<<<< HEAD
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        panX += dragX;
        panY += dragY;
=======
    public void mouseMoved(double mouseX, double mouseY) {
        if (lastMouseX >= 0 && lastMouseY >= 0) {
            double dx = mouseX - lastMouseX;
            double dy = mouseY - lastMouseY;
            panX -= dx * 1.5;
            panY -= dy * 1.5;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        panX -= dragX * 1.5;
        panY -= dragY * 1.5;
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
<<<<<<< HEAD
            int cx = width / 2;
            int cy = height / 2;
            Map<BlockPos, FacilityNetworkManager.RoomNode> rooms = FacilityNetworkManager.getRooms();

            // 1. Direct click on any room cell on the tactical vector map
            for (Map.Entry<BlockPos, FacilityNetworkManager.RoomNode> entry : rooms.entrySet()) {
                FacilityNetworkManager.RoomNode room = entry.getValue();

                int nodeW = 50;
                int nodeH = 36;
                int nodeX = cx + (int) panX + (room.gridX * 68) - (nodeW / 2);
                int nodeY = cy + (int) panY + (room.gridY * 50) - (nodeH / 2);

                if (mouseX >= nodeX && mouseX <= nodeX + nodeW && mouseY >= nodeY && mouseY <= nodeY + nodeH) {
                    ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(entry.getKey()));
                    this.onClose();
                    return true;
                }
            }

            // 2. Click with room centered under reticle
=======
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
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
        renderHeader(graphics, font, topY);

        // Render Vector Schematic Grid with Center Reticle & Panning
<<<<<<< HEAD
        renderVectorMap(graphics, font, mouseX, mouseY, cx, cy, topY, bottomY);
=======
        renderVectorMap(graphics, font, cx, cy, topY, bottomY);
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)

        // Fixed Reticle Locked at Screen Center
        renderCenterFixedReticle(graphics, font, cx, cy);

        // Bottom Status Bar
        renderBottomStatusBar(graphics, font, bottomY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics, Font font, int topY) {
        int hY = topY + 10;
        int hH = 28;

        graphics.fill(20, hY, width - 20, hY + hH, 0xCC061018);
        graphics.fill(20, hY, width - 20, hY + 2, 0xFF00E5FF);

        String title = "SITE-02 TACTICAL SCHEMATIC // TIER " + ClientCameraHandler.tier;
        graphics.drawString(font, title, 30, hY + 10, 0xFFE0F7FA, true);

        // AP Counter
        String apStatus = String.format("AP: %.0f/%.0f (+%.1f/s)", ClientCameraHandler.ap, ClientCameraHandler.maxAp, ClientCameraHandler.apRegen);
        graphics.drawString(font, apStatus, width - font.width(apStatus) - 30, hY + 10, 0xAA80DEEA, false);
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

<<<<<<< HEAD
    private void renderVectorMap(GuiGraphics graphics, Font font, int mouseX, int mouseY, int cx, int cy, int topY, int bottomY) {
=======
    private void renderVectorMap(GuiGraphics graphics, Font font, int cx, int cy, int topY, int bottomY) {
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
        // Background coordinate grid lines
        int gridSpacing = 48;
        int gridOffsetX = ((int) panX) % gridSpacing;
        int gridOffsetY = ((int) panY) % gridSpacing;

        for (int x = gridOffsetX; x < width; x += gridSpacing) {
            graphics.fill(x, topY + 42, x + 1, bottomY - 38, 0x1800E5FF);
        }
        for (int y = topY + 42 + gridOffsetY; y < bottomY - 38; y += gridSpacing) {
            graphics.fill(20, y, width - 20, y + 1, 0x1800E5FF);
        }

        Map<BlockPos, FacilityNetworkManager.RoomNode> rooms = FacilityNetworkManager.getRooms();
        focusedRoomPos = null;
<<<<<<< HEAD
        double closestDistSq = 36.0 * 36.0;
=======
        double closestDistSq = 40.0 * 40.0;
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)

        for (Map.Entry<BlockPos, FacilityNetworkManager.RoomNode> entry : rooms.entrySet()) {
            BlockPos roomPos = entry.getKey();
            FacilityNetworkManager.RoomNode room = entry.getValue();

            int nodeW = 50;
            int nodeH = 36;
            int nodeX = cx + (int) panX + (room.gridX * 68) - (nodeW / 2);
            int nodeY = cy + (int) panY + (room.gridY * 50) - (nodeH / 2);

            int cellCenterX = nodeX + (nodeW / 2);
            int cellCenterY = nodeY + (nodeH / 2);

<<<<<<< HEAD
            // Check if focused by center reticle or hovered by mouse cursor
            double distSq = (cellCenterX - cx) * (cellCenterX - cx) + (cellCenterY - cy) * (cellCenterY - cy);
            boolean isMouseHover = (mouseX >= nodeX && mouseX <= nodeX + nodeW && mouseY >= nodeY && mouseY <= nodeY + nodeH);
            boolean isReticleFocus = distSq <= closestDistSq;

            boolean isFocused = isMouseHover || isReticleFocus;
            if (isFocused) {
=======
            // Check if focused by center reticle
            double distSq = (cellCenterX - cx) * (cellCenterX - cx) + (cellCenterY - cy) * (cellCenterY - cy);
            if (distSq <= closestDistSq) {
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
                closestDistSq = distSq;
                focusedRoomPos = roomPos;
            }

            boolean isCurrent = roomPos.equals(ClientCameraHandler.activeCameraPos);
            boolean isTransition = (room.roomType == FacilityMapNodeBlockEntity.RoomType.ELEVATOR ||
                                    room.roomType == FacilityMapNodeBlockEntity.RoomType.CHECKPOINT);
<<<<<<< HEAD
=======
            boolean isFocused = roomPos.equals(focusedRoomPos);
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)

            renderGeometricCell(graphics, font, nodeX, nodeY, nodeW, nodeH, isCurrent, isTransition, isFocused, room.hasGenerator, room.isGeneratorBooting);
        }
    }

    private void renderGeometricCell(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                     boolean isCurrent, boolean isTransition, boolean isFocused,
                                     boolean hasGenerator, boolean isGeneratorBooting) {
        int bgCol = isCurrent ? 0x8800E5FF : (isFocused ? 0xBB0D2636 : 0xDD061018);
        int frameCol = isFocused ? 0xFFFFFFFF : (isCurrent ? 0xFFE0F7FA : 0xAA00E5FF);

        if (isGeneratorBooting) {
            long time = System.currentTimeMillis() / 250;
            int alertCol = (time % 2 == 0) ? 0xFFFF3300 : 0xFFFFAA00;
            bgCol = 0x88FF3300;
            frameCol = alertCol;
        }

        graphics.fill(x, y, x + w, y + h, bgCol);

        // Vector outline
        graphics.fill(x, y, x + w, y + 1, frameCol);
        graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
        graphics.fill(x, y, x + 1, y + h, frameCol);
        graphics.fill(x + w - 1, y, x + w, y + h, frameCol);

        int midX = x + (w / 2);
        int midY = y + (h / 2);

        // Pure Geometric Symbols (NO text names, NO "FREE" labels, NO generator icons)
        if (isCurrent) {
<<<<<<< HEAD
            // Active Camera Blinking Dot
            graphics.fill(midX - 4, midY - 4, midX + 4, midY + 4, 0xFF00E5FF);
            graphics.fill(midX - 2, midY - 2, midX + 2, midY + 2, 0xFF040A10);
        } else if (isTransition) {
            // Elevator / Airlock Symbol
=======
            graphics.fill(midX - 4, midY - 4, midX + 4, midY + 4, 0xFF00E5FF);
            graphics.fill(midX - 2, midY - 2, midX + 2, midY + 2, 0xFF040A10);
        } else if (isTransition) {
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
            graphics.fill(midX - 8, midY - 6, midX - 6, midY + 6, frameCol);
            graphics.fill(midX + 6, midY - 6, midX + 8, midY + 6, frameCol);
            graphics.fill(midX - 4, midY - 1, midX + 4, midY + 1, frameCol);
        } else {
<<<<<<< HEAD
            // Standard Camera Point
=======
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
            graphics.fill(midX - 2, midY - 2, midX + 2, midY + 2, frameCol);
        }

        if (isFocused) {
<<<<<<< HEAD
            // Glowing Focus Diamond Brackets
=======
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
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

<<<<<<< HEAD
        String helpTxt = "DRAG: PAN MAP  |  [LMB / SPACE]: JUMP TO RETICLE ROOM  |  [TAB / ESC]: EXIT";
=======
        String helpTxt = "MOUSE MOVE: PAN MAP  |  [LMB / SPACE]: JUMP TO RETICLE ROOM  |  [TAB / ESC]: EXIT";
>>>>>>> 9568ffc (fix(0-7-9): disable auto-discovery, clean HUD markers to hover-only, lock cursor in TAB map, and fix door toggle/lock interactions)
        graphics.drawString(font, helpTxt, width - font.width(helpTxt) - 30, by + 8, 0xAA80DEEA, false);
    }
}
