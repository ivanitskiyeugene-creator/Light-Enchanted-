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
 * Beam editor. Opened by right-clicking a Light Emitter block.
 *
 * Widgets are polled every frame and the working config is applied straight
 * to the client-side block entity, so the world behind the GUI previews the
 * beam live. The config is sent to the server on Apply / Done / closing.
 */
public class LightEmitterScreen extends Screen {
    private static final double AIM_REACH = 160.0;

    private final LightEmitterBlockEntity emitter;
    private final boolean creative;
    private BeamConfig working = new BeamConfig();

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
    private Checkbox enabledBox;
    private Checkbox shadowsBox;
    private Checkbox rainbowBox;
    private Checkbox downBox;
    private Checkbox toSkyBox;
    private CycleButton<BeamShape> shapeButton;
    private EditBox hexBox;

    // creative-only offset controls (null on the regular emitter)
    private ForgeSlider offsetXSlider;
    private ForgeSlider offsetYSlider;
    private ForgeSlider offsetZSlider;

    private int previewX;
    private int previewY;
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

        int w = 150;
        int h = 20;
        int gap = 26;
        int left = this.width / 2 - 170;
        int right = this.width / 2 + 20;
        int y = 44;

        // ---- left column: numeric sliders
        redSlider = addRenderableWidget(new ForgeSlider(left, y, w, h,
                text("red").append(": "), Component.empty(), 0, 255, (working.color >> 16) & 0xFF, 1, 0, true));
        y += gap;
        greenSlider = addRenderableWidget(new ForgeSlider(left, y, w, h,
                text("green").append(": "), Component.empty(), 0, 255, (working.color >> 8) & 0xFF, 1, 0, true));
        y += gap;
        blueSlider = addRenderableWidget(new ForgeSlider(left, y, w, h,
                text("blue").append(": "), Component.empty(), 0, 255, working.color & 0xFF, 1, 0, true));
        y += gap;
        alphaSlider = addRenderableWidget(new ForgeSlider(left, y, w, h,
                text("alpha").append(": "), Component.empty(), 0, 255, working.alpha, 1, 0, true));
        y += gap + 6;
        widthSlider = addRenderableWidget(new ForgeSlider(left, y, w, h,
                text("width").append(": "), Component.empty(), 0.05, 2.0, working.width, 0.05, 2, true));
        y += gap;
        spreadSlider = addRenderableWidget(new ForgeSlider(left, y, w, h,
                text("spread").append(": "), Component.empty(), 0.1, 4.0, working.endWidth, 0.1, 1, true));
        y += gap;
        glowSlider = addRenderableWidget(new ForgeSlider(left, y, w, h,
                text("glow").append(": "), Component.empty(), 0.0, 2.0, working.glow, 0.1, 1, true));
        y += gap;
        heightSlider = addRenderableWidget(new ForgeSlider(left, y, w, h,
                text("height").append(": "), Component.empty(), BeamConfig.MIN_HEIGHT, BeamConfig.MAX_HEIGHT,
                working.height, 1, 0, true));
        y += gap;
        pulseSlider = addRenderableWidget(new ForgeSlider(left, y, w, h,
                text("pulse").append(": "), Component.empty(), 0.0, 2.0, working.pulse, 0.1, 1, true));
        y += gap;
        rotateSlider = addRenderableWidget(new ForgeSlider(left, y, w, h,
                text("rotation").append(": "), Component.empty(), 0.0, 2.0, working.rotation, 0.1, 1, true));

        // ---- right column: toggles, shape, hex
        int ry = 44;
        enabledBox = addRenderableWidget(new Checkbox(right, ry, 20, 20, text("enabled"), working.enabled));
        ry += gap;
        shadowsBox = addRenderableWidget(new Checkbox(right, ry, 20, 20, text("shadows"), working.shadows));
        ry += gap;
        rainbowBox = addRenderableWidget(new Checkbox(right, ry, 20, 20, text("rainbow"), working.rainbow));
        ry += gap;
        downBox = addRenderableWidget(new Checkbox(right, ry, 20, 20, text("down"), working.down));
        ry += gap;
        toSkyBox = addRenderableWidget(new Checkbox(right, ry, 20, 20, text("to_sky"), working.toSky));
        ry += gap + 4;
        shapeButton = addRenderableWidget(CycleButton.builder(BeamShape::displayName)
                .withValues(BeamShape.values())
                .withInitialValue(working.shape)
                .create(right, ry, w, h, text("shape"), (button, value) -> {
                }));
        ry += gap + 4;
        hexBox = new EditBox(this.font, right, ry, w, h, text("hex"));
        hexBox.setMaxLength(7);
        hexBox.setValue(String.format("#%06X", working.color & 0xFFFFFF));
        hexBox.setResponder(this::onHexChanged);
        addRenderableWidget(hexBox);
        ry += gap + 6;

        previewX = right;
        previewY = ry;
        ry += 34;

        // ---- target section
        addRenderableWidget(Button.builder(text("aim"), button -> aimFromCrosshair())
                .bounds(right, ry, w, h).build());
        ry += gap - 2;
        addRenderableWidget(Button.builder(text("clear_target"), button -> clearTarget())
                .bounds(right, ry, w, h).build());
        ry += gap - 2;
        targetInfoY = ry;
        ry += 18;

        // ---- creative-only: beam origin offset
        if (creative) {
            offsetXSlider = addRenderableWidget(new ForgeSlider(right, ry, w, h,
                    text("offset_x").append(": "), Component.empty(), -8.0, 8.0, working.offsetX, 0.1, 1, true));
            ry += gap;
            offsetYSlider = addRenderableWidget(new ForgeSlider(right, ry, w, h,
                    text("offset_y").append(": "), Component.empty(), -8.0, 8.0, working.offsetY, 0.1, 1, true));
            ry += gap;
            offsetZSlider = addRenderableWidget(new ForgeSlider(right, ry, w, h,
                    text("offset_z").append(": "), Component.empty(), -8.0, 8.0, working.offsetZ, 0.1, 1, true));
        }

        // ---- bottom buttons
        int by = this.height - 34;
        addRenderableWidget(Button.builder(text("done"), button -> applyAndClose())
                .bounds(this.width / 2 - 110, by, 100, 20).build());
        addRenderableWidget(Button.builder(text("apply"), button -> sendToServer())
                .bounds(this.width / 2 + 10, by, 100, 20).build());
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
            // incomplete hex input, wait for the user to finish typing
        }
    }

    /** Set the beam target to the block the player is currently looking at. */
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

    /** Read every widget into {@link #working} and update inter-widget states. */
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

    /** Live preview: mirror the working config into the client-side block entity. */
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
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);

        // color preview swatch
        int swatchColor = working.rainbow ? currentRainbowColor() : working.color;
        graphics.fill(previewX, previewY, previewX + 150, previewY + 26, 0xFF181824);
        graphics.fill(previewX + 2, previewY + 2, previewX + 148, previewY + 24, 0xFF000000 | swatchColor);

        // target info line
        Component info = working.hasTarget
                ? text("target").append(": " + String.format("%.1f, %.1f, %.1f",
                        working.targetX, working.targetY, working.targetZ))
                : text("target").append(": ").append(text("target_none"));
        graphics.drawString(this.font, info, previewX, targetInfoY + 6, 0x9A9AA8);
    }

    private int currentRainbowColor() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return working.color;
        }
        float hue = ((this.minecraft.level.getGameTime() % 720000L) * 0.02f) % 1.0f;
        if (hue < 0.0f) {
            hue += 1.0f;
        }
        // cheap HSV->RGB, matches the renderer
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
