package dev.slc.round;

import dev.slc.cassie.CassieVoiceEngine;
import dev.slc.decontam.DecontaminationManager;
import dev.slc.role.Role;
import dev.slc.role.Team;
import dev.slc.warhead.AlphaWarheadManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.*;

/**
 * Core round lifecycle manager implementing canonical 1:1 SCP:SL (14.2+)
 * victory conditions, team tracking, and civilian escape calculations.
 */
public class RoundEngine {
    private static MinecraftServer server = null;
    private static RoundState state = RoundState.WAITING_FOR_PLAYERS;

    private static int countdownTicks = 0;
    private static int roundElapsedTicks = 0;
    private static final Map<UUID, Role> playerRoles = new HashMap<>();

    // Escape counters strictly following SL 14.2 canon (only untied civilians score points)
    private static int freeScientistEscapes = 0;
    private static int freeClassDEscapes = 0;
    private static int tiedScientistEscapes = 0;
    private static int tiedClassDEscapes = 0;

    public static void init(MinecraftServer s) {
        server = s;
        state = RoundState.WAITING_FOR_PLAYERS;
        countdownTicks = 0;
        roundElapsedTicks = 0;
        playerRoles.clear();
        freeScientistEscapes = 0;
        freeClassDEscapes = 0;
        tiedScientistEscapes = 0;
        tiedClassDEscapes = 0;
        DecontaminationManager.reset();
        AlphaWarheadManager.reset();
    }

    public static void cleanup() {
        server = null;
        playerRoles.clear();
    }

    public static RoundState getState() {
        return state;
    }

    public static int getRoundElapsedTicks() {
        return roundElapsedTicks;
    }

    public static Role getPlayerRole(UUID uuid) {
        return playerRoles.getOrDefault(uuid, Role.SPECTATOR);
    }

    public static void setPlayerRole(ServerPlayer player, Role role) {
        if (player == null || role == null) return;
        playerRoles.put(player.getUUID(), role);
        applyRoleToPlayer(player, role);
    }

    public static int getFreeScientistEscapes() {
        return freeScientistEscapes;
    }

    public static int getFreeClassDEscapes() {
        return freeClassDEscapes;
    }

    public static int getTiedScientistEscapes() {
        return tiedScientistEscapes;
    }

    public static int getTiedClassDEscapes() {
        return tiedClassDEscapes;
    }

    /**
     * Triggered when a player enters the surface escape zone.
     * Tied civilians change team and grant tickets, but do NOT count as civilian points for their original team.
     */
    public static void handlePlayerEscape(ServerPlayer player, boolean isTied) {
        if (player == null) return;
        Role currentRole = getPlayerRole(player.getUUID());

        if (currentRole == Role.CLASS_D) {
            if (isTied) {
                tiedClassDEscapes++;
                setPlayerRole(player, Role.MTF_PRIVATE);
            } else {
                freeClassDEscapes++;
                setPlayerRole(player, Role.CHAOS_CONSCRIPT);
            }
        } else if (currentRole == Role.SCIENTIST) {
            if (isTied) {
                tiedScientistEscapes++;
                setPlayerRole(player, Role.CHAOS_CONSCRIPT);
            } else {
                freeScientistEscapes++;
                setPlayerRole(player, Role.MTF_SPECIALIST);
            }
        }
    }

    public static void startRoundCountdown(int seconds) {
        state = RoundState.LOBBY_COUNTDOWN;
        countdownTicks = seconds * 20;
        CassieVoiceEngine.broadcast("FACILITY BREACH SEQUENCE INITIALIZED IN " + seconds + " SECONDS", 0x3498DB);
    }

    public static void forceStartRound() {
        state = RoundState.IN_PROGRESS;
        roundElapsedTicks = 0;
        freeScientistEscapes = 0;
        freeClassDEscapes = 0;
        tiedScientistEscapes = 0;
        tiedClassDEscapes = 0;
        assignInitialRoles();
        DecontaminationManager.startTimer();
        CassieVoiceEngine.broadcast("FACILITY OPERATING NORMALLY . ALL STATIONS AT FULL BREACH READINESS", 0x2ECC71);
    }

    public static void endRound(Team winningTeam) {
        state = RoundState.ROUND_ENDED;
        String winMsg = winningTeam != null ?
                "РАУНД ОКОНЧЕН! ПОБЕДА: " + winningTeam.getDisplayName().toUpperCase() :
                "РАУНД ОКОНЧЕН! НИЧЬЯ (STALEMATE)";

        CassieVoiceEngine.broadcast(winMsg, winningTeam != null ? winningTeam.getColorRgb() : 0xFFFFFF);
    }

    public static void tick() {
        if (server == null) return;

        switch (state) {
            case LOBBY_COUNTDOWN -> {
                if (countdownTicks > 0) {
                    countdownTicks--;
                    if (countdownTicks % 20 == 0) {
                        int sec = countdownTicks / 20;
                        if (sec <= 5 && sec > 0) {
                            CassieVoiceEngine.broadcast("T MINUS " + sec + " SECONDS", 0xF39C12);
                        }
                    }
                    if (countdownTicks == 0) {
                        forceStartRound();
                    }
                }
            }
            case IN_PROGRESS -> {
                roundElapsedTicks++;
                DecontaminationManager.tick(roundElapsedTicks);
                AlphaWarheadManager.tick();
                if (roundElapsedTicks % 10 == 0) {
                    syncSpectatorData();
                }
                checkWinConditions();
            }
            case WARHEAD_DETONATING -> {
                roundElapsedTicks++;
                AlphaWarheadManager.tick();
                if (roundElapsedTicks % 10 == 0) {
                    syncSpectatorData();
                }
                checkWinConditions();
            }
            default -> {}
        }
    }

