package dev.zerosevennine.network;

import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
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
        public final FacilityZone targetZone;
        public final int gridX;
        public final int gridY;
        public final boolean hasGenerator;
        public final boolean isGeneratorBooting;

        public NodeData(BlockPos pos, String name, FacilityZone zone,
                        FacilityMapNodeBlockEntity.RoomType roomType, FacilityZone targetZone,
                        int gridX, int gridY, boolean hasGenerator, boolean isGeneratorBooting) {
            this.pos = pos;
            this.name = name;
            this.zone = zone;
            this.roomType = roomType;
            this.targetZone = targetZone;
            this.gridX = gridX;
            this.gridY = gridY;
            this.hasGenerator = hasGenerator;
            this.isGeneratorBooting = isGeneratorBooting;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos != null ? pos : BlockPos.ZERO);
            buf.writeUtf(name != null ? name : "Room");
            buf.writeEnum(zone != null ? zone : FacilityZone.HCZ);
            buf.writeEnum(roomType != null ? roomType : FacilityMapNodeBlockEntity.RoomType.STANDARD);
            buf.writeEnum(targetZone != null ? targetZone : FacilityZone.HCZ);
            buf.writeInt(gridX);
            buf.writeInt(gridY);
            buf.writeBoolean(hasGenerator);
            buf.writeBoolean(isGeneratorBooting);
        }

        public static NodeData read(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            String name = buf.readUtf();
            FacilityZone zone = buf.readEnum(FacilityZone.class);
            FacilityMapNodeBlockEntity.RoomType roomType = buf.readEnum(FacilityMapNodeBlockEntity.RoomType.class);
            FacilityZone targetZone = buf.readEnum(FacilityZone.class);
            int gx = buf.readInt();
            int gy = buf.readInt();
            boolean gen = buf.readBoolean();
            boolean booting = buf.readBoolean();
            return new NodeData(pos, name, zone, roomType, targetZone, gx, gy, gen, booting);
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
        this.nodes = nodes;
        this.cameras = cameras;
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
