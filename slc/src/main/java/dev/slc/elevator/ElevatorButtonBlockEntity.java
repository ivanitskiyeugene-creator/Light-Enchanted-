package dev.slc.elevator;

import dev.slc.init.ModBlockEntities;
import dev.slc.init.ModSounds;
import dev.slc.zone.SpatialZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ElevatorButtonBlockEntity extends BlockEntity {
    private BlockPos linkedPos = null;
    private ElevatorState state = ElevatorState.IDLE;
    private int timerTicks = 0;
    private boolean redstoneEmit = true; // Emits redstone when doors should be OPEN

    public ElevatorButtonBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ELEVATOR_BUTTON_BE.get(), pos, blockState);
    }

    public BlockPos getLinkedPos() {
        return linkedPos;
    }

    public void setLinkedPos(BlockPos pos) {
        this.linkedPos = pos;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public ElevatorState getState() {
        return state;
    }

    public boolean isEmittingRedstone() {
        return redstoneEmit;
    }

    public boolean triggerElevator() {
        if (level == null || level.isClientSide) return false;
        if (state != ElevatorState.IDLE) return false;
        if (linkedPos == null) return false;

        BlockEntity targetBe = level.getBlockEntity(linkedPos);
        if (!(targetBe instanceof ElevatorButtonBlockEntity targetElevator)) return false;
        if (targetElevator.getState() != ElevatorState.IDLE) return false;

        // Step 1: Start door closing sequence
        this.state = ElevatorState.CLOSING_DOORS;
        this.timerTicks = 30; // 1.5 seconds door closing
        this.redstoneEmit = false;

        targetElevator.state = ElevatorState.CLOSING_DOORS;
        targetElevator.timerTicks = 30;
        targetElevator.redstoneEmit = false;

        updateState();
        targetElevator.updateState();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, ElevatorButtonBlockEntity be) {
        if (be.timerTicks > 0) {
            be.timerTicks--;

            if (be.timerTicks == 0) {
                switch (be.state) {
                    case CLOSING_DOORS -> {
                        be.state = ElevatorState.MOVING;
                        be.timerTicks = 80; // 4.0 seconds movement
                        level.playSound(null, pos, ModSounds.ELEVATOR_MOVING.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                        be.updateState();
                    }
                    case MOVING -> {
                        // Perform seamless teleportation
                        if (level instanceof ServerLevel serverLevel && be.linkedPos != null) {
                            be.performTeleportation(serverLevel, pos, be.linkedPos);
                        }

                        be.state = ElevatorState.OPENING_DOORS;
                        be.timerTicks = 30; // 1.5 seconds open pulse
                        be.redstoneEmit = true; // Open doors on arrival
                        be.updateState();

                        if (be.linkedPos != null && level.getBlockEntity(be.linkedPos) instanceof ElevatorButtonBlockEntity targetBe) {
                            targetBe.state = ElevatorState.OPENING_DOORS;
                            targetBe.timerTicks = 30;
                            targetBe.redstoneEmit = true;
                            targetBe.updateState();
                        }
                    }
                    case OPENING_DOORS -> {
                        be.state = ElevatorState.COOLDOWN;
                        be.timerTicks = 40; // 2.0 seconds cooldown
                        be.updateState();
                    }
                    case COOLDOWN -> {
                        be.state = ElevatorState.IDLE;
                        be.updateState();
                    }
                    default -> {}
                }
            }
        }
    }

    private void performTeleportation(ServerLevel level, BlockPos srcPos, BlockPos dstPos) {
        SpatialZoneManager zoneMgr = SpatialZoneManager.get(level);
        AABB srcCabin = zoneMgr.getElevatorCabin(srcPos);
        AABB dstCabin = zoneMgr.getElevatorCabin(dstPos);

        if (srcCabin == null) {
            // Default 3x3x4 cabin centered 2 blocks in front of the button
            srcCabin = new AABB(srcPos).inflate(2.0, 2.0, 2.0);
        }
        if (dstCabin == null) {
            dstCabin = new AABB(dstPos).inflate(2.0, 2.0, 2.0);
        }

        double dx = (dstCabin.minX + dstCabin.maxX) / 2.0 - (srcCabin.minX + srcCabin.maxX) / 2.0;
        double dy = dstCabin.minY - srcCabin.minY;
        double dz = (dstCabin.minZ + dstCabin.maxZ) / 2.0 - (srcCabin.minZ + srcCabin.maxZ) / 2.0;

        List<Entity> entities = level.getEntities(null, srcCabin);
        for (Entity entity : entities) {
            double targetX = entity.getX() + dx;
            double targetY = entity.getY() + dy;
            double targetZ = entity.getZ() + dz;

            entity.teleportTo(targetX, targetY, targetZ);
        }
    }

    private void updateState() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("LinkedX")) {
            this.linkedPos = new BlockPos(tag.getInt("LinkedX"), tag.getInt("LinkedY"), tag.getInt("LinkedZ"));
        } else {
            this.linkedPos = null;
        }
        if (tag.contains("ElevatorState")) {
            try {
                this.state = ElevatorState.valueOf(tag.getString("ElevatorState"));
            } catch (Exception e) {
                this.state = ElevatorState.IDLE;
            }
        }
        this.timerTicks = tag.getInt("TimerTicks");
        this.redstoneEmit = tag.getBoolean("RedstoneEmit");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (linkedPos != null) {
            tag.putInt("LinkedX", linkedPos.getX());
            tag.putInt("LinkedY", linkedPos.getY());
            tag.putInt("LinkedZ", linkedPos.getZ());
        }
        tag.putString("ElevatorState", state.name());
        tag.putInt("TimerTicks", timerTicks);
        tag.putBoolean("RedstoneEmit", redstoneEmit);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
