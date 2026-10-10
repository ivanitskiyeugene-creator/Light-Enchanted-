package dev.zerosevennine.client.gui;

import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.facility.FacilityZone;
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
    private EditBox roomNameInput;
    private EditBox gridXInput;
    private EditBox gridYInput;
    private FacilityZone zone = FacilityZone.HCZ;
    private FacilityMapNodeBlockEntity.RoomType roomType = FacilityMapNodeBlockEntity.RoomType.STANDARD;
    private FacilityZone targetZone = null;
    private boolean hasGenerator = false;

    private Button zoneButton;
    private Button roomTypeButton;
    private Button targetZoneButton;
    private Button generatorButton;

    public MapNodeConfigScreen(BlockPos nodePos) {
        super(Component.literal("Tactical Map Node Config"));
        this.nodePos = nodePos;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        this.roomNameInput = new EditBox(this.font, cx - 110, cy - 80, 220, 20, Component.literal("Room Name"));
        this.roomNameInput.setValue("Heavy Hallway");
        this.addRenderableWidget(this.roomNameInput);

        this.zoneButton = this.addRenderableWidget(Button.builder(
                Component.literal("Zone: " + zone.getDisplayName()),
                btn -> {
                    int nextOrd = (zone.ordinal() + 1) % FacilityZone.values().length;
                    zone = FacilityZone.values()[nextOrd];
                    btn.setMessage(Component.literal("Zone: " + zone.getDisplayName()));
                }
        ).bounds(cx - 110, cy - 55, 220, 20).build());

        this.roomTypeButton = this.addRenderableWidget(Button.builder(
                Component.literal("Type: " + roomType.getLabel()),
                btn -> {
                    int nextOrd = (roomType.ordinal() + 1) % FacilityMapNodeBlockEntity.RoomType.values().length;
                    roomType = FacilityMapNodeBlockEntity.RoomType.values()[nextOrd];
                    btn.setMessage(Component.literal("Type: " + roomType.getLabel()));
                }
        ).bounds(cx - 110, cy - 30, 220, 20).build());

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
        ).bounds(cx - 110, cy - 5, 220, 20).build());

        this.gridXInput = new EditBox(this.font, cx - 110, cy + 20, 105, 20, Component.literal("Grid X"));
        this.gridXInput.setValue("0");
        this.addRenderableWidget(this.gridXInput);

        this.gridYInput = new EditBox(this.font, cx + 5, cy + 20, 105, 20, Component.literal("Grid Y"));
        this.gridYInput.setValue("0");
        this.addRenderableWidget(this.gridYInput);

        this.generatorButton = this.addRenderableWidget(Button.builder(
                Component.literal("Generator in Room: " + (hasGenerator ? "YES" : "NO")),
                btn -> {
                    hasGenerator = !hasGenerator;
                    btn.setMessage(Component.literal("Generator in Room: " + (hasGenerator ? "YES" : "NO")));
                }
        ).bounds(cx - 110, cy + 45, 220, 20).build());

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

                    ModNetwork.CHANNEL.sendToServer(new C2SConfigureMapNodePacket(nodePos, roomNameInput.getValue(), zone, roomType, targetZone, gx, gy, hasGenerator));
                    this.onClose();
                }
        ).bounds(cx - 110, cy + 72, 220, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int cx = width / 2;
        int cy = height / 2;

        graphics.fill(cx - 130, cy - 105, cx + 130, cy + 105, 0xEE111E2E);
        graphics.fill(cx - 130, cy - 105, cx + 130, cy - 103, 0xFF00E5FF);

        graphics.drawString(this.font, "TACTICAL MAP NODE CONFIG", cx - 85, cy - 95, 0x00E5FF, true);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
