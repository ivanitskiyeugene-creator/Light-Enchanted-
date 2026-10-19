package dev.slc.network;

import dev.slc.loadout.QuickAction;
import dev.slc.round.RoundEngine;
import dev.slc.round.RoundState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Server-authorized global SCP:SL controls. These actions never read a role-kit shortcut: every
 * living player gets Ctrl/card, G/grenade and X/medkit while the round is active.
 */
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
                    || player.isSpectator() || RoundEngine.getPlayerRole(player.getUUID()).isScp()
                    || msg.action == QuickAction.INTERACT) return;
            int source = findCanonicalItem(player, msg.action);
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

    private static int findCanonicalItem(ServerPlayer player, QuickAction action) {
        for (int slot = 0; slot < dev.slc.loadout.SlcLoadoutSavedData.AMMO_SLOT; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && matchesCanonicalAction(stack, action)) return slot;
        }
        return -1;
    }

    private static boolean matchesCanonicalAction(ItemStack stack, QuickAction action) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return switch (action) {
            case CARD -> path.contains("keycard") || path.contains("key_card") || path.contains("access_card");
            case GRENADE -> path.contains("grenade");
            case MEDKIT -> path.contains("medkit") || path.contains("med_kit") || path.contains("first_aid") || path.contains("medical_kit");
            case INTERACT -> false;
        };
    }
}
