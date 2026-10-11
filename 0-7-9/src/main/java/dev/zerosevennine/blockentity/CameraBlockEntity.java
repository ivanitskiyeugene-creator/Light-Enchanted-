package dev.zerosevennine.blockentity;

import dev.zerosevennine.facility.UniversalDoorHandler;
import dev.zerosevennine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class CameraBlockEntity extends BlockEntity {

    private String cameraName = "CAM-01";
    private String roomName = "Heavy Containment Zone";
    private String zoneName = "HCZ";
    private boolean active = false;
    private UUID activeViewerUUID = null;

    private float currentYaw = 0.0f;
    private float currentPitch = 0.0f;
    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;

    private final Set<BlockPos> linkedDoors = new HashSet<>();
    private final Set<BlockPos> linkedTeslaGates = new HashSet<>();
    private final Set<BlockPos> linkedGenerators = new HashSet<>();
    private final Set<BlockPos> linkedElevators = new HashSet<>();
    private final Set<BlockPos> lockedDoors = new HashSet<>();

    private final Map<BlockPos, Integer> activeRedstonePulses = new HashMap<>();

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CameraBlockEntity be) {
        if (!be.activeRedstonePulses.isEmpty()) {
            Iterator<Map.Entry<BlockPos, Integer>> it = be.activeRedstonePulses.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, Integer> entry = it.next();
                int ticksLeft = entry.getValue() - 1;
                if (ticksLeft <= 0) {
                    it.remove();
                    UniversalDoorHandler.notifyNeighbors(level, entry.getKey());
                } else {
                    entry.setValue(ticksLeft);
                }
            }
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CameraBlockEntity be) {
        if (be.active) {
            be.currentYaw = lerpAngle(be.currentYaw, be.targetYaw, 0.25f);
            be.currentPitch = lerpAngle(be.currentPitch, be.targetPitch, 0.25f);
        } else {
            be.currentYaw = lerpAngle(be.currentYaw, 0.0f, 0.05f);
            be.currentPitch = lerpAngle(be.currentPitch, 0.0f, 0.05f);
        }
    }

    private static float lerpAngle(float current, float target, float speed) {
        float diff = (target - current) % 360.0f;
        if (diff > 180.0f) diff -= 360.0f;
        if (diff < -180.0f) diff += 360.0f;
        return current + diff * speed;
    }

    public void updateTargetAngles(float yaw, float pitch) {
        this.targetYaw = yaw;
        this.targetPitch = pitch;
        if (this.level != null && !this.level.isClientSide) {
            this.setChanged();
        }
    }

    public boolean toggleDoor(BlockPos doorPos, Player player) {
        if (level == null) return false;
        boolean isLocked = lockedDoors.contains(doorPos);
        if (isLocked) {
            return false;
        }

        activeRedstonePulses.put(doorPos, 20);
        boolean success = UniversalDoorHandler.toggleDoor(level, doorPos, player);
        UniversalDoorHandler.notifyNeighbors(level, doorPos);
        return success;
    }

    public boolean toggleLockDoor(BlockPos doorPos, Player player) {
        if (level == null) return false;
        boolean nowLocked;
        if (lockedDoors.contains(doorPos)) {
            lockedDoors.remove(doorPos);
            nowLocked = false;
        } else {
            lockedDoors.add(doorPos);
            nowLocked = true;
        }

        UniversalDoorHandler.setAreaLockState(level, doorPos, nowLocked, player);
        setChanged();
        return nowLocked;
    }

    public boolean triggerTesla(BlockPos teslaPos, Player player) {
        if (level == null) return false;
        activeRedstonePulses.put(teslaPos, 40);
        boolean success = UniversalDoorHandler.triggerTeslaGate(level, teslaPos, player);
        UniversalDoorHandler.notifyNeighbors(level, teslaPos);
        return success;
    }

    public boolean isDoorLocked(BlockPos pos) {
        return lockedDoors.contains(pos);
    }

    public boolean hasActiveRedstonePulse(BlockPos pos) {
        return activeRedstonePulses.containsKey(pos) && activeRedstonePulses.get(pos) > 0;
    }

    public void addLinkedDoor(BlockPos pos) {
        linkedDoors.add(pos);
        setChanged();
    }

    public void removeLinkedDoor(BlockPos pos) {
        linkedDoors.remove(pos);
        lockedDoors.remove(pos);
        setChanged();
    }

    public void addLinkedTeslaGate(BlockPos pos) {
        linkedTeslaGates.add(pos);
        setChanged();
    }

    public void removeLinkedTeslaGate(BlockPos pos) {
        linkedTeslaGates.remove(pos);
        setChanged();
    }

    public void addLinkedGenerator(BlockPos pos) {
        linkedGenerators.add(pos);
        setChanged();
    }

    public void addLinkedElevator(BlockPos pos) {
        linkedElevators.add(pos);
        setChanged();
    }

    public Set<BlockPos> getLinkedDoors() { return Collections.unmodifiableSet(linkedDoors); }
    public Set<BlockPos> getLinkedTeslaGates() { return Collections.unmodifiableSet(linkedTeslaGates); }
    public Set<BlockPos> getLinkedGenerators() { return Collections.unmodifiableSet(linkedGenerators); }
    public Set<BlockPos> getLinkedElevators() { return Collections.unmodifiableSet(linkedElevators); }
    public Set<BlockPos> getLockedDoors() { return Collections.unmodifiableSet(lockedDoors); }

    public String getCameraName() { return cameraName; }
    public void setCameraName(String cameraName) { this.cameraName = cameraName; setChanged(); }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; setChanged(); }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; setChanged(); }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; setChanged(); }

    public UUID getActiveViewerUUID() { return activeViewerUUID; }
    public void setActiveViewerUUID(UUID activeViewerUUID) { this.activeViewerUUID = activeViewerUUID; setChanged(); }

    public float getCurrentYaw() { return currentYaw; }
    public float getCurrentPitch() { return currentPitch; }
    public float getTargetYaw() { return targetYaw; }
    public float getTargetPitch() { return targetPitch; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("CameraName", cameraName);
        tag.putString("RoomName", roomName);
        tag.putString("ZoneName", zoneName);
        tag.putBoolean("Active", active);
        if (activeViewerUUID != null) {
            tag.putUUID("ViewerUUID", activeViewerUUID);
        }
        tag.putFloat("TargetYaw", targetYaw);
        tag.putFloat("TargetPitch", targetPitch);

        tag.put("LinkedDoors", savePosList(linkedDoors));
        tag.put("LinkedTesla", savePosList(linkedTeslaGates));
        tag.put("LinkedGenerators", savePosList(linkedGenerators));
        tag.put("LinkedElevators", savePosList(linkedElevators));
        tag.put("LockedDoors", savePosList(lockedDoors));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("CameraName")) cameraName = tag.getString("CameraName");
        if (tag.contains("RoomName")) roomName = tag.getString("RoomName");
        if (tag.contains("ZoneName")) zoneName = tag.getString("ZoneName");
        if (tag.contains("Active")) active = tag.getBoolean("Active");
        if (tag.hasUUID("ViewerUUID")) activeViewerUUID = tag.getUUID("ViewerUUID");
        if (tag.contains("TargetYaw")) targetYaw = tag.getFloat("TargetYaw");
        if (tag.contains("TargetPitch")) targetPitch = tag.getFloat("TargetPitch");

        loadPosList(tag.getList("LinkedDoors", Tag.TAG_COMPOUND), linkedDoors);
        loadPosList(tag.getList("LinkedTesla", Tag.TAG_COMPOUND), linkedTeslaGates);
        loadPosList(tag.getList("LinkedGenerators", Tag.TAG_COMPOUND), linkedGenerators);
        loadPosList(tag.getList("LinkedElevators", Tag.TAG_COMPOUND), linkedElevators);
        loadPosList(tag.getList("LockedDoors", Tag.TAG_COMPOUND), lockedDoors);
    }

    private ListTag savePosList(Set<BlockPos> set) {
        ListTag list = new ListTag();
        for (BlockPos p : set) {
            list.add(NbtUtils.writeBlockPos(p));
        }
        return list;
    }

    private void loadPosList(ListTag list, Set<BlockPos> targetSet) {
        targetSet.clear();
        for (int i = 0; i < list.size(); i++) {
            targetSet.add(NbtUtils.readBlockPos(list.getCompound(i)));
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
