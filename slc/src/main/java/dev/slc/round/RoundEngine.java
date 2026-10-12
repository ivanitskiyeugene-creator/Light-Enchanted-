package dev.slc.round;

import dev.slc.cassie.CassieVoiceEngine;
import dev.slc.decontam.DecontaminationManager;
import dev.slc.role.Role;
import dev.slc.role.Team;
import dev.slc.warhead.AlphaWarheadManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.*;

public class RoundEngine {
    private static MinecraftServer server = null;
    private static RoundState state = RoundState.WAITING_FOR_PLAYERS;

    private static int countdownTicks = 0;
    private static int roundElapsedTicks = 0;
    private static final Map<UUID, Role> playerRoles = new HashMap<>();

    public static void init(MinecraftServer s) {
        server = s;
        state = RoundState.WAITING_FOR_PLAYERS;
        countdownTicks = 0;
        roundElapsedTicks = 0;
        playerRoles.clear();
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

    public static void startRoundCountdown(int seconds) {
        state = RoundState.LOBBY_COUNTDOWN;
        countdownTicks = seconds * 20;
        CassieVoiceEngine.broadcast("FACILITY BREACH SEQUENCE INITIALIZED IN " + seconds + " SECONDS", 0x3498DB);
    }

    public static void forceStartRound() {
        state = RoundState.IN_PROGRESS;
        roundElapsedTicks = 0;
        assignInitialRoles();
        DecontaminationManager.startTimer();
        CassieVoiceEngine.broadcast("FACILITY OPERATING NORMALLY . ALL STATIONS AT FULL BREACH READINESS", 0x2ECC71);
    }

    public static void endRound(Team winningTeam) {
        state = RoundState.ROUND_ENDED;
        String winMsg = winningTeam != null ?
                "РАУНД ОКОНЧЕН! ПОБЕДА: " + winningTeam.getDisplayName().toUpperCase() :
                "РАУНД ОКОНЧЕН! НИЧЬЯ";

        CassieVoiceEngine.broadcast(winMsg, winningTeam != null ? winningTeam.getColorRgb() : 0xFFFFFF);

        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.displayClientMessage(Component.literal("§6§l[SLC] §e" + winMsg), true);
            }
        }
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
                checkWinConditions();
            }
            case WARHEAD_DETONATING -> {
                roundElapsedTicks++;
                AlphaWarheadManager.tick();
                checkWinConditions();
            }
            default -> {}
        }
    }

    private static void assignInitialRoles() {
        if (server == null) return;
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (players.isEmpty()) return;

        Collections.shuffle(players);
        int total = players.size();

        // 1. Assign SCPs (e.g. 1 per 4-5 players)
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

        // 3. Assign Scientists (~30% of remaining)
        int remaining = total - assigned;
        int sciCount = Math.max(1, remaining / 3);
        for (int i = 0; i < sciCount && assigned < total; i++) {
            setPlayerRole(players.get(assigned++), Role.SCIENTIST);
        }

        // 4. Remaining players are Class-D
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

        String msg = "§eВы играете за: §l" + role.getDisplayName() + " §7(" + role.getTeam().getDisplayName() + ")";
        player.sendSystemMessage(Component.literal(msg));
    }

    private static void checkWinConditions() {
        if (server == null || roundElapsedTicks < 200) return; // Warmup grace period

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

        int humanForces = aliveClassD + aliveScientists + aliveFoundation + aliveChaos;

        // SCP Win: All humans neutralized
        if (aliveScps > 0 && humanForces == 0) {
            endRound(Team.SCP_OBJECTS);
        }
        // Foundation Win: All SCPs and Chaos eliminated, Scientists saved
        else if (aliveScps == 0 && aliveChaos == 0 && aliveClassD == 0 && (aliveFoundation > 0 || aliveScientists > 0)) {
            endRound(Team.FOUNDATION_FORCES);
        }
        // Chaos Win: All SCPs and Foundation eliminated, Class-D saved
        else if (aliveScps == 0 && aliveFoundation == 0 && aliveScientists == 0 && (aliveChaos > 0 || aliveClassD > 0)) {
            endRound(Team.CHAOS_INSURGENCY);
        }
    }
}
