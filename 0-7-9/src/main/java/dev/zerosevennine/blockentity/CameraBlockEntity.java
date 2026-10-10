package dev.zerosevennine.blockentity;

import dev.zerosevennine.facility.DeviceType;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.UniversalDoorHandler;
import dev.zerosevennine.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            FacilityNetworkManager.registerCamera(worldPosition, this);
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
                    UniversalDoorHandler.notifyNeighbors(level, pulse.pos);
                }
            }
        }

        // Live generator monitoring
        if (!level.isClientSide && level.getGameTime() % 20 == 0) {
            be.monitorGenerators(level);
        }
    }

    private boolean lastKnownGenBooting = false;
    private boolean lastKnownGenEngaged = false;

    private void monitorGenerators(Level level) {
        for (LinkedDevice d : linkedDevices) {
            if (d.type == DeviceType.GENERATOR) {
                dev.zerosevennine.facility.UniversalGeneratorHandler.GeneratorState genState =
                        dev.zerosevennine.facility.UniversalGeneratorHandler.getGeneratorState(level, d.pos);
                if (genState.exists) {
                    if (genState.isEngaging && !lastKnownGenBooting) {
                        lastKnownGenBooting = true;
                        FacilityNetworkManager.onGeneratorBooting(cameraName, d.pos);
                        FacilityNetworkManager.RoomNode r = FacilityNetworkManager.getRooms().get(worldPosition);
                        if (r != null) {
                            r.hasGenerator = true;
                            r.isGeneratorBooting = true;
                        }
                    } else if (genState.isEngaged && !lastKnownGenEngaged) {
                        lastKnownGenEngaged = true;
                        lastKnownGenBooting = false;
                        FacilityNetworkManager.onGeneratorOvercharged(cameraName, d.pos, level);
                        FacilityNetworkManager.RoomNode r = FacilityNetworkManager.getRooms().get(worldPosition);
                        if (r != null) {
                            r.hasGenerator = true;
                            r.isGeneratorBooting = false;
                        }
                    } else if (!genState.isEngaging && !genState.isEngaged) {
                        lastKnownGenBooting = false;
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
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public FacilityZone getZone() {
        return zone;
    }

    public void setZone(FacilityZone zone) {
        this.zone = zone;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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
    public boolean toggleDoor(BlockPos doorPos, ServerPlayer player) {
        if (level == null) return false;

        // Check lock status
        for (LinkedDevice d : linkedDevices) {
            if ((d.pos.equals(doorPos) || d.pos.equals(doorPos.below()) || d.pos.equals(doorPos.above())) && d.isLocked) {
                return false;
            }
        }

        boolean toggled = UniversalDoorHandler.toggleDoor(level, doorPos, player);
        if (toggled) {
            activePulses.add(new ActivePulse(doorPos, 20));
            return true;
        }
        return false;
    }

    public boolean lockDoor(BlockPos doorPos, ServerPlayer player, boolean lock) {
        if (level == null) return false;

        boolean found = false;
        for (LinkedDevice d : linkedDevices) {
            if (d.pos.equals(doorPos) || d.pos.equals(doorPos.below()) || d.pos.equals(doorPos.above())) {
                d.isLocked = lock;
                found = true;
                break;
            }
        }

        if (!found) {
            BlockState state = level.getBlockState(doorPos);
            if (UniversalDoorHandler.isDoorLikeBlock(state)) {
                LinkedDevice newDev = new LinkedDevice(doorPos, DeviceType.DOOR);
                newDev.isLocked = lock;
                linkedDevices.add(newDev);
                found = true;
            }
        }

        if (lock) {
            UniversalDoorHandler.closeDoor(level, doorPos, player);
        }

        setChanged();
        if (!level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return found;
    }

    public void triggerTesla(BlockPos teslaPos, ServerPlayer player) {
        if (level == null) return;
        UniversalDoorHandler.triggerTeslaGate(level, teslaPos, player);
    }

    public void triggerBlackout(int durationTicks) {
        this.blackoutTimer = durationTicks;
        setChanged();
    }

    public void triggerLockdown(int durationTicks, ServerPlayer player) {
        this.lockdownTimer = durationTicks;
        for (LinkedDevice d : linkedDevices) {
            if (d.type == DeviceType.DOOR) {
                lockDoor(d.pos, player, true);
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
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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
