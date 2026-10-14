package dev.slc.cassie;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class CassieVoiceEngine {
    private static MinecraftServer server = null;

    public static void init(MinecraftServer s) {
        server = s;
    }

    public static void cleanup() {
        server = null;
    }

    public static void broadcast(String message, int colorRgb) {
        // Broadcast strictly via audio and C.A.S.S.I.E. HUD Overlay, no chat spam
    }
}
