package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.blockentity.CameraBlockEntity;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class Scp079MapScreen extends Screen {
    private static final ResourceLocation SCANLINES = new ResourceLocation("zero_seven_nine", "textures/gui/scanlines.png");

    private double panX = 0;
    private double panY = 0;
    private FacilityNetworkManager.RoomNode focusedRoom = null;

    private long openTimeMs = 0;
    private double lastMouseX = -1;
    private double lastMouseY = -1;

    private FacilityZone viewingZone = FacilityZone.HCZ;

    public Scp079MapScreen() {
        super(Component.literal("Tactical Facility Map"));
    }

    @Override
    protected void init() {
        super.init();
        this.openTimeMs = System.currentTimeMillis();
        this.focusedRoom = null;
        this.lastMouseX = -1;
        this.lastMouseY = -1;

        // Determine active zone from current camera
        if (ClientCameraHandler.activeCameraPos != null) {
            CameraBlockEntity cam = FacilityNetworkManager.getCamera(ClientCameraHandler.activeCameraPos);
            if (cam != null) {
                this.viewingZone = cam.getZone();
            }
            FacilityNetworkManager.RoomNode r = FacilityNetworkManager.findRoomForCamera(ClientCameraHandler.activeCameraPos);
            if (r != null) {
                this.viewingZone = r.zone;
                // Center pan on active room
                this.panX = -r.gridX * 72;
                this.panY = -r.gridY * 54;
            }
        }

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
            if (focusedRoom != null && !focusedRoom.linkedCameras.isEmpty()) {
                BlockPos targetPos = focusedRoom.linkedCameras.get(0);
                if (focusedRoom.zone != viewingZone) {
                    ClientCameraHandler.startZoneTransition(viewingZone, focusedRoom.zone, targetPos);
                }
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(targetPos));
                this.onClose();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (focusedRoom != null && !focusedRoom.linkedCameras.isEmpty()) {
                BlockPos targetPos = focusedRoom.linkedCameras.get(0);
                if (focusedRoom.zone != viewingZone) {
                    ClientCameraHandler.startZoneTransition(viewingZone, focusedRoom.zone, targetPos);
                }
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(targetPos));
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

        // 1. Vector Schematic Map (Strictly isolated for current zone)
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
        String title = switch (viewingZone) {
            case LCZ -> "ЗОНА ЛЁГКОГО СОДЕРЖАНИЯ (LCZ)";
            case HCZ -> "ЗОНА ТЯЖЁЛОГО СОДЕРЖАНИЯ (HCZ)";
            case EZ -> "ВХОДНАЯ ЗОНА (EZ)";
            case SURFACE -> "ПОВЕРХНОСТЬ (SURFACE)";
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
        int col = (focusedRoom != null) ? 0xFFFFFFFF : 0xCCE0F7FA;

        // Fixed Center Target Dot
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, col);

        if (focusedRoom != null) {
            String prompt = "[LMB / SPACE] SWITCH CAMERA" + (!focusedRoom.linkedCameras.isEmpty() ? "" : " (NO CAMERA)");
            graphics.drawString(font, prompt, cx - (font.width(prompt) / 2), cy + 22, 0xFFE0F7FA, true);
        }
    }

    private void renderSchematicMap(GuiGraphics graphics, Font font, int cx, int cy) {
        List<FacilityNetworkManager.RoomNode> rooms = FacilityNetworkManager.getRoomsInZone(viewingZone);
        focusedRoom = null;
        double closestDistSq = 42.0 * 42.0;

        for (FacilityNetworkManager.RoomNode room : rooms) {
            int nodeW = 54;
            int nodeH = 38;
            int nodeX = cx + (int) panX + (room.gridX * 72) - (nodeW / 2);
            int nodeY = cy + (int) panY + (room.gridY * 54) - (nodeH / 2);

            int cellCenterX = nodeX + (nodeW / 2);
            int cellCenterY = nodeY + (nodeH / 2);

            double distSq = (cellCenterX - cx) * (cellCenterX - cx) + (cellCenterY - cy) * (cellCenterY - cy);

            if (distSq <= closestDistSq) {
                closestDistSq = distSq;
                focusedRoom = room;
            }

            boolean isCurrent = (ClientCameraHandler.activeCameraPos != null && room.linkedCameras.contains(ClientCameraHandler.activeCameraPos));
            boolean isFocused = (room == focusedRoom);

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

        if (!room.customPolygon.isEmpty()) {
            FacilityNetworkManager.fillPolygonWithScanlines(graphics, midX, midY, room.customPolygon, bgCol, scanlineCol, frameCol);
        } else {
            switch (shape) {
            case T_SHAPE -> {
                if (rot == 0) { // Stem South
                    fillWithScanlines(graphics, x, y, x + w, y + armW, bgCol, scanlineCol);
                    fillWithScanlines(graphics, midX - armW / 2, y + armW, midX + armW / 2, y + h, bgCol, scanlineCol);
                    graphics.fill(x, y, x + w, y + 1, frameCol);
                    graphics.fill(x, y + armW - 1, midX - armW / 2 + 1, y + armW, frameCol);
                    graphics.fill(midX + armW / 2 - 1, y + armW - 1, x + w, y + armW, frameCol);
                    graphics.fill(x, y, x + 1, y + armW, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + armW, frameCol);
                    graphics.fill(midX - armW / 2, y + armW, midX - armW / 2 + 1, y + h, frameCol);
                    graphics.fill(midX + armW / 2 - 1, y + armW, midX + armW / 2, y + h, frameCol);
                    graphics.fill(midX - armW / 2, y + h - 1, midX + armW / 2, y + h, frameCol);
                } else if (rot == 1) { // Stem West
                    fillWithScanlines(graphics, x + w - armW, y, x + w, y + h, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x, midY - armW / 2, x + w - armW, midY + armW / 2, bgCol, scanlineCol);
                    graphics.fill(x + w - 1, y, x + w, y + h, frameCol);
                    graphics.fill(x + w - armW, y, x + w, y + 1, frameCol);
                    graphics.fill(x + w - armW, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x, midY - armW / 2, x + 1, midY + armW / 2, frameCol);
                    graphics.fill(x, midY - armW / 2, x + w - armW, midY - armW / 2 + 1, frameCol);
                    graphics.fill(x, midY + armW / 2 - 1, x + w - armW, midY + armW / 2, frameCol);
                    graphics.fill(x + w - armW, y, x + w - armW + 1, midY - armW / 2 + 1, frameCol);
                    graphics.fill(x + w - armW, midY + armW / 2 - 1, x + w - armW + 1, y + h, frameCol);
                } else if (rot == 2) { // Stem North
                    fillWithScanlines(graphics, x, y + h - armW, x + w, y + h, bgCol, scanlineCol);
                    fillWithScanlines(graphics, midX - armW / 2, y, midX + armW / 2, y + h - armW, bgCol, scanlineCol);
                    graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x, y + h - armW, midX - armW / 2 + 1, y + h - armW + 1, frameCol);
                    graphics.fill(midX + armW / 2 - 1, y + h - armW, x + w, y + h - armW + 1, frameCol);
                    graphics.fill(x, y + h - armW, x + 1, y + h, frameCol);
                    graphics.fill(x + w - 1, y + h - armW, x + w, y + h, frameCol);
                    graphics.fill(midX - armW / 2, y, midX - armW / 2 + 1, y + h - armW, frameCol);
                    graphics.fill(midX + armW / 2 - 1, y, midX + armW / 2, y + h - armW, frameCol);
                    graphics.fill(midX - armW / 2, y, midX + armW / 2, y + 1, frameCol);
                } else { // Stem East
                    fillWithScanlines(graphics, x, y, x + armW, y + h, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x + armW, midY - armW / 2, x + w, midY + armW / 2, bgCol, scanlineCol);
                    graphics.fill(x, y, x + 1, y + h, frameCol);
                    graphics.fill(x, y, x + armW, y + 1, frameCol);
                    graphics.fill(x, y + h - 1, x + armW, y + h, frameCol);
                    graphics.fill(x + w - 1, midY - armW / 2, x + w, midY + armW / 2, frameCol);
                    graphics.fill(x + armW, midY - armW / 2, x + w, midY - armW / 2 + 1, frameCol);
                    graphics.fill(x + armW, midY + armW / 2 - 1, x + w, midY + armW / 2, frameCol);
                    graphics.fill(x + armW - 1, y, x + armW, midY - armW / 2 + 1, frameCol);
                    graphics.fill(x + armW - 1, midY + armW / 2 - 1, x + armW, y + h, frameCol);
                }
            }
            case CORNER -> {
                if (rot == 0) { // Top-Right corner
                    fillWithScanlines(graphics, x, y, x + w, y + armW, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x + w - armW, y + armW, x + w, y + h, bgCol, scanlineCol);
                    graphics.fill(x, y, x + w, y + 1, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + h, frameCol);
                    graphics.fill(x, y, x + 1, y + armW, frameCol);
                    graphics.fill(x + w - armW, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x, y + armW - 1, x + w - armW + 1, y + armW, frameCol);
                    graphics.fill(x + w - armW, y + armW, x + w - armW + 1, y + h, frameCol);
                } else if (rot == 1) { // Bottom-Right corner
                    fillWithScanlines(graphics, x, y + h - armW, x + w, y + h, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x + w - armW, y, x + w, y + h - armW, bgCol, scanlineCol);
                    graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + h, frameCol);
                    graphics.fill(x, y + h - armW, x + 1, y + h, frameCol);
                    graphics.fill(x + w - armW, y, x + w, y + 1, frameCol);
                    graphics.fill(x, y + h - armW, x + w - armW + 1, y + h - armW + 1, frameCol);
                    graphics.fill(x + w - armW, y, x + w - armW + 1, y + h - armW, frameCol);
                } else if (rot == 2) { // Bottom-Left corner
                    fillWithScanlines(graphics, x, y + h - armW, x + w, y + h, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x, y, x + armW, y + h - armW, bgCol, scanlineCol);
                    graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                    graphics.fill(x, y, x + 1, y + h, frameCol);
                    graphics.fill(x + w - 1, y + h - armW, x + w, y + h, frameCol);
                    graphics.fill(x, y, x + armW, y + 1, frameCol);
                    graphics.fill(x + armW - 1, y + h - armW, x + w, y + h - armW + 1, frameCol);
                    graphics.fill(x + armW - 1, y, x + armW, y + h - armW, frameCol);
                } else { // Top-Left corner
                    fillWithScanlines(graphics, x, y, x + w, y + armW, bgCol, scanlineCol);
                    fillWithScanlines(graphics, x, y + armW, x + armW, y + h, bgCol, scanlineCol);
                    graphics.fill(x, y, x + w, y + 1, frameCol);
                    graphics.fill(x, y, x + 1, y + h, frameCol);
                    graphics.fill(x + w - 1, y, x + w, y + armW, frameCol);
                    graphics.fill(x, y + h - 1, x + armW, y + h, frameCol);
                    graphics.fill(x + armW - 1, y + armW - 1, x + w, y + armW, frameCol);
                    graphics.fill(x + armW - 1, y + armW, x + armW, y + h, frameCol);
                }
            }
            case STRAIGHT -> {
                if (rot == 0 || rot == 2) { // Vertical Corridor
                    int cx1 = midX - (armW / 2);
                    int cx2 = midX + (armW / 2);
                    fillWithScanlines(graphics, cx1, y, cx2, y + h, bgCol, scanlineCol);
                    graphics.fill(cx1, y, cx1 + 1, y + h, frameCol);
                    graphics.fill(cx2 - 1, y, cx2, y + h, frameCol);
                    graphics.fill(cx1, y, cx2, y + 1, frameCol);
                    graphics.fill(cx1, y + h - 1, cx2, y + h, frameCol);
                } else { // Horizontal Corridor
                    int cy1 = midY - (armW / 2);
                    int cy2 = midY + (armW / 2);
                    fillWithScanlines(graphics, x, cy1, x + w, cy2, bgCol, scanlineCol);
                    graphics.fill(x, cy1, x + w, cy1 + 1, frameCol);
                    graphics.fill(x, cy2 - 1, x + w, cy2, frameCol);
                    graphics.fill(x, cy1, x + 1, cy2, frameCol);
                    graphics.fill(x + w - 1, cy1, x + w, cy2, frameCol);
                }
            }
            case CROSS -> {
                fillWithScanlines(graphics, x, midY - armW / 2, x + w, midY + armW / 2, bgCol, scanlineCol);
                fillWithScanlines(graphics, midX - armW / 2, y, midX + armW / 2, y + h, bgCol, scanlineCol);
                int x1 = midX - armW / 2;
                int x2 = midX + armW / 2;
                int y1 = midY - armW / 2;
                int y2 = midY + armW / 2;
                graphics.fill(x, y1, x1 + 1, y1 + 1, frameCol);
                graphics.fill(x2 - 1, y1, x + w, y1 + 1, frameCol);
                graphics.fill(x, y2 - 1, x1 + 1, y2, frameCol);
                graphics.fill(x2 - 1, y2 - 1, x + w, y2, frameCol);
                graphics.fill(x1, y, x1 + 1, y1 + 1, frameCol);
                graphics.fill(x2 - 1, y, x2, y1 + 1, frameCol);
                graphics.fill(x1, y2 - 1, x1 + 1, y + h, frameCol);
                graphics.fill(x2 - 1, y2 - 1, x2, y + h, frameCol);
            }
            case AIRLOCK -> {
                fillWithScanlines(graphics, x, y, x + w, y + h, bgCol, scanlineCol);
                graphics.fill(x, y, x + w, y + 1, frameCol);
                graphics.fill(x, y + h - 1, x + w, y + h, frameCol);
                graphics.fill(x, y + 1, x + 1, y + h, frameCol);
                graphics.fill(x + w - 1, y, x + w, y + h, frameCol);

                // Double airlock gate indicator bars
                graphics.fill(midX - 12, y + 2, midX - 10, y + h - 2, 0xFFFFCC00);
                graphics.fill(midX + 10, y + 2, midX + 12, y + h - 2, 0xFFFFCC00);
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
        }

        // Clean compact Room Title Text centered inside Room
        String name = room.roomName;
        if (name != null && !name.trim().isEmpty()) {
            name = name.trim();
            int textCol = isCurrent ? 0xFF021B14 : 0xFFE0F7FA;

            String[] parts = name.split("\\s+");
            String line1 = "";
            String line2 = "";

            if (parts.length == 1) {
                if (name.length() > 8) {
                    line1 = name.substring(0, 7);
                    line2 = name.substring(7);
                    if (line2.length() > 7) line2 = line2.substring(0, 6) + ".";
                } else {
                    line1 = name;
                }
            } else if (parts.length == 2) {
                line1 = parts[0];
                line2 = parts[1];
                if (line1.length() > 8) line1 = line1.substring(0, 7) + ".";
                if (line2.length() > 8) line2 = line2.substring(0, 7) + ".";
            } else {
                line1 = parts[0];
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(parts[i]);
                }
                line2 = sb.toString();
                if (line1.length() > 8) line1 = line1.substring(0, 7) + ".";
                if (line2.length() > 8) line2 = line2.substring(0, 7) + ".";
            }

            if (!line2.isEmpty()) {
                int tw1 = font.width(line1);
                int tw2 = font.width(line2);
                graphics.drawString(font, line1, midX - (tw1 / 2), midY - 8, textCol, false);
                graphics.drawString(font, line2, midX - (tw2 / 2), midY + 1, textCol, false);
            } else {
                int tw = font.width(line1);
                graphics.drawString(font, line1, midX - (tw / 2), midY - 4, textCol, false);
            }
        }

        // If active camera: draw small red camera symbol
        if (isCurrent) {
            graphics.fill(midX - 3, midY + 6, midX + 3, midY + 8, 0xFFFF3333);
        }
    }

    private void fillWithScanlines(GuiGraphics graphics, int x1, int y1, int x2, int y2, int bgCol, int scanlineCol) {
        graphics.fill(x1, y1, x2, y2, bgCol);
        for (int py = y1; py < y2; py += 2) {
            graphics.fill(x1, py, x2, py + 1, scanlineCol);
        }
    }
}
