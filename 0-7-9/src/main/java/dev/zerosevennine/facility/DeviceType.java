package dev.zerosevennine.facility;

public enum DeviceType {
    DOOR("Door", 5, 10),
    TESLA("Tesla Gate", 35, 50),
    LIGHT("Room Lighting", 40, 35),
    SPEAKER("Intercom Speaker", 0, 0),
    ELEVATOR("Elevator", 10, 15);

    private final String label;
    private final int apCost;
    private final int expReward;

    DeviceType(String label, int apCost, int expReward) {
        this.label = label;
        this.apCost = apCost;
        this.expReward = expReward;
    }

    public String getLabel() {
        return label;
    }

    public int getApCost() {
        return apCost;
    }

    public int getExpReward() {
        return expReward;
    }
}
