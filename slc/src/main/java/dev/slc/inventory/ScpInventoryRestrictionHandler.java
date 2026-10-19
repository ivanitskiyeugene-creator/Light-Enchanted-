package dev.slc.inventory;

import dev.slc.SecretLabCubed;
import dev.slc.round.RoundEngine;
import dev.slc.round.RoundState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server authority for the "SCPs have no inventory" rule. UI suppression is only a convenience;
 * SCP players cannot acquire world items even with a modified client.
 */
@Mod.EventBusSubscriber(modid = SecretLabCubed.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ScpInventoryRestrictionHandler {
    private ScpInventoryRestrictionHandler() {}

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        RoundState state = RoundEngine.getState();
        if ((state == RoundState.IN_PROGRESS || state == RoundState.WARHEAD_DETONATING)
                && RoundEngine.getPlayerRole(player.getUUID()).isScp()) {
            event.setCanceled(true);
        }
    }
}
