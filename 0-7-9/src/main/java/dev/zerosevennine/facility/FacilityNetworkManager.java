package dev.zerosevennine.facility;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.config.Scp079Config;
import dev.zerosevennine.network.ModNetwork;
import dev.zerosevennine.system.Scp079PlayerManager;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

public class FacilityNetworkManager {
    public static class RoomNode {
        public BlockPos pos;
        public String roomName;
        public FacilityZone zone;
        public FacilityMapNodeBlockEntity.RoomType roomType;
        public RoomShape roomShape;
        public int rotation;
        public FacilityZone targetZone;
        public int gridX;
        public int gridY;
        public boolean hasGenerator;
        public boolean isGeneratorBooting;
        public final List<BlockPos> linkedCameras = new ArrayList<>();

        public RoomNode(BlockPos pos, String roomName, FacilityZone zone,
                        FacilityMapNodeBlockEntity.RoomType roomType, RoomShape roomShape, int rotation,
                        FacilityZone targetZone, int gridX, int gridY) {
            this.pos = pos != null ? pos.immutable() : new BlockPos(gridX * 16, 64, gridY * 16);
            this.roomName = roomName != null ? roomName : "ROOM";
            this.zone = zone != null ? zone : FacilityZone.HCZ;
            this.roomType = roomType != null ? roomType : FacilityMapNodeBlockEntity.RoomType.STANDARD;
            this.roomShape = roomShape != null ? roomShape : RoomShape.SQUARE;
            this.rotation = rotation;
            this.targetZone = targetZone != null ? targetZone : this.zone;
            this.gridX = gridX;
            this.gridY = gridY;
            this.hasGenerator = false;
            this.isGeneratorBooting = false;
        }

        public String getKey() {
            return zone.name() + "_" + gridX + "_" + gridY;
        }
    }

    private static final Map<String, RoomNode> ROOMS = new LinkedHashMap<>();
    private static final Map<BlockPos, CameraBlockEntity> CAMERAS = new HashMap<>();

    public static String getRoomKey(FacilityZone zone, int gx, int gy) {
        return zone.name() + "_" + gx + "_" + gy;
    }

    public static void clearAllRooms() {
        ROOMS.clear();
    }

    public static void registerRoomNode(RoomNode node) {
        if (node == null) return;
        ROOMS.put(node.getKey(), node);
    }

    public static void removeRoom(FacilityZone zone, int gx, int gy) {
        ROOMS.remove(getRoomKey(zone, gx, gy));
    }

    public static void clearZone(FacilityZone zone) {
        ROOMS.entrySet().removeIf(e -> e.getValue().zone == zone);
    }

    public static RoomNode getRoomAt(FacilityZone zone, int gx, int gy) {
        return ROOMS.get(getRoomKey(zone, gx, gy));
    }

    public static List<RoomNode> getRoomsInZone(FacilityZone zone) {
        List<RoomNode> list = new ArrayList<>();
        for (RoomNode r : ROOMS.values()) {
            if (r.zone == zone) {
                list.add(r);
            }
        }
        return list;
    }

    public static RoomNode findRoomForCamera(BlockPos camPos) {
        if (camPos == null) return null;
        for (RoomNode r : ROOMS.values()) {
            if (r.linkedCameras.contains(camPos) || r.pos.equals(camPos)) {
                return r;
            }
        }
        return null;
    }

    public static void registerCamera(BlockPos pos, CameraBlockEntity be) {
        if (pos == null || be == null) return;
        CAMERAS.put(pos.immutable(), be);
    }

    public static void unregisterCamera(BlockPos pos) {
        if (pos == null) return;
        CAMERAS.remove(pos);
        for (RoomNode r : ROOMS.values()) {
            r.linkedCameras.remove(pos);
        }
    }

    public static Map<String, RoomNode> getRooms() {
        return ROOMS;
    }

    public static Map<BlockPos, CameraBlockEntity> getCameras() {
        return CAMERAS;
    }

    public static CameraBlockEntity getCamera(BlockPos pos) {
        if (pos == null) return null;
        return CAMERAS.get(pos);
    }

