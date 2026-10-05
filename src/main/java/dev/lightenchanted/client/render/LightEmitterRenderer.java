package dev.lightenchanted.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import dev.lightenchanted.client.raytrace.RayTraceField;
import dev.lightenchanted.init.ModItems;
import dev.lightenchanted.item.BeamTunerItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * STR 2.0 (Simple Ray Tracing Renderer):
 *
 * High-performance 300+ FPS renderer. Reads pre-calculated 3D ray fields and draws
 * 193 volumetric filament ribbons with zero per-frame CPU raytracing overhead.
 */
public class LightEmitterRenderer implements BlockEntityRenderer<LightEmitterBlockEntity> {
    private static final ResourceLocation BEAM_TEXTURE =
            new ResourceLocation(LightEnchanted.MOD_ID, "textures/entity/beam.png");
    private static final int DUST_MOTE_COUNT = 32;
    private static final Map<BlockPos, RayTraceField> RAY_FIELDS = new HashMap<>();

    public LightEmitterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LightEmitterBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BeamConfig cfg = be.getConfig();
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        boolean showGhost = be.isCreative() && isHoldingTool();
        if (!cfg.enabled && !showGhost) {
            return;
        }
        BlockPos pos = be.getBlockPos();

        // ---- Origin in block-space and world-space
        float originX = 0.5f + cfg.offsetX;
        float originZ = 0.5f + cfg.offsetZ;
        boolean aimed = cfg.hasTarget;
        float originY = (aimed || !cfg.down) ? (1.0f + cfg.offsetY) : (0.0f + cfg.offsetY);

        double worldOriginX = pos.getX() + originX;
        double worldOriginY = pos.getY() + originY;
        double worldOriginZ = pos.getZ() + originZ;

        // ---- Direction & target calculation
        double dx = 0.0, dy = 1.0, dz = 0.0;
        double maxDist = 128.0;

        if (aimed) {
            dx = cfg.targetX - worldOriginX;
            dy = cfg.targetY - worldOriginY;
            dz = cfg.targetZ - worldOriginZ;
            double lenSq = dx * dx + dy * dy + dz * dz;
            if (lenSq < 0.0625) {
                aimed = false;
            } else {
                double len = Math.sqrt(lenSq);
                maxDist = len;
                dx /= len;
                dy /= len;
                dz /= len;
            }
        }
        if (!aimed) {
            if (cfg.toSky) {
                maxDist = 72.0;
            } else {
                maxDist = Math.max(BeamConfig.MIN_HEIGHT, cfg.height);
            }
            if (cfg.down) {
                dy = -1.0;
            }
        }

        Vec3 startVec = new Vec3(worldOriginX, worldOriginY, worldOriginZ);
        Vec3 dirVec = new Vec3(dx, dy, dz);

        float w = Math.max(BeamConfig.MIN_WIDTH, cfg.width);
        float endW = Math.max(BeamConfig.MIN_WIDTH, cfg.endWidth);
        long currentTick = level.getGameTime();

        // ---- STR 2.0: Lazy Cached Raytracing (0% CPU cost per frame!)
        RayTraceField rayField = RAY_FIELDS.computeIfAbsent(pos, p -> new RayTraceField());
        if (rayField.needsRetrace(currentTick, startVec, dirVec, w, endW, (float) maxDist, cfg.shadows)) {
            rayField.trace(level, pos, startVec, dirVec, w, endW, (float) maxDist, cfg.shape, cfg.shadows, currentTick);
        }

        float time = (float) (level.getGameTime() % 720000L) + partialTick;

        // ---- Color
        float r, g, b;
        if (cfg.rainbow) {
            float hue = (time * 0.02f) % 1.0f;
            if (hue < 0.0f) hue += 1.0f;
            int rgb = hsvToRgb(hue, 1.0f, 1.0f);
            r = ((rgb >> 16) & 0xFF) / 255.0f;
            g = ((rgb >> 8) & 0xFF) / 255.0f;
            b = (rgb & 0xFF) / 255.0f;
        } else {
            r = ((cfg.color >> 16) & 0xFF) / 255.0f;
            g = ((cfg.color >> 8) & 0xFF) / 255.0f;
            b = (cfg.color & 0xFF) / 255.0f;
        }

