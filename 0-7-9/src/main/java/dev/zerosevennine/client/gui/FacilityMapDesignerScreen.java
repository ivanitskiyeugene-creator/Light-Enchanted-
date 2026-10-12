package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.facility.FacilityMapAutoBuilder;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityReferenceManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.RoomShape;
import dev.zerosevennine.network.C2SEditMapRoomPacket;
import dev.zerosevennine.network.C2SRequestMapSyncPacket;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FacilityMapDesignerScreen extends Screen {
    private static final ResourceLocation SCANLINES = new ResourceLocation("zero_seven_nine", "textures/gui/scanlines.png");

    public enum GridMode {
        FULL("СЕТКА: 1.0", 4, true, false),
        TENTH("СЕТКА: 0.1", 1, true, true),
        OFF("СЕТКА: ВЫКЛ", 1, false, false);

        public final String label;
        public final int snapStep;
        public final boolean showMainGrid;
        public final boolean showSubGrid;

        GridMode(String label, int snapStep, boolean showMainGrid, boolean showSubGrid) {
            this.label = label;
            this.snapStep = snapStep;
            this.showMainGrid = showMainGrid;
            this.showSubGrid = showSubGrid;
        }

        public GridMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private FacilityZone currentZone = FacilityZone.HCZ;
    private GridMode currentGridMode = GridMode.FULL;

    // Grid Coordinates & Pan
    private int selectedGx = 0;
    private int selectedGy = 0;
    private int gridPanX = 0;
    private int gridPanY = 0;
    private static final int CELL_SIZE = 42;

    // Editor fields
    private EditBox nameEdit;
    private RoomShape selectedShape = RoomShape.STRAIGHT;
    private int selectedRotation = 0;
    private boolean selectedHasGen = false;

    // Freeform Polygon Vertices (local coords, -24 to +24)
    private final List<int[]> activePolygon = new ArrayList<>();
    private int selectedVertexIndex = 0;
    private boolean draggingVertex = false;
    private boolean draggingPreviewShape = false;

    // Dragging Room on Grid Canvas
    private boolean draggingRoom = false;
    private int dragSourceGx = 0;
    private int dragSourceGy = 0;

    // Camera Linking
    private int selectedCamIndex = 0;
    private final List<BlockPos> availableCameras = new ArrayList<>();
    private final List<BlockPos> localLinkedCameras = new ArrayList<>();

    private Button genBtn;
    private Button targetZoneBtn;
    private Button gridModeBtn;
    private Button refToggleBtn;
    private Button refAlphaBtn;
    private FacilityZone selectedTargetZone = FacilityZone.HCZ;

    // Clipboard for Room Copy & Paste (Ctrl+C / Ctrl+V)
    public static class RoomClipboard {
        public String roomName;
        public RoomShape shape;
        public int rotation;
        public boolean hasGen;
        public FacilityZone targetZone;
        public final List<int[]> polygon = new ArrayList<>();
    }
    private static RoomClipboard clipboard = null;
    private String noticeMessage = null;
    private int noticeTimer = 0;

    public FacilityMapDesignerScreen() {
        super(Component.literal("Редактор карты 079"));
    }

    public FacilityMapDesignerScreen(FacilityZone initialZone) {
        super(Component.literal("Редактор карты 079"));
        if (initialZone != null) {
            this.currentZone = initialZone;
        }
    }

    @Override
    protected void init() {
        super.init();
        refreshAvailableCameras();

        // 1. Immediately request fresh map data from server
        ModNetwork.CHANNEL.sendToServer(new C2SRequestMapSyncPacket());

        int panelW = 208;
        int panelX = width - panelW - 6;
        int panelY = 24;

        // Zone Tab Buttons at Top
        int tabW = 46;
        int tabStartX = 8;
        addRenderableWidget(Button.builder(Component.literal("LCZ"), b -> setZone(FacilityZone.LCZ))
                .bounds(tabStartX, 4, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("HCZ"), b -> setZone(FacilityZone.HCZ))
                .bounds(tabStartX + tabW + 3, 4, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("EZ"), b -> setZone(FacilityZone.EZ))
                .bounds(tabStartX + (tabW + 3) * 2, 4, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("SURF"), b -> setZone(FacilityZone.SURFACE))
                .bounds(tabStartX + (tabW + 3) * 3, 4, tabW, 18).build());

        // Grid Scale Mode Switcher (1.0 / 0.1 / OFF)
        gridModeBtn = Button.builder(Component.literal(currentGridMode.label), b -> {
            currentGridMode = currentGridMode.next();
            b.setMessage(Component.literal(currentGridMode.label));
        }).bounds(tabStartX + (tabW + 3) * 4, 4, 76, 18).build();
        addRenderableWidget(gridModeBtn);

        // Reference Image Toggle Button
        refToggleBtn = Button.builder(Component.literal(FacilityReferenceManager.isVisible() ? "РЕФ: ВКЛ" : "РЕФ: ВЫКЛ"), b -> {
            if (FacilityReferenceManager.getActiveTextureLocation() == null) {
                boolean loaded = FacilityReferenceManager.loadFirstAvailable();
                if (!loaded) {
                    showNotice("Положите карту в .minecraft/079_references/map.png");
                }
            } else {
                FacilityReferenceManager.toggleVisible();
            }
            b.setMessage(Component.literal(FacilityReferenceManager.isVisible() ? "РЕФ: ВКЛ" : "РЕФ: ВЫКЛ"));
        }).bounds(tabStartX + (tabW + 3) * 4 + 80, 4, 68, 18).build();
        addRenderableWidget(refToggleBtn);

        // Auto-Builder Button
        addRenderableWidget(Button.builder(Component.literal("⚡ АВТОСБОРКА"), b -> triggerAutoBuilder())
                .bounds(tabStartX + (tabW + 3) * 4 + 152, 4, 84, 18).build());

        // Room Name EditBox
        int sy = panelY + 12;
        nameEdit = new EditBox(font, panelX + 6, sy, panelW - 12, 16, Component.literal("Название комнаты"));
        nameEdit.setMaxLength(24);
        nameEdit.setValue("ROOM " + selectedGx + ":" + selectedGy);
        addRenderableWidget(nameEdit);

        // Load existing room data if present
        loadRoomDataAtSelection();

        // --- POINT / POLYGON TOOLS ---
        sy += 16 + 72; // Under Live Preview box
        int pBtnW = (panelW - 18) / 3;
        int pBtnH = 15;

        // Add / Remove Vertex & Center Buttons
        addRenderableWidget(Button.builder(Component.literal("+ ТОЧКА"), b -> addVertex())
                .bounds(panelX + 6, sy, pBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("- ТОЧКА"), b -> removeVertex())
                .bounds(panelX + 8 + pBtnW, sy, pBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("ЦЕНТР"), b -> centerPolygon())
                .bounds(panelX + 10 + pBtnW * 2, sy, pBtnW, pBtnH).build());

        // Scale & Dimension Tools (W-, W+, H-, H+)
        sy += 16;
        int sBtnW = (panelW - 22) / 4;
        addRenderableWidget(Button.builder(Component.literal("ШИР -"), b -> scaleWidth(0.85f))
                .bounds(panelX + 6, sy, sBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("ШИР +"), b -> scaleWidth(1.15f))
                .bounds(panelX + 8 + sBtnW, sy, sBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("ВЫС -"), b -> scaleHeight(0.85f))
                .bounds(panelX + 10 + sBtnW * 2, sy, sBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("ВЫС +"), b -> scaleHeight(1.15f))
                .bounds(panelX + 12 + sBtnW * 3, sy, sBtnW, pBtnH).build());

        // Rotate Tools
        sy += 16;
        int rBtnW = (panelW - 18) / 3;
        addRenderableWidget(Button.builder(Component.literal("↺ 45°"), b -> rotatePolygon(-45))
                .bounds(panelX + 6, sy, rBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("↻ 45°"), b -> rotatePolygon(45))
                .bounds(panelX + 8 + rBtnW, sy, rBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("↻ 90°"), b -> rotatePolygon(90))
                .bounds(panelX + 10 + rBtnW * 2, sy, rBtnW, pBtnH).build());

        // --- PRESET SHAPE BUTTONS ---
        sy += 18;
        int shapeBtnW = (panelW - 18) / 4;
        addRenderableWidget(Button.builder(Component.literal("| ПРЯМ"), b -> applyPreset(RoomShape.STRAIGHT))
                .bounds(panelX + 6, sy, shapeBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("L УГОЛ"), b -> applyPreset(RoomShape.CORNER))
                .bounds(panelX + 8 + shapeBtnW, sy, shapeBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("T Т-ОБР"), b -> applyPreset(RoomShape.T_SHAPE))
                .bounds(panelX + 10 + shapeBtnW * 2, sy, shapeBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("+ КРЕСТ"), b -> applyPreset(RoomShape.CROSS))
                .bounds(panelX + 12 + shapeBtnW * 3, sy, shapeBtnW, pBtnH).build());

        sy += 16;
        addRenderableWidget(Button.builder(Component.literal("[ ] ЗАЛ"), b -> applyPreset(RoomShape.SQUARE))
                .bounds(panelX + 6, sy, shapeBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal(">< ШЛЮЗ"), b -> applyPreset(RoomShape.AIRLOCK))
                .bounds(panelX + 8 + shapeBtnW, sy, shapeBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("[^] ЛИФТ"), b -> applyPreset(RoomShape.ELEVATOR))
                .bounds(panelX + 10 + shapeBtnW * 2, sy, shapeBtnW, pBtnH).build());
        addRenderableWidget(Button.builder(Component.literal("CHK КПП"), b -> applyPreset(RoomShape.CHECKPOINT))
                .bounds(panelX + 12 + shapeBtnW * 3, sy, shapeBtnW, pBtnH).build());

        // Generator & Target Zone Toggles
        sy += 18;
        int tBtnW = (panelW - 16) / 2;
        genBtn = Button.builder(Component.literal("ГЕН: " + (selectedHasGen ? "ВКЛ" : "ВЫКЛ")), b -> {
            selectedHasGen = !selectedHasGen;
            b.setMessage(Component.literal("ГЕН: " + (selectedHasGen ? "ВКЛ" : "ВЫКЛ")));
        }).bounds(panelX + 6, sy, tBtnW, 15).build();
        addRenderableWidget(genBtn);

        targetZoneBtn = Button.builder(Component.literal("ЦЕЛЬ: " + (selectedTargetZone != currentZone ? selectedTargetZone.name() : "НЕТ")), b -> {
            cycleTargetZone();
            b.setMessage(Component.literal("ЦЕЛЬ: " + (selectedTargetZone != currentZone ? selectedTargetZone.name() : "НЕТ")));
        }).bounds(panelX + 8 + tBtnW, sy, tBtnW, 15).build();
        addRenderableWidget(targetZoneBtn);

        // --- CAMERA LINKING TOOLS ---
        sy += 17;
        addRenderableWidget(Button.builder(Component.literal("🔗 РЕЖИМ ПРИВЯЗКИ (В МИРЕ)"), b -> startInWorldLinkMode())
                .bounds(panelX + 6, sy, panelW - 12, 16).build());

        sy += 17;
        addRenderableWidget(Button.builder(Component.literal("- ОТВЯЗАТЬ КАМЕРЫ"), b -> unlinkAllCameras())
                .bounds(panelX + 6, sy, panelW - 12, 14).build());

        // --- ACTION BUTTONS (Copy / Paste / Save / Delete) ---
        sy += 16;
        int cpBtnW = (panelW - 16) / 2;
        addRenderableWidget(Button.builder(Component.literal("📋 КОПИР. (Ctrl+C)"), b -> copyCurrentRoom())
                .bounds(panelX + 6, sy, cpBtnW, 15).build());
        addRenderableWidget(Button.builder(Component.literal("📋 ВСТАВ. (Ctrl+V)"), b -> pasteRoomAtSelection())
                .bounds(panelX + 8 + cpBtnW, sy, cpBtnW, 15).build());

        sy += 17;
        addRenderableWidget(Button.builder(Component.literal("✓ СОХРАНИТЬ КОМНАТУ"), b -> placeOrUpdateRoom())
                .bounds(panelX + 6, sy, panelW - 12, 17).build());

        sy += 19;
        addRenderableWidget(Button.builder(Component.literal("✗ УДАЛИТЬ КОМНАТУ"), b -> deleteSelectedRoom())
                .bounds(panelX + 6, sy, panelW - 12, 14).build());

        sy += 16;
        addRenderableWidget(Button.builder(Component.literal("ОЧИСТИТЬ ВСЮ ЗОНУ"), b -> clearCurrentZone())
                .bounds(panelX + 6, sy, panelW - 12, 14).build());
    }

    private void triggerAutoBuilder() {
        if (FacilityReferenceManager.getActiveBufferedImage() != null) {
            boolean success = FacilityMapAutoBuilder.parseFromScreenshot(FacilityReferenceManager.getActiveBufferedImage(), currentZone);
            if (success) {
                showNotice("СХЕМА АВТОМАТИЧЕСКИ РАСПОЗНАНА ПО СКРИНШОТУ!");
                return;
            }
        }
        FacilityMapAutoBuilder.buildCanonicalLayout(currentZone);
        showNotice("СОБРАНА КАНОНИЧНАЯ СХЕМА ЗОНЫ " + currentZone.name() + " (1:1 SCP:SL)!");
    }

    public void copyCurrentRoom() {
        clipboard = new RoomClipboard();
        clipboard.roomName = nameEdit != null ? nameEdit.getValue() : ("ROOM " + selectedGx + ":" + selectedGy);
        clipboard.shape = selectedShape;
        clipboard.rotation = selectedRotation;
        clipboard.hasGen = selectedHasGen;
        clipboard.targetZone = selectedTargetZone;
        clipboard.polygon.clear();
        for (int[] pt : activePolygon) {
            clipboard.polygon.add(new int[]{pt[0], pt[1]});
        }
        showNotice("СКОПИРОВАНО В БУФЕР: " + clipboard.roomName + " (Ctrl+C)");
    }

    public void pasteRoomAtSelection() {
        if (clipboard == null) {
            showNotice("БУФЕР ОБМЕНА ПУСТ (Сначала нажмите Ctrl+C)");
            return;
        }
        selectedShape = clipboard.shape;
        selectedRotation = clipboard.rotation;
        selectedHasGen = clipboard.hasGen;
        selectedTargetZone = clipboard.targetZone != null ? clipboard.targetZone : getDefaultTargetZone(currentZone, selectedShape);
        activePolygon.clear();
        for (int[] pt : clipboard.polygon) {
            activePolygon.add(new int[]{pt[0], pt[1]});
        }
        localLinkedCameras.clear();
        if (nameEdit != null) {
            nameEdit.setValue(clipboard.roomName);
        }
        if (genBtn != null) {
            genBtn.setMessage(Component.literal("ГЕН: " + (selectedHasGen ? "ВКЛ" : "ВЫКЛ")));
        }
        if (targetZoneBtn != null) {
            targetZoneBtn.setMessage(Component.literal("ЦЕЛЬ: " + (selectedTargetZone != currentZone ? selectedTargetZone.name() : "НЕТ")));
        }
        placeOrUpdateRoom();
        showNotice("ВСТАВЛЕНО В [" + selectedGx + ", " + selectedGy + "] (Ctrl+V)");
    }

    private void showNotice(String msg) {
        this.noticeMessage = msg;
        this.noticeTimer = 80;
    }

    public void restoreState(FacilityZone zone, int gx, int gy, String name,
                             RoomShape shape, int rot, boolean hasGen,
                             List<int[]> polygon, List<BlockPos> cameras) {
        this.currentZone = zone != null ? zone : FacilityZone.HCZ;
        this.selectedGx = gx;
        this.selectedGy = gy;
        this.selectedShape = shape != null ? shape : RoomShape.SQUARE;
        this.selectedRotation = rot;
        this.selectedHasGen = hasGen;
        if (this.nameEdit != null) {
            this.nameEdit.setValue(name != null ? name : ("ROOM " + gx + ":" + gy));
        }
        this.activePolygon.clear();
        if (polygon != null) {
            for (int[] pt : polygon) {
                this.activePolygon.add(new int[]{pt[0], pt[1]});
            }
        }
        this.localLinkedCameras.clear();
        if (cameras != null) {
            for (BlockPos p : cameras) {
                this.localLinkedCameras.add(p.immutable());
            }
        }
    }

    private void startInWorldLinkMode() {
        dev.zerosevennine.client.handler.ClientCameraLinker.enterLinkMode(
                currentZone, selectedGx, selectedGy, nameEdit.getValue(),
                selectedShape, selectedRotation, selectedHasGen,
                activePolygon, localLinkedCameras
        );
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
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
    }

    private void unlinkAllCameras() {
        localLinkedCameras.clear();
    }

    private void loadRoomDataAtSelection() {
        localLinkedCameras.clear();
        activePolygon.clear();

        FacilityNetworkManager.RoomNode room = FacilityNetworkManager.getRoomAt(currentZone, selectedGx, selectedGy);
        if (room != null) {
            nameEdit.setValue(room.roomName != null ? room.roomName : "");
            selectedShape = room.roomShape != null ? room.roomShape : RoomShape.SQUARE;
            selectedRotation = room.rotation;
            selectedHasGen = room.hasGenerator;
            selectedTargetZone = room.targetZone != null ? room.targetZone : getDefaultTargetZone(currentZone, selectedShape);
            localLinkedCameras.addAll(room.linkedCameras);

            if (!room.customPolygon.isEmpty()) {
                for (int[] pt : room.customPolygon) {
                    activePolygon.add(new int[]{pt[0], pt[1]});
                }
            } else {
                activePolygon.addAll(FacilityNetworkManager.generateDefaultVertices(selectedShape, selectedRotation));
            }
        } else {
            nameEdit.setValue("ROOM " + selectedGx + ":" + selectedGy);
            selectedShape = RoomShape.STRAIGHT;
            selectedRotation = 0;
            selectedHasGen = false;
            selectedTargetZone = getDefaultTargetZone(currentZone, selectedShape);
            activePolygon.addAll(FacilityNetworkManager.generateDefaultVertices(selectedShape, selectedRotation));
        }

        selectedVertexIndex = 0;
        if (genBtn != null) {
            genBtn.setMessage(Component.literal("ГЕН: " + (selectedHasGen ? "ВКЛ" : "ВЫКЛ")));
        }
        if (targetZoneBtn != null) {
            targetZoneBtn.setMessage(Component.literal("ЦЕЛЬ: " + (selectedTargetZone != currentZone ? selectedTargetZone.name() : "НЕТ")));
        }
    }

    private void cycleTargetZone() {
        FacilityZone[] zones = FacilityZone.values();
        int next = (selectedTargetZone.ordinal() + 1) % (zones.length + 1);
        if (next == zones.length) {
            selectedTargetZone = currentZone;
        } else {
            selectedTargetZone = zones[next];
        }
    }

    private FacilityZone getDefaultTargetZone(FacilityZone zone, RoomShape shape) {
        if (shape == RoomShape.CHECKPOINT) {
            return zone == FacilityZone.HCZ ? FacilityZone.EZ : (zone == FacilityZone.EZ ? FacilityZone.HCZ : zone);
        }
        if (shape == RoomShape.ELEVATOR) {
            if (zone == FacilityZone.LCZ) return FacilityZone.HCZ;
            if (zone == FacilityZone.HCZ) return FacilityZone.LCZ;
            if (zone == FacilityZone.EZ) return FacilityZone.SURFACE;
            if (zone == FacilityZone.SURFACE) return FacilityZone.EZ;
        }
        return zone;
    }

    private void applyPreset(RoomShape shape) {
        this.selectedShape = shape;
        this.selectedTargetZone = getDefaultTargetZone(currentZone, shape);
        if (targetZoneBtn != null) {
            targetZoneBtn.setMessage(Component.literal("ЦЕЛЬ: " + (selectedTargetZone != currentZone ? selectedTargetZone.name() : "НЕТ")));
        }
        activePolygon.clear();
        activePolygon.addAll(FacilityNetworkManager.generateDefaultVertices(shape, selectedRotation));
        selectedVertexIndex = 0;
    }

    private void centerPolygon() {
        if (activePolygon.isEmpty()) return;
        int sumX = 0, sumY = 0;
        for (int[] pt : activePolygon) {
            sumX += pt[0];
            sumY += pt[1];
        }
        int avgX = sumX / activePolygon.size();
        int avgY = sumY / activePolygon.size();

        for (int[] pt : activePolygon) {
            pt[0] = Math.max(-28, Math.min(28, pt[0] - avgX));
            pt[1] = Math.max(-28, Math.min(28, pt[1] - avgY));
        }
    }

    private void addVertex() {
        if (activePolygon.isEmpty()) {
            activePolygon.addAll(FacilityNetworkManager.generateDefaultVertices(selectedShape, selectedRotation));
            return;
        }
        int next = (selectedVertexIndex + 1) % activePolygon.size();
        int[] p1 = activePolygon.get(selectedVertexIndex);
        int[] p2 = activePolygon.get(next);
        int midX = (p1[0] + p2[0]) / 2;
        int midY = (p1[1] + p2[1]) / 2;

        activePolygon.add(selectedVertexIndex + 1, new int[]{midX, midY});
        selectedVertexIndex++;
    }

    private void removeVertex() {
        if (activePolygon.size() > 3) {
            activePolygon.remove(selectedVertexIndex);
            if (selectedVertexIndex >= activePolygon.size()) {
                selectedVertexIndex = 0;
            }
        }
    }

    private void scaleWidth(float factor) {
        for (int[] pt : activePolygon) {
            pt[0] = Math.max(-28, Math.min(28, Math.round(pt[0] * factor)));
        }
    }

    private void scaleHeight(float factor) {
        for (int[] pt : activePolygon) {
            pt[1] = Math.max(-28, Math.min(28, Math.round(pt[1] * factor)));
        }
    }

    private void rotatePolygon(int deg) {
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        for (int[] pt : activePolygon) {
            int nx = (int) Math.round(pt[0] * cos - pt[1] * sin);
            int ny = (int) Math.round(pt[0] * sin + pt[1] * cos);
            pt[0] = Math.max(-28, Math.min(28, nx));
            pt[1] = Math.max(-28, Math.min(28, ny));
        }
    }

    private void placeOrUpdateRoom() {
        String name = nameEdit.getValue().trim();
        if (name.isEmpty()) name = "ROOM " + selectedGx + ":" + selectedGy;

        BlockPos primaryCam = !localLinkedCameras.isEmpty() ? localLinkedCameras.get(0) : null;

        ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                C2SEditMapRoomPacket.Action.PLACE_OR_UPDATE,
                currentZone, selectedGx, selectedGy, name, selectedShape, selectedRotation,
                selectedHasGen, selectedTargetZone, primaryCam, selectedGx, selectedGy, activePolygon
        ));

        // Optimistically update locally
        FacilityNetworkManager.RoomNode node = new FacilityNetworkManager.RoomNode(
                new BlockPos(selectedGx * 16, 64, selectedGy * 16), name, currentZone, null, selectedShape, selectedRotation, selectedTargetZone, selectedGx, selectedGy
        );
        node.hasGenerator = selectedHasGen;
        node.linkedCameras.addAll(localLinkedCameras);
        node.customPolygon.clear();
        for (int[] pt : activePolygon) {
            node.customPolygon.add(new int[]{pt[0], pt[1]});
        }
        FacilityNetworkManager.registerRoomNode(node);
        showNotice("КОМНАТА СОХРАНЕНА: " + name);
    }

    private void deleteSelectedRoom() {
        ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                C2SEditMapRoomPacket.Action.DELETE,
                currentZone, selectedGx, selectedGy, "", RoomShape.SQUARE, 0, false, currentZone, null
        ));
        FacilityNetworkManager.removeRoom(currentZone, selectedGx, selectedGy);
        loadRoomDataAtSelection();
        showNotice("КОМНАТА УДАЛЕНА ИЗ БАЗЫ");
    }

    private void clearCurrentZone() {
        List<FacilityNetworkManager.RoomNode> rooms = new ArrayList<>(FacilityNetworkManager.getRoomsInZone(currentZone));
        for (FacilityNetworkManager.RoomNode r : rooms) {
            ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                    C2SEditMapRoomPacket.Action.DELETE,
                    currentZone, r.gridX, r.gridY, "", RoomShape.SQUARE, 0, false, currentZone, null
            ));
            FacilityNetworkManager.removeRoom(currentZone, r.gridX, r.gridY);
        }
        loadRoomDataAtSelection();
        showNotice("ЗОНА " + currentZone.name() + " ПОЛНОСТЬЮ ОЧИЩЕНА");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = 208;
        int canvasX = 8;
        int canvasY = 24;
        int canvasW = width - panelW - 20;
        int canvasH = height - 30;

        // Click on blueprint canvas
        if (mouseX >= canvasX && mouseX <= canvasX + canvasW && mouseY >= canvasY && mouseY <= canvasY + canvasH) {
            int centerX = canvasX + canvasW / 2 + gridPanX;
            int centerY = canvasY + canvasH / 2 + gridPanY;

            int clickGx = (int) Math.floor((mouseX - centerX) / (double) CELL_SIZE);
            int clickGy = (int) Math.floor((mouseY - centerY) / (double) CELL_SIZE);

            if (button == 0) { // LMB -> Select or start drag
                selectedGx = clickGx;
                selectedGy = clickGy;
                loadRoomDataAtSelection();

                FacilityNetworkManager.RoomNode clickedRoom = FacilityNetworkManager.getRoomAt(currentZone, clickGx, clickGy);
                if (clickedRoom != null) {
                    draggingRoom = true;
                    dragSourceGx = clickGx;
                    dragSourceGy = clickGy;
                }
                return true;
            }
        }

        // Live preview interaction
        int panelX = width - panelW - 6;
        int prevX = panelX + 6;
        int prevY = 24 + 30;
        int prevW = panelW - 12;
        int prevH = 68;

        if (mouseX >= prevX && mouseX <= prevX + prevW && mouseY >= prevY && mouseY <= prevY + prevH) {
            int originX = prevX + prevW / 2;
            int originY = prevY + prevH / 2;

            if (button == 0) {
                for (int i = 0; i < activePolygon.size(); i++) {
                    int[] pt = activePolygon.get(i);
                    int vx = originX + pt[0];
                    int vy = originY + pt[1];
                    if (Math.abs(mouseX - vx) <= 6 && Math.abs(mouseY - vy) <= 6) {
                        selectedVertexIndex = i;
                        draggingVertex = true;
                        return true;
                    }
                }
                draggingPreviewShape = true;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingVertex) draggingVertex = false;
        if (draggingPreviewShape) draggingPreviewShape = false;

        if (draggingRoom) {
            draggingRoom = false;
            int panelW = 208;
            int canvasW = width - panelW - 20;
            int canvasH = height - 34;

            if (mouseX >= 8 && mouseX <= 8 + canvasW && mouseY >= 24 && mouseY <= 24 + canvasH) {
                int centerX = 8 + canvasW / 2 + gridPanX;
                int centerY = 24 + canvasH / 2 + gridPanY;

                int dropGx = (int) Math.floor((mouseX - centerX) / (double) CELL_SIZE);
                int dropGy = (int) Math.floor((mouseY - centerY) / (double) CELL_SIZE);

                if (dropGx != dragSourceGx || dropGy != dragSourceGy) {
                    ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                            C2SEditMapRoomPacket.Action.MOVE_ROOM,
                            currentZone, dragSourceGx, dragSourceGy, "", RoomShape.SQUARE, 0, false, currentZone, null,
                            dropGx, dropGy, null
                    ));
                    FacilityNetworkManager.moveRoom(currentZone, dragSourceGx, dragSourceGy, dropGx, dropGy);
                    selectedGx = dropGx;
                    selectedGy = dropGy;
                    loadRoomDataAtSelection();
                }
            }
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingVertex && selectedVertexIndex >= 0 && selectedVertexIndex < activePolygon.size()) {
            int panelW = 208;
            int panelX = width - panelW - 6;
            int prevX = panelX + 6;
            int prevY = 24 + 30;
            int prevW = panelW - 12;
            int prevH = 68;
            int originX = prevX + prevW / 2;
            int originY = prevY + prevH / 2;

            int rawX = (int) Math.round(mouseX - originX);
            int rawY = (int) Math.round(mouseY - originY);

            int snap = currentGridMode.snapStep;
            int newLocalX = Math.round((float) rawX / snap) * snap;
            int newLocalY = Math.round((float) rawY / snap) * snap;

            newLocalX = Math.max(-28, Math.min(28, newLocalX));
            newLocalY = Math.max(-28, Math.min(28, newLocalY));

            activePolygon.get(selectedVertexIndex)[0] = newLocalX;
            activePolygon.get(selectedVertexIndex)[1] = newLocalY;
            return true;
        }

        if (draggingPreviewShape) {
            int dx = (int) Math.round(dragX);
            int dy = (int) Math.round(dragY);

            for (int[] pt : activePolygon) {
                pt[0] = Math.max(-28, Math.min(28, pt[0] + dx));
                pt[1] = Math.max(-28, Math.min(28, pt[1] + dy));
            }
            return true;
        }

        if (button == 1 || button == 2) {
            gridPanX += (int) dragX;
            gridPanY += (int) dragY;
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameEdit != null && this.nameEdit.isFocused()) {
            if (this.nameEdit.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (Screen.hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C) {
            copyCurrentRoom();
            return true;
        }

        if (Screen.hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
            pasteRoomAtSelection();
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            selectedRotation = (selectedRotation + 1) % 4;
            rotatePolygon(90);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int panelW = 208;
        int canvasX = 8;
        int canvasY = 24;
        int canvasW = width - panelW - 20;
        int canvasH = height - 30;

        // Blueprint Grid canvas background
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF030D1A);
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + 1, 0xFF00E5FF);
        graphics.fill(canvasX, canvasY + canvasH - 1, canvasX + canvasW, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(canvasX, canvasY, canvasX + 1, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(canvasX + canvasW - 1, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF00E5FF);

        int centerX = canvasX + canvasW / 2 + gridPanX;
        int centerY = canvasY + canvasH / 2 + gridPanY;

        // 1. Render Reference Image Underlay if enabled
        if (FacilityReferenceManager.isVisible() && FacilityReferenceManager.getActiveTextureLocation() != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float refAlpha = FacilityReferenceManager.getAlpha();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, refAlpha);

            int refW = (int) (canvasW * FacilityReferenceManager.getScale());
            int refH = (int) (canvasH * FacilityReferenceManager.getScale());
            int refX = centerX - refW / 2 + FacilityReferenceManager.getOffsetX();
            int refY = centerY - refH / 2 + FacilityReferenceManager.getOffsetY();

            graphics.blit(FacilityReferenceManager.getActiveTextureLocation(), refX, refY, 0, 0, refW, refH, refW, refH);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // 2. Render Coordinate Grid
        if (currentGridMode.showSubGrid) {
            int subStep = CELL_SIZE / 10;
            for (int x = -100; x <= 100; x++) {
                int px = centerX + x * subStep;
                if (px >= canvasX && px <= canvasX + canvasW) {
                    graphics.fill(px, canvasY + 1, px + 1, canvasY + canvasH - 1, 0x0D00E5FF);
                }
            }
            for (int y = -100; y <= 100; y++) {
                int py = centerY + y * subStep;
                if (py >= canvasY && py <= canvasY + canvasH) {
                    graphics.fill(canvasX + 1, py, canvasX + canvasW - 1, py + 1, 0x0D00E5FF);
                }
            }
        }

        if (currentGridMode.showMainGrid) {
            for (int x = -20; x <= 20; x++) {
                int px = centerX + x * CELL_SIZE;
                if (px >= canvasX && px <= canvasX + canvasW) {
                    graphics.fill(px, canvasY + 1, px + 1, canvasY + canvasH - 1, (x == 0) ? 0x6600E5FF : 0x1A00E5FF);
                }
            }
            for (int y = -20; y <= 20; y++) {
                int py = centerY + y * CELL_SIZE;
                if (py >= canvasY && py <= canvasY + canvasH) {
                    graphics.fill(canvasX + 1, py, canvasX + canvasW - 1, py + 1, (y == 0) ? 0x6600E5FF : 0x1A00E5FF);
                }
            }
        }

        // 3. Render All Rooms in Current Zone using Unified Exact Renderer
        List<FacilityNetworkManager.RoomNode> zoneRooms = FacilityNetworkManager.getRoomsInZone(currentZone);
        for (FacilityNetworkManager.RoomNode r : zoneRooms) {
            int rx = centerX + r.gridX * CELL_SIZE;
            int ry = centerY + r.gridY * CELL_SIZE;

            if (rx + CELL_SIZE >= canvasX && rx <= canvasX + canvasW && ry + CELL_SIZE >= canvasY && ry <= canvasY + canvasH) {
                boolean isSelected = (r.gridX == selectedGx && r.gridY == selectedGy);
                int midX = rx + CELL_SIZE / 2;
                int midY = ry + CELL_SIZE / 2;

                FacilityNetworkManager.renderRoomNode(graphics, font, midX, midY, r, isSelected, isSelected, true);
            }
        }

        // 4. Highlight Selected Cell Cursor
        int selPx = centerX + selectedGx * CELL_SIZE;
        int selPy = centerY + selectedGy * CELL_SIZE;
        if (selPx + CELL_SIZE >= canvasX && selPx <= canvasX + canvasW && selPy + CELL_SIZE >= canvasY && selPy <= canvasY + canvasH) {
            graphics.fill(selPx, selPy, selPx + CELL_SIZE, selPy + 1, 0xFF00FF88);
            graphics.fill(selPx, selPy + CELL_SIZE - 1, selPx + CELL_SIZE, selPy + CELL_SIZE, 0xFF00FF88);
            graphics.fill(selPx, selPy + 1, selPx + 1, selPy + CELL_SIZE, 0xFF00FF88);
            graphics.fill(selPx + CELL_SIZE - 1, selPy, selPx + CELL_SIZE, selPy + CELL_SIZE, 0xFF00FF88);
        }

        // 5. Right Inspector Panel
        int panelX = width - panelW - 6;
        graphics.fill(panelX, canvasY, panelX + panelW, canvasY + canvasH, 0xEE020914);
        graphics.fill(panelX, canvasY, panelX + panelW, canvasY + 1, 0xFF00E5FF);
        graphics.fill(panelX, canvasY + canvasH - 1, panelX + panelW, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(panelX, canvasY, panelX + 1, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(panelX + panelW - 1, canvasY, panelX + panelW, canvasY + canvasH, 0xFF00E5FF);

        // Header Title in Inspector
        String inspectorTitle = "[ НАСТРОЙКА: " + currentZone.name() + " (" + selectedGx + ", " + selectedGy + ") ]";
        graphics.drawString(font, inspectorTitle, panelX + (panelW - font.width(inspectorTitle)) / 2, canvasY + 4, 0xFF00E5FF, true);

        // Live Preview Box in Inspector
        int prevX = panelX + 6;
        int prevY = canvasY + 30;
        int prevW = panelW - 12;
        int prevH = 68;

        graphics.fill(prevX, prevY, prevX + prevW, prevY + prevH, 0xFF010610);
        graphics.fill(prevX, prevY, prevX + prevW, prevY + 1, 0xFF00E5FF);
        graphics.fill(prevX, prevY + prevH - 1, prevX + prevW, prevY + prevH, 0xFF00E5FF);
        graphics.fill(prevX, prevY, prevX + 1, prevY + prevH, 0xFF00E5FF);
        graphics.fill(prevX + prevW - 1, prevY, prevX + prevW, prevY + prevH, 0xFF00E5FF);

        int prevOriginX = prevX + prevW / 2;
        int prevOriginY = prevY + prevH / 2;

        FacilityNetworkManager.fillPolygonFast(graphics, prevOriginX, prevOriginY, activePolygon, 0xFF1D78B2, 0xFF00FFFF);

        for (int i = 0; i < activePolygon.size(); i++) {
            int[] pt = activePolygon.get(i);
            int vx = prevOriginX + pt[0];
            int vy = prevOriginY + pt[1];
            int dotCol = (i == selectedVertexIndex) ? 0xFF00FF88 : 0xFFFFCC00;
            graphics.fill(vx - 2, vy - 2, vx + 2, vy + 2, dotCol);
        }

        // Notification popup toast
        if (noticeMessage != null && noticeTimer > 0) {
            noticeTimer--;
            int nw = font.width(noticeMessage) + 16;
            int nx = (width - nw) / 2;
            int ny = height - 28;
            graphics.fill(nx, ny, nx + nw, ny + 16, 0xEE002233);
            graphics.fill(nx, ny, nx + nw, ny + 1, 0xFF00FF88);
            graphics.fill(nx, ny + 15, nx + nw, ny + 16, 0xFF00FF88);
            graphics.drawString(font, noticeMessage, nx + 8, ny + 4, 0xFF00FF88, true);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
