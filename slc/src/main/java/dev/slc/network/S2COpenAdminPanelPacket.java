package dev.slc.network;

import dev.slc.client.ClientAdminPanelOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class S2COpenAdminPanelPacket {
    public static void encode(S2COpenAdminPanelPacket msg, FriendlyByteBuf buf) { }
    public static S2COpenAdminPanelPacket decode(FriendlyByteBuf buf) { return new S2COpenAdminPanelPacket(); }

    public static void handle(S2COpenAdminPanelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientAdminPanelOpener::open));
        ctx.get().setPacketHandled(true);
    }
}
