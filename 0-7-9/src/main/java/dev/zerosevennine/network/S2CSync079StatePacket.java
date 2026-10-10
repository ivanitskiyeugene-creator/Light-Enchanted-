package dev.zerosevennine.network;

import dev.zerosevennine.client.handler.ClientCameraHandler;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CSync079StatePacket {
    private final boolean active;
    private final BlockPos cameraPos;
    private final int tier;
    private final int exp;
    private final int nextExp;
    private final float ap;
    private final float maxAp;
    private final float apRegen;
    private final List<Scp079Session.LogEntry> logEntries;

    public S2CSync079StatePacket(boolean active, BlockPos cameraPos, int tier, int exp, int nextExp,
                                 float ap, float maxAp, float apRegen, List<Scp079Session.LogEntry> logEntries) {
        this.active = active;
        this.cameraPos = cameraPos;
        this.tier = tier;
        this.exp = exp;
        this.nextExp = nextExp;
        this.ap = ap;
        this.maxAp = maxAp;
        this.apRegen = apRegen;
        this.logEntries = logEntries;
    }

    public static void encode(S2CSync079StatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeBlockPos(msg.cameraPos != null ? msg.cameraPos : BlockPos.ZERO);
        buf.writeInt(msg.tier);
        buf.writeInt(msg.exp);
        buf.writeInt(msg.nextExp);
        buf.writeFloat(msg.ap);
        buf.writeFloat(msg.maxAp);
        buf.writeFloat(msg.apRegen);

        buf.writeInt(msg.logEntries != null ? msg.logEntries.size() : 0);
        if (msg.logEntries != null) {
            for (Scp079Session.LogEntry e : msg.logEntries) {
                buf.writeUtf(e.message);
                buf.writeInt(e.colorRgb);
            }
        }
    }

    public static S2CSync079StatePacket decode(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        BlockPos camPos = buf.readBlockPos();
        int tier = buf.readInt();
        int exp = buf.readInt();
        int nextExp = buf.readInt();
        float ap = buf.readFloat();
        float maxAp = buf.readFloat();
        float apRegen = buf.readFloat();

        int logCount = buf.readInt();
        List<Scp079Session.LogEntry> logs = new ArrayList<>();
        for (int i = 0; i < logCount; i++) {
            String m = buf.readUtf();
            int c = buf.readInt();
            logs.add(new Scp079Session.LogEntry(m, c, 0));
        }

        return new S2CSync079StatePacket(active, camPos, tier, exp, nextExp, ap, maxAp, apRegen, logs);
    }

    public static void handle(S2CSync079StatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientCameraHandler.handleSyncState(msg.active, msg.cameraPos, msg.tier, msg.exp, msg.nextExp,
                    msg.ap, msg.maxAp, msg.apRegen, msg.logEntries);
        });
        ctx.get().setPacketHandled(true);
    }
}
