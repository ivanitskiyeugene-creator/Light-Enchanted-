package dev.slc.client;

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

    private static int spectateIndex = 0;
    private static boolean freecamMode = false;
    private static boolean muteDeadChat = false;
    private static boolean muteSpectatedPlayer = false;

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
