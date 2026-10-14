package dev.slc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SSelectInventorySlotPacket {
    private final int slotIndex; // 0 to 7

    public C2SSelectInventorySlotPacket(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public static void encode(C2SSelectInventorySlotPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.slotIndex);
    }

    public static C2SSelectInventorySlotPacket decode(FriendlyByteBuf buf) {
        return new C2SSelectInventorySlotPacket(buf.readInt());
    }

    public static void handle(C2SSelectInventorySlotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && msg.slotIndex >= 0 && msg.slotIndex < 8) {
                player.getInventory().selected = msg.slotIndex;
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
