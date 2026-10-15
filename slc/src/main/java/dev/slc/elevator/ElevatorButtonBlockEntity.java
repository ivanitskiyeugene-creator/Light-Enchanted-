package dev.slc.elevator;

import dev.slc.init.ModBlockEntities;
import dev.slc.init.ModSounds;
import dev.slc.zone.SpatialZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class ElevatorButtonBlockEntity extends BlockEntity {
    private BlockPos linkedPos = null;
    private ElevatorState state = ElevatorState.IDLE;
    private boolean hasCabin = true; // True if the physical cabin is currently at THIS floor
    private int timerTicks = 0;
    private boolean redstoneEmit = true; // Emits redstone when doors at THIS floor should be OPEN

    public ElevatorButtonBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ELEVATOR_BUTTON_BE.get(), pos, blockState);
    }

    public BlockPos getLinkedPos() {
        return linkedPos;
    }

    public void setLinkedPos(BlockPos pos) {
        this.linkedPos = pos;
        updateState();
    }

    public boolean hasCabin() {
        return hasCabin;
    }

    public void setHasCabin(boolean hasCabin) {
        this.hasCabin = hasCabin;
        // Doors only open on the floor where the cabin physically is!
        this.redstoneEmit = (this.state == ElevatorState.IDLE || this.state == ElevatorState.OPENING_DOORS) && this.hasCabin;
        syncPanelGlow();
        updateState();
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

        ServerLevel serverLevel = (ServerLevel) level;

        // Force-load target chunk
        serverLevel.getChunkSource().getChunk(linkedPos.getX() >> 4, linkedPos.getZ() >> 4, ChunkStatus.FULL, true);

        // Immediate tactile click sound
        serverLevel.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0f, 1.2f);

        BlockEntity targetBe = serverLevel.getBlockEntity(linkedPos);
        ElevatorButtonBlockEntity targetElevator = (targetBe instanceof ElevatorButtonBlockEntity be) ? be : null;

        if (targetElevator != null && targetElevator.getState() != ElevatorState.IDLE) {
            return false;
        }

        // Close doors everywhere during movement
        this.state = ElevatorState.CLOSING_DOORS;
        this.timerTicks = 30; // 1.5 seconds door closing
        this.redstoneEmit = false;
        this.syncPanelGlow(); // panel goes dark: cabin is leaving

        if (targetElevator != null) {
            targetElevator.state = ElevatorState.CLOSING_DOORS;
            targetElevator.timerTicks = 30;
            targetElevator.redstoneEmit = false;
            targetElevator.syncPanelGlow();
            targetElevator.updateState();
        }

        updateState();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, ElevatorButtonBlockEntity be) {
        if (be.needsInitialSync) {
            be.needsInitialSync = false;
            be.syncPanelGlow();
        }
        if (be.timerTicks > 0) {
            be.timerTicks--;

            if (be.timerTicks == 0) {
                switch (be.state) {
                    case CLOSING_DOORS -> {
                        be.state = ElevatorState.MOVING;
                        be.timerTicks = 80; // 4.0 seconds movement
                        level.playSound(null, pos, ModSounds.ELEVATOR_MOVING.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                        if (be.linkedPos != null) {
                            level.playSound(null, be.linkedPos, ModSounds.ELEVATOR_MOVING.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                        }
                        be.updateState();
                    }
                    case MOVING -> {
                        if (level instanceof ServerLevel serverLevel && be.linkedPos != null) {
                            // Determine direction of transfer based on which floor had the cabin
                            BlockPos fromPos = be.hasCabin ? pos : be.linkedPos;
                            BlockPos toPos = be.hasCabin ? be.linkedPos : pos;

                            be.performTeleportation(serverLevel, fromPos, toPos);

                            // The destination floor now has the cabin!
                            boolean currentIsDest = (toPos.equals(pos));
                            be.hasCabin = currentIsDest;
                            be.state = ElevatorState.OPENING_DOORS;
                            be.timerTicks = 30;
                            be.redstoneEmit = currentIsDest; // ONLY open doors on the floor where cabin arrived!
                            be.syncPanelGlow(); // arrival panel lights up mint
                            be.updateState();

                            BlockEntity targetBe = serverLevel.getBlockEntity(be.linkedPos);
                            if (targetBe instanceof ElevatorButtonBlockEntity targetElevator) {
                                targetElevator.hasCabin = !currentIsDest;
                                targetElevator.state = ElevatorState.OPENING_DOORS;
                                targetElevator.timerTicks = 30;
                                targetElevator.redstoneEmit = !currentIsDest; // ONLY open doors if cabin is here
                                targetElevator.syncPanelGlow();
                                targetElevator.updateState();
                            }
                        }
                    }
                    case OPENING_DOORS -> {
                        be.state = ElevatorState.COOLDOWN;
                        be.timerTicks = 40; // 2.0 seconds cooldown
                        be.syncPanelGlow();
                        be.updateState();
                    }
                    case COOLDOWN -> {
                        be.state = ElevatorState.IDLE;
                        be.redstoneEmit = be.hasCabin; // Keep doors open only if cabin is here
                        be.syncPanelGlow();
                        be.updateState();
                    }
                    default -> {}
                }
            }
        }
    }

    private void performTeleportation(ServerLevel level, BlockPos fromPos, BlockPos toPos) {
        level.getChunkSource().getChunk(toPos.getX() >> 4, toPos.getZ() >> 4, ChunkStatus.FULL, true);

        SpatialZoneManager zoneMgr = SpatialZoneManager.get(level);
        AABB srcCabin = zoneMgr.getElevatorCabin(fromPos);
        AABB dstCabin = zoneMgr.getElevatorCabin(toPos);

        BlockState fromState = level.getBlockState(fromPos);
        Direction srcFacing = fromState.hasProperty(ElevatorButtonBlock.FACING) ?
                fromState.getValue(ElevatorButtonBlock.FACING) : Direction.NORTH;

        if (srcCabin == null) {
            BlockPos srcCenter = fromPos.relative(srcFacing, 2);
            srcCabin = new AABB(srcCenter.getX() - 2.0, fromPos.getY() - 1.0, srcCenter.getZ() - 2.0,
                    srcCenter.getX() + 2.0, fromPos.getY() + 3.0, srcCenter.getZ() + 2.0);
        }

        if (dstCabin == null) {
            BlockState toState = level.getBlockState(toPos);
            Direction dstFacing = toState.hasProperty(ElevatorButtonBlock.FACING) ?
                    toState.getValue(ElevatorButtonBlock.FACING) : Direction.NORTH;
            BlockPos dstCenter = toPos.relative(dstFacing, 2);
            dstCabin = new AABB(dstCenter.getX() - 2.0, toPos.getY() - 1.0, dstCenter.getZ() - 2.0,
                    dstCenter.getX() + 2.0, toPos.getY() + 3.0, dstCenter.getZ() + 2.0);
        }

        double dx = ((dstCabin.minX + dstCabin.maxX) / 2.0) - ((srcCabin.minX + srcCabin.maxX) / 2.0);
        double dy = dstCabin.minY - srcCabin.minY;
        double dz = ((dstCabin.minZ + dstCabin.maxZ) / 2.0) - ((srcCabin.minZ + srcCabin.maxZ) / 2.0);

        List<Entity> entities = level.getEntities(null, srcCabin);
        for (Entity entity : entities) {
            double targetX = entity.getX() + dx;
            double targetY = entity.getY() + dy;
            double targetZ = entity.getZ() + dz;

            if (entity instanceof ServerPlayer player) {
                player.connection.teleport(targetX, targetY, targetZ, player.getYRot(), player.getXRot());
                player.resetFallDistance();
            } else {
                entity.teleportTo(targetX, targetY, targetZ);
            }
        }
    }

    public void updateState() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    private boolean needsInitialSync = true;

    /** Panel is lit (mint glow + pulsing node) only while the cabin physically rests at THIS floor.
     * During closing/moving both panels go dark and blink red, so the animation shows the trip. */
    private boolean isPanelLit() {
        return hasCabin && (state == ElevatorState.IDLE
                || state == ElevatorState.OPENING_DOORS
                || state == ElevatorState.COOLDOWN);
    }

    /** Applies the visual ACTIVE blockstate property so the correct model/animation renders. */
    public void syncPanelGlow() {
        if (level == null || level.isClientSide) return;
        BlockState st = level.getBlockState(worldPosition);
        if (st.getBlock() instanceof ElevatorButtonBlock
                && st.hasProperty(ElevatorButtonBlock.ACTIVE)
                && st.getValue(ElevatorButtonBlock.ACTIVE) != isPanelLit()) {
            level.setBlock(worldPosition, st.setValue(ElevatorButtonBlock.ACTIVE, isPanelLit()), 3);
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
        this.hasCabin = tag.getBoolean("HasCabin");
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
        tag.putBoolean("HasCabin", hasCabin);
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
