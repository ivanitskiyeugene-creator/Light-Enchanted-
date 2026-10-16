package dev.slc.client;

import dev.slc.controls.RoundControl;

/** The server's saved SLC round control layout, received on login and after every OP edit. */
public final class ClientRoundControlCache {
    private static final int[] KEYS = new int[RoundControl.values().length];

    static {
        for (RoundControl control : RoundControl.values()) KEYS[control.ordinal()] = control.defaultKey();
    }

    private ClientRoundControlCache() {}

    public static void set(int[] keys) {
        for (RoundControl control : RoundControl.values()) {
            KEYS[control.ordinal()] = keys != null && control.ordinal() < keys.length
                    ? keys[control.ordinal()] : control.defaultKey();
        }
    }

    public static int key(RoundControl control) {
        return KEYS[control.ordinal()];
    }
}
