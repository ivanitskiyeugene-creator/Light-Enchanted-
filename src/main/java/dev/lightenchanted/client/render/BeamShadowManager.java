package dev.lightenchanted.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.model.data.ModelData;

import java.util.*;

/**
 * Photorealistic obstacle shadow raytracer with 5x5 Gaussian PCF penumbra filtering.
 *
 * Extracts exact 3D polygon triangles and AABBs from ALL blocks (vanilla and modded:
 * gratings, ventilation fans, catwalks, fences, bars, stairs, custom 3D models).
 */
public class BeamShadowManager {
    private static final int MASK_SIZE = 64;
    private static final float SCAN_DIST = 16.0f;
    private static final Map<BlockPos, CacheEntry> CACHE = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create(42L);

    // 5x5 Gaussian kernel weights for realistic optical penumbra
    private static final float[][] GAUSS_5X5 = {
        { 0.0037f, 0.0147f, 0.0256f, 0.0147f, 0.0037f },
        { 0.0147f, 0.0586f, 0.0952f, 0.0586f, 0.0147f },
        { 0.0256f, 0.0952f, 0.1502f, 0.0952f, 0.0256f },
        { 0.0147f, 0.0586f, 0.0952f, 0.0586f, 0.0147f },
        { 0.0037f, 0.0147f, 0.0256f, 0.0147f, 0.0037f }
    };

    public static class Triangle {
        public final float x0, y0, z0;
        public final float x1, y1, z1;
        public final float x2, y2, z2;

