package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.client.handler.ClientCameraHandler;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.RoomShape;
import dev.zerosevennine.network.C2SRequestMapSyncPacket;
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

    public static final int CELL_STEP_X = 72;
    public static final int CELL_STEP_Y = 54;
    public static final int NODE_W = 54;
    public static final int NODE_H = 38;
    public static final int ARM_W = 16;

    private double panX = 0;
    private double panY = 0;
    private FacilityNetworkManager.RoomNode focusedRoom = null;

    private long openTimeMs = 0;
    private double lastMouseX = -1;
    private double lastMouseY = -1;

    private FacilityZone viewingZone = FacilityZone.HCZ;

    public Scp079MapScreen() {
        super(Component.literal("Тактическая карта комплекса"));
    }

    @Override
    protected void init() {
        super.init();
        this.openTimeMs = System.currentTimeMillis();
        this.focusedRoom = null;
        this.lastMouseX = -1;
        this.lastMouseY = -1;

        // Immediately request fresh map data from server
        ModNetwork.CHANNEL.sendToServer(new C2SRequestMapSyncPacket());

        // Determine active zone from current camera
        if (ClientCameraHandler.activeCameraPos != null) {
            CameraBlockEntity cam = FacilityNetworkManager.getCamera(ClientCameraHandler.activeCameraPos);
            if (cam != null) {
                this.viewingZone = cam.getZone();
            }
            FacilityNetworkManager.RoomNode r = FacilityNetworkManager.findRoomForCamera(ClientCameraHandler.activeCameraPos);
            if (r != null) {
                this.viewingZone = r.zone;
                int zox = getZoneOffsetX(r.zone);
                int zoy = getZoneOffsetY(r.zone);
                this.panX = -(zox + r.gridX * CELL_STEP_X);
                this.panY = -(zoy + r.gridY * CELL_STEP_Y);
            } else {
                int zox = getZoneOffsetX(this.viewingZone);
                int zoy = getZoneOffsetY(this.viewingZone);
                this.panX = -zox;
                this.panY = -zoy;
            }
        }

        // Unrestricted raw mouse motion (never hits screen edges)
        if (this.minecraft != null && this.minecraft.getWindow() != null) {
            GLFW.glfwSetInputMode(this.minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        }
    }

    public static int getZoneOffsetX(FacilityZone zone) {
        return switch (zone) {
            case LCZ -> -320;
            case HCZ -> -320;
            case EZ -> 320;
            case SURFACE -> 320;
        };
    }

    public static int getZoneOffsetY(FacilityZone zone) {
        return switch (zone) {
            case LCZ -> 280;
            case HCZ -> -140;
            case EZ -> -140;
            case SURFACE -> -560;
        };
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
            handleRoomClick();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            handleRoomClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private FacilityZone getDefaultDestination(FacilityNetworkManager.RoomNode room) {
        if (room.targetZone != null && room.targetZone != room.zone) return room.targetZone;
        if (room.roomShape == RoomShape.CHECKPOINT) {
            return room.zone == FacilityZone.HCZ ? FacilityZone.EZ : (room.zone == FacilityZone.EZ ? FacilityZone.HCZ : room.zone);
        }
        if (room.roomShape == RoomShape.ELEVATOR) {
            if (room.zone == FacilityZone.LCZ) return FacilityZone.HCZ;
            if (room.zone == FacilityZone.HCZ) return FacilityZone.LCZ;
            if (room.zone == FacilityZone.EZ) return FacilityZone.SURFACE;
            if (room.zone == FacilityZone.SURFACE) return FacilityZone.EZ;
        }
        return room.zone;
    }

    private void handleRoomClick() {
        if (focusedRoom == null) return;

        FacilityZone activeZone = this.viewingZone;
        if (ClientCameraHandler.activeCameraPos != null) {
            CameraBlockEntity activeCam = FacilityNetworkManager.getCamera(ClientCameraHandler.activeCameraPos);
            if (activeCam != null) activeZone = activeCam.getZone();
        }

        int cost = (int) C2SSwitchCameraPacket.calculateTransitionCost(activeZone, focusedRoom.zone);
        if (cost > 0 && ClientCameraHandler.ap < cost) {
            return; // Insufficient AP
        }

        BlockPos targetCam = null;
        if (!focusedRoom.linkedCameras.isEmpty()) {
            targetCam = focusedRoom.linkedCameras.get(0);
        } else {
            targetCam = FacilityNetworkManager.findTransitCamera(focusedRoom, focusedRoom.zone);
        }

        if (targetCam != null) {
            if (focusedRoom.zone != activeZone) {
                ClientCameraHandler.startZoneTransition(activeZone, focusedRoom.zone, targetCam);
            }
            ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(targetCam));
            this.onClose();
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (lastMouseX != -1 && lastMouseY != -1) {
            double dx = mouseX - lastMouseX;
            double dy = mouseY - lastMouseY;
            if (Math.abs(dx) < 400 && Math.abs(dy) < 400) {
                panX -= dx * 1.5;
                panY -= dy * 1.5;
            }
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

        // 1. Vector Schematic Map (Zone titles hover naturally above their respective room clusters)
        renderSchematicMap(graphics, font, cx, cy);

        // 2. Top Left Lifeform Status Counters (Authentic Russian translation)
        renderLeftLifeformCounters(graphics, font);

        // 3. Bottom Left Ephemeral Event Log Feed
        renderBottomLeftLog(graphics, font);

        // 4. Bottom Right Access Tier & AP Bars
        renderBottomRightResources(graphics, font);

        // 5. Center Reticle
        renderCenterReticle(graphics, font, cx, cy);

        super.render(graphics, mouseX, mouseY, partialTick);
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
        int ly = 18;

        graphics.drawString(font, "ВСЕГО ФОРМ ЖИЗНИ: " + Math.max(1, totalLifeforms), lx, ly, 0xFFE0F7FA, true);
        graphics.drawString(font, "ЦЕЛИ: " + Math.max(1, targets), lx, ly + 11, 0xFFE0F7FA, true);
        graphics.drawString(font, "SCP ОБЪЕКТЫ: " + scps, lx, ly + 22, 0xFFE0F7FA, true);
        graphics.drawString(font, "СИЛЫ ФОНДА: " + military, lx, ly + 33, 0xFF81D4FA, false);
        graphics.drawString(font, "ПОВСТАНЦЫ ХАОСА: 0", lx, ly + 44, 0xFF81D4FA, false);
        graphics.drawString(font, "НАУЧНЫЕ СОТРУДНИКИ: " + (civilians / 2), lx, ly + 55, 0xFF81D4FA, false);
        graphics.drawString(font, "ПЕРСОНАЛ КЛАССА D: " + Math.max(1, civilians - (civilians / 2)), lx, ly + 66, 0xFF81D4FA, false);

        // Active / Hovered Room Name below counters (Top-Left)
        String activeRoomName = null;
        if (focusedRoom != null) {
            activeRoomName = focusedRoom.roomName;
        } else if (ClientCameraHandler.activeCameraPos != null) {
            FacilityNetworkManager.RoomNode cur = FacilityNetworkManager.findRoomForCamera(ClientCameraHandler.activeCameraPos);
            if (cur != null) activeRoomName = cur.roomName;
        }

        if (activeRoomName != null && !activeRoomName.trim().isEmpty()) {
            graphics.drawString(font, "> " + activeRoomName.toUpperCase(), lx, ly + 84, 0xFF00FF88, true);
        }
    }

    private void renderBottomLeftLog(GuiGraphics graphics, Font font) {
        long now = System.currentTimeMillis();
        List<Scp079Session.LogEntry> logs = ClientCameraHandler.logs;
        if (logs.isEmpty()) return;

        int ly = height - 28;
        int lx = 24;
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

    private void renderBottomRightResources(GuiGraphics graphics, Font font) {
        int barW = 142;
        int barH = 11;
        int rx = width - 24 - barW;
        int ry = height - 52;

        // Tier Title
        String tierTxt = "УРОВЕНЬ ДОСТУПА " + ClientCameraHandler.tier;
        graphics.drawString(font, tierTxt, rx + (barW - font.width(tierTxt)) / 2, ry - 22, 0xFFE0F7FA, true);

        // EXP Bar
        String expTxt = "ОПЫТ " + ClientCameraHandler.exp + " / " + ClientCameraHandler.nextExp;
        renderFramedBar(graphics, font, rx, ry - 11, barW, barH, (float) ClientCameraHandler.exp / Math.max(1, ClientCameraHandler.nextExp), expTxt);

        // AP Bar Title
        String apTitle = "ВСПОМОГАТ. ЭНЕРГИЯ";
        graphics.drawString(font, apTitle, rx + (barW - font.width(apTitle)) / 2, ry + 2, 0xFFE0F7FA, true);

        // AP Bar
        String apTxt = (int) ClientCameraHandler.ap + " / " + (int) ClientCameraHandler.maxAp;
        renderFramedBar(graphics, font, rx, ry + 13, barW, barH, ClientCameraHandler.ap / Math.max(1.0f, ClientCameraHandler.maxAp), apTxt);
    }

    private void renderFramedBar(GuiGraphics graphics, Font font, int x, int y, int w, int h, float ratio, String label) {
        ratio = Math.max(0.0f, Math.min(1.0f, ratio));
        graphics.fill(x, y, x + w, y + h, 0x88000000);

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
            FacilityZone activeZone = this.viewingZone;
            if (ClientCameraHandler.activeCameraPos != null) {
                CameraBlockEntity activeCam = FacilityNetworkManager.getCamera(ClientCameraHandler.activeCameraPos);
                if (activeCam != null) activeZone = activeCam.getZone();
            }

            int cost = (int) C2SSwitchCameraPacket.calculateTransitionCost(activeZone, focusedRoom.zone);
            boolean canAfford = ClientCameraHandler.ap >= cost;

            if (focusedRoom.zone == activeZone) {
                String prompt = !focusedRoom.linkedCameras.isEmpty() ?
                        "[LMB / SPACE] " + focusedRoom.roomName.toUpperCase() + " (0 AP)" :
                        "[ " + focusedRoom.roomName.toUpperCase() + " ] (НЕТ КАМЕРЫ)";
                graphics.drawString(font, prompt, cx - (font.width(prompt) / 2), cy + 14, 0xFFE0F7FA, true);
            } else {
                String prompt = !focusedRoom.linkedCameras.isEmpty() ?
                        "[LMB / SPACE] " + focusedRoom.zone.name() + ": " + focusedRoom.roomName.toUpperCase() + " (" + cost + " AP)" :
                        "[ " + focusedRoom.zone.name() + ": " + focusedRoom.roomName.toUpperCase() + " ] (" + cost + " AP)";
                int promptCol = canAfford ? 0xFFFFCC00 : 0xFFFF4444;
                graphics.drawString(font, prompt, cx - (font.width(prompt) / 2), cy + 14, promptCol, true);
            }
        }
    }

    private void renderSchematicMap(GuiGraphics graphics, Font font, int cx, int cy) {
        FacilityZone activeZone = this.viewingZone;
        if (ClientCameraHandler.activeCameraPos != null) {
            CameraBlockEntity activeCam = FacilityNetworkManager.getCamera(ClientCameraHandler.activeCameraPos);
            if (activeCam != null) activeZone = activeCam.getZone();
        }

        focusedRoom = null;
        double closestDistSq = 42.0 * 42.0;

        for (FacilityZone zone : FacilityZone.values()) {
            boolean isCurrentZone = (zone == activeZone);
            int zoneOx = getZoneOffsetX(zone);
            int zoneOy = getZoneOffsetY(zone);

            List<FacilityNetworkManager.RoomNode> rooms = FacilityNetworkManager.getRoomsInZone(zone);

            // Calculate Bounding Box of rooms to position Floating Zone Title directly above
            int minGx = 0, maxGx = 0, minGy = 0;
            if (!rooms.isEmpty()) {
                minGx = Integer.MAX_VALUE;
                maxGx = Integer.MIN_VALUE;
                minGy = Integer.MAX_VALUE;
                for (FacilityNetworkManager.RoomNode r : rooms) {
                    if (r.gridX < minGx) minGx = r.gridX;
                    if (r.gridX > maxGx) maxGx = r.gridX;
                    if (r.gridY < minGy) minGy = r.gridY;
                }
            }

            // Floating Zone Header moving with zone schematics
            int titleCenterX = cx + (int) panX + zoneOx + (((minGx + maxGx) * CELL_STEP_X) / 2);
            int titleY = cy + (int) panY + zoneOy + (minGy * CELL_STEP_Y) - (NODE_H / 2) - 24;

            String zoneTitle = switch (zone) {
                case LCZ -> "ЗОНА ЛЁГКОГО СОДЕРЖАНИЯ";
                case HCZ -> "ЗОНА ТЯЖЁЛОГО СОДЕРЖАНИЯ";
                case EZ -> "ВХОДНАЯ ЗОНА";
                case SURFACE -> "ЗОНА ПОВЕРХНОСТИ";
            };

            int titleCol = isCurrentZone ? 0xFF29B6F6 : 0x5529B6F6;
            int tw = font.width(zoneTitle);
            graphics.drawString(font, zoneTitle, titleCenterX - (tw / 2), titleY, titleCol, true);

            // Render all rooms in zone
            for (FacilityNetworkManager.RoomNode room : rooms) {
                int nodeW = NODE_W;
                int nodeH = NODE_H;
                int nodeX = cx + (int) panX + zoneOx + (room.gridX * CELL_STEP_X) - (nodeW / 2);
                int nodeY = cy + (int) panY + zoneOy + (room.gridY * CELL_STEP_Y) - (nodeH / 2);

                int cellCenterX = nodeX + (nodeW / 2);
                int cellCenterY = nodeY + (nodeH / 2);

                double distSq = (cellCenterX - cx) * (cellCenterX - cx) + (cellCenterY - cy) * (cellCenterY - cy);

                if (distSq <= closestDistSq) {
                    closestDistSq = distSq;
                    focusedRoom = room;
                }

                boolean isCurrent = (ClientCameraHandler.activeCameraPos != null && room.linkedCameras.contains(ClientCameraHandler.activeCameraPos));
                boolean isFocused = (room == focusedRoom);

                renderScpSlRoom(graphics, font, nodeX, nodeY, nodeW, nodeH, room, isCurrent, isFocused, isCurrentZone);
            }
        }
    }

    private void renderScpSlRoom(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                 FacilityNetworkManager.RoomNode room, boolean isCurrent, boolean isFocused, boolean isCurrentZone) {
        RoomShape shape = room.roomShape != null ? room.roomShape : RoomShape.SQUARE;
        int rot = Math.floorMod(room.rotation, 4);

        boolean isTransit = (room.targetZone != room.zone ||
                             room.roomShape == RoomShape.ELEVATOR ||
                             room.roomShape == RoomShape.CHECKPOINT);

        int bgCol;
        int frameCol;
        int scanlineCol;

        if (isCurrentZone) {
            bgCol = isCurrent ? 0xFF00C882 : (isFocused ? 0xFF1D78B2 : 0xFF125884);
            frameCol = isCurrent ? 0xFF00FFB0 : (isFocused ? 0xFFFFFFFF : 0xFF186494);
            scanlineCol = isCurrent ? 0x22FFFFFF : 0x1A00E5FF;

            if (room.isGeneratorBooting) {
                long time = System.currentTimeMillis() / 250;
                bgCol = (time % 2 == 0) ? 0xFFFF3300 : 0xFFFFAA00;
                frameCol = 0xFFFFFFFF;
            }
        } else {
            // Fog of War for foreign zones
            bgCol = isFocused ? 0xFF1A3850 : 0xFF081420;
            frameCol = isFocused ? 0xFFFFCC00 : 0xFF0E3854;
            scanlineCol = 0x0A00E5FF;
        }

        int midX = x + (w / 2);
        int midY = y + (h / 2);
        int armW = ARM_W;

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
                graphics.fill(rx, ry + 1, rx + 1, ry + rh, frameCol);
                graphics.fill(rx + rw - 1, ry, rx + rw, ry + rh, frameCol);

                if (shape == RoomShape.ELEVATOR) {
                    // Elevator arrow indicator
                    graphics.drawString(font, "▲", midX - 3, midY - 12, 0xFF00FF88, false);
                } else if (shape == RoomShape.CHECKPOINT) {
                    graphics.fill(midX - 8, midY - 2, midX + 8, midY + 2, 0xFFFFCC00);
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

        // Clean Room Title centered inside Room Outline Box (1:1 with authentic SCP:SL screenshot)
        String name = room.roomName;
        if (name != null && !name.trim().isEmpty()) {
            name = name.trim().toUpperCase();
            int textCol = isCurrent ? 0xFF021B14 : (isCurrentZone ? 0xFFE0F7FA : 0x8890CAF9);

            String[] parts = name.split("\\s+");
            if (parts.length == 1) {
                int tw = font.width(name);
                graphics.drawString(font, name, midX - (tw / 2), midY - 4, textCol, false);
            } else if (parts.length == 2) {
                int tw1 = font.width(parts[0]);
                int tw2 = font.width(parts[1]);
                graphics.drawString(font, parts[0], midX - (tw1 / 2), midY - 8, textCol, false);
                graphics.drawString(font, parts[1], midX - (tw2 / 2), midY + 1, textCol, false);
            } else {
                int tw = font.width(parts[0]);
                graphics.drawString(font, parts[0], midX - (tw / 2), midY - 4, textCol, false);
            }
        }

        // If player active camera: render small bright red camera indicator dot
        if (isCurrent) {
            graphics.fill(midX - 3, midY + 8, midX + 3, midY + 10, 0xFFFF3333);
        }
    }

    private void fillWithScanlines(GuiGraphics graphics, int x1, int y1, int x2, int y2, int bgCol, int scanlineCol) {
        graphics.fill(x1, y1, x2, y2, bgCol);
        for (int py = y1; py < y2; py += 2) {
            graphics.fill(x1, py, x2, py + 1, scanlineCol);
        }
    }
}
