package dev.slc.role;

public enum Role {
    CLASS_D("Класс-D", Team.CLASS_D_PERSONNEL, 100, 0xFFE67E22),
    SCIENTIST("Учёный", Team.SCIENTISTS, 100, 0xFFF1C40F),
    FACILITY_GUARD("Охранник Комплекса", Team.FOUNDATION_FORCES, 100, 0xFF5DADE2),
    MTF_CADET("Рядовой МОГ", Team.FOUNDATION_FORCES, 100, 0xFF3498DB),
    MTF_LIEUTENANT("Специалист МОГ", Team.FOUNDATION_FORCES, 120, 0xFF2980B9),
    MTF_COMMANDER("Капитан МОГ", Team.FOUNDATION_FORCES, 150, 0xFF1F618D),
    CHAOS_CONSCRIPT("Новобранец Хаоса", Team.CHAOS_INSURGENCY, 100, 0xFF2ECC71),
    CHAOS_MARAUDER("Мародёр Хаоса", Team.CHAOS_INSURGENCY, 120, 0xFF27AE60),
    CHAOS_REPRESSOR("Подавитель Хаоса", Team.CHAOS_INSURGENCY, 150, 0xFF1E8449),
    SCP_079("SCP-079 (Старый ИИ)", Team.SCP_OBJECTS, 100, 0xFFE74C3C),
    SCP_939("SCP-939 (С многими голосами)", Team.SCP_OBJECTS, 1500, 0xFFC0392B),
    SCP_173("SCP-173 (Скульптура)", Team.SCP_OBJECTS, 3200, 0xFFA93226),
    SCP_049("SCP-049 (Чумной Доктор)", Team.SCP_OBJECTS, 1700, 0xFF922B21),
    SCP_096("SCP-096 (Скромник)", Team.SCP_OBJECTS, 2000, 0xFF7B241C),
    SPECTATOR("Наблюдатель", Team.SPECTATORS, 1, 0xFF95A5A6);

    private final String displayName;
    private final Team team;
    private final int maxHealth;
    private final int colorRgb;

    Role(String displayName, Team team, int maxHealth, int colorRgb) {
        this.displayName = displayName;
        this.team = team;
        this.maxHealth = maxHealth;
        this.colorRgb = colorRgb;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Team getTeam() {
        return team;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public int getColorRgb() {
        return colorRgb;
    }

    public boolean isScp() {
        return team == Team.SCP_OBJECTS;
    }

    public boolean isHuman() {
        return team != Team.SCP_OBJECTS && team != Team.SPECTATORS;
    }
}
