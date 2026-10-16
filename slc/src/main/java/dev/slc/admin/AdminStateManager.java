package dev.slc.admin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime-only administration powers. They deliberately are not persisted: a server restart or
 * de-op always clears god mode/no-clip instead of leaving an invisible privilege behind.
 */
public final class AdminStateManager {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private AdminStateManager() {}

    public static boolean isGodMode(ServerPlayer player) {
        return player != null && STATES.getOrDefault(player.getUUID(), State.NONE).godMode;
    }

    public static boolean isNoClip(ServerPlayer player) {
        return player != null && STATES.getOrDefault(player.getUUID(), State.NONE).noClip;
    }

    public static boolean toggleGodMode(ServerPlayer player) {
        State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        state.godMode = !state.godMode;
        prune(player.getUUID(), state);
        return state.godMode;
    }

    public static boolean toggleNoClip(ServerPlayer player) {
        State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        if (!state.noClip) {
            state.savedMayFly = player.getAbilities().mayfly;
            state.savedFlying = player.getAbilities().flying;
            state.noClip = true;
            applyNoClip(player, state);
        } else {
            state.noClip = false;
            restoreMovement(player, state);
            prune(player.getUUID(), state);
        }
        return state.noClip;
    }

    /** Called every server tick, including after a dimension change. */
    public static void tick(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null) return;
        if (!AdminPermission.isAdmin(player)) {
            clear(player);
            return;
        }
        if (state.noClip) {
            applyNoClip(player, state);
        }
    }

    public static void clear(ServerPlayer player) {
        State state = STATES.remove(player.getUUID());
        if (state != null && state.noClip) {
            restoreMovement(player, state);
        }
    }

    public static void clearAll() {
        STATES.clear();
    }

    private static void applyNoClip(ServerPlayer player, State state) {
        player.noPhysics = true;
        player.setNoGravity(true);
        player.fallDistance = 0.0F;
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        player.onUpdateAbilities();
    }

    private static void restoreMovement(ServerPlayer player, State state) {
        player.noPhysics = false;
        player.setNoGravity(false);
        player.fallDistance = 0.0F;
        Abilities abilities = player.getAbilities();
        abilities.mayfly = state.savedMayFly;
        abilities.flying = state.savedFlying && state.savedMayFly;
        player.onUpdateAbilities();
    }

    private static void prune(UUID id, State state) {
        if (!state.godMode && !state.noClip) STATES.remove(id);
    }

    private static final class State {
        private static final State NONE = new State();
        private boolean godMode;
        private boolean noClip;
        private boolean savedMayFly;
        private boolean savedFlying;
    }
}
