package dev.zerosevennine.network;

import dev.zerosevennine.client.gui.MapNodeConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2COpenMapNodeConfigPacket {
    private final BlockPos nodePos;

    public S2COpenMapNodeConfigPacket(BlockPos nodePos) {
        this.nodePos = nodePos;
    }

    public static void encode(S2COpenMapNodeConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.nodePos);
    }

    public static S2COpenMapNodeConfigPacket decode(FriendlyByteBuf buf) {
        return new S2COpenMapNodeConfigPacket(buf.readBlockPos());
    }

    public static void handle(S2COpenMapNodeConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft.getInstance().setScreen(new MapNodeConfigScreen(msg.nodePos));
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
