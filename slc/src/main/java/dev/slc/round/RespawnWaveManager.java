package dev.slc.round;

import dev.slc.cassie.CassieVoiceEngine;
import dev.slc.config.SlcConfig;
import dev.slc.role.Role;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Canonical six-minute respawn waves plus the explicit OP-only free emergency wave. */
public class RespawnWaveManager {
    private static MinecraftServer server = null;
    private static int mtfTickets = 0;
    private static int chaosTickets = 0;
    private static int ticksSinceWave = 0;
    private static final int TICKET_COST = 1;

    public static void init(MinecraftServer s) {
        server = s;
        reset();
    }

    public static void reset() {
        mtfTickets = SlcConfig.COMMON.startingTickets.get();
        chaosTickets = SlcConfig.COMMON.startingTickets.get();
        ticksSinceWave = 0;
    }

    public static void cleanup() {
        server = null;
    }

    public static int getMtfTickets() { return mtfTickets; }
    public static int getChaosTickets() { return chaosTickets; }
    public static void addMtfTickets(int amount) { mtfTickets += amount; }
    public static void addChaosTickets(int amount) { chaosTickets += amount; }

    /** Called by the round engine whenever a civilian escapes (free or converted). */
    public static void onCivilianEscaped(boolean classD) {
        if (classD) addChaosTickets(SlcConfig.COMMON.chaosEscapeTicketBonus.get());
        else addMtfTickets(SlcConfig.COMMON.mtfEscapeTicketBonus.get());
    }

    public static void tick() {
        if (server == null) return;
        ticksSinceWave++;
        if (ticksSinceWave < SlcConfig.COMMON.respawnWaveIntervalSeconds.get() * 20) return;
        ticksSinceWave = 0;
        List<ServerPlayer> candidates = collectCandidates();
        if (!candidates.isEmpty()) spawnWave(candidates, mtfTickets >= chaosTickets, false);
    }

    /** Immediate, free reinforcement selected by the OP-4 administration panel. */
    public static int forceWave(boolean mtf) {
        if (server == null) return 0;
        List<ServerPlayer> candidates = collectCandidates();
        return candidates.isEmpty() ? 0 : spawnWave(candidates, mtf, true);
    }

    private static List<ServerPlayer> collectCandidates() {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Role role = RoundEngine.getPlayerRole(player.getUUID());
            // SCP-079 intentionally uses spectator mode for its camera session, but is still a
            // living SCP role and must never enter either automatic or forced reinforcement pools.
            if (role == Role.SCP_079) continue;
            boolean dead = !player.isAlive() || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR || role == Role.SPECTATOR;
            if (dead) candidates.add(player);
        }
        Collections.shuffle(candidates);
        return candidates;
    }

    private static int spawnWave(List<ServerPlayer> candidates, boolean mtf, boolean free) {
        int budget = free ? candidates.size() : (mtf ? mtfTickets : chaosTickets);
        if (budget <= 0) return 0;

        int spawned = 0;
        for (ServerPlayer player : candidates) {
            if (budget < TICKET_COST) break;
            RoundEngine.forceRoleAndSpawn(player, mtf ? Role.MTF_PRIVATE : Role.CHAOS_CONSCRIPT);
            if (!free) budget -= TICKET_COST;
            spawned++;
        }
        if (!free) {
            if (mtf) mtfTickets = budget;
            else chaosTickets = budget;
        }
        if (spawned > 0) {
            if (mtf) CassieVoiceEngine.announceMtfWave();
            else CassieVoiceEngine.announceChaosWave();
        }
        return spawned;
    }
}
