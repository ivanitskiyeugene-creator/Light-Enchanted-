package dev.zerosevennine.facility;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.system.Scp079PlayerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.*;

public class FacilityNetworkManager {
    public static class RoomNode {
        public BlockPos pos;
        public String roomName;
        public FacilityZone zone;
        public FacilityMapNodeBlockEntity.RoomType roomType;
        public FacilityZone targetZone;
        public int gridX;
        public int gridY;
        public boolean hasGenerator;
        public boolean isGeneratorBooting;

        public RoomNode(BlockPos pos, String roomName, FacilityZone zone,
                        FacilityMapNodeBlockEntity.RoomType roomType, FacilityZone targetZone,
                        int gridX, int gridY) {
            this.pos = pos;
            this.roomName = roomName;
            this.zone = zone;
            this.roomType = roomType;
            this.targetZone = targetZone;
            this.gridX = gridX;
            this.gridY = gridY;
            this.hasGenerator = false;
            this.isGeneratorBooting = false;
        }
    }

    private static final Map<BlockPos, RoomNode> ROOMS = new HashMap<>();
    private static final Map<BlockPos, CameraBlockEntity> CAMERAS = new HashMap<>();

    public static void registerMapNode(BlockPos pos, FacilityMapNodeBlockEntity be) {
        RoomNode node = new RoomNode(pos.immutable(), be.getRoomName(), be.getZone(),
                be.getRoomType(), be.getTargetZone(), be.getGridX(), be.getGridY());
        node.hasGenerator = be.hasGenerator();
        node.isGeneratorBooting = be.isGeneratorBooting();
        ROOMS.put(pos.immutable(), node);
    }

    public static void unregisterMapNode(BlockPos pos) {
        ROOMS.remove(pos);
    }

    public static void registerCamera(BlockPos pos, CameraBlockEntity be) {
        CAMERAS.put(pos.immutable(), be);
    }

    public static void unregisterCamera(BlockPos pos) {
        CAMERAS.remove(pos);
    }

    public static Map<BlockPos, RoomNode> getRooms() {
        return ROOMS;
    }

    public static Map<BlockPos, CameraBlockEntity> getCameras() {
        return CAMERAS;
    }

    public static CameraBlockEntity getCamera(BlockPos pos) {
        return CAMERAS.get(pos);
    }

    public static BlockPos findCameraInZone(FacilityZone zone) {
        for (Map.Entry<BlockPos, CameraBlockEntity> entry : CAMERAS.entrySet()) {
            if (entry.getValue().getZone() == zone) {
                return entry.getKey();
            }
        }
        for (Map.Entry<BlockPos, RoomNode> entry : ROOMS.entrySet()) {
            if (entry.getValue().zone == zone) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static BlockPos findNextCamera(BlockPos currentPos, Direction direction) {
        CameraBlockEntity current = CAMERAS.get(currentPos);
        if (current != null) {
            BlockPos target = current.getWasdNeighbors().get(direction);
            if (target != null && CAMERAS.containsKey(target)) {
                return target;
            }
        }

        // Automatic spatial fallback if no manual link was defined
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

    private static final Set<BlockPos> OVERCHARGED_GENERATORS = new HashSet<>();

    public static void broadcastGeneratorWarning(FacilityMapNodeBlockEntity node) {
        onGeneratorBooting(node.getRoomName(), node.getBlockPos());
    }

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

        // In SCP:SL, there are 2 generators in Heavy Containment.
        // When 2 generators are overcharged, SCP-079 is recontained (terminated).
        if (OVERCHARGED_GENERATORS.size() >= 2) {
            if (level != null && level.getServer() != null) {
                for (UUID uuid : new ArrayList<>(Scp079PlayerManager.getSessionsMap().keySet())) {
                    net.minecraft.server.level.ServerPlayer p = level.getServer().getPlayerList().getPlayer(uuid);
                    if (p != null) {
                        Scp079PlayerManager.terminate079(p);
                    }
                }
            }
        }
    }
    }

    public static void applyClientMapSync(List<dev.zerosevennine.network.S2CSyncFacilityMapPacket.NodeData> nodes,
                                          List<dev.zerosevennine.network.S2CSyncFacilityMapPacket.CameraData> cameras) {
        ROOMS.clear();
        for (dev.zerosevennine.network.S2CSyncFacilityMapPacket.NodeData n : nodes) {
            RoomNode node = new RoomNode(n.pos, n.name, n.zone, n.roomType, n.targetZone, n.gridX, n.gridY);
            node.hasGenerator = n.hasGenerator;
            node.isGeneratorBooting = n.isGeneratorBooting;
            ROOMS.put(n.pos, node);
        }

        // For any camera that doesn't have an explicit map node, auto-create a RoomNode so it appears on the schematic!
        for (dev.zerosevennine.network.S2CSyncFacilityMapPacket.CameraData cam : cameras) {
            boolean hasNode = false;
            for (RoomNode r : ROOMS.values()) {
                if (r.pos.distSqr(cam.pos) <= 16 * 16 && r.zone == cam.zone) {
                    hasNode = true;
                    break;
                }
            }
            if (!hasNode) {
                int gx = ((cam.pos.getX() / 16) % 5);
                int gy = ((cam.pos.getZ() / 16) % 5);
                RoomNode autoNode = new RoomNode(cam.pos, cam.name, cam.zone,
                        FacilityMapNodeBlockEntity.RoomType.STANDARD, cam.zone, gx, gy);
                ROOMS.put(cam.pos, autoNode);
            }
        }
    }
}
