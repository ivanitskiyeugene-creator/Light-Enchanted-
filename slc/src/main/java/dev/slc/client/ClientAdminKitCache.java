package dev.slc.client;

import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.role.Role;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ClientAdminKitCache {
    private static final Map<Role, List<ItemStack>> INVENTORIES = new EnumMap<>(Role.class);
    private static final Map<Role, ItemStack> ARMOR = new EnumMap<>(Role.class);

    private ClientAdminKitCache() {}

    public static void set(Role role, List<ItemStack> inventory, ItemStack armor) {
        List<ItemStack> copy = new ArrayList<>();
        for (int i = 0; i < SlcLoadoutSavedData.INVENTORY_SLOTS; i++) {
            copy.add(i < inventory.size() ? inventory.get(i).copy() : ItemStack.EMPTY);
        }
        INVENTORIES.put(role, copy);
        ARMOR.put(role, armor.copy());
    }

    public static ItemStack get(Role role, int slot) {
        if (slot == SlcLoadoutSavedData.BODY_ARMOR_SLOT) return ARMOR.getOrDefault(role, ItemStack.EMPTY).copy();
        List<ItemStack> inventory = INVENTORIES.get(role);
        return inventory != null && slot >= 0 && slot < inventory.size() ? inventory.get(slot).copy() : ItemStack.EMPTY;
    }
}
