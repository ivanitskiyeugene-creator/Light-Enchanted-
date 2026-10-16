package dev.slc.network;

import dev.slc.client.ClientAdminPanelState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CAdminStatusPacket(String message, boolean success) {
    public static void encode(S2CAdminStatusPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.message, 256);
        buf.writeBoolean(msg.success);
    }

    public static S2CAdminStatusPacket decode(FriendlyByteBuf buf) {
        return new S2CAdminStatusPacket(buf.readUtf(256), buf.readBoolean());
    }

    public static void handle(S2CAdminStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientAdminPanelState.setStatus(msg.message, msg.success));
        ctx.get().setPacketHandled(true);
    }
}
