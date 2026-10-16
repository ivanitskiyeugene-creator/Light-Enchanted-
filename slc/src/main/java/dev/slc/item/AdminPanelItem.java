package dev.slc.item;

import dev.slc.admin.AdminPermission;
import dev.slc.network.ModNetwork;
import dev.slc.network.S2COpenAdminPanelPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** OP-4-only entrypoint. Authority is checked again for every panel action. */
public class AdminPanelItem extends Item {
    public AdminPanelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && AdminPermission.isAdmin(serverPlayer)) {
            ModNetwork.sendToPlayer(new S2COpenAdminPanelPacket(), serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