        public Triangle(float x0, float y0, float z0,
                        float x1, float y1, float z1,
                        float x2, float y2, float z2) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
        }
    }

    private static class ObstacleData {
        final List<Triangle> triangles = new ArrayList<>();
        final List<AABB> boxes = new ArrayList<>();
        int hash;

        boolean isEmpty() {
            return triangles.isEmpty() && boxes.isEmpty();
        }
    }

    private static class CacheEntry {
        DynamicTexture texture;
        ResourceLocation location;
        NativeImage image;
        Vec3 lastDir;
        long lastCheckTick;
        int lastObstacleHash;
        boolean hasObstacles;

        void close(TextureManager tm) {
            if (location != null) {
                tm.release(location);
            }
            if (texture != null) {
                texture.close();
            }
            if (image != null) {
                image.close();
            }
        }
    }

    public static ResourceLocation getShadowTexture(LightEmitterBlockEntity be, Vec3 origin,
                                                   Vec3 beamDir, float spreadRadius) {
        BeamConfig cfg = be.getConfig();
        if (!cfg.shadows) {
            return null;
        }

        Level level = be.getLevel();
        if (level == null) {
            return null;
        }

        BlockPos emitterPos = be.getBlockPos();
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        long currentTick = level.getGameTime();

        CacheEntry entry = CACHE.computeIfAbsent(emitterPos, pos -> {
            CacheEntry ce = new CacheEntry();
            ce.image = new NativeImage(MASK_SIZE, MASK_SIZE, true);
            ce.texture = new DynamicTexture(ce.image);
            ce.location = tm.register(
                    "lightenchanted_shadow_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
                    ce.texture);
            return ce;
        });

        boolean dirChanged = entry.lastDir == null || entry.lastDir.distanceToSqr(beamDir) > 0.0002;
        boolean timeExpired = currentTick - entry.lastCheckTick > 10L;

        if (dirChanged || timeExpired) {
            entry.lastDir = beamDir;
            entry.lastCheckTick = currentTick;
            bakeShadowMask(level, emitterPos, origin, beamDir, spreadRadius, entry);
        }

        return entry.hasObstacles ? entry.location : null;
    }

    private static void bakeShadowMask(Level level, BlockPos emitterPos, Vec3 origin,
                                       Vec3 dir, float spreadRadius, CacheEntry entry) {
        ObstacleData obstacles = collectObstacles(level, emitterPos, origin, dir, spreadRadius, SCAN_DIST);

        if (obstacles.hash == entry.lastObstacleHash && entry.lastDir != null && entry.hasObstacles) {
            return;
        }
        entry.lastObstacleHash = obstacles.hash;

        if (obstacles.isEmpty()) {
            entry.hasObstacles = false;
            return;
        }

        entry.hasObstacles = true;

        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 actualUp = right.cross(dir).normalize();

        float tanFov = Math.max(0.18f, spreadRadius * 0.40f);

        float[][] rawMask = new float[MASK_SIZE][MASK_SIZE];
        double ox = origin.x, oy = origin.y, oz = origin.z;

        List<Triangle> tris = obstacles.triangles;
        List<AABB> boxes = obstacles.boxes;
        int triCount = tris.size();
        int boxCount = boxes.size();

        // Micro-raycast 64x64 grid
        for (int py = 0; py < MASK_SIZE; py++) {
            float v = (py + 0.5f) / MASK_SIZE * 2.0f - 1.0f;
            for (int px = 0; px < MASK_SIZE; px++) {
                float u = (px + 0.5f) / MASK_SIZE * 2.0f - 1.0f;
                float rDist = (float) Math.sqrt(u * u + v * v);

                if (rDist > 1.0f) {
                    rawMask[py][px] = 0.0f;
                    continue;
                }

                Vec3 rayDir = dir.add(right.scale(u * tanFov)).add(actualUp.scale(v * tanFov)).normalize();
                double rdx = rayDir.x, rdy = rayDir.y, rdz = rayDir.z;

                boolean blocked = false;

                // 1. Exact 3D model polygons
                for (int i = 0; i < triCount; i++) {
                    Triangle t = tris.get(i);
                    if (intersectRayTriangle(ox, oy, oz, rdx, rdy, rdz,
                            t.x0, t.y0, t.z0, t.x1, t.y1, t.z1, t.x2, t.y2, t.z2, SCAN_DIST)) {
                        blocked = true;
                        break;
                    }
                }

                // 2. AABB boxes
                if (!blocked && boxCount > 0) {
                    double invDx = Math.abs(rdx) > 1e-6 ? 1.0 / rdx : 1e6;
                    double invDy = Math.abs(rdy) > 1e-6 ? 1.0 / rdy : 1e6;
                    double invDz = Math.abs(rdz) > 1e-6 ? 1.0 / rdz : 1e6;

                    for (int i = 0; i < boxCount; i++) {
                        AABB b = boxes.get(i);
                        if (intersectRayAABB(ox, oy, oz, invDx, invDy, invDz,
                                b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, SCAN_DIST)) {
                            blocked = true;
                            break;
                        }
                    }
                }

                float edgeFalloff = 1.0f - smoothstep(0.85f, 1.0f, rDist);
                rawMask[py][px] = blocked ? 0.0f : edgeFalloff;
            }
        }

        // Apply 5x5 Gaussian PCF filter for soft, cinematic optical penumbra
        NativeImage img = entry.image;
        for (int py = 0; py < MASK_SIZE; py++) {
            for (int px = 0; px < MASK_SIZE; px++) {
                float sum = 0.0f;
                float weightSum = 0.0f;
                for (int dy = -2; dy <= 2; dy++) {
                    int ny = py + dy;
                    if (ny < 0 || ny >= MASK_SIZE) continue;
                    for (int dx = -2; dx <= 2; dx++) {
                        int nx = px + dx;
                        if (nx < 0 || nx >= MASK_SIZE) continue;
                        float w = GAUSS_5X5[dy + 2][dx + 2];
                        sum += rawMask[ny][nx] * w;
                        weightSum += w;
                    }
                }
                float val = weightSum > 0 ? sum / weightSum : rawMask[py][px];
                int a = (int) (clamp(val) * 255.0f);
                int color = (a << 24) | 0x00FFFFFF;
                img.setPixelRGBA(px, py, color);
            }
        }

        entry.texture.upload();
    }

    private static ObstacleData collectObstacles(Level level, BlockPos emitterPos, Vec3 origin,
                                                 Vec3 dir, float spreadRadius, float maxDist) {
        ObstacleData data = new ObstacleData();
        BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();

        Vec3 end = origin.add(dir.scale(maxDist));
        int margin = (int) Math.ceil(Math.max(spreadRadius * 0.5f + 2.0f, 4.0f));
        int minX = (int) Math.floor(Math.min(origin.x, end.x) - margin);
        int maxX = (int) Math.ceil(Math.max(origin.x, end.x) + margin);
        int minY = (int) Math.floor(Math.min(origin.y, end.y) - margin);
        int maxY = (int) Math.ceil(Math.max(origin.y, end.y) + margin);
        int minZ = (int) Math.floor(Math.min(origin.z, end.z) - margin);
        int maxZ = (int) Math.ceil(Math.max(origin.z, end.z) + margin);

        int hashAcc = 17;
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

                    hashAcc = hashAcc * 31 + state.hashCode() + mpos.hashCode();

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
                            for (RenderType rt : RenderType.chunkBufferLayers()) {
                                try {
                                    List<BakedQuad> layerQuads = model.getQuads(state, null, RANDOM, ModelData.EMPTY, rt);
                                    if (layerQuads != null && !layerQuads.isEmpty()) {
                                        quads.addAll(layerQuads);
                                    }
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

                                        data.triangles.add(new Triangle(x0, y0, z0, x1, y1, z1, x2, y2, z2));
                                        data.triangles.add(new Triangle(x0, y0, z0, x2, y2, z2, x3, y3, z3));
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
                                data.boxes.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
                            }
                        } else {
                            for (int i = 0; i < aabbs.size(); i++) {
                                AABB box = aabbs.get(i);
                                data.boxes.add(new AABB(
                                        x + box.minX, y + box.minY, z + box.minZ,
                                        x + box.maxX, y + box.maxY, z + box.maxZ));
                            }
                        }
                    }
                }
            }
        }

        data.hash = hashAcc;
        return data;
    }

    private static boolean intersectRayTriangle(double ox, double oy, double oz,
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
        if (Math.abs(a) < 1e-7) return false;
        double f = 1.0 / a;
        double sx = ox - x0, sy = oy - y0, sz = oz - z0;
        double u = f * (sx * hx + sy * hy + sz * hz);
        if (u < 0.0 || u > 1.0) return false;
        double qx = sy * e1z - sz * e1y;
        double qy = sz * e1x - sx * e1z;
        double qz = sx * e1y - sy * e1x;
        double v = f * (rdx * qx + rdy * qy + rdz * qz);
        if (v < 0.0 || u + v > 1.0) return false;
        double t = f * (e2x * qx + e2y * qy + e2z * qz);
        return t >= 0.01 && t <= maxDist;
    }

    private static boolean intersectRayAABB(double ox, double oy, double oz,
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
            return false;
        }
        return tmin >= 0.01 && tmin <= maxDist;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    public static void clearAll() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (CacheEntry ce : CACHE.values()) {
            ce.close(tm);
        }
        CACHE.clear();
    }
}
