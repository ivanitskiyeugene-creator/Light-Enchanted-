package dev.slc.compat;

import dev.slc.SecretLabCubed;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional Valkyrien Skies bridge for SLC's coordinate-based elevators.
 *
 * <p>VS stores ship blocks in shipyard coordinates while players stand at their transformed
 * world-space position. A vanilla AABB query therefore misses riders inside an elevator cabin on
 * a moving ship, and a vanilla destination delta can send them to shipyard coordinates. This
 * bridge translates cabin bounds and entity positions between the two spaces. It intentionally
 * uses reflection so SLC remains loadable when Valkyrien Skies is not installed.</p>
 */
public final class ValkyrienSkiesCompat {
    private static final String VS_MOD_ID = "valkyrienskies";
    private static final String VS_UTILS = "org.valkyrienskies.mod.common.VSGameUtilsKt";
    private static final double CABIN_EPSILON = 0.0625D;
    private static final AtomicBoolean REFLECTION_FAILURE_LOGGED = new AtomicBoolean(false);

    private ValkyrienSkiesCompat() {
    }

    /**
     * Creates a coordinate-space view for a saved cabin AABB. Normal-world cabins use identity
     * transforms; shipyard cabins are projected through their currently loaded VS ship transform.
     */
    public static CabinSpace cabinSpace(ServerLevel level, BlockPos anchor, AABB localBounds) {
        if (!isValkyrienSkiesLoaded()) {
            return CabinSpace.identity(localBounds);
        }

        try {
            Object ship = getShipManagingPosition(level, anchor);
            if (ship == null) {
                // A shipyard position without a loaded ship has no safe world-space transform.
                // Refuse only that endpoint; ordinary non-VS elevators keep the identity path.
                return isShipyardPosition(level, anchor)
                        ? CabinSpace.unavailable(localBounds)
                        : CabinSpace.identity(localBounds);
            }

            Object transform = invokeNoArg(ship, "getTransform");
            Object shipToWorld = invokeNoArg(transform, "getShipToWorld");
            Object worldToShip = invokeNoArg(transform, "getWorldToShip");
            if (shipToWorld instanceof Matrix4dc forward && worldToShip instanceof Matrix4dc inverse) {
                return new CabinSpace(localBounds, forward, inverse, true, true);
            }
            throw new ReflectiveOperationException("VS ship transform did not expose Matrix4dc transforms");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logReflectionFailure(exception);
            // Preserve the legacy elevator behaviour rather than crashing a world because an
            // installed VS version changed an optional API.
            return CabinSpace.identity(localBounds);
        }
    }

    /** Finds the entities physically standing inside a cabin, including a cabin carried by a VS ship. */
    public static List<Entity> entitiesInCabin(ServerLevel level, CabinSpace cabin) {
        AABB searchBounds = cabin.worldBounds().inflate(CABIN_EPSILON);
        return level.getEntities((Entity) null, searchBounds,
                entity -> cabin.containsWorldPosition(entity.position()));
    }

    public static boolean isValkyrienSkiesLoaded() {
        return ModList.get().isLoaded(VS_MOD_ID);
    }

    private static Object getShipManagingPosition(ServerLevel level, BlockPos position)
            throws ReflectiveOperationException {
        Class<?> utils = Class.forName(VS_UTILS);
        for (Method method : utils.getMethods()) {
            if (!method.getName().equals("getLoadedShipManagingPos")
                    || !Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes[0].isAssignableFrom(level.getClass())
                    && parameterTypes[1].isAssignableFrom(position.getClass())) {
                return method.invoke(null, level, position);
            }
        }
        throw new NoSuchMethodException("VS getLoadedShipManagingPos(ServerLevel, BlockPos)");
    }

