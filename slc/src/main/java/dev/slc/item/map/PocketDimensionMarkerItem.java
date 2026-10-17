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

/** Three explicit, map-owned SCP-106 pocket-dimension editor tools. */
public final class PocketDimensionMarkerItem extends Item {
    public enum Type { ARRIVAL_ZONE, ESCAPE_ZONE, RETURN_POINT }

    private final Type type;

    public PocketDimensionMarkerItem(Properties properties, Type type) {
        super(properties);
        this.type = type;
    }

    public Type type() { return type; }
    public boolean hasFirstPoint(ItemStack stack) { return MapMarkerFeedback.hasFirst(stack); }
    public BlockPos firstPoint(ItemStack stack) { return MapMarkerFeedback.firstPoint(stack); }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos, Player player) {
        if (type != Type.RETURN_POINT && !level.isClientSide && player instanceof ServerPlayer serverPlayer
                && AdminPermission.isAdmin(serverPlayer)) {
            MapMarkerFeedback.setFirst(player.getMainHandItem(), pos);
            MapMarkerFeedback.feedback((ServerLevel) level, pos, false);
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)
                || !AdminPermission.isAdmin(player)) return InteractionResult.FAIL;
        SpatialZoneManager zones = SpatialZoneManager.get(level);
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();

        if (type == Type.RETURN_POINT) {
            zones.setPocketReturnPoint(pos.above(), player.getYRot(), player.getXRot());
            MapMarkerFeedback.feedback(level, pos.above(), true);
            dev.slc.zone.MapZoneVisualSync.send(player);
            return InteractionResult.CONSUME;
        }

        if (!MapMarkerFeedback.hasFirst(stack) || !player.isShiftKeyDown()) {
            MapMarkerFeedback.setFirst(stack, pos);
            MapMarkerFeedback.feedback(level, pos, false);
            return InteractionResult.CONSUME;
        }
        if (type == Type.ARRIVAL_ZONE) zones.setPocketArrivalZone(MapMarkerFeedback.finishBox(stack, pos));
        else zones.setPocketEscapeZone(MapMarkerFeedback.finishBox(stack, pos));
        MapMarkerFeedback.feedback(level, pos, true);
        dev.slc.zone.MapZoneVisualSync.send(player);
        return InteractionResult.CONSUME;
    }
}
