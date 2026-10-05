package dev.lightenchanted.client.gui;

import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.beam.BeamShape;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import dev.lightenchanted.network.ModNetwork;
import dev.lightenchanted.network.UpdateLightEmitterPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.widget.ForgeSlider;

/**
 * Beam editor GUI with a compact, responsive two-column layout.
 */
public class LightEmitterScreen extends Screen {
    private static final double AIM_REACH = 160.0;

    private final LightEmitterBlockEntity emitter;
    private final boolean creative;
    private BeamConfig working = new BeamConfig();

    // Left column sliders
    private ForgeSlider redSlider;
    private ForgeSlider greenSlider;
    private ForgeSlider blueSlider;
    private ForgeSlider alphaSlider;
    private ForgeSlider widthSlider;
    private ForgeSlider spreadSlider;
    private ForgeSlider glowSlider;
    private ForgeSlider heightSlider;
    private ForgeSlider pulseSlider;
    private ForgeSlider rotateSlider;

    // Right column toggles & widgets
    private Checkbox enabledBox;
    private Checkbox shadowsBox;
    private Checkbox rainbowBox;
    private Checkbox downBox;
    private Checkbox toSkyBox;
    private CycleButton<BeamShape> shapeButton;
    private EditBox hexBox;

    // Creative offset sliders
    private ForgeSlider offsetXSlider;
    private ForgeSlider offsetYSlider;
    private ForgeSlider offsetZSlider;

    private int previewX;
    private int previewY;
    private int targetInfoX;
    private int targetInfoY;
    private boolean syncingWidgets;

    public LightEmitterScreen(LightEmitterBlockEntity emitter) {
        super(Component.translatable("screen.lightenchanted.title"));
        this.emitter = emitter;
        this.creative = emitter.isCreative();
    }

    private static MutableComponent text(String key) {
        return Component.translatable("screen.lightenchanted." + key);
    }

