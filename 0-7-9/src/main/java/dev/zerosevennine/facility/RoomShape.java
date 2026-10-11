package dev.zerosevennine.facility;

public enum RoomShape {
    SQUARE("Square Hall / Box", "BOX"),
    STRAIGHT("Straight Hallway", "I"),
    CORNER("Corner Turn", "L"),
    T_SHAPE("T-Junction", "T"),
    CROSS("4-Way Intersection", "X"),
    END_ROOM("End Chamber / Dead End", "END"),
    AIRLOCK("Airlock Gate", "AIR"),
    ELEVATOR("Elevator Chamber", "ELEV"),
    CHECKPOINT("Heavy Checkpoint", "CHK");

    private final String label;
    private final String code;

    RoomShape(String label, String code) {
        this.label = label;
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public String getCode() {
        return code;
    }
}
