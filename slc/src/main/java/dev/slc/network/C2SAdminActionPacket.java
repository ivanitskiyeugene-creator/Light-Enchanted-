package dev.slc.network;

import dev.slc.admin.AdminAction;
import dev.slc.admin.AdminPermission;
import dev.slc.admin.AdminStateManager;
import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.role.Role;
import dev.slc.role.Team;
import dev.slc.round.RespawnWaveManager;
import dev.slc.round.RoundEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;
import java.util.function.Supplier;

/** Client requests only; all authority, item lookup and target lookup happen server-side. */
public record C2SAdminActionPacket(AdminAction action, UUID targetId, String value, int number) {
    public static void encode(C2SAdminActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeBoolean(msg.targetId != null);
        if (msg.targetId != null) buf.writeUUID(msg.targetId);
        buf.writeUtf(msg.value == null ? "" : msg.value, 256);
        buf.writeVarInt(msg.number);
    }

    public static C2SAdminActionPacket decode(FriendlyByteBuf buf) {
        AdminAction action = buf.readEnum(AdminAction.class);
        UUID target = buf.readBoolean() ? buf.readUUID() : null;
        return new C2SAdminActionPacket(action, target, buf.readUtf(256), buf.readVarInt());
    }

    public static void handle(C2SAdminActionPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer admin = context.getSender();
            if (!AdminPermission.isAdmin(admin)) return;
            ServerPlayer target = msg.targetId == null ? admin : admin.server.getPlayerList().getPlayer(msg.targetId);
            execute(admin, target, msg);
        });
        context.setPacketHandled(true);
    }

    private static void execute(ServerPlayer admin, ServerPlayer target, C2SAdminActionPacket request) {
        switch (request.action) {
            case ROUND_START -> RoundEngine.forceStartRound();
            case ROUND_RESTART -> RoundEngine.restartToLobby();
            case ROUND_END_DRAW -> RoundEngine.endRound(null);
            case ROUND_END_FOUNDATION -> RoundEngine.endRound(Team.FOUNDATION_FORCES);
            case ROUND_END_CHAOS -> RoundEngine.endRound(Team.CHAOS_INSURGENCY);
            case ROUND_END_SCP -> RoundEngine.endRound(Team.SCP_OBJECTS);
            case FORCE_MTF_WAVE -> RespawnWaveManager.forceWave(true);
            case FORCE_CHAOS_WAVE -> RespawnWaveManager.forceWave(false);
            case SET_ROLE -> {
                if (target == null) return;
                try {
                    RoundEngine.forceRoleAndSpawn(target, Role.valueOf(request.value));
                } catch (IllegalArgumentException ignored) { }
            }
            case TOGGLE_GOD -> { if (target != null) AdminStateManager.toggleGodMode(target); }
            case TOGGLE_NOCLIP -> { if (target != null) AdminStateManager.toggleNoClip(target); }
            case GIVE_ITEM -> giveItem(target, request.value, request.number);
            case KICK -> {
                if (target != null && target != admin) target.connection.disconnect(Component.literal("Kicked by an SLC administrator"));
            }
            case BAN -> ban(admin, target);
            case KIT_REQUEST -> sendKit(admin, request.value);
            case KIT_SAVE_HAND -> updateKitFromHand(admin, request.value, request.number, false);
            case KIT_CLEAR -> updateKitFromHand(admin, request.value, request.number, true);
            case GIVE_DUMMY_SPAWNER -> giveDummySpawner(admin, request.value);
            case GIVE_ZONE_MARKER -> giveZoneMarker(admin, request.value, request.number);
            case GIVE_079_MAP -> giveItem(target == null ? admin : target, "zero_seven_nine:map_tablet", 1);
        }
    }

    private static void giveItem(ServerPlayer target, String rawId, int requestedCount) {
        if (target == null) return;
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) return;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return;
        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(requestedCount, item.getMaxStackSize())));
        if (!target.getInventory().add(stack)) target.drop(stack, false);
        target.inventoryMenu.broadcastChanges();
    }

    private static void giveDummySpawner(ServerPlayer admin, String raw) {
        String[] parts = raw.split(":", 2);
        ItemStack spawner = dev.slc.item.DummySpawnerItem.configured(parts[0], parts.length > 1 ? parts[1] : "PATROL");
        if (!admin.getInventory().add(spawner)) admin.drop(spawner, false);
        admin.inventoryMenu.broadcastChanges();
    }

    private static void giveZoneMarker(ServerPlayer admin, String mode, int roleOrdinal) {
        Role role = roleOrdinal >= 0 && roleOrdinal < Role.values().length ? Role.values()[roleOrdinal] : null;
        ItemStack marker = dev.slc.item.AdminZoneMarkerItem.configured(mode, role);
        if (!admin.getInventory().add(marker)) admin.drop(marker, false);
        admin.inventoryMenu.broadcastChanges();
    }

    private static void ban(ServerPlayer admin, ServerPlayer target) {
        if (target == null || target == admin) return;
        admin.server.getPlayerList().getBans().add(new UserBanListEntry(
                target.getGameProfile(), null, admin.getScoreboardName(), null, "Banned by an SLC administrator"));
        target.connection.disconnect(Component.literal("Banned by an SLC administrator"));
    }

    private static void sendKit(ServerPlayer admin, String rawRole) {
        try {
            Role role = Role.valueOf(rawRole);
            SlcLoadoutSavedData.Loadout kit = SlcLoadoutSavedData.get(admin.serverLevel()).getLoadout(role);
            ModNetwork.sendToPlayer(new S2CAdminKitPacket(role, kit.inventory(), kit.bodyArmor()), admin);
        } catch (IllegalArgumentException ignored) { }
    }

    private static void updateKitFromHand(ServerPlayer admin, String rawRole, int slot, boolean clear) {
        if (slot < 0 || slot > SlcLoadoutSavedData.INVENTORY_SLOTS) return;
        try {
            Role role = Role.valueOf(rawRole);
            SlcLoadoutSavedData data = SlcLoadoutSavedData.get(admin.serverLevel());
            data.setItem(role, slot, clear ? ItemStack.EMPTY : admin.getMainHandItem().copy());
            sendKit(admin, rawRole);
        } catch (IllegalArgumentException ignored) { }
    }
}
