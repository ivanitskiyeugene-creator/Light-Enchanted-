package dev.slc.admin;

import net.minecraft.server.level.ServerPlayer;

/** Central, server-side authority check for every SLC administration surface. */
public final class AdminPermission {
    public static final int REQUIRED_OP_LEVEL = 4;

    private AdminPermission() {}

    public static boolean isAdmin(ServerPlayer player) {
        return player != null && player.hasPermissions(REQUIRED_OP_LEVEL);
    }
}
