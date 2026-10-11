package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.RoomShape;
import dev.zerosevennine.network.C2SEditMapRoomPacket;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FacilityMapDesignerScreen extends Screen {
    private static final ResourceLocation SCANLINES = new ResourceLocation("zero_seven_nine", "textures/gui/scanlines.png");

    private FacilityZone currentZone = FacilityZone.HCZ;

    // Grid Coordinates
    private int selectedGx = 0;
    private int selectedGy = 0;
    private int gridPanX = 0;
    private int gridPanY = 0;
    private static final int CELL_SIZE = 40;

    // Editor fields
    private EditBox nameEdit;
    private RoomShape selectedShape = RoomShape.STRAIGHT;
    private int selectedRotation = 0;
    private boolean selectedHasGen = false;
    private BlockPos selectedCamToLink = null;

    private int selectedCamIndex = 0;
    private final List<BlockPos> availableCameras = new ArrayList<>();

    private Button rotateBtn;
    private Button genBtn;

    public FacilityMapDesignerScreen() {
        super(Component.literal("Facility Map Architect"));
    }

    public FacilityMapDesignerScreen(FacilityZone initialZone) {
        super(Component.literal("Facility Map Architect"));
        if (initialZone != null) {
            this.currentZone = initialZone;
        }
    }

    @Override
    protected void init() {
        super.init();
        refreshAvailableCameras();

        int panelW = 180;
        int panelX = width - panelW - 8;
        int panelY = 28;

        // Zone Tab Buttons at Top
        int tabW = 64;
        int tabStartX = 10;
        addRenderableWidget(Button.builder(Component.literal("LCZ"), b -> setZone(FacilityZone.LCZ))
                .bounds(tabStartX, 6, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("HCZ"), b -> setZone(FacilityZone.HCZ))
                .bounds(tabStartX + tabW + 4, 6, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("EZ"), b -> setZone(FacilityZone.EZ))
                .bounds(tabStartX + (tabW + 4) * 2, 6, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("SURFACE"), b -> setZone(FacilityZone.SURFACE))
                .bounds(tabStartX + (tabW + 4) * 3, 6, tabW, 18).build());

        // Room Name EditBox
        int sy = panelY + 16;
        nameEdit = new EditBox(font, panelX + 8, sy, panelW - 16, 16, Component.literal("Room Name"));
        nameEdit.setMaxLength(24);
        nameEdit.setValue("ROOM " + selectedGx + ":" + selectedGy);
        addRenderableWidget(nameEdit);

        // Load existing room data if present
        loadRoomDataAtSelection();

        // Template Shape Selector Buttons (2 Columns)
        int shapeBtnW = (panelW - 20) / 2;
        int shapeBtnH = 15;
        int sX1 = panelX + 8;
        int sX2 = panelX + 8 + shapeBtnW + 4;
        sy += 20 + 64; // space for Live Preview box (64px)

        addRenderableWidget(Button.builder(Component.literal("| STRAIGHT"), b -> selectedShape = RoomShape.STRAIGHT)
                .bounds(sX1, sy, shapeBtnW, shapeBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("L CORNER"), b -> selectedShape = RoomShape.CORNER)
                .bounds(sX2, sy, shapeBtnW, shapeBtnH).build());

        sy += 17;
        addRenderableWidget(Button.builder(Component.literal("T SHAPE"), b -> selectedShape = RoomShape.T_SHAPE)
                .bounds(sX1, sy, shapeBtnW, shapeBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("+ CROSS"), b -> selectedShape = RoomShape.CROSS)
                .bounds(sX2, sy, shapeBtnW, shapeBtnH).build());

        sy += 17;
        addRenderableWidget(Button.builder(Component.literal("[ ] END"), b -> selectedShape = RoomShape.END_ROOM)
                .bounds(sX1, sy, shapeBtnW, shapeBtnH).build());
        addRenderableWidget(Button.builder(Component.literal(">< AIRLOCK"), b -> selectedShape = RoomShape.AIRLOCK)
                .bounds(sX2, sy, shapeBtnW, shapeBtnH).build());

        sy += 17;
        addRenderableWidget(Button.builder(Component.literal("[^] ELEVATOR"), b -> selectedShape = RoomShape.ELEVATOR)
                .bounds(sX1, sy, shapeBtnW, shapeBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("# HUB"), b -> selectedShape = RoomShape.SQUARE)
                .bounds(sX2, sy, shapeBtnW, shapeBtnH).build());

        // Rotation & Generator Buttons
        sy += 19;
        rotateBtn = Button.builder(Component.literal("ROTATE: " + (selectedRotation * 90) + "°"), b -> {
            selectedRotation = (selectedRotation + 1) % 4;
            b.setMessage(Component.literal("ROTATE: " + (selectedRotation * 90) + "°"));
        }).bounds(sX1, sy, shapeBtnW, 16).build();
        addRenderableWidget(rotateBtn);

        genBtn = Button.builder(Component.literal("GEN: " + (selectedHasGen ? "YES" : "NO")), b -> {
            selectedHasGen = !selectedHasGen;
            b.setMessage(Component.literal("GEN: " + (selectedHasGen ? "YES" : "NO")));
        }).bounds(sX2, sy, shapeBtnW, 16).build();
        addRenderableWidget(genBtn);

        // Camera Picker Cycle & Link
        sy += 18;
        addRenderableWidget(Button.builder(Component.literal("CYCLE CAM"), b -> cycleCamera())
                .bounds(sX1, sy, shapeBtnW, 15).build());
        addRenderableWidget(Button.builder(Component.literal("+ LINK CAM"), b -> linkSelectedCamera())
                .bounds(sX2, sy, shapeBtnW, 15).build());

        // Place / Update Room Action Button
        sy += 28;
        addRenderableWidget(Button.builder(Component.literal("✓ PLACE / UPDATE ROOM"), b -> placeOrUpdateRoom())
                .bounds(sX1, sy, panelW - 16, 18).build());

        // Delete Room Button
        sy += 20;
        addRenderableWidget(Button.builder(Component.literal("✗ DELETE ROOM"), b -> deleteSelectedRoom())
                .bounds(sX1, sy, panelW - 16, 16).build());

        // Auto-Link WASD Button
        sy += 18;
        addRenderableWidget(Button.builder(Component.literal("⚡ AUTO-LINK WASD"), b -> autoLinkWasd())
                .bounds(sX1, sy, panelW - 16, 16).build());

        // Clear Zone Button
        sy += 18;
        addRenderableWidget(Button.builder(Component.literal("CLEAR ZONE"), b -> clearCurrentZone())
                .bounds(sX1, sy, panelW - 16, 15).build());
    }

    private void setZone(FacilityZone zone) {
        this.currentZone = zone;
        refreshAvailableCameras();
        loadRoomDataAtSelection();
    }

    private void refreshAvailableCameras() {
        availableCameras.clear();
        for (Map.Entry<BlockPos, CameraBlockEntity> e : FacilityNetworkManager.getCameras().entrySet()) {
            if (e.getValue().getZone() == currentZone) {
                availableCameras.add(e.getKey());
            }
        }
        if (selectedCamIndex >= availableCameras.size()) {
            selectedCamIndex = 0;
        }
        if (!availableCameras.isEmpty()) {
            selectedCamToLink = availableCameras.get(selectedCamIndex);
        } else {
            selectedCamToLink = null;
        }
    }

    private void cycleCamera() {
        if (availableCameras.isEmpty()) {
            selectedCamToLink = null;
            return;
        }
        selectedCamIndex = (selectedCamIndex + 1) % availableCameras.size();
        selectedCamToLink = availableCameras.get(selectedCamIndex);
    }

    private void loadRoomDataAtSelection() {
        FacilityNetworkManager.RoomNode room = FacilityNetworkManager.getRoomAt(currentZone, selectedGx, selectedGy);
        if (room != null) {
            nameEdit.setValue(room.roomName != null ? room.roomName : "");
            selectedShape = room.roomShape != null ? room.roomShape : RoomShape.SQUARE;
            selectedRotation = room.rotation;
            selectedHasGen = room.hasGenerator;
        } else {
            nameEdit.setValue("ROOM " + selectedGx + ":" + selectedGy);
            selectedShape = RoomShape.STRAIGHT;
            selectedRotation = 0;
            selectedHasGen = false;
        }

        if (rotateBtn != null) {
            rotateBtn.setMessage(Component.literal("ROTATE: " + (selectedRotation * 90) + "°"));
        }
        if (genBtn != null) {
            genBtn.setMessage(Component.literal("GEN: " + (selectedHasGen ? "YES" : "NO")));
        }
    }

    private void placeOrUpdateRoom() {
        String name = nameEdit.getValue().trim();
        if (name.isEmpty()) name = "ROOM " + selectedGx + ":" + selectedGy;

        ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                C2SEditMapRoomPacket.Action.PLACE_OR_UPDATE,
                currentZone, selectedGx, selectedGy, name, selectedShape, selectedRotation,
                selectedHasGen, currentZone, selectedCamToLink
        ));

        // Optimistically register locally
        FacilityNetworkManager.RoomNode node = new FacilityNetworkManager.RoomNode(
                new BlockPos(selectedGx * 16, 64, selectedGy * 16), name, currentZone, null, selectedShape, selectedRotation, currentZone, selectedGx, selectedGy
        );
        node.hasGenerator = selectedHasGen;
        if (selectedCamToLink != null && !node.linkedCameras.contains(selectedCamToLink)) {
            node.linkedCameras.add(selectedCamToLink);
        }
        FacilityNetworkManager.registerRoomNode(node);
    }

    private void deleteSelectedRoom() {
        ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                C2SEditMapRoomPacket.Action.DELETE,
                currentZone, selectedGx, selectedGy, "", RoomShape.SQUARE, 0, false, currentZone, null
        ));
        FacilityNetworkManager.removeRoom(currentZone, selectedGx, selectedGy);
        loadRoomDataAtSelection();
    }

    private void linkSelectedCamera() {
        if (selectedCamToLink != null) {
            ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                    C2SEditMapRoomPacket.Action.LINK_CAMERA,
                    currentZone, selectedGx, selectedGy, "", selectedShape, selectedRotation, selectedHasGen, currentZone, selectedCamToLink
            ));
            FacilityNetworkManager.RoomNode room = FacilityNetworkManager.getRoomAt(currentZone, selectedGx, selectedGy);
            if (room != null && !room.linkedCameras.contains(selectedCamToLink)) {
                room.linkedCameras.add(selectedCamToLink);
            }
        }
    }

    private void autoLinkWasd() {
        ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                C2SEditMapRoomPacket.Action.AUTO_LINK_WASD,
                currentZone, 0, 0, "", RoomShape.SQUARE, 0, false, currentZone, null
        ));
    }

    private void clearCurrentZone() {
        ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                C2SEditMapRoomPacket.Action.CLEAR_ZONE,
                currentZone, 0, 0, "", RoomShape.SQUARE, 0, false, currentZone, null
        ));
        FacilityNetworkManager.clearZone(currentZone);
        loadRoomDataAtSelection();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = 180;
        int canvasW = width - panelW - 24;
        int canvasH = height - 36;

        if (mouseX >= 8 && mouseX <= 8 + canvasW && mouseY >= 28 && mouseY <= 28 + canvasH) {
            int centerX = 8 + canvasW / 2 + gridPanX;
            int centerY = 28 + canvasH / 2 + gridPanY;

            int clickGx = (int) Math.floor((mouseX - centerX) / (double) CELL_SIZE);
            int clickGy = (int) Math.floor((mouseY - centerY) / (double) CELL_SIZE);

            selectedGx = clickGx;
            selectedGy = clickGy;
            loadRoomDataAtSelection();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 || button == 2) {
            int panelW = 180;
            int canvasW = width - panelW - 24;
            if (mouseX < 8 + canvasW) {
                gridPanX += (int) dragX;
                gridPanY += (int) dragY;
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deep blueprint background
        graphics.fill(0, 0, width, height, 0xFF020914);

        int panelW = 180;
        int canvasX = 8;
        int canvasY = 28;
        int canvasW = width - panelW - 24;
        int canvasH = height - 36;

        // Blueprint Grid canvas border
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF030D1A);
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + 1, 0xFF00E5FF);
        graphics.fill(canvasX, canvasY + canvasH - 1, canvasX + canvasW, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(canvasX, canvasY, canvasX + 1, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(canvasX + canvasW - 1, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF00E5FF);

        int centerX = canvasX + canvasW / 2 + gridPanX;
        int centerY = canvasY + canvasH / 2 + gridPanY;

        // Render Coordinate Grid Lines
        for (int x = -15; x <= 15; x++) {
            int px = centerX + x * CELL_SIZE;
            if (px >= canvasX && px <= canvasX + canvasW) {
                graphics.fill(px, canvasY + 1, px + 1, canvasY + canvasH - 1, (x == 0) ? 0x6600E5FF : 0x1A00E5FF);
            }
        }
        for (int y = -15; y <= 15; y++) {
            int py = centerY + y * CELL_SIZE;
            if (py >= canvasY && py <= canvasY + canvasH) {
                graphics.fill(canvasX + 1, py, canvasX + canvasW - 1, py + 1, (y == 0) ? 0x6600E5FF : 0x1A00E5FF);
            }
        }

        // Render Placed Rooms on Grid Canvas with full geometry & scanlines
        List<FacilityNetworkManager.RoomNode> zoneRooms = FacilityNetworkManager.getRoomsInZone(currentZone);
        for (FacilityNetworkManager.RoomNode r : zoneRooms) {
            int rx = centerX + r.gridX * CELL_SIZE;
            int ry = centerY + r.gridY * CELL_SIZE;

            if (rx + CELL_SIZE >= canvasX && rx <= canvasX + canvasW && ry + CELL_SIZE >= canvasY && ry <= canvasY + canvasH) {
                boolean isSelected = (r.gridX == selectedGx && r.gridY == selectedGy);
                renderGeometricRoomBox(graphics, font, rx + 1, ry + 1, CELL_SIZE - 2, CELL_SIZE - 2,
                        r.roomShape, r.rotation, r.roomName, r.hasGenerator, isSelected, false);
            }
        }

        // Highlight Selected Grid Cell Cursor
        int selPx = centerX + selectedGx * CELL_SIZE;
        int selPy = centerY + selectedGy * CELL_SIZE;
        if (selPx + CELL_SIZE >= canvasX && selPx <= canvasX + canvasW && selPy + CELL_SIZE >= canvasY && selPy <= canvasY + canvasH) {
            graphics.fill(selPx, selPy, selPx + CELL_SIZE, selPy + 1, 0xFF00FF88);
            graphics.fill(selPx, selPy + CELL_SIZE - 1, selPx + CELL_SIZE, selPy + CELL_SIZE, 0xFF00FF88);
            graphics.fill(selPx, selPy, selPx + 1, selPy + CELL_SIZE, 0xFF00FF88);
            graphics.fill(selPx + CELL_SIZE - 1, selPy, selPx + CELL_SIZE, selPy + CELL_SIZE, 0xFF00FF88);
        }

        // Right Inspector Panel
        int panelX = width - panelW - 8;
        graphics.fill(panelX, canvasY, width - 8, canvasY + canvasH, 0xFF081928);
        graphics.fill(panelX, canvasY, width - 8, canvasY + 1, 0xFF00E5FF);
        graphics.fill(panelX, canvasY + canvasH - 1, width - 8, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(panelX, canvasY, panelX + 1, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(width - 9, canvasY, width - 8, canvasY + canvasH, 0xFF00E5FF);

        // Header info
        String zoneTitle = "ZONE: " + currentZone.name() + " BLUEPRINT";
        graphics.drawString(font, zoneTitle, 290, 10, 0xFF00E5FF, false);
        graphics.drawString(font, "CELL: [" + selectedGx + ", " + selectedGy + "]", panelX + 8, canvasY + 4, 0xFFE0F7FA, false);

        // ==========================================
        // 🔍 LIVE ROOM PREVIEW BOX (ПРЕДПРОСМОТР)
        // ==========================================
        int prevX = panelX + 8;
        int prevY = canvasY + 36;
        int prevW = panelW - 16;
        int prevH = 58;

        // Preview Frame
        graphics.fill(prevX, prevY, prevX + prevW, prevY + prevH, 0xFF020712);
        graphics.fill(prevX, prevY, prevX + prevW, prevY + 1, 0xFF00E5FF);
        graphics.fill(prevX, prevY + prevH - 1, prevX + prevW, prevY + prevH, 0xFF00E5FF);
        graphics.fill(prevX, prevY, prevX + 1, prevY + prevH, 0xFF00E5FF);
        graphics.fill(prevX + prevW - 1, prevY, prevX + prevW, prevY + prevH, 0xFF00E5FF);

        // Preview Title Badge
        String prevTitle = "LIVE MAP PREVIEW";
        graphics.drawString(font, prevTitle, prevX + (prevW - font.width(prevTitle)) / 2, prevY - 9, 0xFF81D4FA, false);

        // Live Vector schematic inside preview
        String liveName = nameEdit != null ? nameEdit.getValue() : "ROOM";
        int boxW = 54;
        int boxH = 38;
        int bx = prevX + (prevW - boxW) / 2;
        int by = prevY + (prevH - boxH) / 2;
        renderGeometricRoomBox(graphics, font, bx, by, boxW, boxH, selectedShape, selectedRotation, liveName, selectedHasGen, true, true);

        // Camera Link info
        String camText = (selectedCamToLink != null) ?
                ("CAM: [" + selectedCamToLink.getX() + ", " + selectedCamToLink.getZ() + "]") : "CAM: (NONE)";
        graphics.drawString(font, camText, panelX + 8, canvasY + 192, 0xFF00FF88, false);

        FacilityNetworkManager.RoomNode curRoom = FacilityNetworkManager.getRoomAt(currentZone, selectedGx, selectedGy);
        if (curRoom != null) {
            graphics.drawString(font, "LINKED: " + curRoom.linkedCameras.size() + " CAMS", panelX + 8, canvasY + 204, 0xFFE0F7FA, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderGeometricRoomBox(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                        RoomShape shape, int rot, String name, boolean hasGen, boolean isSelected, boolean isLivePreview) {
        rot = Math.floorMod(rot, 4);

        int bgCol = isSelected ? 0xFF1D78B2 : 0xFF125884;
        int frameCol = isSelected ? 0xFF00FFFF : 0xFF186494;
        int scanlineCol = 0x2200E5FF;

        int midX = x + (w / 2);
        int midY = y + (h / 2);
        int armW = Math.max(8, (int) (w * 0.38f));

        if (shape == null) shape = RoomShape.SQUARE;

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
                graphics.fill(midX - 10, y + 2, midX - 8, y + h - 2, 0xFFFFCC00);
                graphics.fill(midX + 8, y + 2, midX + 10, y + h - 2, 0xFFFFCC00);
            }
            case END_ROOM, ELEVATOR, CHECKPOINT -> {
                int rx = x + 3;
                int ry = y + 3;
                int rw = w - 6;
                int rh = h - 6;
                fillWithScanlines(graphics, rx, ry, rx + rw, ry + rh, bgCol, scanlineCol);
                graphics.fill(rx, ry, rx + rw, ry + 1, frameCol);
                graphics.fill(rx, ry + rh - 1, rx + rw, ry + rh, frameCol);
                graphics.fill(rx, ry, rx + 1, ry + rh, frameCol);
                graphics.fill(rx + rw - 1, ry, rx + rw, ry + rh, frameCol);

                if (shape == RoomShape.ELEVATOR || shape == RoomShape.CHECKPOINT) {
                    graphics.fill(midX - 3, midY + 1, midX + 3, midY + 3, 0xFFFF4444);
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

        // Clean compact Room Title Text centered inside Room (with intelligent word-aware wrapping)
        if (name != null && !name.trim().isEmpty()) {
            name = name.trim();
            int textCol = 0xFFE0F7FA;

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

        if (hasGen) {
            graphics.fill(midX - 2, y + 2, midX + 2, y + 4, 0xFFFFCC00);
        }
    }

    private void fillWithScanlines(GuiGraphics graphics, int x1, int y1, int x2, int y2, int bgCol, int scanlineCol) {
        graphics.fill(x1, y1, x2, y2, bgCol);
        for (int py = y1; py < y2; py += 2) {
            graphics.fill(x1, py, x2, py + 1, scanlineCol);
        }
    }
}
