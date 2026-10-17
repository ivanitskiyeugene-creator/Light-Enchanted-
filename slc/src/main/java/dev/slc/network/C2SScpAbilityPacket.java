package dev.slc.network;

import dev.slc.scp.ScpAbilityAction;
import dev.slc.scp.ScpAbilityManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SScpAbilityPacket(ScpAbilityAction action) {
    public static void encode(C2SScpAbilityPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
    }

    public static C2SScpAbilityPacket decode(FriendlyByteBuf buf) {
        return new C2SScpAbilityPacket(buf.readEnum(ScpAbilityAction.class));
    }

    public static void handle(C2SScpAbilityPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) ScpAbilityManager.activate(context.getSender(), msg.action);
        });
        context.setPacketHandled(true);
    }
}