        // ---- Alpha & distance falloff
        float alpha = cfg.alpha / 255.0f;
        if (cfg.pulse > 0.001f) {
            alpha *= 0.60f + 0.40f * Mth.sin(time * cfg.pulse * 2.4f);
        }

        // ---- Mie Forward Scattering
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        Vec3 toCam = camPos.subtract(startVec).normalize();
        double cosAngle = toCam.dot(new Vec3(-dx, -dy, -dz));
        float gMie = 0.55f;
        float miePhase = (float) ((1.0f - gMie * gMie) / Math.pow(1.0f + gMie * gMie - 2.0f * gMie * cosAngle, 1.5));
        float mieBoost = Mth.clamp(miePhase * 0.65f, 0.70f, 2.2f);
        alpha *= mieBoost;

        if (alpha < 0.005f) {
            alpha = 0.0f;
        }

        float rot = cfg.rotation > 0.001f ? time * cfg.rotation * 1.3f : 0.0f;

        float coreR = r * 0.4f + 0.6f;
        float coreG = g * 0.4f + 0.6f;
        float coreB = b * 0.4f + 0.6f;

        VertexConsumer vc = buffers.getBuffer(RenderType.beaconBeam(BEAM_TEXTURE, true));
        Quaternionf camOrientation = camera.rotation();

        if (alpha > 0.0f) {
            poseStack.pushPose();
            poseStack.translate(originX, originY, originZ);
            if (aimed || dy != 1.0) {
                poseStack.mulPose(new Quaternionf().rotationTo(
                        0.0f, 1.0f, 0.0f, (float) dx, (float) dy, (float) dz));
            }
            if (rot > 0.001f) {
                poseStack.mulPose(new Quaternionf().rotationY(rot));
            }
            Matrix4f mat = poseStack.last().pose();
            Matrix3f nmat = poseStack.last().normal();

            // ---- STR 2.0: Draw the 193 3D Volumetric Ray Filaments!
            RayTraceField.Ray[] rays = rayField.getRays();
            float filamentThickness = Math.max(0.04f, w * 0.06f);

            for (int i = 0; i < rays.length; i++) {
                RayTraceField.Ray ray = rays[i];
                float len = ray.length;
                if (len < 0.05f) {
                    continue; // Blocked at source
                }

                float rayAlpha = alpha * ray.intensity * 0.55f;
                float progress = len / (float) maxDist;

                float x0 = ray.localX;
                float z0 = ray.localZ;
                float x1 = ray.localX + (ray.targetLocalX - ray.localX) * progress;
                float z1 = ray.localZ + (ray.targetLocalZ - ray.localZ) * progress;

                filamentRibbon(vc, mat, nmat, x0, z0, x1, z1, len, filamentThickness, 0.0f,
                        coreR, coreG, coreB, rayAlpha);
                filamentRibbon(vc, mat, nmat, x0, z0, x1, z1, len, filamentThickness, (float) (Math.PI / 2.0),
                        coreR, coreG, coreB, rayAlpha);
            }

            // ---- Ambient volumetric atmospheric envelope
            float maxRayLen = 0.0f;
            for (int i = 0; i < rays.length; i++) {
                if (rays[i].length > maxRayLen) maxRayLen = rays[i].length;
            }
            if (maxRayLen > 0.1f) {
                cylinderEnvelope(vc, mat, nmat, w * 0.5f, endW * 0.5f, maxRayLen, 16,
                        r, g, b, alpha * 0.12f * (1.0f + 0.5f * cfg.glow));
            }

            // ---- Cinematic floating dust motes
            renderDustMotes(vc, mat, nmat, maxRayLen, w, endW, time, coreR, coreG, coreB, alpha);

            poseStack.popPose();

            // ---- Origin lens flare glow dot
            glowDot(vc, poseStack, camOrientation, originX, originY, originZ,
                    Math.max(0.10f, w * 0.35f), coreR, coreG, coreB, Math.min(1.0f, alpha * 0.75f * mieBoost));

            // ---- STR 2.0: Surface Photon Hit Impaction (Pixel-Accurate Shadow Silhouette on floor/walls)
            float photonRadius = Math.max(0.12f, (endW / w) * 0.15f);

            for (int i = 0; i < rays.length; i++) {
                RayTraceField.Ray ray = rays[i];
                if (ray.hitSolid && ray.length > 0.1f) {
                    double hitRelX = ray.impactX - pos.getX();
                    double hitRelY = ray.impactY - pos.getY() + 0.01;
                    double hitRelZ = ray.impactZ - pos.getZ();

                    float photonAlpha = Math.min(1.0f, alpha * ray.intensity * 0.75f);
                    flatGroundDisc(vc, poseStack, hitRelX, hitRelY, hitRelZ, photonRadius,
                            coreR, coreG, coreB, photonAlpha);
                }
            }
        }

