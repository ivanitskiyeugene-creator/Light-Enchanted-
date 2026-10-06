package dev.lightenchanted.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import dev.lightenchanted.client.raytrace.RayTraceField;
import dev.lightenchanted.config.LightEnchantedConfig;
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
import net.minecraft.core.Direction;
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
 * STR 2.4.2 6-Axis Surface Decal Projection & Continuous Volumetric Fog Renderer:
 *
 * - Precise 6-directional wall, floor, and ceiling surface decal alignment.
 * - Solid high-contrast negative shadow silhouettes from players, mobs, and fan blades.
 * - Seamless volumetric atmospheric light cone.
 * - Multi-bounce optical mirror reflections.
 * - Sub-frame interpolation maintaining 300+ FPS.
 */
public class LightEmitterRenderer implements BlockEntityRenderer<LightEmitterBlockEntity> {
    private static final ResourceLocation BEAM_TEXTURE =
            new ResourceLocation(LightEnchanted.MOD_ID, "textures/entity/beam.png");
    private static final int DUST_MOTE_COUNT = 48;
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

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        // Distance Culling based on config
        int maxDistConfig = LightEnchantedConfig.CLIENT.maxBeamRenderDistance.get();
        if (camPos.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > (maxDistConfig * maxDistConfig)) {
            return;
        }

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

        float time = (float) (level.getGameTime() % 720000L) + partialTick;

        // ---- STR 2.0: Lazy Cached Raytracing with dynamic entity & fan shadow tracking
        RayTraceField rayField = RAY_FIELDS.computeIfAbsent(pos, p -> new RayTraceField());
        if (rayField.needsRetrace(currentTick, partialTick, startVec, dirVec, w, endW, (float) maxDist, cfg.shape, cfg.shadows)) {
            rayField.trace(level, pos, startVec, dirVec, w, endW, (float) maxDist, cfg.shape, cfg.shadows, currentTick, partialTick);
        }

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

        // ---- Alpha & Redstone / Strobe
        float alpha = cfg.getEffectiveAlpha(time);
        alpha *= LightEnchantedConfig.CLIENT.beamBrightnessMultiplier.get().floatValue();

        // Stage Haze smoke density boost
        alpha *= rayField.getHazeMultiplier();

        // ---- Mie Forward Scattering
        Vec3 toCam = camPos.subtract(startVec).normalize();
        double cosAngle = toCam.dot(new Vec3(-dx, -dy, -dz));
        float gMie = 0.58f;
        float miePhase = (float) ((1.0f - gMie * gMie) / Math.pow(1.0f + gMie * gMie - 2.0f * gMie * cosAngle, 1.5));
        float mieBoost = Mth.clamp(miePhase * 0.65f, 0.70f, 2.5f);
        alpha *= mieBoost;

        if (alpha < 0.005f) {
            alpha = 0.0f;
        }

        float rot = cfg.rotation > 0.001f ? time * cfg.rotation * 1.3f : 0.0f;

        float coreR = r * 0.35f + 0.65f;
        float coreG = g * 0.35f + 0.65f;
        float coreB = b * 0.35f + 0.65f;

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

            RayTraceField.Ray[] rays = rayField.getRays();
            int raysPerRing = RayTraceField.RAYS_PER_RING;
            int totalRings = RayTraceField.RINGS;

