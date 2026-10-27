package dev.slc.elevator;

import dev.slc.SecretLabCubed;
import dev.slc.compat.ValkyrienSkiesCompat;
import dev.slc.init.ModBlockEntities;
import dev.slc.init.ModSounds;
import dev.slc.zone.SpatialZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Elevator call/entry button. Any number of these blocks form ONE elevator network:
 * floor call buttons (exterior) + an optional button INSIDE the cabin (interior flag).
 * All members mirror the same state machine so every panel renders the same live cabin
 * position (see ElevatorButtonRenderer).
 */
public class ElevatorButtonBlockEntity extends BlockEntity {
    public static final int CLOSE_TICKS = 30;
    public static final int OPEN_TICKS = 30;
    public static final int COOLDOWN_TICKS = 40;
    public static final int TRAVEL_BASE_TICKS = 90; // canon default: 4.5 seconds
    public static final int TRAVEL_MIN_TICKS = 20;  // 1.0 s
    public static final int TRAVEL_MAX_TICKS = 600; // 30.0 s

    private final List<BlockPos> members = new ArrayList<>();
    private final List<Boolean> memberInterior = new ArrayList<>();
    private boolean interior = false;      // TRUE if THIS button lives inside the cabin
    private int cabinFloor = 0;            // index into exterior floors sorted by Y (asc)
    private int srcFloor = 0;
    private int dstFloor = 0;
    private int travelTotal = 90;
    private int travelSetting = TRAVEL_BASE_TICKS; // network-wide travel time, tunable by the timer tool
    private boolean floorFlipped = false;          // floor status inverted by the Flipper tool (bottom<->top)
    private ElevatorState state = ElevatorState.IDLE;
    private int timerTicks = 0;

