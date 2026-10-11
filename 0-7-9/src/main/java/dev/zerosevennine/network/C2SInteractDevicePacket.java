package dev.zerosevennine.network;

import dev.zerosevennine.ai.Scp079DataManager;
import dev.zerosevennine.ai.Scp079PlayerData;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SInteractDevicePacket {

    public enum ActionType {
        DOOR_TOGGLE(2.0f),
        DOOR_LOCK_TOGGLE(10.0f),
        TESLA_TRIGGER(20.0f),
        ELEVATOR_CALL(5.0f);

        public final float apCost;
        ActionType(float apCost) {
            this.apCost = apCost;
        }
    }

    private final BlockPos cameraPos;
    private final BlockPos devicePos;
    private final ActionType action;

    public C2SInteractDevicePacket(BlockPos cameraPos, BlockPos devicePos, ActionType action) {
        this.cameraPos = cameraPos;
        this.devicePos = devicePos;
        this.action = action;
    }

    public C2SInteractDevicePacket(FriendlyByteBuf buf) {
        this.cameraPos = buf.readBlockPos();
        this.devicePos = buf.readBlockPos();
        this.action = buf.readEnum(ActionType.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(cameraPos);
        buf.writeBlockPos(devicePos);
        buf.writeEnum(action);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            Scp079PlayerData data = Scp079DataManager.get(player);
            if (!data.isActive()) return;

            if (data.consumeAp(action.apCost)) {
                BlockEntity be = player.serverLevel().getBlockEntity(cameraPos);
                if (be instanceof CameraBlockEntity cbe) {
                    boolean success = false;
                    switch (action) {
                        case DOOR_TOGGLE -> {
                            success = cbe.toggleDoor(devicePos, player);
                            if (success) {
                                data.addExp(5.0f);
                                player.displayClientMessage(Component.literal("§a[079] Door toggled"), true);
                            }
                        }
                        case DOOR_LOCK_TOGGLE -> {
                            boolean locked = cbe.toggleLockDoor(devicePos, player);
                            data.addExp(10.0f);
                            player.displayClientMessage(Component.literal(locked ? "§c[079] Door LOCKED" : "§a[079] Door UNLOCKED"), true);
                            success = true;
                        }
                        case TESLA_TRIGGER -> {
                            success = cbe.triggerTesla(devicePos, player);
                            if (success) {
                                data.addExp(15.0f);
                                player.displayClientMessage(Component.literal("§e[079] Tesla overcharged!"), true);
                            }
                        }
                        case ELEVATOR_CALL -> {
                            success = true;
                        }
                    }
                }
            } else {
                player.displayClientMessage(Component.literal("§c[079] Insufficient Auxiliary Power! (Need " + (int)action.apCost + " AP)"), true);
            }
        });
        ctx.setPacketHandled(true);
    }
}
