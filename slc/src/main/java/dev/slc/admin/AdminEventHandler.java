package dev.slc.admin;

import dev.slc.SecretLabCubed;
import dev.slc.loadout.SlcArmorManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Event guard for administration powers and the hidden single-slot SLC body armor. */
@Mod.EventBusSubscriber(modid = SecretLabCubed.MOD_ID)
public final class AdminEventHandler {
    private AdminEventHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide && event.player instanceof ServerPlayer player) {
            AdminStateManager.tick(player);
            // Do not touch normal Minecraft inventory before a player has an actual SLC role.
            if (player.tickCount % 5 == 0 && dev.slc.round.RoundEngine.getPlayerRole(player.getUUID()) != dev.slc.role.Role.SPECTATOR) {
                dev.slc.loadout.SlcAmmoSlot.enforceReservedSlot(player);
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (AdminStateManager.isGodMode(player)) {
                event.setCanceled(true);
                return;
            }
            SlcArmorManager.damageArmor(player, event.getAmount());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AdminStateManager.clear(player);
            SlcArmorManager.forgetRuntime(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SlcArmorManager.syncToClient(player);
        }
    }
}
