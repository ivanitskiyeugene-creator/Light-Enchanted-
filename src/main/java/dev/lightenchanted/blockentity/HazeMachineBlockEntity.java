package dev.lightenchanted.blockentity;

import dev.lightenchanted.block.HazeMachineBlock;
import dev.lightenchanted.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HazeMachineBlockEntity extends BlockEntity {
    public HazeMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAZE_MACHINE.get(), pos, state);
    }

    public boolean isActive() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof HazeMachineBlock)) return false;
        boolean enabled = state.getValue(HazeMachineBlock.ENABLED);
        boolean powered = state.getValue(HazeMachineBlock.POWERED);
        return enabled || powered;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HazeMachineBlockEntity be) {
        // Keeps active state synced
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, HazeMachineBlockEntity be) {
        if (!be.isActive()) return;

        RandomSource rand = level.getRandom();
        Direction facing = state.getValue(HazeMachineBlock.FACING);

        // Spawn dense atmospheric stage smoke drifting from nozzle
        if (rand.nextFloat() < 0.85f) {
            double nx = pos.getX() + 0.5 + facing.getStepX() * 0.52;
            double ny = pos.getY() + 0.5 + rand.nextFloat() * 0.15;
            double nz = pos.getZ() + 0.5 + facing.getStepZ() * 0.52;

            double vx = facing.getStepX() * (0.08 + rand.nextFloat() * 0.05) + (rand.nextFloat() - 0.5) * 0.04;
            double vy = 0.02 + rand.nextFloat() * 0.03;
            double vz = facing.getStepZ() * (0.08 + rand.nextFloat() * 0.05) + (rand.nextFloat() - 0.5) * 0.04;

            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, nx, ny, nz, vx, vy, vz);
        }
    }
}
