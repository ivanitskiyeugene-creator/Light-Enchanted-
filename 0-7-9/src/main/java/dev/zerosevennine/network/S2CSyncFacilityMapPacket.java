package dev.zerosevennine.network;

import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.RoomShape;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CSyncFacilityMapPacket {
    public static class NodeData {
        public final BlockPos pos;
        public final String name;
        public final FacilityZone zone;
        public final FacilityMapNodeBlockEntity.RoomType roomType;
        public final RoomShape roomShape;
        public final int rotation;
        public final FacilityZone targetZone;
        public final int gridX;
        public final int gridY;
        public final boolean hasGenerator;
        public final boolean isGeneratorBooting;
        public final boolean showText;
        public final List<BlockPos> linkedCameras = new ArrayList<>();
        public final List<int[]> customPolygon = new ArrayList<>();

        public NodeData(BlockPos pos, String name, FacilityZone zone,
                        FacilityMapNodeBlockEntity.RoomType roomType, RoomShape roomShape, int rotation,
                        FacilityZone targetZone, int gridX, int gridY, boolean hasGenerator, boolean isGeneratorBooting, boolean showText) {
            this.pos = pos;
            this.name = name;
            this.zone = zone;
            this.roomType = roomType;
            this.roomShape = roomShape != null ? roomShape : RoomShape.SQUARE;
            this.rotation = rotation;
            this.targetZone = targetZone;
            this.gridX = gridX;
            this.gridY = gridY;
            this.hasGenerator = hasGenerator;
            this.isGeneratorBooting = isGeneratorBooting;
            this.showText = showText;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos != null ? pos : BlockPos.ZERO);
            buf.writeUtf(name != null ? name : "Room");
            buf.writeEnum(zone != null ? zone : FacilityZone.HCZ);
            buf.writeEnum(roomType != null ? roomType : FacilityMapNodeBlockEntity.RoomType.STANDARD);
            buf.writeEnum(roomShape != null ? roomShape : RoomShape.SQUARE);
            buf.writeInt(rotation);
            buf.writeEnum(targetZone != null ? targetZone : FacilityZone.HCZ);
            buf.writeInt(gridX);
            buf.writeInt(gridY);
            buf.writeBoolean(hasGenerator);
            buf.writeBoolean(isGeneratorBooting);
            buf.writeBoolean(showText);

            buf.writeInt(linkedCameras.size());
            for (BlockPos cp : linkedCameras) {
                buf.writeBlockPos(cp);
            }

            buf.writeInt(customPolygon.size());
            for (int[] pt : customPolygon) {
                buf.writeInt(pt[0]);
                buf.writeInt(pt[1]);
            }
        }

        public static NodeData read(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            String name = buf.readUtf();
            FacilityZone zone = buf.readEnum(FacilityZone.class);
            FacilityMapNodeBlockEntity.RoomType roomType = buf.readEnum(FacilityMapNodeBlockEntity.RoomType.class);
            RoomShape roomShape = buf.readEnum(RoomShape.class);
            int rot = buf.readInt();
            FacilityZone targetZone = buf.readEnum(FacilityZone.class);
            int gx = buf.readInt();
            int gy = buf.readInt();
            boolean gen = buf.readBoolean();
            boolean booting = buf.readBoolean();
            boolean showText = buf.readBoolean();
            NodeData data = new NodeData(pos, name, zone, roomType, roomShape, rot, targetZone, gx, gy, gen, booting, showText);

            int camCount = buf.readInt();
            for (int i = 0; i < camCount; i++) {
                data.linkedCameras.add(buf.readBlockPos());
            }

            int polyCount = buf.readInt();
            for (int i = 0; i < polyCount; i++) {
                data.customPolygon.add(new int[]{buf.readInt(), buf.readInt()});
            }

            return data;
        }
    }

    public static class CameraData {
        public final BlockPos pos;
        public final String name;
        public final FacilityZone zone;

        public CameraData(BlockPos pos, String name, FacilityZone zone) {
            this.pos = pos;
            this.name = name;
            this.zone = zone;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos != null ? pos : BlockPos.ZERO);
            buf.writeUtf(name != null ? name : "Camera");
            buf.writeEnum(zone != null ? zone : FacilityZone.HCZ);
        }

        public static CameraData read(FriendlyByteBuf buf) {
            return new CameraData(buf.readBlockPos(), buf.readUtf(), buf.readEnum(FacilityZone.class));
        }
    }

    private final List<NodeData> nodes;
    private final List<CameraData> cameras;

    public S2CSyncFacilityMapPacket(List<NodeData> nodes, List<CameraData> cameras) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
        this.cameras = cameras != null ? cameras : new ArrayList<>();
    }

    public static void encode(S2CSyncFacilityMapPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.nodes.size());
        for (NodeData n : msg.nodes) {
            n.write(buf);
        }
        buf.writeInt(msg.cameras.size());
        for (CameraData c : msg.cameras) {
            c.write(buf);
        }
    }

    public static S2CSyncFacilityMapPacket decode(FriendlyByteBuf buf) {
        int nodeCount = buf.readInt();
        List<NodeData> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(NodeData.read(buf));
        }

        int camCount = buf.readInt();
        List<CameraData> cameras = new ArrayList<>(camCount);
        for (int i = 0; i < camCount; i++) {
            cameras.add(CameraData.read(buf));
        }

        return new S2CSyncFacilityMapPacket(nodes, cameras);
    }

    public static void handle(S2CSyncFacilityMapPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            FacilityNetworkManager.applyClientMapSync(msg.nodes, msg.cameras);
        });
        ctx.get().setPacketHandled(true);
    }
}
