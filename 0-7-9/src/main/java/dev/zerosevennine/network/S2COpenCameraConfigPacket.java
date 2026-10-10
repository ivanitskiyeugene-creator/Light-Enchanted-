package dev.zerosevennine.network;

import dev.zerosevennine.client.gui.CameraConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2COpenCameraConfigPacket {
    private final BlockPos cameraPos;

    public S2COpenCameraConfigPacket(BlockPos cameraPos) {
        this.cameraPos = cameraPos;
    }

    public static void encode(S2COpenCameraConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.cameraPos);
    }

    public static S2COpenCameraConfigPacket decode(FriendlyByteBuf buf) {
        return new S2COpenCameraConfigPacket(buf.readBlockPos());
    }

    public static void handle(S2COpenCameraConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft.getInstance().setScreen(new CameraConfigScreen(msg.cameraPos));
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
