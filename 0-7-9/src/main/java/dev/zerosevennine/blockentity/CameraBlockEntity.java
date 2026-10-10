package dev.zerosevennine.blockentity;

import dev.zerosevennine.facility.DeviceType;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

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

    private int blackoutTimer = 0;
    private int lockdownTimer = 0;

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CameraBlockEntity be) {
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
                return;
            }
        }
        linkedDevices.add(new LinkedDevice(devPos, type));
        setChanged();
    }

    public void removeDevice(BlockPos devPos) {
        linkedDevices.removeIf(d -> d.pos.equals(devPos));
        setChanged();
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
    }

    // Ability Handlers
    public boolean toggleDoor(BlockPos doorPos) {
        if (level == null) return false;
        BlockState state = level.getBlockState(doorPos);
        if (state.getBlock() instanceof DoorBlock) {
            boolean open = state.getValue(DoorBlock.OPEN);
            level.setBlock(doorPos, state.setValue(DoorBlock.OPEN, !open), 3);
            return true;
        }
        return false;
    }

    public boolean lockDoor(BlockPos doorPos, boolean lock) {
        for (LinkedDevice d : linkedDevices) {
            if (d.pos.equals(doorPos) && d.type == DeviceType.DOOR) {
                d.isLocked = lock;
                if (lock && level != null) {
                    BlockState state = level.getBlockState(doorPos);
                    if (state.getBlock() instanceof DoorBlock) {
                        level.setBlock(doorPos, state.setValue(DoorBlock.OPEN, false), 3);
                    }
                }
                setChanged();
                return true;
            }
        }
        return false;
    }

    public void triggerTesla(BlockPos teslaPos) {
        if (level == null) return;
        // Pulse redstone / damage living entities in radius
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
}
