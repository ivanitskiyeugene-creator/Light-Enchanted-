package dev.slc.item;

import dev.slc.admin.AdminPermission;
import dev.slc.elevator.ElevatorButtonBlockEntity;
import dev.slc.role.Role;
import dev.slc.zone.SpatialZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** OP-only world setup marker configured by the admin panel: exit volume, spawn point/zone or lift cabin. */
public final class AdminZoneMarkerItem extends Item {
    public static final String MODE = "SlcMarkerMode";
    public static final String ROLE = "SlcMarkerRole";

    public AdminZoneMarkerItem(Properties properties) { super(properties); }

    public static ItemStack configured(String mode, Role role) {
        ItemStack stack = new ItemStack(dev.slc.init.ModItems.ADMIN_ZONE_MARKER.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(MODE, mode);
        if (role != null) tag.putString(ROLE, role.name());
        return stack;
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && AdminPermission.isAdmin(serverPlayer)) {
            ItemStack stack = player.getMainHandItem();
            CompoundTag tag = stack.getOrCreateTag();
            tag.putLong("SlcP1", pos.asLong());
            markerFeedback((ServerLevel) level, pos, false);
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || !AdminPermission.isAdmin(serverPlayer)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getOrCreateTag();
        String mode = tag.getString(MODE);
        Role role = readRole(tag);
        BlockPos clicked = context.getClickedPos();
        SpatialZoneManager zones = SpatialZoneManager.get(serverLevel);

        if ("SPAWN_POINT".equals(mode) && role != null) {
            zones.addRoleSpawn(role, clicked.above(), player.getYRot(), player.getXRot());
            markerFeedback(serverLevel, clicked.above(), true);
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown() && tag.contains("SlcP1")) {
            BlockPos first = BlockPos.of(tag.getLong("SlcP1"));
            AABB box = new AABB(
                    Math.min(first.getX(), clicked.getX()), Math.min(first.getY(), clicked.getY()), Math.min(first.getZ(), clicked.getZ()),
                    Math.max(first.getX(), clicked.getX()) + 1D, Math.max(first.getY(), clicked.getY()) + 1D, Math.max(first.getZ(), clicked.getZ()) + 1D
            );
            if ("EXIT".equals(mode)) zones.setEscapeZone(box);
            else if ("SPAWN_ZONE".equals(mode) && role != null) zones.addRoleSpawnZone(role, box, player.getYRot(), player.getXRot());
            else if ("ELEVATOR_CABIN".equals(mode) && level.getBlockEntity(clicked) instanceof ElevatorButtonBlockEntity) zones.setElevatorCabin(clicked, box);
            else return InteractionResult.FAIL;
            tag.remove("SlcP1");
            markerFeedback(serverLevel, clicked, true);
            return InteractionResult.CONSUME;
        }

        tag.putLong("SlcP2", clicked.asLong());
        markerFeedback(serverLevel, clicked, false);
        return InteractionResult.CONSUME;
    }

    private static Role readRole(CompoundTag tag) {
        try { return tag.contains(ROLE) ? Role.valueOf(tag.getString(ROLE)) : null; }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static void markerFeedback(ServerLevel level, BlockPos pos, boolean complete) {
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + .5D, pos.getY() + 1D, pos.getZ() + .5D, complete ? 14 : 5, .18D, .18D, .18D, .01D);
        level.playSound(null, pos, complete ? SoundEvents.NOTE_BLOCK_PLING.get() : SoundEvents.NOTE_BLOCK_HAT.get(), SoundSource.PLAYERS,
                .6F, complete ? 1.3F : .85F);
    }
}