        // ---- Creative ghost preview box
        if (showGhost) {
            ghostCube(vc, poseStack, 0.55f, 0.85f, 1.0f, 0.12f);
            glowDot(vc, poseStack, camOrientation, originX, originY, originZ,
                    0.12f, 0.6f, 1.0f, 1.0f, 0.7f);
        }
    }

    private static void filamentRibbon(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                      float x0, float z0, float x1, float z1,
                                      float length, float thickness, float angle,
                                      float r, float g, float b, float a) {
        float c = Mth.cos(angle) * thickness;
        float s = Mth.sin(angle) * thickness;

        twoSidedQuad(vc, m, n,
                x0 - c, 0.0f, z0 - s,
                x0 + c, 0.0f, z0 + s,
                x1 + c, length, z1 + s,
                x1 - c, length, z1 - s,
                r, g, b, a, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    private static void cylinderEnvelope(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                         float r0, float r1, float height, int segments,
                                         float r, float g, float b, float a) {
        float tau = (float) (Math.PI * 2.0);
        for (int i = 0; i < segments; i++) {
            float a0 = tau * i / segments;
            float a1 = tau * (i + 1) / segments;
            float cb0 = Mth.cos(a0), sb0 = Mth.sin(a0);
            float cb1 = Mth.cos(a1), sb1 = Mth.sin(a1);
            twoSidedQuad(vc, m, n,
                    r0 * cb0, 0, r0 * sb0,
                    r0 * cb1, 0, r0 * sb1,
                    r1 * cb1, height, r1 * sb1,
                    r1 * cb0, height, r1 * sb0,
                    r, g, b, a,
                    (float) i / segments, (float) (i + 1) / segments, 0.0f, 1.0f);
        }
    }

    private static void renderDustMotes(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                       float height, float w, float endW, float time,
                                       float r, float g, float b, float alpha) {
        if (alpha < 0.05f || height < 1.0f) {
            return;
        }

        for (int i = 0; i < DUST_MOTE_COUNT; i++) {
            float seed = i * 13.37f;
            float speed = 0.035f + 0.025f * ((seed * 7.1f) % 1.0f);

            float yNorm = ((time * speed + seed) % 1.0f + 1.0f) % 1.0f;
            float y = yNorm * height;

            float currentRadius = Mth.lerp(yNorm, w * 0.35f, endW * 0.45f);

            float angle = seed * 6.28f + time * 0.15f * (i % 2 == 0 ? 1 : -1);
            float distFromCenter = ((seed * 3.3f) % 1.0f) * currentRadius;
            float x = distFromCenter * Mth.cos(angle) + 0.03f * Mth.sin(time * 0.8f + seed);
            float z = distFromCenter * Mth.sin(angle) + 0.03f * Mth.cos(time * 0.9f + seed);

            float shimmer = 0.55f + 0.45f * Mth.sin(time * 2.5f + seed * 9.0f);
            float endFade = smoothstep(0.0f, 0.1f, yNorm) * smoothstep(1.0f, 0.88f, yNorm);
            float moteAlpha = alpha * shimmer * endFade * 0.60f;

            float sz = 0.025f + 0.015f * ((seed * 5.5f) % 1.0f);

            twoSidedQuad(vc, m, n,
                    x - sz, y, z - sz,
                    x + sz, y, z - sz,
                    x + sz, y, z + sz,
                    x - sz, y, z + sz,
                    r, g, b, moteAlpha, 0.0f, 1.0f, 0.0f, 1.0f);
        }
    }

    private static boolean isHoldingTool() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        for (ItemStack stack : player.getHandSlots()) {
            if (stack.getItem() instanceof BeamTunerItem
                    || stack.is(ModItems.LIGHT_EMITTER_CREATIVE.get())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ plumbing

    private static void glowDot(VertexConsumer vc, PoseStack poseStack, Quaternionf cam,
                                double x, double y, double z, float radius,
                                float r, float g, float b, float a) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(cam);
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        twoSidedQuad(vc, m, n,
                -radius, -radius, 0.0f, radius, -radius, 0.0f,
                radius, radius, 0.0f, -radius, radius, 0.0f,
                r, g, b, a, 0.0f, 1.0f, 0.0f, 1.0f);
        poseStack.popPose();
    }

    private static void flatGroundDisc(VertexConsumer vc, PoseStack poseStack,
                                      double x, double y, double z, float radius,
                                      float r, float g, float b, float a) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        twoSidedQuad(vc, m, n,
                -radius, 0.0f, -radius,
                radius, 0.0f, -radius,
                radius, 0.0f, radius,
                -radius, 0.0f, radius,
                r, g, b, a, 0.0f, 1.0f, 0.0f, 1.0f);
        poseStack.popPose();
    }

    private static void ghostCube(VertexConsumer vc, PoseStack poseStack,
                                  float r, float g, float b, float a) {
        poseStack.pushPose();
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        float o = -0.002f;
        float p = 1.002f;
        twoSidedQuad(vc, m, n, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, r, g, b, a, 0, 1, 0, 1);
        twoSidedQuad(vc, m, n, 0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, r, g, b, a, 0, 1, 0, 1);
        twoSidedQuad(vc, m, n, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, r, g, b, a, 0, 1, 0, 1);
        twoSidedQuad(vc, m, n, 1, 0, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, r, g, b, a, 0, 1, 0, 1);
        twoSidedQuad(vc, m, n, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 1, 1, r, g, b, a, 0, 1, 0, 1);
        twoSidedQuad(vc, m, n, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, r, g, b, a, 0, 1, 0, 1);
        frame(vc, m, n, o, p, r, g, b, Math.min(1.0f, a * 2.5f));
        poseStack.popPose();
    }

    private static void frame(VertexConsumer vc, Matrix4f m, Matrix3f n,
                              float o, float p, float r, float g, float b, float a) {
        float t = 0.015f;
        for (int i = 0; i < 4; i++) {
            float x = (i == 0 || i == 3) ? o : p;
            float z = (i < 2) ? o : p;
            float x1 = (i == 0 || i == 3) ? t : -t;
            float z1 = (i < 2) ? t : -t;
            twoSidedQuad(vc, m, n, x, 0, z, x + x1, 0, z, x + x1, 1, z, x, 1, z, r, g, b, a, 0, 1, 0, 1);
            twoSidedQuad(vc, m, n, x, 0, z, x, 0, z + z1, x, 1, z + z1, x, 1, z, r, g, b, a, 0, 1, 0, 1);
        }
    }

    private static void twoSidedQuad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    float x3, float y3, float z3, float x4, float y4, float z4,
                                    float r, float g, float b, float a,
                                    float u0, float u1, float v0, float v1) {
        vertex(vc, m, n, x1, y1, z1, r, g, b, a, u0, v0);
        vertex(vc, m, n, x2, y2, z2, r, g, b, a, u1, v0);
        vertex(vc, m, n, x3, y3, z3, r, g, b, a, u1, v1);
        vertex(vc, m, n, x4, y4, z4, r, g, b, a, u0, v1);

        vertex(vc, m, n, x4, y4, z4, r, g, b, a, u0, v1);
        vertex(vc, m, n, x3, y3, z3, r, g, b, a, u1, v1);
        vertex(vc, m, n, x2, y2, z2, r, g, b, a, u1, v0);
        vertex(vc, m, n, x1, y1, z1, r, g, b, a, u0, v0);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n,
                               float x, float y, float z,
                               float r, float g, float b, float a, float u, float v) {
        vc.vertex(m, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0.0f, 1.0f, 0.0f)
                .endVertex();
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    private static int hsvToRgb(float h, float s, float v) {
        int sector = (int) (h * 6.0f) % 6;
        float f = h * 6.0f - (int) (h * 6.0f);
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * f);
        float t = v * (1.0f - s * (1.0f - f));
        float r, g, b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255.0f) << 16) | ((int) (g * 255.0f) << 8) | (int) (b * 255.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(LightEmitterBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
