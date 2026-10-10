package dev.zerosevennine.blockentity;

import dev.zerosevennine.facility.DeviceType;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CameraBlockEntity extends BlockEntity {
    public static class LinkedDevice {
        public BlockPos pos;
        public DeviceType type;
        public boolean isActivated;
        public boolean isLocked;

        public LinkedDevice(BlockPos pos, DeviceType type) {
            this.pos = pos;
            this.type = type;
            this.isActivated = false;
            this.isLocked = false;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", pos.asLong());
            tag.putString("type", type.name());
            tag.putBoolean("active", isActivated);
            tag.putBoolean("locked", isLocked);
            return tag;
        }

        public static LinkedDevice load(CompoundTag tag) {
            BlockPos p = BlockPos.of(tag.getLong("pos"));
            DeviceType t = DeviceType.DOOR;
            try {
                t = DeviceType.valueOf(tag.getString("type"));
            } catch (Exception ignored) {}
            LinkedDevice dev = new LinkedDevice(p, t);
            dev.isActivated = tag.getBoolean("active");
            dev.isLocked = tag.getBoolean("locked");
            return dev;
        }
    }

    private String cameraName = "HCZ - Heavy Hallway";
    private FacilityZone zone = FacilityZone.HCZ;

    private boolean isOccupied = false;
    private UUID occupantUuid = null;

    public float currentYaw = 0.0f;
    public float currentPitch = 0.0f;
    public float targetYaw = 0.0f;
    public float targetPitch = 0.0f;

    private final List<LinkedDevice> linkedDevices = new ArrayList<>();
    private final Map<Direction, BlockPos> wasdNeighbors = new EnumMap<>(Direction.class);

    private final List<BlockPos> boundaryPoints = new ArrayList<>();
    private BlockPos minBound = null;
    private BlockPos maxBound = null;

    private int blackoutTimer = 0;
    private int lockdownTimer = 0;
    private boolean autoDiscovered = false;

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            FacilityNetworkManager.registerCamera(worldPosition, this);
            if (!autoDiscovered) {
                autoDiscoverSurroundings();
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            FacilityNetworkManager.unregisterCamera(worldPosition);
        }
        super.setRemoved();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CameraBlockEntity be) {
        if (!level.isClientSide) {
            FacilityNetworkManager.registerCamera(pos, be);
            if (!be.autoDiscovered && level.getGameTime() % 40 == 0) {
                be.autoDiscoverSurroundings();
            }
        }

        // Smooth rotation interpolation
        float yawDiff = be.targetYaw - be.currentYaw;
        while (yawDiff < -180.0f) yawDiff += 360.0f;
        while (yawDiff > 180.0f) yawDiff -= 360.0f;
        be.currentYaw += yawDiff * 0.25f;

        float pitchDiff = be.targetPitch - be.currentPitch;
        be.currentPitch += pitchDiff * 0.25f;

        if (be.blackoutTimer > 0) {
            be.blackoutTimer--;
            if (be.blackoutTimer == 0) {
                be.restoreLighting();
            }
        }
        if (be.lockdownTimer > 0) {
            be.lockdownTimer--;
            if (be.lockdownTimer == 0) {
                be.unlockAllDoors();
            }
        }

        // 20-tick redstone pulse timer for doors and devices
        if (!level.isClientSide && !be.activePulses.isEmpty()) {
            java.util.Iterator<ActivePulse> it = be.activePulses.iterator();
            while (it.hasNext()) {
                ActivePulse pulse = it.next();
                pulse.remainingTicks--;
                if (pulse.remainingTicks <= 0) {
                    it.remove();
                    be.endRedstonePulse(pulse.pos);
                }
            }
        }
    }

    public void autoDiscoverSurroundings() {
        if (level == null || level.isClientSide) return;
        autoDiscovered = true;

        // Auto-discover nearby doors (including SCP:FR and modded doors), buttons, and lights in 14-block radius
        BlockPos cp = worldPosition;
        for (int dx = -14; dx <= 14; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -14; dz <= 14; dz++) {
                    BlockPos p = cp.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (dev.zerosevennine.facility.UniversalDoorHandler.isDoorLikeBlock(s)) {
                        if (!hasDevice(p)) {
                            addOrUpdateDevice(p, DeviceType.DOOR);
                        }
                    } else if (s.getBlock() instanceof RedstoneLampBlock) {
                        if (!hasDevice(p)) {
                            addOrUpdateDevice(p, DeviceType.LIGHT);
                        }
                    }
                }
            }
        }

        // Auto-discover neighbor cameras for WASD routing
        for (Map.Entry<BlockPos, CameraBlockEntity> entry : FacilityNetworkManager.getCameras().entrySet()) {
            BlockPos otherPos = entry.getKey();
            if (otherPos.equals(cp)) continue;

            double dx = otherPos.getX() - cp.getX();
            double dz = otherPos.getZ() - cp.getZ();
            double distSq = dx * dx + dz * dz;

            if (distSq <= 48 * 48) {
                if (Math.abs(dz) >= Math.abs(dx)) {
                    if (dz < -2.0 && !wasdNeighbors.containsKey(Direction.NORTH)) {
                        setWasdNeighbor(Direction.NORTH, otherPos);
                    } else if (dz > 2.0 && !wasdNeighbors.containsKey(Direction.SOUTH)) {
                        setWasdNeighbor(Direction.SOUTH, otherPos);
                    }
                } else {
                    if (dx < -2.0 && !wasdNeighbors.containsKey(Direction.WEST)) {
                        setWasdNeighbor(Direction.WEST, otherPos);
                    } else if (dx > 2.0 && !wasdNeighbors.containsKey(Direction.EAST)) {
                        setWasdNeighbor(Direction.EAST, otherPos);
                    }
                }
            }
        }
    }

    public boolean hasDevice(BlockPos p) {
        for (LinkedDevice d : linkedDevices) {
            if (d.pos.equals(p)) return true;
        }
        return false;
    }

    public LinkedDevice getDevice(BlockPos p) {
        for (LinkedDevice d : linkedDevices) {
            if (d.pos.equals(p)) return d;
        }
        return null;
    }

    public void setOccupied(boolean occupied, UUID uuid) {
        this.isOccupied = occupied;
        this.occupantUuid = uuid;
        if (!occupied) {
            this.targetYaw = 0.0f;
            this.targetPitch = 0.0f;
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isOccupied() {
        return isOccupied;
    }

    public UUID getOccupantUuid() {
        return occupantUuid;
    }

    public void updateOrientation(float yaw, float pitch) {
        this.targetYaw = yaw;
        this.targetPitch = pitch;
    }

    public String getCameraName() {
        return cameraName;
    }

    public void setCameraName(String cameraName) {
        this.cameraName = cameraName;
        setChanged();
    }

    public FacilityZone getZone() {
        return zone;
    }

    public void setZone(FacilityZone zone) {
        this.zone = zone;
        setChanged();
    }

    public List<LinkedDevice> getLinkedDevices() {
        return linkedDevices;
    }

    public void addOrUpdateDevice(BlockPos devPos, DeviceType type) {
        for (LinkedDevice d : linkedDevices) {
            if (d.pos.equals(devPos)) {
                d.type = type;
                setChanged();
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
                return;
            }
        }
        linkedDevices.add(new LinkedDevice(devPos, type));
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void removeDevice(BlockPos devPos) {
        linkedDevices.removeIf(d -> d.pos.equals(devPos));
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public Map<Direction, BlockPos> getWasdNeighbors() {
        return wasdNeighbors;
    }

    public void setWasdNeighbor(Direction dir, BlockPos targetPos) {
        if (targetPos == null) {
            wasdNeighbors.remove(dir);
        } else {
            wasdNeighbors.put(dir, targetPos.immutable());
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public List<BlockPos> getBoundaryPoints() {
        return boundaryPoints;
    }

    public BlockPos getMinBound() {
        return minBound;
    }

    public BlockPos getMaxBound() {
        return maxBound;
    }

    public void setBoundaryPoints(List<BlockPos> points) {
        this.boundaryPoints.clear();
        this.boundaryPoints.addAll(points);
        recalcBounds();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void recalcBounds() {
        if (boundaryPoints.isEmpty()) {
            minBound = null;
            maxBound = null;
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : boundaryPoints) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        minBound = new BlockPos(minX, minY, minZ);
        maxBound = new BlockPos(maxX, maxY, maxZ);
    }

    public static class ActivePulse {
        public final BlockPos pos;
        public int remainingTicks;

        public ActivePulse(BlockPos pos, int remainingTicks) {
            this.pos = pos;
            this.remainingTicks = remainingTicks;
        }
    }

    private final List<ActivePulse> activePulses = new ArrayList<>();

    // Ability Handlers
    public boolean toggleDoor(BlockPos doorPos) {
        if (level == null) return false;
        // Check lock status
        for (LinkedDevice d : linkedDevices) {
            if ((d.pos.equals(doorPos) || d.pos.equals(doorPos.below()) || d.pos.equals(doorPos.above())) && d.isLocked) {
                return false;
            }
        }

        // If door currently has an active pulse, cancel it and close immediately
        for (int i = 0; i < activePulses.size(); i++) {
            ActivePulse p = activePulses.get(i);
            if (p.pos.equals(doorPos) || p.pos.equals(doorPos.below()) || p.pos.equals(doorPos.above())) {
                activePulses.remove(i);
                endRedstonePulse(p.pos);
                return true;
            }
        }

        // Send 20-tick redstone pulse (1 second) to the target door/point
        activePulses.add(new ActivePulse(doorPos, 20));
        startRedstonePulse(doorPos);
        return true;
    }

    private void startRedstonePulse(BlockPos pos) {
        if (level == null) return;
        net.minecraft.server.level.ServerPlayer player = null;
        if (occupantUuid != null && level.getServer() != null) {
            player = level.getServer().getPlayerList().getPlayer(occupantUuid);
        }
        dev.zerosevennine.facility.UniversalDoorHandler.openDoor(level, pos, player);
    }

    private void endRedstonePulse(BlockPos pos) {
        if (level == null) return;
        net.minecraft.server.level.ServerPlayer player = null;
        if (occupantUuid != null && level.getServer() != null) {
            player = level.getServer().getPlayerList().getPlayer(occupantUuid);
        }
        dev.zerosevennine.facility.UniversalDoorHandler.closeDoor(level, pos, player);
    }

    public boolean lockDoor(BlockPos doorPos, boolean lock) {
        for (LinkedDevice d : linkedDevices) {
            if (d.pos.equals(doorPos) || d.pos.equals(doorPos.below()) || d.pos.equals(doorPos.above())) {
                d.isLocked = lock;
                if (lock) {
                    activePulses.removeIf(p -> p.pos.equals(d.pos) || p.pos.equals(d.pos.below()) || p.pos.equals(d.pos.above()));
                    endRedstonePulse(d.pos);
                }
                setChanged();
                return true;
            }
        }

        // Auto-register on click if not in linked list
        if (level != null) {
            BlockState state = level.getBlockState(doorPos);
            if (dev.zerosevennine.facility.UniversalDoorHandler.isDoorLikeBlock(state)) {
                LinkedDevice newDev = new LinkedDevice(doorPos, DeviceType.DOOR);
                newDev.isLocked = lock;
                linkedDevices.add(newDev);
                if (lock) {
                    activePulses.removeIf(p -> p.pos.equals(doorPos) || p.pos.equals(doorPos.below()) || p.pos.equals(doorPos.above()));
                    endRedstonePulse(doorPos);
                }
                setChanged();
                return true;
            }
        }
        return false;
    }

    public void triggerTesla(BlockPos teslaPos) {
        if (level == null) return;
        level.setBlock(teslaPos, level.getBlockState(teslaPos), 3);
    }

    public void triggerBlackout(int durationTicks) {
        this.blackoutTimer = durationTicks;
        setChanged();
    }

    public void triggerLockdown(int durationTicks) {
        this.lockdownTimer = durationTicks;
        for (LinkedDevice d : linkedDevices) {
            if (d.type == DeviceType.DOOR) {
                lockDoor(d.pos, true);
            }
        }
        setChanged();
    }

    private void restoreLighting() {
        setChanged();
    }

    private void unlockAllDoors() {
        for (LinkedDevice d : linkedDevices) {
            if (d.type == DeviceType.DOOR) {
                d.isLocked = false;
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("cameraName", cameraName);
        tag.putString("zone", zone.name());
        tag.putBoolean("occupied", isOccupied);
        if (occupantUuid != null) {
            tag.putUUID("occupant", occupantUuid);
        }
        tag.putFloat("yaw", currentYaw);
        tag.putFloat("pitch", currentPitch);
        tag.putBoolean("autoDisc", autoDiscovered);

        ListTag devList = new ListTag();
        for (LinkedDevice d : linkedDevices) {
            devList.add(d.save());
        }
        tag.put("devices", devList);

        CompoundTag wasdTag = new CompoundTag();
        for (Map.Entry<Direction, BlockPos> entry : wasdNeighbors.entrySet()) {
            wasdTag.putLong(entry.getKey().getName(), entry.getValue().asLong());
        }
        tag.put("wasd", wasdTag);

        long[] pts = new long[boundaryPoints.size()];
        for (int i = 0; i < boundaryPoints.size(); i++) {
            pts[i] = boundaryPoints.get(i).asLong();
        }
        tag.putLongArray("boundaryPoints", pts);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        cameraName = tag.getString("cameraName");
        if (tag.contains("zone")) {
            try {
                zone = FacilityZone.valueOf(tag.getString("zone"));
            } catch (Exception ignored) {}
        }
        isOccupied = tag.getBoolean("occupied");
        if (tag.hasUUID("occupant")) {
            occupantUuid = tag.getUUID("occupant");
        }
        currentYaw = tag.getFloat("yaw");
        currentPitch = tag.getFloat("pitch");
        targetYaw = currentYaw;
        targetPitch = currentPitch;
        autoDiscovered = tag.getBoolean("autoDisc");

        linkedDevices.clear();
        ListTag devList = tag.getList("devices", Tag.TAG_COMPOUND);
        for (int i = 0; i < devList.size(); i++) {
            linkedDevices.add(LinkedDevice.load(devList.getCompound(i)));
        }

        wasdNeighbors.clear();
        if (tag.contains("wasd")) {
            CompoundTag wasdTag = tag.getCompound("wasd");
            for (String key : wasdTag.getAllKeys()) {
                Direction dir = Direction.byName(key);
                if (dir != null) {
                    wasdNeighbors.put(dir, BlockPos.of(wasdTag.getLong(key)));
                }
            }
        }

        boundaryPoints.clear();
        if (tag.contains("boundaryPoints")) {
            long[] pts = tag.getLongArray("boundaryPoints");
            for (long p : pts) {
                boundaryPoints.add(BlockPos.of(p));
            }
        }
        recalcBounds();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            load(pkt.getTag());
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }
}
