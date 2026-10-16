package dev.slc.network;

import dev.slc.client.ClientAdminKitCache;
import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.role.Role;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record S2CAdminKitPacket(Role role, List<ItemStack> inventory, ItemStack armor, List<ItemStack> quickBindings) {
    public static void encode(S2CAdminKitPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.role);
        for (int i = 0; i < SlcLoadoutSavedData.INVENTORY_SLOTS; i++) {
            buf.writeItem(i < msg.inventory.size() ? msg.inventory.get(i) : ItemStack.EMPTY);
        }
        buf.writeItem(msg.armor);
        for (int i = 0; i < dev.slc.loadout.QuickAction.values().length; i++) {
            buf.writeItem(i < msg.quickBindings.size() ? msg.quickBindings.get(i) : ItemStack.EMPTY);
        }
    }

    public static S2CAdminKitPacket decode(FriendlyByteBuf buf) {
        Role role = buf.readEnum(Role.class);
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < SlcLoadoutSavedData.INVENTORY_SLOTS; i++) contents.add(buf.readItem());
        ItemStack armor = buf.readItem();
        List<ItemStack> bindings = new ArrayList<>();
        for (int i = 0; i < dev.slc.loadout.QuickAction.values().length; i++) bindings.add(buf.readItem());
        return new S2CAdminKitPacket(role, contents, armor, bindings);
    }

    public static void handle(S2CAdminKitPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientAdminKitCache.set(msg.role, msg.inventory, msg.armor, msg.quickBindings));
        ctx.get().setPacketHandled(true);
    }
}