    @Override
    protected void init() {
        working = new BeamConfig(emitter.getConfig());

        int colW = 154;
        int btnH = 18;
        int gap = 21;
        int left = this.width / 2 - 165;
        int right = this.width / 2 + 10;
        int y = 32;

        // ---- Left Column: Numeric Sliders
        redSlider = addRenderableWidget(new ForgeSlider(left, y, colW, btnH,
                text("red").append(": "), Component.empty(), 0, 255, (working.color >> 16) & 0xFF, 1, 0, true));
        y += gap;
        greenSlider = addRenderableWidget(new ForgeSlider(left, y, colW, btnH,
                text("green").append(": "), Component.empty(), 0, 255, (working.color >> 8) & 0xFF, 1, 0, true));
        y += gap;
        blueSlider = addRenderableWidget(new ForgeSlider(left, y, colW, btnH,
                text("blue").append(": "), Component.empty(), 0, 255, working.color & 0xFF, 1, 0, true));
        y += gap;
        alphaSlider = addRenderableWidget(new ForgeSlider(left, y, colW, btnH,
                text("alpha").append(": "), Component.empty(), 0, 255, working.alpha, 1, 0, true));
        y += gap + 2;
        widthSlider = addRenderableWidget(new ForgeSlider(left, y, colW, btnH,
                text("width").append(": "), Component.empty(), 0.05, 2.0, working.width, 0.05, 2, true));
        y += gap;
        spreadSlider = addRenderableWidget(new ForgeSlider(left, y, colW, btnH,
                text("spread").append(": "), Component.empty(), 0.1, 4.0, working.endWidth, 0.1, 1, true));
        y += gap;
        glowSlider = addRenderableWidget(new ForgeSlider(left, y, colW, btnH,
                text("glow").append(": "), Component.empty(), 0.0, 2.0, working.glow, 0.1, 1, true));
        y += gap;
        heightSlider = addRenderableWidget(new ForgeSlider(left, y, colW, btnH,
                text("height").append(": "), Component.empty(), BeamConfig.MIN_HEIGHT, BeamConfig.MAX_HEIGHT,
                working.height, 1, 0, true));
        y += gap;
        pulseSlider = addRenderableWidget(new ForgeSlider(left, y, colW, btnH,
                text("pulse").append(": "), Component.empty(), 0.0, 2.0, working.pulse, 0.1, 1, true));
        y += gap;
        rotateSlider = addRenderableWidget(new ForgeSlider(left, y, colW, btnH,
                text("rotation").append(": "), Component.empty(), 0.0, 2.0, working.rotation, 0.1, 1, true));

        // ---- Right Column: Compact Toggles (2 per row), Shape, Hex, Target, Offsets
        int ry = 32;
        int halfW = (colW - 4) / 2;

        enabledBox = addRenderableWidget(new Checkbox(right, ry, halfW + 4, 18, text("enabled"), working.enabled));
        shadowsBox = addRenderableWidget(new Checkbox(right + halfW + 4, ry, halfW + 4, 18, text("shadows"), working.shadows));
        ry += gap;

        rainbowBox = addRenderableWidget(new Checkbox(right, ry, halfW + 4, 18, text("rainbow"), working.rainbow));
        downBox = addRenderableWidget(new Checkbox(right + halfW + 4, ry, halfW + 4, 18, text("down"), working.down));
        ry += gap;

        toSkyBox = addRenderableWidget(new Checkbox(right, ry, colW, 18, text("to_sky"), working.toSky));
        ry += gap;

        shapeButton = addRenderableWidget(CycleButton.builder(BeamShape::displayName)
                .withValues(BeamShape.values())
                .withInitialValue(working.shape)
                .create(right, ry, colW, btnH, text("shape"), (button, value) -> {
                }));
        ry += gap;

        hexBox = new EditBox(this.font, right, ry, colW - 36, btnH, text("hex"));
        hexBox.setMaxLength(7);
        hexBox.setValue(String.format("#%06X", working.color & 0xFFFFFF));
        hexBox.setResponder(this::onHexChanged);
        addRenderableWidget(hexBox);

        previewX = right + colW - 30;
        previewY = ry;
        ry += gap + 2;

        // Target buttons: Aim and Clear Target side-by-side
        addRenderableWidget(Button.builder(text("aim"), button -> aimFromCrosshair())
                .bounds(right, ry, halfW, btnH).build());
        addRenderableWidget(Button.builder(text("clear_target"), button -> clearTarget())
                .bounds(right + halfW + 4, ry, halfW, btnH).build());
        ry += gap - 2;

        targetInfoX = right;
        targetInfoY = ry;
        ry += 14;

        // Creative-only offset sliders
        if (creative) {
            offsetXSlider = addRenderableWidget(new ForgeSlider(right, ry, colW, btnH,
                    text("offset_x").append(": "), Component.empty(), -8.0, 8.0, working.offsetX, 0.1, 1, true));
            ry += gap;
            offsetYSlider = addRenderableWidget(new ForgeSlider(right, ry, colW, btnH,
                    text("offset_y").append(": "), Component.empty(), -8.0, 8.0, working.offsetY, 0.1, 1, true));
            ry += gap;
            offsetZSlider = addRenderableWidget(new ForgeSlider(right, ry, colW, btnH,
                    text("offset_z").append(": "), Component.empty(), -8.0, 8.0, working.offsetZ, 0.1, 1, true));
        }

        // ---- Bottom Action Buttons
        int by = this.height - 28;
        addRenderableWidget(Button.builder(text("done"), button -> applyAndClose())
                .bounds(this.width / 2 - 105, by, 100, 20).build());
        addRenderableWidget(Button.builder(text("apply"), button -> sendToServer())
                .bounds(this.width / 2 + 5, by, 100, 20).build());
    }

