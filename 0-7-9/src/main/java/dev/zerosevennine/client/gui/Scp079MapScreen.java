package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.client.handler.ClientCameraHandler;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.RoomShape;
import dev.zerosevennine.network.C2SSwitchCameraPacket;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class Scp079MapScreen extends Screen {
    private static final ResourceLocation SCANLINES = new ResourceLocation("zero_seven_nine", "textures/gui/scanlines.png");

    private double panX = 0;
    private double panY = 0;
    private float animProgress = 0.0f;
    private BlockPos focusedRoomPos = null;

    private boolean tabHasBeenReleased = false;
    private double lastRawMouseX = -1;
    private double lastRawMouseY = -1;

    public Scp079MapScreen() {
        super(Component.literal("Tactical Facility Map"));
    }

    @Override
    protected void init() {
        super.init();
        animProgress = 0.0f;
        focusedRoomPos = null;
        tabHasBeenReleased = false;
        lastRawMouseX = -1;
        lastRawMouseY = -1;

        if (this.minecraft != null) {
            this.minecraft.mouseHandler.grabMouse();
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (this.minecraft != null) {
            this.minecraft.mouseHandler.releaseMouse();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private boolean isTabKey(int keyCode, int scanCode) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            return true;
        }
        if (this.minecraft != null && this.minecraft.options != null) {
            KeyMapping playerListKey = this.minecraft.options.keyPlayerList;
            if (playerListKey != null && playerListKey.matches(keyCode, scanCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (isTabKey(keyCode, scanCode)) {
            tabHasBeenReleased = true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isTabKey(keyCode, scanCode)) {
            if (tabHasBeenReleased) {
                this.onClose();
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            if (focusedRoomPos != null) {
                switchCameraToRoom(focusedRoomPos);
                this.onClose();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (focusedRoomPos != null) {
                switchCameraToRoom(focusedRoomPos);
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void switchCameraToRoom(BlockPos roomPos) {
        BlockPos camPos = findCameraForRoom(roomPos);
        if (camPos != null) {
            ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(camPos));
        }
    }

    private BlockPos findCameraForRoom(BlockPos roomPos) {
        for (Map.Entry<BlockPos, CameraBlockEntity> entry : FacilityNetworkManager.getCameras().entrySet()) {
            if (entry.getKey().distSqr(roomPos) <= 16 * 16) {
                return entry.getKey();
            }
        }
        if (FacilityNetworkManager.getCameras().containsKey(roomPos)) {
            return roomPos;
        }
        return roomPos;
    }

    private Map<BlockPos, FacilityNetworkManager.RoomNode> getEffectiveRooms() {
        Map<BlockPos, FacilityNetworkManager.RoomNode> rooms = new HashMap<>(FacilityNetworkManager.getRooms());

        if (ClientCameraHandler.activeCameraPos != null && !ClientCameraHandler.activeCameraPos.equals(BlockPos.ZERO)) {
            BlockPos currentPos = ClientCameraHandler.activeCameraPos;
            if (!rooms.containsKey(currentPos)) {
                String camName = "CAM-" + currentPos.getX() + ":" + currentPos.getZ();
                FacilityZone zone = FacilityZone.HCZ;
                if (minecraft != null && minecraft.level != null) {
                    if (minecraft.level.getBlockEntity(currentPos) instanceof CameraBlockEntity cbe) {
                        camName = cbe.getCameraName();
                        zone = cbe.getZone();
                    }
                }
                rooms.put(currentPos, new FacilityNetworkManager.RoomNode(
                        currentPos, camName, zone, FacilityMapNodeBlockEntity.RoomType.STANDARD, RoomShape.SQUARE, 0, zone, 0, 0
                ));
            }

            if (minecraft != null && minecraft.level != null) {
                if (minecraft.level.getBlockEntity(currentPos) instanceof CameraBlockEntity cbe) {
                    for (Map.Entry<Direction, BlockPos> neighbor : cbe.getWasdNeighbors().entrySet()) {
                        BlockPos nPos = neighbor.getValue();
                        if (!rooms.containsKey(nPos)) {
                            int gx = switch (neighbor.getKey()) {
                                case EAST -> 1;
                                case WEST -> -1;
                                default -> 0;
                            };
                            int gy = switch (neighbor.getKey()) {
                                case SOUTH -> 1;
                                case NORTH -> -1;
                                default -> 0;
                            };
                            rooms.put(nPos, new FacilityNetworkManager.RoomNode(
                                    nPos, "Neighbor Room", cbe.getZone(), FacilityMapNodeBlockEntity.RoomType.STANDARD, RoomShape.SQUARE, 0, cbe.getZone(), gx, gy
                            ));
                        }
                    }
                }
            }
        }

        return rooms;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft != null) {
            if (!this.minecraft.mouseHandler.isMouseGrabbed()) {
                this.minecraft.mouseHandler.grabMouse();
            }

            double curX = this.minecraft.mouseHandler.xpos();
            double curY = this.minecraft.mouseHandler.ypos();

            if (lastRawMouseX >= 0 && lastRawMouseY >= 0) {
                double dx = curX - lastRawMouseX;
                double dy = curY - lastRawMouseY;
                double sens = this.minecraft.options.sensitivity().get() * 0.8 + 0.4;
                panX -= dx * sens;
                panY -= dy * sens;
            }
            lastRawMouseX = curX;
            lastRawMouseY = curY;
        }

        animProgress = Math.min(1.0f, animProgress + 0.25f);

        int cx = width / 2;
        int cy = height / 2;

        graphics.fill(0, 0, width, height, 0xFF040A10);

        int topY = 0;
        int bottomY = height;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0f, 0.9f, 1.0f, 0.08f);
        graphics.blit(SCANLINES, 0, 0, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Font font = this.font;

        renderHeader(graphics, font, topY);
        renderVectorMap(graphics, font, cx, cy, topY, bottomY);
        renderCenterFixedReticle(graphics, font, cx, cy);
        renderBottomStatusBar(graphics, font, bottomY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics, Font font, int topY) {
        int barH = 34;
        graphics.fill(20, topY + 8, width - 20, topY + barH, 0xCC061018);
        graphics.fill(20, topY + barH - 2, width - 20, topY + barH, 0xFF00E5FF);

        String title = "FACILITY SCHEMATIC OVERVIEW // SCP-079 INTERFACE";
        graphics.drawString(font, title, 30, topY + 16, 0xFF00E5FF, true);

        String zoneCode = "HCZ";
        if (minecraft != null && minecraft.level != null && ClientCameraHandler.activeCameraPos != null) {
            if (minecraft.level.getBlockEntity(ClientCameraHandler.activeCameraPos) instanceof CameraBlockEntity cam) {
                zoneCode = cam.getZone().getCode();
            }
        }
        String activeZone = "ZONE: " + zoneCode;
        graphics.drawString(font, activeZone, width - font.width(activeZone) - 30, topY + 16, 0xFFE0F7FA, false);
    }

    private void renderCenterFixedReticle(GuiGraphics graphics, Font font, int cx, int cy) {
        int r = 10;
        int col = (focusedRoomPos != null) ? 0xFF00E5FF : 0xAA80DEEA;

        graphics.fill(cx - r, cy, cx - 3, cy + 1, col);
        graphics.fill(cx + 4, cy, cx + r + 1, cy + 1, col);
        graphics.fill(cx, cy - r, cx + 1, cy - 3, col);
        graphics.fill(cx, cy + 4, cx + 1, cy + r + 1, col);

        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, col);

        if (focusedRoomPos != null) {
            String prompt = "[LMB / SPACE] SWITCH CAMERA";
            graphics.drawString(font, prompt, cx - (font.width(prompt) / 2), cy + 24, 0xFFE0F7FA, true);
        }
    }

    private void renderVectorMap(GuiGraphics graphics, Font font, int cx, int cy, int topY, int bottomY) {
        int gridSpacing = 48;
        int gridOffsetX = ((int) panX) % gridSpacing;
        int gridOffsetY = ((int) panY) % gridSpacing;

        for (int x = gridOffsetX; x < width; x += gridSpacing) {
            graphics.fill(x, topY + 44, x + 1, bottomY - 42, 0x1800E5FF);
        }
        for (int y = topY + 44 + gridOffsetY; y < bottomY - 42; y += gridSpacing) {
            graphics.fill(20, y, width - 20, y + 1, 0x1800E5FF);
        }

        Map<BlockPos, FacilityNetworkManager.RoomNode> rooms = getEffectiveRooms();
        focusedRoomPos = null;
        double closestDistSq = 42.0 * 42.0;

        for (Map.Entry<BlockPos, FacilityNetworkManager.RoomNode> entry : rooms.entrySet()) {
            BlockPos roomPos = entry.getKey();
            FacilityNetworkManager.RoomNode room = entry.getValue();

            int nodeW = 54;
            int nodeH = 38;
            int nodeX = cx + (int) panX + (room.gridX * 72) - (nodeW / 2);
            int nodeY = cy + (int) panY + (room.gridY * 54) - (nodeH / 2);

            int cellCenterX = nodeX + (nodeW / 2);
            int cellCenterY = nodeY + (nodeH / 2);

            double distSq = (cellCenterX - cx) * (cellCenterX - cx) + (cellCenterY - cy) * (cellCenterY - cy);

            if (distSq <= closestDistSq) {
                closestDistSq = distSq;
                focusedRoomPos = roomPos;
            }

            boolean isCurrent = roomPos.equals(ClientCameraHandler.activeCameraPos);
            boolean isTransition = (room.roomType == FacilityMapNodeBlockEntity.RoomType.ELEVATOR ||
                                    room.roomType == FacilityMapNodeBlockEntity.RoomType.CHECKPOINT);
            boolean isFocused = roomPos.equals(focusedRoomPos);

            renderGeometricCell(graphics, font, nodeX, nodeY, nodeW, nodeH, room, isCurrent, isTransition, isFocused, room.hasGenerator, room.isGeneratorBooting);
        }
    }

    private void renderGeometricCell(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                     FacilityNetworkManager.RoomNode room,
                                     boolean isCurrent, boolean isTransition, boolean isFocused,
                                     boolean hasGenerator, boolean isGeneratorBooting) {
        RoomShape shape = room.roomShape != null ? room.roomShape : RoomShape.SQUARE;
        int rot = Math.floorMod(room.rotation, 4);

        int bgCol = isCurrent ? 0x8800E5FF : (isFocused ? 0xBB0D2636 : 0xDD061018);
        int frameCol = isFocused ? 0xFFFFFFFF : (isCurrent ? 0xFFE0F7FA : 0xAA00E5FF);

        if (isGeneratorBooting) {
            long time = System.currentTimeMillis() / 250;
            int alertCol = (time % 2 == 0) ? 0xFFFF3300 : 0xFFFFAA00;
            bgCol = 0x88FF3300;
            frameCol = alertCol;
        }

        int midX = x + (w / 2);
        int midY = y + (h / 2);
        int armW = 18;

        switch (shape) {
            case T_SHAPE -> {
                if (rot == 0) { // Stem South
                    graphics.fill(x, y, x + w, y + armW, bgCol);
                    graphics.fill(midX - armW / 2, y + armW, midX + armW / 2, y + h, bgCol);

                    graphics.fill(x, y, x + w, y + 1, frameCol);
                    graphics.fill(x, y, x + 1, y + armW, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + armW, frameCol);
                    graphics.fill(x, y + armW - 1, midX - armW / 2, y + armW, frameCol);
                    graphics.fill(midX + armW / 2, y + armW - 1, x + w, y + armW, frameCol);
                    graphics.fill(midX - armW / 2, y + armW, midX - armW / 2 + 1, y + h, frameCol);
                    graphics.fill(midX + armW / 2 - 1, y + armW, midX + armW / 2, y + h, frameCol);
                    graphics.fill(midX - armW / 2, y + h - 1, midX + armW / 2, y + h, frameCol);
                } else if (rot == 1) { // Stem West
                    graphics.fill(x + w - armW, y, x + w, y + h, bgCol);
                    graphics.fill(x, midY - armW / 2, x + w - armW, midY + armW / 2, bgCol);

                    graphics.fill(x + w - 1, y, x + w, y + h, frameCol);
                    graphics.fill(x + w - armW, y, x + w, y + 1, frameCol);
                    graphics.fill(x + w - armW, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x + w - armW, y, x + w - armW + 1, midY - armW / 2, frameCol);
                    graphics.fill(x + w - armW, midY + armW / 2, x + w - armW + 1, y + h, frameCol);
                    graphics.fill(x, midY - armW / 2, x + w - armW, midY - armW / 2 + 1, frameCol);
                    graphics.fill(x, midY + armW / 2 - 1, x + w - armW, midY + armW / 2, frameCol);
                    graphics.fill(x, midY - armW / 2, x + 1, midY + armW / 2, frameCol);
                } else if (rot == 2) { // Stem North
                    graphics.fill(x, y + h - armW, x + w, y + h, bgCol);
                    graphics.fill(midX - armW / 2, y, midX + armW / 2, y + h - armW, bgCol);

                    graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x, y + h - armW, x + 1, y + h, frameCol);
                    graphics.fill(x + w - 1, y + h - armW, x + w, y + h, frameCol);
                    graphics.fill(x, y + h - armW, midX - armW / 2, y + h - armW + 1, frameCol);
                    graphics.fill(midX + armW / 2, y + h - armW, x + w, y + h - armW + 1, frameCol);
                    graphics.fill(midX - armW / 2, y, midX - armW / 2 + 1, y + h - armW, frameCol);
                    graphics.fill(midX + armW / 2 - 1, y, midX + armW / 2, y + h - armW, frameCol);
                    graphics.fill(midX - armW / 2, y, midX + armW / 2, y + 1, frameCol);
                } else { // Stem East
                    graphics.fill(x, y, x + armW, y + h, bgCol);
                    graphics.fill(x + armW, midY - armW / 2, x + w, midY + armW / 2, bgCol);

                    graphics.fill(x, y, x + 1, y + h, frameCol);
                    graphics.fill(x, y, x + armW, y + 1, frameCol);
                    graphics.fill(x, y + h - 1, x + armW, y + h, frameCol);
                    graphics.fill(x + armW - 1, y, x + armW, midY - armW / 2, frameCol);
                    graphics.fill(x + armW - 1, midY + armW / 2, x + armW, y + h, frameCol);
                    graphics.fill(x + armW, midY - armW / 2, x + w, midY - armW / 2 + 1, frameCol);
                    graphics.fill(x + armW, midY + armW / 2 - 1, x + w, midY + armW / 2, frameCol);
                    graphics.fill(x + w - 1, midY - armW / 2, x + w, midY + armW / 2, frameCol);
                }
            }
            case CORNER_L -> {
                if (rot == 0) {
                    graphics.fill(x, y, x + w, y + armW, bgCol);
                    graphics.fill(x + w - armW, y + armW, x + w, y + h, bgCol);

                    graphics.fill(x, y, x + w, y + 1, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + h, frameCol);
                    graphics.fill(x, y, x + 1, y + armW, frameCol);
                    graphics.fill(x, y + armW - 1, x + w - armW, y + armW, frameCol);
                    graphics.fill(x + w - armW, y + armW, x + w - armW + 1, y + h, frameCol);
                    graphics.fill(x + w - armW, y + h - 1, x + w, y + h, frameCol);
                } else if (rot == 1) {
                    graphics.fill(x, y + h - armW, x + w, y + h, bgCol);
                    graphics.fill(x + w - armW, y, x + w, y + h - armW, bgCol);

                    graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + h, frameCol);
                    graphics.fill(x, y + h - armW, x + 1, y + h, frameCol);
                    graphics.fill(x, y + h - armW, x + w - armW, y + h - armW + 1, frameCol);
                    graphics.fill(x + w - armW, y, x + w - armW + 1, y + h - armW, frameCol);
                    graphics.fill(x + w - armW, y, x + w, y + 1, frameCol);
                } else if (rot == 2) {
                    graphics.fill(x, y + h - armW, x + w, y + h, bgCol);
                    graphics.fill(x, y, x + armW, y + h - armW, bgCol);

                    graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x, y, x + 1, y + h, frameCol);
                    graphics.fill(x + w - 1, y + h - armW, x + w, y + h, frameCol);
                    graphics.fill(x + armW, y + h - armW, x + w, y + h - armW + 1, frameCol);
                    graphics.fill(x + armW - 1, y, x + armW, y + h - armW, frameCol);
                    graphics.fill(x, y, x + armW, y + 1, frameCol);
                } else {
                    graphics.fill(x, y, x + w, y + armW, bgCol);
                    graphics.fill(x, y + armW, x + armW, y + h, bgCol);

                    graphics.fill(x, y, x + w, y + 1, frameCol);
                    graphics.fill(x, y, x + 1, y + h, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + armW, frameCol);
                    graphics.fill(x + armW, y + armW - 1, x + w, y + armW, frameCol);
                    graphics.fill(x + armW - 1, y + armW, x + armW, y + h, frameCol);
                    graphics.fill(x, y + h - 1, x + armW, y + h, frameCol);
                }
            }
            case STRAIGHT -> {
                if (rot == 0 || rot == 2) {
                    int cx1 = midX - armW / 2;
                    int cx2 = midX + armW / 2;
                    graphics.fill(cx1, y, cx2, y + h, bgCol);
                    graphics.fill(cx1, y, cx1 + 1, y + h, frameCol);
                    graphics.fill(cx2 - 1, y, cx2, y + h, frameCol);
                    graphics.fill(cx1, y, cx2, y + 1, frameCol);
                    graphics.fill(cx1, y + h - 1, cx2, y + h, frameCol);
                } else {
                    int cy1 = midY - armW / 2;
                    int cy2 = midY + armW / 2;
                    graphics.fill(x, cy1, x + w, cy2, bgCol);
                    graphics.fill(x, cy1, x + w, cy1 + 1, frameCol);
                    graphics.fill(x, cy2 - 1, x + w, cy2, frameCol);
                    graphics.fill(x, cy1, x + 1, cy2, frameCol);
                    graphics.fill(x + w - 1, cy1, x + w, cy2, frameCol);
                }
            }
            case CROSS_X -> {
                graphics.fill(x, midY - armW / 2, x + w, midY + armW / 2, bgCol);
                graphics.fill(midX - armW / 2, y, midX + armW / 2, y + h, bgCol);

                int x1 = midX - armW / 2;
                int x2 = midX + armW / 2;
                int y1 = midY - armW / 2;
                int y2 = midY + armW / 2;

                graphics.fill(x1, y, x2, y + 1, frameCol);
                graphics.fill(x1, y + h - 1, x2, y + h, frameCol);
                graphics.fill(x, y1, x + 1, y2, frameCol);
                graphics.fill(x + w - 1, y1, x + w, y2, frameCol);

                graphics.fill(x, y1, x1, y1 + 1, frameCol);
                graphics.fill(x1, y, x1 + 1, y1, frameCol);

                graphics.fill(x2, y1, x + w, y1 + 1, frameCol);
                graphics.fill(x2 - 1, y, x2, y1, frameCol);

                graphics.fill(x, y2 - 1, x1, y2, frameCol);
                graphics.fill(x1, y2, x1 + 1, y + h, frameCol);

                graphics.fill(x2, y2 - 1, x + w, y2, frameCol);
                graphics.fill(x2 - 1, y2, x2, y + h, frameCol);
            }
            case END_ROOM -> {
                int rx = x + 4;
                int ry = y + 4;
                int rw = w - 8;
                int rh = h - 8;
                graphics.fill(rx, ry, rx + rw, ry + rh, bgCol);
                graphics.fill(rx, ry, rx + rw, ry + 1, frameCol);
                graphics.fill(rx, ry + rh - 1, rx + rw, ry + rh, frameCol);
                graphics.fill(rx, ry, rx + 1, ry + rh, frameCol);
                graphics.fill(rx + rw - 1, ry, rx + rw, ry + rh, frameCol);

                if (rot == 0) {
                    graphics.fill(midX - 4, ry + rh - 1, midX + 4, y + h, bgCol);
                    graphics.fill(midX - 4, ry + rh, midX - 3, y + h, frameCol);
                    graphics.fill(midX + 3, ry + rh, midX + 4, y + h, frameCol);
                } else if (rot == 1) {
                    graphics.fill(x, midY - 4, rx + 1, midY + 4, bgCol);
                    graphics.fill(x, midY - 4, rx, midY - 3, frameCol);
                    graphics.fill(x, midY + 3, rx, midY + 4, frameCol);
                } else if (rot == 2) {
                    graphics.fill(midX - 4, y, midX + 4, ry + 1, bgCol);
                    graphics.fill(midX - 4, y, midX - 3, ry, frameCol);
                    graphics.fill(midX + 3, y, midX + 4, ry, frameCol);
                } else {
                    graphics.fill(rx + rw - 1, midY - 4, x + w, midY + 4, bgCol);
                    graphics.fill(rx + rw, midY - 4, x + w, midY - 3, frameCol);
                    graphics.fill(rx + rw, midY + 3, x + w, midY + 4, frameCol);
                }
            }
            default -> { // SQUARE, ELEVATOR, CHECKPOINT
                graphics.fill(x, y, x + w, y + h, bgCol);
                graphics.fill(x, y, x + w, y + 1, frameCol);
                graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                graphics.fill(x, y + 1, x + 1, y + h, frameCol);
                graphics.fill(x + w - 1, y, x + w, y + h, frameCol);
            }
        }

        // Center indicator
        if (isCurrent) {
            graphics.fill(midX - 4, midY - 4, midX + 4, midY + 4, 0xFF00E5FF);
            graphics.fill(midX - 2, midY - 2, midX + 2, midY + 2, 0xFF040A10);
        } else if (isTransition) {
            graphics.fill(midX - 8, midY - 6, midX - 6, midY + 6, frameCol);
            graphics.fill(midX + 6, midY - 6, midX + 8, midY + 6, frameCol);
            graphics.fill(midX - 4, midY - 1, midX + 4, midY + 1, frameCol);
        } else {
            graphics.fill(midX - 2, midY - 2, midX + 2, midY + 2, frameCol);
        }

        // Focused reticle brackets
        if (isFocused) {
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
        int by = bottomY - 34;
        graphics.fill(20, by, width - 20, by + 26, 0xCC061018);
        graphics.fill(20, by, width - 20, by + 2, 0xFF00E5FF);

        boolean scannerOn = ClientCameraHandler.breachScannerActive;
        String scannerTxt = scannerOn ? "BREACH SCANNER: [ONLINE (-50% AP REGEN)]" :
                (ClientCameraHandler.tier >= 4 ? "BREACH SCANNER: [STANDBY] - [SPACE]" : "BREACH SCANNER: [TIER 4 LOCKED]");

        graphics.drawString(font, scannerTxt, 30, by + 8, 0xFFE0F7FA, true);

        String helpTxt = "MOUSE MOVE: PAN MAP  |  [LMB / SPACE]: SWITCH CAMERA  |  [TAB / ESC]: EXIT";
        graphics.drawString(font, helpTxt, width - font.width(helpTxt) - 30, by + 8, 0xAA80DEEA, false);
    }
}
