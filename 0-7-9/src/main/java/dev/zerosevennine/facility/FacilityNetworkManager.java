package dev.zerosevennine.facility;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.config.Scp079Config;
import dev.zerosevennine.network.ModNetwork;
import dev.zerosevennine.system.Scp079PlayerManager;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

public class FacilityNetworkManager {
    public static final int CELL_SIZE = 42;

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

    public static Map<String, RoomNode> getRooms() {
        return ROOMS;
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
        if (pos != null && be != null) {
            CAMERAS.put(pos.immutable(), be);
        }
    }

    public static void unregisterCamera(BlockPos pos) {
        if (pos != null) {
            CAMERAS.remove(pos);
        }
    }

    public static CameraBlockEntity getCamera(BlockPos pos) {
        return pos != null ? CAMERAS.get(pos) : null;
    }

    public static Map<BlockPos, CameraBlockEntity> getCameras() {
        return CAMERAS;
    }

    public static Map<Direction, BlockPos> getDynamicWasdMap(BlockPos currentCamPos) {
        Map<Direction, BlockPos> map = new EnumMap<>(Direction.class);
        if (currentCamPos == null) return map;

        map.put(Direction.NORTH, findNextCamera(currentCamPos, Direction.NORTH));
        map.put(Direction.SOUTH, findNextCamera(currentCamPos, Direction.SOUTH));
        map.put(Direction.WEST, findNextCamera(currentCamPos, Direction.WEST));
        map.put(Direction.EAST, findNextCamera(currentCamPos, Direction.EAST));

        return map;
    }

