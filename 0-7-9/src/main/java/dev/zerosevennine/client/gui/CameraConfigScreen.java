package dev.zerosevennine.client.gui;

import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.network.C2SConfigureCameraPacket;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class CameraConfigScreen extends Screen {
    private final BlockPos cameraPos;
    private final String initialRoomName;
    private EditBox roomNameInput;
    private FacilityZone zone;
    private Button zoneButton;

    public CameraConfigScreen(BlockPos cameraPos, String initialRoomName, FacilityZone initialZone) {
        super(Component.literal("Camera Configuration"));
        this.cameraPos = cameraPos;
        this.initialRoomName = (initialRoomName != null && !initialRoomName.isEmpty()) ? initialRoomName : "Heavy Hallway 01";
        this.zone = initialZone != null ? initialZone : FacilityZone.HCZ;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        this.roomNameInput = new EditBox(this.font, cx - 100, cy - 40, 200, 20, Component.literal("Room Name"));
        this.roomNameInput.setValue(initialRoomName);
        this.addRenderableWidget(this.roomNameInput);

        this.zoneButton = this.addRenderableWidget(Button.builder(
                Component.literal("Zone: " + zone.getDisplayName()),
                btn -> {
                    int nextOrd = (zone.ordinal() + 1) % FacilityZone.values().length;
                    zone = FacilityZone.values()[nextOrd];
                    btn.setMessage(Component.literal("Zone: " + zone.getDisplayName()));
                }
        ).bounds(cx - 100, cy - 10, 200, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Save & Link"),
                btn -> {
                    ModNetwork.CHANNEL.sendToServer(new C2SConfigureCameraPacket(cameraPos, roomNameInput.getValue(), zone));
                    this.onClose();
                }
        ).bounds(cx - 100, cy + 25, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int cx = width / 2;
        int cy = height / 2;

        graphics.fill(cx - 120, cy - 70, cx + 120, cy + 65, 0xEE111E2E);
        graphics.fill(cx - 120, cy - 70, cx + 120, cy - 68, 0xFF00E5FF);

        graphics.drawString(this.font, "CAMERA CONFIGURATION", cx - 75, cy - 60, 0x00E5FF, true);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
