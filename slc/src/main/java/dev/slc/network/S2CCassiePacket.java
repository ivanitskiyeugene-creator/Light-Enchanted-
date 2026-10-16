package dev.slc.network;

import dev.slc.client.ClientRoundCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CCassiePacket {
    private final String text;
    private final int colorRgb;

    public S2CCassiePacket(String text, int colorRgb) {
        this.text = text;
        this.colorRgb = colorRgb;
    }

    public static void encode(S2CCassiePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.text);
        buf.writeInt(msg.colorRgb);
    }

    public static S2CCassiePacket decode(FriendlyByteBuf buf) {
        return new S2CCassiePacket(buf.readUtf(), buf.readInt());
    }

    public static void handle(S2CCassiePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientRoundCache.pushCassieMessage(msg.text, msg.colorRgb));
        ctx.get().setPacketHandled(true);
    }
}
