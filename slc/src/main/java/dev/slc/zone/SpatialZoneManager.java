package dev.slc.zone;

import dev.slc.role.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class SpatialZoneManager extends SavedData {
    private static final String DATA_NAME = "slc_spatial_zones";

    private AABB escapeZone = null;
    private final Map<Role, List<BlockPos>> roleSpawnPoints = new EnumMap<>(Role.class);
    private final Map<BlockPos, AABB> elevatorCabins = new HashMap<>();

    public static SpatialZoneManager get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                SpatialZoneManager::load,
                SpatialZoneManager::new,
                DATA_NAME
        );
    }

    public AABB getEscapeZone() {
        return escapeZone;
    }

    public void setEscapeZone(AABB aabb) {
        this.escapeZone = aabb;
        setDirty();
    }

    public void addRoleSpawn(Role role, BlockPos pos) {
        roleSpawnPoints.computeIfAbsent(role, r -> new ArrayList<>()).add(pos);
        setDirty();
    }

    public List<BlockPos> getRoleSpawns(Role role) {
        return roleSpawnPoints.getOrDefault(role, Collections.emptyList());
    }

    public void clearRoleSpawns(Role role) {
        roleSpawnPoints.remove(role);
        setDirty();
    }

    public void setElevatorCabin(BlockPos buttonPos, AABB cabinAABB) {
        elevatorCabins.put(buttonPos, cabinAABB);
        setDirty();
    }

    public AABB getElevatorCabin(BlockPos buttonPos) {
        return elevatorCabins.get(buttonPos);
    }

    public void removeElevatorCabin(BlockPos buttonPos) {
        elevatorCabins.remove(buttonPos);
        setDirty();
    }

    public static SpatialZoneManager load(CompoundTag nbt) {
        SpatialZoneManager mgr = new SpatialZoneManager();

        if (nbt.contains("EscapeZone")) {
            CompoundTag esc = nbt.getCompound("EscapeZone");
            mgr.escapeZone = new AABB(
                    esc.getDouble("minX"), esc.getDouble("minY"), esc.getDouble("minZ"),
                    esc.getDouble("maxX"), esc.getDouble("maxY"), esc.getDouble("maxZ")
            );
        }

        if (nbt.contains("Spawns")) {
            CompoundTag spawnsTag = nbt.getCompound("Spawns");
            for (String roleKey : spawnsTag.getAllKeys()) {
                try {
                    Role role = Role.valueOf(roleKey);
                    ListTag list = spawnsTag.getList(roleKey, Tag.TAG_COMPOUND);
                    List<BlockPos> posList = new ArrayList<>();
                    for (int i = 0; i < list.size(); i++) {
                        CompoundTag posTag = list.getCompound(i);
                        posList.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
                    }
                    mgr.roleSpawnPoints.put(role, posList);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        if (nbt.contains("ElevatorCabins")) {
            ListTag cabinsList = nbt.getList("ElevatorCabins", Tag.TAG_COMPOUND);
            for (int i = 0; i < cabinsList.size(); i++) {
                CompoundTag tag = cabinsList.getCompound(i);
                BlockPos btnPos = new BlockPos(tag.getInt("bx"), tag.getInt("by"), tag.getInt("bz"));
                AABB cabin = new AABB(
                        tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"),
                        tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ")
                );
                mgr.elevatorCabins.put(btnPos, cabin);
            }
        }

        return mgr;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        if (escapeZone != null) {
            CompoundTag esc = new CompoundTag();
            esc.putDouble("minX", escapeZone.minX);
            esc.putDouble("minY", escapeZone.minY);
            esc.putDouble("minZ", escapeZone.minZ);
            esc.putDouble("maxX", escapeZone.maxX);
            esc.putDouble("maxY", escapeZone.maxY);
            esc.putDouble("maxZ", escapeZone.maxZ);
            nbt.put("EscapeZone", esc);
        }

        CompoundTag spawnsTag = new CompoundTag();
        for (Map.Entry<Role, List<BlockPos>> entry : roleSpawnPoints.entrySet()) {
            ListTag list = new ListTag();
            for (BlockPos pos : entry.getValue()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("x", pos.getX());
                posTag.putInt("y", pos.getY());
                posTag.putInt("z", pos.getZ());
                list.add(posTag);
            }
            spawnsTag.put(entry.getKey().name(), list);
        }
        nbt.put("Spawns", spawnsTag);

        ListTag cabinsList = new ListTag();
        for (Map.Entry<BlockPos, AABB> entry : elevatorCabins.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("bx", entry.getKey().getX());
            tag.putInt("by", entry.getKey().getY());
            tag.putInt("bz", entry.getKey().getZ());
            AABB c = entry.getValue();
            tag.putDouble("minX", c.minX);
            tag.putDouble("minY", c.minY);
            tag.putDouble("minZ", c.minZ);
            tag.putDouble("maxX", c.maxX);
            tag.putDouble("maxY", c.maxY);
            tag.putDouble("maxZ", c.maxZ);
            cabinsList.add(tag);
        }
        nbt.put("ElevatorCabins", cabinsList);

        return nbt;
    }
}
