package dev.lightenchanted.beam;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Available beam geometries.
 */
public enum BeamShape {
    /** Square column with an outer glow shell, beacon-like. */
    CLASSIC,
    /** Round polygonal column. */
    CYLINDER,
    /** Column tapering towards the top. */
    CONE,
    /** Two ribbons spiralling upwards. */
    HELIX,
    /** Flat plane "wall" of light with a soft perpendicular copy. */
    SHEET,
    /** Four crossed planes forming a sparkle / star. */
    STAR;

    public Component displayName() {
        return Component.translatable("shape.lightenchanted." + name().toLowerCase(Locale.ROOT));
    }

    public BeamShape next() {
        BeamShape[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
