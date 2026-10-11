package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.zerosevennine.ai.Scp079PlayerData;
import dev.zerosevennine.client.handler.ClientCameraHandler;
import dev.zerosevennine.network.C2SSwitchCameraPacket;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.*;

public class Scp079MapScreen extends Screen {

    private double panX = 0;
    private double panY = 0;
    private double zoom = 1.0;
    private final List<MapNode> nodes = new ArrayList<>();
    private int openTicks = 0;

    public static class MapNode {
        public BlockPos pos;
        public String roomName;
        public String zone;
        public boolean isCurrent;
        public boolean isVisited;
        public boolean isOvercharged;
        public boolean isBooting;
        public boolean hasScp;
        public int gridX;
        public int gridY;

        public MapNode(BlockPos pos, String roomName, String zone, boolean isCurrent, boolean isVisited,
                       boolean isOvercharged, boolean isBooting, boolean hasScp, int gridX, int gridY) {
            this.pos = pos;
            this.roomName = roomName;
            this.zone = zone;
            this.isCurrent = isCurrent;
            this.isVisited = isVisited;
            this.isOvercharged = isOvercharged;
            this.isBooting = isBooting;
            this.hasScp = hasScp;
            this.gridX = gridX;
            this.gridY = gridY;
        }
    }

    public Scp079MapScreen() {
        super(Component.literal("Tactical Facility Map"));
    }

    @Override
    protected void init() {
        super.init();
        this.openTicks = 0;
        buildMockFacilityNodes();
        if (minecraft != null) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.openTicks++;
    }

