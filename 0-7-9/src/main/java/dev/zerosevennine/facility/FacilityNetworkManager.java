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
        public int gridX;
        public int gridY;
        public boolean hasGenerator;
        public boolean isGeneratorBooting;
        public final List<BlockPos> cameraPositions = new ArrayList<>();

        public RoomNode(BlockPos pos, String roomName, FacilityZone zone, int gridX, int gridY) {
            this.pos = pos;
            this.roomName = roomName;
            this.zone = zone;
            this.gridX = gridX;
            this.gridY = gridY;
            this.hasGenerator = false;
            this.isGeneratorBooting = false;
        }
    }

    private static final Map<BlockPos, RoomNode> ROOMS = new HashMap<>();
    private static final Map<BlockPos, CameraBlockEntity> CAMERAS = new HashMap<>();

    public static void registerMapNode(BlockPos pos, FacilityMapNodeBlockEntity be) {
        RoomNode node = new RoomNode(pos.immutable(), be.getRoomName(), be.getZone(), be.getGridX(), be.getGridY());
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

    public static void broadcastGeneratorWarning(FacilityMapNodeBlockEntity node) {
        String msg = "GENERATOR BOOTING UP: " + node.getRoomName() + " (" + node.getZone().getCode() + ")";
        Scp079PlayerManager.broadcastSystemAlert(msg, 0xF39C12);
    }

    public static void onGeneratorOvercharge(Level level) {
        String msg = "CRITICAL: GENERATOR OVERCHARGE COMPLETED! FACILITY LOCKDOWN ENGAGED";
        Scp079PlayerManager.broadcastSystemAlert(msg, 0xE74C3C);
    }
}
