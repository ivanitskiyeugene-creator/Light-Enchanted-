package dev.slc.loadout;

import com.google.common.collect.Multimap;
import dev.slc.network.ModNetwork;
import dev.slc.network.S2CSyncSlcArmorPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One invisible, functional SLC body-armor cell. The item stays out of Minecraft's four visual
 * armor slots; its own attribute modifiers are applied directly to the player.
 */
public final class SlcArmorManager {
    private static final String NBT_KEY = "SlcBodyArmor";
    private static final Map<UUID, List<ModifierRef>> APPLIED_MODIFIERS = new HashMap<>();

    private SlcArmorManager() {}

    public static ItemStack getArmor(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        return persistent.contains(NBT_KEY) ? ItemStack.of(persistent.getCompound(NBT_KEY)) : ItemStack.EMPTY;
    }

    public static void setArmor(ServerPlayer player, ItemStack armor) {
        removeModifiers(player);
        ItemStack stored = armor.copy();
        if (stored.isEmpty()) player.getPersistentData().remove(NBT_KEY);
        else player.getPersistentData().put(NBT_KEY, stored.save(new CompoundTag()));
        applyModifiers(player, stored);
        syncToClient(player);
    }

    public static void applyRoleLoadout(ServerPlayer player, SlcLoadoutSavedData.Loadout loadout) {
        player.getInventory().clearContent();
        player.getInventory().armor.clear();
        player.getInventory().offhand.clear();
        List<ItemStack> contents = loadout.inventory();
        for (int i = 0; i < contents.size(); i++) player.getInventory().setItem(i, contents.get(i));
        setArmor(player, loadout.bodyArmor());
        player.inventoryMenu.broadcastChanges();
    }

    public static void damageArmor(ServerPlayer player, float incomingDamage) {
        ItemStack armor = getArmor(player);
        if (armor.isEmpty() || !armor.isDamageableItem()) return;
        int wear = Math.max(1, Mth.ceil(incomingDamage / 4.0F));
        armor.hurtAndBreak(wear, player, ignored -> {});
        setArmor(player, armor.isEmpty() ? ItemStack.EMPTY : armor);
    }

    public static void syncToClient(ServerPlayer player) {
        ModNetwork.sendToPlayer(new S2CSyncSlcArmorPacket(getArmor(player)), player);
    }

    public static void forgetRuntime(ServerPlayer player) {
        removeModifiers(player);
    }

    private static void applyModifiers(ServerPlayer player, ItemStack armor) {
        if (armor.isEmpty()) return;
        EquipmentSlot sourceSlot = armor.getItem() instanceof ArmorItem armorItem
                ? armorItem.getEquipmentSlot() : EquipmentSlot.CHEST;
        Multimap<Attribute, AttributeModifier> modifiers = armor.getAttributeModifiers(sourceSlot);
        List<ModifierRef> applied = new ArrayList<>();
        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            AttributeInstance instance = player.getAttribute(entry.getKey());
            if (instance == null) continue;
            AttributeModifier modifier = entry.getValue();
            instance.removeModifier(modifier.getId());
            instance.addTransientModifier(modifier);
            applied.add(new ModifierRef(entry.getKey(), modifier.getId()));
        }
        if (!applied.isEmpty()) APPLIED_MODIFIERS.put(player.getUUID(), applied);
    }

    private static void removeModifiers(ServerPlayer player) {
        List<ModifierRef> refs = APPLIED_MODIFIERS.remove(player.getUUID());
        if (refs == null) return;
        for (ModifierRef ref : refs) {
            AttributeInstance instance = player.getAttribute(ref.attribute());
            if (instance != null) instance.removeModifier(ref.id());
        }
    }

    private record ModifierRef(Attribute attribute, UUID id) {}
}
