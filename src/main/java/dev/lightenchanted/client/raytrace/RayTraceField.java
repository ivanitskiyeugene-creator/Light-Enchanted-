package dev.lightenchanted.client.raytrace;

import dev.lightenchanted.beam.BeamShape;
import dev.lightenchanted.blockentity.IndustrialFanBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

/**
 * STR 2.2 High-Performance Raytracer:
 *
 * Fully optimized with lazy evaluation, spatial bounding box early rejection,
 * pre-allocated memory pools, and periodic caching.
 *
 * Supports custom beam shapes (CONE, SQUARE, OVAL, SLIT, SHEET, STAR) and dynamic
 * rotating obstacle testing (SCP:SL HCZ Industrial Fans).
 */
public class RayTraceField {
    public static final int RINGS = 8;
    public static final int RAYS_PER_RING = 24;
    public static final int TOTAL_RAYS = 1 + RINGS * RAYS_PER_RING; // 193 rays

    public static class Ray {
        public float localX, localZ;
        public float targetLocalX, targetLocalZ;
        public float length;
        public double impactX, impactY, impactZ;
        public boolean hitSolid;
        public float intensity;
    }

    public static class Triangle {
        public float x0, y0, z0;
        public float x1, y1, z1;
        public float x2, y2, z2;

        public void set(float x0, float y0, float z0,
                        float x1, float y1, float z1,
                        float x2, float y2, float z2) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
        }
    }

    private final Ray[] rays = new Ray[TOTAL_RAYS];
    private final List<Triangle> obstacleTriangles = new ArrayList<>();
    private final List<AABB> obstacleBoxes = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create(42L);

    // Cache tracking
    private Vec3 lastOrigin;
    private Vec3 lastDir;
    private float lastW, lastEndW, lastMaxDist;
    private boolean lastShadows;
    private BeamShape lastShape;
    private long lastTraceTick = -100L;
    private int triangleCount = 0;
    private int boxCount = 0;
    private AABB combinedBounds = null;

    public RayTraceField() {
        for (int i = 0; i < TOTAL_RAYS; i++) {
            rays[i] = new Ray();
        }
        for (int i = 0; i < 4096; i++) {
            obstacleTriangles.add(new Triangle());
        }
    }

    public Ray[] getRays() {
        return rays;
    }

    public boolean needsRetrace(long currentTick, Vec3 origin, Vec3 dir,
                                float width, float endWidth, float maxDist,
                                BeamShape shape, boolean shadows) {
        if (lastOrigin == null || lastDir == null) return true;
        if (currentTick - lastTraceTick > 20L) return true; // periodic 1 sec refresh
        if (lastShadows != shadows || lastShape != shape) return true;
        if (Math.abs(lastW - width) > 0.01f || Math.abs(lastEndW - endWidth) > 0.01f || Math.abs(lastMaxDist - maxDist) > 0.1f) return true;
        if (lastOrigin.distanceToSqr(origin) > 0.0001) return true;
        return lastDir.distanceToSqr(dir) > 0.0001;
    }

    public void trace(Level level, BlockPos emitterPos, Vec3 origin, Vec3 dir,
                      float width, float endWidth, float maxDist, BeamShape shape,
                      boolean shadows, long currentTick) {
        lastOrigin = origin;
        lastDir = dir;
        lastW = width;
        lastEndW = endWidth;
        lastMaxDist = maxDist;
        lastShadows = shadows;
        lastShape = shape;
        lastTraceTick = currentTick;

        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 actualUp = right.cross(dir).normalize();

        float r0 = Math.max(0.02f, width * 0.5f);
        float r1 = (shape == BeamShape.CONE || shape == BeamShape.SQUARE || shape == BeamShape.OVAL)
                ? Math.max(0.05f, endWidth * 0.5f) : r0;

        if (shadows) {
            collectObstacles(level, emitterPos, origin, dir, Math.max(r0, r1), Math.min(16.0f, maxDist));
        } else {
            triangleCount = 0;
            boxCount = 0;
            combinedBounds = null;
        }

        int rayIdx = 0;
        setupRay(rays[rayIdx++], 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);

        for (int ring = 1; ring <= RINGS; ring++) {
            float frac = (float) ring / RINGS;
            float ringR0 = r0 * frac;
            float ringR1 = r1 * frac;
            float ringIntensity = (float) Math.exp(-2.8 * frac * frac);

            for (int r = 0; r < RAYS_PER_RING; r++) {
                float angle = (r * (float) (Math.PI * 2.0) / RAYS_PER_RING) + (ring * 0.35f);
                float c = Mth.cos(angle);
                float s = Mth.sin(angle);

                float lx0 = ringR0 * c;
                float lz0 = ringR0 * s;
                float lx1 = ringR1 * c;
                float lz1 = ringR1 * s;

                // Shape-specific profile modifications
                if (shape == BeamShape.SQUARE) {
                    float maxCoord = Math.max(Math.abs(c), Math.abs(s));
                    if (maxCoord > 0.01f) {
                        float sqScale = 1.0f / maxCoord;
                        lx0 *= sqScale; lz0 *= sqScale;
                        lx1 *= sqScale; lz1 *= sqScale;
                    }
                } else if (shape == BeamShape.OVAL) {
                    lx0 *= 1.8f; lz0 *= 0.6f;
                    lx1 *= 1.8f; lz1 *= 0.6f;
                } else if (shape == BeamShape.SLIT) {
                    lx0 *= 3.0f; lz0 *= 0.15f;
                    lx1 *= 3.0f; lz1 *= 0.15f;
                } else if (shape == BeamShape.SHEET) {
                    lx0 *= 2.5f; lz0 = 0.0f;
                    lx1 *= 2.5f; lz1 = 0.0f;
                }

                setupRay(rays[rayIdx++], lx0, lz0, lx1, lz1, ringIntensity);
            }
        }

        // Central terrain depth raycast
        Vec3 centralTarget = origin.add(dir.scale(maxDist));
        BlockHitResult mainHit = level.clip(new ClipContext(
                origin, centralTarget, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        double defaultTerrainDist = mainHit.getType() != HitResult.Type.MISS
                ? origin.distanceTo(mainHit.getLocation())
                : maxDist;

        // March all 193 rays with BVH / early bounds rejection
        for (int i = 0; i < TOTAL_RAYS; i++) {
            Ray ray = rays[i];

            Vec3 rayStart = origin.add(right.scale(ray.localX)).add(actualUp.scale(ray.localZ));
            Vec3 rayEndTarget = origin.add(dir.scale(maxDist))
                    .add(right.scale(ray.targetLocalX))
                    .add(actualUp.scale(ray.targetLocalZ));

            Vec3 rayDir = rayEndTarget.subtract(rayStart).normalize();
            double rdx = rayDir.x, rdy = rayDir.y, rdz = rayDir.z;

            double hitDist = defaultTerrainDist;
            boolean solidHit = mainHit.getType() != HitResult.Type.MISS;

            // Only test 3D obstacle triangles if ray intersects obstacle bounding box
            if (shadows && triangleCount > 0 && combinedBounds != null) {
                double invDx = Math.abs(rdx) > 1e-6 ? 1.0 / rdx : 1e6;
                double invDy = Math.abs(rdy) > 1e-6 ? 1.0 / rdy : 1e6;
                double invDz = Math.abs(rdz) > 1e-6 ? 1.0 / rdz : 1e6;

                if (intersectRayAABB(rayStart.x, rayStart.y, rayStart.z, invDx, invDy, invDz,
                        combinedBounds.minX, combinedBounds.minY, combinedBounds.minZ,
                        combinedBounds.maxX, combinedBounds.maxY, combinedBounds.maxZ, hitDist) > 0.0) {

                    for (int t = 0; t < triangleCount; t++) {
                        Triangle tri = obstacleTriangles.get(t);
                        double triHit = intersectRayTriangle(rayStart.x, rayStart.y, rayStart.z,
                                rdx, rdy, rdz,
                                tri.x0, tri.y0, tri.z0, tri.x1, tri.y1, tri.z1, tri.x2, tri.y2, tri.z2,
                                hitDist);
                        if (triHit > 0.02 && triHit < hitDist) {
                            hitDist = triHit;
                            solidHit = true;
                        }
                    }
                }
            }

            // Test obstacle boxes
            if (shadows && boxCount > 0) {
                double invDx = Math.abs(rdx) > 1e-6 ? 1.0 / rdx : 1e6;
                double invDy = Math.abs(rdy) > 1e-6 ? 1.0 / rdy : 1e6;
                double invDz = Math.abs(rdz) > 1e-6 ? 1.0 / rdz : 1e6;

                for (int b = 0; b < boxCount; b++) {
                    AABB box = obstacleBoxes.get(b);
                    double boxHit = intersectRayAABB(rayStart.x, rayStart.y, rayStart.z,
                            invDx, invDy, invDz,
                            box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                            hitDist);
                    if (boxHit > 0.02 && boxHit < hitDist) {
                        hitDist = boxHit;
                        solidHit = true;
                    }
                }
            }

            ray.length = (float) hitDist;
            ray.hitSolid = solidHit;
            Vec3 impact = rayStart.add(rayDir.scale(hitDist));
            ray.impactX = impact.x;
            ray.impactY = impact.y;
            ray.impactZ = impact.z;
        }
    }

    private static void setupRay(Ray ray, float lx0, float lz0, float lx1, float lz1, float intensity) {
        ray.localX = lx0;
        ray.localZ = lz0;
        ray.targetLocalX = lx1;
        ray.targetLocalZ = lz1;
        ray.intensity = intensity;
    }

    private void collectObstacles(Level level, BlockPos emitterPos, Vec3 origin,
                                  Vec3 dir, float radius, float maxDist) {
        triangleCount = 0;
        obstacleBoxes.clear();

        BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        Vec3 end = origin.add(dir.scale(maxDist));

        int margin = (int) Math.ceil(Math.max(radius * 0.5f + 2.0f, 4.0f));
        int minX = (int) Math.floor(Math.min(origin.x, end.x) - margin);
        int maxX = (int) Math.ceil(Math.max(origin.x, end.x) + margin);
        int minY = (int) Math.floor(Math.min(origin.y, end.y) - margin);
        int maxY = (int) Math.ceil(Math.max(origin.y, end.y) + margin);
        int minZ = (int) Math.floor(Math.min(origin.z, end.z) - margin);
        int maxZ = (int) Math.ceil(Math.max(origin.z, end.z) + margin);

        double bMinX = Double.MAX_VALUE, bMinY = Double.MAX_VALUE, bMinZ = Double.MAX_VALUE;
        double bMaxX = -Double.MAX_VALUE, bMaxY = -Double.MAX_VALUE, bMaxZ = -Double.MAX_VALUE;

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    mpos.set(x, y, z);
                    if (mpos.equals(emitterPos)) {
                        continue;
                    }
                    if (!level.hasChunkAt(mpos)) {
                        continue;
                    }

                    // Dynamic Fan Obstacles
                    if (level.getBlockEntity(mpos) instanceof IndustrialFanBlockEntity fan && fan.isMaster()) {
                        Direction fFacing = fan.getFacing();
                        float cx = x + 0.5f, cy = y + 0.5f, cz = z + 0.5f;
                        float fAngle = fan.getSpinAngle(0.0f);

                        // Add fan blade collision triangles
                        int bCount = 5;
                        float tau = (float) (Math.PI * 2.0);
                        float rHub = 0.35f, rBlade = 1.35f;

                        for (int bi = 0; bi < bCount; bi++) {
                            float ba = tau * bi / bCount + (float) Math.toRadians(fAngle);
                            float cos = Mth.cos(ba), sin = Mth.sin(ba);
                            float pCos = -sin * 0.25f, pSin = cos * 0.25f;

                            float bx0 = cx + cos * rHub - pCos;
                            float by0 = cy + sin * rHub - pSin;
                            float bx1 = cx + cos * rHub + pCos;
                            float by1 = cy + sin * rHub + pSin;
                            float bx2 = cx + cos * rBlade + pCos * 1.5f;
                            float by2 = cy + sin * rBlade + pSin * 1.5f;
                            float bx3 = cx + cos * rBlade - pCos * 1.5f;
                            float by3 = cy + sin * rBlade - pSin * 1.5f;

                            if (triangleCount + 2 < obstacleTriangles.size()) {
                                obstacleTriangles.get(triangleCount++).set(bx0, by0, cz, bx1, by1, cz, bx2, by2, cz);
                                obstacleTriangles.get(triangleCount++).set(bx0, by0, cz, bx2, by2, cz, bx3, by3, cz);

                                bMinX = Math.min(bMinX, Math.min(bx0, bx2));
                                bMinY = Math.min(bMinY, Math.min(by0, by2));
                                bMinZ = Math.min(bMinZ, cz - 0.5);
                                bMaxX = Math.max(bMaxX, Math.max(bx1, bx3));
                                bMaxY = Math.max(bMaxY, Math.max(by1, by3));
                                bMaxZ = Math.max(bMaxZ, cz + 0.5);
                            }
                        }
                    }

                    BlockState state = level.getBlockState(mpos);
                    if (state.isAir() || state.is(Blocks.LIGHT) || state.canBeReplaced()) {
                        continue;
                    }

                    boolean gotPolygons = false;
                    try {
                        BakedModel model = brd.getBlockModel(state);
                        if (model != null) {
                            List<BakedQuad> quads = new ArrayList<>();
                            try {
                                quads.addAll(model.getQuads(state, null, RANDOM, ModelData.EMPTY, null));
                            } catch (Exception ignored) {}
                            for (Direction d : Direction.values()) {
                                try {
                                    quads.addAll(model.getQuads(state, d, RANDOM, ModelData.EMPTY, null));
                                } catch (Exception ignored) {}
                            }

                            if (!quads.isEmpty()) {
                                for (int qi = 0; qi < quads.size() && triangleCount + 2 < obstacleTriangles.size(); qi++) {
                                    BakedQuad q = quads.get(qi);
                                    int[] vData = q.getVertices();
                                    if (vData.length >= 32) {
                                        float x0 = x + Float.intBitsToFloat(vData[0]);
                                        float y0 = y + Float.intBitsToFloat(vData[1]);
                                        float z0 = z + Float.intBitsToFloat(vData[2]);

                                        float x1 = x + Float.intBitsToFloat(vData[8]);
                                        float y1 = y + Float.intBitsToFloat(vData[9]);
                                        float z1 = z + Float.intBitsToFloat(vData[10]);

                                        float x2 = x + Float.intBitsToFloat(vData[16]);
                                        float y2 = y + Float.intBitsToFloat(vData[17]);
                                        float z2 = z + Float.intBitsToFloat(vData[18]);

                                        float x3 = x + Float.intBitsToFloat(vData[24]);
                                        float y3 = y + Float.intBitsToFloat(vData[25]);
                                        float z3 = z + Float.intBitsToFloat(vData[26]);

                                        obstacleTriangles.get(triangleCount++).set(x0, y0, z0, x1, y1, z1, x2, y2, z2);
                                        obstacleTriangles.get(triangleCount++).set(x0, y0, z0, x2, y2, z2, x3, y3, z3);

                                        bMinX = Math.min(bMinX, Math.min(Math.min(x0, x1), Math.min(x2, x3)));
                                        bMinY = Math.min(bMinY, Math.min(Math.min(y0, y1), Math.min(y2, y3)));
                                        bMinZ = Math.min(bMinZ, Math.min(Math.min(z0, z1), Math.min(z2, z3)));
                                        bMaxX = Math.max(bMaxX, Math.max(Math.max(x0, x1), Math.max(x2, x3)));
                                        bMaxY = Math.max(bMaxY, Math.max(Math.max(y0, y1), Math.max(y2, y3)));
                                        bMaxZ = Math.max(bMaxZ, Math.max(Math.max(z0, z1), Math.max(z2, z3)));

                                        gotPolygons = true;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    if (!gotPolygons) {
                        VoxelShape shape = state.getVisualShape(level, mpos, CollisionContext.empty());
                        if (shape.isEmpty()) {
                            shape = state.getShape(level, mpos);
                        }

                        List<AABB> aabbs = shape.toAabbs();
                        if (aabbs.isEmpty()) {
                            if (state.isSolidRender(level, mpos) || !state.isAir()) {
                                obstacleBoxes.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
                            }
                        } else {
                            for (int i = 0; i < aabbs.size(); i++) {
                                AABB box = aabbs.get(i);
                                obstacleBoxes.add(new AABB(
                                        x + box.minX, y + box.minY, z + box.minZ,
                                        x + box.maxX, y + box.maxY, z + box.maxZ));
                            }
                        }
                    }
                }
            }
        }

        boxCount = obstacleBoxes.size();
        if (triangleCount > 0) {
            combinedBounds = new AABB(bMinX - 0.1, bMinY - 0.1, bMinZ - 0.1, bMaxX + 0.1, bMaxY + 0.1, bMaxZ + 0.1);
        } else {
            combinedBounds = null;
        }
    }

    private static double intersectRayTriangle(double ox, double oy, double oz,
                                              double rdx, double rdy, double rdz,
                                              float x0, float y0, float z0,
                                              float x1, float y1, float z1,
                                              float x2, float y2, float z2,
                                              double maxDist) {
        double e1x = x1 - x0, e1y = y1 - y0, e1z = z1 - z0;
        double e2x = x2 - x0, e2y = y2 - y0, e2z = z2 - z0;
        double hx = rdy * e2z - rdz * e2y;
        double hy = rdz * e1x - rdx * e2z;
        double hz = rdx * e2y - rdy * e2x;
        double a = e1x * hx + e1y * hy + e1z * hz;
        if (Math.abs(a) < 1e-7) return -1.0;
        double f = 1.0 / a;
        double sx = ox - x0, sy = oy - y0, sz = oz - z0;
        double u = f * (sx * hx + sy * hy + sz * hz);
        if (u < 0.0 || u > 1.0) return -1.0;
        double qx = sy * e1z - sz * e1y;
        double qy = sz * e1x - sx * e1z;
        double qz = sx * e1y - sy * e1x;
        double v = f * (rdx * qx + rdy * qy + rdz * qz);
        if (v < 0.0 || u + v > 1.0) return -1.0;
        double t = f * (e2x * qx + e2y * qy + e2z * qz);
        return (t >= 0.01 && t <= maxDist) ? t : -1.0;
    }

    private static double intersectRayAABB(double ox, double oy, double oz,
                                           double invDx, double invDy, double invDz,
                                           double minX, double minY, double minZ,
                                           double maxX, double maxY, double maxZ,
                                           double maxDist) {
        double t1 = (minX - ox) * invDx;
        double t2 = (maxX - ox) * invDx;
        double t3 = (minY - oy) * invDy;
        double t4 = (maxY - oy) * invDy;
        double t5 = (minZ - oz) * invDz;
        double t6 = (maxZ - oz) * invDz;

        double tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
        double tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));

        if (tmax < 0 || tmin > tmax) {
            return -1.0;
        }
        return (tmin >= 0.01 && tmin <= maxDist) ? tmin : -1.0;
    }
}
