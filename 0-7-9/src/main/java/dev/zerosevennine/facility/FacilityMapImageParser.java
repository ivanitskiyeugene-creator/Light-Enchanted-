package dev.zerosevennine.facility;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class FacilityMapImageParser {

    public static class ParseResult {
        public final boolean success;
        public final String message;
        public final List<FacilityNetworkManager.RoomNode> rooms;

        public ParseResult(boolean success, String message, List<FacilityNetworkManager.RoomNode> rooms) {
            this.success = success;
            this.message = message;
            this.rooms = rooms;
        }
    }

    public static ParseResult parseMapFromImage(BufferedImage img, FacilityZone zone) {
        if (img == null) {
            return new ParseResult(false, "Изображение не загружено!", new ArrayList<>());
        }

        int w = img.getWidth();
        int h = img.getHeight();

        // 1. Detect active schematic bounding box
        int minX = w, maxX = 0, minY = h, maxY = 0;
        int activePixelCount = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                if (isSchematicPixel(rgb)) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    activePixelCount++;
                }
            }
        }

        if (activePixelCount < 500 || minX >= maxX || minY >= maxY) {
            return new ParseResult(false, "Схема комнат не обнаружена на скриншоте (слишком темно или мало пикселей).", new ArrayList<>());
        }

        int boundW = maxX - minX;
        int boundH = maxY - minY;

        // 2. Define grid sampling resolution (typically 9 to 13 cells wide/high)
        int cols = Math.max(7, Math.min(15, Math.round((float) boundW / 54.0f)));
        int rows = Math.max(7, Math.min(15, Math.round((float) boundH / 42.0f)));

        float cellPixelW = (float) boundW / (float) cols;
        float cellPixelH = (float) boundH / (float) rows;

        boolean[][] cellActive = new boolean[cols][rows];
        int[][] cellColorAverage = new int[cols][rows];

        // 3. Sample each cell center
        for (int gx = 0; gx < cols; gx++) {
            for (int gy = 0; gy < rows; gy++) {
                int centerX = minX + (int) ((gx + 0.5f) * cellPixelW);
                int centerY = minY + (int) ((gy + 0.5f) * cellPixelH);

                int hits = 0;
                long totalR = 0, totalG = 0, totalB = 0;
                int sampleRadius = Math.max(2, (int) (cellPixelW * 0.18f));

                for (int sy = -sampleRadius; sy <= sampleRadius; sy++) {
                    for (int sx = -sampleRadius; sx <= sampleRadius; sx++) {
                        int px = Math.max(0, Math.min(w - 1, centerX + sx));
                        int py = Math.max(0, Math.min(h - 1, centerY + sy));
                        int rgb = img.getRGB(px, py);
                        if (isSchematicPixel(rgb)) {
                            hits++;
                            Color c = new Color(rgb);
                            totalR += c.getRed();
                            totalG += c.getGreen();
                            totalB += c.getBlue();
                        }
                    }
                }

                int totalSamples = (sampleRadius * 2 + 1) * (sampleRadius * 2 + 1);
                if ((float) hits / (float) totalSamples >= 0.28f) {
                    cellActive[gx][gy] = true;
                    int avgR = (int) (totalR / Math.max(1, hits));
                    int avgG = (int) (totalG / Math.max(1, hits));
                    int avgB = (int) (totalB / Math.max(1, hits));
                    cellColorAverage[gx][gy] = (0xFF << 24) | (avgR << 16) | (avgG << 8) | avgB;
                }
            }
        }

        // 4. Analyze connections between neighboring active cells
        List<FacilityNetworkManager.RoomNode> parsedRooms = new ArrayList<>();
        int centerGx = cols / 2;
        int centerGy = rows / 2;

        for (int gx = 0; gx < cols; gx++) {
            for (int gy = 0; gy < rows; gy++) {
                if (!cellActive[gx][gy]) continue;

                int cx = minX + (int) ((gx + 0.5f) * cellPixelW);
                int cy = minY + (int) ((gy + 0.5f) * cellPixelH);

                // Sample North, South, West, East connection bridges
                boolean north = (gy > 0 && cellActive[gx][gy - 1]) && checkBridge(img, cx, cy, cx, cy - (int) (cellPixelH * 0.5f));
                boolean south = (gy < rows - 1 && cellActive[gx][gy + 1]) && checkBridge(img, cx, cy, cx, cy + (int) (cellPixelH * 0.5f));
                boolean west = (gx > 0 && cellActive[gx - 1][gy]) && checkBridge(img, cx, cy, cx - (int) (cellPixelW * 0.5f), cy);
                boolean east = (gx < cols - 1 && cellActive[gx + 1][gy]) && checkBridge(img, cx, cy, cx + (int) (cellPixelW * 0.5f), cy);

                // If no bridges detected but neighbors exist, connect to existing neighbors
                if (!north && !south && !west && !east) {
                    if (gy > 0 && cellActive[gx][gy - 1]) north = true;
                    if (gy < rows - 1 && cellActive[gx][gy + 1]) south = true;
                    if (gx > 0 && cellActive[gx - 1][gy]) west = true;
                    if (gx < cols - 1 && cellActive[gx + 1][gy]) east = true;
                }

                RoomShape shape = RoomShape.STRAIGHT;
                int rot = 0;
                int connCount = (north ? 1 : 0) + (south ? 1 : 0) + (west ? 1 : 0) + (east ? 1 : 0);

                if (connCount == 4) {
                    shape = RoomShape.CROSS;
                    rot = 0;
                } else if (connCount == 3) {
                    shape = RoomShape.T_SHAPE;
                    if (!north) rot = 0;      // Stem South
                    else if (!east) rot = 1;  // Stem West
                    else if (!south) rot = 2; // Stem North
                    else rot = 3;             // Stem East
                } else if (connCount == 2) {
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
                    } else { // north && west
                        shape = RoomShape.CORNER;
                        rot = 3;
                    }
                } else if (connCount == 1) {
                    shape = RoomShape.END_ROOM;
                    if (south) rot = 0;
                    else if (west) rot = 1;
                    else if (north) rot = 2;
                    else rot = 3;
                } else {
                    shape = RoomShape.SQUARE;
                    rot = 0;
                }

                // Check special room characteristics
                int color = cellColorAverage[gx][gy];
                Color c = new Color(color);
                boolean isReddish = c.getRed() > 140 && c.getBlue() < 120;
                boolean isYellowish = c.getRed() > 140 && c.getGreen() > 140 && c.getBlue() < 100;
                boolean isGreenish = c.getGreen() > 140 && c.getBlue() > 120 && c.getRed() < 100;

                String name = generateRoomName(zone, shape, gx, gy, cols, rows, isReddish, isYellowish, isGreenish);
                if (isReddish || isYellowish) {
                    if (gx == 0 || gx == cols - 1 || gy == 0 || gy == rows - 1) {
                        shape = RoomShape.CHECKPOINT;
                    } else {
                        shape = RoomShape.ELEVATOR;
                    }
                }

                // Center relative grid coords
                int finalGx = gx - centerGx;
                int finalGy = gy - centerGy;

                FacilityNetworkManager.RoomNode node = new FacilityNetworkManager.RoomNode(
                        new BlockPos(finalGx * 16, 64, finalGy * 16),
                        name,
                        zone,
                        dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity.RoomType.STANDARD,
                        shape,
                        rot,
                        zone,
                        finalGx,
                        finalGy);
                parsedRooms.add(node);
            }
        }

        if (parsedRooms.isEmpty()) {
            return new ParseResult(false, "Не удалось выделить отдельные комнаты из контура.", new ArrayList<>());
        }

        return new ParseResult(true, "Успешно распознано " + parsedRooms.size() + " комнат для сектора " + zone.name() + "!", parsedRooms);
    }

    private static boolean isSchematicPixel(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Dark background filter
        if (r < 25 && g < 35 && b < 45) return false;

        // Authentic CRT Blueprint cyan/blue hues
        if (b > 60 && (b >= r + 20 || g >= r + 20)) return true;

        // Active highlighted rooms (Emerald Green)
        if (g > 100 && g > r + 30 && b > 60) return true;

        // Special warning markers (Yellow/Red)
        if ((r > 120 && g > 120) || (r > 130 && b < 80)) return true;

        return false;
    }

    private static boolean checkBridge(BufferedImage img, int x1, int y1, int x2, int y2) {
        int midX = (x1 + x2) / 2;
        int midY = (y1 + y2) / 2;
        if (midX < 0 || midX >= img.getWidth() || midY < 0 || midY >= img.getHeight()) return false;
        int rgb = img.getRGB(midX, midY);
        return isSchematicPixel(rgb);
    }

    private static String generateRoomName(FacilityZone zone, RoomShape shape, int gx, int gy, int cols, int rows,
                                           boolean isRed, boolean isYellow, boolean isGreen) {
        if (isGreen) return "TESLA";

        if (zone == FacilityZone.HCZ) {
            if (gx <= 1 && gy == rows / 2) return "CHECKPOINT";
            if (gx == cols / 2 && gy == rows / 2) return "106 CONT";
            if (gx >= cols - 2 && gy == rows / 2) return "079 CONT";
            if (gy == 0) return "096 CONT";
            if (gy == rows - 1 && gx <= 2) return "939 CONT";
            if (gy == rows - 1 && gx == cols / 2) return "WARHEAD";
            if (gy == rows - 1 && gx > cols / 2) return "H.I.D";
            if (shape == RoomShape.STRAIGHT && (gx % 2 == 1)) return "TESLA";
            if (shape == RoomShape.T_SHAPE) return "ARMORY";
            if (shape == RoomShape.CORNER) return "TESTROOM";
            return "HCZ CORRIDOR";
        } else if (zone == FacilityZone.LCZ) {
            if (gx == 0) return "914 CONT";
            if (gx >= cols - 2 && gy <= 1) return "173 CONT";
            if (gx == cols / 2 && gy == rows / 2) return "CLASS-D";
            if (shape == RoomShape.AIRLOCK || shape == RoomShape.STRAIGHT && (gx == 2 || gx == cols - 3)) return "AIRLOCK";
            if (gx >= cols - 2 && gy >= rows - 2) return "ELEV A";
            if (gx >= cols - 2 && gy == rows / 2) return "ELEV B";
            if (gy >= rows - 2 && gx == cols / 2) return "GREENHOUSE";
            if (gy <= 1 && gx == cols / 2) return "TEST ROOM";
            if (shape == RoomShape.END_ROOM) return "ARMORY";
            return "LCZ CORRIDOR";
        } else {
            if (gx == 0 || gx == cols - 1) return "GATE A";
            if (shape == RoomShape.T_SHAPE) return "INTERCOM";
            if (shape == RoomShape.END_ROOM) return "EVAC SHELTER";
            if (shape == RoomShape.CORNER) return "CURVED OFFICE";
            return "TWO-WAY OFFICE";
        }
    }
}
