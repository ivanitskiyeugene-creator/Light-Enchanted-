package dev.slc.role;

public enum Team {
    CLASS_D_PERSONNEL("Класс-D", 0xFFE67E22),
    SCIENTISTS("Учёные", 0xFFF1C40F),
    FOUNDATION_FORCES("МОГ / Охрана", 0xFF3498DB),
    CHAOS_INSURGENCY("Повстанцы Хаоса", 0xFF2ECC71),
    SCP_OBJECTS("Объекты SCP", 0xFFE74C3C),
    SPECTATORS("Наблюдатели", 0xFF95A5A6);

    private final String displayName;
    private final int colorRgb;

    Team(String displayName, int colorRgb) {
        this.displayName = displayName;
        this.colorRgb = colorRgb;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColorRgb() {
        return colorRgb;
    }
}