    private void onHexChanged(String raw) {
        if (syncingWidgets) {
            return;
        }
        String t = raw.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() != 6) {
            return;
        }
        try {
            int rgb = Integer.parseInt(t, 16) & 0xFFFFFF;
            working.color = rgb;
            syncingWidgets = true;
            redSlider.setValue((rgb >> 16) & 0xFF);
            greenSlider.setValue((rgb >> 8) & 0xFF);
            blueSlider.setValue(rgb & 0xFF);
            syncingWidgets = false;
        } catch (NumberFormatException ignored) {
        }
    }

    private void aimFromCrosshair() {
        Player player = this.minecraft != null ? this.minecraft.player : null;
        if (player == null) {
            return;
        }
        HitResult hit = player.pick(AIM_REACH, 0.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 loc = hit.getLocation();
            working.hasTarget = true;
            working.targetX = loc.x;
            working.targetY = loc.y;
            working.targetZ = loc.z;
            applyPreview();
        }
    }

    private void clearTarget() {
        working.hasTarget = false;
        applyPreview();
    }

    private void pollWidgets() {
        if (syncingWidgets) {
            return;
        }
        working.enabled = enabledBox.selected();
        working.shadows = shadowsBox.selected();
        working.rainbow = rainbowBox.selected();
        working.down = downBox.selected();
        working.toSky = toSkyBox.selected();
        working.shape = shapeButton.getValue();
        working.color = ((int) redSlider.getValue() << 16)
                | ((int) greenSlider.getValue() << 8)
                | (int) blueSlider.getValue();
        working.alpha = (int) alphaSlider.getValue();
        working.width = (float) widthSlider.getValue();
        working.endWidth = (float) spreadSlider.getValue();
        working.glow = (float) glowSlider.getValue();
        working.height = heightSlider.getValueInt();
        working.pulse = (float) pulseSlider.getValue();
        working.rotation = (float) rotateSlider.getValue();
        if (creative && offsetXSlider != null) {
            working.offsetX = (float) offsetXSlider.getValue();
            working.offsetY = (float) offsetYSlider.getValue();
            working.offsetZ = (float) offsetZSlider.getValue();
        }
        working.sanitize();

        boolean colorEditable = !working.rainbow;
        redSlider.active = colorEditable;
        greenSlider.active = colorEditable;
        blueSlider.active = colorEditable;
        hexBox.active = colorEditable;
        heightSlider.active = !working.toSky && !working.hasTarget;
        spreadSlider.active = working.shape == BeamShape.CONE;
        downBox.active = !working.hasTarget;
        toSkyBox.active = !working.hasTarget;

        if (!hexBox.isFocused()) {
            syncingWidgets = true;
            hexBox.setValue(String.format("#%06X", working.color & 0xFFFFFF));
            syncingWidgets = false;
        }
    }

    private void applyPreview() {
        emitter.getConfig().copyFrom(working);
    }

    private void sendToServer() {
        if (emitter.getLevel() == null) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(
                new UpdateLightEmitterPacket(emitter.getBlockPos(), new BeamConfig(working)));
    }

    private void applyAndClose() {
        sendToServer();
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        sendToServer();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        pollWidgets();
        applyPreview();

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        // Color preview swatch next to Hex box
        int swatchColor = working.rainbow ? currentRainbowColor() : working.color;
        graphics.fill(previewX - 1, previewY - 1, previewX + 31, previewY + 19, 0xFF181824);
        graphics.fill(previewX, previewY, previewX + 30, previewY + 18, 0xFF000000 | swatchColor);

        // Target coordinates info text
        Component info = working.hasTarget
                ? text("target").append(": " + String.format("%.1f, %.1f, %.1f",
                        working.targetX, working.targetY, working.targetZ))
                : text("target").append(": ").append(text("target_none"));
        graphics.drawString(this.font, info, targetInfoX, targetInfoY, 0x9A9AA8);
    }

    private int currentRainbowColor() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return working.color;
        }
        float hue = ((this.minecraft.level.getGameTime() % 720000L) * 0.02f) % 1.0f;
        if (hue < 0.0f) {
            hue += 1.0f;
        }
        int sector = (int) (hue * 6.0f) % 6;
        float f = hue * 6.0f - (int) (hue * 6.0f);
        float q = 1.0f - f;
        float r, g, b;
        switch (sector) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
