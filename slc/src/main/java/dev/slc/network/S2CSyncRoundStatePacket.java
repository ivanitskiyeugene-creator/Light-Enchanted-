package dev.slc.network;

import dev.slc.client.ClientRoundCache;
import dev.slc.round.RoundState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncRoundStatePacket {
    private final RoundState state;
    private final int roundElapsedTicks;
    private final float mtfInfluence;
    private final float chaosInfluence;

    public S2CSyncRoundStatePacket(RoundState state, int roundElapsedTicks, float mtfInfluence, float chaosInfluence) {
        this.state = state;
        this.roundElapsedTicks = roundElapsedTicks;
        this.mtfInfluence = mtfInfluence;
        this.chaosInfluence = chaosInfluence;
    }

    public static void encode(S2CSyncRoundStatePacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.state);
        buf.writeInt(msg.roundElapsedTicks);
        buf.writeFloat(msg.mtfInfluence);
        buf.writeFloat(msg.chaosInfluence);
    }

    public static S2CSyncRoundStatePacket decode(FriendlyByteBuf buf) {
        RoundState state = buf.readEnum(RoundState.class);
        int ticks = buf.readInt();
        float mtf = buf.readFloat();
        float chaos = buf.readFloat();
        return new S2CSyncRoundStatePacket(state, ticks, mtf, chaos);
    }

    public static void handle(S2CSyncRoundStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientRoundCache.setRoundState(msg.state);
            ClientRoundCache.setRoundElapsedTicks(msg.roundElapsedTicks);
            ClientRoundCache.setInfluence(msg.mtfInfluence, msg.chaosInfluence);
        });
        ctx.get().setPacketHandled(true);
    }
}
