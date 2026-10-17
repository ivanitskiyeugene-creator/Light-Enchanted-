package dev.slc.controls;

/** Server-owned keyboard actions used only by living players during an SLC round. */
public enum RoundControl {
    RADIAL_INVENTORY("ИНВЕНТАРЬ", 258),   // GLFW_KEY_TAB
    ACCESS_CARD("КАРТА ДОСТУПА", 341),    // GLFW_KEY_LEFT_CONTROL
    SPRINT("БЕГ", 340),                   // GLFW_KEY_LEFT_SHIFT
    INTERACT("ВЗАИМОДЕЙСТВИЕ", 69),      // GLFW_KEY_E
    GRENADE("ГРАНАТА", 71),               // GLFW_KEY_G
    MEDKIT("АПТЕЧКА", 88),               // GLFW_KEY_X
    SCP_ABILITY("СПОСОБНОСТЬ SCP", 70),  // GLFW_KEY_F
    SCP_MIMIC("ГОЛОС SCP-939", 86);      // GLFW_KEY_V

    private final String displayName;
    private final int defaultKey;

    RoundControl(String displayName, int defaultKey) {
        this.displayName = displayName;
        this.defaultKey = defaultKey;
    }

    public String displayName() { return displayName; }
    public int defaultKey() { return defaultKey; }
}
