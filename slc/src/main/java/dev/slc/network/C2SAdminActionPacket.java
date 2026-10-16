package dev.slc.network;

import dev.slc.admin.AdminAction;
import dev.slc.admin.AdminPermission;
import dev.slc.admin.AdminStateManager;
import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.role.Role;
import dev.slc.role.Team;
import dev.slc.round.RespawnWaveManager;
import dev.slc.round.RoundEngine;
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
            if (admin == null) return;
            if (!AdminPermission.isAdmin(admin)) {
                status(admin, "Нужно право OP 4", false);
                return;
            }
            ServerPlayer target = msg.targetId == null ? admin : admin.server.getPlayerList().getPlayer(msg.targetId);
            execute(admin, target, msg);
        });
        context.setPacketHandled(true);
    }

    private static void execute(ServerPlayer admin, ServerPlayer target, C2SAdminActionPacket request) {
        switch (request.action) {
            case ROUND_START -> {
                RoundEngine.forceStartRound();
                status(admin, "Раунд запущен", true);
            }
            case ROUND_RESTART -> {
                RoundEngine.restartToLobby();
                status(admin, "Раунд перезапущен: лобби", true);
            }
            case ROUND_STOP -> {
                RoundEngine.endRound(null);
                status(admin, "Раунд остановлен", true);
            }
            case ROUND_END_DRAW -> {
                RoundEngine.endRound(null);
                status(admin, "Раунд завершён: ничья", true);
            }
            case ROUND_END_FOUNDATION -> {
                RoundEngine.endRound(Team.FOUNDATION_FORCES);
                status(admin, "Раунд завершён: Foundation", true);
            }
            case ROUND_END_CHAOS -> {
                RoundEngine.endRound(Team.CHAOS_INSURGENCY);
                status(admin, "Раунд завершён: Chaos", true);
            }
            case ROUND_END_SCP -> {
                RoundEngine.endRound(Team.SCP_OBJECTS);
                status(admin, "Раунд завершён: SCP", true);
            }
            case FORCE_MTF_WAVE -> {
                int spawned = RespawnWaveManager.forceWave(true);
                status(admin, spawned > 0 ? "Вызвана волна MTF: " + spawned : "Нет наблюдателей для волны MTF", spawned > 0);
            }
            case FORCE_CHAOS_WAVE -> {
                int spawned = RespawnWaveManager.forceWave(false);
                status(admin, spawned > 0 ? "Вызвана волна Chaos: " + spawned : "Нет наблюдателей для волны Chaos", spawned > 0);
            }
            case SET_ROLE -> setRole(admin, target, request.value);
            case TOGGLE_GOD -> {
                if (target == null) status(admin, "Игрок вышел с сервера", false);
                else status(admin, "Бессмертие: " + (AdminStateManager.toggleGodMode(target) ? "ВКЛ" : "ВЫКЛ"), true);
            }
            case TOGGLE_NOCLIP -> {
                if (target == null) status(admin, "Игрок вышел с сервера", false);
                else status(admin, "No-clip: " + (AdminStateManager.toggleNoClip(target) ? "ВКЛ" : "ВЫКЛ"), true);
            }
            case GIVE_ITEM -> status(admin, giveItem(target, request.value, request.number)
                    ? "Предмет выдан" : "Не удалось выдать предмет: проверь ID", giveItemPossible(target, request.value));
            case KICK -> kick(admin, target);
            case BAN -> ban(admin, target);
            case KIT_REQUEST -> sendKit(admin, request.value);
            case KIT_SAVE_HAND -> updateKitFromHand(admin, request.value, request.number, false);
            case KIT_CLEAR -> updateKitFromHand(admin, request.value, request.number, true);
            case GIVE_DUMMY_SPAWNER -> {
                giveDummySpawner(admin, request.value);
                status(admin, "Спавнер dummy выдан", true);
            }
            case GIVE_EXIT_ZONE_MARKER -> giveMapTool(admin, dev.slc.init.ModItems.EXIT_ZONE_MARKER.get(), "Маркер зоны выхода выдан");
            case GIVE_ELEVATOR_CABIN_MARKER -> giveMapTool(admin, dev.slc.init.ModItems.ELEVATOR_CABIN_MARKER.get(), "Маркер кабины лифта выдан");
            case GIVE_ROLE_SPAWN_POINT_MARKER -> giveRoleSpawnTool(admin, request.number, false);
            case GIVE_ROLE_SPAWN_ZONE_MARKER -> giveRoleSpawnTool(admin, request.number, true);
            case GIVE_079_MAP -> status(admin, giveItem(target == null ? admin : target, "zero_seven_nine:map_tablet", 1)
                    ? "Планшет 079 выдан" : "0-7-9 не найден или планшет недоступен", giveItemPossible(target == null ? admin : target, "zero_seven_nine:map_tablet"));
        }
    }

    private static void setRole(ServerPlayer admin, ServerPlayer target, String rawRole) {
        if (target == null) {
            status(admin, "Игрок вышел с сервера", false);
            return;
        }
        try {
            Role role = Role.valueOf(rawRole);
            RoundEngine.forceRoleAndSpawn(target, role);
            dev.slc.cassie.CassieVoiceEngine.announceForcedReinforcement(role);
            status(admin, target.getScoreboardName() + " → " + role.getDisplayName(), true);
        } catch (IllegalArgumentException ignored) {
            status(admin, "Неизвестная роль", false);
        }
    }

    private static boolean giveItemPossible(ServerPlayer target, String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return target != null && item != null && item != net.minecraft.world.item.Items.AIR;
    }

    private static boolean giveItem(ServerPlayer target, String rawId, int requestedCount) {
        if (!giveItemPossible(target, rawId)) return false;
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(rawId));
        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(requestedCount, item.getMaxStackSize())));
        if (!target.getInventory().add(stack)) target.drop(stack, false);
        target.inventoryMenu.broadcastChanges();
        return true;
    }

    private static void kick(ServerPlayer admin, ServerPlayer target) {
        if (target == null) status(admin, "Игрок вышел с сервера", false);
        else if (target == admin) status(admin, "Нельзя кикнуть себя", false);
        else {
            String name = target.getScoreboardName();
            target.connection.disconnect(Component.literal("Kicked by an SLC administrator"));
            status(admin, name + " кикнут", true);
        }
    }

    private static void giveDummySpawner(ServerPlayer admin, String raw) {
        String[] parts = raw.split(":", 2);
        ItemStack spawner = dev.slc.item.DummySpawnerItem.configured(parts[0], parts.length > 1 ? parts[1] : "PATROL");
        if (!admin.getInventory().add(spawner)) admin.drop(spawner, false);
        admin.inventoryMenu.broadcastChanges();
    }

    private static void giveRoleSpawnTool(ServerPlayer admin, int roleOrdinal, boolean zone) {
        if (roleOrdinal < 0 || roleOrdinal >= Role.values().length) {
            status(admin, "Некорректная роль", false);
            return;
        }
        Role role = Role.values()[roleOrdinal];
        giveMapTool(admin, (zone ? dev.slc.init.ModItems.spawnZoneMarker(role) : dev.slc.init.ModItems.spawnPointMarker(role)).get(),
                (zone ? "Маркер зоны спавна: " : "Маркер точки спавна: ") + role.getDisplayName());
    }

    private static void giveMapTool(ServerPlayer admin, Item item, String message) {
        ItemStack marker = new ItemStack(item);
        if (!admin.getInventory().add(marker)) admin.drop(marker, false);
        admin.inventoryMenu.broadcastChanges();
        status(admin, message, true);
    }

    private static void ban(ServerPlayer admin, ServerPlayer target) {
        if (target == null) {
            status(admin, "Игрок вышел с сервера", false);
        } else if (target == admin) {
            status(admin, "Нельзя забанить себя", false);
        } else {
            String name = target.getScoreboardName();
            admin.server.getPlayerList().getBans().add(new UserBanListEntry(
                    target.getGameProfile(), null, admin.getScoreboardName(), null, "Banned by an SLC administrator"));
            target.connection.disconnect(Component.literal("Banned by an SLC administrator"));
            status(admin, name + " забанен", true);
        }
    }

    private static void sendKit(ServerPlayer admin, String rawRole) {
        try {
            Role role = Role.valueOf(rawRole);
            SlcLoadoutSavedData.Loadout kit = SlcLoadoutSavedData.get(admin.serverLevel()).getLoadout(role);
            ModNetwork.sendToPlayer(new S2CAdminKitPacket(role, kit.inventory(), kit.bodyArmor()), admin);
        } catch (IllegalArgumentException ignored) {
            status(admin, "Неизвестная роль набора", false);
        }
    }

    private static void updateKitFromHand(ServerPlayer admin, String rawRole, int slot, boolean clear) {
        if (slot < 0 || slot > SlcLoadoutSavedData.INVENTORY_SLOTS) {
            status(admin, "Некорректная ячейка", false);
            return;
        }
        try {
            Role role = Role.valueOf(rawRole);
            SlcLoadoutSavedData data = SlcLoadoutSavedData.get(admin.serverLevel());
            data.setItem(role, slot, clear ? ItemStack.EMPTY : admin.getMainHandItem().copy());
            sendKit(admin, rawRole);
            status(admin, clear ? "Ячейка очищена" : "Ячейка набора сохранена", true);
        } catch (IllegalArgumentException ignored) {
            status(admin, "Неизвестная роль набора", false);
        }
    }

    private static void status(ServerPlayer player, String text, boolean success) {
        ModNetwork.sendToPlayer(new S2CAdminStatusPacket(text, success), player);
    }
}
