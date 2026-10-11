package dev.zerosevennine.network;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.config.Scp079Config;
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
        SPEAKER,
        BREACH_SCANNER
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
            if (camBe == null && player.level().getBlockEntity(session.getCurrentCameraPos()) instanceof CameraBlockEntity cbe) {
                camBe = cbe;
                FacilityNetworkManager.registerCamera(session.getCurrentCameraPos(), cbe);
            }
            if (camBe == null) return;

            switch (msg.action) {
                case TOGGLE_DOOR -> {
                    float cost = Scp079Config.COMMON != null ?
                            Scp079Config.COMMON.doorToggleApCost.get().floatValue() : 5.0f;
                    if (session.spendAp(cost)) {
                        boolean ok = camBe.toggleDoor(msg.targetPos, player);
                        if (ok) {
                            session.addExp(10, player);
                            session.addLog("Door toggled", 0x2ECC71, player.level().getGameTime());
                        } else {
                            session.spendAp(-cost); // Refund
                        }
                    }
                }
                case LOCK_DOOR -> {
                    float cost = Scp079Config.COMMON != null ?
                            Scp079Config.COMMON.doorLockApCost.get().floatValue() : 15.0f;

                    boolean currentlyLocked = false;
                    for (CameraBlockEntity.LinkedDevice d : camBe.getLinkedDevices()) {
                        if (d.pos.equals(msg.targetPos) || d.pos.equals(msg.targetPos.below()) || d.pos.equals(msg.targetPos.above())) {
                            currentlyLocked = d.isLocked;
                            break;
                        }
                    }
                    boolean newLock = !currentlyLocked;

                    if (session.spendAp(cost)) {
                        boolean ok = camBe.lockDoor(msg.targetPos, player, newLock);
                        if (ok) {
                            session.addExp(25, player);
                            session.addLog(newLock ? "Door locked" : "Door unlocked", newLock ? 0xE74C3C : 0x2ECC71, player.level().getGameTime());
                            player.level().playSound(null, msg.targetPos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, newLock ? 0.6f : 1.2f);
                        } else {
                            session.spendAp(-cost); // Refund
                        }
                    }
                }
                case TRIGGER_TESLA -> {
                    float cost = Scp079Config.COMMON != null ?
                            Scp079Config.COMMON.teslaOverchargeApCost.get().floatValue() : 35.0f;
                    if (session.spendAp(cost)) {
                        camBe.triggerTesla(msg.targetPos, player);
                        session.addExp(50, player);
                        session.addLog("Tesla overcharged", 0x3498DB, player.level().getGameTime());
                        player.level().playSound(null, msg.targetPos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.0f, 1.8f);
                    }
                }
                case BLACKOUT -> {
                    float cost = Scp079Config.COMMON != null ?
                            Scp079Config.COMMON.blackoutApCost.get().floatValue() : 40.0f;
                    if (session.getTier() >= 2 && session.spendAp(cost)) {
                        camBe.triggerBlackout(200);
                        session.addExp(35, player);
                        session.addLog("Blackout initiated", 0xF39C12, player.level().getGameTime());
                        player.level().playSound(null, camBe.getBlockPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.8f);
                    }
                }
                case LOCKDOWN -> {
                    float cost = Scp079Config.COMMON != null ?
                            Scp079Config.COMMON.lockdownApCost.get().floatValue() : 80.0f;
                    if (session.getTier() >= 3 && session.spendAp(cost)) {
                        camBe.triggerLockdown(240, player);
                        session.addExp(100, player);
                        session.addLog("Room lockdown initiated", 0xE74C3C, player.level().getGameTime());
                        player.level().playSound(null, camBe.getBlockPos(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.2f, 0.5f);
                    }
                }
                case PING -> {
                    session.addLog("Tactical ping placed", 0xE67E22, player.level().getGameTime());
                    player.level().playSound(null, msg.targetPos, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0f, 1.5f);
                }
                case SPEAKER -> {
                    player.level().playSound(null, camBe.getBlockPos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 2.0f);
                    session.addLog("Intercom broadcast active", 0x9B59B6, player.level().getGameTime());
                }
                case BREACH_SCANNER -> {
                    session.toggleBreachScanner(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
