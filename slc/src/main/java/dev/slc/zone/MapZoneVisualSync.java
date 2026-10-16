package dev.slc.zone;

import dev.slc.network.ModNetwork;
import dev.slc.network.S2CMapZonesPacket;
import net.minecraft.server.level.ServerPlayer;

/** Sends only server-owned, read-only map editor geometry to the OP holding a tool. */
public final class MapZoneVisualSync {
    private MapZoneVisualSync() {}

    public static void send(ServerPlayer player) {
        if (player == null) return;
        ModNetwork.sendToPlayer(S2CMapZonesPacket.from(SpatialZoneManager.get(player.serverLevel())), player);
    }
}
