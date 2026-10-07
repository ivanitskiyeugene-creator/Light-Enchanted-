package dev.snowscpied.snow;

import dev.snowscpied.config.SnowConfig;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class BloodStainManager {
    public static class BloodStainInstance {
        public double x, y, z;
        public float rotation;
        public float scale;
        public BloodType type;
        public int ageTicks;
        public int maxAgeTicks;
        public float opacity;
    }

    private static final List<BloodStainInstance> ACTIVE_STAINS = new ArrayList<>();

    public static synchronized void addBloodStain(Vec3 pos, BloodType type, float scale) {
        if (!SnowConfig.CLIENT.enableBloodStains.get()) return;

        int max = SnowConfig.CLIENT.maxBloodStains.get();
        if (ACTIVE_STAINS.size() >= max) {
            ACTIVE_STAINS.remove(0);
        }

        BloodStainInstance stain = new BloodStainInstance();
        stain.x = pos.x;
        stain.y = pos.y + 0.02; // Sits above snow plane
        stain.z = pos.z;
        stain.rotation = (float) (Math.random() * 360.0);
        stain.scale = scale;
        stain.type = type;
        stain.ageTicks = 0;
        stain.maxAgeTicks = SnowConfig.CLIENT.footprintLifetimeTicks.get() * 2;
        stain.opacity = 1.0f;

        ACTIVE_STAINS.add(stain);
    }

    public static synchronized void tick(boolean isSnowing) {
        int ageIncrement = isSnowing ? 4 : 1;
        ACTIVE_STAINS.removeIf(stain -> {
            stain.ageTicks += ageIncrement;
            stain.opacity = Math.max(0.0f, 1.0f - ((float) stain.ageTicks / stain.maxAgeTicks));
            return stain.ageTicks >= stain.maxAgeTicks;
        });
    }

    public static synchronized List<BloodStainInstance> getActiveStains() {
        return new ArrayList<>(ACTIVE_STAINS);
    }
}
