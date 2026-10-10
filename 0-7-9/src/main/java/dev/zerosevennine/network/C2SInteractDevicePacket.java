package dev.zerosevennine.network;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.facility.DeviceType;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.system.Scp079PlayerManager;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SInteractDevicePacket {
    public enum Action {
        TOGGLE_DOOR,
        LOCK_DOOR,
        TRIGGER_TESLA,
        BLACKOUT,
        LOCKDOWN,
        PING,
        SPEAKER
    }

    private final BlockPos targetPos;
    private final Action action;

    public C2SInteractDevicePacket(BlockPos targetPos, Action action) {
        this.targetPos = targetPos;
        this.action = action;
    }

    public static void encode(C2SInteractDevicePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.targetPos != null ? msg.targetPos : BlockPos.ZERO);
        buf.writeEnum(msg.action);
    }

    public static C2SInteractDevicePacket decode(FriendlyByteBuf buf) {
        return new C2SInteractDevicePacket(buf.readBlockPos(), buf.readEnum(Action.class));
    }

    public static void handle(C2SInteractDevicePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Scp079Session session = Scp079PlayerManager.getSession(player.getUUID());
            if (session == null) return;

            CameraBlockEntity camBe = FacilityNetworkManager.getCamera(session.getCurrentCameraPos());
            if (camBe == null) return;

            switch (msg.action) {
                case TOGGLE_DOOR -> {
                    if (session.spendAp(5.0f)) {
                        boolean ok = camBe.toggleDoor(msg.targetPos);
                        if (ok) {
                            session.addExp(10, player);
                        }
                    }
                }
                case LOCK_DOOR -> {
                    if (session.spendAp(15.0f)) {
                        boolean ok = camBe.lockDoor(msg.targetPos, true);
                        if (ok) {
                            session.addExp(25, player);
                            session.addLog("DOOR LOCKDOWN ENGAGED", 0xE74C3C, player.level().getGameTime());
                        }
                    }
                }
                case TRIGGER_TESLA -> {
                    if (session.spendAp(35.0f)) {
                        camBe.triggerTesla(msg.targetPos);
                        session.addExp(50, player);
                        session.addLog("TESLA GATE OVERCHARGED", 0x3498DB, player.level().getGameTime());
                        player.level().playSound(null, msg.targetPos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.0f, 1.8f);
                    }
                }
                case BLACKOUT -> {
                    if (session.getTier() >= 2 && session.spendAp(40.0f)) {
                        camBe.triggerBlackout(200);
                        session.addExp(35, player);
                        session.addLog("FACILITY BLACKOUT INITIATED", 0xF39C12, player.level().getGameTime());
                        player.level().playSound(null, camBe.getBlockPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.8f);
                    }
                }
                case LOCKDOWN -> {
                    if (session.getTier() >= 3 && session.spendAp(80.0f)) {
                        camBe.triggerLockdown(240);
                        session.addExp(100, player);
                        session.addLog("ROOM FULL LOCKDOWN ENGAGED", 0xE74C3C, player.level().getGameTime());
                        player.level().playSound(null, camBe.getBlockPos(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.2f, 0.5f);
                    }
                }
                case PING -> {
                    session.addLog("TACTICAL PING PLACED", 0xE67E22, player.level().getGameTime());
                    player.level().playSound(null, msg.targetPos, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0f, 1.5f);
                }
                case SPEAKER -> {
                    player.level().playSound(null, camBe.getBlockPos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 2.0f);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
