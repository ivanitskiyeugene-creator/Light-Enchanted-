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
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scp079MapScreen extends Screen {
    private static final ResourceLocation SCANLINES = new ResourceLocation("zero_seven_nine", "textures/gui/scanlines.png");

    private double panX = 0;
    private double panY = 0;
    private BlockPos focusedRoomPos = null;

    private long openTimeMs = 0;
    private double lastMouseX = -1;
    private double lastMouseY = -1;

    public Scp079MapScreen() {
        super(Component.literal("Tactical Facility Map"));
    }

    @Override
    protected void init() {
        super.init();
        this.openTimeMs = System.currentTimeMillis();
        this.focusedRoomPos = null;
        this.lastMouseX = -1;
        this.lastMouseY = -1;

        if (this.minecraft != null && this.minecraft.getWindow() != null) {
            GLFW.glfwSetInputMode(this.minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (this.minecraft != null && this.minecraft.getWindow() != null) {
            GLFW.glfwSetInputMode(this.minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isTabKey(keyCode, scanCode)) {
            // Debounce within first 250ms of opening to avoid instant auto-closing
            if (System.currentTimeMillis() - openTimeMs > 250) {
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

    @Override
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
        return true;
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
        if (lastMouseX >= 0 && lastMouseY >= 0) {
            double dx = mouseX - lastMouseX;
            double dy = mouseY - lastMouseY;
            if (dx != 0 || dy != 0) {
                panX -= dx * 1.5;
                panY -= dy * 1.5;
            }
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        int cx = width / 2;
        int cy = height / 2;

        // Dark CRT Blue Background
        graphics.fill(0, 0, width, height, 0xFF020712);

        // CRT Scanline Raster
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0f, 0.7f, 1.0f, 0.08f);
        graphics.blit(SCANLINES, 0, 0, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Font font = this.font;

        // 1. Vector Schematic Map (Rendered underneath UI panels)
        renderSchematicMap(graphics, font, cx, cy);

        // 2. Top Center Zone Title
        renderTopZoneHeader(graphics, font);

        // 3. Top Left Lifeform Status Counters
        renderLeftLifeformCounters(graphics, font);

        // 4. Bottom Left Event Log Feed
        renderBottomLeftLog(graphics, font);

        // 5. Bottom Right Access Tier & AP Bars
        renderBottomRightResources(graphics, font);

        // 6. Fixed Screen Center Reticle
        renderCenterReticle(graphics, font, cx, cy);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTopZoneHeader(GuiGraphics graphics, Font font) {
        FacilityZone zone = FacilityZone.LCZ;
        if (minecraft != null && minecraft.level != null && ClientCameraHandler.activeCameraPos != null) {
            if (minecraft.level.getBlockEntity(ClientCameraHandler.activeCameraPos) instanceof CameraBlockEntity cam) {
                zone = cam.getZone();
            }
        }

        String title = switch (zone) {
            case LCZ -> "ЗОНА ЛЁГКОГО СОДЕРЖАНИЯ";
            case HCZ -> "ЗОНА ТЯЖЁЛОГО СОДЕРЖАНИЯ";
            case EZ -> "ВХОДНАЯ ЗОНА";
            case SURFACE -> "ПОВЕРХНОСТЬ";
        };

        int tw = font.width(title);
        int tx = (width - tw) / 2;
        graphics.drawString(font, title, tx, 22, 0xFF42A5F5, true);
    }

    private void renderLeftLifeformCounters(GuiGraphics graphics, Font font) {
        int totalLifeforms = 0;
        int targets = 0;
        int scps = 0;
        int civilians = 0;
        int military = 0;

        if (minecraft != null && minecraft.level != null) {
            for (Player p : minecraft.level.players()) {
                if (!p.isSpectator()) {
                    totalLifeforms++;
                    targets++;
                    civilians++;
                }
            }
            List<LivingEntity> entities = minecraft.level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(minecraft.player.blockPosition()).inflate(128.0));
            for (LivingEntity e : entities) {
                if (e instanceof Monster) {
                    totalLifeforms++;
                    scps++;
                }
            }
        }

        int lx = 24;
        int ly = 22;

        graphics.drawString(font, "ВСЕГО ФОРМ ЖИЗНИ: " + Math.max(1, totalLifeforms), lx, ly, 0xFFE0F7FA, true);
        graphics.drawString(font, "ЦЕЛИ: " + Math.max(1, targets), lx, ly + 11, 0xFFE0F7FA, true);
        graphics.drawString(font, "SCP ОБЪЕКТЫ: " + scps, lx, ly + 22, 0xFFE0F7FA, true);
        graphics.drawString(font, "ГРАЖДАНСКИЙ: " + Math.max(1, civilians), lx, ly + 33, 0xFF90CAF9, false);
        graphics.drawString(font, "ВОЕННЫЙ: " + military, lx, ly + 44, 0xFF90CAF9, false);
    }

    private void renderBottomLeftLog(GuiGraphics graphics, Font font) {
        int ly = height - 90;
        int lx = 24;

        graphics.drawString(font, "УРОВЕНЬ ДОСТУПА " + ClientCameraHandler.tier + " - РАЗБЛОКИРОВАН", lx, ly, 0xFF42A5F5, true);
        graphics.drawString(font, "- ЭНЕРГИЯ лимит: " + (int) ClientCameraHandler.maxAp, lx, ly + 11, 0xFF64B5F6, false);
        graphics.drawString(font, "- ИЗОЛЯЦИЯ доступна [F] / [G]", lx, ly + 22, 0xFF64B5F6, false);

        List<Scp079Session.LogEntry> logs = ClientCameraHandler.logs;
        int maxShow = Math.min(3, logs.size());
        for (int i = 0; i < maxShow; i++) {
            Scp079Session.LogEntry e = logs.get(logs.size() - 1 - i);
            graphics.drawString(font, "> " + e.message, lx, ly + 35 + (i * 11), 0xFF81D4FA, false);
        }
    }

    private void renderBottomRightResources(GuiGraphics graphics, Font font) {
        int barW = 140;
        int barH = 12;
        int rx = width - 24 - barW;
        int ry = height - 60;

        // Tier
        String tierTxt = "УРОВЕНЬ ДОСТУПА " + ClientCameraHandler.tier;
        graphics.drawString(font, tierTxt, rx + (barW - font.width(tierTxt)) / 2, ry - 22, 0xFFE0F7FA, true);

        // EXP Bar
        String expTxt = "ОПЫТ " + ClientCameraHandler.exp + " / " + ClientCameraHandler.nextExp;
        renderFramedBar(graphics, font, rx, ry - 11, barW, barH, (float) ClientCameraHandler.exp / Math.max(1, ClientCameraHandler.nextExp), expTxt);

        // AP Bar
        String apTitle = "ВСПОМОГАТ. ЭНЕРГИЯ";
        graphics.drawString(font, apTitle, rx + (barW - font.width(apTitle)) / 2, ry + 3, 0xFFE0F7FA, true);

        String apTxt = (int) ClientCameraHandler.ap + " / " + (int) ClientCameraHandler.maxAp;
        renderFramedBar(graphics, font, rx, ry + 14, barW, barH, ClientCameraHandler.ap / Math.max(1.0f, ClientCameraHandler.maxAp), apTxt);
    }

    private void renderFramedBar(GuiGraphics graphics, Font font, int x, int y, int w, int h, float ratio, String label) {
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        graphics.fill(x, y, x + w, y + h, 0xAA020712);

        int fillW = (int) ((w - 2) * ratio);
        if (fillW > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, 0xCCE0F7FA);
        }

        graphics.fill(x, y, x + w, y + 1, 0xFFE0F7FA);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFFE0F7FA);
        graphics.fill(x, y + 1, x + 1, y + h, 0xFFE0F7FA);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFFE0F7FA);

        int tw = font.width(label);
        graphics.drawString(font, label, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFE0F7FA, true);
    }

    private void renderCenterReticle(GuiGraphics graphics, Font font, int cx, int cy) {
        int col = (focusedRoomPos != null) ? 0xFFFFFFFF : 0xCCE0F7FA;

        // Fixed Center Target Dot
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, col);

        if (focusedRoomPos != null) {
            String prompt = "[LMB / SPACE] SWITCH CAMERA";
            graphics.drawString(font, prompt, cx - (font.width(prompt) / 2), cy + 22, 0xFFE0F7FA, true);
        }
    }

    private void renderSchematicMap(GuiGraphics graphics, Font font, int cx, int cy) {
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
            boolean isFocused = roomPos.equals(focusedRoomPos);

            renderScpSlRoom(graphics, font, nodeX, nodeY, nodeW, nodeH, room, isCurrent, isFocused);
        }
    }

    private void renderScpSlRoom(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                 FacilityNetworkManager.RoomNode room, boolean isCurrent, boolean isFocused) {
        RoomShape shape = room.roomShape != null ? room.roomShape : RoomShape.SQUARE;
        int rot = Math.floorMod(room.rotation, 4);

        // Authentic SCP:SL Room Colors
        int bgCol = isCurrent ? 0xFF00C882 : (isFocused ? 0xFF1D78B2 : 0xFF125884);
        int frameCol = isCurrent ? 0xFF00FFB0 : (isFocused ? 0xFFFFFFFF : 0xFF186494);
        int scanlineCol = isCurrent ? 0x22FFFFFF : 0x1A00E5FF;

        if (room.isGeneratorBooting) {
            long time = System.currentTimeMillis() / 250;
            bgCol = (time % 2 == 0) ? 0xFFFF3300 : 0xFFFFAA00;
            frameCol = 0xFFFFFFFF;
        }

        int midX = x + (w / 2);
        int midY = y + (h / 2);
        int armW = 16;

        switch (shape) {
            case T_SHAPE -> {
                if (rot == 0) { // Stem South
                    fillWithScanlines(graphics, x, y, x + w, y + armW, bgCol, scanlineCol);
                    fillWithScanlines(graphics, midX - armW / 2, y + armW, midX + armW / 2, y + h, bgCol, scanlineCol);

                    graphics.fill(x, y, x + w, y + 1, frameCol);
                    graphics.fill(x, y, x + 1, y + armW, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + armW, frameCol);
                    graphics.fill(x, y + armW - 1, midX - armW / 2, y + armW, frameCol);
                    graphics.fill(midX + armW / 2, y + armW - 1, x + w, y + armW, frameCol);
                    graphics.fill(midX - armW / 2, y + armW, midX - armW / 2 + 1, y + h, frameCol);
                    graphics.fill(midX + armW / 2 - 1, y + armW, midX + armW / 2, y + h, frameCol);
                    graphics.fill(midX - armW / 2, y + h - 1, midX + armW / 2, y + h, frameCol);
                } else if (rot == 1) { // Stem West
                    fillWithScanlines(graphics, x + w - armW, y, x + w, y + h, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x, midY - armW / 2, x + w - armW, midY + armW / 2, bgCol, scanlineCol);

                    graphics.fill(x + w - 1, y, x + w, y + h, frameCol);
                    graphics.fill(x + w - armW, y, x + w, y + 1, frameCol);
                    graphics.fill(x + w - armW, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x + w - armW, y, x + w - armW + 1, midY - armW / 2, frameCol);
                    graphics.fill(x + w - armW, midY + armW / 2, x + w - armW + 1, y + h, frameCol);
                    graphics.fill(x, midY - armW / 2, x + w - armW, midY - armW / 2 + 1, frameCol);
                    graphics.fill(x, midY + armW / 2 - 1, x + w - armW, midY + armW / 2, frameCol);
                    graphics.fill(x, midY - armW / 2, x + 1, midY + armW / 2, frameCol);
                } else if (rot == 2) { // Stem North
                    fillWithScanlines(graphics, x, y + h - armW, x + w, y + h, bgCol, scanlineCol);
                    fillWithScanlines(graphics, midX - armW / 2, y, midX + armW / 2, y + h - armW, bgCol, scanlineCol);

                    graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x, y + h - armW, x + 1, y + h, frameCol);
                    graphics.fill(x + w - 1, y + h - armW, x + w, y + h, frameCol);
                    graphics.fill(x, y + h - armW, midX - armW / 2, y + h - armW + 1, frameCol);
                    graphics.fill(midX + armW / 2, y + h - armW, x + w, y + h - armW + 1, frameCol);
                    graphics.fill(midX - armW / 2, y, midX - armW / 2 + 1, y + h - armW, frameCol);
                    graphics.fill(midX + armW / 2 - 1, y, midX + armW / 2, y + h - armW, frameCol);
                    graphics.fill(midX - armW / 2, y, midX + armW / 2, y + 1, frameCol);
                } else { // Stem East
                    fillWithScanlines(graphics, x, y, x + armW, y + h, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x + armW, midY - armW / 2, x + w, midY + armW / 2, bgCol, scanlineCol);

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
                    fillWithScanlines(graphics, x, y, x + w, y + armW, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x + w - armW, y + armW, x + w, y + h, bgCol, scanlineCol);

                    graphics.fill(x, y, x + w, y + 1, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + h, frameCol);
                    graphics.fill(x, y, x + 1, y + armW, frameCol);
                    graphics.fill(x, y + armW - 1, x + w - armW, y + armW, frameCol);
                    graphics.fill(x + w - armW, y + armW, x + w - armW + 1, y + h, frameCol);
                    graphics.fill(x + w - armW, y + h - 1, x + w, y + h, frameCol);
                } else if (rot == 1) {
                    fillWithScanlines(graphics, x, y + h - armW, x + w, y + h, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x + w - armW, y, x + w, y + h - armW, bgCol, scanlineCol);

                    graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + h, frameCol);
                    graphics.fill(x, y + h - armW, x + 1, y + h, frameCol);
                    graphics.fill(x, y + h - armW, x + w - armW, y + h - armW + 1, frameCol);
                    graphics.fill(x + w - armW, y, x + w - armW + 1, y + h - armW, frameCol);
                    graphics.fill(x + w - armW, y, x + w, y + 1, frameCol);
                } else if (rot == 2) {
                    fillWithScanlines(graphics, x, y + h - armW, x + w, y + h, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x, y, x + armW, y + h - armW, bgCol, scanlineCol);

                    graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x, y, x + 1, y + h, frameCol);
                    graphics.fill(x + w - 1, y + h - armW, x + w, y + h, frameCol);
                    graphics.fill(x + armW, y + h - armW, x + w, y + h - armW + 1, frameCol);
                    graphics.fill(x + armW - 1, y, x + armW, y + h - armW, frameCol);
                    graphics.fill(x, y, x + armW, y + 1, frameCol);
                } else {
                    fillWithScanlines(graphics, x, y, x + w, y + armW, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x, y + armW, x + armW, y + h, bgCol, scanlineCol);

                    graphics.fill(x, y, x + w, y + 1, frameCol);
                    graphics.fill(x, y, x + 1, y + h, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + armW, frameCol);
                    graphics.fill(x + armW, y + armW - 1, x + w, y + armW, frameCol);
                    graphics.fill(x + armW - 1, y + armW, x + armW, y + h, frameCol);
                    graphics.fill(x, y + h - 1, x + armW, y + h, frameCol);
                }
            }
            case STRAIGHT -> {
                if (rot == 0 || rot == 2) { // Vertical
                    int cx1 = midX - armW / 2;
                    int cx2 = midX + armW / 2;
                    fillWithScanlines(graphics, cx1, y, cx2, y + h, bgCol, scanlineCol);
                    graphics.fill(cx1, y, cx1 + 1, y + h, frameCol);
                    graphics.fill(cx2 - 1, y, cx2, y + h, frameCol);
                    graphics.fill(cx1, y, cx2, y + 1, frameCol);
                    graphics.fill(cx1, y + h - 1, cx2, y + h, frameCol);
                } else { // Horizontal
                    int cy1 = midY - armW / 2;
                    int cy2 = midY + armW / 2;
                    fillWithScanlines(graphics, x, cy1, x + w, cy2, bgCol, scanlineCol);
                    graphics.fill(x, cy1, x + w, cy1 + 1, frameCol);
                    graphics.fill(x, cy2 - 1, x + w, cy2, frameCol);
                    graphics.fill(x, cy1, x + 1, cy2, frameCol);
                    graphics.fill(x + w - 1, cy1, x + w, cy2, frameCol);
                }
            }
            case CROSS_X -> {
                fillWithScanlines(graphics, x, midY - armW / 2, x + w, midY + armW / 2, bgCol, scanlineCol);
                fillWithScanlines(graphics, midX - armW / 2, y, midX + armW / 2, y + h, bgCol, scanlineCol);

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
            case END_ROOM, ELEVATOR, CHECKPOINT -> {
                int rx = x + 4;
                int ry = y + 4;
                int rw = w - 8;
                int rh = h - 8;
                fillWithScanlines(graphics, rx, ry, rx + rw, ry + rh, bgCol, scanlineCol);
                graphics.fill(rx, ry, rx + rw, ry + 1, frameCol);
                graphics.fill(rx, ry + rh - 1, rx + rw, ry + rh, frameCol);
                graphics.fill(rx, ry, rx + 1, ry + rh, frameCol);
                graphics.fill(rx + rw - 1, ry, rx + rw, ry + rh, frameCol);

                // Small entry doorway stub based on rotation
                if (rot == 0) {
                    fillWithScanlines(graphics, midX - 4, ry + rh - 1, midX + 4, y + h, bgCol, scanlineCol);
                    graphics.fill(midX - 4, ry + rh, midX - 3, y + h, frameCol);
                    graphics.fill(midX + 3, ry + rh, midX + 4, y + h, frameCol);
                } else if (rot == 1) {
                    fillWithScanlines(graphics, x, midY - 4, rx + 1, midY + 4, bgCol, scanlineCol);
                    graphics.fill(x, midY - 4, rx, midY - 3, frameCol);
                    graphics.fill(x, midY + 3, rx, midY + 4, frameCol);
                } else if (rot == 2) {
                    fillWithScanlines(graphics, midX - 4, y, midX + 4, ry + 1, bgCol, scanlineCol);
                    graphics.fill(midX - 4, y, midX - 3, ry, frameCol);
                    graphics.fill(midX + 3, y, midX + 4, ry, frameCol);
                } else {
                    fillWithScanlines(graphics, rx + rw - 1, midY - 4, x + w, midY + 4, bgCol, scanlineCol);
                    graphics.fill(rx + rw, midY - 4, x + w, midY - 3, frameCol);
                    graphics.fill(rx + rw, midY + 3, x + w, midY + 4, frameCol);
                }

                // If elevator / checkpoint: small red/green door indicator
                if (shape == RoomShape.ELEVATOR || shape == RoomShape.CHECKPOINT) {
                    graphics.fill(midX - 4, midY + 2, midX + 4, midY + 4, 0xFFFF4444);
                }
            }
            default -> { // SQUARE Hub
                fillWithScanlines(graphics, x, y, x + w, y + h, bgCol, scanlineCol);
                graphics.fill(x, y, x + w, y + 1, frameCol);
                graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                graphics.fill(x, y + 1, x + 1, y + h, frameCol);
                graphics.fill(x + w - 1, y, x + w, y + h, frameCol);
            }
        }

        // Room Name / Code Text inside Room
        String name = room.roomName;
        if (name != null && !name.isEmpty()) {
            int tw = font.width(name);
            int tx = midX - (tw / 2);
            int ty = midY - 4;
            int textCol = isCurrent ? 0xFF021B14 : 0xFFE0F7FA;
            graphics.drawString(font, name, tx, ty, textCol, false);
        }

        // If active camera: draw small red camera symbol
        if (isCurrent) {
            graphics.fill(midX - 3, midY + 4, midX + 3, midY + 6, 0xFFFF3333);
        }

        // Focused brackets
        if (isFocused) {
            graphics.fill(x - 3, y - 3, x + 1, y - 2, 0xFFFFFFFF);
            graphics.fill(x - 3, y - 3, x - 2, y + 1, 0xFFFFFFFF);

            graphics.fill(x + w - 1, y - 3, x + w + 3, y - 2, 0xFFFFFFFF);
            graphics.fill(x + w + 2, y - 3, x + w + 3, y + 1, 0xFFFFFFFF);

            graphics.fill(x - 3, y + h + 2, x + 1, y + h + 3, 0xFFFFFFFF);
            graphics.fill(x - 3, y + h - 1, x - 2, y + h + 3, 0xFFFFFFFF);

            graphics.fill(x + w - 1, y + h + 2, x + w + 3, y + h + 3, 0xFFFFFFFF);
            graphics.fill(x + w + 2, y + h - 1, x + w + 3, y + h + 3, 0xFFFFFFFF);
        }
    }

    private void fillWithScanlines(GuiGraphics graphics, int x1, int y1, int x2, int y2, int bgCol, int scanlineCol) {
        graphics.fill(x1, y1, x2, y2, bgCol);
        for (int y = y1; y < y2; y += 2) {
            graphics.fill(x1, y, x2, y + 1, scanlineCol);
        }
    }
}
