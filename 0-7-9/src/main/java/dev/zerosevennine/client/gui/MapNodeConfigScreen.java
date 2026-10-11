package dev.zerosevennine.client.gui;

import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.RoomShape;
import dev.zerosevennine.network.C2SConfigureMapNodePacket;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class MapNodeConfigScreen extends Screen {
    private final BlockPos nodePos;
    private final String initialRoomName;
    private final int initialGridX;
    private final int initialGridY;
    private EditBox roomNameInput;
    private EditBox gridXInput;
    private EditBox gridYInput;
    private FacilityZone zone;
    private FacilityMapNodeBlockEntity.RoomType roomType;
    private RoomShape roomShape;
    private int rotation;
    private FacilityZone targetZone;
    private boolean hasGenerator;

    private Button zoneButton;
    private Button roomTypeButton;
    private Button shapeButton;
    private Button rotationButton;
    private Button targetZoneButton;
    private Button generatorButton;

    public MapNodeConfigScreen(BlockPos nodePos, String roomName, FacilityZone zone,
                               FacilityMapNodeBlockEntity.RoomType roomType, RoomShape roomShape, int rotation,
                               FacilityZone targetZone, int gridX, int gridY, boolean hasGenerator) {
        super(Component.literal("Tactical Map Node Config"));
        this.nodePos = nodePos;
        this.initialRoomName = (roomName != null && !roomName.isEmpty()) ? roomName : "Heavy Hallway";
        this.zone = zone != null ? zone : FacilityZone.HCZ;
        this.roomType = roomType != null ? roomType : FacilityMapNodeBlockEntity.RoomType.STANDARD;
        this.roomShape = roomShape != null ? roomShape : RoomShape.SQUARE;
        this.rotation = Math.floorMod(rotation, 4);
        this.targetZone = targetZone;
        this.initialGridX = gridX;
        this.initialGridY = gridY;
        this.hasGenerator = hasGenerator;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        this.roomNameInput = new EditBox(this.font, cx - 110, cy - 95, 220, 20, Component.literal("Room Name"));
        this.roomNameInput.setValue(initialRoomName);
        this.addRenderableWidget(this.roomNameInput);

        this.zoneButton = this.addRenderableWidget(Button.builder(
                Component.literal("Zone: " + zone.getCode()),
                btn -> {
                    int nextOrd = (zone.ordinal() + 1) % FacilityZone.values().length;
                    zone = FacilityZone.values()[nextOrd];
                    btn.setMessage(Component.literal("Zone: " + zone.getCode()));
                }
        ).bounds(cx - 110, cy - 70, 105, 20).build());

        this.roomTypeButton = this.addRenderableWidget(Button.builder(
                Component.literal("Type: " + roomType.getLabel()),
                btn -> {
                    int nextOrd = (roomType.ordinal() + 1) % FacilityMapNodeBlockEntity.RoomType.values().length;
                    roomType = FacilityMapNodeBlockEntity.RoomType.values()[nextOrd];
                    btn.setMessage(Component.literal("Type: " + roomType.getLabel()));
                }
        ).bounds(cx + 5, cy - 70, 105, 20).build());

        this.shapeButton = this.addRenderableWidget(Button.builder(
                Component.literal("Shape: " + roomShape.getLabel()),
                btn -> {
                    int nextOrd = (roomShape.ordinal() + 1) % RoomShape.values().length;
                    roomShape = RoomShape.values()[nextOrd];
                    btn.setMessage(Component.literal("Shape: " + roomShape.getLabel()));
                }
        ).bounds(cx - 110, cy - 45, 140, 20).build());

        this.rotationButton = this.addRenderableWidget(Button.builder(
                Component.literal("Rot: " + (rotation * 90) + "°"),
                btn -> {
                    rotation = (rotation + 1) % 4;
                    btn.setMessage(Component.literal("Rot: " + (rotation * 90) + "°"));
                }
        ).bounds(cx + 35, cy - 45, 75, 20).build());

        this.targetZoneButton = this.addRenderableWidget(Button.builder(
                Component.literal("Transition Target: " + (targetZone != null ? targetZone.getCode() : "None")),
                btn -> {
                    if (targetZone == null) {
                        targetZone = FacilityZone.LCZ;
                    } else {
                        int nextOrd = (targetZone.ordinal() + 1) % FacilityZone.values().length;
                        targetZone = FacilityZone.values()[nextOrd];
                    }
                    btn.setMessage(Component.literal("Transition Target: " + targetZone.getCode()));
                }
        ).bounds(cx - 110, cy - 20, 220, 20).build());

        this.gridXInput = new EditBox(this.font, cx - 110, cy + 5, 105, 20, Component.literal("Grid X"));
        this.gridXInput.setValue(String.valueOf(initialGridX));
        this.addRenderableWidget(this.gridXInput);

        this.gridYInput = new EditBox(this.font, cx + 5, cy + 5, 105, 20, Component.literal("Grid Y"));
        this.gridYInput.setValue(String.valueOf(initialGridY));
        this.addRenderableWidget(this.gridYInput);

        this.generatorButton = this.addRenderableWidget(Button.builder(
                Component.literal("Generator in Room: " + (hasGenerator ? "YES" : "NO")),
                btn -> {
                    hasGenerator = !hasGenerator;
                    btn.setMessage(Component.literal("Generator in Room: " + (hasGenerator ? "YES" : "NO")));
                }
        ).bounds(cx - 110, cy + 30, 220, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Save Map Node"),
                btn -> {
                    int gx = 0;
                    int gy = 0;
                    try {
                        gx = Integer.parseInt(gridXInput.getValue());
                    } catch (Exception ignored) {}
                    try {
                        gy = Integer.parseInt(gridYInput.getValue());
                    } catch (Exception ignored) {}

                    ModNetwork.CHANNEL.sendToServer(new C2SConfigureMapNodePacket(nodePos, roomNameInput.getValue(), zone, roomType, roomShape, rotation, targetZone, gx, gy, hasGenerator));
                    this.onClose();
                }
        ).bounds(cx - 110, cy + 55, 220, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int cx = width / 2;
        int cy = height / 2;

        graphics.drawCenteredString(this.font, "TACTICAL MAP NODE CONFIGURATION", cx, cy - 115, 0x00E5FF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
