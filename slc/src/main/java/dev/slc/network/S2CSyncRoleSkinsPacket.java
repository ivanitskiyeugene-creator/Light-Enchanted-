package dev.slc.network;

import dev.slc.appearance.RoleSkinSavedData;
import dev.slc.client.ClientRoleSkinCache;
import dev.slc.role.Role;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/** World-defined human role skin URLs. The client only receives the vetted HTTPS mapping. */
public record S2CSyncRoleSkinsPacket(Map<Role, String> urls) {
    public static S2CSyncRoleSkinsPacket from(ServerPlayer player) {
        return new S2CSyncRoleSkinsPacket(RoleSkinSavedData.get(player.serverLevel()).copyUrls());
    }

    public static void encode(S2CSyncRoleSkinsPacket msg, FriendlyByteBuf buf) {
        int count = 0;
        for (Role role : Role.values()) if (role.isHuman()) count++;
        buf.writeVarInt(count);
        for (Role role : Role.values()) {
            if (!role.isHuman()) continue;
            buf.writeEnum(role);
            buf.writeUtf(msg.urls.getOrDefault(role, ""), 512);
        }
    }

    public static S2CSyncRoleSkinsPacket decode(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), Role.values().length);
        Map<Role, String> urls = new EnumMap<>(Role.class);
        for (int i = 0; i < count; i++) {
            Role role = buf.readEnum(Role.class);
            String url = buf.readUtf(512);
            if (role.isHuman()) urls.put(role, url);
        }
        return new S2CSyncRoleSkinsPacket(urls);
    }

    public static void handle(S2CSyncRoleSkinsPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientRoleSkinCache.replace(msg.urls));
        context.setPacketHandled(true);
    }
}
