package dev.slc.network;

import dev.slc.client.ClientMapZoneCache;
import dev.slc.role.Role;
import dev.slc.zone.SpatialZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Compact OP map-editor visualization data. No map configuration is trusted from the client. */
public record S2CMapZonesPacket(AABB escapeZone, Map<Role, List<AABB>> spawnZones,
                                Map<Role, List<BlockPos>> spawnPoints, List<AABB> elevatorCabins) {
    public static S2CMapZonesPacket from(SpatialZoneManager zones) {
        Map<Role, List<AABB>> roleZones = new EnumMap<>(Role.class);
        Map<Role, List<BlockPos>> points = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            roleZones.put(role, zones.getRoleSpawnZones(role).stream().map(SpatialZoneManager.SpawnZone::bounds).toList());
            points.put(role, zones.getRoleSpawnPoints(role).stream().map(SpatialZoneManager.SpawnPoint::pos).toList());
        }
        return new S2CMapZonesPacket(zones.getEscapeZone(), roleZones, points, new ArrayList<>(zones.getElevatorCabins().values()));
    }

    public static void encode(S2CMapZonesPacket msg, FriendlyByteBuf buf) {
        writeBox(buf, msg.escapeZone);
        for (Role role : Role.values()) {
            List<AABB> zones = msg.spawnZones.getOrDefault(role, List.of());
            buf.writeVarInt(zones.size());
            for (AABB box : zones) writeBox(buf, box);
            List<BlockPos> points = msg.spawnPoints.getOrDefault(role, List.of());
            buf.writeVarInt(points.size());
            for (BlockPos point : points) buf.writeBlockPos(point);
        }
        buf.writeVarInt(msg.elevatorCabins.size());
        for (AABB box : msg.elevatorCabins) writeBox(buf, box);
    }

    public static S2CMapZonesPacket decode(FriendlyByteBuf buf) {
        AABB escape = readBox(buf);
        Map<Role, List<AABB>> zones = new EnumMap<>(Role.class);
        Map<Role, List<BlockPos>> points = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            int zoneCount = buf.readVarInt();
            List<AABB> roleZones = new ArrayList<>();
            for (int i = 0; i < zoneCount; i++) roleZones.add(readBox(buf));
            zones.put(role, roleZones);
            int pointCount = buf.readVarInt();
            List<BlockPos> rolePoints = new ArrayList<>();
            for (int i = 0; i < pointCount; i++) rolePoints.add(buf.readBlockPos());
            points.put(role, rolePoints);
        }
        int cabinCount = buf.readVarInt();
        List<AABB> cabins = new ArrayList<>();
        for (int i = 0; i < cabinCount; i++) cabins.add(readBox(buf));
        return new S2CMapZonesPacket(escape, zones, points, cabins);
    }

    public static void handle(S2CMapZonesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientMapZoneCache.set(msg.escapeZone, msg.spawnZones, msg.spawnPoints, msg.elevatorCabins));
        ctx.get().setPacketHandled(true);
    }

    private static void writeBox(FriendlyByteBuf buf, AABB box) {
        buf.writeBoolean(box != null);
        if (box == null) return;
        buf.writeDouble(box.minX);
        buf.writeDouble(box.minY);
        buf.writeDouble(box.minZ);
        buf.writeDouble(box.maxX);
        buf.writeDouble(box.maxY);
        buf.writeDouble(box.maxZ);
    }

    private static AABB readBox(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        return new AABB(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
