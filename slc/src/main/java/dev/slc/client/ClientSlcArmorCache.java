package dev.slc.client;

import net.minecraft.world.item.ItemStack;

/** Client mirror for the single invisible SLC armor cell, used by the inventory overlay. */
public final class ClientSlcArmorCache {
    private static ItemStack armor = ItemStack.EMPTY;

    private ClientSlcArmorCache() {}

    public static ItemStack getArmor() {
        return armor.copy();
    }

    public static void setArmor(ItemStack stack) {
        armor = stack.copy();
    }
}
