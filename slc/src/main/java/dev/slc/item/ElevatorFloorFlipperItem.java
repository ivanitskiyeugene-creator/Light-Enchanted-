package dev.slc.item;

import dev.slc.elevator.ElevatorButtonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Floor Flipper — swaps which end of the shaft counts as the BOTTOM/TOP floor
 * on the WHOLE elevator network at once. Right-click any button of the network.
 * Pitch up = flipped, pitch down = back to normal. No chat spam.
 */
public class ElevatorFloorFlipperItem extends Item {
    public ElevatorFloorFlipperItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ElevatorButtonBlockEntity button)) {
            return InteractionResult.PASS;
        }

        boolean flipped = button.networkToggleFloorFlip();
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS,
                0.9f, flipped ? 1.7f : 0.7f);
        return InteractionResult.CONSUME;
    }
}
