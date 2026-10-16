package dev.slc.client;

import dev.slc.role.Role;
import dev.slc.round.RoundState;
import dev.slc.spectator.SpectatorTargetInfo;

import java.util.ArrayList;
import java.util.List;

public class ClientRoundCache {
    private static RoundState roundState = RoundState.WAITING_FOR_PLAYERS;
    private static int roundElapsedTicks = 0;
    private static float mtfInfluence = 0.5f;
    private static float chaosInfluence = 0.5f;
    private static final List<SpectatorTargetInfo> alivePlayers = new ArrayList<>();

    private static Role localRole = Role.SPECTATOR;
    private static int roleIntroTicks = 0;

    private static int spectateIndex = 0;
    private static boolean freecamMode = false;
    private static boolean muteDeadChat = false;
    private static boolean muteSpectatedPlayer = false;

    private static boolean localTied = false;
    private static final java.util.Set<java.util.UUID> tiedPlayers = new java.util.HashSet<>();

    // C.A.S.S.I.E. streaming text state
    private static String cassieText = "";
    private static int cassieColor = 0xFFFFFF;
    private static long cassieStartMillis = 0L;

    public static boolean isLocalTied() {
        return localTied;
    }

    public static void setLocalTied(boolean tied) {
        localTied = tied;
    }

    public static java.util.Set<java.util.UUID> getTiedPlayers() {
        return tiedPlayers;
    }

    public static void setTiedPlayers(java.util.Set<java.util.UUID> set) {
        tiedPlayers.clear();
        tiedPlayers.addAll(set);
    }

    public static boolean isPlayerTied(java.util.UUID uuid) {
        return tiedPlayers.contains(uuid);
    }

    public static void pushCassieMessage(String text, int colorRgb) {
        cassieText = text;
        cassieColor = colorRgb;
        cassieStartMillis = System.currentTimeMillis();
    }

    public static String getCassieText() {
        return cassieText;
    }

    public static int getCassieColor() {
        return cassieColor;
    }

    public static long getCassieStartMillis() {
        return cassieStartMillis;
    }

    public static boolean hasCassieMessage() {
        return !cassieText.isEmpty();
    }

    public static boolean isRoundActive() {
        return roundState == RoundState.IN_PROGRESS || roundState == RoundState.WARHEAD_DETONATING;
    }

    public static RoundState getRoundState() {
        return roundState;
    }

    public static void setRoundState(RoundState state) {
        roundState = state;
    }

    public static int getRoundElapsedTicks() {
        return roundElapsedTicks;
    }

    public static void setRoundElapsedTicks(int ticks) {
        roundElapsedTicks = ticks;
    }

    public static float getMtfInfluence() {
        return mtfInfluence;
    }

    public static float getChaosInfluence() {
        return chaosInfluence;
    }

    public static void setInfluence(float mtf, float chaos) {
        mtfInfluence = mtf;
        chaosInfluence = chaos;
    }

    public static Role getLocalRole() {
        return localRole;
    }

    public static void updateLocalRole(Role role) {
        if (role != null && role != localRole) {
            localRole = role;
            // Role card is shown after every real role transition: start, escape conversion,
            // forced admin assignment and every MTF/Chaos respawn wave.
            if (role != Role.SPECTATOR) {
                roleIntroTicks = 140; // 7 seconds intro popup
            }
        }
    }

    public static int getRoleIntroTicks() {
        return roleIntroTicks;
    }

    public static void tickRoleIntro() {
        if (roleIntroTicks > 0) {
            roleIntroTicks--;
        }
    }

    public static List<SpectatorTargetInfo> getAlivePlayers() {
        return alivePlayers;
    }

    public static void setAlivePlayers(List<SpectatorTargetInfo> players) {
        alivePlayers.clear();
        alivePlayers.addAll(players);
        if (spectateIndex >= alivePlayers.size()) {
            spectateIndex = Math.max(0, alivePlayers.size() - 1);
        }
    }

    public static int getSpectateIndex() {
        return spectateIndex;
    }

    public static void nextSpectateTarget() {
        if (!alivePlayers.isEmpty()) {
            spectateIndex = (spectateIndex + 1) % alivePlayers.size();
            freecamMode = false;
        }
    }

    public static void prevSpectateTarget() {
        if (!alivePlayers.isEmpty()) {
            spectateIndex = (spectateIndex - 1 + alivePlayers.size()) % alivePlayers.size();
            freecamMode = false;
        }
    }

    public static boolean isFreecamMode() {
        return freecamMode;
    }

    public static void toggleFreecam() {
        freecamMode = !freecamMode;
    }

    public static void setFreecamMode(boolean freecam) {
        freecamMode = freecam;
    }

    public static SpectatorTargetInfo getCurrentSpectateTarget() {
        if (alivePlayers.isEmpty() || spectateIndex >= alivePlayers.size()) return null;
        return alivePlayers.get(spectateIndex);
    }

    public static boolean isMuteDeadChat() {
        return muteDeadChat;
    }

    public static void toggleMuteDeadChat() {
        muteDeadChat = !muteDeadChat;
    }

    public static boolean isMuteSpectatedPlayer() {
        return muteSpectatedPlayer;
    }

    public static void toggleMuteSpectatedPlayer() {
        muteSpectatedPlayer = !muteSpectatedPlayer;
    }
}
