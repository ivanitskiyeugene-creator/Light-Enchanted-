package dev.zerosevennine.network;

import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.RoomShape;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class C2SEditMapRoomPacket {
    public enum Action {
        PLACE_OR_UPDATE,
        SAVE_ROOM,
        DELETE,
        CLEAR_ZONE,
        AUTO_LINK_WASD,
        LINK_CAMERA,
        UNLINK_CAMERA,
        MOVE_ROOM
    }

    private final Action action;
    private final FacilityZone zone;
    private final int gridX;
    private final int gridY;
    private final String roomName;
    private final RoomShape roomShape;
    private final int rotation;
    private final boolean hasGenerator;
    private final boolean showText;
    private final FacilityZone targetZone;
    private final BlockPos cameraPos;
    private final int toGridX;
    private final int toGridY;
    private final List<int[]> customPolygon = new ArrayList<>();
    private final List<BlockPos> linkedCameras = new ArrayList<>();

    public C2SEditMapRoomPacket(Action action, FacilityZone zone, int gridX, int gridY,
                               String roomName, RoomShape roomShape, int rotation,
                               boolean hasGenerator, FacilityZone targetZone, BlockPos cameraPos) {
        this(action, zone, gridX, gridY, roomName, roomShape, rotation, hasGenerator, true, targetZone, cameraPos, gridX, gridY, null, null);
    }

    public C2SEditMapRoomPacket(Action action, FacilityZone zone, int gridX, int gridY,
                               String roomName, RoomShape roomShape, int rotation,
                               boolean hasGenerator, FacilityZone targetZone, BlockPos cameraPos,
                               int toGridX, int toGridY, List<int[]> polygon) {
        this(action, zone, gridX, gridY, roomName, roomShape, rotation, hasGenerator, true, targetZone, cameraPos, toGridX, toGridY, polygon, null);
    }

    public C2SEditMapRoomPacket(Action action, FacilityZone zone, int gridX, int gridY,
                               String roomName, RoomShape roomShape, int rotation,
                               FacilityZone targetZone, boolean hasGenerator,
                               List<BlockPos> cameras, List<int[]> polygon) {
        this(action, zone, gridX, gridY, roomName, roomShape, rotation, hasGenerator, true, targetZone, BlockPos.ZERO, gridX, gridY, polygon, cameras);
    }

    public C2SEditMapRoomPacket(Action action, FacilityZone zone, int gridX, int gridY,
                               String roomName, RoomShape roomShape, int rotation,
                               FacilityZone targetZone, boolean hasGenerator, boolean showText,
                               List<BlockPos> cameras, List<int[]> polygon) {
        this(action, zone, gridX, gridY, roomName, roomShape, rotation, hasGenerator, showText, targetZone, BlockPos.ZERO, gridX, gridY, polygon, cameras);
    }

    public C2SEditMapRoomPacket(Action action, FacilityZone zone, int gridX, int gridY,
                               String roomName, RoomShape roomShape, int rotation,
                               boolean hasGenerator, boolean showText, FacilityZone targetZone, BlockPos cameraPos,
                               int toGridX, int toGridY, List<int[]> polygon, List<BlockPos> cameras) {
        this.action = action;
        this.zone = zone != null ? zone : FacilityZone.HCZ;
        this.gridX = gridX;
        this.gridY = gridY;
        this.roomName = roomName != null ? roomName : "ROOM";
        this.roomShape = roomShape != null ? roomShape : RoomShape.SQUARE;
        this.rotation = rotation;
        this.hasGenerator = hasGenerator;
        this.showText = showText;
        this.targetZone = targetZone != null ? targetZone : this.zone;
        this.cameraPos = cameraPos != null ? cameraPos : BlockPos.ZERO;
        this.toGridX = toGridX;
        this.toGridY = toGridY;
        if (polygon != null) {
            for (int[] pt : polygon) {
                this.customPolygon.add(new int[]{pt[0], pt[1]});
            }
        }
        if (cameras != null) {
            for (BlockPos cp : cameras) {
                this.linkedCameras.add(cp.immutable());
            }
        }
    }

    public static void encode(C2SEditMapRoomPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeEnum(msg.zone);
        buf.writeInt(msg.gridX);
        buf.writeInt(msg.gridY);
        buf.writeUtf(msg.roomName);
        buf.writeEnum(msg.roomShape);
        buf.writeInt(msg.rotation);
        buf.writeBoolean(msg.hasGenerator);
        buf.writeBoolean(msg.showText);
        buf.writeEnum(msg.targetZone);
        buf.writeBlockPos(msg.cameraPos);
        buf.writeInt(msg.toGridX);
        buf.writeInt(msg.toGridY);

        buf.writeInt(msg.customPolygon.size());
        for (int[] pt : msg.customPolygon) {
            buf.writeInt(pt[0]);
            buf.writeInt(pt[1]);
        }

        buf.writeInt(msg.linkedCameras.size());
        for (BlockPos cp : msg.linkedCameras) {
            buf.writeBlockPos(cp);
        }
    }

    public static C2SEditMapRoomPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        FacilityZone zone = buf.readEnum(FacilityZone.class);
        int gx = buf.readInt();
        int gy = buf.readInt();
        String name = buf.readUtf();
        RoomShape shape = buf.readEnum(RoomShape.class);
        int rot = buf.readInt();
        boolean gen = buf.readBoolean();
        boolean showTxt = buf.readBoolean();
        FacilityZone targetZone = buf.readEnum(FacilityZone.class);
        BlockPos camPos = buf.readBlockPos();
        int toGx = buf.readInt();
        int toGy = buf.readInt();

        int polyCount = buf.readInt();
        List<int[]> poly = new ArrayList<>();
        for (int i = 0; i < polyCount; i++) {
            poly.add(new int[]{buf.readInt(), buf.readInt()});
        }

        int camCount = buf.readInt();
        List<BlockPos> cams = new ArrayList<>();
        for (int i = 0; i < camCount; i++) {
            cams.add(buf.readBlockPos());
        }

        return new C2SEditMapRoomPacket(action, zone, gx, gy, name, shape, rot, gen, showTxt, targetZone, camPos, toGx, toGy, poly, cams);
    }

    public static void handle(C2SEditMapRoomPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.serverLevel() == null) return;

            switch (msg.action) {
                case PLACE_OR_UPDATE, SAVE_ROOM -> {
                    FacilityNetworkManager.RoomNode existing = FacilityNetworkManager.getRoomAt(msg.zone, msg.gridX, msg.gridY);
                    List<BlockPos> finalCams = new ArrayList<>();

                    if (!msg.linkedCameras.isEmpty()) {
                        finalCams.addAll(msg.linkedCameras);
                    } else if (existing != null) {
                        finalCams.addAll(existing.linkedCameras);
                    }

                    if (!msg.cameraPos.equals(BlockPos.ZERO) && !finalCams.contains(msg.cameraPos)) {
                        finalCams.add(msg.cameraPos);
                    }

                    BlockPos worldPos = existing != null ? existing.pos : new BlockPos(msg.gridX * 16, 64, msg.gridY * 16);

                    FacilityNetworkManager.RoomNode node = new FacilityNetworkManager.RoomNode(
                            worldPos, msg.roomName, msg.zone, null, msg.roomShape, msg.rotation, msg.targetZone, msg.gridX, msg.gridY
                    );
                    node.hasGenerator = msg.hasGenerator;
                    node.showText = msg.showText;
                    node.linkedCameras.addAll(finalCams);

                    if (!msg.customPolygon.isEmpty()) {
                        node.customPolygon.clear();
                        for (int[] pt : msg.customPolygon) {
                            node.customPolygon.add(new int[]{pt[0], pt[1]});
                        }
                    }
                    FacilityNetworkManager.registerRoomNode(node);
                    FacilityNetworkManager.markDirtyAndSync(player.serverLevel());
                }
                case MOVE_ROOM -> {
                    FacilityNetworkManager.moveRoom(msg.zone, msg.gridX, msg.gridY, msg.toGridX, msg.toGridY);
                    FacilityNetworkManager.markDirtyAndSync(player.serverLevel());
                }
                case DELETE -> {
                    FacilityNetworkManager.removeRoom(msg.zone, msg.gridX, msg.gridY);
                    FacilityNetworkManager.markDirtyAndSync(player.serverLevel());
                }
                case CLEAR_ZONE -> {
                    FacilityNetworkManager.clearZone(msg.zone);
                    FacilityNetworkManager.markDirtyAndSync(player.serverLevel());
                }
                case AUTO_LINK_WASD -> {
                    FacilityNetworkManager.autoLinkWasd(player.serverLevel());
                    FacilityNetworkManager.markDirtyAndSync(player.serverLevel());
                }
                case LINK_CAMERA -> {
                    FacilityNetworkManager.RoomNode room = FacilityNetworkManager.getRoomAt(msg.zone, msg.gridX, msg.gridY);
                    if (room != null && !msg.cameraPos.equals(BlockPos.ZERO) && !room.linkedCameras.contains(msg.cameraPos)) {
                        room.linkedCameras.add(msg.cameraPos);
                        FacilityNetworkManager.markDirtyAndSync(player.serverLevel());
                    }
                }
                case UNLINK_CAMERA -> {
                    FacilityNetworkManager.RoomNode room = FacilityNetworkManager.getRoomAt(msg.zone, msg.gridX, msg.gridY);
                    if (room != null && !msg.cameraPos.equals(BlockPos.ZERO)) {
                        room.linkedCameras.remove(msg.cameraPos);
                        FacilityNetworkManager.markDirtyAndSync(player.serverLevel());
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
