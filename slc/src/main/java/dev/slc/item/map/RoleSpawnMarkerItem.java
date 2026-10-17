package dev.slc.item.map;

import dev.slc.admin.AdminPermission;
import dev.slc.role.Role;
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

/** One concrete map-editor tool per role and per spawn type; no invisible role selector in NBT. */
public final class RoleSpawnMarkerItem extends Item {
    public enum Type { POINT, ZONE }

    private final Role role;
    private final Type type;

    public RoleSpawnMarkerItem(Properties properties, Role role, Type type) {
        super(properties);
        this.role = role;
        this.type = type;
    }

    public Role role() { return role; }
    public Type type() { return type; }
    public boolean hasFirstPoint(ItemStack stack) { return MapMarkerFeedback.hasFirst(stack); }
    public BlockPos firstPoint(ItemStack stack) { return MapMarkerFeedback.firstPoint(stack); }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos, Player player) {
        if (type == Type.ZONE && !level.isClientSide && player instanceof ServerPlayer serverPlayer && AdminPermission.isAdmin(serverPlayer)) {
            MapMarkerFeedback.setFirst(player.getMainHandItem(), pos);
            MapMarkerFeedback.feedback((ServerLevel) level, pos, false);
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer player) || !AdminPermission.isAdmin(player)) {
            return InteractionResult.FAIL;
        }
        BlockPos clicked = context.getClickedPos();
        SpatialZoneManager zones = SpatialZoneManager.get(serverLevel);
        if (type == Type.POINT) {
            zones.addRoleSpawn(role, clicked.above(), player.getYRot(), player.getXRot());
            MapMarkerFeedback.feedback(serverLevel, clicked.above(), true);
            dev.slc.zone.MapZoneVisualSync.send(player);
            return InteractionResult.CONSUME;
        }

        ItemStack stack = context.getItemInHand();
        if (!MapMarkerFeedback.hasFirst(stack)) {
            MapMarkerFeedback.setFirst(stack, clicked);
            MapMarkerFeedback.feedback(serverLevel, clicked, false);
            return InteractionResult.CONSUME;
        }
        if (!player.isShiftKeyDown()) {
            MapMarkerFeedback.setFirst(stack, clicked);
            MapMarkerFeedback.feedback(serverLevel, clicked, false);
            return InteractionResult.CONSUME;
        }
        zones.addRoleSpawnZone(role, MapMarkerFeedback.finishBox(stack, clicked), player.getYRot(), player.getXRot());
        MapMarkerFeedback.feedback(serverLevel, clicked, true);
        dev.slc.zone.MapZoneVisualSync.send(player);
        return InteractionResult.CONSUME;
    }
}
