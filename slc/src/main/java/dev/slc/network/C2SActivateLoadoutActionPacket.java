package dev.slc.network;

import dev.slc.loadout.QuickAction;
import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.role.Role;
import dev.slc.round.RoundEngine;
import dev.slc.round.RoundState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-authorized canonical key action. A client cannot select an arbitrary source stack. */
public record C2SActivateLoadoutActionPacket(QuickAction action) {
    public static void encode(C2SActivateLoadoutActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
    }

    public static C2SActivateLoadoutActionPacket decode(FriendlyByteBuf buf) {
        return new C2SActivateLoadoutActionPacket(buf.readEnum(QuickAction.class));
    }

    public static void handle(C2SActivateLoadoutActionPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || msg.action == null
                    || (RoundEngine.getState() != RoundState.IN_PROGRESS && RoundEngine.getState() != RoundState.WARHEAD_DETONATING)
                    || player.isSpectator()) return;
            Role role = RoundEngine.getPlayerRole(player.getUUID());
            if (role == Role.SPECTATOR) return;

            ItemStack configured = SlcLoadoutSavedData.get(player.serverLevel()).getLoadout(role).quickBinding(msg.action);
            if (configured.isEmpty()) return;
            int source = findConfiguredStack(player, configured);
            if (source < 0) return;

            int selected = player.getInventory().selected;
            if (selected < 0 || selected >= 9) selected = 0;
            if (source != selected) {
                ItemStack active = player.getInventory().getItem(selected);
                player.getInventory().setItem(selected, player.getInventory().getItem(source));
                player.getInventory().setItem(source, active);
            }
            player.getInventory().selected = selected;
            player.inventoryMenu.broadcastChanges();
            ModNetwork.sendToPlayer(new S2CActivateLoadoutActionPacket(msg.action, msg.action.useImmediately()), player);
        });
        context.setPacketHandled(true);
    }

    private static int findConfiguredStack(ServerPlayer player, ItemStack configured) {
        for (int slot = 0; slot < SlcLoadoutSavedData.AMMO_SLOT; slot++) {
            ItemStack actual = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameTags(actual, configured)) return slot;
        }
        return -1;
    }
}
