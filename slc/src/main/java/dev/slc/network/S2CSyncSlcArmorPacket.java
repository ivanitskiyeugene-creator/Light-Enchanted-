package dev.slc.network;

import dev.slc.client.ClientSlcArmorCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CSyncSlcArmorPacket(ItemStack armor) {
    public static void encode(S2CSyncSlcArmorPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.armor);
    }

    public static S2CSyncSlcArmorPacket decode(FriendlyByteBuf buf) {
        return new S2CSyncSlcArmorPacket(buf.readItem());
    }

    public static void handle(S2CSyncSlcArmorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientSlcArmorCache.setArmor(msg.armor));
        ctx.get().setPacketHandled(true);
    }
}
