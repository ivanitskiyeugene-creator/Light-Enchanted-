package dev.slc.loadout;

import dev.slc.role.Role;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Per-world templates used when an SLC role is applied. */
public final class SlcLoadoutSavedData extends SavedData {
    /** Physical Minecraft inventory cells; the final one is reserved as the TACZ ammunition cell. */
    public static final int INVENTORY_SLOTS = 36;
    public static final int AMMO_SLOT = INVENTORY_SLOTS - 1;
    public static final int BODY_ARMOR_SLOT = INVENTORY_SLOTS;
    private static final String DATA_NAME = "slc_role_loadouts";
    private final Map<Role, Loadout> loadouts = new EnumMap<>(Role.class);

    public static SlcLoadoutSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                SlcLoadoutSavedData::load, SlcLoadoutSavedData::new, DATA_NAME
        );
    }

    public Loadout getLoadout(Role role) {
        return loadouts.computeIfAbsent(role, ignored -> new Loadout());
    }

    public void setItem(Role role, int slot, ItemStack stack) {
        Loadout loadout = getLoadout(role);
        if (slot == BODY_ARMOR_SLOT) loadout.bodyArmor = stack.copy();
        else if (slot == AMMO_SLOT) {
            if (!SlcAmmoSlot.accepts(stack)) return;
            loadout.inventory.set(slot, stack.copy());
        } else if (slot >= 0 && slot < INVENTORY_SLOTS) loadout.inventory.set(slot, stack.copy());
        setDirty();
    }

    public void clearItem(Role role, int slot) {
        setItem(role, slot, ItemStack.EMPTY);
    }

    public static SlcLoadoutSavedData load(CompoundTag root) {
        SlcLoadoutSavedData data = new SlcLoadoutSavedData();
        for (String key : root.getAllKeys()) {
            try {
                Role role = Role.valueOf(key);
                CompoundTag tag = root.getCompound(key);
                Loadout loadout = new Loadout();
                if (tag.contains("BodyArmor", Tag.TAG_COMPOUND)) loadout.bodyArmor = ItemStack.of(tag.getCompound("BodyArmor"));
                ListTag inventory = tag.getList("Inventory", Tag.TAG_COMPOUND);
                for (int i = 0; i < Math.min(INVENTORY_SLOTS, inventory.size()); i++) {
                    loadout.inventory.set(i, ItemStack.of(inventory.getCompound(i)));
                }
                data.loadouts.put(role, loadout);
            } catch (IllegalArgumentException ignored) {
                // Templates for roles removed by a future version are deliberately skipped.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        for (Map.Entry<Role, Loadout> entry : loadouts.entrySet()) {
            Loadout loadout = entry.getValue();
            CompoundTag tag = new CompoundTag();
            if (!loadout.bodyArmor.isEmpty()) tag.put("BodyArmor", loadout.bodyArmor.save(new CompoundTag()));
            ListTag inventory = new ListTag();
            for (ItemStack stack : loadout.inventory) inventory.add(stack.save(new CompoundTag()));
            tag.put("Inventory", inventory);
            root.put(entry.getKey().name(), tag);
        }
        return root;
    }

    public static final class Loadout {
        private final List<ItemStack> inventory = new ArrayList<>(INVENTORY_SLOTS);
        private ItemStack bodyArmor = ItemStack.EMPTY;

        private Loadout() {
            for (int i = 0; i < INVENTORY_SLOTS; i++) inventory.add(ItemStack.EMPTY);
        }

        public List<ItemStack> inventory() {
            return inventory.stream().map(ItemStack::copy).toList();
        }

        public ItemStack bodyArmor() {
            return bodyArmor.copy();
        }
    }
}
