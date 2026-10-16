package dev.slc.round;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative SLC stamina. Only actual sprinting drains it; clients receive the value in round sync. */
public final class StaminaManager {
    public static final float MAX_STAMINA = 100.0F;
    private static final float SPRINT_DRAIN_PER_TICK = 0.16F;
    private static final float RECOVERY_PER_TICK = 0.11F;
    private static final Map<UUID, Float> STAMINA = new HashMap<>();

    private StaminaManager() {}

    public static void reset() { STAMINA.clear(); }

    public static void refill(ServerPlayer player) {
        if (player != null) STAMINA.put(player.getUUID(), MAX_STAMINA);
    }

    public static float get(ServerPlayer player) {
        return player == null ? MAX_STAMINA : STAMINA.getOrDefault(player.getUUID(), MAX_STAMINA);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                    || RoundEngine.getPlayerRole(player.getUUID()) == dev.slc.role.Role.SCP_079) continue;
            float value = get(player);
            if (player.isSprinting() && player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() > 0.0008D) {
                value = Math.max(0.0F, value - SPRINT_DRAIN_PER_TICK);
                if (value <= 0.0F) player.setSprinting(false);
            } else {
                value = Math.min(MAX_STAMINA, value + RECOVERY_PER_TICK);
            }
            STAMINA.put(player.getUUID(), value);
        }
    }
}
