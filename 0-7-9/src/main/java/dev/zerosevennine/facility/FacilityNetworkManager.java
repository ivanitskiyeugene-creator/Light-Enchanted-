package dev.zerosevennine.facility;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.config.Scp079Config;
import dev.zerosevennine.network.ModNetwork;
import dev.zerosevennine.system.Scp079PlayerManager;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.client.gui.GuiGraphics;
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
        public final List<int[]> customPolygon = new ArrayList<>();

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

        public List<int[]> getEffectivePolygon() {
            if (!customPolygon.isEmpty()) {
                return customPolygon;
            }
            return generateDefaultVertices(roomShape, rotation);
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

    public static void moveRoom(FacilityZone zone, int fromGx, int fromGy, int toGx, int toGy) {
        RoomNode room = ROOMS.remove(getRoomKey(zone, fromGx, fromGy));
        if (room != null) {
            room.gridX = toGx;
            room.gridY = toGy;
            room.pos = new BlockPos(toGx * 16, 64, toGy * 16);
            ROOMS.put(room.getKey(), room);
        }
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

    public static void registerMapNode(BlockPos pos, FacilityMapNodeBlockEntity be) {
        if (pos == null || be == null) return;
        RoomNode node = getRoomAt(be.getZone(), be.getGridX(), be.getGridY());
        if (node == null) {
            node = new RoomNode(pos, be.getRoomName(), be.getZone(), be.getRoomType(), be.getRoomShape(), be.getRotation(), be.getTargetZone(), be.getGridX(), be.getGridY());
            node.hasGenerator = be.hasGenerator();
            node.isGeneratorBooting = be.isGeneratorBooting();
            registerRoomNode(node);
        } else {
            node.pos = pos;
            node.roomName = be.getRoomName();
            node.roomType = be.getRoomType();
            node.roomShape = be.getRoomShape();
            node.rotation = be.getRotation();
            node.targetZone = be.getTargetZone();
            node.hasGenerator = be.hasGenerator();
            node.isGeneratorBooting = be.isGeneratorBooting();
        }
    }

    public static void unregisterMapNode(BlockPos pos) {
        if (pos == null) return;
        ROOMS.values().removeIf(r -> r.pos != null && r.pos.equals(pos));
    }

    public static void broadcastGeneratorWarning(FacilityMapNodeBlockEntity be) {
        if (be == null) return;
        onGeneratorBooting(be.getRoomName(), be.getBlockPos());
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

        RoomNode currentRoom = findRoomForCamera(currentPos);
        if (currentRoom != null) {
            int dx = 0;
            int dy = 0;
            switch (direction) {
                case NORTH -> dy = -1;
                case SOUTH -> dy = 1;
                case WEST -> dx = -1;
                case EAST -> dx = 1;
            }

            RoomNode neighborRoom = getRoomAt(currentRoom.zone, currentRoom.gridX + dx, currentRoom.gridY + dy);
            if (neighborRoom != null && !neighborRoom.linkedCameras.isEmpty()) {
                return neighborRoom.linkedCameras.get(0);
            }
        }

        return null;
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
            for (int[] pt : n.customPolygon) {
                node.customPolygon.add(new int[]{pt[0], pt[1]});
            }
            ROOMS.put(node.getKey(), node);
        }
    }

    // 1:1 Square Bounding Box Templates (Bounds: [-18..18, -18..18])
    public static List<int[]> generateDefaultVertices(RoomShape shape, int rot) {
        List<int[]> pts = new ArrayList<>();
        if (shape == null) shape = RoomShape.SQUARE;
        rot = Math.floorMod(rot, 4);

        switch (shape) {
            case STRAIGHT -> {
                if (rot == 0 || rot == 2) { // Vertical Corridor
                    pts.add(new int[]{-6, -18});
                    pts.add(new int[]{6, -18});
                    pts.add(new int[]{6, 18});
                    pts.add(new int[]{-6, 18});
                } else { // Horizontal Corridor
                    pts.add(new int[]{-18, -6});
                    pts.add(new int[]{18, -6});
                    pts.add(new int[]{18, 6});
                    pts.add(new int[]{-18, 6});
                }
            }
            case CORNER -> {
                if (rot == 0) { // Top-Right Corner (1:1 square bounds)
                    pts.add(new int[]{-18, -18});
                    pts.add(new int[]{18, -18});
                    pts.add(new int[]{18, 18});
                    pts.add(new int[]{6, 18});
                    pts.add(new int[]{6, -6});
                    pts.add(new int[]{-18, -6});
                } else if (rot == 1) { // Bottom-Right Corner (1:1 square bounds)
                    pts.add(new int[]{6, -18});
                    pts.add(new int[]{18, -18});
                    pts.add(new int[]{18, 18});
                    pts.add(new int[]{-18, 18});
                    pts.add(new int[]{-18, 6});
                    pts.add(new int[]{6, 6});
                } else if (rot == 2) { // Bottom-Left Corner (1:1 square bounds)
                    pts.add(new int[]{-18, -18});
                    pts.add(new int[]{-6, -18});
                    pts.add(new int[]{-6, 6});
                    pts.add(new int[]{18, 6});
                    pts.add(new int[]{18, 18});
                    pts.add(new int[]{-18, 18});
                } else { // Top-Left Corner (1:1 square bounds)
                    pts.add(new int[]{-18, -18});
                    pts.add(new int[]{18, -18});
                    pts.add(new int[]{18, -6});
                    pts.add(new int[]{-6, -6});
                    pts.add(new int[]{-6, 18});
                    pts.add(new int[]{-18, 18});
                }
            }
            case T_SHAPE -> {
                if (rot == 0) { // Stem South (1:1 square bounds)
                    pts.add(new int[]{-18, -18});
                    pts.add(new int[]{18, -18});
                    pts.add(new int[]{18, -6});
                    pts.add(new int[]{6, -6});
                    pts.add(new int[]{6, 18});
                    pts.add(new int[]{-6, 18});
                    pts.add(new int[]{-6, -6});
                    pts.add(new int[]{-18, -6});
                } else if (rot == 1) { // Stem West (1:1 square bounds)
                    pts.add(new int[]{6, -18});
                    pts.add(new int[]{18, -18});
                    pts.add(new int[]{18, 18});
                    pts.add(new int[]{6, 18});
                    pts.add(new int[]{6, 6});
                    pts.add(new int[]{-18, 6});
                    pts.add(new int[]{-18, -6});
                    pts.add(new int[]{6, -6});
                } else if (rot == 2) { // Stem North (1:1 square bounds)
                    pts.add(new int[]{-6, -18});
                    pts.add(new int[]{6, -18});
                    pts.add(new int[]{6, 6});
                    pts.add(new int[]{18, 6});
                    pts.add(new int[]{18, 18});
                    pts.add(new int[]{-18, 18});
                    pts.add(new int[]{-18, 6});
                    pts.add(new int[]{-6, 6});
                } else { // Stem East (1:1 square bounds)
                    pts.add(new int[]{-18, -18});
                    pts.add(new int[]{-6, -18});
                    pts.add(new int[]{-6, -6});
                    pts.add(new int[]{18, -6});
                    pts.add(new int[]{18, 6});
                    pts.add(new int[]{-6, 6});
                    pts.add(new int[]{-6, 18});
                    pts.add(new int[]{-18, 18});
                }
            }
            case CROSS -> { // 4-Way Cross (1:1 square bounds)
                pts.add(new int[]{-6, -18});
                pts.add(new int[]{6, -18});
                pts.add(new int[]{6, -6});
                pts.add(new int[]{18, -6});
                pts.add(new int[]{18, 6});
                pts.add(new int[]{6, 6});
                pts.add(new int[]{6, 18});
                pts.add(new int[]{-6, 18});
                pts.add(new int[]{-6, 6});
                pts.add(new int[]{-18, 6});
                pts.add(new int[]{-18, -6});
                pts.add(new int[]{-6, -6});
            }
            default -> { // SQUARE / BOX / END / AIRLOCK / ELEVATOR (1:1 square bounds)
                pts.add(new int[]{-18, -18});
                pts.add(new int[]{18, -18});
                pts.add(new int[]{18, 18});
                pts.add(new int[]{-18, 18});
            }
        }
        return pts;
    }

    public static void fillPolygonWithScanlines(GuiGraphics graphics, int originX, int originY, List<int[]> pts, int bgCol, int scanlineCol, int frameCol) {
        if (pts == null || pts.size() < 3) return;
        int n = pts.size();

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int[] pt : pts) {
            int py = originY + pt[1];
            if (py < minY) minY = py;
            if (py > maxY) maxY = py;
        }

        List<Double> xIntersections = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            xIntersections.clear();
            for (int i = 0; i < n; i++) {
                int[] p1 = pts.get(i);
                int[] p2 = pts.get((i + 1) % n);
                int y1 = originY + p1[1];
                int y2 = originY + p2[1];
                int x1 = originX + p1[0];
                int x2 = originX + p2[0];

                if ((y1 <= y && y2 > y) || (y2 <= y && y1 > y)) {
                    double x = x1 + (double)(y - y1) / (y2 - y1) * (x2 - x1);
                    xIntersections.add(x);
                }
            }
            Collections.sort(xIntersections);
            for (int i = 0; i < xIntersections.size() - 1; i += 2) {
                int xStart = (int) Math.round(xIntersections.get(i));
                int xEnd = (int) Math.round(xIntersections.get(i + 1));
                if (xEnd > xStart) {
                    int col = (y % 2 == 0) ? scanlineCol : bgCol;
                    graphics.fill(xStart, y, xEnd, y + 1, col);
                }
            }
        }

        for (int i = 0; i < n; i++) {
            int[] p1 = pts.get(i);
            int[] p2 = pts.get((i + 1) % n);
            drawBresenhamLine(graphics, originX + p1[0], originY + p1[1], originX + p2[0], originY + p2[1], frameCol);
        }
    }

    public static void drawBresenhamLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
}
