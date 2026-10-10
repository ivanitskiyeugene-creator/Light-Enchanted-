package dev.zerosevennine.network;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SConfigureCameraPacket {
    private final BlockPos cameraPos;
    private final String roomName;
    private final FacilityZone zone;

    public C2SConfigureCameraPacket(BlockPos cameraPos, String roomName, FacilityZone zone) {
        this.cameraPos = cameraPos;
        this.roomName = roomName;
        this.zone = zone;
    }

    public static void encode(C2SConfigureCameraPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.cameraPos);
        buf.writeUtf(msg.roomName);
        buf.writeEnum(msg.zone);
    }

    public static C2SConfigureCameraPacket decode(FriendlyByteBuf buf) {
        return new C2SConfigureCameraPacket(buf.readBlockPos(), buf.readUtf(), buf.readEnum(FacilityZone.class));
    }

    public static void handle(C2SConfigureCameraPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (player.level().getBlockEntity(msg.cameraPos) instanceof CameraBlockEntity camBe) {
                camBe.setCameraName(msg.roomName);
                camBe.setZone(msg.zone);
                camBe.setChanged();
                player.level().sendBlockUpdated(msg.cameraPos, camBe.getBlockState(), camBe.getBlockState(), 3);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