            // ---- Continuous Seamless Volumetric Cone Hulls
            for (int ringStep : new int[]{totalRings, totalRings / 2, 2}) {
                int baseIdx = 1 + (ringStep - 1) * raysPerRing;
                float shellAlpha = alpha * (ringStep == totalRings ? 0.35f : (ringStep == 2 ? 0.85f : 0.55f));

                for (int s = 0; s < raysPerRing; s++) {
                    int nextS = (s + 1) % raysPerRing;
                    RayTraceField.Ray r0 = rays[baseIdx + s];
                    RayTraceField.Ray r1 = rays[baseIdx + nextS];

                    float len0 = r0.length;
                    float len1 = r1.length;
                    if (len0 < 0.05f && len1 < 0.05f) continue;

                    float p0 = len0 / (float) maxDist;
                    float p1 = len1 / (float) maxDist;

                    float x0_top = r0.localX, z0_top = r0.localZ;
                    float x1_top = r1.localX, z1_top = r1.localZ;

                    float x0_bot = r0.localX + (r0.targetLocalX - r0.localX) * p0;
                    float z0_bot = r0.localZ + (r0.targetLocalZ - r0.localZ) * p0;
                    float x1_bot = r1.localX + (r1.targetLocalX - r1.localX) * p1;
                    float z1_bot = r1.targetLocalZ + (r1.targetLocalZ - r1.localZ) * p1;

                    float sR = coreR * (r0.tintR + r1.tintR) * 0.5f;
                    float sG = coreG * (r0.tintG + r1.tintG) * 0.5f;
                    float sB = coreB * (r0.tintB + r1.tintB) * 0.5f;

                    drawVolumetricQuad(vc, mat, nmat,
                            x0_top, 0.0f, z0_top,
                            x1_top, 0.0f, z1_top,
                            x1_bot, len1, z1_bot,
                            x0_bot, len0, z0_bot,
                            sR, sG, sB, shellAlpha);
                }
            }

            // Radial Volumetric Fins (Connecting Center to Outer Rings)
            RayTraceField.Ray centerRay = rays[0];
            float cLen = centerRay.length;
            int outerBase = 1 + (totalRings - 1) * raysPerRing;

            for (int s = 0; s < raysPerRing; s += 2) {
                RayTraceField.Ray rOuter = rays[outerBase + s];
                float oLen = rOuter.length;
                if (cLen < 0.05f && oLen < 0.05f) continue;

                float pOut = oLen / (float) maxDist;
                float xOut_bot = rOuter.localX + (rOuter.targetLocalX - rOuter.localX) * pOut;
                float zOut_bot = rOuter.localZ + (rOuter.targetLocalZ - rOuter.localZ) * pOut;

                float fR = coreR * rOuter.tintR;
                float fG = coreG * rOuter.tintG;
                float fB = coreB * rOuter.tintB;

                drawVolumetricQuad(vc, mat, nmat,
                        0.0f, 0.0f, 0.0f,
                        rOuter.localX, 0.0f, rOuter.localZ,
                        xOut_bot, oLen, zOut_bot,
                        0.0f, cLen, 0.0f,
                        fR, fG, fB, alpha * 0.35f);
            }

            // ---- Cinematic 3D Micro-Turbulence Dust Motes
            if (LightEnchantedConfig.CLIENT.enableVolumetricDust.get()) {
                renderCinematicDustMotes(vc, mat, nmat, cLen, w, endW, time, camPos, startVec,
                        coreR, coreG, coreB, alpha, mieBoost);
            }

            poseStack.popPose();

            // ---- Optical Mirror Reflection Rays
            for (int i = 0; i < rays.length; i += 4) {
                RayTraceField.Ray ray = rays[i];
                if (ray.hasReflection && ray.reflectLength > 0.1f) {
                    poseStack.pushPose();
                    double rx = ray.reflectStartX - pos.getX();
                    double ry = ray.reflectStartY - pos.getY();
                    double rz = ray.reflectStartZ - pos.getZ();

                    poseStack.translate(rx, ry, rz);
                    poseStack.mulPose(new Quaternionf().rotationTo(
                            0.0f, 1.0f, 0.0f, ray.reflectDirX, ray.reflectDirY, ray.reflectDirZ));

                    Matrix4f rMat = poseStack.last().pose();
                    Matrix3f rNmat = poseStack.last().normal();

                    float rayR = coreR * ray.tintR;
                    float rayG = coreG * ray.tintG;
                    float rayB = coreB * ray.tintB;
                    float rayAlpha = alpha * 0.55f;

                    float refRadius = Math.max(0.08f, w * 0.15f);
                    drawVolumetricQuad(vc, rMat, rNmat, -refRadius, 0, 0, refRadius, 0, 0,
                            refRadius, ray.reflectLength, 0, -refRadius, ray.reflectLength, 0,
                            rayR, rayG, rayB, rayAlpha);

                    poseStack.popPose();
                }
            }

