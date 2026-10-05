package dev.lightenchanted.beam;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Available beam geometries and custom gobo profiles.
 */
public enum BeamShape {
    /** Square column with an outer glow shell, beacon-like. */
    CLASSIC,
    /** Round polygonal column. */
    CYLINDER,
    /** Column tapering towards the top / conical spotlight. */
    CONE,
    /** Two ribbons spiralling upwards. */
    HELIX,
    /** Flat plane "wall" of light with a soft perpendicular copy. */
    SHEET,
    /** Four crossed planes forming a sparkle / star. */
    STAR,
    /** Square pyramid spotlight. */
    SQUARE,
    /** Wide elliptical / oval spotlight. */
    OVAL,
    /** Thin cinema gobo letterbox slit / laser bar. */
    SLIT;

    public Component displayName() {
        return Component.translatable("shape.lightenchanted." + name().toLowerCase(Locale.ROOT));
    }

    public BeamShape next() {
        BeamShape[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
