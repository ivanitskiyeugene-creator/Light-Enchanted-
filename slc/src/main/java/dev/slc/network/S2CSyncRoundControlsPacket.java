package dev.slc.network;

import dev.slc.client.ClientRoundControlCache;
import dev.slc.controls.RoundControl;
import dev.slc.controls.SlcControlSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Read-only copy of the world's OP-configured SLC controls. */
public record S2CSyncRoundControlsPacket(int[] keys) {
    public static S2CSyncRoundControlsPacket from(ServerPlayer player) {
        return new S2CSyncRoundControlsPacket(SlcControlSavedData.get(player.serverLevel()).copyKeys());
    }

    public static void encode(S2CSyncRoundControlsPacket msg, FriendlyByteBuf buf) {
        for (RoundControl ignored : RoundControl.values()) buf.writeVarInt(msg.keys[ignored.ordinal()]);
    }

    public static S2CSyncRoundControlsPacket decode(FriendlyByteBuf buf) {
        int[] keys = new int[RoundControl.values().length];
        for (RoundControl control : RoundControl.values()) keys[control.ordinal()] = buf.readVarInt();
        return new S2CSyncRoundControlsPacket(keys);
    }

    public static void handle(S2CSyncRoundControlsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientRoundControlCache.set(msg.keys));
        ctx.get().setPacketHandled(true);
    }
}
