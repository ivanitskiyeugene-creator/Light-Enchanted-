package dev.slc.zone;

import dev.slc.role.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.*;

/** Persistent physical setup for an SLC map: escape volume, role anchors/regions and lift cabins. */
public class SpatialZoneManager extends SavedData {
    private static final String DATA_NAME = "slc_spatial_zones";

    private AABB escapeZone = null;
    private final Map<Role, List<SpawnPoint>> roleSpawnPoints = new EnumMap<>(Role.class);
    private final Map<Role, List<SpawnZone>> roleSpawnZones = new EnumMap<>(Role.class);
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
        addRoleSpawn(role, pos, 0.0F, 0.0F);
    }

    public void addRoleSpawn(Role role, BlockPos pos, float yaw, float pitch) {
        roleSpawnPoints.computeIfAbsent(role, r -> new ArrayList<>()).add(new SpawnPoint(pos, yaw, pitch));
        setDirty();
    }

    /** Legacy position-only view for callers that do not need facing information. */
    public List<BlockPos> getRoleSpawns(Role role) {
        return roleSpawnPoints.getOrDefault(role, Collections.emptyList()).stream().map(SpawnPoint::pos).toList();
    }

    public List<SpawnPoint> getRoleSpawnPoints(Role role) {
        return List.copyOf(roleSpawnPoints.getOrDefault(role, Collections.emptyList()));
    }

    public void clearRoleSpawns(Role role) {
        roleSpawnPoints.remove(role);
        roleSpawnZones.remove(role);
        setDirty();
    }

    public void addRoleSpawnZone(Role role, AABB bounds, float yaw, float pitch) {
        roleSpawnZones.computeIfAbsent(role, r -> new ArrayList<>()).add(new SpawnZone(bounds, yaw, pitch));
        setDirty();
    }

    public List<SpawnZone> getRoleSpawnZones(Role role) {
        return List.copyOf(roleSpawnZones.getOrDefault(role, Collections.emptyList()));
    }

    /** Chooses uniformly from point anchors and boxed spawn regions; returns null when role has none. */
    public SpawnTarget getRandomRoleSpawn(ServerLevel level, Role role, Random random) {
        List<SpawnPoint> points = roleSpawnPoints.getOrDefault(role, Collections.emptyList());
        List<SpawnZone> zones = roleSpawnZones.getOrDefault(role, Collections.emptyList());
        int count = points.size() + zones.size();
        if (count == 0) return null;
        int choice = random.nextInt(count);
        if (choice < points.size()) {
            SpawnPoint point = points.get(choice);
            return new SpawnTarget(point.pos.getX() + 0.5D, point.pos.getY(), point.pos.getZ() + 0.5D, point.yaw, point.pitch);
        }
        SpawnZone zone = zones.get(choice - points.size());
        return randomPointInZone(level, zone, random);
    }

    private SpawnTarget randomPointInZone(ServerLevel level, SpawnZone zone, Random random) {
        AABB box = zone.bounds;
        int minX = (int) Math.floor(box.minX);
        int maxX = Math.max(minX, (int) Math.ceil(box.maxX) - 1);
        int minY = (int) Math.floor(box.minY);
        int maxY = Math.max(minY, (int) Math.ceil(box.maxY) - 1);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = Math.max(minZ, (int) Math.ceil(box.maxZ) - 1);
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);
            for (int y = maxY; y >= minY; y--) {
                BlockPos candidate = new BlockPos(x, y, z);
                BlockState feet = level.getBlockState(candidate);
                BlockState head = level.getBlockState(candidate.above());
                if (feet.isAir() && head.isAir() && !level.getBlockState(candidate.below()).isAir()) {
                    return new SpawnTarget(x + 0.5D, y, z + 0.5D, zone.yaw, zone.pitch);
                }
            }
        }
        return new SpawnTarget((box.minX + box.maxX) / 2.0D, box.minY, (box.minZ + box.maxZ) / 2.0D, zone.yaw, zone.pitch);
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

    public boolean hasAnySpawn(Role role) {
        return !roleSpawnPoints.getOrDefault(role, Collections.emptyList()).isEmpty()
                || !roleSpawnZones.getOrDefault(role, Collections.emptyList()).isEmpty();
    }

    public static SpatialZoneManager load(CompoundTag nbt) {
        SpatialZoneManager mgr = new SpatialZoneManager();

        if (nbt.contains("EscapeZone")) {
            CompoundTag esc = nbt.getCompound("EscapeZone");
            mgr.escapeZone = readBox(esc);
        }

        if (nbt.contains("Spawns")) {
            CompoundTag spawnsTag = nbt.getCompound("Spawns");
            for (String roleKey : spawnsTag.getAllKeys()) {
                try {
                    Role role = Role.valueOf(roleKey);
                    ListTag list = spawnsTag.getList(roleKey, Tag.TAG_COMPOUND);
                    List<SpawnPoint> posList = new ArrayList<>();
                    for (int i = 0; i < list.size(); i++) {
                        CompoundTag posTag = list.getCompound(i);
                        posList.add(new SpawnPoint(
                                new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")),
                                posTag.getFloat("yaw"), posTag.getFloat("pitch")
                        ));
                    }
                    mgr.roleSpawnPoints.put(role, posList);
                } catch (IllegalArgumentException ignored) { }
            }
        }

        if (nbt.contains("SpawnZones")) {
            CompoundTag zonesTag = nbt.getCompound("SpawnZones");
            for (String roleKey : zonesTag.getAllKeys()) {
                try {
                    Role role = Role.valueOf(roleKey);
                    ListTag list = zonesTag.getList(roleKey, Tag.TAG_COMPOUND);
                    List<SpawnZone> zones = new ArrayList<>();
                    for (int i = 0; i < list.size(); i++) {
                        CompoundTag tag = list.getCompound(i);
                        zones.add(new SpawnZone(readBox(tag), tag.getFloat("yaw"), tag.getFloat("pitch")));
                    }
                    mgr.roleSpawnZones.put(role, zones);
                } catch (IllegalArgumentException ignored) { }
            }
        }

        if (nbt.contains("ElevatorCabins")) {
            ListTag cabinsList = nbt.getList("ElevatorCabins", Tag.TAG_COMPOUND);
            for (int i = 0; i < cabinsList.size(); i++) {
                CompoundTag tag = cabinsList.getCompound(i);
                BlockPos btnPos = new BlockPos(tag.getInt("bx"), tag.getInt("by"), tag.getInt("bz"));
                mgr.elevatorCabins.put(btnPos, readBox(tag));
            }
        }
        return mgr;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        if (escapeZone != null) nbt.put("EscapeZone", writeBox(escapeZone));

        CompoundTag spawnsTag = new CompoundTag();
        for (Map.Entry<Role, List<SpawnPoint>> entry : roleSpawnPoints.entrySet()) {
            ListTag list = new ListTag();
            for (SpawnPoint point : entry.getValue()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("x", point.pos.getX());
                posTag.putInt("y", point.pos.getY());
                posTag.putInt("z", point.pos.getZ());
                posTag.putFloat("yaw", point.yaw);
                posTag.putFloat("pitch", point.pitch);
                list.add(posTag);
            }
            spawnsTag.put(entry.getKey().name(), list);
        }
        nbt.put("Spawns", spawnsTag);

        CompoundTag zonesTag = new CompoundTag();
        for (Map.Entry<Role, List<SpawnZone>> entry : roleSpawnZones.entrySet()) {
            ListTag list = new ListTag();
            for (SpawnZone zone : entry.getValue()) {
                CompoundTag tag = writeBox(zone.bounds);
                tag.putFloat("yaw", zone.yaw);
                tag.putFloat("pitch", zone.pitch);
                list.add(tag);
            }
            zonesTag.put(entry.getKey().name(), list);
        }
        nbt.put("SpawnZones", zonesTag);

        ListTag cabinsList = new ListTag();
        for (Map.Entry<BlockPos, AABB> entry : elevatorCabins.entrySet()) {
            CompoundTag tag = writeBox(entry.getValue());
            tag.putInt("bx", entry.getKey().getX());
            tag.putInt("by", entry.getKey().getY());
            tag.putInt("bz", entry.getKey().getZ());
            cabinsList.add(tag);
        }
        nbt.put("ElevatorCabins", cabinsList);
        return nbt;
    }

    private static CompoundTag writeBox(AABB box) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("minX", box.minX);
        tag.putDouble("minY", box.minY);
        tag.putDouble("minZ", box.minZ);
        tag.putDouble("maxX", box.maxX);
        tag.putDouble("maxY", box.maxY);
        tag.putDouble("maxZ", box.maxZ);
        return tag;
    }

    private static AABB readBox(CompoundTag tag) {
        return new AABB(tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"),
                tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ"));
    }

    public record SpawnPoint(BlockPos pos, float yaw, float pitch) {}
    public record SpawnZone(AABB bounds, float yaw, float pitch) {}
    public record SpawnTarget(double x, double y, double z, float yaw, float pitch) {}
}
