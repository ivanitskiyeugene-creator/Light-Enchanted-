package dev.zerosevennine.network;

import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.facility.FacilityZone;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SConfigureMapNodePacket {
    private final BlockPos nodePos;
    private final String roomName;
    private final FacilityZone zone;
    private final FacilityMapNodeBlockEntity.RoomType roomType;
    private final FacilityZone targetZone;
    private final int gridX;
    private final int gridY;
    private final boolean hasGenerator;

    public C2SConfigureMapNodePacket(BlockPos nodePos, String roomName, FacilityZone zone,
                                     FacilityMapNodeBlockEntity.RoomType roomType, FacilityZone targetZone,
                                     int gridX, int gridY, boolean hasGenerator) {
        this.nodePos = nodePos;
        this.roomName = roomName;
        this.zone = zone;
        this.roomType = roomType;
        this.targetZone = targetZone;
        this.gridX = gridX;
        this.gridY = gridY;
        this.hasGenerator = hasGenerator;
    }

    public static void encode(C2SConfigureMapNodePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.nodePos);
        buf.writeUtf(msg.roomName);
        buf.writeEnum(msg.zone);
        buf.writeEnum(msg.roomType);
        buf.writeBoolean(msg.targetZone != null);
        if (msg.targetZone != null) {
            buf.writeEnum(msg.targetZone);
        }
        buf.writeInt(msg.gridX);
        buf.writeInt(msg.gridY);
        buf.writeBoolean(msg.hasGenerator);
    }

    public static C2SConfigureMapNodePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf();
        FacilityZone zone = buf.readEnum(FacilityZone.class);
        FacilityMapNodeBlockEntity.RoomType type = buf.readEnum(FacilityMapNodeBlockEntity.RoomType.class);
        FacilityZone targetZ = buf.readBoolean() ? buf.readEnum(FacilityZone.class) : null;
        int gx = buf.readInt();
        int gy = buf.readInt();
        boolean gen = buf.readBoolean();
        return new C2SConfigureMapNodePacket(pos, name, zone, type, targetZ, gx, gy, gen);
    }

    public static void handle(C2SConfigureMapNodePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (player.level().getBlockEntity(msg.nodePos) instanceof FacilityMapNodeBlockEntity nodeBe) {
                nodeBe.setRoomName(msg.roomName);
                nodeBe.setZone(msg.zone);
                nodeBe.setRoomType(msg.roomType);
                nodeBe.setTargetZone(msg.targetZone);
                nodeBe.setGridX(msg.gridX);
                nodeBe.setGridY(msg.gridY);
                nodeBe.setHasGenerator(msg.hasGenerator);
                nodeBe.setChanged();
                player.level().sendBlockUpdated(msg.nodePos, nodeBe.getBlockState(), nodeBe.getBlockState(), 3);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
