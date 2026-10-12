package dev.zerosevennine.facility;

import java.util.ArrayList;
import java.util.List;

public enum RoomPrefab {
    // === HCZ PREFABS ===
    HCZ_079("079 CONT", FacilityZone.HCZ, RoomShape.SQUARE, 0, false, null, "079 Containment Chamber"),
    HCZ_106("106 CONT", FacilityZone.HCZ, RoomShape.SQUARE, 0, false, null, "106 Containment Chamber"),
    HCZ_049_173("049/173 CONT", FacilityZone.HCZ, RoomShape.ELEVATOR, 0, true, FacilityZone.LCZ, "049/173 Elevator Hub"),
    HCZ_096("096 CONT", FacilityZone.HCZ, RoomShape.END_ROOM, 0, false, null, "096 Containment Chamber"),
    HCZ_TESLA("TESLA", FacilityZone.HCZ, RoomShape.STRAIGHT, 0, false, null, "Tesla Gate Corridor"),
    HCZ_CHECKPOINT("CHECKPOINT", FacilityZone.HCZ, RoomShape.CHECKPOINT, 1, true, FacilityZone.EZ, "HCZ/EZ Security Checkpoint"),
    HCZ_WARHEAD("WARHEAD", FacilityZone.HCZ, RoomShape.ELEVATOR, 0, true, FacilityZone.HCZ, "Alpha Warhead Silo Elevator"),
    HCZ_HID("H.I.D", FacilityZone.HCZ, RoomShape.END_ROOM, 0, false, null, "Micro-HID Storage Vault"),
    HCZ_ARMORY("ARMORY", FacilityZone.HCZ, RoomShape.T_SHAPE, 1, false, null, "Heavy Weapons Armory"),
    HCZ_TESTROOM("TESTROOM", FacilityZone.HCZ, RoomShape.CORNER, 0, false, null, "HCZ Test Room (Curved)"),
    HCZ_939("939 CONT", FacilityZone.HCZ, RoomShape.END_ROOM, 0, false, null, "939 Containment Silo"),
    HCZ_SERVERS("SERVERS", FacilityZone.HCZ, RoomShape.T_SHAPE, 0, false, null, "Server Room Hub"),

    // === LCZ PREFABS ===
    LCZ_914("914 CONT", FacilityZone.LCZ, RoomShape.END_ROOM, 1, false, null, "SCP-914 Research Lab"),
    LCZ_173("173 CONT", FacilityZone.LCZ, RoomShape.SQUARE, 0, false, null, "SCP-173 Containment Cell"),
    LCZ_CLASS_D("CLASS-D", FacilityZone.LCZ, RoomShape.STRAIGHT, 1, false, null, "Class-D Containment Block"),
    LCZ_AIRLOCK("AIRLOCK", FacilityZone.LCZ, RoomShape.AIRLOCK, 0, false, null, "Decontamination Airlock"),
    LCZ_ELEV_A("ELEV A", FacilityZone.LCZ, RoomShape.ELEVATOR, 1, true, FacilityZone.HCZ, "Elevator System A"),
    LCZ_ELEV_B("ELEV B", FacilityZone.LCZ, RoomShape.ELEVATOR, 1, true, FacilityZone.HCZ, "Elevator System B"),
    LCZ_ARMORY("ARMORY", FacilityZone.LCZ, RoomShape.END_ROOM, 0, false, null, "LCZ Security Armory"),
    LCZ_GREENHOUSE("GREENHOUSE", FacilityZone.LCZ, RoomShape.END_ROOM, 0, false, null, "Botanical Greenhouse / Plants"),
    LCZ_WC("WC", FacilityZone.LCZ, RoomShape.END_ROOM, 0, false, null, "Restroom (WC)"),
    LCZ_TEST_ROOM("TEST ROOM", FacilityZone.LCZ, RoomShape.END_ROOM, 0, false, null, "PC-15 / Test Chamber"),
    LCZ_CHECKPOINT("CHECKPOINT", FacilityZone.LCZ, RoomShape.CHECKPOINT, 1, true, FacilityZone.HCZ, "LCZ/HCZ Security Checkpoint"),

    // === EZ PREFABS ===
    EZ_GATE_A("GATE A", FacilityZone.EZ, RoomShape.ELEVATOR, 0, true, FacilityZone.SURFACE, "Surface Gate A Exit"),
    EZ_GATE_B("GATE B", FacilityZone.EZ, RoomShape.ELEVATOR, 0, true, FacilityZone.SURFACE, "Surface Gate B Exit"),
    EZ_INTERCOM("INTERCOM", FacilityZone.EZ, RoomShape.T_SHAPE, 0, false, null, "Facility Broadcast Intercom"),
    EZ_EVAC_SHELTER("EVAC SHELTER", FacilityZone.EZ, RoomShape.END_ROOM, 0, false, null, "Evacuation Blast Shelter"),
    EZ_CHECKPOINT("CHECKPOINT", FacilityZone.EZ, RoomShape.CHECKPOINT, 1, true, FacilityZone.HCZ, "EZ/HCZ Checkpoint Gate"),
    EZ_TWO_WAY("TWO-WAY OFFICE", FacilityZone.EZ, RoomShape.STRAIGHT, 1, false, null, "Two-Way Administrative Office"),
    EZ_CURVED("CURVED OFFICE", FacilityZone.EZ, RoomShape.CORNER, 0, false, null, "Curved Administrative Office"),
    EZ_RED_ROOM("RED ROOM", FacilityZone.EZ, RoomShape.END_ROOM, 0, false, null, "Red Lighting Office");

    public final String defaultName;
    public final FacilityZone zone;
    public final RoomShape shape;
    public final int defaultRotation;
    public final boolean isTransit;
    public final FacilityZone targetZone;
    public final String description;

    RoomPrefab(String defaultName, FacilityZone zone, RoomShape shape, int defaultRotation,
               boolean isTransit, FacilityZone targetZone, String description) {
        this.defaultName = defaultName;
        this.zone = zone;
        this.shape = shape;
        this.defaultRotation = defaultRotation;
        this.isTransit = isTransit;
        this.targetZone = targetZone != null ? targetZone : zone;
        this.description = description;
    }

    public static List<RoomPrefab> getPrefabsForZone(FacilityZone zone) {
        List<RoomPrefab> list = new ArrayList<>();
        for (RoomPrefab p : values()) {
            if (p.zone == zone) {
                list.add(p);
            }
        }
        return list;
    }
}
