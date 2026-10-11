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
        DELETE,
        CLEAR_ZONE,
        AUTO_LINK_WASD,
        LINK_CAMERA,
        UNLINK_CAMERA
    }

    private final Action action;
    private final FacilityZone zone;
    private final int gridX;
    private final int gridY;
    private final String roomName;
    private final RoomShape roomShape;
    private final int rotation;
    private final boolean hasGenerator;
    private final FacilityZone targetZone;
    private final BlockPos cameraPos;

    public C2SEditMapRoomPacket(Action action, FacilityZone zone, int gridX, int gridY,
                               String roomName, RoomShape roomShape, int rotation,
                               boolean hasGenerator, FacilityZone targetZone, BlockPos cameraPos) {
        this.action = action;
        this.zone = zone != null ? zone : FacilityZone.HCZ;
        this.gridX = gridX;
        this.gridY = gridY;
        this.roomName = roomName != null ? roomName : "ROOM";
        this.roomShape = roomShape != null ? roomShape : RoomShape.SQUARE;
        this.rotation = rotation;
        this.hasGenerator = hasGenerator;
        this.targetZone = targetZone != null ? targetZone : this.zone;
        this.cameraPos = cameraPos != null ? cameraPos : BlockPos.ZERO;
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
        buf.writeEnum(msg.targetZone);
        buf.writeBlockPos(msg.cameraPos);
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
        FacilityZone targetZone = buf.readEnum(FacilityZone.class);
        BlockPos camPos = buf.readBlockPos();
        return new C2SEditMapRoomPacket(action, zone, gx, gy, name, shape, rot, gen, targetZone, camPos);
    }

    public static void handle(C2SEditMapRoomPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.serverLevel() == null) return;

            switch (msg.action) {
                case PLACE_OR_UPDATE -> {
                    FacilityNetworkManager.RoomNode existing = FacilityNetworkManager.getRoomAt(msg.zone, msg.gridX, msg.gridY);
                    List<BlockPos> prevCams = existing != null ? new ArrayList<>(existing.linkedCameras) : new ArrayList<>();
                    BlockPos worldPos = existing != null ? existing.pos : new BlockPos(msg.gridX * 16, 64, msg.gridY * 16);

                    FacilityNetworkManager.RoomNode node = new FacilityNetworkManager.RoomNode(
                            worldPos, msg.roomName, msg.zone, null, msg.roomShape, msg.rotation, msg.targetZone, msg.gridX, msg.gridY
                    );
                    node.hasGenerator = msg.hasGenerator;
                    node.linkedCameras.addAll(prevCams);
                    if (!msg.cameraPos.equals(BlockPos.ZERO) && !node.linkedCameras.contains(msg.cameraPos)) {
                        node.linkedCameras.add(msg.cameraPos);
                    }
                    FacilityNetworkManager.registerRoomNode(node);
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
