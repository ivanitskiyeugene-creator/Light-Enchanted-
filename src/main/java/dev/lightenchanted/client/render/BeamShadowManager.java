package dev.lightenchanted.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

/**
 * Micro-raytracer that scans obstacle geometry in front of the emitter
 * (gratings, 3D models, fences, bars, stairs, custom modded blocks) and
 * generates a soft 2D shadow transmission mask (GOBO / Light Cookie).
 *
 * The mask is uploaded to a {@link DynamicTexture} and cached per emitter.
 * Volumetric god-rays and ground-projected shadows use this texture to
 * produce cinema-quality light shafts cutting through gratings and meshes.
 */
public class BeamShadowManager {
    private static final int MASK_SIZE = 64;
    private static final float SCAN_DIST = 16.0f;
    private static final Map<BlockPos, CacheEntry> CACHE = new HashMap<>();

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

    /**
     * Retrieves or builds the shadow mask texture for the given emitter.
     * Returns {@code null} if shadows are disabled or no obstacles intersect the beam.
     */
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

        // Check if rebuild needed (every 20 ticks or when direction changes)
        boolean dirChanged = entry.lastDir == null || entry.lastDir.distanceToSqr(beamDir) > 0.001;
        boolean timeExpired = currentTick - entry.lastCheckTick > 20L;

        if (dirChanged || timeExpired) {
            entry.lastDir = beamDir;
            entry.lastCheckTick = currentTick;
            bakeShadowMask(level, emitterPos, origin, beamDir, spreadRadius, entry);
        }

        return entry.hasObstacles ? entry.location : null;
    }

    /** Scans obstacles and renders the 2D transmission mask. */
    private static void bakeShadowMask(Level level, BlockPos emitterPos, Vec3 origin,
                                       Vec3 dir, float spreadRadius, CacheEntry entry) {
        // Collect obstacle AABBs in the beam frustum
        List<AABB> obstacles = collectObstacles(level, emitterPos, origin, dir, SCAN_DIST);

        int hash = obstacles.hashCode();
        if (hash == entry.lastObstacleHash && entry.lastDir != null) {
            return; // No obstacle change
        }
        entry.lastObstacleHash = hash;

        if (obstacles.isEmpty()) {
            entry.hasObstacles = false;
            return;
        }

        entry.hasObstacles = true;

        // Basis vectors perpendicular to the beam direction
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 actualUp = right.cross(dir).normalize();

        float tanFov = Math.max(0.15f, spreadRadius * 0.40f);

        float[][] rawMask = new float[MASK_SIZE][MASK_SIZE];
        double ox = origin.x, oy = origin.y, oz = origin.z;

        // Micro-raycast 64x64 grid
        for (int py = 0; py < MASK_SIZE; py++) {
            float v = (py + 0.5f) / MASK_SIZE * 2.0f - 1.0f;
            for (int px = 0; px < MASK_SIZE; px++) {
                float u = (px + 0.5f) / MASK_SIZE * 2.0f - 1.0f;
                float rDist = (float) Math.sqrt(u * u + v * v);

                if (rDist > 1.0f) {
                    rawMask[py][px] = 0.0f; // Outside beam circle
                    continue;
                }

                // Ray direction through this pixel
                Vec3 rayDir = dir.add(right.scale(u * tanFov)).add(actualUp.scale(v * tanFov)).normalize();
                double rdx = rayDir.x, rdy = rayDir.y, rdz = rayDir.z;
                double invDx = Math.abs(rdx) > 1e-6 ? 1.0 / rdx : 1e6;
                double invDy = Math.abs(rdy) > 1e-6 ? 1.0 / rdy : 1e6;
                double invDz = Math.abs(rdz) > 1e-6 ? 1.0 / rdz : 1e6;

                boolean blocked = false;
                for (int i = 0; i < obstacles.size(); i++) {
                    AABB box = obstacles.get(i);
                    if (intersectRayAABB(ox, oy, oz, invDx, invDy, invDz,
                            box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, SCAN_DIST)) {
                        blocked = true;
                        break;
                    }
                }

                // Smooth radial vignette falloff at beam edges
                float edgeFalloff = 1.0f - smoothstep(0.85f, 1.0f, rDist);
                rawMask[py][px] = blocked ? 0.0f : edgeFalloff;
            }
        }

        // Apply 3x3 box blur for soft penumbra shadow edges
        NativeImage img = entry.image;
        for (int py = 0; py < MASK_SIZE; py++) {
            for (int px = 0; px < MASK_SIZE; px++) {
                float sum = 0.0f;
                int count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = py + dy;
                    if (ny < 0 || ny >= MASK_SIZE) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = px + dx;
                        if (nx < 0 || nx >= MASK_SIZE) continue;
                        sum += rawMask[ny][nx];
                        count++;
                    }
                }
                float val = count > 0 ? sum / count : rawMask[py][px];
                int a = (int) (clamp(val) * 255.0f);
                // NativeImage stores ABGR / RGBA
                int color = (a << 24) | 0x00FFFFFF;
                img.setPixelRGBA(px, py, color);
            }
        }

        entry.texture.upload();
    }

    /** Collects all obstacle bounding boxes in the path of the beam. */
    private static List<AABB> collectObstacles(Level level, BlockPos emitterPos, Vec3 origin,
                                              Vec3 dir, float maxDist) {
        List<AABB> list = new ArrayList<>();

        Vec3 end = origin.add(dir.scale(maxDist));
        int minX = (int) Math.floor(Math.min(origin.x, end.x) - 2);
        int maxX = (int) Math.ceil(Math.max(origin.x, end.x) + 2);
        int minY = (int) Math.floor(Math.min(origin.y, end.y) - 2);
        int maxY = (int) Math.ceil(Math.max(origin.y, end.y) + 2);
        int minZ = (int) Math.floor(Math.min(origin.z, end.z) - 2);
        int maxZ = (int) Math.ceil(Math.max(origin.z, end.z) + 2);

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    mpos.set(x, y, z);
                    if (mpos.equals(emitterPos)) {
                        continue; // Skip the emitter itself
                    }

                    BlockState state = level.getBlockState(mpos);
                    if (state.isAir() || state.is(Blocks.LIGHT) || state.canBeReplaced()) {
                        continue;
                    }

                    // Extract detailed visual voxel shapes (e.g. iron bars, fences, chains)
                    VoxelShape shape = state.getVisualShape(level, mpos, CollisionContext.empty());
                    if (shape.isEmpty()) {
                        shape = state.getShape(level, mpos);
                    }

                    List<AABB> aabbs = shape.toAabbs();
                    if (aabbs.isEmpty()) {
                        if (state.isSolidRender(level, mpos) || !state.isAir()) {
                            list.add(new AABB(x, y, z, x + 1, y + 1, z + 1));
                        }
                    } else {
                        for (int i = 0; i < aabbs.size(); i++) {
                            AABB box = aabbs.get(i);
                            list.add(new AABB(
                                    x + box.minX, y + box.minY, z + box.minZ,
                                    x + box.maxX, y + box.maxY, z + box.maxZ));
                        }
                    }
                }
            }
        }
        return list;
    }

    /** Fast ray-AABB intersection test. */
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
        return tmin >= 0.08 && tmin <= maxDist;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    /** Clears texture memory when switching worlds. */
    public static void clearAll() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (CacheEntry ce : CACHE.values()) {
            ce.close(tm);
        }
        CACHE.clear();
    }
}
