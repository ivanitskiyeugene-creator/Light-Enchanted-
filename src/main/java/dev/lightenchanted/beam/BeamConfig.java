package dev.lightenchanted.beam;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * Full description of a single light beam: color, shape and effects.
 * Mutable POJO; always run through {@link #sanitize()} before trusting.
 */
public class BeamConfig {
    public static final float MIN_WIDTH = 0.05f;
    public static final float MAX_WIDTH = 2.0f;
    public static final int MIN_HEIGHT = 2;
    public static final int MAX_HEIGHT = 160;

    /** Beam on/off. */
    public boolean enabled = true;
    /** RGB color, 0xRRGGBB. Ignored while {@link #rainbow} is on. */
    public int color = 0x67C7FF;
    /** Beam geometry. */
    public BeamShape shape = BeamShape.CLASSIC;
    /** Beam diameter in blocks (fractional). */
    public float width = 0.6f;
    /** Extra glow shell strength, 0..2. */
    public float glow = 1.0f;
    /** Beam length in blocks. Ignored while {@link #toSky} is on. */
    public int height = 24;
    /** Stretch the beam up to the build limit. */
    public boolean toSky = false;
    /** Opacity, 0..255. */
    public int alpha = 230;
    /** Pulse speed, 0 = steady beam. */
    public float pulse = 0.0f;
    /** Rotation speed, 0 = static. */
    public float rotation = 0.0f;
    /** Cycle through the hue spectrum instead of using {@link #color}. */
    public boolean rainbow = false;

    public BeamConfig() {
    }

    public BeamConfig(BeamConfig other) {
        copyFrom(other);
    }

    public void copyFrom(BeamConfig other) {
        this.enabled = other.enabled;
        this.color = other.color;
        this.shape = other.shape;
        this.width = other.width;
        this.glow = other.glow;
        this.height = other.height;
        this.toSky = other.toSky;
        this.alpha = other.alpha;
        this.pulse = other.pulse;
        this.rotation = other.rotation;
        this.rainbow = other.rainbow;
    }

    /** Clamp every value into its legal range. Called server-side and on load. */
    public void sanitize() {
        width = Mth.clamp(width, MIN_WIDTH, MAX_WIDTH);
        glow = Mth.clamp(glow, 0.0f, 2.0f);
        height = Mth.clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        alpha = Mth.clamp(alpha, 0, 255);
        pulse = Mth.clamp(pulse, 0.0f, 2.0f);
        rotation = Mth.clamp(rotation, 0.0f, 2.0f);
        color &= 0xFFFFFF;
        if (shape == null) {
            shape = BeamShape.CLASSIC;
        }
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Enabled", enabled);
        tag.putInt("Color", color);
        tag.putInt("Shape", shape.ordinal());
        tag.putFloat("Width", width);
        tag.putFloat("Glow", glow);
        tag.putInt("Height", height);
        tag.putBoolean("ToSky", toSky);
        tag.putInt("Alpha", alpha);
        tag.putFloat("Pulse", pulse);
        tag.putFloat("Rotation", rotation);
        tag.putBoolean("Rainbow", rainbow);
        return tag;
    }

    public static BeamConfig load(CompoundTag tag) {
        BeamConfig cfg = new BeamConfig();
        if (tag.contains("Enabled")) cfg.enabled = tag.getBoolean("Enabled");
        if (tag.contains("Color")) cfg.color = tag.getInt("Color");
        if (tag.contains("Shape")) {
            BeamShape[] shapes = BeamShape.values();
            cfg.shape = shapes[Mth.clamp(tag.getInt("Shape"), 0, shapes.length - 1)];
        }
        if (tag.contains("Width")) cfg.width = tag.getFloat("Width");
        if (tag.contains("Glow")) cfg.glow = tag.getFloat("Glow");
        if (tag.contains("Height")) cfg.height = tag.getInt("Height");
        if (tag.contains("ToSky")) cfg.toSky = tag.getBoolean("ToSky");
        if (tag.contains("Alpha")) cfg.alpha = tag.getInt("Alpha");
        if (tag.contains("Pulse")) cfg.pulse = tag.getFloat("Pulse");
        if (tag.contains("Rotation")) cfg.rotation = tag.getFloat("Rotation");
        if (tag.contains("Rainbow")) cfg.rainbow = tag.getBoolean("Rainbow");
        cfg.sanitize();
        return cfg;
    }

    // ------------------------------------------------------- Network sync

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeInt(color);
        buf.writeEnum(shape);
        buf.writeFloat(width);
        buf.writeFloat(glow);
        buf.writeInt(height);
        buf.writeBoolean(toSky);
        buf.writeInt(alpha);
        buf.writeFloat(pulse);
        buf.writeFloat(rotation);
        buf.writeBoolean(rainbow);
    }

    public static BeamConfig read(FriendlyByteBuf buf) {
        BeamConfig cfg = new BeamConfig();
        cfg.enabled = buf.readBoolean();
        cfg.color = buf.readInt();
        cfg.shape = buf.readEnum(BeamShape.class);
        cfg.width = buf.readFloat();
        cfg.glow = buf.readFloat();
        cfg.height = buf.readInt();
        cfg.toSky = buf.readBoolean();
        cfg.alpha = buf.readInt();
        cfg.pulse = buf.readFloat();
        cfg.rotation = buf.readFloat();
        cfg.rainbow = buf.readBoolean();
        cfg.sanitize();
        return cfg;
    }
}