    public static BlockPos findCameraInZone(FacilityZone zone) {
        for (RoomNode r : getRoomsInZone(zone)) {
            if (!r.linkedCameras.isEmpty()) {
                return r.linkedCameras.get(0);
            }
        }
        for (Map.Entry<BlockPos, CameraBlockEntity> entry : CAMERAS.entrySet()) {
            if (entry.getValue().getZone() == zone) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static BlockPos findNextCamera(BlockPos currentPos, Direction direction) {
        if (currentPos == null) return null;

        CameraBlockEntity current = CAMERAS.get(currentPos);
        if (current != null) {
            BlockPos manualTarget = current.getWasdNeighbors().get(direction);
            if (manualTarget != null && (CAMERAS.containsKey(manualTarget) || manualTarget != null)) {
                return manualTarget;
            }
        }

        // Grid-based automatic navigation lookup
        RoomNode currentRoom = findRoomForCamera(currentPos);
        if (currentRoom != null) {
            int nx = currentRoom.gridX;
            int ny = currentRoom.gridY;
            switch (direction) {
                case NORTH -> ny -= 1;
                case SOUTH -> ny += 1;
                case WEST -> nx -= 1;
                case EAST -> nx += 1;
                default -> {}
            }
            RoomNode nextRoom = getRoomAt(currentRoom.zone, nx, ny);
            if (nextRoom != null && !nextRoom.linkedCameras.isEmpty()) {
                return nextRoom.linkedCameras.get(0);
            }
        }

        // Automatic spatial fallback
        double bestDist = Double.MAX_VALUE;
        BlockPos bestPos = null;

        for (BlockPos p : CAMERAS.keySet()) {
            if (p.equals(currentPos)) continue;
            double dx = p.getX() - currentPos.getX();
            double dz = p.getZ() - currentPos.getZ();

            boolean matches = switch (direction) {
                case NORTH -> (dz < -2.0 && Math.abs(dx) < Math.abs(dz));
                case SOUTH -> (dz > 2.0 && Math.abs(dx) < Math.abs(dz));
                case WEST -> (dx < -2.0 && Math.abs(dz) < Math.abs(dx));
                case EAST -> (dx > 2.0 && Math.abs(dz) < Math.abs(dx));
                default -> false;
            };

            if (matches) {
                double dist = p.distSqr(currentPos);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPos = p;
                }
            }
        }

        return bestPos;
    }

    public static void autoLinkWasd(ServerLevel level) {
        for (FacilityZone zone : FacilityZone.values()) {
            List<RoomNode> zoneRooms = getRoomsInZone(zone);
            for (RoomNode r : zoneRooms) {
                if (r.linkedCameras.isEmpty()) continue;
                BlockPos camPos = r.linkedCameras.get(0);
                CameraBlockEntity camBe = CAMERAS.get(camPos);
                if (camBe == null && level != null && level.getBlockEntity(camPos) instanceof CameraBlockEntity cbe) {
                    camBe = cbe;
                    registerCamera(camPos, cbe);
                }
                if (camBe == null) continue;

                // North
                RoomNode northRoom = getRoomAt(zone, r.gridX, r.gridY - 1);
                if (northRoom != null && !northRoom.linkedCameras.isEmpty()) {
                    camBe.setWasdNeighbor(Direction.NORTH, northRoom.linkedCameras.get(0));
                } else {
                    camBe.setWasdNeighbor(Direction.NORTH, null);
                }

                // South
                RoomNode southRoom = getRoomAt(zone, r.gridX, r.gridY + 1);
                if (southRoom != null && !southRoom.linkedCameras.isEmpty()) {
                    camBe.setWasdNeighbor(Direction.SOUTH, southRoom.linkedCameras.get(0));
                } else {
                    camBe.setWasdNeighbor(Direction.SOUTH, null);
                }

                // West
                RoomNode westRoom = getRoomAt(zone, r.gridX - 1, r.gridY);
                if (westRoom != null && !westRoom.linkedCameras.isEmpty()) {
                    camBe.setWasdNeighbor(Direction.WEST, westRoom.linkedCameras.get(0));
                } else {
                    camBe.setWasdNeighbor(Direction.WEST, null);
                }

                // East
                RoomNode eastRoom = getRoomAt(zone, r.gridX + 1, r.gridY);
                if (eastRoom != null && !eastRoom.linkedCameras.isEmpty()) {
                    camBe.setWasdNeighbor(Direction.EAST, eastRoom.linkedCameras.get(0));
                } else {
                    camBe.setWasdNeighbor(Direction.EAST, null);
                }
            }
        }
    }

    public static void markDirtyAndSync(ServerLevel level) {
        if (level == null) return;
        FacilityMapSavedData.get(level).setDirty();
        autoLinkWasd(level);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            ModNetwork.sendFacilityMapToClient(player);
        }
    }

    private static final Set<BlockPos> OVERCHARGED_GENERATORS = new HashSet<>();

    public static void onGeneratorBooting(String roomName, BlockPos pos) {
        String msg = "Generator booting up: " + roomName;
        Scp079PlayerManager.broadcastSystemAlert(msg, 0xF39C12);
    }

    public static void onGeneratorOvercharged(String roomName, BlockPos pos, Level level) {
        OVERCHARGED_GENERATORS.add(pos.immutable());
        String msg = "Generator overcharged: " + roomName;
        Scp079PlayerManager.broadcastSystemAlert(msg, 0x2ECC71);

        for (Scp079Session session : Scp079PlayerManager.getAllSessions()) {
            session.boostApRegenForGenerator();
        }

        int reqGens = Scp079Config.COMMON != null ?
                Scp079Config.COMMON.generatorsRequiredForRecontainment.get() : 3;
        if (OVERCHARGED_GENERATORS.size() >= reqGens) {
            if (level != null && level.getServer() != null) {
                for (UUID uuid : new ArrayList<>(Scp079PlayerManager.getSessionsMap().keySet())) {
                    ServerPlayer p = level.getServer().getPlayerList().getPlayer(uuid);
                    if (p != null) {
                        Scp079PlayerManager.terminate079(p);
                    }
                }
            }
        }
    }

    public static void applyClientMapSync(List<dev.zerosevennine.network.S2CSyncFacilityMapPacket.NodeData> nodes,
                                          List<dev.zerosevennine.network.S2CSyncFacilityMapPacket.CameraData> cameras) {
        ROOMS.clear();
        for (dev.zerosevennine.network.S2CSyncFacilityMapPacket.NodeData n : nodes) {
            RoomNode node = new RoomNode(n.pos, n.name, n.zone, n.roomType, n.roomShape, n.rotation, n.targetZone, n.gridX, n.gridY);
            node.hasGenerator = n.hasGenerator;
            node.isGeneratorBooting = n.isGeneratorBooting;
            node.linkedCameras.addAll(n.linkedCameras);
            ROOMS.put(node.getKey(), node);
        }
    }
}
