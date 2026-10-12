package dev.zerosevennine.facility;

import dev.zerosevennine.network.C2SEditMapRoomPacket;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.core.BlockPos;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class FacilityMapAutoBuilder {

    public static void buildCanonicalLayout(FacilityZone zone) {
        switch (zone) {
            case HCZ -> buildCanonicalHcz();
            case LCZ -> buildCanonicalLcz();
            case EZ -> buildCanonicalEz();
            case SURFACE -> buildCanonicalSurface();
        }
    }

    public static void buildCanonicalHcz() {
        FacilityZone z = FacilityZone.HCZ;
        List<FacilityNetworkManager.RoomNode> list = new ArrayList<>();

        // 1. Center Containment Zones
        list.add(createRoom("106 CONT", z, RoomShape.SQUARE, 0, 0, 0, false, null));
        list.add(createRoom("049/173 CONT", z, RoomShape.ELEVATOR, 0, 0, -2, false, null));
        list.add(createRoom("ARMORY", z, RoomShape.T_SHAPE, 0, 1, -3, true, null));
        list.add(createRoom("096 CONT", z, RoomShape.END_ROOM, 0, 1, -5, false, null));

        // 2. Upper Corridors & Tesla
        list.add(createRoom("TESLA", z, RoomShape.STRAIGHT, 1, -1, -3, false, null));
        list.add(createRoom("HC-01", z, RoomShape.CORNER, 3, -3, -3, false, null));
        list.add(createRoom("TESTROOM", z, RoomShape.END_ROOM, 1, -3, -1, false, null));
        list.add(createRoom("HC-02", z, RoomShape.CORNER, 2, -3, 0, false, null));

        // 3. West Gate & Checkpoint
        list.add(createRoom("CHECKPOINT", z, RoomShape.CHECKPOINT, 1, -4, 1, false, FacilityZone.EZ));
        list.add(createRoom("HC-03", z, RoomShape.T_SHAPE, 1, -2, 1, false, null));
        list.add(createRoom("HC-04", z, RoomShape.CORNER, 3, -2, 0, false, null));
        list.add(createRoom("HC-05", z, RoomShape.CORNER, 2, -2, 2, false, null));

        // 4. East Corridors & 079 Containment
        list.add(createRoom("HC-06", z, RoomShape.CORNER, 0, 2, -3, false, null));
        list.add(createRoom("TESLA", z, RoomShape.STRAIGHT, 0, 2, 0, false, null));
        list.add(createRoom("079 CONT", z, RoomShape.END_ROOM, 1, 3, 1, true, null));
        list.add(createRoom("HC-07", z, RoomShape.CORNER, 1, 2, 3, false, null));

        // 5. South Corridors, 939, Warhead, H.I.D
        list.add(createRoom("TESLA", z, RoomShape.STRAIGHT, 1, 0, 2, false, null));
        list.add(createRoom("939 CONT", z, RoomShape.END_ROOM, 2, -2, 2, false, null));
        list.add(createRoom("H.I.D", z, RoomShape.END_ROOM, 0, -2, 4, false, null));
        list.add(createRoom("WARHEAD", z, RoomShape.ELEVATOR, 0, -1, 4, true, null));
        list.add(createRoom("HC-08", z, RoomShape.CORNER, 1, 1, 4, false, null));

        sendBatchToServer(list);
    }

    public static void buildCanonicalLcz() {
        FacilityZone z = FacilityZone.LCZ;
        List<FacilityNetworkManager.RoomNode> list = new ArrayList<>();

        // 1. West Area: 914, Airlocks, Armory
        list.add(createRoom("914 CONT", z, RoomShape.END_ROOM, 3, -3, 0, true, null));
        list.add(createRoom("IT-04", z, RoomShape.T_SHAPE, 1, -2, 0, false, null));
        list.add(createRoom("AIRLOCK", z, RoomShape.AIRLOCK, 0, -2, -1, false, null));
        list.add(createRoom("AIRLOCK", z, RoomShape.AIRLOCK, 0, -2, 1, false, null));
        list.add(createRoom("ARMORY", z, RoomShape.END_ROOM, 0, -2, 2, false, null));

        // 2. North Area: Corridors, Test Room, 173
        list.add(createRoom("HC-02", z, RoomShape.STRAIGHT, 1, -1, -1, false, null));
        list.add(createRoom("IT-02", z, RoomShape.T_SHAPE, 2, 0, -1, false, null));
        list.add(createRoom("TEST ROOM", z, RoomShape.END_ROOM, 2, 0, -2, false, null));
        list.add(createRoom("AIRLOCK", z, RoomShape.AIRLOCK, 1, 1, -1, false, null));
        list.add(createRoom("IT-03", z, RoomShape.T_SHAPE, 1, 2, -1, false, null));
        list.add(createRoom("173 CONT", z, RoomShape.END_ROOM, 2, 2, -2, true, null));

        // 3. Center & East: Class-D, Elev B
        list.add(createRoom("CLASS-D", z, RoomShape.STRAIGHT, 1, 1, 0, false, null));
        list.add(createRoom("IX-03", z, RoomShape.CROSS, 0, 2, 0, false, null));
        list.add(createRoom("ELEV B", z, RoomShape.ELEVATOR, 1, 3, 0, false, FacilityZone.HCZ));

        // 4. South Area: WC, Greenhouse, Elev A
        list.add(createRoom("WC", z, RoomShape.END_ROOM, 0, -1, 2, false, null));
        list.add(createRoom("IT-01", z, RoomShape.STRAIGHT, 1, 0, 2, false, null));
        list.add(createRoom("HC-03", z, RoomShape.CORNER, 1, 1, 2, false, null));
        list.add(createRoom("GREENHOUSE", z, RoomShape.END_ROOM, 0, 2, 2, false, null));
        list.add(createRoom("IT-07", z, RoomShape.CORNER, 2, 1, 3, false, null));
        list.add(createRoom("ELEV A", z, RoomShape.ELEVATOR, 1, 2, 3, false, FacilityZone.HCZ));

        sendBatchToServer(list);
    }

    public static void buildCanonicalEz() {
        FacilityZone z = FacilityZone.EZ;
        List<FacilityNetworkManager.RoomNode> list = new ArrayList<>();

        list.add(createRoom("INTERCOM", z, RoomShape.END_ROOM, 2, 0, -2, true, null));
        list.add(createRoom("EZ-01", z, RoomShape.T_SHAPE, 0, 0, -1, false, null));
        list.add(createRoom("GATE A", z, RoomShape.ELEVATOR, 3, -2, -1, false, FacilityZone.SURFACE));
        list.add(createRoom("GATE B", z, RoomShape.ELEVATOR, 1, 2, -1, false, FacilityZone.SURFACE));
        list.add(createRoom("EZ-02", z, RoomShape.STRAIGHT, 0, 0, 0, false, null));
        list.add(createRoom("EVAC SHELTER", z, RoomShape.END_ROOM, 3, -1, 0, false, null));
        list.add(createRoom("RED ROOM", z, RoomShape.END_ROOM, 1, 1, 0, false, null));
        list.add(createRoom("CHECKPOINT", z, RoomShape.CHECKPOINT, 0, 0, 1, false, FacilityZone.HCZ));

        sendBatchToServer(list);
    }

    public static void buildCanonicalSurface() {
        FacilityZone z = FacilityZone.SURFACE;
        List<FacilityNetworkManager.RoomNode> list = new ArrayList<>();

        list.add(createRoom("SURFACE GATE A", z, RoomShape.ELEVATOR, 1, -1, 0, false, FacilityZone.EZ));
        list.add(createRoom("SURFACE GATE B", z, RoomShape.ELEVATOR, 1, 1, 0, false, FacilityZone.EZ));
        list.add(createRoom("SURFACE ROAD", z, RoomShape.STRAIGHT, 1, 0, 0, false, null));
        list.add(createRoom("ESCAPE AREA", z, RoomShape.END_ROOM, 2, 0, -1, false, null));

        sendBatchToServer(list);
    }

    public static boolean parseFromScreenshot(BufferedImage img, FacilityZone zone) {
        if (img == null) return false;
        int w = img.getWidth();
        int h = img.getHeight();
        if (w < 100 || h < 100) return false;

        // Auto-Detect rooms based on bright blue/cyan room outlines and corridors
        int cols = 14;
        int rows = 12;
        int cellW = w / cols;
        int cellH = h / rows;

        boolean[][] occupancy = new boolean[cols][rows];

        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int cx = c * cellW + cellW / 2;
                int cy = r * cellH + cellH / 2;

                // Sample 5 points within cell
                int blueSamples = 0;
                int[] sampleX = {cx, cx - cellW / 4, cx + cellW / 4, cx, cx};
                int[] sampleY = {cy, cy, cy, cy - cellH / 4, cy + cellH / 4};

                for (int i = 0; i < 5; i++) {
                    int rgb = img.getRGB(sampleX[i], sampleY[i]);
                    int red = (rgb >> 16) & 0xFF;
                    int green = (rgb >> 8) & 0xFF;
                    int blue = rgb & 0xFF;

                    // Detect Cyan/Blue Map elements (Blue > 60 and Blue > Red)
                    if (blue > 50 && blue > red + 15) {
                        blueSamples++;
                    }
                }

                if (blueSamples >= 2) {
                    occupancy[c][r] = true;
                }
            }
        }

        List<FacilityNetworkManager.RoomNode> list = new ArrayList<>();
        int midC = cols / 2;
        int midR = rows / 2;

        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                if (!occupancy[c][r]) continue;

                boolean north = (r > 0 && occupancy[c][r - 1]);
                boolean south = (r < rows - 1 && occupancy[c][r + 1]);
                boolean west = (c > 0 && occupancy[c - 1][r]);
                boolean east = (c < cols - 1 && occupancy[c + 1][r]);

                int connections = (north ? 1 : 0) + (south ? 1 : 0) + (west ? 1 : 0) + (east ? 1 : 0);

                RoomShape shape = RoomShape.SQUARE;
                int rot = 0;

                if (connections == 4) {
                    shape = RoomShape.CROSS;
                } else if (connections == 3) {
                    shape = RoomShape.T_SHAPE;
                    if (!north) rot = 0;
                    else if (!east) rot = 1;
                    else if (!south) rot = 2;
                    else rot = 3;
                } else if (connections == 2) {
                    if (north && south) {
                        shape = RoomShape.STRAIGHT;
                        rot = 0;
                    } else if (west && east) {
                        shape = RoomShape.STRAIGHT;
                        rot = 1;
                    } else if (north && east) {
                        shape = RoomShape.CORNER;
                        rot = 0;
                    } else if (south && east) {
                        shape = RoomShape.CORNER;
                        rot = 1;
                    } else if (south && west) {
                        shape = RoomShape.CORNER;
                        rot = 2;
                    } else {
                        shape = RoomShape.CORNER;
                        rot = 3;
                    }
                } else if (connections == 1) {
                    shape = RoomShape.END_ROOM;
                    if (south) rot = 0;
                    else if (west) rot = 1;
                    else if (north) rot = 2;
                    else rot = 3;
                }

                int gx = c - midC;
                int gy = r - midR;
                String roomName = "ROOM " + (c + 1) + "-" + (r + 1);

                list.add(createRoom(roomName, zone, shape, rot, gx, gy, false, null));
            }
        }

        if (!list.isEmpty()) {
            sendBatchToServer(list);
            return true;
        }
        return false;
    }

    private static FacilityNetworkManager.RoomNode createRoom(String name, FacilityZone zone, RoomShape shape, int rot, int gx, int gy, boolean gen, FacilityZone targetZone) {
        BlockPos pos = new BlockPos(gx * 16, 64, gy * 16);
        FacilityNetworkManager.RoomNode node = new FacilityNetworkManager.RoomNode(pos, name, zone, null, shape, rot, targetZone != null ? targetZone : zone, gx, gy);
        node.hasGenerator = gen;
        return node;
    }

    private static void sendBatchToServer(List<FacilityNetworkManager.RoomNode> list) {
        for (FacilityNetworkManager.RoomNode node : list) {
            ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                    C2SEditMapRoomPacket.Action.PLACE_OR_UPDATE,
                    node.zone, node.gridX, node.gridY, node.roomName,
                    node.roomShape, node.rotation, node.hasGenerator,
                    node.targetZone, null, node.gridX, node.gridY, node.customPolygon, node.linkedCameras
            ));
        }
    }
}
