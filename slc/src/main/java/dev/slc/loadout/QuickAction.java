package dev.slc.loadout;

/** Canonical in-round SLC shortcuts. The item assigned to each action is saved per role. */
public enum QuickAction {
    CARD("CTRL", "КАРТА ДОСТУПА", false),
    INTERACT("E", "ВЗАИМОДЕЙСТВИЕ", true),
    GRENADE("G", "ГРАНАТА", true),
    MEDKIT("X", "АПТЕЧКА", true);

    private final String keyLabel;
    private final String displayName;
    private final boolean useImmediately;

    QuickAction(String keyLabel, String displayName, boolean useImmediately) {
        this.keyLabel = keyLabel;
        this.displayName = displayName;
        this.useImmediately = useImmediately;
    }

    public String keyLabel() { return keyLabel; }
    public String displayName() { return displayName; }
    public boolean useImmediately() { return useImmediately; }
}
