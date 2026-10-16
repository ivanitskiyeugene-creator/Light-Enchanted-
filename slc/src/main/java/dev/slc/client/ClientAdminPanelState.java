package dev.slc.client;

/** Short-lived visual status for the open admin panel; deliberately never writes to chat/actionbar. */
public final class ClientAdminPanelState {
    private static String message = "";
    private static boolean success;
    private static long expiresAt;

    private ClientAdminPanelState() {}

    public static void setStatus(String text, boolean wasSuccessful) {
        message = text;
        success = wasSuccessful;
        expiresAt = System.currentTimeMillis() + 5000L;
    }

    public static String message() {
        return System.currentTimeMillis() < expiresAt ? message : "";
    }

    public static boolean success() {
        return success;
    }
}
