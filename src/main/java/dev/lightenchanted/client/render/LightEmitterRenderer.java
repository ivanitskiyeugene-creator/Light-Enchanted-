package dev.lightenchanted.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.beam.BeamShape;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import dev.lightenchanted.init.ModItems;
import dev.lightenchanted.item.BeamTunerItem;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Renders the beam.
 *
 * Uses {@link RenderType#beaconBeam(ResourceLocation, boolean)} with a custom
 * soft gradient texture. This standard pipeline is fully recognized and hooked
 * by Iris, Oculus, OptiFine, and vanilla shaders for post-process bloom,
 * volumetric god-rays, depth-buffer sorting, and distance fog.
 *
 * Geometry features:
 * - Aimed spotlight cone penetrates slightly into surfaces and fades vertically
 *   to avoid any floating circular cutoffs.
 * - Flat glowing ground disc at the impact point.
 * - Smooth distance fade for long-range beams (smooth falloff beyond 30-50 blocks).
 * - Per-axis creative offset and ghost preview box.
 */
public class LightEmitterRenderer implements BlockEntityRenderer<LightEmitterBlockEntity> {
    private static final ResourceLocation BEAM_TEXTURE =
            new ResourceLocation(LightEnchanted.MOD_ID, "textures/entity/beam.png");
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float HALF_PI = (float) (Math.PI / 2.0);
    private static final float QUARTER_PI = (float) (Math.PI / 4.0);

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

        // ---- origin (block-relative, includes the creative offset)
        float originX = 0.5f + cfg.offsetX;
        float originZ = 0.5f + cfg.offsetZ;
        double originBaseWorldX = pos.getX() + 0.5 + cfg.offsetX;
        double originBaseWorldY = pos.getY() + 1.0 + cfg.offsetY;
        double originBaseWorldZ = pos.getZ() + 0.5 + cfg.offsetZ;

        // ---- direction & length
        boolean aimed = cfg.hasTarget;
        double dx = 0.0, dy = 1.0, dz = 0.0;
        float originY = 1.0f + cfg.offsetY;
        float actualDist = 0.0f;
        float renderHeight = 0.0f;

        if (aimed) {
            dx = cfg.targetX - originBaseWorldX;
            dy = cfg.targetY - originBaseWorldY;
            dz = cfg.targetZ - originBaseWorldZ;
            double lenSq = dx * dx + dy * dy + dz * dz;
            if (lenSq < 0.0625) { // less than 0.25 blocks: degenerate
                aimed = false;
            } else {
                double len = Math.sqrt(lenSq);
                actualDist = (float) len;
                dx /= len;
                dy /= len;
                dz /= len;
                // Extend cone slightly past impact point so the tilted circular rim
                // sinks into the ground rather than floating as an arc in mid-air.
                float endW = Math.max(BeamConfig.MIN_WIDTH, cfg.endWidth);
                renderHeight = actualDist + Math.max(0.6f, endW * 0.45f);
            }
        }
        if (!aimed) {
            if (cfg.toSky) {
                // To-sky beams fade gracefully into the atmosphere over 60-80 blocks
                renderHeight = 72.0f;
                actualDist = renderHeight;
            } else {
                renderHeight = Math.max(BeamConfig.MIN_HEIGHT, cfg.height);
                actualDist = renderHeight;
            }
            if (cfg.down) {
                dy = -1.0;
                originY = 0.0f + cfg.offsetY;
            }
        }

        float time = (float) (level.getGameTime() % 720000L) + partialTick;

        // ---- color
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

        // ---- alpha & smooth distance falloff (> 30-50 blocks)
        float alpha = cfg.alpha / 255.0f;
        if (cfg.pulse > 0.001f) {
            alpha *= 0.60f + 0.40f * Mth.sin(time * cfg.pulse * 2.4f);
        }
        if (actualDist > 30.0f) {
            // Smooth attenuation beyond 30 blocks: 50 blocks -> ~65%, 80 blocks -> ~45%
            float distFactor = 1.0f / (1.0f + 0.022f * (actualDist - 30.0f));
            alpha *= distFactor;
        }
        if (alpha < 0.005f) {
            alpha = 0.0f;
        }

        float rot = cfg.rotation > 0.001f ? time * cfg.rotation * 1.3f : 0.0f;

        // Core is hotter/whiter; shell provides the soft colored halo
        float coreR = r * 0.5f + 0.5f;
        float coreG = g * 0.5f + 0.5f;
        float coreB = b * 0.5f + 0.5f;
        float coreAlpha = alpha * 0.70f;
        float glowAlpha = Math.min(1.0f, alpha * (0.18f + 0.12f * cfg.glow));

        float w = Math.max(BeamConfig.MIN_WIDTH, cfg.width);
        float endW = Math.max(BeamConfig.MIN_WIDTH, cfg.endWidth);
        float glowScale = 1.0f + 0.50f * cfg.glow;

        // Standard beacon-beam render type (full shader & Iris/Oculus compatibility)
        RenderType beamType = RenderType.beaconBeam(BEAM_TEXTURE, true);
        VertexConsumer vc = buffers.getBuffer(beamType);

        if (alpha > 0.0f) {
            poseStack.pushPose();
            poseStack.translate(originX, originY, originZ);
            if (aimed) {
                poseStack.mulPose(new Quaternionf().rotationTo(
                        0.0f, 1.0f, 0.0f, (float) dx, (float) dy, (float) dz));
            } else if (cfg.down) {
                poseStack.mulPose(new Quaternionf().rotationX((float) Math.PI));
            }
            Matrix4f mat = poseStack.last().pose();
            Matrix3f nmat = poseStack.last().normal();

            switch (cfg.shape) {
                case CLASSIC -> {
                    squareColumn(vc, mat, nmat, w * 0.5f, renderHeight, rot, coreR, coreG, coreB, coreAlpha);
                    squareColumn(vc, mat, nmat, w * 0.5f * glowScale, renderHeight, rot, r, g, b, glowAlpha);
                }
                case CYLINDER -> {
                    cylinder(vc, mat, nmat, w * 0.4f, w * 0.4f, renderHeight, 14, rot, coreR, coreG, coreB, coreAlpha);
                    cylinder(vc, mat, nmat, w * 0.4f * glowScale, w * 0.4f * glowScale, renderHeight, 14, rot, r, g, b, glowAlpha);
                }
                case CONE -> {
                    float r0 = Math.max(0.03f, w * 0.15f);
                    float r1 = endW * 0.5f;
                    cylinder(vc, mat, nmat, r0, r1, renderHeight, 16, rot, coreR, coreG, coreB, coreAlpha);
                    cylinder(vc, mat, nmat, r0 * glowScale, r1 * glowScale, renderHeight, 16, rot, r, g, b, glowAlpha);
                }
                case HELIX -> {
                    helix(vc, mat, nmat, w * 0.5f, renderHeight, rot, r, g, b, alpha * 0.75f);
                    cylinder(vc, mat, nmat, w * 0.12f + 0.03f, w * 0.12f + 0.03f, renderHeight, 8, rot,
                            coreR, coreG, coreB, coreAlpha * 0.7f);
                }
                case SHEET -> {
                    sheet(vc, mat, nmat, w, renderHeight, rot, coreR, coreG, coreB, coreAlpha * 0.9f);
                    sheet(vc, mat, nmat, w * glowScale, renderHeight, rot + HALF_PI, r, g, b, glowAlpha);
                }
                case STAR -> {
                    for (int i = 0; i < 4; i++) {
                        sheet(vc, mat, nmat, w, renderHeight, rot + i * QUARTER_PI, coreR, coreG, coreB, coreAlpha * 0.7f);
                    }
                    cylinder(vc, mat, nmat, w * 0.14f + 0.04f, w * 0.14f + 0.04f, renderHeight, 8, rot,
                            coreR, coreG, coreB, coreAlpha * 0.6f);
                }
            }
            poseStack.popPose();

            // ---- glow dots at origin and impact
            Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
            glowDot(vc, poseStack, cam, originX, originY, originZ,
                    Math.max(0.08f, w * 0.32f), coreR, coreG, coreB, Math.min(1.0f, alpha * 0.6f));

            if (aimed) {
                // Impact ground pool disc
                double hitRelX = cfg.targetX - pos.getX();
                double hitRelY = cfg.targetY - pos.getY() + 0.02;
                double hitRelZ = cfg.targetZ - pos.getZ();
                float groundRadius = cfg.shape == BeamShape.CONE
                        ? Math.max(0.25f, endW * 0.48f)
                        : Math.max(0.20f, w * 0.45f);

                flatGroundDisc(vc, poseStack, hitRelX, hitRelY, hitRelZ, groundRadius,
                        coreR, coreG, coreB, Math.min(1.0f, alpha * 0.75f));
            }
        }

        // ---- creative ghost box + origin marker while holding a tool
        if (showGhost) {
            ghostCube(vc, poseStack, 0.55f, 0.85f, 1.0f, 0.12f);
            Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
            glowDot(vc, poseStack, cam, originX, originY, originZ,
                    0.12f, 0.6f, 1.0f, 1.0f, 0.7f);
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

    // ------------------------------------------------------------ geometry

    /** Four-sided square tube (two-sided for clean interior and exterior). */
    private static void squareColumn(VertexConsumer vc, Matrix4f m, Matrix3f n, float half, float height,
                                     float rot, float r, float g, float b, float a) {
        for (int i = 0; i < 4; i++) {
            float a0 = rot + i * HALF_PI;
            float a1 = a0 + HALF_PI;
            float x0 = half * Mth.cos(a0);
            float z0 = half * Mth.sin(a0);
            float x1 = half * Mth.cos(a1);
            float z1 = half * Mth.sin(a1);
            twoSidedQuad(vc, m, n,
                    x0, 0, z0, x1, 0, z1,
                    x1, height, z1, x0, height, z0,
                    r, g, b, a, 0.0f, 1.0f, 0.0f, 1.0f);
        }
    }

    /** Polygonal prism/cone (two-sided). */
    private static void cylinder(VertexConsumer vc, Matrix4f m, Matrix3f n, float rBottom, float rTop,
                                 float height, int segments, float rot,
                                 float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            float a0 = rot + TAU * i / segments;
            float a1 = rot + TAU * (i + 1) / segments;
            float cb0 = Mth.cos(a0), sb0 = Mth.sin(a0);
            float cb1 = Mth.cos(a1), sb1 = Mth.sin(a1);
            twoSidedQuad(vc, m, n,
                    rBottom * cb0, 0, rBottom * sb0,
                    rBottom * cb1, 0, rBottom * sb1,
                    rTop * cb1, height, rTop * sb1,
                    rTop * cb0, height, rTop * sb0,
                    r, g, b, a,
                    (float) i / segments, (float) (i + 1) / segments, 0.0f, 1.0f);
        }
    }

    /** Two ribbons spiralling around the core. */
    private static void helix(VertexConsumer vc, Matrix4f m, Matrix3f n, float radius, float height,
                              float rot, float r, float g, float b, float a) {
        int slices = Mth.clamp(Mth.ceil(height / 0.35f), 8, 800);
        float step = height / slices;
        float halfWidth = Math.max(0.05f, radius * 0.35f);
        float inner = Math.max(0.02f, radius - halfWidth);
        float outer = radius + halfWidth;

        for (int ribbon = 0; ribbon < 2; ribbon++) {
            float phase = ribbon * (float) Math.PI;
            for (int k = 0; k < slices; k++) {
                float y0 = k * step;
                float y1 = y0 + step;
                float ang0 = rot + phase + y0 * 1.1f;
                float ang1 = rot + phase + y1 * 1.1f;
                twoSidedQuad(vc, m, n,
                        inner * Mth.cos(ang0), y0, inner * Mth.sin(ang0),
                        outer * Mth.cos(ang0), y0, outer * Mth.sin(ang0),
                        outer * Mth.cos(ang1), y1, outer * Mth.sin(ang1),
                        inner * Mth.cos(ang1), y1, inner * Mth.sin(ang1),
                        r, g, b, a, 0.0f, 1.0f, y0 / height, y1 / height);
            }
        }
    }

    /** Single vertical plane of given width. */
    private static void sheet(VertexConsumer vc, Matrix4f m, Matrix3f n, float width, float height,
                              float angle, float r, float g, float b, float a) {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        float hw = width * 0.5f;
        twoSidedQuad(vc, m, n,
                -hw * c, 0, -hw * s,
                hw * c, 0, hw * s,
                hw * c, height, hw * s,
                -hw * c, height, -hw * s,
                r, g, b, a, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    /** Camera-facing billboard glowing dot. */
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

    /** Flat glowing disc lying on the floor at the impact location. */
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

    /** Translucent unit cube drawn around the invisible creative emitter. */
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

    // ------------------------------------------------------------ plumbing

    /** Emits a two-sided quad (both front and back windings). */
    private static void twoSidedQuad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    float x3, float y3, float z3, float x4, float y4, float z4,
                                    float r, float g, float b, float a,
                                    float u0, float u1, float v0, float v1) {
        // Front face
        vertex(vc, m, n, x1, y1, z1, r, g, b, a, u0, v0);
        vertex(vc, m, n, x2, y2, z2, r, g, b, a, u1, v0);
        vertex(vc, m, n, x3, y3, z3, r, g, b, a, u1, v1);
        vertex(vc, m, n, x4, y4, z4, r, g, b, a, u0, v1);

        // Back face
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
