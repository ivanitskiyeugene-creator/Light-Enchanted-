package dev.slc.network;

import dev.slc.client.ClientRoundCache;
import dev.slc.role.Role;
import dev.slc.round.RoundState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CSyncRoundStatePacket {
    private final RoundState state;
    private final int roundElapsedTicks;
    private final float mtfInfluence;
    private final float chaosInfluence;
    private final Role assignedRole;
    private final boolean selfTied;
    private final Set<UUID> tiedPlayers;

    public S2CSyncRoundStatePacket(RoundState state, int roundElapsedTicks, float mtfInfluence,
                                   float chaosInfluence, Role assignedRole, boolean selfTied, Set<UUID> tiedPlayers) {
        this.state = state;
        this.roundElapsedTicks = roundElapsedTicks;
        this.mtfInfluence = mtfInfluence;
        this.chaosInfluence = chaosInfluence;
        this.assignedRole = assignedRole;
        this.selfTied = selfTied;
        this.tiedPlayers = tiedPlayers;
    }

    public static void encode(S2CSyncRoundStatePacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.state);
        buf.writeInt(msg.roundElapsedTicks);
        buf.writeFloat(msg.mtfInfluence);
        buf.writeFloat(msg.chaosInfluence);
        buf.writeEnum(msg.assignedRole);
        buf.writeBoolean(msg.selfTied);
        buf.writeVarInt(msg.tiedPlayers.size());
        for (UUID id : msg.tiedPlayers) {
            buf.writeUUID(id);
        }
    }

    public static S2CSyncRoundStatePacket decode(FriendlyByteBuf buf) {
        RoundState state = buf.readEnum(RoundState.class);
        int ticks = buf.readInt();
        float mtf = buf.readFloat();
        float chaos = buf.readFloat();
        Role role = buf.readEnum(Role.class);
        boolean selfTied = buf.readBoolean();
        int count = buf.readVarInt();
        Set<UUID> tied = new HashSet<>();
        for (int i = 0; i < count; i++) {
            tied.add(buf.readUUID());
        }
        return new S2CSyncRoundStatePacket(state, ticks, mtf, chaos, role, selfTied, tied);
    }

    public static void handle(S2CSyncRoundStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientRoundCache.setRoundState(msg.state);
            ClientRoundCache.setRoundElapsedTicks(msg.roundElapsedTicks);
            ClientRoundCache.setInfluence(msg.mtfInfluence, msg.chaosInfluence);
            ClientRoundCache.updateLocalRole(msg.assignedRole);
            ClientRoundCache.setLocalTied(msg.selfTied);
            ClientRoundCache.setTiedPlayers(msg.tiedPlayers);
        });
        ctx.get().setPacketHandled(true);
    }
}
