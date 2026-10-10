package dev.zerosevennine.network;

import dev.zerosevennine.client.gui.CameraConfigScreen;
import dev.zerosevennine.facility.FacilityZone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2COpenCameraConfigPacket {
    private final BlockPos cameraPos;
    private final String roomName;
    private final FacilityZone zone;

    public S2COpenCameraConfigPacket(BlockPos cameraPos, String roomName, FacilityZone zone) {
        this.cameraPos = cameraPos;
        this.roomName = roomName;
        this.zone = zone;
    }

    public static void encode(S2COpenCameraConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.cameraPos);
        buf.writeUtf(msg.roomName);
        buf.writeEnum(msg.zone);
    }

    public static S2COpenCameraConfigPacket decode(FriendlyByteBuf buf) {
        return new S2COpenCameraConfigPacket(buf.readBlockPos(), buf.readUtf(), buf.readEnum(FacilityZone.class));
    }

    public static void handle(S2COpenCameraConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft.getInstance().setScreen(new CameraConfigScreen(msg.cameraPos, msg.roomName, msg.zone));
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
