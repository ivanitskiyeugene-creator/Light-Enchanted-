package dev.slc.warhead;

import dev.slc.cassie.CassieVoiceEngine;

public class AlphaWarheadManager {
    public static final int DETONATION_TIME_SECONDS = 90; // 90 Seconds countdown
    private static boolean detonating = false;
    private static int countdownTicks = 0;

    public static void reset() {
        detonating = false;
        countdownTicks = 0;
    }

    public static void startDetonation() {
        if (detonating) return;
        detonating = true;
        countdownTicks = DETONATION_TIME_SECONDS * 20;
        CassieVoiceEngine.broadcast("ALPHA WARHEAD EMERGENCY DETONATION SEQUENCE ENGAGED . T MINUS 90 SECONDS", 0xE74C3C);
    }

    public static void cancelDetonation() {
        if (!detonating) return;
        detonating = false;
        countdownTicks = 0;
        CassieVoiceEngine.broadcast("ALPHA WARHEAD DETONATION CANCELLED . RESUMING STANDARD PROTOCOLS", 0x2ECC71);
    }

    public static boolean isDetonating() {
        return detonating;
    }

    public static void tick() {
        if (!detonating || countdownTicks <= 0) return;

        countdownTicks--;
        if (countdownTicks % 20 == 0) {
            int sec = countdownTicks / 20;
            if (sec == 60 || sec == 30 || sec == 20 || sec == 10 || (sec <= 5 && sec > 0)) {
                CassieVoiceEngine.broadcast("ALPHA WARHEAD DETONATION IN T MINUS " + sec + " SECONDS", 0xC0392B);
            } else if (sec == 0) {
                detonating = false;
                CassieVoiceEngine.broadcast("ALPHA WARHEAD DETONATED . FACILITY SECTOR STERILIZATION COMPLETE", 0x922B21);
            }
        }
    }
}
