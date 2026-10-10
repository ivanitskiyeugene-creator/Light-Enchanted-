package dev.zerosevennine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SRequestMapSyncPacket {
    public C2SRequestMapSyncPacket() {}

    public static void encode(C2SRequestMapSyncPacket msg, FriendlyByteBuf buf) {}

    public static C2SRequestMapSyncPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestMapSyncPacket();
    }

    public static void handle(C2SRequestMapSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ModNetwork.sendFacilityMapToClient(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
