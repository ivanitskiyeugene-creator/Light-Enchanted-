package dev.slc.role;

public enum Role {
    CLASS_D("Класс-D", Team.CLASS_D_PERSONNEL, 100, 0, 0xFFE67E22),
    SCIENTIST("Учёный", Team.SCIENTISTS, 100, 0, 0xFFF1C40F),
    FACILITY_GUARD("Охранник Комплекса", Team.FOUNDATION_FORCES, 100, 0, 0xFF5DADE2),
    MTF_PRIVATE("Рядовой МОГ", Team.FOUNDATION_FORCES, 100, 0, 0xFF3498DB),
    MTF_SERGEANT("Сержант МОГ", Team.FOUNDATION_FORCES, 120, 0, 0xFF2E86C1),
    MTF_SPECIALIST("Специалист МОГ", Team.FOUNDATION_FORCES, 120, 0, 0xFF2874A6),
    MTF_CAPTAIN("Капитан МОГ", Team.FOUNDATION_FORCES, 150, 0, 0xFF1B4F72),
    CHAOS_CONSCRIPT("Новобранец Хаоса", Team.CHAOS_INSURGENCY, 100, 0, 0xFF2ECC71),
    CHAOS_MARAUDER("Мародёр Хаоса", Team.CHAOS_INSURGENCY, 120, 0, 0xFF27AE60),
    CHAOS_REPRESSOR("Подавитель Хаоса", Team.CHAOS_INSURGENCY, 150, 0, 0xFF1E8449),
    SCP_079("SCP-079 (Старый ИИ)", Team.SCP_OBJECTS, 100, 0, 0xFFE74C3C),
    SCP_939("SCP-939 (С многими голосами)", Team.SCP_OBJECTS, 1500, 600, 0xFFC0392B),
    SCP_173("SCP-173 (Скульптура)", Team.SCP_OBJECTS, 3200, 1200, 0xFFA93226),
    SCP_049("SCP-049 (Чумной Доктор)", Team.SCP_OBJECTS, 1700, 0, 0xFF922B21),
    SCP_049_2("SCP-049-2 (Чумной зомби)", Team.SCP_OBJECTS, 600, 0, 0xFF566573),
    SCP_096("SCP-096 (Скромник)", Team.SCP_OBJECTS, 2000, 800, 0xFF7B241C),
    SPECTATOR("Наблюдатель", Team.SPECTATORS, 1, 0, 0xFF95A5A6);

    private final String displayName;
    private final Team team;
    private final int maxHealth;
    private final int maxHumeShield;
    private final int colorRgb;

    Role(String displayName, Team team, int maxHealth, int maxHumeShield, int colorRgb) {
        this.displayName = displayName;
        this.team = team;
        this.maxHealth = maxHealth;
        this.maxHumeShield = maxHumeShield;
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

    public int getMaxHumeShield() {
        return maxHumeShield;
    }

    public int getColorRgb() {
        return colorRgb;
    }

    public boolean isScp() {
        return team == Team.SCP_OBJECTS;
    }

    public boolean hasHumeShield() {
        return maxHumeShield > 0;
    }

    public boolean isHuman() {
        return team != Team.SCP_OBJECTS && team != Team.SPECTATORS;
    }
}