    public static BlockPos findNextCamera(BlockPos currentPos, Direction direction) {
        if (currentPos == null || direction == null) return null;

        RoomNode currentRoom = findRoomForCamera(currentPos);
        if (currentRoom != null) {
            int dx = 0;
            int dy = 0;
            switch (direction) {
                case NORTH -> dy = -1;
                case SOUTH -> dy = 1;
                case WEST -> dx = -1;
                case EAST -> dx = 1;
                default -> {}
            }

            // 1. Scan neighbor cells in grid direction up to 8 steps away
            for (int step = 1; step <= 8; step++) {
                RoomNode neighborRoom = getRoomAt(currentRoom.zone, currentRoom.gridX + dx * step, currentRoom.gridY + dy * step);
                if (neighborRoom != null && !neighborRoom.linkedCameras.isEmpty()) {
                    return neighborRoom.linkedCameras.get(0);
                }
            }

            // 2. If current room has multiple cameras, cycle to next camera in room
            if (currentRoom.linkedCameras.size() > 1) {
                int idx = currentRoom.linkedCameras.indexOf(currentPos);
                if (idx >= 0) {
                    if (direction == Direction.NORTH || direction == Direction.EAST) {
                        int nextIdx = (idx + 1) % currentRoom.linkedCameras.size();
                        if (nextIdx != idx) return currentRoom.linkedCameras.get(nextIdx);
                    } else if (direction == Direction.SOUTH || direction == Direction.WEST) {
                        int nextIdx = (idx - 1 + currentRoom.linkedCameras.size()) % currentRoom.linkedCameras.size();
                        if (nextIdx != idx) return currentRoom.linkedCameras.get(nextIdx);
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos findNearestCameraInDirection(FacilityZone zone, int startGx, int startGy, int dx, int dy) {
        for (int step = 1; step <= 8; step++) {
            int targetGx = startGx + dx * step;
            int targetGy = startGy + dy * step;

            RoomNode room = getRoomAt(zone, targetGx, targetGy);
            if (room != null && !room.linkedCameras.isEmpty()) {
                return room.linkedCameras.get(0);
            }
        }
        return null;
    }

    public static BlockPos findTransitCamera(RoomNode sourceRoom, FacilityZone targetZone) {
        if (sourceRoom == null || targetZone == null) return null;

        List<RoomNode> targetRooms = getRoomsInZone(targetZone);
        for (RoomNode r : targetRooms) {
            if ((r.roomShape == RoomShape.CHECKPOINT || r.roomShape == RoomShape.ELEVATOR) && !r.linkedCameras.isEmpty()) {
                return r.linkedCameras.get(0);
            }
        }

        for (RoomNode r : targetRooms) {
            if (!r.linkedCameras.isEmpty()) {
                return r.linkedCameras.get(0);
            }
        }

        for (Map.Entry<BlockPos, CameraBlockEntity> entry : CAMERAS.entrySet()) {
            if (entry.getValue().getZone() == targetZone) {
                return entry.getKey();
            }
        }

        return null;
    }

    public static void autoLinkWasdGraph(FacilityZone zone) {
        List<RoomNode> zoneRooms = getRoomsInZone(zone);
        for (RoomNode room : zoneRooms) {
            if (room.linkedCameras.isEmpty()) continue;

            Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
            int[][] deltas = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

            for (int i = 0; i < 4; i++) {
                BlockPos neighborCam = findNearestCameraInDirection(zone, room.gridX, room.gridY, deltas[i][0], deltas[i][1]);
                if (neighborCam != null) {
                    for (BlockPos camPos : room.linkedCameras) {
                        CameraBlockEntity cbe = getCamera(camPos);
                        if (cbe != null) {
                            cbe.setWasdNeighbor(dirs[i], neighborCam);
                        }
                    }
                }
            }
        }
    }

    public static void autoLinkWasd(ServerLevel level) {
        if (level == null) return;
        for (FacilityZone zone : FacilityZone.values()) {
            autoLinkWasdGraph(zone);
        }
    }

    public static void markDirtyAndSync(ServerLevel level) {
        if (level == null) return;
        FacilityMapSavedData.get(level).setDirty();
        autoLinkWasd(level);
        if (level.getServer() != null) {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                ModNetwork.sendFacilityMapToClient(player);
            }
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

        checkGeneratorOvercharge(level);
    }

    public static final Set<BlockPos> OVERCHARGED_GENERATORS = new HashSet<>();

    public static void checkGeneratorOvercharge(Level level) {
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

    // 1:1 Unified Vertex Geometry Bounds: [-21..21, -21..21] for CELL_SIZE = 42
    public static List<int[]> generateDefaultVertices(RoomShape shape, int rot) {
        List<int[]> pts = new ArrayList<>();
        if (shape == null) shape = RoomShape.SQUARE;
        rot = Math.floorMod(rot, 4);

        int arm = 7;
        int edge = 21;

        switch (shape) {
            case STRAIGHT -> {
                if (rot == 0 || rot == 2) { // Vertical Corridor
                    pts.add(new int[]{-arm, -edge});
                    pts.add(new int[]{arm, -edge});
                    pts.add(new int[]{arm, edge});
                    pts.add(new int[]{-arm, edge});
                } else { // Horizontal Corridor
                    pts.add(new int[]{-edge, -arm});
                    pts.add(new int[]{edge, -arm});
                    pts.add(new int[]{edge, arm});
                    pts.add(new int[]{-edge, arm});
                }
            }
            case CORNER -> {
                if (rot == 0) { // Top-Right Corner
                    pts.add(new int[]{-edge, -edge});
                    pts.add(new int[]{edge, -edge});
                    pts.add(new int[]{edge, edge});
                    pts.add(new int[]{arm, edge});
                    pts.add(new int[]{arm, -arm});
                    pts.add(new int[]{-edge, -arm});
                } else if (rot == 1) { // Bottom-Right Corner
                    pts.add(new int[]{arm, -edge});
                    pts.add(new int[]{edge, -edge});
                    pts.add(new int[]{edge, edge});
                    pts.add(new int[]{-edge, edge});
                    pts.add(new int[]{-edge, arm});
                    pts.add(new int[]{arm, arm});
                } else if (rot == 2) { // Bottom-Left Corner
                    pts.add(new int[]{-edge, -edge});
                    pts.add(new int[]{-arm, -edge});
                    pts.add(new int[]{-arm, arm});
                    pts.add(new int[]{edge, arm});
                    pts.add(new int[]{edge, edge});
                    pts.add(new int[]{-edge, edge});
                } else { // Top-Left Corner
                    pts.add(new int[]{-edge, -edge});
                    pts.add(new int[]{edge, -edge});
                    pts.add(new int[]{edge, -arm});
                    pts.add(new int[]{-arm, -arm});
                    pts.add(new int[]{-arm, edge});
                    pts.add(new int[]{-edge, edge});
                }
            }
            case T_SHAPE -> {
                if (rot == 0) { // Stem South
                    pts.add(new int[]{-edge, -edge});
                    pts.add(new int[]{edge, -edge});
                    pts.add(new int[]{edge, -arm});
                    pts.add(new int[]{arm, -arm});
                    pts.add(new int[]{arm, edge});
                    pts.add(new int[]{-arm, edge});
                    pts.add(new int[]{-arm, -arm});
                    pts.add(new int[]{-edge, -arm});
                } else if (rot == 1) { // Stem West
                    pts.add(new int[]{arm, -edge});
                    pts.add(new int[]{edge, -edge});
                    pts.add(new int[]{edge, edge});
                    pts.add(new int[]{arm, edge});
                    pts.add(new int[]{arm, arm});
                    pts.add(new int[]{-edge, arm});
                    pts.add(new int[]{-edge, -arm});
                    pts.add(new int[]{arm, -arm});
                } else if (rot == 2) { // Stem North
                    pts.add(new int[]{-arm, -edge});
                    pts.add(new int[]{arm, -edge});
                    pts.add(new int[]{arm, arm});
                    pts.add(new int[]{edge, arm});
                    pts.add(new int[]{edge, edge});
                    pts.add(new int[]{-edge, edge});
                    pts.add(new int[]{-edge, arm});
                    pts.add(new int[]{-arm, arm});
                } else { // Stem East
                    pts.add(new int[]{-edge, -edge});
                    pts.add(new int[]{-arm, -edge});
                    pts.add(new int[]{-arm, -arm});
                    pts.add(new int[]{edge, -arm});
                    pts.add(new int[]{edge, arm});
                    pts.add(new int[]{-arm, arm});
                    pts.add(new int[]{-arm, edge});
                    pts.add(new int[]{-edge, edge});
                }
            }
            case CROSS -> {
                pts.add(new int[]{-arm, -edge});
                pts.add(new int[]{arm, -edge});
                pts.add(new int[]{arm, -arm});
                pts.add(new int[]{edge, -arm});
                pts.add(new int[]{edge, arm});
                pts.add(new int[]{arm, arm});
                pts.add(new int[]{arm, edge});
                pts.add(new int[]{-arm, edge});
                pts.add(new int[]{-arm, arm});
                pts.add(new int[]{-edge, arm});
                pts.add(new int[]{-edge, -arm});
                pts.add(new int[]{-arm, -arm});
            }
            default -> { // SQUARE / BOX / END / AIRLOCK / ELEVATOR
                pts.add(new int[]{-18, -18});
                pts.add(new int[]{18, -18});
                pts.add(new int[]{18, 18});
                pts.add(new int[]{-18, 18});
            }
        }
        return pts;
    }

    // High-performance unified room renderer (Zero GC allocation per frame)
    public static void renderRoomNode(GuiGraphics graphics, Font font,
                                       int midX, int midY, RoomNode room,
                                       boolean isCurrent, boolean isFocused, boolean isCurrentZone) {
        int bgCol;
        int frameCol;

        if (isCurrentZone) {
            bgCol = isCurrent ? 0xFF00C882 : (isFocused ? 0xFF1D78B2 : 0xFF10486C);
            frameCol = isCurrent ? 0xFF00FFB0 : (isFocused ? 0xFFFFFFFF : 0xFF186494);

            if (room.isGeneratorBooting) {
                long time = System.currentTimeMillis() / 250;
                bgCol = (time % 2 == 0) ? 0xFFFF3300 : 0xFFFFAA00;
                frameCol = 0xFFFFFFFF;
            }
        } else {
            bgCol = isFocused ? 0xFF1A3850 : 0xFF081420;
            frameCol = isFocused ? 0xFFFFCC00 : 0xFF0E3854;
        }

        List<int[]> poly = room.getEffectivePolygon();
        fillPolygonFast(graphics, midX, midY, poly, bgCol, frameCol);

        // Room Name Title
        String name = room.roomName;
        if (name != null && !name.trim().isEmpty()) {
            name = name.trim().toUpperCase();
            int textCol = isCurrent ? 0xFF021B14 : 0xFFE0F7FA;
            renderFormattedRoomTitle(graphics, font, name, midX, midY, textCol);
        }

        // Special Indicators
        if (room.hasGenerator) {
            graphics.fill(midX - 2, midY - 14, midX + 2, midY - 12, 0xFFFFCC00);
        }
        if (room.roomShape == RoomShape.ELEVATOR) {
            graphics.drawString(font, "▲", midX - 3, midY - 14, 0xFF00FF88, false);
        }
        if (room.roomShape == RoomShape.CHECKPOINT) {
            String transitLabel = "=> " + (room.targetZone != null ? room.targetZone.name() : "EZ");
            int tw = font.width(transitLabel);
            graphics.drawString(font, transitLabel, midX - tw / 2, midY + 11, 0xFFFFCC00, false);
        }
        if (isCurrent) {
            graphics.fill(midX - 3, midY + 8, midX + 3, midY + 10, 0xFFFF3333);
        }
    }

    public static void renderFormattedRoomTitle(GuiGraphics graphics, Font font,
                                                String text, int midX, int midY, int color) {
        if (text == null || text.isEmpty()) return;
        String[] parts = text.split("\\s+");
        if (parts.length == 1) {
            int tw = font.width(text);
            graphics.drawString(font, text, midX - tw / 2, midY - 4, color, false);
        } else if (parts.length == 2) {
            int tw1 = font.width(parts[0]);
            int tw2 = font.width(parts[1]);
            graphics.drawString(font, parts[0], midX - tw1 / 2, midY - 8, color, false);
            graphics.drawString(font, parts[1], midX - tw2 / 2, midY + 1, color, false);
        } else {
            String l1 = parts[0] + " " + parts[1];
            String l2 = parts[2];
            int tw1 = font.width(l1);
            int tw2 = font.width(l2);
            graphics.drawString(font, l1, midX - tw1 / 2, midY - 8, color, false);
            graphics.drawString(font, l2, midX - tw2 / 2, midY + 1, color, false);
        }
    }

    // High-speed scanline polygon rasterization (Zero ArrayList/Double allocation)
    public static void fillPolygonFast(GuiGraphics graphics, int originX, int originY, List<int[]> pts, int bgCol, int frameCol) {
        if (pts == null || pts.size() < 3) return;
        int n = pts.size();

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            int[] pt = pts.get(i);
            int py = originY + pt[1];
            if (py < minY) minY = py;
            if (py > maxY) maxY = py;
        }

        int[] xNodes = new int[n + 2];
        for (int y = minY; y <= maxY; y++) {
            int nodes = 0;
            int j = n - 1;
            for (int i = 0; i < n; i++) {
                int y1 = originY + pts.get(i)[1];
                int y2 = originY + pts.get(j)[1];
                int x1 = originX + pts.get(i)[0];
                int x2 = originX + pts.get(j)[0];

                if ((y1 < y && y2 >= y) || (y2 < y && y1 >= y)) {
                    xNodes[nodes++] = (int) Math.round(x1 + (double)(y - y1) / (y2 - y1) * (x2 - x1));
                }
                j = i;
            }

            for (int i = 0; i < nodes - 1; i++) {
                for (int k = i + 1; k < nodes; k++) {
                    if (xNodes[i] > xNodes[k]) {
                        int swap = xNodes[i];
                        xNodes[i] = xNodes[k];
                        xNodes[k] = swap;
                    }
                }
            }

            for (int i = 0; i < nodes - 1; i += 2) {
                if (xNodes[i + 1] > xNodes[i]) {
                    graphics.fill(xNodes[i], y, xNodes[i + 1], y + 1, bgCol);
                }
            }
        }

        for (int i = 0; i < n; i++) {
            int[] p1 = pts.get(i);
            int[] p2 = pts.get((i + 1) % n);
            drawLineSegment(graphics, originX + p1[0], originY + p1[1], originX + p2[0], originY + p2[1], frameCol);
        }
    }

    public static void drawLineSegment(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            graphics.fill(x1, minY, x1 + 1, maxY + 1, color);
        } else if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            graphics.fill(minX, y1, maxX + 1, y1 + 1, color);
        } else {
            drawBresenhamLine(graphics, x1, y1, x2, y2, color);
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
