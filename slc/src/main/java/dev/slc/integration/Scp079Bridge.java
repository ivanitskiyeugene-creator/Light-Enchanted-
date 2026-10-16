package dev.slc.integration;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/** Optional, runtime-only bridge: SLC stays loadable without the separate 0-7-9 mod. */
public final class Scp079Bridge {
    private static final String MOD_ID = "zero_seven_nine";
    private static Method startSlcSession;
    private static Method endSession;
    private static boolean lookedUp;

    private Scp079Bridge() {}

    public static boolean begin(ServerPlayer player) {
        if (player == null || !resolve()) return false;
        try {
            return Boolean.TRUE.equals(startSlcSession.invoke(null, player));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static void end(ServerPlayer player) {
        if (player == null || !resolve()) return;
        try {
            endSession.invoke(null, player);
        } catch (ReflectiveOperationException ignored) {
            // The optional integration must never make a round role transition fail.
        }
    }

    private static boolean resolve() {
        if (lookedUp) return startSlcSession != null && endSession != null;
        lookedUp = true;
        if (!ModList.get().isLoaded(MOD_ID)) return false;
        try {
            Class<?> manager = Class.forName("dev.zerosevennine.system.Scp079PlayerManager");
            startSlcSession = manager.getMethod("startSlcSession", ServerPlayer.class);
            endSession = manager.getMethod("endSession", ServerPlayer.class);
            return true;
        } catch (ReflectiveOperationException ignored) {
            startSlcSession = null;
            endSession = null;
            return false;
        }
    }
}
