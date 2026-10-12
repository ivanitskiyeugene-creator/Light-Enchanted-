package dev.slc.cassie;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class CassieVoiceEngine {
    private static MinecraftServer server = null;

    public static void setServer(MinecraftServer s) {
        server = s;
    }

    public static void broadcast(String message, int colorHex) {
        if (message == null || message.trim().isEmpty()) return;

        Component formatted = Component.literal("§8[§c§lC.A.S.S.I.E§8] §f" + message.toUpperCase());

        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(formatted);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.VOICE, 1.0f, 0.8f);
            }
        }
    }
}
