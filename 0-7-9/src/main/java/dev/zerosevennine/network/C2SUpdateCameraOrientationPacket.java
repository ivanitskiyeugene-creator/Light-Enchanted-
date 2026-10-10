package dev.zerosevennine.network;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.system.Scp079PlayerManager;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SUpdateCameraOrientationPacket {
    private final float yaw;
    private final float pitch;

    public C2SUpdateCameraOrientationPacket(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static void encode(C2SUpdateCameraOrientationPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.yaw);
        buf.writeFloat(msg.pitch);
    }

    public static C2SUpdateCameraOrientationPacket decode(FriendlyByteBuf buf) {
        return new C2SUpdateCameraOrientationPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(C2SUpdateCameraOrientationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Scp079Session session = Scp079PlayerManager.getSession(player.getUUID());
            if (session == null) return;

            CameraBlockEntity camBe = FacilityNetworkManager.getCamera(session.getCurrentCameraPos());
            if (camBe != null) {
                camBe.updateOrientation(msg.yaw, msg.pitch);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
