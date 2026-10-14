package dev.slc.spectator;

import dev.slc.role.Role;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record SpectatorTargetInfo(
        UUID uuid,
        String name,
        Role role,
        float health,
        float maxHealth,
        float humeShield,
        float maxHumeShield
) {
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(uuid);
        buf.writeUtf(name);
        buf.writeEnum(role);
        buf.writeFloat(health);
        buf.writeFloat(maxHealth);
        buf.writeFloat(humeShield);
        buf.writeFloat(maxHumeShield);
    }

    public static SpectatorTargetInfo read(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        String name = buf.readUtf();
        Role role = buf.readEnum(Role.class);
        float hp = buf.readFloat();
        float maxHp = buf.readFloat();
        float hs = buf.readFloat();
        float maxHs = buf.readFloat();
        return new SpectatorTargetInfo(uuid, name, role, hp, maxHp, hs, maxHs);
    }
}
