package dev.lightenchanted.beam;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * Full description of a single light beam: color, shape, redstone and effects.
 * Mutable POJO; always run through {@link #sanitize()} before trusting.
 */
public class BeamConfig {
    public static final float MIN_WIDTH = 0.05f;
    public static final float MAX_WIDTH = 8.0f;
    public static final int MIN_HEIGHT = 2;
    public static final int MAX_HEIGHT = 160;

    // Redstone Modes
    public static final int REDSTONE_ALWAYS_ON = 0;
    public static final int REDSTONE_ON_SIGNAL = 1;
    public static final int REDSTONE_OFF_SIGNAL = 2;
    public static final int REDSTONE_DIMMER = 3;

    // Strobe Modes
    public static final int STROBE_OFF = 0;
    public static final int STROBE_SLOW = 1;
    public static final int STROBE_WARNING = 2;
    public static final int STROBE_RAPID = 3;

    /** Beam on/off. */
    public boolean enabled = true;
    /** RGB color, 0xRRGGBB. Ignored while {@link #rainbow} is on. */
    public int color = 0x67C7FF;
    /** Beam geometry. */
    public BeamShape shape = BeamShape.CONE;
    /** Beam diameter in blocks (fractional). */
    public float width = 0.6f;
    /** Beam radius at the far end for the CONE (spotlight) shape, in blocks. */
    public float endWidth = 1.2f;
    /** Extra glow shell strength, 0..2. */
    public float glow = 1.0f;
    /** Beam length in blocks. Used only while no target point is set. */
    public int height = 24;
    /** Stretch the beam up to the build limit. Used only while no target point is set. */
    public boolean toSky = false;
    /** Without a target: fire downwards instead of upwards. */
    public boolean down = true;
    /** World-space aim point of the beam. */
    public boolean hasTarget = false;
    public double targetX = 0.0;
    public double targetY = 0.0;
    public double targetZ = 0.0;
    /** Shift of the beam's origin point, in blocks (the block itself stays put). */
    public float offsetX = 0.0f;
    public float offsetY = 0.0f;
    public float offsetZ = 0.0f;
    /** Cast projected shadows through obstacles (gratings, 3D models, fences, bars). */
    public boolean shadows = true;
    /** Opacity, 0..255. */
    public int alpha = 230;
    /** Pulse speed, 0 = steady beam. */
    public float pulse = 0.0f;
    /** Rotation around the beam axis, 0 = static. */
    public float rotation = 0.0f;
    /** Cycle through the hue spectrum instead of using {@link #color}. */
    public boolean rainbow = false;

    // ---- v2.2.0 Redstone & Strobe
    public int redstoneMode = REDSTONE_ALWAYS_ON;
    public int strobe = STROBE_OFF;
    public int redstonePower = 0; // Transferred runtime power 0-15

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
        this.endWidth = other.endWidth;
        this.glow = other.glow;
        this.height = other.height;
        this.toSky = other.toSky;
        this.down = other.down;
        this.hasTarget = other.hasTarget;
        this.targetX = other.targetX;
        this.targetY = other.targetY;
        this.targetZ = other.targetZ;
        this.offsetX = other.offsetX;
        this.offsetY = other.offsetY;
        this.offsetZ = other.offsetZ;
        this.shadows = other.shadows;
        this.alpha = other.alpha;
        this.pulse = other.pulse;
        this.rotation = other.rotation;
        this.rainbow = other.rainbow;
        this.redstoneMode = other.redstoneMode;
        this.strobe = other.strobe;
        this.redstonePower = other.redstonePower;
    }

    /** Clamp every value into its legal range. Called server-side and on load. */
    public void sanitize() {
        width = Mth.clamp(width, MIN_WIDTH, MAX_WIDTH);
        endWidth = Mth.clamp(endWidth, MIN_WIDTH, MAX_WIDTH);
        glow = Mth.clamp(glow, 0.0f, 2.0f);
        height = Mth.clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        offsetX = Mth.clamp(offsetX, -8.0f, 8.0f);
        offsetY = Mth.clamp(offsetY, -8.0f, 8.0f);
        offsetZ = Mth.clamp(offsetZ, -8.0f, 8.0f);
        alpha = Mth.clamp(alpha, 0, 255);
        pulse = Mth.clamp(pulse, 0.0f, 2.0f);
        rotation = Mth.clamp(rotation, 0.0f, 2.0f);
        color &= 0xFFFFFF;
        redstoneMode = Mth.clamp(redstoneMode, 0, 3);
        strobe = Mth.clamp(strobe, 0, 3);
        redstonePower = Mth.clamp(redstonePower, 0, 15);
        if (shape == null) {
            shape = BeamShape.CONE;
        }
        if (!Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ)) {
            hasTarget = false;
            targetX = targetY = targetZ = 0.0;
        }
    }

    /**
     * Calculates whether the beam is currently visible considering redstone state.
     */
    public boolean isEmitting() {
        if (!enabled) return false;
        return switch (redstoneMode) {
            case REDSTONE_ON_SIGNAL -> redstonePower > 0;
            case REDSTONE_OFF_SIGNAL -> redstonePower == 0;
            case REDSTONE_DIMMER -> redstonePower > 0;
            default -> true;
        };
    }

    /**
     * Calculates the effective alpha accounting for dimmer, strobe and pulses.
     */
    public float getEffectiveAlpha(float time) {
        if (!isEmitting()) return 0.0f;

        float baseAlpha = alpha / 255.0f;

        // Redstone Dimmer scaling
        if (redstoneMode == REDSTONE_DIMMER) {
            baseAlpha *= (redstonePower / 15.0f);
        }

        // Strobe mode
        if (strobe > 0) {
            float strobeFreq = switch (strobe) {
                case STROBE_SLOW -> 3.0f;
                case STROBE_WARNING -> 7.0f;
                case STROBE_RAPID -> 14.0f;
                default -> 0.0f;
            };
            if (strobeFreq > 0.0f) {
                float flash = (Mth.sin(time * strobeFreq) > 0.15f) ? 1.0f : 0.0f;
                baseAlpha *= flash;
            }
        }

        // Pulse mode
        if (pulse > 0.001f) {
            baseAlpha *= 0.60f + 0.40f * Mth.sin(time * pulse * 2.4f);
        }

        return Mth.clamp(baseAlpha, 0.0f, 1.0f);
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Enabled", enabled);
        tag.putInt("Color", color);
        tag.putInt("Shape", shape.ordinal());
        tag.putFloat("Width", width);
        tag.putFloat("EndWidth", endWidth);
        tag.putFloat("Glow", glow);
        tag.putInt("Height", height);
        tag.putBoolean("ToSky", toSky);
        tag.putBoolean("Down", down);
        tag.putBoolean("HasTarget", hasTarget);
        tag.putDouble("TargetX", targetX);
        tag.putDouble("TargetY", targetY);
        tag.putDouble("TargetZ", targetZ);
        tag.putFloat("OffsetX", offsetX);
        tag.putFloat("OffsetY", offsetY);
        tag.putFloat("OffsetZ", offsetZ);
        tag.putBoolean("Shadows", shadows);
        tag.putInt("Alpha", alpha);
        tag.putFloat("Pulse", pulse);
        tag.putFloat("Rotation", rotation);
        tag.putBoolean("Rainbow", rainbow);
        tag.putInt("RedstoneMode", redstoneMode);
        tag.putInt("Strobe", strobe);
        tag.putInt("RedstonePower", redstonePower);
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
        if (tag.contains("EndWidth")) cfg.endWidth = tag.getFloat("EndWidth");
        if (tag.contains("Glow")) cfg.glow = tag.getFloat("Glow");
        if (tag.contains("Height")) cfg.height = tag.getInt("Height");
        if (tag.contains("ToSky")) cfg.toSky = tag.getBoolean("ToSky");
        if (tag.contains("Down")) cfg.down = tag.getBoolean("Down");
        if (tag.contains("HasTarget")) cfg.hasTarget = tag.getBoolean("HasTarget");
        if (tag.contains("TargetX")) cfg.targetX = tag.getDouble("TargetX");
        if (tag.contains("TargetY")) cfg.targetY = tag.getDouble("TargetY");
        if (tag.contains("TargetZ")) cfg.targetZ = tag.getDouble("TargetZ");
        if (tag.contains("OffsetX")) cfg.offsetX = tag.getFloat("OffsetX");
        if (tag.contains("OffsetY")) cfg.offsetY = tag.getFloat("OffsetY");
        if (tag.contains("OffsetZ")) cfg.offsetZ = tag.getFloat("OffsetZ");
        if (tag.contains("Shadows")) cfg.shadows = tag.getBoolean("Shadows");
        if (tag.contains("Alpha")) cfg.alpha = tag.getInt("Alpha");
        if (tag.contains("Pulse")) cfg.pulse = tag.getFloat("Pulse");
        if (tag.contains("Rotation")) cfg.rotation = tag.getFloat("Rotation");
        if (tag.contains("Rainbow")) cfg.rainbow = tag.getBoolean("Rainbow");
        if (tag.contains("RedstoneMode")) cfg.redstoneMode = tag.getInt("RedstoneMode");
        if (tag.contains("Strobe")) cfg.strobe = tag.getInt("Strobe");
        if (tag.contains("RedstonePower")) cfg.redstonePower = tag.getInt("RedstonePower");
        cfg.sanitize();
        return cfg;
    }

    // ------------------------------------------------------- Network sync

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeInt(color);
        buf.writeEnum(shape);
        buf.writeFloat(width);
        buf.writeFloat(endWidth);
        buf.writeFloat(glow);
        buf.writeInt(height);
        buf.writeBoolean(toSky);
        buf.writeBoolean(down);
        buf.writeBoolean(hasTarget);
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
        buf.writeFloat(offsetX);
        buf.writeFloat(offsetY);
        buf.writeFloat(offsetZ);
        buf.writeBoolean(shadows);
        buf.writeInt(alpha);
        buf.writeFloat(pulse);
        buf.writeFloat(rotation);
        buf.writeBoolean(rainbow);
        buf.writeInt(redstoneMode);
        buf.writeInt(strobe);
        buf.writeInt(redstonePower);
    }

    public static BeamConfig read(FriendlyByteBuf buf) {
        BeamConfig cfg = new BeamConfig();
        cfg.enabled = buf.readBoolean();
        cfg.color = buf.readInt();
        cfg.shape = buf.readEnum(BeamShape.class);
        cfg.width = buf.readFloat();
        cfg.endWidth = buf.readFloat();
        cfg.glow = buf.readFloat();
        cfg.height = buf.readInt();
        cfg.toSky = buf.readBoolean();
        cfg.down = buf.readBoolean();
        cfg.hasTarget = buf.readBoolean();
        cfg.targetX = buf.readDouble();
        cfg.targetY = buf.readDouble();
        cfg.targetZ = buf.readDouble();
        cfg.offsetX = buf.readFloat();
        cfg.offsetY = buf.readFloat();
        cfg.offsetZ = buf.readFloat();
        cfg.shadows = buf.readBoolean();
        cfg.alpha = buf.readInt();
        cfg.pulse = buf.readFloat();
        cfg.rotation = buf.readFloat();
        cfg.rainbow = buf.readBoolean();
        cfg.redstoneMode = buf.readInt();
        cfg.strobe = buf.readInt();
        cfg.redstonePower = buf.readInt();
        cfg.sanitize();
        return cfg;
    }
}
