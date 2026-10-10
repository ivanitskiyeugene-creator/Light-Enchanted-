package dev.zerosevennine.item;

import dev.zerosevennine.block.AbstractCameraBlock;
import dev.zerosevennine.block.FacilityMapNodeBlock;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RoomSelectorItem extends Item {
    public RoomSelectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof RoomSelectorItem) {
                CompoundTag tag = stack.getOrCreateTag();
                long[] points = tag.getLongArray("points");
                if (points.length > 0) {
                    tag.remove("points");
                    player.displayClientMessage(
                            Component.translatable("item.zero_seven_nine.room_selector.reset")
                                    .withStyle(ChatFormatting.GOLD), true);
                    level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.6f);
                }
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                undoLastPoint(stack, player, level);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState state = level.getBlockState(pos);

        if (player != null && player.isShiftKeyDown()) {
            // Shift + RMB: Undo 1 point
            if (!level.isClientSide) {
                undoLastPoint(stack, player, level);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        CompoundTag tag = stack.getOrCreateTag();
        long[] oldPoints = tag.getLongArray("points");

        // Normal RMB on Map Node or Camera: Link existing boundary points
        if ((state.getBlock() instanceof FacilityMapNodeBlock || state.getBlock() instanceof AbstractCameraBlock) && oldPoints.length > 0) {
            List<BlockPos> pointList = new ArrayList<>();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

            for (long pLong : oldPoints) {
                BlockPos p = BlockPos.of(pLong);
                pointList.add(p);
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX());
                maxY = Math.max(maxY, p.getY());
                maxZ = Math.max(maxZ, p.getZ());
            }

            int sizeX = (maxX - minX) + 1;
            int sizeY = (maxY - minY) + 1;
            int sizeZ = (maxZ - minZ) + 1;

            if (!level.isClientSide) {
                if (level.getBlockEntity(pos) instanceof CameraBlockEntity camBe) {
                    camBe.setBoundaryPoints(pointList);
                } else if (level.getBlockEntity(pos) instanceof FacilityMapNodeBlockEntity nodeBe) {
                    nodeBe.setBoundaryPoints(pointList);
                }

                if (player != null) {
                    player.displayClientMessage(
                            Component.translatable("item.zero_seven_nine.room_selector.linked", oldPoints.length, sizeX, sizeY, sizeZ)
                                    .withStyle(ChatFormatting.GREEN), true);
                }
            }
            level.playSound(player, pos, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.9f, 1.4f);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Normal RMB on block: Add next point
        long[] newPoints = Arrays.copyOf(oldPoints, oldPoints.length + 1);
        newPoints[oldPoints.length] = pos.asLong();
        tag.putLongArray("points", newPoints);

        if (!level.isClientSide && player != null) {
            player.displayClientMessage(
                    Component.translatable("item.zero_seven_nine.room_selector.point_added", newPoints.length, pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.AQUA), true);
        }

        float pitch = Math.min(2.0f, 1.0f + (0.08f * newPoints.length));
        level.playSound(player, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 1.0f, pitch);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void undoLastPoint(ItemStack stack, Player player, Level level) {
        CompoundTag tag = stack.getOrCreateTag();
        long[] points = tag.getLongArray("points");
        if (points.length > 0) {
            long removedLong = points[points.length - 1];
            BlockPos removedPos = BlockPos.of(removedLong);
            long[] newPoints = Arrays.copyOf(points, points.length - 1);
            if (newPoints.length == 0) {
                tag.remove("points");
            } else {
                tag.putLongArray("points", newPoints);
            }

            player.displayClientMessage(
                    Component.translatable("item.zero_seven_nine.room_selector.point_undone", points.length, removedPos.getX(), removedPos.getY(), removedPos.getZ(), newPoints.length)
                            .withStyle(ChatFormatting.YELLOW), true);
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8f, 1.2f);
        } else {
            player.displayClientMessage(
                    Component.translatable("item.zero_seven_nine.room_selector.empty")
                            .withStyle(ChatFormatting.RED), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.zero_seven_nine.room_selector.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zero_seven_nine.room_selector.desc2").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.zero_seven_nine.room_selector.desc3").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.zero_seven_nine.room_selector.desc4").withStyle(ChatFormatting.RED));

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("points")) {
            long[] points = tag.getLongArray("points");
            tooltip.add(Component.literal("---").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("item.zero_seven_nine.room_selector.points_count", points.length)
                    .withStyle(ChatFormatting.GREEN));
        }

        super.appendHoverText(stack, level, tooltip, flag);
    }
}
