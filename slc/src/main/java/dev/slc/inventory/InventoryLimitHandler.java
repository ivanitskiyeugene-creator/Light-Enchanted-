package dev.slc.inventory;

/**
 * The old eight-cell pickup cap has intentionally been removed. SLC's radial UI now pages through
 * its full operative inventory, so collecting a ninth unique item must never disable access to it.
 */
public final class InventoryLimitHandler {
    private InventoryLimitHandler() {}
}