    private static boolean isShipyardPosition(ServerLevel level, BlockPos position)
            throws ReflectiveOperationException {
        Class<?> utils = Class.forName(VS_UTILS);
        for (Method method : utils.getMethods()) {
            if (!method.getName().equals("isBlockInShipyard")
                    || !Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes[0].isAssignableFrom(level.getClass())
                    && parameterTypes[1].isAssignableFrom(position.getClass())) {
                Object result = method.invoke(null, level, position);
                return result instanceof Boolean bool && bool;
            }
        }
        throw new NoSuchMethodException("VS isBlockInShipyard(Level, BlockPos)");
    }

    private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) {
            throw new ReflectiveOperationException("VS returned a null transform owner");
        }
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static void logReflectionFailure(Exception exception) {
        if (REFLECTION_FAILURE_LOGGED.compareAndSet(false, true)) {
            SecretLabCubed.LOGGER.warn("[SLC] Valkyrien Skies is installed but its optional elevator transform bridge could not initialize; using vanilla elevator coordinates.", exception);
        }
    }

    /** A saved elevator cabin in either normal-world or Valkyrien Skies ship-local coordinates. */
    public static final class CabinSpace {
        private final AABB localBounds;
        private final Matrix4dc localToWorld;
        private final Matrix4dc worldToLocal;
        private final boolean shipMounted;
        private final boolean available;

        private CabinSpace(AABB localBounds, Matrix4dc localToWorld, Matrix4dc worldToLocal,
                           boolean shipMounted, boolean available) {
            this.localBounds = localBounds;
            this.localToWorld = localToWorld;
            this.worldToLocal = worldToLocal;
            this.shipMounted = shipMounted;
            this.available = available;
        }

        private static CabinSpace identity(AABB bounds) {
            return new CabinSpace(bounds, null, null, false, true);
        }

        private static CabinSpace unavailable(AABB bounds) {
            return new CabinSpace(bounds, null, null, true, false);
        }

        public boolean isShipMounted() {
            return shipMounted;
        }

        /** False only when a VS shipyard endpoint exists but the ship is not currently loaded. */
        public boolean isAvailable() {
            return available;
        }

        /**
         * Matches the legacy lift translation rule: horizontal center plus the cabin floor Y.
         * Keeping this anchor avoids changing the placement of existing static-world elevators.
         */
        public Vec3 localTransferAnchor() {
            return new Vec3((localBounds.minX + localBounds.maxX) * 0.5D,
                    localBounds.minY,
                    (localBounds.minZ + localBounds.maxZ) * 0.5D);
        }

        public Vec3 toLocal(Vec3 worldPosition) {
            return transform(worldToLocal, worldPosition);
        }

        public Vec3 toWorld(Vec3 localPosition) {
            return transform(localToWorld, localPosition);
        }

        public boolean containsWorldPosition(Vec3 worldPosition) {
            Vec3 localPosition = toLocal(worldPosition);
            return localBounds.inflate(CABIN_EPSILON).contains(localPosition.x, localPosition.y, localPosition.z);
        }

        public AABB worldBounds() {
            if (localToWorld == null) {
                return localBounds;
            }

            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            double[] xs = {localBounds.minX, localBounds.maxX};
            double[] ys = {localBounds.minY, localBounds.maxY};
            double[] zs = {localBounds.minZ, localBounds.maxZ};

            for (double x : xs) {
                for (double y : ys) {
                    for (double z : zs) {
                        Vec3 transformed = toWorld(new Vec3(x, y, z));
                        minX = Math.min(minX, transformed.x);
                        minY = Math.min(minY, transformed.y);
                        minZ = Math.min(minZ, transformed.z);
                        maxX = Math.max(maxX, transformed.x);
                        maxY = Math.max(maxY, transformed.y);
                        maxZ = Math.max(maxZ, transformed.z);
                    }
                }
            }
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private static Vec3 transform(Matrix4dc matrix, Vec3 position) {
            if (matrix == null) {
                return position;
            }
            Vector3d transformed = matrix.transformPosition(new Vector3d(position.x, position.y, position.z));
            return new Vec3(transformed.x(), transformed.y(), transformed.z());
        }
    }
}
