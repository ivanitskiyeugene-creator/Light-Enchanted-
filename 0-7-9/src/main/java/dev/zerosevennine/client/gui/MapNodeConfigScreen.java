package dev.zerosevennine.client.gui;

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
    private boolean hasGenerator = false;

    private Button zoneButton;
    private Button generatorButton;

    public MapNodeConfigScreen(BlockPos nodePos) {
        super(Component.literal("Tactical Map Node Config"));
        this.nodePos = nodePos;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        this.roomNameInput = new EditBox(this.font, cx - 100, cy - 60, 200, 20, Component.literal("Room Name"));
        this.roomNameInput.setValue("Heavy Hallway");
        this.addRenderableWidget(this.roomNameInput);

        this.zoneButton = this.addRenderableWidget(Button.builder(
                Component.literal("Zone: " + zone.getDisplayName()),
                btn -> {
                    int nextOrd = (zone.ordinal() + 1) % FacilityZone.values().length;
                    zone = FacilityZone.values()[nextOrd];
                    btn.setMessage(Component.literal("Zone: " + zone.getDisplayName()));
                }
        ).bounds(cx - 100, cy - 35, 200, 20).build());

        this.gridXInput = new EditBox(this.font, cx - 100, cy - 10, 95, 20, Component.literal("Grid X"));
        this.gridXInput.setValue("0");
        this.addRenderableWidget(this.gridXInput);

        this.gridYInput = new EditBox(this.font, cx + 5, cy - 10, 95, 20, Component.literal("Grid Y"));
        this.gridYInput.setValue("0");
        this.addRenderableWidget(this.gridYInput);

        this.generatorButton = this.addRenderableWidget(Button.builder(
                Component.literal("Generator in Room: " + (hasGenerator ? "YES" : "NO")),
                btn -> {
                    hasGenerator = !hasGenerator;
                    btn.setMessage(Component.literal("Generator in Room: " + (hasGenerator ? "YES" : "NO")));
                }
        ).bounds(cx - 100, cy + 15, 200, 20).build());

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

                    ModNetwork.CHANNEL.sendToServer(new C2SConfigureMapNodePacket(nodePos, roomNameInput.getValue(), zone, gx, gy, hasGenerator));
                    this.onClose();
                }
        ).bounds(cx - 100, cy + 45, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int cx = width / 2;
        int cy = height / 2;

        graphics.fill(cx - 120, cy - 85, cx + 120, cy + 80, 0xEE111E2E);
        graphics.fill(cx - 120, cy - 85, cx + 120, cy - 83, 0xFF00E5FF);

        graphics.drawString(this.font, "TACTICAL MAP NODE CONFIG", cx - 85, cy - 75, 0x00E5FF, true);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
