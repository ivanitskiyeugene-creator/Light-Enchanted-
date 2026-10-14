package dev.slc.network;

import dev.slc.client.ClientRoundCache;
import dev.slc.spectator.SpectatorTargetInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CSyncSpectatorDataPacket {
    private final List<SpectatorTargetInfo> alivePlayers;

    public S2CSyncSpectatorDataPacket(List<SpectatorTargetInfo> alivePlayers) {
        this.alivePlayers = alivePlayers;
    }

    public static void encode(S2CSyncSpectatorDataPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.alivePlayers.size());
        for (SpectatorTargetInfo info : msg.alivePlayers) {
            info.write(buf);
        }
    }

    public static S2CSyncSpectatorDataPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<SpectatorTargetInfo> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(SpectatorTargetInfo.read(buf));
        }
        return new S2CSyncSpectatorDataPacket(list);
    }

    public static void handle(S2CSyncSpectatorDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientRoundCache.setAlivePlayers(msg.alivePlayers);
        });
        ctx.get().setPacketHandled(true);
    }
}