    private void buildMockFacilityNodes() {
        nodes.clear();
        BlockPos currentCamPos = ClientCameraHandler.getCurrentCameraPos();

        int[][] layout = {
            {0, 1, 0, 1, 0},
            {1, 1, 1, 1, 1},
            {0, 1, 1, 1, 0},
            {1, 1, 0, 1, 1},
            {0, 1, 1, 1, 0}
        };

        String[] sampleRooms = {
            "HCZ-01 Air", "Tesla Hall", "049 Chamber", "Gen Hall Alpha",
            "914 Airlock", "Server Farm", "Alpha Warhead", "Micro-HID Storage",
            "Heavy Intercom", "Gate A Chk", "Gate B Chk", "LCZ Elevator A",
            "LCZ Elevator B", "Decontam Airlock", "EZ Checkpoint"
        };

        int roomIdx = 0;
        for (int gy = 0; gy < layout.length; gy++) {
            for (int gx = 0; gx < layout[gy].length; gx++) {
                if (layout[gy][gx] == 1) {
                    boolean isCurr = (gx == 2 && gy == 2);
                    boolean isGenBooting = (gx == 1 && gy == 3);
                    boolean isGenOvercharged = (gx == 3 && gy == 1);
                    boolean hasScp = (gx == 1 && gy == 1);
                    String name = sampleRooms[roomIdx % sampleRooms.length];
                    roomIdx++;

                    BlockPos p = (currentCamPos != null && isCurr) ? currentCamPos :
                        new BlockPos((gx - 2) * 20, 60, (gy - 2) * 20);

                    nodes.add(new MapNode(p, name, "HCZ", isCurr, true, isGenOvercharged, isGenBooting, hasScp, gx - 2, gy - 2));
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (openTicks <= 3 && (keyCode == 258 || keyCode == 256)) {
            return true;
        }

        if (keyCode == 258 || keyCode == 256) {
            this.onClose();
            return true;
        }
        if (keyCode == 32) {
            triggerBreachScanner();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double centerX = width / 2.0 + panX;
            double centerY = height / 2.0 + panY;
            double cellSize = 38.0 * zoom;

            for (MapNode node : nodes) {
                double cellCenterX = centerX + node.gridX * (cellSize + 10.0 * zoom);
                double cellCenterY = centerY + node.gridY * (cellSize + 10.0 * zoom);
                double half = cellSize / 2.0;

                if (mouseX >= cellCenterX - half && mouseX <= cellCenterX + half &&
                    mouseY >= cellCenterY - half && mouseY <= cellCenterY + half) {
                    ModNetwork.sendToServer(new C2SSwitchCameraPacket(node.pos));
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        this.panX += dragX;
        this.panY += dragY;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            zoom = Math.min(2.5, zoom + 0.15);
        } else if (delta < 0) {
            zoom = Math.max(0.4, zoom - 0.15);
        }
        return true;
    }

    private void triggerBreachScanner() {
        Scp079PlayerData data = ClientCameraHandler.getClientData();
        if (data != null && data.getTier() >= 4 && data.getAuxiliaryPower() >= 40.0f) {
            Minecraft.getInstance().gui.getChat().addMessage(Component.literal("§b[079 BREACH SCANNER]§f Scanning facility sectors for living SCP lifeforms..."));
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        renderBackground(gfx);

        gfx.fill(0, 0, width, height, 0xDD030B12);

        renderGridLines(gfx);
        renderFacilityGraph(gfx);
        renderReticleAndOverlays(gfx);

        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    private void renderGridLines(GuiGraphics gfx) {
        int gridColor = 0x1A00FFFF;
        int step = (int) (24 * zoom);
        if (step < 8) step = 8;

        int offsetX = (int) (panX % step);
        int offsetY = (int) (panY % step);

        for (int x = offsetX; x < width; x += step) {
            gfx.fill(x, 0, x + 1, height, gridColor);
        }
        for (int y = offsetY; y < height; y += step) {
            gfx.fill(0, y, width, y + 1, gridColor);
        }
    }

    private void renderFacilityGraph(GuiGraphics gfx) {
        double centerX = width / 2.0 + panX;
        double centerY = height / 2.0 + panY;
        double cellSize = 38.0 * zoom;
        double spacing = 10.0 * zoom;

        for (int i = 0; i < nodes.size(); i++) {
            MapNode a = nodes.get(i);
            double ax = centerX + a.gridX * (cellSize + spacing);
            double ay = centerY + a.gridY * (cellSize + spacing);

            for (int j = i + 1; j < nodes.size(); j++) {
                MapNode b = nodes.get(j);
                if (Math.abs(a.gridX - b.gridX) + Math.abs(a.gridY - b.gridY) == 1) {
                    double bx = centerX + b.gridX * (cellSize + spacing);
                    double by = centerY + b.gridY * (cellSize + spacing);
                    drawVectorLine(gfx, (float) ax, (float) ay, (float) bx, (float) by, 0x4400E5FF, 1.5f);
                }
            }
        }

        long time = System.currentTimeMillis();
        boolean flashRed = (time / 400) % 2 == 0;

        for (MapNode node : nodes) {
            double nx = centerX + node.gridX * (cellSize + spacing);
            double ny = centerY + node.gridY * (cellSize + spacing);
            double half = cellSize / 2.0;

            int fillColor;
            int borderColor;

            if (node.isCurrent) {
                fillColor = 0x8800FF66;
                borderColor = 0xFF00FF88;
            } else if (node.isBooting) {
                fillColor = flashRed ? 0xAAFF2200 : 0x44FF5500;
                borderColor = 0xFFFF3300;
            } else if (node.isOvercharged) {
                fillColor = 0x660088FF;
                borderColor = 0xFF00AAFF;
            } else {
                fillColor = 0x44002B44;
                borderColor = 0xFF00E5FF;
            }

            gfx.fill((int) (nx - half), (int) (ny - half), (int) (nx + half), (int) (ny + half), fillColor);

            gfx.fill((int) (nx - half), (int) (ny - half), (int) (nx + half), (int) (ny - half + 1), borderColor);
            gfx.fill((int) (nx - half), (int) (ny + half - 1), (int) (nx + half), (int) (ny + half), borderColor);
            gfx.fill((int) (nx - half), (int) (ny - half), (int) (nx - half + 1), (int) (ny + half), borderColor);
            gfx.fill((int) (nx + half - 1), (int) (ny - half), (int) (nx + half), (int) (ny + half), borderColor);

            if (node.hasScp) {
                int scpColor = 0xFFFF0033;
                gfx.fill((int) (nx - 3), (int) (ny - 3), (int) (nx + 3), (int) (ny + 3), scpColor);
            }
        }
    }

    private void renderReticleAndOverlays(GuiGraphics gfx) {
        int cx = width / 2;
        int cy = height / 2;
        int reticleColor = 0xAA00E5FF;

        gfx.fill(cx - 10, cy, cx - 3, cy + 1, reticleColor);
        gfx.fill(cx + 3, cy, cx + 10, cy + 1, reticleColor);
        gfx.fill(cx, cy - 10, cx + 1, cy - 3, reticleColor);
        gfx.fill(cx, cy + 3, cx + 1, cy + 10, reticleColor);

        gfx.drawString(font, "§b[TACTICAL MAP]§7 - DRAG TO PAN | MMB/WHEEL TO ZOOM | [TAB] EXIT", 12, 12, 0xFFFFFFFF);
    }

    private void drawVectorLine(GuiGraphics gfx, float x1, float y1, float x2, float y2, int color, float width) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;

        float nx = -dy / len * (width / 2.0f);
        float ny = dx / len * (width / 2.0f);

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        Matrix4f mat = gfx.pose().last().pose();
        buffer.vertex(mat, x1 + nx, y1 + ny, 0).color(r, g, b, a).endVertex();
        buffer.vertex(mat, x1 - nx, y1 - ny, 0).color(r, g, b, a).endVertex();
        buffer.vertex(mat, x2 - nx, y2 - ny, 0).color(r, g, b, a).endVertex();
        buffer.vertex(mat, x2 + nx, y2 + ny, 0).color(r, g, b, a).endVertex();

        tesselator.end();
        RenderSystem.disableBlend();
    }
}
