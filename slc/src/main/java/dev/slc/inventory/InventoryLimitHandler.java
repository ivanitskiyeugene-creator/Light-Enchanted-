package dev.slc.inventory;

import dev.slc.round.RoundEngine;
import dev.slc.round.RoundState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class InventoryLimitHandler {
    public static final int MAX_SLOTS = 8;

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        // Strict requirement: Only active when round is active (Round = true)
        if (RoundEngine.getState() != RoundState.IN_PROGRESS && RoundEngine.getState() != RoundState.WARHEAD_DETONATING) {
            return;
        }

        Player player = event.getEntity();
        if (player.isCreative() || player.isSpectator()) return;

        ItemStack pickingUp = event.getItem().getItem();
        if (pickingUp.isEmpty()) return;

        int filledSlots = 0;
        boolean canMerge = false;

        for (int i = 0; i < 36; i++) {
            ItemStack inSlot = player.getInventory().getItem(i);
            if (!inSlot.isEmpty()) {
                filledSlots++;
                if (ItemStack.isSameItemSameTags(inSlot, pickingUp) && inSlot.getCount() < inSlot.getMaxStackSize()) {
                    canMerge = true;
                }
            }
        }

        if (filledSlots >= MAX_SLOTS && !canMerge) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("§c[Инвентарь] Карманы переполнены! (Максимум: " + MAX_SLOTS + " предметов)"), true);
        }
    }
}
