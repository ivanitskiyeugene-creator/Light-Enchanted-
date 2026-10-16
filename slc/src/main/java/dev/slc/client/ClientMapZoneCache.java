package dev.slc.client;

import dev.slc.role.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Client-only copy of persisted map volumes, sent exclusively to an OP using a map tool. */
public final class ClientMapZoneCache {
    private static AABB escapeZone;
    private static final Map<Role, List<AABB>> SPAWN_ZONES = new EnumMap<>(Role.class);
    private static final Map<Role, List<BlockPos>> SPAWN_POINTS = new EnumMap<>(Role.class);
    private static List<AABB> elevatorCabins = List.of();

    private ClientMapZoneCache() {}

    public static void set(AABB escape, Map<Role, List<AABB>> zones, Map<Role, List<BlockPos>> points, List<AABB> cabins) {
        escapeZone = escape;
        SPAWN_ZONES.clear();
        SPAWN_POINTS.clear();
        zones.forEach((role, list) -> SPAWN_ZONES.put(role, List.copyOf(list)));
        points.forEach((role, list) -> SPAWN_POINTS.put(role, List.copyOf(list)));
        elevatorCabins = List.copyOf(cabins);
    }

    public static AABB escapeZone() { return escapeZone; }
    public static List<AABB> spawnZones(Role role) { return SPAWN_ZONES.getOrDefault(role, Collections.emptyList()); }
    public static List<BlockPos> spawnPoints(Role role) { return SPAWN_POINTS.getOrDefault(role, Collections.emptyList()); }
    public static List<AABB> elevatorCabins() { return elevatorCabins; }
}
