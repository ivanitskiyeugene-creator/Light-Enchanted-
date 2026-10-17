package dev.slc.item.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Shared two-corner selection storage and non-chat feedback for SLC map editor tools. */
final class MapMarkerFeedback {
    static final String FIRST_POINT = "SlcEditorFirstPoint";

    private MapMarkerFeedback() {}

    static void setFirst(ItemStack stack, BlockPos pos) {
        stack.getOrCreateTag().putLong(FIRST_POINT, pos.asLong());
    }

    static boolean hasFirst(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(FIRST_POINT);
    }

    static BlockPos firstPoint(ItemStack stack) {
        return hasFirst(stack) ? BlockPos.of(stack.getTag().getLong(FIRST_POINT)) : null;
    }

    static AABB finishBox(ItemStack stack, BlockPos second) {
        CompoundTag tag = stack.getOrCreateTag();
        BlockPos first = BlockPos.of(tag.getLong(FIRST_POINT));
        tag.remove(FIRST_POINT);
        return new AABB(
                Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()) + 1D, Math.max(first.getY(), second.getY()) + 1D, Math.max(first.getZ(), second.getZ()) + 1D
        );
    }

    static void feedback(ServerLevel level, BlockPos pos, boolean complete) {
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + .5D, pos.getY() + 1D, pos.getZ() + .5D,
                complete ? 14 : 5, .18D, .18D, .18D, .01D);
        level.playSound(null, pos, complete ? SoundEvents.NOTE_BLOCK_PLING.get() : SoundEvents.NOTE_BLOCK_HAT.get(),
                SoundSource.PLAYERS, .6F, complete ? 1.3F : .85F);
    }
}
