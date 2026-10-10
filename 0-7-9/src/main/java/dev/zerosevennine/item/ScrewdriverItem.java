package dev.zerosevennine.item;

import dev.zerosevennine.block.AbstractCameraBlock;
import dev.zerosevennine.block.FacilityMapNodeBlock;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.facility.DeviceType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScrewdriverItem extends Item {
    public ScrewdriverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof AbstractCameraBlock) {
            if (player != null && player.isShiftKeyDown()) {
                // Shift + RMB: Open Camera Config GUI
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    dev.zerosevennine.network.ModNetwork.sendOpenCameraConfig(serverPlayer, pos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            } else {
                // Normal RMB: Select Camera
                CompoundTag tag = stack.getOrCreateTag();
                tag.putLong("selectedCamera", pos.asLong());
                if (!level.isClientSide && player != null) {
                    player.displayClientMessage(
                            Component.literal("Selected Camera at [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")
                                    .withStyle(ChatFormatting.AQUA), true);
                }
                level.playSound(player, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 1.0f, 1.5f);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        if (state.getBlock() instanceof FacilityMapNodeBlock) {
            // Open Map Node Config GUI
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                dev.zerosevennine.network.ModNetwork.sendOpenMapNodeConfig(serverPlayer, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Link Device to Selected Camera
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("selectedCamera")) {
            BlockPos camPos = BlockPos.of(tag.getLong("selectedCamera"));
            if (level.getBlockEntity(camPos) instanceof CameraBlockEntity camBe) {
                DeviceType detectedType = DeviceType.DOOR;
                if (state.getBlock() instanceof RedstoneLampBlock) {
                    detectedType = DeviceType.LIGHT;
                }

                camBe.addOrUpdateDevice(pos, detectedType);
                if (!level.isClientSide && player != null) {
                    player.displayClientMessage(
                            Component.literal("Linked " + detectedType.getLabel() + " at [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "] to Camera!")
                                    .withStyle(ChatFormatting.GREEN), true);
                }
                level.playSound(player, pos, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 1.8f);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.zero_seven_nine.screwdriver.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zero_seven_nine.screwdriver.desc2").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.zero_seven_nine.screwdriver.desc3").withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
