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
    private final int gridX;
    private final int gridY;
    private final boolean hasGenerator;

    public C2SConfigureMapNodePacket(BlockPos nodePos, String roomName, FacilityZone zone, int gridX, int gridY, boolean hasGenerator) {
        this.nodePos = nodePos;
        this.roomName = roomName;
        this.zone = zone;
        this.gridX = gridX;
        this.gridY = gridY;
        this.hasGenerator = hasGenerator;
    }

    public static void encode(C2SConfigureMapNodePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.nodePos);
        buf.writeUtf(msg.roomName);
        buf.writeEnum(msg.zone);
        buf.writeInt(msg.gridX);
        buf.writeInt(msg.gridY);
        buf.writeBoolean(msg.hasGenerator);
    }

    public static C2SConfigureMapNodePacket decode(FriendlyByteBuf buf) {
        return new C2SConfigureMapNodePacket(buf.readBlockPos(), buf.readUtf(), buf.readEnum(FacilityZone.class),
                buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(C2SConfigureMapNodePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (player.level().getBlockEntity(msg.nodePos) instanceof FacilityMapNodeBlockEntity nodeBe) {
                nodeBe.setRoomName(msg.roomName);
                nodeBe.setZone(msg.zone);
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
