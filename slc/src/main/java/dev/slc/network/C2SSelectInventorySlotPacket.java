package dev.slc.network;

import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.round.RoundEngine;
import dev.slc.round.RoundState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Selects an SLC wheel source cell by swapping it into the real active hand slot. */
public class C2SSelectInventorySlotPacket {
    private final int sourceCell;
    private final boolean drop;

    public C2SSelectInventorySlotPacket(int sourceCell, boolean drop) {
        this.sourceCell = sourceCell;
        this.drop = drop;
    }

    public static void encode(C2SSelectInventorySlotPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.sourceCell);
        buf.writeBoolean(msg.drop);
    }

    public static C2SSelectInventorySlotPacket decode(FriendlyByteBuf buf) {
        return new C2SSelectInventorySlotPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(C2SSelectInventorySlotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || (RoundEngine.getState() != RoundState.IN_PROGRESS && RoundEngine.getState() != RoundState.WARHEAD_DETONATING)
                    || msg.sourceCell < 0 || msg.sourceCell >= SlcLoadoutSavedData.AMMO_SLOT) return;
            int selected = player.getInventory().selected;
            if (selected < 0 || selected >= 9) selected = 0;
            if (msg.sourceCell != selected) {
                ItemStack active = player.getInventory().getItem(selected);
                ItemStack source = player.getInventory().getItem(msg.sourceCell);
                player.getInventory().setItem(selected, source);
                player.getInventory().setItem(msg.sourceCell, active);
            }
            player.getInventory().selected = selected;
            if (msg.drop) player.drop(false);
            player.inventoryMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }
}
