package dev.lightenchanted.blockentity;

import dev.lightenchanted.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Stores state and handles smooth blade rotation animation for the 3x3 SCP:SL Industrial Fan.
 */
public class IndustrialFanBlockEntity extends BlockEntity {
    private BlockPos masterPos = null;
    private int speedMode = 2; // 0=OFF, 1=SLOW, 2=NORMAL, 3=FAST
    private boolean redstonePowered = false;

    // Client-side rotation interpolation
    private float spinAngle = 0.0f;
    private float prevSpinAngle = 0.0f;

    public IndustrialFanBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INDUSTRIAL_FAN.get(), pos, state);
    }

    public boolean isMaster() {
        return masterPos == null || masterPos.equals(worldPosition);
    }

    public BlockPos getMasterPos() {
        return masterPos;
    }

    public void setMasterPos(BlockPos pos) {
        this.masterPos = pos;
        setChanged();
    }

    public int getSpeedMode() {
        return speedMode;
    }

    public void setSpeedMode(int mode) {
        this.speedMode = mode % 4;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void cycleSpeedMode() {
        setSpeedMode((speedMode + 1) % 4);
    }

    public void setRedstonePowered(boolean powered) {
        if (this.redstonePowered != powered) {
            this.redstonePowered = powered;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    public boolean isSpinning() {
        return (speedMode > 0) || redstonePowered;
    }

    public float getEffectiveSpeed() {
        if (redstonePowered) return 18.0f; // Fast speed under redstone
        return switch (speedMode) {
            case 1 -> 4.0f;
            case 2 -> 9.0f;
            case 3 -> 18.0f;
            default -> 0.0f;
        };
    }

    public float getSpinAngle(float partialTick) {
        return prevSpinAngle + (spinAngle - prevSpinAngle) * partialTick;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, IndustrialFanBlockEntity be) {
        be.prevSpinAngle = be.spinAngle;
        if (be.isSpinning()) {
            be.spinAngle = (be.spinAngle + be.getEffectiveSpeed()) % 360.0f;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IndustrialFanBlockEntity be) {
        // Server synchronization
    }

    public Direction getFacing() {
        if (getBlockState().hasProperty(BlockStateProperties.FACING)) {
            return getBlockState().getValue(BlockStateProperties.FACING);
        }
        return Direction.NORTH;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (masterPos != null) {
            tag.putLong("MasterPos", masterPos.asLong());
        }
        tag.putInt("SpeedMode", speedMode);
        tag.putBoolean("Powered", redstonePowered);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("MasterPos")) {
            masterPos = BlockPos.of(tag.getLong("MasterPos"));
        }
        if (tag.contains("SpeedMode")) {
            speedMode = tag.getInt("SpeedMode");
        }
        if (tag.contains("Powered")) {
            redstonePowered = tag.getBoolean("Powered");
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
