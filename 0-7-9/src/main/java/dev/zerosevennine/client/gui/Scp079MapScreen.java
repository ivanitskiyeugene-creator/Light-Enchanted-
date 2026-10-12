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

    public static final int CELL_SIZE = FacilityNetworkManager.CELL_SIZE; // 42

    private double panX = 0;
    private double panY = 0;
    private FacilityNetworkManager.RoomNode focusedRoom = null;

    private long openTimeMs = 0;
    private double lastMouseX = -1;
    private double lastMouseY = -1;

    private FacilityZone viewingZone = FacilityZone.HCZ;

    // Entity count performance caching
    private static int cachedTotalLifeforms = 1;
    private static int cachedTargets = 1;
    private static int cachedScps = 0;
    private static int cachedCivilians = 1;
    private static int cachedMilitary = 0;
    private static long lastEntityScanTime = 0;

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
                this.panX = -(zox + r.gridX * CELL_SIZE);
                this.panY = -(zoy + r.gridY * CELL_SIZE);
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
            case LCZ -> -260;
            case HCZ -> -260;
            case EZ -> 260;
            case SURFACE -> 260;
        };
    }

    public static int getZoneOffsetY(FacilityZone zone) {
        return switch (zone) {
            case LCZ -> 220;
            case HCZ -> -110;
            case EZ -> -110;
            case SURFACE -> -440;
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

        // Single Fullscreen CRT Scanline Overlay
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0f, 0.7f, 1.0f, 0.08f);
        graphics.blit(SCANLINES, 0, 0, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Font font = this.font;

        // 1. Vector Schematic Map (1:1 identical to Designer Tablet)
        renderSchematicMap(graphics, font, cx, cy);

        // 2. Top Left Lifeform Status Counters
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
        long now = System.currentTimeMillis();
        if (now - lastEntityScanTime > 1000) { // Scan once per second to prevent FPS drop
            lastEntityScanTime = now;
            cachedTotalLifeforms = 0;
            cachedTargets = 0;
            cachedScps = 0;
            cachedCivilians = 0;
            cachedMilitary = 0;

            if (minecraft != null && minecraft.level != null) {
                for (Player p : minecraft.level.players()) {
                    if (!p.isSpectator()) {
                        cachedTotalLifeforms++;
                        cachedTargets++;
                        cachedCivilians++;
                    }
                }
                List<LivingEntity> entities = minecraft.level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(minecraft.player.blockPosition()).inflate(128.0));
                for (LivingEntity e : entities) {
                    if (e instanceof Monster) {
                        cachedTotalLifeforms++;
                        cachedScps++;
                    }
                }
            }
        }

        int lx = 24;
        int ly = 18;

        graphics.drawString(font, "ВСЕГО ФОРМ ЖИЗНИ: " + Math.max(1, cachedTotalLifeforms), lx, ly, 0xFFE0F7FA, true);
        graphics.drawString(font, "ЦЕЛИ: " + Math.max(1, cachedTargets), lx, ly + 11, 0xFFE0F7FA, true);
        graphics.drawString(font, "SCP ОБЪЕКТЫ: " + cachedScps, lx, ly + 22, 0xFFE0F7FA, true);
        graphics.drawString(font, "ГРАЖДАНСКИЙ: " + Math.max(1, cachedCivilians), lx, ly + 33, 0xFF81D4FA, false);
        graphics.drawString(font, "ВОЕННЫЙ: " + cachedMilitary, lx, ly + 44, 0xFF81D4FA, false);

        // Active / Hovered Room Name below counters (Top-Left)
        String activeRoomName = null;
        if (focusedRoom != null) {
            activeRoomName = focusedRoom.roomName;
        } else if (ClientCameraHandler.activeCameraPos != null) {
            FacilityNetworkManager.RoomNode cur = FacilityNetworkManager.findRoomForCamera(ClientCameraHandler.activeCameraPos);
            if (cur != null) activeRoomName = cur.roomName;
        }

        if (activeRoomName != null && !activeRoomName.trim().isEmpty()) {
            graphics.drawString(font, "> " + activeRoomName.toUpperCase(), lx, ly + 62, 0xFF00FF88, true);
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
            if (age > 4500) continue;

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

        String tierTxt = "УРОВЕНЬ ДОСТУПА " + ClientCameraHandler.tier;
        graphics.drawString(font, tierTxt, rx + (barW - font.width(tierTxt)) / 2, ry - 22, 0xFFE0F7FA, true);

        String expTxt = "ОПЫТ " + ClientCameraHandler.exp + " / " + ClientCameraHandler.nextExp;
        renderFramedBar(graphics, font, rx, ry - 11, barW, barH, (float) ClientCameraHandler.exp / Math.max(1, ClientCameraHandler.nextExp), expTxt);

        String apTitle = "ВСПОМОГАТ. ЭНЕРГИЯ";
        graphics.drawString(font, apTitle, rx + (barW - font.width(apTitle)) / 2, ry + 2, 0xFFE0F7FA, true);

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
        double closestDistSq = 32.0 * 32.0;

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
            int titleCenterX = cx + (int) panX + zoneOx + (((minGx + maxGx) * CELL_SIZE) / 2) + CELL_SIZE / 2;
            int titleY = cy + (int) panY + zoneOy + (minGy * CELL_SIZE) - 22;

            String zoneTitle = switch (zone) {
                case LCZ -> "ЗОНА ЛЁГКОГО СОДЕРЖАНИЯ";
                case HCZ -> "ЗОНА ТЯЖЁЛОГО СОДЕРЖАНИЯ";
                case EZ -> "ВХОДНАЯ ЗОНА";
                case SURFACE -> "ЗОНА ПОВЕРХНОСТИ";
            };

            int titleCol = isCurrentZone ? 0xFF29B6F6 : 0x5529B6F6;
            int tw = font.width(zoneTitle);
            graphics.drawString(font, zoneTitle, titleCenterX - (tw / 2), titleY, titleCol, true);

            // Render all rooms in zone using UNIFIED exact renderer
            for (FacilityNetworkManager.RoomNode room : rooms) {
                int nodeX = cx + (int) panX + zoneOx + (room.gridX * CELL_SIZE);
                int nodeY = cy + (int) panY + zoneOy + (room.gridY * CELL_SIZE);

                int cellCenterX = nodeX + CELL_SIZE / 2;
                int cellCenterY = nodeY + CELL_SIZE / 2;

                double distSq = (cellCenterX - cx) * (cellCenterX - cx) + (cellCenterY - cy) * (cellCenterY - cy);

                if (distSq <= closestDistSq) {
                    closestDistSq = distSq;
                    focusedRoom = room;
                }

                boolean isCurrent = (ClientCameraHandler.activeCameraPos != null && room.linkedCameras.contains(ClientCameraHandler.activeCameraPos));
                boolean isFocused = (room == focusedRoom);

                // Exact 1:1 Unified Room Renderer
                FacilityNetworkManager.renderRoomNode(graphics, font, cellCenterX, cellCenterY, room, isCurrent, isFocused, isCurrentZone);
            }
        }
    }
}