    public ElevatorButtonBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ELEVATOR_BUTTON_BE.get(), pos, blockState);
    }

    // ------------------------------------------------------------------
    // Network management (called by the linker item)
    // ------------------------------------------------------------------

    public void applyNetwork(List<BlockPos> newMembers, List<Boolean> interiors) {
        this.members.clear();
        this.members.addAll(newMembers);
        this.memberInterior.clear();
        this.memberInterior.addAll(interiors);
        int self = newMembers.indexOf(worldPosition);
        this.interior = self >= 0 && self < interiors.size() && interiors.get(self);
        this.cabinFloor = 0; // cabin starts at the lowest exterior floor
        this.srcFloor = 0;
        this.dstFloor = 0;
        this.state = ElevatorState.IDLE;
        this.timerTicks = 0;
        sync();
    }

    public boolean hasNetwork() {
        return members.size() >= 2;
    }

    public boolean isInterior() {
        return interior;
    }

    /** Exterior (floor) member positions sorted bottom -> top
     *  (or top -> bottom when the network is flipped by the Floor Flipper tool). */
    public List<BlockPos> getExteriorFloors() {
        List<BlockPos> floors = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            if (i < memberInterior.size() && !memberInterior.get(i)) {
                floors.add(members.get(i));
            }
        }
        floors.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        if (floorFlipped) {
            Collections.reverse(floors);
        }
        return floors;
    }

    public boolean isFloorFlipped() {
        return floorFlipped;
    }

    /** Floor Flipper tool: swaps which end of the shaft counts as the bottom/top floor
     *  on EVERY panel of the network at once. Sound feedback lives in the item. */
    public boolean networkToggleFloorFlip() {
        for (BlockPos memberPos : members) {
            BlockEntity be = getMemberBlockEntity(memberPos);
            if (be instanceof ElevatorButtonBlockEntity member) {
                member.toggleFlipInternal();
            }
        }
        if (!hasNetwork()) {
            toggleFlipInternal();
            return floorFlipped;
        }
        return members.isEmpty() ? floorFlipped : floorFlipped;
    }

    private void toggleFlipInternal() {
        List<BlockPos> before = getExteriorFloors();
        BlockPos cabPos = (cabinFloor >= 0 && cabinFloor < before.size()) ? before.get(cabinFloor) : null;
        BlockPos srcPos = (srcFloor >= 0 && srcFloor < before.size()) ? before.get(srcFloor) : null;
        BlockPos dstPos = (dstFloor >= 0 && dstFloor < before.size()) ? before.get(dstFloor) : null;

        this.floorFlipped = !this.floorFlipped;

        List<BlockPos> after = getExteriorFloors();
        if (cabPos != null) cabinFloor = Math.max(0, after.indexOf(cabPos));
        if (srcPos != null) srcFloor = Math.max(0, after.indexOf(srcPos));
        if (dstPos != null) dstFloor = Math.max(0, after.indexOf(dstPos));
        sync();
    }

    public int getFloorCount() {
        return getExteriorFloors().size();
    }

    public int getCabinFloor() {
        return Math.min(cabinFloor, Math.max(0, getFloorCount() - 1));
    }

    public int getSrcFloor() {
        return srcFloor;
    }

    public int getDstFloor() {
        return dstFloor;
    }

    public int getTravelTotal() {
        return travelTotal;
    }

    public ElevatorState getState() {
        return state;
    }

    public int getTimerTicks() {
        return timerTicks;
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    /** Called when a player right-clicks this button. Server-side only. */
    public void onPressed() {
        if (level == null || level.isClientSide) return;
        level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8f, 1.2f);

        if (!hasNetwork() || state != ElevatorState.IDLE) return;

        List<BlockPos> floors = getExteriorFloors();
        if (floors.size() < 2) return;

        int floor = getCabinFloor();
        int target;
        if (interior) {
            // In-cabin button: go to the NEXT floor (cyclic), like a floor selector
            target = (floor + 1) % floors.size();
        } else {
            target = floors.indexOf(worldPosition);
            if (target < 0 || target == floor) return; // cabin is already here
        }

        networkStartMove(floor, target, travelSetting);
    }

    // ------------------------------------------------------------------
    // Network-wide travel time (Elevator Timer Tuner item)
    // ------------------------------------------------------------------

    public int getTravelSetting() {
        return travelSetting;
    }

    /** Applies the travel time to EVERY member of the network automatically. */
    public void networkSetTravelSetting(int ticks) {
        int clamped = Math.max(TRAVEL_MIN_TICKS, Math.min(TRAVEL_MAX_TICKS, ticks));
        for (BlockPos memberPos : members) {
            BlockEntity be = getMemberBlockEntity(memberPos);
            if (be instanceof ElevatorButtonBlockEntity member) {
                member.travelSetting = clamped;
                member.sync();
            }
        }
        // Unlinked button: just self
        if (!hasNetwork()) {
            this.travelSetting = clamped;
            sync();
        }
    }

    private void networkStartMove(int src, int dst, int travel) {
        for (BlockPos memberPos : members) {
            BlockEntity be = getMemberBlockEntity(memberPos);
            if (be instanceof ElevatorButtonBlockEntity member) {
                member.beginMove(src, dst, travel);
            }
        }
    }

    private void beginMove(int src, int dst, int travel) {
        this.srcFloor = src;
        this.dstFloor = dst;
        this.travelTotal = travel;
        this.state = ElevatorState.CLOSING_DOORS;
        this.timerTicks = CLOSE_TICKS;
        sync();
    }

    @org.jetbrains.annotations.Nullable
    private BlockEntity getMemberBlockEntity(BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return level != null ? level.getBlockEntity(pos) : null;
        serverLevel.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
        return serverLevel.getBlockEntity(pos);
    }

    // ------------------------------------------------------------------
    // State machine (ticked on BOTH sides; client mirrors timers for smooth render)
    // ------------------------------------------------------------------

    public static void tick(Level level, BlockPos pos, BlockState blockState, ElevatorButtonBlockEntity be) {
        if (be.timerTicks <= 0) return;
        be.timerTicks--;
        if (be.timerTicks > 0) return;

        boolean server = !level.isClientSide;
        switch (be.state) {
            case CLOSING_DOORS -> {
                be.state = ElevatorState.MOVING;
                be.timerTicks = be.travelTotal;
                if (server && level instanceof ServerLevel serverLevel) {
                    for (BlockPos memberPos : be.members) {
                        level.playSound(null, memberPos, ModSounds.ELEVATOR_MOVING.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                        serverLevel.getChunkSource().getChunk(memberPos.getX() >> 4, memberPos.getZ() >> 4, ChunkStatus.FULL, true);
                    }
                }
            }
            case MOVING -> {
                boolean transferSucceeded = true;
                if (server && level instanceof ServerLevel serverLevel) {
                    List<BlockPos> floors = be.getExteriorFloors();
                    if (floors.size() >= 2) {
                        BlockPos src = floors.get(Math.min(be.srcFloor, floors.size() - 1));
                        BlockPos dst = floors.get(Math.min(be.dstFloor, floors.size() - 1));
                        // The physical transfer happens exactly once, from the destination member.
                        if (pos.equals(dst)) {
                            transferSucceeded = be.performTeleportation(serverLevel, src, dst);
                        }
                    }
                }
                if (!transferSucceeded) {
                    // performTeleportation has restored the entire linked network to the source
                    // floor. Do not let this destination ticker overwrite that cancellation.
                    return;
                }
                be.cabinFloor = be.dstFloor;
                be.state = ElevatorState.OPENING_DOORS;
                be.timerTicks = OPEN_TICKS;
            }
            case OPENING_DOORS -> {
                be.state = ElevatorState.COOLDOWN;
                be.timerTicks = COOLDOWN_TICKS;
            }
            case COOLDOWN -> {
                be.state = ElevatorState.IDLE;
            }
            default -> {}
        }
        be.sync();
    }

    private boolean performTeleportation(ServerLevel level, BlockPos fromPos, BlockPos toPos) {
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

        // Cabin boxes are saved in the coordinate space of their elevator buttons. When one of
        // those buttons sits on a Valkyrien Skies ship, that coordinate space is the shipyard,
        // not the position players see in the world. Convert through the optional VS bridge so a
        // lift may safely travel ship -> world, world -> ship, or ship -> ship.
        ValkyrienSkiesCompat.CabinSpace sourceSpace = ValkyrienSkiesCompat.cabinSpace(level, fromPos, srcCabin);
        ValkyrienSkiesCompat.CabinSpace destinationSpace = ValkyrienSkiesCompat.cabinSpace(level, toPos, dstCabin);
        if (!sourceSpace.isAvailable() || !destinationSpace.isAvailable()) {
            SecretLabCubed.LOGGER.warn("[SLC] Elevator transfer cancelled: a Valkyrien Skies ship endpoint is not loaded ({} -> {}).", fromPos, toPos);
            networkAbortMove(srcFloor);
            return false;
        }
        Vec3 sourceCenter = sourceSpace.localTransferAnchor();
        Vec3 destinationCenter = destinationSpace.localTransferAnchor();

        List<Entity> entities = ValkyrienSkiesCompat.entitiesInCabin(level, sourceSpace);
        for (Entity entity : entities) {
            Vec3 sourceLocalPosition = sourceSpace.toLocal(entity.position());
            Vec3 targetLocalPosition = sourceLocalPosition.add(
                    destinationCenter.x - sourceCenter.x,
                    destinationCenter.y - sourceCenter.y,
                    destinationCenter.z - sourceCenter.z);
            Vec3 targetWorldPosition = destinationSpace.toWorld(targetLocalPosition);

            if (entity instanceof ServerPlayer player) {
                ViewAngles arrivalView = transformViewForCabinTransfer(player, sourceSpace, destinationSpace);
                player.connection.teleport(targetWorldPosition.x, targetWorldPosition.y, targetWorldPosition.z,
                        arrivalView.yaw(), arrivalView.pitch());
                player.resetFallDistance();
            } else {
                entity.teleportTo(targetWorldPosition.x, targetWorldPosition.y, targetWorldPosition.z);
            }
        }
        return true;
    }

    /**
     * Keeps a player's look direction relative to the elevator cabin. A player facing the front
     * door of a ship rotated 45 degrees will therefore face the front door after arriving in a
     * cabin rotated 90 degrees, instead of retaining an unrelated global yaw.
     */
    private static ViewAngles transformViewForCabinTransfer(ServerPlayer player,
                                                             ValkyrienSkiesCompat.CabinSpace sourceSpace,
                                                             ValkyrienSkiesCompat.CabinSpace destinationSpace) {
        Vec3 worldLook = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        Vec3 sourceLocalLook = sourceSpace.toLocalDirection(worldLook);
        Vec3 destinationWorldLook = destinationSpace.toWorldDirection(sourceLocalLook);
        if (destinationWorldLook.lengthSqr() < 1.0E-8D) {
            return new ViewAngles(player.getYRot(), player.getXRot());
        }

        Vec3 normalized = destinationWorldLook.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-normalized.x, normalized.z));
        float pitch = (float) -Math.toDegrees(Math.asin(normalized.y));
        return new ViewAngles(yaw, pitch);
    }

    private record ViewAngles(float yaw, float pitch) {
    }

    /** Restores every panel to the source floor when a VS endpoint is not safely transformable. */
    private void networkAbortMove(int sourceFloor) {
        for (BlockPos memberPos : members) {
            BlockEntity blockEntity = getMemberBlockEntity(memberPos);
            if (blockEntity instanceof ElevatorButtonBlockEntity member) {
                member.cabinFloor = sourceFloor;
                member.srcFloor = sourceFloor;
                member.dstFloor = sourceFloor;
                member.state = ElevatorState.IDLE;
                member.timerTicks = 0;
                member.sync();
            }
        }
    }

    // ------------------------------------------------------------------
    // Redstone doors control
    // ------------------------------------------------------------------

    public boolean isEmittingRedstone() {
        if (interior || !hasNetwork()) return false;
        List<BlockPos> floors = getExteriorFloors();
        int cabin = getCabinFloor();
        // Doors open ONLY on the floor where the cabin physically rests
        return cabin < floors.size()
                && floors.get(cabin).equals(worldPosition)
                && (state == ElevatorState.IDLE
                || state == ElevatorState.OPENING_DOORS
                || state == ElevatorState.COOLDOWN);
    }

    // ------------------------------------------------------------------
    // Sync / NBT
    // ------------------------------------------------------------------

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        members.clear();
        memberInterior.clear();
        if (tag.contains("Members", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Members", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag m = list.getCompound(i);
                members.add(new BlockPos(m.getInt("X"), m.getInt("Y"), m.getInt("Z")));
                memberInterior.add(m.getBoolean("Interior"));
            }
        }
        this.interior = tag.getBoolean("SelfInterior");
        this.floorFlipped = tag.getBoolean("FloorFlipped");
        this.cabinFloor = tag.getInt("CabinFloor");
        this.srcFloor = tag.getInt("SrcFloor");
        this.dstFloor = tag.getInt("DstFloor");
        this.travelTotal = Math.max(20, tag.getInt("TravelTotal"));
        if (tag.contains("TravelSetting")) {
            this.travelSetting = Math.max(TRAVEL_MIN_TICKS, Math.min(TRAVEL_MAX_TICKS, tag.getInt("TravelSetting")));
        }
        this.timerTicks = tag.getInt("TimerTicks");
        if (tag.contains("ElevatorState")) {
            try {
                this.state = ElevatorState.valueOf(tag.getString("ElevatorState"));
            } catch (Exception e) {
                this.state = ElevatorState.IDLE;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (int i = 0; i < members.size(); i++) {
            CompoundTag m = new CompoundTag();
            m.putInt("X", members.get(i).getX());
            m.putInt("Y", members.get(i).getY());
            m.putInt("Z", members.get(i).getZ());
            m.putBoolean("Interior", i < memberInterior.size() && memberInterior.get(i));
            list.add(m);
        }
        tag.put("Members", list);
        tag.putBoolean("SelfInterior", interior);
        tag.putBoolean("FloorFlipped", floorFlipped);
        tag.putInt("CabinFloor", cabinFloor);
        tag.putInt("SrcFloor", srcFloor);
        tag.putInt("DstFloor", dstFloor);
        tag.putInt("TravelTotal", travelTotal);
        tag.putInt("TravelSetting", travelSetting);
        tag.putInt("TimerTicks", timerTicks);
        tag.putString("ElevatorState", state.name());
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
