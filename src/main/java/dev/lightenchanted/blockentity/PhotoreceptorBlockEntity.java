package dev.lightenchanted.blockentity;

import dev.lightenchanted.block.PhotoreceptorBlock;
import dev.lightenchanted.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PhotoreceptorBlockEntity extends BlockEntity {
    private int targetPower = 0;
    private int currentPower = 0;
    private int holdTicks = 0;

    public PhotoreceptorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHOTORECEPTOR.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PhotoreceptorBlockEntity be) {
        if (level.isClientSide) return;

        if (be.holdTicks > 0) {
            be.holdTicks--;
        } else {
            be.targetPower = 0;
        }

        if (be.currentPower != be.targetPower) {
            be.currentPower = be.targetPower;
            level.setBlock(pos, state.setValue(PhotoreceptorBlock.POWER, be.currentPower), 3);
            level.updateNeighborsAt(pos, state.getBlock());
            for (Direction d : Direction.values()) {
                level.updateNeighborsAt(pos.relative(d), state.getBlock());
            }
            be.setChanged();
        }
    }

    public void receiveLightSignal(int powerLevel) {
        this.targetPower = Math.max(this.targetPower, Math.min(15, powerLevel));
        this.holdTicks = 4; // Keep signal stable for 4 ticks
    }

    public Direction getFacing() {
        return getBlockState().getValue(PhotoreceptorBlock.FACING);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("CurrentPower", currentPower);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentPower = tag.getInt("CurrentPower");
        targetPower = currentPower;
    }
}
