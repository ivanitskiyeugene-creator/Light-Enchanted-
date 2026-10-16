package dev.slc.item.map;

import dev.slc.admin.AdminPermission;
import dev.slc.zone.SpatialZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Dedicated surface escape-zone editor tool. */
public final class ExitZoneMarkerItem extends Item {
    public ExitZoneMarkerItem(Properties properties) { super(properties); }
    public boolean hasFirstPoint(ItemStack stack) { return MapMarkerFeedback.hasFirst(stack); }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && AdminPermission.isAdmin(serverPlayer)) {
            MapMarkerFeedback.setFirst(player.getMainHandItem(), pos);
            MapMarkerFeedback.feedback((ServerLevel) level, pos, false);
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player) || !AdminPermission.isAdmin(player)) return InteractionResult.FAIL;
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        if (!MapMarkerFeedback.hasFirst(stack) || !player.isShiftKeyDown()) {
            MapMarkerFeedback.setFirst(stack, pos);
            MapMarkerFeedback.feedback(level, pos, false);
            return InteractionResult.CONSUME;
        }
        SpatialZoneManager.get(level).setEscapeZone(MapMarkerFeedback.finishBox(stack, pos));
        MapMarkerFeedback.feedback(level, pos, true);
        return InteractionResult.CONSUME;
    }
}
