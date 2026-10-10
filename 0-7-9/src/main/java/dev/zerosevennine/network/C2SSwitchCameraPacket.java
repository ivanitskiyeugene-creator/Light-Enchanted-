package dev.zerosevennine.network;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.system.Scp079PlayerManager;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
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
            boolean isWasd = (msg.wasdDir != null);

            if (isWasd) {
                targetPos = FacilityNetworkManager.findNextCamera(session.getCurrentCameraPos(), msg.wasdDir);
            }

            if (targetPos != null) {
                // If targetPos is a MapNode position, resolve to its camera
                CameraBlockEntity targetCam = FacilityNetworkManager.getCamera(targetPos);
                if (targetCam == null && player.level().getBlockEntity(targetPos) instanceof CameraBlockEntity cbe) {
                    targetCam = cbe;
                    FacilityNetworkManager.registerCamera(targetPos, cbe);
                }

                if (targetCam == null) {
                    double bestDist = Double.MAX_VALUE;
                    BlockPos bestCamPos = null;
                    for (Map.Entry<BlockPos, CameraBlockEntity> entry : FacilityNetworkManager.getCameras().entrySet()) {
                        double d = entry.getKey().distSqr(targetPos);
                        if (d < bestDist) {
                            bestDist = d;
                            bestCamPos = entry.getKey();
                        }
                    }
                    if (bestCamPos != null) {
                        targetPos = bestCamPos;
                        targetCam = FacilityNetworkManager.getCamera(bestCamPos);
                    }
                }

                if (targetPos != null && !targetPos.equals(session.getCurrentCameraPos())) {
                    CameraBlockEntity currentCam = FacilityNetworkManager.getCamera(session.getCurrentCameraPos());
                    FacilityZone currentZone = currentCam != null ? currentCam.getZone() : FacilityZone.HCZ;

                    FacilityZone targetZone = targetCam != null ? targetCam.getZone() : currentZone;

                    boolean isCrossZone = (targetZone != currentZone);
                    // Map navigation within same zone is 0 AP (FREE). Cross-zone transition (Elevators/Checkpoints) is 10 AP. WASD is 2 AP.
                    float cost = isWasd ? 2.0f : (isCrossZone ? 10.0f : 0.0f);

                    if (session.spendAp(cost)) {
                        Scp079PlayerManager.switchCamera(player, targetPos);
                        float pitch = isCrossZone ? 1.0f : 1.8f;
                        player.level().playSound(null, targetPos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 0.8f, pitch);
                        if (isCrossZone) {
                            session.addLog("TRANSFERRED TO " + targetZone.getCode() + " (-10 AP)", 0x00E5FF, player.level().getGameTime());
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
