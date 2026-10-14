package dev.slc.network;

import dev.slc.client.ClientRoundCache;
import dev.slc.role.Role;
import dev.slc.round.RoundState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncRoundStatePacket {
    private final RoundState state;
    private final int roundElapsedTicks;
    private final float mtfInfluence;
    private final float chaosInfluence;
    private final Role assignedRole;

    public S2CSyncRoundStatePacket(RoundState state, int roundElapsedTicks, float mtfInfluence, float chaosInfluence, Role assignedRole) {
        this.state = state;
        this.roundElapsedTicks = roundElapsedTicks;
        this.mtfInfluence = mtfInfluence;
        this.chaosInfluence = chaosInfluence;
        this.assignedRole = assignedRole;
    }

    public static void encode(S2CSyncRoundStatePacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.state);
        buf.writeInt(msg.roundElapsedTicks);
        buf.writeFloat(msg.mtfInfluence);
        buf.writeFloat(msg.chaosInfluence);
        buf.writeEnum(msg.assignedRole);
    }

    public static S2CSyncRoundStatePacket decode(FriendlyByteBuf buf) {
        RoundState state = buf.readEnum(RoundState.class);
        int ticks = buf.readInt();
        float mtf = buf.readFloat();
        float chaos = buf.readFloat();
        Role role = buf.readEnum(Role.class);
        return new S2CSyncRoundStatePacket(state, ticks, mtf, chaos, role);
    }

    public static void handle(S2CSyncRoundStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientRoundCache.setRoundState(msg.state);
            ClientRoundCache.setRoundElapsedTicks(msg.roundElapsedTicks);
            ClientRoundCache.setInfluence(msg.mtfInfluence, msg.chaosInfluence);
            ClientRoundCache.updateLocalRole(msg.assignedRole);
        });
        ctx.get().setPacketHandled(true);
    }
}
