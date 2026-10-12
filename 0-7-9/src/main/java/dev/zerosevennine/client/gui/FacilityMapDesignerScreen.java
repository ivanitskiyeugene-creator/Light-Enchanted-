package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.client.handler.ClientCameraLinker;
import dev.zerosevennine.facility.FacilityMapImageParser;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.RoomPrefab;
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

import java.awt.image.BufferedImage;
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
    private Button refFileBtn;
    private Button refAlphaBtn;
    private Button prefabBtn;
    private FacilityZone selectedTargetZone = FacilityZone.HCZ;

    // Prefab Catalog
    private int currentPrefabIndex = 0;

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
        ReferenceOverlayManager.refreshFileList();

        int panelW = 208;
        int panelX = width - panelW - 6;
        int panelY = 24;

        // Zone Tab Buttons at Top
        int tabW = 48;
        int tabStartX = 8;
        addRenderableWidget(Button.builder(Component.literal("LCZ"), b -> setZone(FacilityZone.LCZ))
                .bounds(tabStartX, 4, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("HCZ"), b -> setZone(FacilityZone.HCZ))
                .bounds(tabStartX + tabW + 2, 4, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("EZ"), b -> setZone(FacilityZone.EZ))
                .bounds(tabStartX + (tabW + 2) * 2, 4, tabW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("SURF"), b -> setZone(FacilityZone.SURFACE))
                .bounds(tabStartX + (tabW + 2) * 3, 4, tabW, 18).build());

        // Grid Scale Mode Switcher (1.0 / 0.1 / OFF)
        gridModeBtn = Button.builder(Component.literal(currentGridMode.label), b -> {
            currentGridMode = currentGridMode.next();
            b.setMessage(Component.literal(currentGridMode.label));
        }).bounds(tabStartX + (tabW + 2) * 4, 4, 72, 18).build();
        addRenderableWidget(gridModeBtn);

        // Reference Overlay Controls (Top Bar)
        int refX = tabStartX + (tabW + 2) * 4 + 76;
        refToggleBtn = Button.builder(Component.literal("ПОДЛОЖКА: " + (ReferenceOverlayManager.enabled ? "ВКЛ" : "ВЫКЛ")), b -> {
            ReferenceOverlayManager.enabled = !ReferenceOverlayManager.enabled;
            b.setMessage(Component.literal("ПОДЛОЖКА: " + (ReferenceOverlayManager.enabled ? "ВКЛ" : "ВЫКЛ")));
        }).bounds(refX, 4, 88, 18).build();
        addRenderableWidget(refToggleBtn);

        refFileBtn = Button.builder(Component.literal("ФАЙЛ: " + ReferenceOverlayManager.getActiveFileName()), b -> {
            ReferenceOverlayManager.cycleNextFile();
            b.setMessage(Component.literal("ФАЙЛ: " + ReferenceOverlayManager.getActiveFileName()));
        }).bounds(refX + 90, 4, 110, 18).build();
        addRenderableWidget(refFileBtn);

        refAlphaBtn = Button.builder(Component.literal((int)(ReferenceOverlayManager.alpha * 100) + "%"), b -> {
            float[] alphas = {0.25f, 0.5f, 0.75f, 1.0f};
            float nextA = 0.5f;
            for (int i = 0; i < alphas.length; i++) {
                if (Math.abs(ReferenceOverlayManager.alpha - alphas[i]) < 0.05f) {
                    nextA = alphas[(i + 1) % alphas.length];
                    break;
                }
            }
            ReferenceOverlayManager.alpha = nextA;
            b.setMessage(Component.literal((int)(ReferenceOverlayManager.alpha * 100) + "%"));
        }).bounds(refX + 202, 4, 34, 18).build();
        addRenderableWidget(refAlphaBtn);

        // Auto-Build by Screenshot Button
        addRenderableWidget(Button.builder(Component.literal("⚡ АВТОСБОРКА"), b -> triggerAutoBuild())
                .bounds(refX + 238, 4, 86, 18).build());

        // Room Name EditBox
        int sy = panelY + 12;
        nameEdit = new EditBox(font, panelX + 6, sy, panelW - 12, 16, Component.literal("Название комнаты"));
        nameEdit.setMaxLength(24);
        nameEdit.setValue("ROOM " + selectedGx + ":" + selectedGy);
        addRenderableWidget(nameEdit);

        // Load existing room data if present
        loadRoomDataAtSelection();

        // --- PREFABS CATALOG TOOL ---
        sy += 16 + 72; // Under Live Preview box
        int pfbW = (panelW - 18) / 3;
        addRenderableWidget(Button.builder(Component.literal("◀ ПРЕФАБ"), b -> cyclePrefab(-1))
                .bounds(panelX + 6, sy, pfbW, 15).build());

        List<RoomPrefab> prefabs = RoomPrefab.getPrefabsForZone(currentZone);
        String pfbTitle = !prefabs.isEmpty() ? prefabs.get(Math.min(currentPrefabIndex, prefabs.size() - 1)).defaultName : "ПРЕФАБЫ";
        prefabBtn = Button.builder(Component.literal(pfbTitle), b -> applyCurrentPrefab())
                .bounds(panelX + 8 + pfbW, sy, pfbW, 15).build();
        addRenderableWidget(prefabBtn);

        addRenderableWidget(Button.builder(Component.literal("ПРЕФАБ ▶"), b -> cyclePrefab(1))
                .bounds(panelX + 10 + pfbW * 2, sy, pfbW, 15).build());

        // --- POINT / POLYGON TOOLS ---
        sy += 17;
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

    private void cyclePrefab(int delta) {
        List<RoomPrefab> prefabs = RoomPrefab.getPrefabsForZone(currentZone);
        if (prefabs.isEmpty()) return;
        currentPrefabIndex = (currentPrefabIndex + delta + prefabs.size()) % prefabs.size();
        RoomPrefab p = prefabs.get(currentPrefabIndex);
        if (prefabBtn != null) {
            prefabBtn.setMessage(Component.literal(p.defaultName));
        }
        applyCurrentPrefab();
    }

    private void applyCurrentPrefab() {
        List<RoomPrefab> prefabs = RoomPrefab.getPrefabsForZone(currentZone);
        if (prefabs.isEmpty() || currentPrefabIndex >= prefabs.size()) return;
        RoomPrefab p = prefabs.get(currentPrefabIndex);

        this.selectedShape = p.shape;
        this.selectedRotation = p.defaultRotation;
        this.selectedTargetZone = p.targetZone;
        this.nameEdit.setValue(p.defaultName);

        this.activePolygon.clear();
        List<int[]> pts = FacilityNetworkManager.generateDefaultVertices(p.shape, p.defaultRotation);
        for (int[] pt : pts) {
            this.activePolygon.add(new int[]{pt[0], pt[1]});
        }

        if (targetZoneBtn != null) {
            targetZoneBtn.setMessage(Component.literal("ЦЕЛЬ: " + (selectedTargetZone != currentZone ? selectedTargetZone.name() : "НЕТ")));
        }

        placeOrUpdateRoom();
        showNotice("ПРЕФАБ ПРИМЕНЕН: " + p.defaultName + " (" + p.description + ")");
    }

    private void triggerAutoBuild() {
        BufferedImage img = ReferenceOverlayManager.getBufferedImage();
        if (img == null) {
            showNotice("ОШИБКА: Поместите .png в папку 079_references и выберите файл!");
            return;
        }

        FacilityMapImageParser.ParseResult res = FacilityMapImageParser.parseMapFromImage(img, currentZone);
        if (!res.success) {
            showNotice("ОШИБКА: " + res.message);
            return;
        }

        // Apply all parsed rooms
        FacilityNetworkManager.clearZone(currentZone);
        for (FacilityNetworkManager.RoomNode r : res.rooms) {
            FacilityNetworkManager.registerRoomNode(r);
            ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                    C2SEditMapRoomPacket.Action.PLACE_OR_UPDATE,
                    currentZone, r.gridX, r.gridY, r.roomName, r.roomShape, r.rotation,
                    r.hasGenerator, r.targetZone, null, r.gridX, r.gridY, r.customPolygon
            ));
        }

        autoLinkWasd();
        loadRoomDataAtSelection();
        showNotice("⚡ " + res.message);
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
        if (this.genBtn != null) {
            this.genBtn.setMessage(Component.literal("ГЕН: " + (selectedHasGen ? "ВКЛ" : "ВЫКЛ")));
        }
    }

    private void refreshAvailableCameras() {
        availableCameras.clear();
        for (Map.Entry<BlockPos, CameraBlockEntity> entry : FacilityNetworkManager.getCameras().entrySet()) {
            if (entry.getValue().getZone() == currentZone) {
                availableCameras.add(entry.getKey());
            }
        }
    }

    private void loadRoomDataAtSelection() {
        FacilityNetworkManager.RoomNode node = FacilityNetworkManager.getRoomAt(currentZone, selectedGx, selectedGy);
        activePolygon.clear();
        localLinkedCameras.clear();

        if (node != null) {
            nameEdit.setValue(node.roomName);
            selectedShape = node.roomShape != null ? node.roomShape : RoomShape.SQUARE;
            selectedRotation = node.rotation;
            selectedHasGen = node.hasGenerator;
            selectedTargetZone = node.targetZone != null ? node.targetZone : currentZone;
            localLinkedCameras.addAll(node.linkedCameras);

            for (int[] pt : node.getEffectivePolygon()) {
                activePolygon.add(new int[]{pt[0], pt[1]});
            }
        } else {
            nameEdit.setValue("ROOM " + selectedGx + ":" + selectedGy);
            selectedShape = RoomShape.SQUARE;
            selectedRotation = 0;
            selectedHasGen = false;
            selectedTargetZone = getDefaultTargetZone(currentZone, selectedShape);
            applyPreset(RoomShape.SQUARE);
        }

        if (genBtn != null) {
            genBtn.setMessage(Component.literal("ГЕН: " + (selectedHasGen ? "ВКЛ" : "ВЫКЛ")));
        }
        if (targetZoneBtn != null) {
            targetZoneBtn.setMessage(Component.literal("ЦЕЛЬ: " + (selectedTargetZone != currentZone ? selectedTargetZone.name() : "НЕТ")));
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

    private void cycleTargetZone() {
        FacilityZone[] zones = FacilityZone.values();
        int next = (selectedTargetZone.ordinal() + 1) % zones.length;
        selectedTargetZone = zones[next];
    }

    private void setZone(FacilityZone z) {
        this.currentZone = z;
        refreshAvailableCameras();
        currentPrefabIndex = 0;
        List<RoomPrefab> prefabs = RoomPrefab.getPrefabsForZone(currentZone);
        if (prefabBtn != null && !prefabs.isEmpty()) {
            prefabBtn.setMessage(Component.literal(prefabs.get(0).defaultName));
        }
        loadRoomDataAtSelection();
    }

    private void applyPreset(RoomShape shape) {
        this.selectedShape = shape;
        this.selectedTargetZone = getDefaultTargetZone(currentZone, shape);
        if (targetZoneBtn != null) {
            targetZoneBtn.setMessage(Component.literal("ЦЕЛЬ: " + (selectedTargetZone != currentZone ? selectedTargetZone.name() : "НЕТ")));
        }
        this.activePolygon.clear();
        List<int[]> defaultPts = FacilityNetworkManager.generateDefaultVertices(shape, selectedRotation);
        for (int[] pt : defaultPts) {
            this.activePolygon.add(new int[]{pt[0], pt[1]});
        }
    }

    private void addVertex() {
        if (activePolygon.isEmpty()) {
            activePolygon.add(new int[]{0, 0});
            selectedVertexIndex = 0;
            return;
        }
        int[] cur = activePolygon.get(selectedVertexIndex);
        int nextIdx = (selectedVertexIndex + 1) % activePolygon.size();
        int[] next = activePolygon.get(nextIdx);
        int mx = (cur[0] + next[0]) / 2;
        int my = (cur[1] + next[1]) / 2;

        activePolygon.add(nextIdx, new int[]{mx, my});
        selectedVertexIndex = nextIdx;
    }

    private void removeVertex() {
        if (activePolygon.size() > 3) {
            activePolygon.remove(selectedVertexIndex);
            if (selectedVertexIndex >= activePolygon.size()) {
                selectedVertexIndex = activePolygon.size() - 1;
            }
        }
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
            pt[0] -= avgX;
            pt[1] -= avgY;
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
    }

    private void deleteSelectedRoom() {
        ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                C2SEditMapRoomPacket.Action.DELETE,
                currentZone, selectedGx, selectedGy, "", RoomShape.SQUARE, 0, false, currentZone, null
        ));
        FacilityNetworkManager.removeRoom(currentZone, selectedGx, selectedGy);
        loadRoomDataAtSelection();
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

    private void startInWorldLinkMode() {
        ClientCameraLinker.startLinking(this.currentZone, this.selectedGx, this.selectedGy,
                this.nameEdit.getValue(), this.selectedShape, this.selectedRotation,
                this.selectedHasGen, this.activePolygon, this.localLinkedCameras);
        this.onClose();
    }

    private void unlinkAllCameras() {
        localLinkedCameras.clear();
        placeOrUpdateRoom();
        showNotice("ВСЕ КАМЕРЫ ОТВЯЗАНЫ");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelW = 208;
        int panelX = width - panelW - 6;
        int canvasW = width - panelW - 20;
        int canvasH = height - 34;

        // 1. Check Live Preview Box Point / Shape Clicking
        int prevX = panelX + 6;
        int prevY = 24 + 30;
        int prevW = panelW - 12;
        int prevH = 68;

        if (mouseX >= prevX && mouseX <= prevX + prevW && mouseY >= prevY && mouseY <= prevY + prevH) {
            int originX = prevX + prevW / 2;
            int originY = prevY + prevH / 2;

            if (button == 0) { // LMB
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
                // Clicked on background in preview -> drag entire shape
                draggingPreviewShape = true;
                return true;
            } else if (button == 1 || button == 2) { // RMB/MMB drag entire shape
                draggingPreviewShape = true;
                return true;
            }
        }

        // 2. Check Blueprint Grid Canvas Clicking & Dragging
        if (mouseX >= 8 && mouseX <= 8 + canvasW && mouseY >= 24 && mouseY <= 24 + canvasH) {
            int centerX = 8 + canvasW / 2 + gridPanX;
            int centerY = 24 + canvasH / 2 + gridPanY;

            int clickGx = (int) Math.floor((mouseX - centerX) / (double) CELL_SIZE);
            int clickGy = (int) Math.floor((mouseY - centerY) / (double) CELL_SIZE);

            if (button == 0) {
                // If clicked an existing room, prepare potential room drag
                FacilityNetworkManager.RoomNode existing = FacilityNetworkManager.getRoomAt(currentZone, clickGx, clickGy);
                if (existing != null) {
                    draggingRoom = true;
                    dragSourceGx = clickGx;
                    dragSourceGy = clickGy;
                }
                selectedGx = clickGx;
                selectedGy = clickGy;
                loadRoomDataAtSelection();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (draggingRoom) {
                int panelW = 208;
                int canvasW = width - panelW - 20;
                int centerX = 8 + canvasW / 2 + gridPanX;
                int centerY = 24 + (height - 34) / 2 + gridPanY;

                int targetGx = (int) Math.floor((mouseX - centerX) / (double) CELL_SIZE);
                int targetGy = (int) Math.floor((mouseY - centerY) / (double) CELL_SIZE);

                if (targetGx != dragSourceGx || targetGy != dragSourceGy) {
                    FacilityNetworkManager.RoomNode src = FacilityNetworkManager.getRoomAt(currentZone, dragSourceGx, dragSourceGy);
                    if (src != null) {
                        FacilityNetworkManager.removeRoom(currentZone, dragSourceGx, dragSourceGy);
                        ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                                C2SEditMapRoomPacket.Action.MOVE_ROOM,
                                currentZone, dragSourceGx, dragSourceGy, src.roomName, src.roomShape,
                                src.rotation, src.hasGenerator, src.targetZone,
                                !src.linkedCameras.isEmpty() ? src.linkedCameras.get(0) : null,
                                targetGx, targetGy, src.customPolygon
                        ));

                        src.gridX = targetGx;
                        src.gridY = targetGy;
                        FacilityNetworkManager.registerRoomNode(src);

                        selectedGx = targetGx;
                        selectedGy = targetGy;
                        loadRoomDataAtSelection();
                        showNotice("КОМНАТА ПЕРЕМЕЩЕНА В [" + targetGx + ", " + targetGy + "]");
                    }
                }
                draggingRoom = false;
            }
            draggingVertex = false;
            draggingPreviewShape = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int panelW = 208;
        int panelX = width - panelW - 6;

        // Preview box point dragging
        if (draggingVertex && !activePolygon.isEmpty() && selectedVertexIndex < activePolygon.size()) {
            int prevX = panelX + 6;
            int prevY = 24 + 30;
            int originX = prevX + (panelW - 12) / 2;
            int originY = prevY + 34;

            int rawX = (int) (mouseX - originX);
            int rawY = (int) (mouseY - originY);

            int snap = currentGridMode.snapStep;
            int snappedX = Math.round((float) rawX / snap) * snap;
            int snappedY = Math.round((float) rawY / snap) * snap;

            int[] pt = activePolygon.get(selectedVertexIndex);
            pt[0] = Math.max(-28, Math.min(28, snappedX));
            pt[1] = Math.max(-28, Math.min(28, snappedY));
            return true;
        }

        // Preview box entire shape dragging
        if (draggingPreviewShape && !activePolygon.isEmpty()) {
            int snap = currentGridMode.snapStep;
            int dx = Math.round((float) dragX / snap) * snap;
            int dy = Math.round((float) dragY / snap) * snap;

            for (int[] pt : activePolygon) {
                pt[0] = Math.max(-28, Math.min(28, pt[0] + dx));
                pt[1] = Math.max(-28, Math.min(28, pt[1] + dy));
            }
            return true;
        }

        // Canvas pan
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

        // Room Copy (Ctrl+C)
        if (Screen.hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C) {
            copyCurrentRoom();
            return true;
        }

        // Room Paste (Ctrl+V)
        if (Screen.hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
            pasteRoomAtSelection();
            return true;
        }

        // Room Delete (Delete / Backspace when not typing)
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (this.nameEdit == null || !this.nameEdit.isFocused()) {
                deleteSelectedRoom();
                showNotice("КОМНАТА УДАЛЕНА ИЗ [" + selectedGx + ", " + selectedGy + "]");
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF020914);

        int panelW = 208;
        int canvasX = 8;
        int canvasY = 24;
        int canvasW = width - panelW - 20;
        int canvasH = height - 30;

        // Blueprint Grid canvas
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF030D1A);
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + 1, 0xFF00E5FF);
        graphics.fill(canvasX, canvasY + canvasH - 1, canvasX + canvasW, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(canvasX, canvasY, canvasX + 1, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(canvasX + canvasW - 1, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF00E5FF);

        int centerX = canvasX + canvasW / 2 + gridPanX;
        int centerY = canvasY + canvasH / 2 + gridPanY;

        // 1. Render Reference Overlay (if enabled)
        if (ReferenceOverlayManager.enabled && ReferenceOverlayManager.getTextureLocation() != null) {
            ResourceLocation refLoc = ReferenceOverlayManager.getTextureLocation();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, ReferenceOverlayManager.alpha);

            int imgW = ReferenceOverlayManager.getImageWidth();
            int imgH = ReferenceOverlayManager.getImageHeight();
            int drawW = (int) (imgW * ReferenceOverlayManager.scale);
            int drawH = (int) (imgH * ReferenceOverlayManager.scale);
            int imgX = centerX - drawW / 2 + ReferenceOverlayManager.panX;
            int imgY = centerY - drawH / 2 + ReferenceOverlayManager.panY;

            graphics.blit(refLoc, imgX, imgY, 0, 0, drawW, drawH, drawW, drawH);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // 2. Render Coordinate Grid based on gridMode
        if (currentGridMode.showSubGrid) { // 1/10 Subgrid lines
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

        if (currentGridMode.showMainGrid) { // Main grid lines
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

        // Render All Rooms in Current Zone on Blueprint Canvas
        List<FacilityNetworkManager.RoomNode> zoneRooms = FacilityNetworkManager.getRoomsInZone(currentZone);
        for (FacilityNetworkManager.RoomNode r : zoneRooms) {
            int rx = centerX + r.gridX * CELL_SIZE;
            int ry = centerY + r.gridY * CELL_SIZE;

            if (rx + CELL_SIZE >= canvasX && rx <= canvasX + canvasW && ry + CELL_SIZE >= canvasY && ry <= canvasY + canvasH) {
                boolean isSelected = (r.gridX == selectedGx && r.gridY == selectedGy);
                int bgCol = isSelected ? 0xFF1D78B2 : 0xFF125884;
                int frameCol = isSelected ? 0xFF00FFFF : 0xFF186494;
                int scanlineCol = 0x2200E5FF;

                int midX = rx + CELL_SIZE / 2;
                int midY = ry + CELL_SIZE / 2;

                List<int[]> poly = r.getEffectivePolygon();
                FacilityNetworkManager.fillPolygonWithScanlines(graphics, midX, midY, poly, bgCol, scanlineCol, frameCol);

                // Room Name Title
                renderFormattedTitle(graphics, font, r.roomName, midX, midY, 0xFFE0F7FA);

                if (r.hasGenerator) {
                    graphics.drawString(font, "⚡", rx + 2, ry + 2, 0xFFFFCC00, false);
                }
                if (!r.linkedCameras.isEmpty()) {
                    graphics.drawString(font, "📷" + r.linkedCameras.size(), rx + CELL_SIZE - 18, ry + 2, 0xFF00FF88, false);
                }
            }
        }

        // Render Selection Box on Grid
        int selX = centerX + selectedGx * CELL_SIZE;
        int selY = centerY + selectedGy * CELL_SIZE;
        if (selX + CELL_SIZE >= canvasX && selX <= canvasX + canvasW && selY + CELL_SIZE >= canvasY && selY <= canvasY + canvasH) {
            graphics.fill(selX, selY, selX + CELL_SIZE, selY + 1, 0xFF00FF88);
            graphics.fill(selX, selY + CELL_SIZE - 1, selX + CELL_SIZE, selY + CELL_SIZE, 0xFF00FF88);
            graphics.fill(selX, selY, selX + 1, selY + CELL_SIZE, 0xFF00FF88);
            graphics.fill(selX + CELL_SIZE - 1, selY, selX + CELL_SIZE, selY + CELL_SIZE, 0xFF00FF88);
        }

        // Render Right Properties Panel
        int panelX = width - panelW - 6;
        graphics.fill(panelX, canvasY, panelX + panelW, canvasY + canvasH, 0xEE020B16);
        graphics.fill(panelX, canvasY, panelX + panelW, canvasY + 1, 0xFF00E5FF);
        graphics.fill(panelX, canvasY + canvasH - 1, panelX + panelW, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(panelX, canvasY, panelX + 1, canvasY + canvasH, 0xFF00E5FF);
        graphics.fill(panelX + panelW - 1, canvasY, panelX + panelW, canvasY + canvasH, 0xFF00E5FF);

        graphics.drawString(font, "СВОЙСТВА ПОМЕЩЕНИЯ", panelX + 8, canvasY + 4, 0xFF00E5FF, true);

        // Render Live Preview Box with Vertices
        int prevX = panelX + 6;
        int prevY = canvasY + 30;
        int prevW = panelW - 12;
        int prevH = 68;

        graphics.fill(prevX, prevY, prevX + prevW, prevY + prevH, 0xFF01060D);
        graphics.fill(prevX, prevY, prevX + prevW, prevY + 1, 0xFF00E5FF);
        graphics.fill(prevX, prevY + prevH - 1, prevX + prevW, prevY + prevH, 0xFF00E5FF);
        graphics.fill(prevX, prevY, prevX + 1, prevY + prevH, 0xFF00E5FF);
        graphics.fill(prevX + prevW - 1, prevY, prevX + prevW, prevY + prevH, 0xFF00E5FF);

        int originX = prevX + prevW / 2;
        int originY = prevY + prevH / 2;

        // Preview axis crosshair
        graphics.fill(originX - 16, originY, originX + 16, originY + 1, 0x3300E5FF);
        graphics.fill(originX, originY - 16, originX + 1, originY + 16, 0x3300E5FF);

        // Fill Live Polygon in preview
        if (activePolygon.size() >= 3) {
            FacilityNetworkManager.fillPolygonWithScanlines(graphics, originX, originY, activePolygon, 0xFF1D78B2, 0x2200E5FF, 0xFF00FFFF);
        }

        // Draw Interactive Vertices
        for (int i = 0; i < activePolygon.size(); i++) {
            int[] pt = activePolygon.get(i);
            int vx = originX + pt[0];
            int vy = originY + pt[1];

            int vCol = (i == selectedVertexIndex) ? 0xFF00FF88 : 0xFF00E5FF;
            graphics.fill(vx - 2, vy - 2, vx + 2, vy + 2, vCol);
            graphics.fill(vx - 1, vy - 1, vx + 1, vy + 1, 0xFFFFFFFF);
        }

        // Render Feedback Notice Banner (if active)
        if (noticeMessage != null && noticeTimer > 0) {
            noticeTimer--;
            int nw = font.width(noticeMessage) + 16;
            int nx = (canvasX + canvasW - nw) / 2;
            int ny = canvasY + 8;
            graphics.fill(nx, ny, nx + nw, ny + 16, 0xEE001B2E);
            graphics.fill(nx, ny, nx + nw, ny + 1, 0xFF00FF88);
            graphics.fill(nx, ny + 15, nx + nw, ny + 16, 0xFF00FF88);
            graphics.drawString(font, noticeMessage, nx + 8, ny + 4, 0xFFE0F7FA, true);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderFormattedTitle(GuiGraphics graphics, Font font, String name, int midX, int midY, int color) {
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim().toUpperCase();

        String[] parts = name.split("\\s+");
        if (parts.length == 1) {
            int tw = font.width(name);
            graphics.drawString(font, name, midX - (tw / 2), midY - 4, color, false);
        } else if (parts.length == 2) {
            int tw1 = font.width(parts[0]);
            int tw2 = font.width(parts[1]);
            graphics.drawString(font, parts[0], midX - (tw1 / 2), midY - 8, color, false);
            graphics.drawString(font, parts[1], midX - (tw2 / 2), midY + 1, color, false);
        } else {
            String line1 = parts[0];
            String line2 = parts[1] + " " + parts[2];
            int tw1 = font.width(line1);
            int tw2 = font.width(line2);
            graphics.drawString(font, line1, midX - (tw1 / 2), midY - 8, color, false);
            graphics.drawString(font, line2, midX - (tw2 / 2), midY + 1, color, false);
        }
    }
}
