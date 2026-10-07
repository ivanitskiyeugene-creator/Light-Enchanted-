package dev.snowscpied.snow;

import dev.snowscpied.config.SnowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class FootprintManager {
    public static class FootprintInstance {
        public double x, y, z;
        public float yaw;
        public FootprintType type;
        public int ageTicks;
        public int maxAgeTicks;
        public float opacity = 1.0f;
        public boolean isLeftFoot;
    }

    private static final List<FootprintInstance> ACTIVE_PRINTS = new ArrayList<>();

    public static synchronized void addFootprint(Vec3 pos, float yaw, FootprintType type, boolean isLeftFoot) {
        if (!SnowConfig.CLIENT.enableFootprints.get()) return;

        int max = SnowConfig.CLIENT.maxFootprints.get();
        if (ACTIVE_PRINTS.size() >= max) {
            ACTIVE_PRINTS.remove(0);
        }

        FootprintInstance fp = new FootprintInstance();
        fp.x = pos.x;
        fp.y = pos.y + 0.015; // Slightly above snow surface
        fp.z = pos.z;
        fp.yaw = yaw;
        fp.type = type;
        fp.isLeftFoot = isLeftFoot;
        fp.ageTicks = 0;
        fp.maxAgeTicks = SnowConfig.CLIENT.footprintLifetimeTicks.get();
        fp.opacity = 1.0f;

        ACTIVE_PRINTS.add(fp);
    }

    public static synchronized void tick(boolean isRainingOrSnowing) {
        int ageIncrement = isRainingOrSnowing ? 3 : 1; // Snowfall fills prints faster!
        ACTIVE_PRINTS.removeIf(fp -> {
            fp.ageTicks += ageIncrement;
            fp.opacity = Math.max(0.0f, 1.0f - ((float) fp.ageTicks / fp.maxAgeTicks));
            return fp.ageTicks >= fp.maxAgeTicks;
        });
    }

    public static synchronized List<FootprintInstance> getActivePrints() {
        return new ArrayList<>(ACTIVE_PRINTS);
    }
}
