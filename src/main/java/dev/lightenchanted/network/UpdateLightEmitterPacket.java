package dev.lightenchanted.network;

import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: the player edited a beam in the GUI.
 * The server sanity-checks distance, applies (already clamped) config
 * and broadcasts it to all tracking clients via the BE update packet.
 */
public class UpdateLightEmitterPacket {
    private final BlockPos pos;
    private final BeamConfig config;

    public UpdateLightEmitterPacket(BlockPos pos, BeamConfig config) {
        this.pos = pos;
        this.config = config;
    }

    public static void encode(UpdateLightEmitterPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        msg.config.write(buf);
    }

    public static UpdateLightEmitterPacket decode(FriendlyByteBuf buf) {
        return new UpdateLightEmitterPacket(buf.readBlockPos(), BeamConfig.read(buf));
    }

    public static void handle(UpdateLightEmitterPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            ServerLevel level = player.serverLevel();
            // Only accept edits from players standing near the emitter.
            double dist = player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5);
            if (dist > 64.0) {
                return;
            }
            if (level.getBlockEntity(msg.pos) instanceof LightEmitterBlockEntity emitter) {
                emitter.applyConfig(msg.config);
            }
        });
        context.setPacketHandled(true);
    }
}
