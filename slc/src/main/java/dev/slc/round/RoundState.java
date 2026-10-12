package dev.slc.round;

public enum RoundState {
    WAITING_FOR_PLAYERS("Ожидание игроков"),
    LOBBY_COUNTDOWN("Подготовка к раунду"),
    IN_PROGRESS("Раунд в процессе"),
    WARHEAD_DETONATING("Детонация Альфа-Боеголовки"),
    ROUND_ENDED("Раунд завершён");

    private final String displayTitle;

    RoundState(String displayTitle) {
        this.displayTitle = displayTitle;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }
}
