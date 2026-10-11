package dev.zerosevennine.client.gui;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.RoomShape;
import dev.zerosevennine.network.C2SEditMapRoomPacket;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FacilityMapDesignerScreen extends Screen {
    private FacilityZone currentZone = FacilityZone.HCZ;

    // Grid Coordinates
    private int selectedGx = 0;
    private int selectedGy = 0;
    private int gridPanX = 0;
    private int gridPanY = 0;
    private static final int CELL_SIZE = 32;

    // Editor fields
    private EditBox nameEdit;
    private RoomShape selectedShape = RoomShape.STRAIGHT;
    private int selectedRotation = 0;
    private boolean selectedHasGen = false;
    private BlockPos selectedCamToLink = null;

    private int selectedCamIndex = 0;
    private final List<BlockPos> availableCameras = new ArrayList<>();

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

        int panelX = width - 160;
        int panelY = 28;

        // Room Name EditBox
        nameEdit = new EditBox(font, panelX + 8, panelY + 16, 144, 18, Component.literal("Room Name"));
        nameEdit.setMaxLength(24);
        nameEdit.setValue("ROOM " + selectedGx + ":" + selectedGy);
        addRenderableWidget(nameEdit);

        // Load existing room data if present
        loadRoomDataAtSelection();

        // Zone Tab Buttons at Top
        int tabW = 68;
        int tabStartX = 12;
        addRenderableWidget(Button.builder(Component.literal("LCZ"), b -> setZone(FacilityZone.LCZ))
                .bounds(tabStartX, 6, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("HCZ"), b -> setZone(FacilityZone.HCZ))
                .bounds(tabStartX + tabW + 4, 6, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("EZ"), b -> setZone(FacilityZone.EZ))
                .bounds(tabStartX + (tabW + 4) * 2, 6, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("SURFACE"), b -> setZone(FacilityZone.SURFACE))
                .bounds(tabStartX + (tabW + 4) * 3, 6, tabW, 18).build());

        // Template Shape Selector Buttons
        int shapeBtnW = 68;
        int shapeBtnH = 16;
        int sX1 = panelX + 8;
        int sX2 = panelX + 80;
        int sy = panelY + 48;

        addRenderableWidget(Button.builder(Component.literal("| STRAIGHT"), b -> selectedShape = RoomShape.STRAIGHT)
                .bounds(sX1, sy, shapeBtnW, shapeBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("L CORNER"), b -> selectedShape = RoomShape.CORNER)
                .bounds(sX2, sy, shapeBtnW, shapeBtnH).build());

        sy += 18;
        addRenderableWidget(Button.builder(Component.literal("T SHAPE"), b -> selectedShape = RoomShape.T_SHAPE)
                .bounds(sX1, sy, shapeBtnW, shapeBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("+ CROSS"), b -> selectedShape = RoomShape.CROSS)
                .bounds(sX2, sy, shapeBtnW, shapeBtnH).build());

        sy += 18;
        addRenderableWidget(Button.builder(Component.literal("[ ] END"), b -> selectedShape = RoomShape.END_ROOM)
                .bounds(sX1, sy, shapeBtnW, shapeBtnH).build());
        addRenderableWidget(Button.builder(Component.literal(">< AIRLOCK"), b -> selectedShape = RoomShape.AIRLOCK)
                .bounds(sX2, sy, shapeBtnW, shapeBtnH).build());

        sy += 18;
        addRenderableWidget(Button.builder(Component.literal("[^] ELEVATOR"), b -> selectedShape = RoomShape.ELEVATOR)
                .bounds(sX1, sy, shapeBtnW, shapeBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("# HUB"), b -> selectedShape = RoomShape.SQUARE)
                .bounds(sX2, sy, shapeBtnW, shapeBtnH).build());

        // Rotation Button
        sy += 22;
        addRenderableWidget(Button.builder(Component.literal("ROTATE: " + (selectedRotation * 90) + "°"), b -> {
            selectedRotation = (selectedRotation + 1) % 4;
            b.setMessage(Component.literal("ROTATE: " + (selectedRotation * 90) + "°"));
        }).bounds(sX1, sy, 140, 18).build());

        // Generator Toggle
        sy += 20;
        addRenderableWidget(Button.builder(Component.literal("GENERATOR: " + (selectedHasGen ? "YES" : "NO")), b -> {
            selectedHasGen = !selectedHasGen;
            b.setMessage(Component.literal("GENERATOR: " + (selectedHasGen ? "YES" : "NO")));
        }).bounds(sX1, sy, 140, 18).build());

        // Camera Picker Cycle
        sy += 22;
        addRenderableWidget(Button.builder(Component.literal("CYCLE CAM"), b -> cycleCamera())
                .bounds(sX1, sy, 68, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+ LINK CAM"), b -> linkSelectedCamera())
                .bounds(sX2, sy, 68, 16).build());

        // Place / Update Room Action Button
        sy += 22;
        addRenderableWidget(Button.builder(Component.literal("✓ PLACE / UPDATE"), b -> placeOrUpdateRoom())
                .bounds(sX1, sy, 140, 20).build());

        // Delete Room Button
        sy += 22;
        addRenderableWidget(Button.builder(Component.literal("✗ DELETE ROOM"), b -> deleteSelectedRoom())
                .bounds(sX1, sy, 140, 18).build());

        // Auto-Link WASD Button
        sy += 20;
        addRenderableWidget(Button.builder(Component.literal("⚡ AUTO-LINK WASD"), b -> autoLinkWasd())
                .bounds(sX1, sy, 140, 18).build());

        // Clear Zone Button
        sy += 20;
        addRenderableWidget(Button.builder(Component.literal("CLEAR ZONE"), b -> clearCurrentZone())
                .bounds(sX1, sy, 140, 16).build());
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
        int canvasW = width - 168;
        int canvasH = height - 32;

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
            int canvasW = width - 168;
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
        // Deep monochrome blueprint background
        graphics.fill(0, 0, width, height, 0xFF020914);

        int canvasX = 8;
        int canvasY = 28;
        int canvasW = width - 176;
        int canvasH = height - 36;

        // Grid canvas border
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF04101E);
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + 1, 0xFF00E5FF);
        graphics.fill(canvasX, canvasY + canvasH - 1, canvasX + canvasW, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(canvasX, canvasY, canvasX + 1, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(canvasX + canvasW - 1, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF00E5FF);

        int centerX = canvasX + canvasW / 2 + gridPanX;
        int centerY = canvasY + canvasH / 2 + gridPanY;

        // Render Grid Lines
        for (int x = -15; x <= 15; x++) {
            int px = centerX + x * CELL_SIZE;
            if (px >= canvasX && px <= canvasX + canvasW) {
                graphics.fill(px, canvasY + 1, px + 1, canvasY + canvasH - 1, (x == 0) ? 0x6600E5FF : 0x2200E5FF);
            }
        }
        for (int y = -15; y <= 15; y++) {
            int py = centerY + y * CELL_SIZE;
            if (py >= canvasY && py <= canvasY + canvasH) {
                graphics.fill(canvasX + 1, py, canvasX + canvasW - 1, py + 1, (y == 0) ? 0x6600E5FF : 0x2200E5FF);
            }
        }

        // Render Placed Rooms strictly for CURRENT ZONE
        List<FacilityNetworkManager.RoomNode> zoneRooms = FacilityNetworkManager.getRoomsInZone(currentZone);
        for (FacilityNetworkManager.RoomNode r : zoneRooms) {
            int rx = centerX + r.gridX * CELL_SIZE;
            int ry = centerY + r.gridY * CELL_SIZE;

            if (rx + CELL_SIZE >= canvasX && rx <= canvasX + canvasW && ry + CELL_SIZE >= canvasY && ry <= canvasY + canvasH) {
                boolean isSelected = (r.gridX == selectedGx && r.gridY == selectedGy);
                int bgCol = isSelected ? 0xFF005577 : 0xFF0A2B42;
                int borderCol = isSelected ? 0xFF00FFFF : 0xFF00A2C7;

                graphics.fill(rx + 2, ry + 2, rx + CELL_SIZE - 2, ry + CELL_SIZE - 2, bgCol);
                graphics.fill(rx + 2, ry + 2, rx + CELL_SIZE - 2, ry + 3, borderCol);
                graphics.fill(rx + 2, ry + CELL_SIZE - 3, rx + CELL_SIZE - 2, ry + CELL_SIZE - 2, borderCol);
                graphics.fill(rx + 2, ry + 2, rx + 3, ry + CELL_SIZE - 2, borderCol);
                graphics.fill(rx + CELL_SIZE - 3, ry + 2, rx + CELL_SIZE - 2, ry + CELL_SIZE - 2, borderCol);

                // Small shape icon / text inside room
                String label = r.roomName != null && !r.roomName.isEmpty() ? r.roomName : (r.gridX + ":" + r.gridY);
                if (label.length() > 6) label = label.substring(0, 5) + ".";
                graphics.drawString(font, label, rx + 4, ry + 4, 0xFFE0F7FA, false);

                if (r.hasGenerator) {
                    graphics.drawString(font, "⚡", rx + CELL_SIZE - 10, ry + CELL_SIZE - 12, 0xFFFFCC00, false);
                }
                if (!r.linkedCameras.isEmpty()) {
                    graphics.drawString(font, "📹" + r.linkedCameras.size(), rx + 4, ry + CELL_SIZE - 12, 0xFF00FF88, false);
                }
            }
        }

        // Highlight Selected Grid Cell
        int selPx = centerX + selectedGx * CELL_SIZE;
        int selPy = centerY + selectedGy * CELL_SIZE;
        if (selPx + CELL_SIZE >= canvasX && selPx <= canvasX + canvasW && selPy + CELL_SIZE >= canvasY && selPy <= canvasY + canvasH) {
            graphics.fill(selPx, selPy, selPx + CELL_SIZE, selPy + 1, 0xFF00FF88);
            graphics.fill(selPx, selPy + CELL_SIZE - 1, selPx + CELL_SIZE, selPy + CELL_SIZE, 0xFF00FF88);
            graphics.fill(selPx, selPy, selPx + 1, selPy + CELL_SIZE, 0xFF00FF88);
            graphics.fill(selPx + CELL_SIZE - 1, selPy, selPx + CELL_SIZE, selPy + CELL_SIZE, 0xFF00FF88);
        }

        // Right Inspector Panel
        int panelX = width - 168;
        graphics.fill(panelX, canvasY, width - 8, canvasY + canvasH, 0xFF081928);
        graphics.fill(panelX, canvasY, width - 8, canvasY + 1, 0xFF00E5FF);
        graphics.fill(panelX, canvasY + canvasH - 1, width - 8, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(panelX, canvasY, panelX + 1, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(width - 9, canvasY, width - 8, canvasY + canvasH, 0xFF00E5FF);

        // Header and info
        graphics.drawString(font, "ZONE: " + currentZone.name(), 300, 10, 0xFF00E5FF, false);
        graphics.drawString(font, "SELECTED CELL: [" + selectedGx + ", " + selectedGy + "]", panelX + 8, canvasY + 6, 0xFFE0F7FA, false);

        // Show camera link info
        String camText = (selectedCamToLink != null) ?
                ("CAM: [" + selectedCamToLink.getX() + ", " + selectedCamToLink.getZ() + "]") : "CAM: (NONE AVAILABLE)";
        graphics.drawString(font, camText, panelX + 8, canvasY + 168, 0xFF00FF88, false);

        FacilityNetworkManager.RoomNode curRoom = FacilityNetworkManager.getRoomAt(currentZone, selectedGx, selectedGy);
        if (curRoom != null) {
            graphics.drawString(font, "LINKED CAMS: " + curRoom.linkedCameras.size(), panelX + 8, canvasY + 180, 0xFFE0F7FA, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
