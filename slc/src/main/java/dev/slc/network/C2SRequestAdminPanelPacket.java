package dev.slc.network;

import dev.slc.admin.AdminPermission;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sent only by the configurable client keybind; the server remains the authority on opening. */
public final class C2SRequestAdminPanelPacket {
    public static void encode(C2SRequestAdminPanelPacket msg, FriendlyByteBuf buf) { }
    public static C2SRequestAdminPanelPacket decode(FriendlyByteBuf buf) { return new C2SRequestAdminPanelPacket(); }

    public static void handle(C2SRequestAdminPanelPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (AdminPermission.isAdmin(player)) {
                ModNetwork.sendToPlayer(new S2COpenAdminPanelPacket(), player);
            }
        });
        context.setPacketHandled(true);
    }
}
