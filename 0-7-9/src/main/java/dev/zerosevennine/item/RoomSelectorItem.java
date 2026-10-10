package dev.zerosevennine.item;

import dev.zerosevennine.block.AbstractCameraBlock;
import dev.zerosevennine.block.FacilityMapNodeBlock;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.facility.FacilityNetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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
                tag.putLong("pos1", pos.asLong());
                player.displayClientMessage(
                        Component.literal("Corner 1 set to [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")
                                .withStyle(ChatFormatting.AQUA), true);
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 1.0f, 1.8f);
            }
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState state = level.getBlockState(pos);

        if (player != null && player.isShiftKeyDown()) {
            // Shift + RMB: Link selection to Map Node or Camera
            if (state.getBlock() instanceof FacilityMapNodeBlock || state.getBlock() instanceof AbstractCameraBlock) {
                CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains("pos1") && tag.contains("pos2")) {
                    BlockPos p1 = BlockPos.of(tag.getLong("pos1"));
                    BlockPos p2 = BlockPos.of(tag.getLong("pos2"));

                    int minX = Math.min(p1.getX(), p2.getX());
                    int minY = Math.min(p1.getY(), p2.getY());
                    int minZ = Math.min(p1.getZ(), p2.getZ());
                    int maxX = Math.max(p1.getX(), p2.getX());
                    int maxY = Math.max(p1.getY(), p2.getY());
                    int maxZ = Math.max(p1.getZ(), p2.getZ());

                    int sizeX = (maxX - minX) + 1;
                    int sizeY = (maxY - minY) + 1;
                    int sizeZ = (maxZ - minZ) + 1;

                    if (!level.isClientSide) {
                        player.displayClientMessage(
                                Component.literal("Bound Room Volume (" + sizeX + "x" + sizeY + "x" + sizeZ + ") to Node/Camera!")
                                        .withStyle(ChatFormatting.GREEN), true);
                    }
                    level.playSound(player, pos, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.9f, 1.4f);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                } else if (!level.isClientSide) {
                    player.displayClientMessage(
                            Component.literal("Set Corner 1 (LMB) and Corner 2 (RMB) first!").withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // Normal RMB: Set Pos 2
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong("pos2", pos.asLong());
        if (!level.isClientSide && player != null) {
            player.displayClientMessage(
                    Component.literal("Corner 2 set to [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")
                            .withStyle(ChatFormatting.YELLOW), true);
        }
        level.playSound(player, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 1.0f, 1.4f);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.zero_seven_nine.room_selector.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zero_seven_nine.room_selector.desc2").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.zero_seven_nine.room_selector.desc3").withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