    private static void syncSpectatorData() {
        if (server == null) return;
        List<dev.slc.spectator.SpectatorTargetInfo> targets = new ArrayList<>();
        int aliveMtf = 0;
        int aliveChaos = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isAlive() && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                Role role = getPlayerRole(player.getUUID());
                if (role != Role.SPECTATOR) {
                    float hp = player.getHealth();
                    float maxHp = (float) role.getMaxHealth();
                    float maxHs = (float) role.getMaxHumeShield();
                    float hs = maxHs > 0 ? maxHs * (hp / maxHp) : 0f;

                    targets.add(new dev.slc.spectator.SpectatorTargetInfo(
                            player.getUUID(),
                            player.getScoreboardName(),
                            role,
                            hp,
                            maxHp,
                            hs,
                            maxHs
                    ));

                    if (role.getTeam() == Team.FOUNDATION_FORCES || role.getTeam() == Team.SCIENTISTS) {
                        aliveMtf++;
                    } else if (role.getTeam() == Team.CHAOS_INSURGENCY || role.getTeam() == Team.CLASS_D_PERSONNEL) {
                        aliveChaos++;
                    }
                }
            }
        }

        float mtfTokens = Math.max(0.1f, aliveMtf + freeScientistEscapes * 2.0f);
        float chaosTokens = Math.max(0.1f, aliveChaos + freeClassDEscapes * 2.0f);
        float total = mtfTokens + chaosTokens;
        float mtfRatio = mtfTokens / total;
        float chaosRatio = chaosTokens / total;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Role r = getPlayerRole(player.getUUID());
            dev.slc.network.ModNetwork.sendToPlayer(new dev.slc.network.S2CSyncRoundStatePacket(state, roundElapsedTicks, mtfRatio, chaosRatio, r), player);
        }
        dev.slc.network.ModNetwork.sendToAll(new dev.slc.network.S2CSyncSpectatorDataPacket(targets));
    }

    private static void assignInitialRoles() {
        if (server == null) return;
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (players.isEmpty()) return;

        Collections.shuffle(players);
        int total = players.size();

        // 1. Assign SCPs
        int scpCount = Math.max(1, total / 5);
        int assigned = 0;

        Role[] scpPool = {Role.SCP_173, Role.SCP_079, Role.SCP_939, Role.SCP_049, Role.SCP_096};
        for (int i = 0; i < scpCount && assigned < total; i++) {
            setPlayerRole(players.get(assigned++), scpPool[i % scpPool.length]);
        }

        // 2. Assign Guards
        int guardCount = Math.max(1, (total - assigned) / 4);
        for (int i = 0; i < guardCount && assigned < total; i++) {
            setPlayerRole(players.get(assigned++), Role.FACILITY_GUARD);
        }

        // 3. Assign Scientists
        int remaining = total - assigned;
        int sciCount = Math.max(1, remaining / 3);
        for (int i = 0; i < sciCount && assigned < total; i++) {
            setPlayerRole(players.get(assigned++), Role.SCIENTIST);
        }

        // 4. Remaining are Class-D
        while (assigned < total) {
            setPlayerRole(players.get(assigned++), Role.CLASS_D);
        }
    }

    private static void applyRoleToPlayer(ServerPlayer player, Role role) {
        if (player == null || role == null) return;

        player.setHealth(role.getMaxHealth());
        if (role == Role.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        } else {
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    private static void checkWinConditions() {
        if (server == null || roundElapsedTicks < 200) return;

        int aliveClassD = 0;
        int aliveScientists = 0;
        int aliveFoundation = 0;
        int aliveChaos = 0;
        int aliveScps = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isAlive() && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                Role r = getPlayerRole(player.getUUID());
                switch (r.getTeam()) {
                    case CLASS_D_PERSONNEL -> aliveClassD++;
                    case SCIENTISTS -> aliveScientists++;
                    case FOUNDATION_FORCES -> aliveFoundation++;
                    case CHAOS_INSURGENCY -> aliveChaos++;
                    case SCP_OBJECTS -> aliveScps++;
                    default -> {}
                }
            }
        }

        int aliveFoundationFaction = aliveFoundation + aliveScientists;
        int aliveChaosFaction = aliveChaos + aliveClassD;
        int totalAliveHumans = aliveFoundationFaction + aliveChaosFaction;

        boolean roundShouldEnd = false;

        if (totalAliveHumans == 0) {
            roundShouldEnd = true;
        } else if (aliveFoundationFaction > 0 && aliveChaosFaction == 0 && aliveScps == 0) {
            roundShouldEnd = true;
        } else if (aliveChaosFaction > 0 && aliveFoundationFaction == 0 && aliveScps == 0) {
            roundShouldEnd = true;
        } else if (aliveFoundationFaction == 0 && aliveChaosFaction == 0) {
            roundShouldEnd = true;
        }

        if (!roundShouldEnd) return;

        if (aliveScps > (freeScientistEscapes + freeClassDEscapes) && aliveScps > 0) {
            endRound(Team.SCP_OBJECTS);
        } else if (freeScientistEscapes > freeClassDEscapes) {
            endRound(Team.FOUNDATION_FORCES);
        } else if (freeClassDEscapes > freeScientistEscapes) {
            endRound(Team.CHAOS_INSURGENCY);
        } else {
            endRound(null);
        }
    }
}