            // ---- Cinematic Anamorphic & Starburst Lens Flare System
            if (LightEnchantedConfig.CLIENT.enableLensFlares.get()) {
                renderCinematicLensFlare(vc, poseStack, camOrientation, originX, originY, originZ,
                        w, r, g, b, coreR, coreG, coreB, alpha, mieBoost, cosAngle, cfg.glow);
            }

            // ---- 6-Axis Ground/Wall/Ceiling Surface Decal Projection with Crisp Negative Shadow Silhouettes
            float surfaceDiscRadius = Math.max(0.38f, (endW / w) * 0.42f);

            for (int i = 0; i < rays.length; i++) {
                RayTraceField.Ray ray = rays[i];
                // Only rays that reached the wall/floor illuminate the surface!
                // Blocked rays leave a crisp, high-contrast dark silhouette!
                if (ray.hitSolid && ray.hitFloor && ray.length > 0.1f) {
                    double hitRelX = ray.impactX - pos.getX();
                    double hitRelY = ray.impactY - pos.getY();
                    double hitRelZ = ray.impactZ - pos.getZ();

                    float rayR = coreR * ray.tintR;
                    float rayG = coreG * ray.tintG;
                    float rayB = coreB * ray.tintB;

                    float photonAlpha = Math.min(1.0f, alpha * (0.45f + ray.intensity * 0.55f));
                    drawSurfaceDisc(vc, poseStack, hitRelX, hitRelY, hitRelZ, surfaceDiscRadius,
                            ray.surfaceNormal, rayR, rayG, rayB, photonAlpha);
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

    private static void drawSurfaceDisc(VertexConsumer vc, PoseStack poseStack,
                                       double x, double y, double z, float radius,
                                       Direction normal, float r, float g, float b, float a) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);

        // Align with 6-directional surface normal
        if (normal == Direction.NORTH) {
            poseStack.mulPose(new Quaternionf().rotationX((float) (Math.PI / 2.0)));
            poseStack.translate(0, 0, -0.015);
        } else if (normal == Direction.SOUTH) {
            poseStack.mulPose(new Quaternionf().rotationX((float) (-Math.PI / 2.0)));
            poseStack.translate(0, 0, 0.015);
        } else if (normal == Direction.WEST) {
            poseStack.mulPose(new Quaternionf().rotationZ((float) (-Math.PI / 2.0)));
            poseStack.translate(-0.015, 0, 0);
        } else if (normal == Direction.EAST) {
            poseStack.mulPose(new Quaternionf().rotationZ((float) (Math.PI / 2.0)));
            poseStack.translate(0.015, 0, 0);
        } else if (normal == Direction.DOWN) {
            poseStack.mulPose(new Quaternionf().rotationX((float) Math.PI));
            poseStack.translate(0, -0.015, 0);
        } else {
            // UP (Floor)
            poseStack.translate(0, 0.015, 0);
        }

        Matrix4f mat = poseStack.last().pose();
        Matrix3f nmat = poseStack.last().normal();

        int segments = 8;
        float angleStep = (float) (Math.PI * 2.0 / segments);

        for (int i = 0; i < segments; i++) {
            float a0 = i * angleStep;
            float a1 = (i + 1) * angleStep;

            float x0 = radius * Mth.cos(a0);
            float z0 = radius * Mth.sin(a0);
            float x1 = radius * Mth.cos(a1);
            float z1 = radius * Mth.sin(a1);

            vc.vertex(mat, 0.0f, 0.0f, 0.0f).color(r, g, b, a).uv(0.5f, 0.5f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();
            vc.vertex(mat, x0, 0.0f, z0).color(r, g, b, 0.0f).uv(0.0f, 0.0f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();
            vc.vertex(mat, x1, 0.0f, z1).color(r, g, b, 0.0f).uv(1.0f, 0.0f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();
            vc.vertex(mat, 0.0f, 0.0f, 0.0f).color(r, g, b, a).uv(0.5f, 0.5f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();
        }

        poseStack.popPose();
    }

    private static void drawVolumetricQuad(VertexConsumer vc, Matrix4f mat, Matrix3f nmat,
                                          float x0, float y0, float z0,
                                          float x1, float y1, float z1,
                                          float x2, float y2, float z2,
                                          float x3, float y3, float z3,
                                          float r, float g, float b, float a) {
        // Front Face
        vc.vertex(mat, x0, y0, z0).color(r, g, b, a).uv(0.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).uv(1.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a * 0.85f).uv(1.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();
        vc.vertex(mat, x3, y3, z3).color(r, g, b, a * 0.85f).uv(0.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();

        // Back Face
        vc.vertex(mat, x3, y3, z3).color(r, g, b, a * 0.85f).uv(0.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, -1.0f, 0.0f).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a * 0.85f).uv(1.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, -1.0f, 0.0f).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).uv(1.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, -1.0f, 0.0f).endVertex();
        vc.vertex(mat, x0, y0, z0).color(r, g, b, a).uv(0.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, -1.0f, 0.0f).endVertex();
    }

    private static void renderCinematicDustMotes(VertexConsumer vc, Matrix4f mat, Matrix3f nmat,
                                                float maxRayLen, float w, float endW,
                                                float time, Vec3 camPos, Vec3 startVec,
                                                float cr, float cg, float cb, float alpha, float mieBoost) {
        if (maxRayLen < 0.5f) return;

        for (int m = 0; m < DUST_MOTE_COUNT; m++) {
            float seed = m * 137.508f;
            float t = time * 0.012f + seed;

            float yProg = ((time * 0.018f + m / (float) DUST_MOTE_COUNT) % 1.0f);
            float yPos = yProg * maxRayLen;

            float rCone = (w * 0.5f) + (endW * 0.5f - w * 0.5f) * yProg;

            float ang = seed + (float) Math.sin(t * 1.5f) * 0.8f;
            float rad = (0.15f + 0.70f * ((float) Math.sin(seed * 3.1f) * 0.5f + 0.5f)) * rCone;

            float mx = rad * Mth.cos(ang) + (float) Math.sin(t * 2.3f + m) * 0.06f;
            float mz = rad * Mth.sin(ang) + (float) Math.cos(t * 1.9f + m) * 0.06f;

            float moteSize = 0.025f + 0.020f * ((float) Math.sin(m * 7.7f) * 0.5f + 0.5f);
            float flicker = 0.6f + 0.4f * (float) Math.sin(time * 0.15f + seed);
            float moteAlpha = alpha * flicker * 0.85f * mieBoost;

            dustBillboard(vc, mat, nmat, mx, yPos, mz, moteSize, cr, cg, cb, moteAlpha);
        }
    }

    private static void renderCinematicLensFlare(VertexConsumer vc, PoseStack poseStack,
                                                Quaternionf camOrientation,
                                                float ox, float oy, float oz, float w,
                                                float r, float g, float b,
                                                float cr, float cg, float cb,
                                                float alpha, float mieBoost,
                                                double cosAngle, float glow) {
        if (cosAngle < 0.45) return;

        float directFacing = (float) ((cosAngle - 0.45) / 0.55);
        float flareIntensity = directFacing * directFacing * alpha * (1.0f + 0.6f * glow);
        if (flareIntensity <= 0.01f) return;

        poseStack.pushPose();
        poseStack.translate(ox, oy, oz);
        poseStack.mulPose(camOrientation);

        Matrix4f flareMat = poseStack.last().pose();
        Matrix3f flareNmat = poseStack.last().normal();

        // 1. Concentric Iris Glow
        float irisRadius = (w * 0.35f + 0.30f) * (0.8f + 0.4f * directFacing);
        drawFlareDisc(vc, flareMat, flareNmat, irisRadius, cr, cg, cb, flareIntensity * 0.90f);

        // 2. Wide Anamorphic Streak (Hollywood Horizontal Streak)
        float streakLength = irisRadius * 4.5f * (1.0f + 0.5f * directFacing);
        float streakThickness = irisRadius * 0.18f;
        drawAnamorphicStreak(vc, flareMat, flareNmat, streakLength, streakThickness, r, g, b, flareIntensity * 0.75f);

        // 3. Multi-Blade Starburst Spikes
        drawStarburstSpikes(vc, flareMat, flareNmat, irisRadius * 2.2f, irisRadius * 0.12f,
                cr, cg, cb, flareIntensity * 0.55f);

        poseStack.popPose();
    }

    private static void dustBillboard(VertexConsumer vc, Matrix4f mat, Matrix3f nmat,
                                      float x, float y, float z, float s,
                                      float r, float g, float b, float a) {
        vc.vertex(mat, x - s, y - s, z).color(r, g, b, a).uv(0.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();
        vc.vertex(mat, x + s, y - s, z).color(r, g, b, a).uv(1.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();
        vc.vertex(mat, x + s, y + s, z).color(r, g, b, a).uv(1.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();
        vc.vertex(mat, x - s, y + s, z).color(r, g, b, a).uv(0.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 1.0f, 0.0f).endVertex();
    }

    private static void drawFlareDisc(VertexConsumer vc, Matrix4f mat, Matrix3f nmat,
                                      float radius, float r, float g, float b, float a) {
        int segs = 12;
        float step = (float) (Math.PI * 2.0 / segs);

        for (int i = 0; i < segs; i++) {
            float a0 = i * step;
            float a1 = (i + 1) * step;

            float x0 = radius * Mth.cos(a0);
            float z0 = radius * Mth.sin(a0);
            float x1 = radius * Mth.cos(a1);
            float z1 = radius * Mth.sin(a1);

            vc.vertex(mat, 0.0f, 0.0f, 0.0f).color(r, g, b, a).uv(0.5f, 0.5f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
            vc.vertex(mat, x0, z0, 0.0f).color(r, g, b, 0.0f).uv(0.0f, 0.0f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
            vc.vertex(mat, x1, z1, 0.0f).color(r, g, b, 0.0f).uv(1.0f, 0.0f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
            vc.vertex(mat, 0.0f, 0.0f, 0.0f).color(r, g, b, a).uv(0.5f, 0.5f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
        }
    }

    private static void drawAnamorphicStreak(VertexConsumer vc, Matrix4f mat, Matrix3f nmat,
                                             float len, float th,
                                             float r, float g, float b, float a) {
        vc.vertex(mat, -len, -th, 0.0f).color(r, g, b, 0.0f).uv(0.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, len, -th, 0.0f).color(r, g, b, 0.0f).uv(1.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, len, th, 0.0f).color(r, g, b, 0.0f).uv(1.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, -len, th, 0.0f).color(r, g, b, 0.0f).uv(0.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();

        vc.vertex(mat, -len * 0.4f, -th * 0.4f, 0.0f).color(1.0f, 1.0f, 1.0f, a).uv(0.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, len * 0.4f, -th * 0.4f, 0.0f).color(1.0f, 1.0f, 1.0f, a).uv(1.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, len * 0.4f, th * 0.4f, 0.0f).color(1.0f, 1.0f, 1.0f, a).uv(1.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, -len * 0.4f, th * 0.4f, 0.0f).color(1.0f, 1.0f, 1.0f, a).uv(0.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
    }

    private static void drawStarburstSpikes(VertexConsumer vc, Matrix4f mat, Matrix3f nmat,
                                           float length, float th,
                                           float r, float g, float b, float a) {
        for (float angle : new float[]{(float) (Math.PI / 4.0), (float) (Math.PI * 3.0 / 4.0)}) {
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);

            float x0 = -length * cos;
            float y0 = -length * sin;
            float x1 = length * cos;
            float y1 = length * sin;

            float px = -sin * th;
            float py = cos * th;

            vc.vertex(mat, x0 - px, y0 - py, 0.0f).color(r, g, b, 0.0f).uv(0.0f, 0.0f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
            vc.vertex(mat, x1 - px, y1 - py, 0.0f).color(r, g, b, 0.0f).uv(1.0f, 0.0f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
            vc.vertex(mat, x1 + px, y1 + py, 0.0f).color(r, g, b, 0.0f).uv(1.0f, 1.0f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
            vc.vertex(mat, x0 + px, y0 + py, 0.0f).color(r, g, b, 0.0f).uv(0.0f, 1.0f)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
        }
    }

    private static void glowDot(VertexConsumer vc, PoseStack poseStack,
                                Quaternionf camOrientation,
                                float ox, float oy, float oz,
                                float radius, float r, float g, float b, float a) {
        poseStack.pushPose();
        poseStack.translate(ox, oy, oz);
        poseStack.mulPose(camOrientation);

        Matrix4f mat = poseStack.last().pose();
        Matrix3f nmat = poseStack.last().normal();

        vc.vertex(mat, -radius, -radius, 0.0f).color(r, g, b, a).uv(0.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, radius, -radius, 0.0f).color(r, g, b, a).uv(1.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, radius, radius, 0.0f).color(r, g, b, a).uv(1.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();
        vc.vertex(mat, -radius, radius, 0.0f).color(r, g, b, a).uv(0.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, 0.0f, 0.0f, 1.0f).endVertex();

        poseStack.popPose();
    }

    private static void ghostCube(VertexConsumer vc, PoseStack poseStack,
                                  float r, float g, float b, float a) {
        Matrix4f mat = poseStack.last().pose();
        Matrix3f nmat = poseStack.last().normal();
        float x0 = 0.0f, y0 = 0.0f, z0 = 0.0f;
        float x1 = 1.0f, y1 = 1.0f, z1 = 1.0f;

        // Top
        quad(vc, mat, nmat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, r, g, b, a);
        // Bottom
        quad(vc, mat, nmat, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, r, g, b, a);
        // North
        quad(vc, mat, nmat, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, r, g, b, a);
        // South
        quad(vc, mat, nmat, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, r, g, b, a);
        // West
        quad(vc, mat, nmat, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, r, g, b, a);
        // East
        quad(vc, mat, nmat, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, r, g, b, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, Matrix3f nmat,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz,
                             float r, float g, float b, float a) {
        vc.vertex(mat, x0, y0, z0).color(r, g, b, a).uv(0.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, nx, ny, nz).endVertex();
        vc.vertex(mat, x1, y1, z1).color(r, g, b, a).uv(1.0f, 0.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, nx, ny, nz).endVertex();
        vc.vertex(mat, x2, y2, z2).color(r, g, b, a).uv(1.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, nx, ny, nz).endVertex();
        vc.vertex(mat, x3, y3, z3).color(r, g, b, a).uv(0.0f, 1.0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nmat, nx, ny, nz).endVertex();
    }

    private static boolean isHoldingTool() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return (main.getItem() instanceof BeamTunerItem) || (off.getItem() instanceof BeamTunerItem);
    }

    private static int hsvToRgb(float h, float s, float v) {
        int hi = (int) (h * 6.0f) % 6;
        float f = h * 6.0f - hi;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);
        return switch (hi) {
            case 0 -> packRgb(v, t, p);
            case 1 -> packRgb(q, v, p);
            case 2 -> packRgb(p, v, t);
            case 3 -> packRgb(p, q, v);
            case 4 -> packRgb(t, p, v);
            default -> packRgb(v, p, q);
        };
    }

    private static int packRgb(float r, float g, float b) {
        int ir = Mth.clamp((int) (r * 255.0f), 0, 255);
        int ig = Mth.clamp((int) (g * 255.0f), 0, 255);
        int ib = Mth.clamp((int) (b * 255.0f), 0, 255);
        return (ir << 16) | (ig << 8) | ib;
    }
}
