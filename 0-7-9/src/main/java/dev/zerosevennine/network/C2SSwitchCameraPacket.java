package dev.zerosevennine.network;

import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.system.Scp079PlayerManager;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SSwitchCameraPacket {
    private final Direction wasdDir;
    private final BlockPos targetCamPos;

    public C2SSwitchCameraPacket(Direction wasdDir) {
        this.wasdDir = wasdDir;
        this.targetCamPos = null;
    }

    public C2SSwitchCameraPacket(BlockPos targetCamPos) {
        this.wasdDir = null;
        this.targetCamPos = targetCamPos;
    }

    public static void encode(C2SSwitchCameraPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.wasdDir != null);
        if (msg.wasdDir != null) {
            buf.writeEnum(msg.wasdDir);
        } else {
            buf.writeBlockPos(msg.targetCamPos != null ? msg.targetCamPos : BlockPos.ZERO);
        }
    }

    public static C2SSwitchCameraPacket decode(FriendlyByteBuf buf) {
        boolean isWasd = buf.readBoolean();
        if (isWasd) {
            return new C2SSwitchCameraPacket(buf.readEnum(Direction.class));
        } else {
            return new C2SSwitchCameraPacket(buf.readBlockPos());
        }
    }

    public static void handle(C2SSwitchCameraPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Scp079Session session = Scp079PlayerManager.getSession(player.getUUID());
            if (session == null) return;

            BlockPos targetPos = msg.targetCamPos;
            if (msg.wasdDir != null) {
                targetPos = FacilityNetworkManager.findNextCamera(session.getCurrentCameraPos(), msg.wasdDir);
            }

            if (targetPos != null && !targetPos.equals(session.getCurrentCameraPos())) {
                if (session.spendAp(2.0f)) {
                    Scp079PlayerManager.switchCamera(player, targetPos);
                    player.level().playSound(null, targetPos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 0.8f, 1.8f);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
