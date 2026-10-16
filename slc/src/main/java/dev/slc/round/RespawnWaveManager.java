package dev.slc.round;

import dev.slc.cassie.CassieVoiceEngine;
import dev.slc.config.SlcConfig;
import dev.slc.role.Role;
import dev.slc.role.Team;
import dev.slc.zone.SpatialZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Canonical SCP:SL respawn wave controller:
 *  - A wave fires every respawnWaveIntervalSeconds (default 6 min) during IN_PROGRESS.
 *  - The faction with MORE tickets gets the wave; each respawned player costs 1 ticket.
 *  - MTF wave is announced by C.A.S.S.I.E. (teleport near Gate B markers);
 *    the Chaos wave also has a C.A.S.S.I.E. announcement (Gate A) per project canon.
 *  - Civilian escapes feed tickets (configurable bonuses).
 */
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

    public static int getMtfTickets() {
        return mtfTickets;
    }

    public static int getChaosTickets() {
        return chaosTickets;
    }

    public static void addMtfTickets(int amount) {
        mtfTickets += amount;
    }

    public static void addChaosTickets(int amount) {
        chaosTickets += amount;
    }

    /** Called by the round engine whenever a civilian escapes (free or converted). */
    public static void onCivilianEscaped(boolean classD) {
        if (classD) {
            addChaosTickets(SlcConfig.COMMON.chaosEscapeTicketBonus.get());
        } else {
            addMtfTickets(SlcConfig.COMMON.mtfEscapeTicketBonus.get());
        }
    }

    public static void tick() {
        if (server == null) return;
        ticksSinceWave++;
        int interval = SlcConfig.COMMON.respawnWaveIntervalSeconds.get() * 20;
        if (ticksSinceWave < interval) return;
        ticksSinceWave = 0;
        fireWave();
    }

    private static void fireWave() {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Role role = RoundEngine.getPlayerRole(player.getUUID());
            boolean dead = !player.isAlive()
                    || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                    || role == Role.SPECTATOR;
            if (dead) candidates.add(player);
        }
        if (candidates.isEmpty()) return;

        Collections.shuffle(candidates);

        if (mtfTickets >= chaosTickets) {
            spawnWave(candidates, true);
        } else {
            spawnWave(candidates, false);
        }
    }

    private static void spawnWave(List<ServerPlayer> candidates, boolean mtf) {
        int budget = mtf ? mtfTickets : chaosTickets;
        if (budget <= 0) {
            // No tickets: announce but nobody spawns (canon: squad arrives weakened)
            if (mtf) CassieVoiceEngine.announceMtfWave();
            else CassieVoiceEngine.announceChaosWave();
            return;
        }

        int spawned = 0;
        for (ServerPlayer player : candidates) {
            if (budget < TICKET_COST) break;

            Role newRole = mtf ? Role.MTF_PRIVATE : Role.CHAOS_CONSCRIPT;
            RoundEngine.setPlayerRole(player, newRole);
            teleportToRoleSpawn(player, newRole);

            budget -= TICKET_COST;
            spawned++;
        }

        if (mtf) {
            mtfTickets = budget;
        } else {
            chaosTickets = budget;
        }

        if (spawned > 0) {
            if (mtf) CassieVoiceEngine.announceMtfWave();
            else CassieVoiceEngine.announceChaosWave();
        }
    }

    private static void teleportToRoleSpawn(ServerPlayer player, Role role) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        SpatialZoneManager zoneMgr = SpatialZoneManager.get(serverLevel);
        List<BlockPos> spawns = zoneMgr.getRoleSpawns(role);
        if (spawns == null || spawns.isEmpty()) {
            // Fallback: world surface spawn
            BlockPos worldSpawn = serverLevel.getSharedSpawnPos();
            spawns = List.of(worldSpawn);
        }
        BlockPos target = spawns.get(player.getRandom().nextInt(spawns.size()));
        player.connection.teleport(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        player.setHealth((float) role.getMaxHealth());
    }
}
