package dev.slc.item;

import dev.slc.elevator.ElevatorButtonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Elevator Timer Tuner — adjusts the network-wide cabin travel time.
 *
 *  - Right-click ANY button of the network  : +0.5 s  (20 ticks steps, max 30 s)
 *  - Sneak + right-click ANY button         : -0.5 s  (min 1 s)
 *  - Sneak + right-click while sneaking on the same button twice reaches the floor/ceiling
 *
 * Default is 4.5 seconds (90 ticks). The value applies automatically to the WHOLE
 * elevator network (all floor buttons + the in-cabin button). Sound pitch rises with
 * the configured time so engineers can hear the setting without any chat spam.
 */
public class ElevatorTimerTunerItem extends Item {
    private static final int STEP_TICKS = 10; // 0.5 seconds

    public ElevatorTimerTunerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ElevatorButtonBlockEntity button)) {
            return InteractionResult.PASS;
        }

        boolean decrease = player != null && player.isShiftKeyDown();
        int current = button.getTravelSetting();
        int next = current + (decrease ? -STEP_TICKS : STEP_TICKS);
        next = Math.max(ElevatorButtonBlockEntity.TRAVEL_MIN_TICKS,
                Math.min(ElevatorButtonBlockEntity.TRAVEL_MAX_TICKS, next));

        button.networkSetTravelSetting(next);

        // Audible gauge: pling pitch scales 0.5 (1 s) -> 2.0 (30 s); dull bass on limits
        if (next == current) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8f, 0.5f);
        } else {
            float range = ElevatorButtonBlockEntity.TRAVEL_MAX_TICKS - ElevatorButtonBlockEntity.TRAVEL_MIN_TICKS;
            float pitch = 0.5f + 1.5f * ((next - ElevatorButtonBlockEntity.TRAVEL_MIN_TICKS) / range);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.0f, pitch);
        }

        return InteractionResult.CONSUME;
    }
}
