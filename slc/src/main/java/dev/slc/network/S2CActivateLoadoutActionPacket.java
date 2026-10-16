package dev.slc.network;

import dev.slc.client.handler.ClientRoundControls;
import dev.slc.loadout.QuickAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Confirms that the server selected the configured backing stack before the client attempts use. */
public record S2CActivateLoadoutActionPacket(QuickAction action, boolean useImmediately) {
    public static void encode(S2CActivateLoadoutActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeBoolean(msg.useImmediately);
    }

    public static S2CActivateLoadoutActionPacket decode(FriendlyByteBuf buf) {
        return new S2CActivateLoadoutActionPacket(buf.readEnum(QuickAction.class), buf.readBoolean());
    }

    public static void handle(S2CActivateLoadoutActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.useImmediately) ClientRoundControls.useActiveItemAfterSync();
        });
        ctx.get().setPacketHandled(true);
    }
}
