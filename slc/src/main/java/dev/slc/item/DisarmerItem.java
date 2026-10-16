package dev.slc.item;

import dev.slc.disarm.DisarmerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Disarmer — ties enemy HUMAN players with a 2 s tape cast.
 * Tied players cannot self-release, and the captor cannot free their own tie;
 * only a third party can untie by holding "E" up close.
 */
public class DisarmerItem extends Item {
    public DisarmerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!player.level().isClientSide
                && player instanceof ServerPlayer captor
                && entity instanceof ServerPlayer victim) {
            DisarmerManager.startCast(captor, victim);
        }
        // Swallow the attack: the disarmer never deals damage
        return true;
    }
}
