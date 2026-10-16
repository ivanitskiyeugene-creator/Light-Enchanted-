package dev.slc.disarm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Enforces the tied-state rules on the server (SCP:SL canon):
 *  - tied players cannot attack, use items/blocks/entities or sprint;
 *  - third-party untie via repeated "E" interact events;
 *  - death / logout / round end release the tie.
 */
@Mod.EventBusSubscriber(modid = dev.slc.SecretLabCubed.MOD_ID)
public class DisarmEventHandler {

    // -------------------- Tied player restrictions --------------------

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof Player player && DisarmerManager.isTied(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (DisarmerManager.isTied(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (DisarmerManager.isTied(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (DisarmerManager.isTied(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        Player untier = event.getEntity();
        if (untier.level().isClientSide) return;
        if (DisarmerManager.isTied(untier.getUUID())) {
            event.setCanceled(true);
            return;
        }
        // Free third party holding "E" on a tied victim -> untie progress
        if (event.getTarget() instanceof ServerPlayer victim
                && untier instanceof ServerPlayer serverUntier
                && DisarmerManager.isTied(victim.getUUID())) {
            DisarmerManager.onUntieEvent(serverUntier, victim);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Player player = event.player;
        if (!player.level().isClientSide && DisarmerManager.isTied(player.getUUID())) {
            if (player.isSprinting()) player.setSprinting(false);
        }
    }

    // -------------------- Cast/tie lifecycle --------------------

    @SubscribeEvent
    public static void onEntityHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer captor) {
            DisarmerManager.onCaptorHurt(captor);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DisarmerManager.onPlayerGone(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DisarmerManager.onPlayerGone(event.getEntity().getUUID());
    }
}
