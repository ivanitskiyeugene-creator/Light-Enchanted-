package dev.slc.round;

import dev.slc.admin.AdminPermission;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the ordinary Minecraft inventory out of the round without destroying it on entry.
 * During a round the live inventory is the hidden technical backing for the SLC wheel; normal
 * players are deliberately cleared at the end, while OPs regain their saved pre-round inventory.
 */
public final class RoundInventoryManager {
    private static final Map<UUID, Snapshot> HIDDEN_INVENTORIES = new HashMap<>();

    private RoundInventoryManager() {}

    public static void hideForRound(ServerPlayer player) {
        if (player == null || HIDDEN_INVENTORIES.containsKey(player.getUUID())) return;
        HIDDEN_INVENTORIES.put(player.getUUID(), Snapshot.capture(player));
    }

    public static void finishRound(MinecraftServer server) {
        if (server == null || HIDDEN_INVENTORIES.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Snapshot original = HIDDEN_INVENTORIES.remove(player.getUUID());
            if (original == null) continue;
            player.getInventory().clearContent();
            player.getInventory().armor.clear();
            player.getInventory().offhand.clear();
            dev.slc.loadout.SlcArmorManager.setArmor(player, ItemStack.EMPTY);
            if (original != null && AdminPermission.isAdmin(player)) original.restore(player);
            player.inventoryMenu.broadcastChanges();
        }
    }

    public static void forgetAll() {
        HIDDEN_INVENTORIES.clear();
    }

    private record Snapshot(List<ItemStack> inventory, List<ItemStack> armor, List<ItemStack> offhand) {
        static Snapshot capture(ServerPlayer player) {
            List<ItemStack> inventory = new ArrayList<>();
            for (int i = 0; i < player.getInventory().items.size(); i++) inventory.add(player.getInventory().items.get(i).copy());
            List<ItemStack> armor = player.getInventory().armor.stream().map(ItemStack::copy).toList();
            List<ItemStack> offhand = player.getInventory().offhand.stream().map(ItemStack::copy).toList();
            return new Snapshot(inventory, armor, offhand);
        }

        void restore(ServerPlayer player) {
            for (int i = 0; i < Math.min(inventory.size(), player.getInventory().items.size()); i++) {
                player.getInventory().items.set(i, inventory.get(i).copy());
            }
            for (int i = 0; i < Math.min(armor.size(), player.getInventory().armor.size()); i++) {
                player.getInventory().armor.set(i, armor.get(i).copy());
            }
            for (int i = 0; i < Math.min(offhand.size(), player.getInventory().offhand.size()); i++) {
                player.getInventory().offhand.set(i, offhand.get(i).copy());
            }
        }
    }
}
