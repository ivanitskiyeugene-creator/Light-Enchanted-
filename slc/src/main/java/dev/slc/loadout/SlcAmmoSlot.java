package dev.slc.loadout;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Validates the dedicated, real inventory ammunition cell without taking a hard dependency on TACZ.
 * TACZ keeps calibre/type/count in ItemStack NBT, so this deliberately verifies only tacz:ammo and
 * leaves the complete tag untouched.
 */
public final class SlcAmmoSlot {
    public static final ResourceLocation TACZ_AMMO_ID = new ResourceLocation("tacz", "ammo");

    private SlcAmmoSlot() {}

    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return true;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return TACZ_AMMO_ID.equals(itemId);
    }

    /** Keeps slot 35 dedicated even when a player opens Minecraft's normal inventory. */
    public static void enforceReservedSlot(ServerPlayer player) {
        ItemStack stack = player.getInventory().getItem(SlcLoadoutSavedData.AMMO_SLOT);
        if (accepts(stack)) return;
        player.getInventory().setItem(SlcLoadoutSavedData.AMMO_SLOT, ItemStack.EMPTY);
        for (int i = 0; i < SlcLoadoutSavedData.AMMO_SLOT; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                player.getInventory().setItem(i, stack);
                player.inventoryMenu.broadcastChanges();
                return;
            }
        }
        player.drop(stack, false);
        player.inventoryMenu.broadcastChanges();
    }
}
