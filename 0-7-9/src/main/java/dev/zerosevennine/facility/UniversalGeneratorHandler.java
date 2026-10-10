package dev.zerosevennine.facility;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class UniversalGeneratorHandler {

    public static class GeneratorState {
        public final boolean exists;
        public final boolean isEngaging;
        public final boolean isEngaged;
        public final int remainingSeconds;
        public final float progress;

        public GeneratorState(boolean exists, boolean isEngaging, boolean isEngaged, int remainingSeconds, float progress) {
            this.exists = exists;
            this.isEngaging = isEngaging;
            this.isEngaged = isEngaged;
            this.remainingSeconds = remainingSeconds;
            this.progress = progress;
        }

        public static final GeneratorState NONE = new GeneratorState(false, false, false, 0, 0.0f);
    }

    private static final Set<BlockPos> KNOWN_GENERATORS = new HashSet<>();

    public static boolean isGeneratorBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        var block = state.getBlock();
        var key = ForgeRegistries.BLOCKS.getKey(block);
        if (key != null) {
            String ns = key.getNamespace().toLowerCase();
            String path = key.getPath().toLowerCase();

            if (path.contains("generator") || path.contains("power_station") || path.contains("power_core") ||
                path.contains("emergency_gen") || path.contains("reactor_core")) {
                return true;
            }

            if (ns.contains("scp") || ns.contains("facility") || ns.contains("foundation")) {
                if (path.contains("power") || path.contains("gen") || path.contains("turbine")) {
                    return true;
                }
            }
        }

        String className = block.getClass().getSimpleName().toLowerCase();
        return className.contains("generator") || className.contains("powerstation") || className.contains("emergencygen");
    }

    public static void registerGenerator(BlockPos pos) {
        KNOWN_GENERATORS.add(pos.immutable());
    }

    public static void unregisterGenerator(BlockPos pos) {
        KNOWN_GENERATORS.remove(pos);
    }

    public static Set<BlockPos> getKnownGenerators() {
        return KNOWN_GENERATORS;
    }

    public static GeneratorState getGeneratorState(Level level, BlockPos pos) {
        if (level == null || pos == null) return GeneratorState.NONE;
        BlockState state = level.getBlockState(pos);
        if (!isGeneratorBlock(state)) return GeneratorState.NONE;

        boolean engaging = false;
        boolean engaged = false;
        int remainingSec = 0;
        float progress = 0.0f;

        // 1. Inspect BlockEntity reflection (SCP:FR, SCP: The Facility, custom SCP mods)
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            // Check multi-block offset blocks around pos
            for (int dy = -1; dy <= 2; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockEntity neighborBe = level.getBlockEntity(pos.offset(dx, dy, dz));
                        if (neighborBe != null && isGeneratorEntityClass(neighborBe)) {
                            be = neighborBe;
                            break;
                        }
                    }
                    if (be != null) break;
                }
                if (be != null) break;
            }
        }

        if (be != null) {
            // Check engaging / booting methods
            Boolean engagingRes = invokeBooleanMethod(be, "isEngaging", "isBooting", "isActivating", "isStarting", "isUnlocked");
            if (engagingRes != null && engagingRes) {
                engaging = true;
            }

            // Check engaged / online methods
            Boolean engagedRes = invokeBooleanMethod(be, "isEngaged", "isActive", "isOnline", "isWorking", "isPowered", "isActivated", "isComplete");
            if (engagedRes != null && engagedRes) {
                engaged = true;
            }

            // Check time / progress methods
            Number timeRes = invokeNumberMethod(be, "getTimeRemaining", "getTimeLeft", "getTimer", "getSecondsLeft", "getRemainingTicks");
            if (timeRes != null) {
                int val = timeRes.intValue();
                remainingSec = val > 120 ? val / 20 : val;
            }

            Number progRes = invokeNumberMethod(be, "getProgress", "getEngagementProgress", "getBootProgress");
            if (progRes != null) {
                progress = progRes.floatValue();
                if (progress > 1.0f && progress <= 100.0f) {
                    progress /= 100.0f;
                }
                if (progress > 0.0f && progress < 1.0f) {
                    engaging = true;
                } else if (progress >= 1.0f) {
                    engaged = true;
                }
            }

            // Check field reflection if methods did not return
            if (!engaging && !engaged) {
                Boolean engagingField = readBooleanField(be, "engaging", "isEngaging", "booting", "activating", "starting");
                if (engagingField != null && engagingField) engaging = true;

                Boolean engagedField = readBooleanField(be, "engaged", "isEngaged", "active", "activated", "online", "completed");
                if (engagedField != null && engagedField) engaged = true;
            }
        }

        // 2. Inspect BlockState properties
        if (!engaging && !engaged) {
            for (Property<?> prop : state.getProperties()) {
                String name = prop.getName().toLowerCase();
                if (prop instanceof BooleanProperty bp) {
                    boolean val = state.getValue(bp);
                    if (name.contains("engaged") || name.contains("active") || name.contains("powered") || name.contains("online") || name.contains("working")) {
                        if (val) engaged = true;
                    } else if (name.contains("engaging") || name.contains("booting") || name.contains("starting")) {
                        if (val) engaging = true;
                    }
                } else if (prop instanceof IntegerProperty ip) {
                    int val = state.getValue(ip);
                    int max = ip.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(1);
                    if (name.contains("stage") || name.contains("state") || name.contains("progress")) {
                        if (val > 0 && val < max) {
                            engaging = true;
                            progress = (float) val / max;
                        } else if (val >= max) {
                            engaged = true;
                            progress = 1.0f;
                        }
                    }
                }
            }
        }

        return new GeneratorState(true, engaging, engaged, remainingSec, progress);
    }

    private static boolean isGeneratorEntityClass(BlockEntity be) {
        String name = be.getClass().getSimpleName().toLowerCase();
        return name.contains("generator") || name.contains("power") || name.contains("emergency");
    }

    private static Boolean invokeBooleanMethod(Object target, String... methodNames) {
        for (String mName : methodNames) {
            try {
                Method m = target.getClass().getMethod(mName);
                Object res = m.invoke(target);
                if (res instanceof Boolean b) return b;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Number invokeNumberMethod(Object target, String... methodNames) {
        for (String mName : methodNames) {
            try {
                Method m = target.getClass().getMethod(mName);
                Object res = m.invoke(target);
                if (res instanceof Number n) return n;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Boolean readBooleanField(Object target, String... fieldNames) {
        for (String fName : fieldNames) {
            try {
                Field f = target.getClass().getDeclaredField(fName);
                f.setAccessible(true);
                Object res = f.get(target);
                if (res instanceof Boolean b) return b;
            } catch (Exception ignored) {}
        }
        return null;
    }
}
