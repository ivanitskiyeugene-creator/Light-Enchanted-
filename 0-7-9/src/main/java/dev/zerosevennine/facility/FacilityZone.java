package dev.zerosevennine.facility;

public enum FacilityZone {
    LCZ("Light Containment Zone", "LCZ", 0xD8B26E),
    HCZ("Heavy Containment Zone", "HCZ", 0xE74C3C),
    EZ("Entrance Zone", "EZ", 0x85929E),
    SURFACE("Surface Zone", "SURF", 0x27AE60);

    private final String displayName;
    private final String code;
    private final int colorRgb;

    FacilityZone(String displayName, String code, int colorRgb) {
        this.displayName = displayName;
        this.code = code;
        this.colorRgb = colorRgb;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    public int getColorRgb() {
        return colorRgb;
    }
}
