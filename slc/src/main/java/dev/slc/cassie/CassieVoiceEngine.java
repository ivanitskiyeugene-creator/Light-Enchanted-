package dev.slc.cassie;

import dev.slc.init.ModSounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.sounds.SoundEvent;

/**
 * C.A.S.S.I.E. broadcast engine.
 * Visual feed: word-synchronized text overlay on the LEFT side, no chat spam.
 * Audio feed: canonical pre-rendered announcement files (slc:sounds/cassie_*.ogg).
 */
public class CassieVoiceEngine {
    // Canonical respawn wave announcements (project-approved wording)
    public static final String MTF_WAVE_TEXT =
            "Mobile Task Force Unit Epsilon-11, designated Nine-Tailed Fox, has entered the facility. "
            + "All remaining personnel are advised to proceed with standard evacuation protocols "
            + "until an MTF squad reaches your destination. Awaiting re-containment of SCP Subjects.";
    public static final String CHAOS_WAVE_TEXT =
            "Attention, all personnel. Detected Chaos Insurgency forces at Gate A. Lethal force authorized.";

    private static MinecraftServer server = null;

    public static void init(MinecraftServer s) {
        server = s;
    }

    public static void cleanup() {
        server = null;
    }

    public static void announceMtfWave() {
        playCassieSound(ModSounds.CASSIE_MTF_WAVE);
        broadcast(MTF_WAVE_TEXT, 0x3498DB);
    }

    public static void announceChaosWave() {
        playCassieSound(ModSounds.CASSIE_CHAOS_WAVE);
        broadcast(CHAOS_WAVE_TEXT, 0x2ECC71);
    }

    /** Text-only C.A.S.S.I.E. feed to every client HUD (never chat). */
    public static void broadcast(String message, int colorRgb) {
        if (server == null) return;
        dev.slc.network.ModNetwork.sendToAll(new dev.slc.network.S2CCassiePacket(message, colorRgb));
    }

    private static void playCassieSound(RegistryObject<SoundEvent> sound) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(sound.get(), SoundSource.MASTER, 1.0f, 1.0f);
        }
    }
}
