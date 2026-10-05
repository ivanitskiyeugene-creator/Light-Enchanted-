package dev.lightenchanted.client.raytrace;

import dev.lightenchanted.beam.BeamShape;
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

import java.util.*;

/**
 * STR 2.0 (Simple Ray Tracing Engine):
 *
 * Micro-raytracer that shoots a dense 3D array of individual light rays (photons)
 * through the world. Every single ray marches through space:
 * - If unobstructed: travels all the way to the floor / target.
 * - If it hits an obstacle (grate bar, fan blade, wall, pillar): STOPS IMMEDIATELY
 *   at the exact 3D point of impact!
 *
 * This produces real volumetric light shafts in the air and genuine pixel-accurate
 * shadows behind obstacles without any 2D approximations.
 */
public class RayTraceField {
    public static final int RINGS = 8;
    public static final int RAYS_PER_RING = 24;
    public static final int TOTAL_RAYS = 1 + RINGS * RAYS_PER_RING; // 193 high-density rays

    public static class Ray {
        public float localX, localZ;       // Lens offset relative to center
        public float targetLocalX, targetLocalZ; // Far-end offset relative to center
        public float length;               // Distance where this ray terminated
        public double impactX, impactY, impactZ; // 3D world impact position
        public boolean hitSolid;           // Whether the ray hit an obstacle
        public float intensity;            // Gaussian radial brightness factor
    }

    private final Ray[] rays = new Ray[TOTAL_RAYS];
    private final List<Triangle> obstacleTriangles = new ArrayList<>();
    private final List<AABB> obstacleBoxes = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create(42L);

    public static class Triangle {
        public float x0, y0, z0;
        public float x1, y1, z1;
        public float x2, y2, z2;

        public Triangle(float x0, float y0, float z0,
                        float x1, float y1, float z1,
                        float x2, float y2, float z2) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
        }
    }

    public RayTraceField() {
        // Initialize ray array
        for (int i = 0; i < TOTAL_RAYS; i++) {
            rays[i] = new Ray();
        }
    }

    public Ray[] getRays() {
        return rays;
    }

    /**
     * Executes STR 2.0 raymarching for all rays in the light beam.
     */
    public void trace(Level level, BlockPos emitterPos, Vec3 origin, Vec3 dir,
                      float width, float endWidth, float maxDist, BeamShape shape, boolean shadows) {
        // Setup perpendicular basis vectors
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 actualUp = right.cross(dir).normalize();

        float r0 = Math.max(0.02f, width * 0.5f);
        float r1 = shape == BeamShape.CONE ? Math.max(0.05f, endWidth * 0.5f) : r0;

        // Collect obstacles in the beam frustum if shadows are enabled
        if (shadows) {
            collectObstacles(level, emitterPos, origin, dir, Math.max(r0, r1), Math.min(16.0f, maxDist));
        } else {
            obstacleTriangles.clear();
            obstacleBoxes.clear();
        }

        int triCount = obstacleTriangles.size();
        int boxCount = obstacleBoxes.size();

        int rayIdx = 0;

        // Center core ray (index 0)
        setupRay(rays[rayIdx++], 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);

        // Concentric circular distribution of ray filaments
        for (int ring = 1; ring <= RINGS; ring++) {
            float frac = (float) ring / RINGS;
            float ringR0 = r0 * frac;
            float ringR1 = r1 * frac;
            float ringIntensity = (float) Math.exp(-2.8 * frac * frac); // Gaussian falloff

            for (int r = 0; r < RAYS_PER_RING; r++) {
                float angle = (r * (float) (Math.PI * 2.0) / RAYS_PER_RING) + (ring * 0.35f);
                float c = Mth.cos(angle);
                float s = Mth.sin(angle);

                float lx0 = ringR0 * c;
                float lz0 = ringR0 * s;
                float lx1 = ringR1 * c;
                float lz1 = ringR1 * s;

                setupRay(rays[rayIdx++], lx0, lz0, lx1, lz1, ringIntensity);
            }
        }

        double ox = origin.x, oy = origin.y, oz = origin.z;

        // March each individual ray through the world!
        for (int i = 0; i < TOTAL_RAYS; i++) {
            Ray ray = rays[i];

            // Ray start in world space
            Vec3 rayStart = origin.add(right.scale(ray.localX)).add(actualUp.scale(ray.localZ));
            // Ray target in world space
            Vec3 rayEndTarget = origin.add(dir.scale(maxDist))
                    .add(right.scale(ray.targetLocalX))
                    .add(actualUp.scale(ray.targetLocalZ));

            Vec3 rayDir = rayEndTarget.subtract(rayStart).normalize();
            double rdx = rayDir.x, rdy = rayDir.y, rdz = rayDir.z;

            double rayMaxDist = maxDist;
            double hitDist = rayMaxDist;
            boolean solidHit = false;

            // 1. Raycast against terrain blocks & solid walls (pillars, ceilings, floors)
            BlockHitResult terrainHit = level.clip(new ClipContext(
                    rayStart, rayEndTarget, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));

            if (terrainHit.getType() != HitResult.Type.MISS) {
                double d = rayStart.distanceTo(terrainHit.getLocation());
                if (d > 0.05 && d < hitDist) {
                    hitDist = d;
                    solidHit = true;
                }
            }

            // 2. Raycast against 3D model triangles (grate bars, fan blades, meshes)
            if (shadows && triCount > 0) {
                for (int t = 0; t < triCount; t++) {
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

            // 3. Raycast against detailed obstacle boxes
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

            // Set final ray termination length and impact coordinate
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
        obstacleTriangles.clear();
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

                    BlockState state = level.getBlockState(mpos);
                    if (state.isAir() || state.is(Blocks.LIGHT) || state.canBeReplaced()) {
                        continue;
                    }

                    // Extract exact 3D model polygons from BakedModel
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

                            if (!quads.isEmpty() && quads.size() <= 4096) {
                                for (int qi = 0; qi < quads.size(); qi++) {
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

                                        obstacleTriangles.add(new Triangle(x0, y0, z0, x1, y1, z1, x2, y2, z2));
                                        obstacleTriangles.add(new Triangle(x0, y0, z0, x2, y2, z2, x3, y3, z3));
                                        gotPolygons = true;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }

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
