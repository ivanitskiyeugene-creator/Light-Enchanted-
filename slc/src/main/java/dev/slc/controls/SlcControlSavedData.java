package dev.slc.controls;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.Map;

/** Per-world authoritative round keyboard layout, configured only by OP 4 through the SLC panel. */
public final class SlcControlSavedData extends SavedData {
    private static final String DATA_NAME = "slc_round_controls";
    private final Map<RoundControl, Integer> keys = new EnumMap<>(RoundControl.class);

    public SlcControlSavedData() {
        for (RoundControl control : RoundControl.values()) keys.put(control, control.defaultKey());
    }

    public static SlcControlSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                SlcControlSavedData::load, SlcControlSavedData::new, DATA_NAME
        );
    }

    public int key(RoundControl control) {
        return keys.getOrDefault(control, control.defaultKey());
    }

    public boolean setKey(RoundControl control, int keyCode) {
        if (control == null || keyCode < 32 || keyCode > 348) return false;
        for (RoundControl other : RoundControl.values()) {
            if (other != control && key(other) == keyCode) return false;
        }
        keys.put(control, keyCode);
        setDirty();
        return true;
    }

    public int[] copyKeys() {
        int[] values = new int[RoundControl.values().length];
        for (RoundControl control : RoundControl.values()) values[control.ordinal()] = key(control);
        return values;
    }

    public static SlcControlSavedData load(CompoundTag tag) {
        SlcControlSavedData data = new SlcControlSavedData();
        for (RoundControl control : RoundControl.values()) {
            String key = control.name();
            if (tag.contains(key)) data.keys.put(control, tag.getInt(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (RoundControl control : RoundControl.values()) tag.putInt(control.name(), key(control));
        return tag;
    }
}
