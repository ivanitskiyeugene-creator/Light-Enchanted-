package dev.slc.decontam;

import dev.slc.cassie.CassieVoiceEngine;

public class DecontaminationManager {
    public static final int DECONTAM_TIME_SECONDS = 11 * 60 + 45; // 11 Minutes 45 Seconds exact
    private static boolean active = false;
    private static int elapsedSeconds = 0;

    public static void reset() {
        active = false;
        elapsedSeconds = 0;
    }

    public static void startTimer() {
        active = true;
        elapsedSeconds = 0;
    }

    public static void tick(int roundElapsedTicks) {
        if (!active || roundElapsedTicks % 20 != 0) return;

        elapsedSeconds++;
        int remaining = DECONTAM_TIME_SECONDS - elapsedSeconds;

        if (remaining == 10 * 60) {
            CassieVoiceEngine.broadcast("LIGHT CONTAINMENT ZONE DECONTAMINATION IN T MINUS 10 MINUTES", 0x3498DB);
        } else if (remaining == 5 * 60) {
            CassieVoiceEngine.broadcast("LIGHT CONTAINMENT ZONE DECONTAMINATION IN T MINUS 5 MINUTES", 0x3498DB);
        } else if (remaining == 60) {
            CassieVoiceEngine.broadcast("LIGHT CONTAINMENT ZONE DECONTAMINATION IN T MINUS 1 MINUTE . ALL CHECKPOINTS PERMANENTLY OPEN", 0xE67E22);
        } else if (remaining == 30) {
            CassieVoiceEngine.broadcast("LIGHT CONTAINMENT ZONE DECONTAMINATION IN T MINUS 30 SECONDS", 0xE74C3C);
        } else if (remaining <= 0) {
            active = false;
            CassieVoiceEngine.broadcast("LIGHT CONTAINMENT ZONE DECONTAMINATION PROTOCOL ACTIVE . HAZARDOUS GAS RELEASED", 0xC0392B);
        }
    }
}
