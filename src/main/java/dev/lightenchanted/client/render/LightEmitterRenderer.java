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
 * The beam is built in a local space stretched along +Y. If a target point is
 * set, that space is rotated so +Y points from the origin to the target;
 * otherwise the beam goes straight up or down (default: down). The origin can
 * be shifted per-axis (used by the invisible creative emitter). Soft additive
 * billboard "glow dots" mark the source and the impact point.
 *
 * Everything renders through our additive, no-cull render type with a soft
 * gradient texture tinted per-vertex.
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
        float height = 0.0f;

        if (aimed) {
            dx = cfg.targetX - originBaseWorldX;
            dy = cfg.targetY - originBaseWorldY;
            dz = cfg.targetZ - originBaseWorldZ;
            double lenSq = dx * dx + dy * dy + dz * dz;
            if (lenSq < 0.0625) { // less than 0.25 blocks: degenerate, fall back
                aimed = false;
            } else {
                double len = Math.sqrt(lenSq);
                height = (float) len;
                dx /= len;
                dy /= len;
                dz /= len;
            }
        }
        if (!aimed) {
            if (cfg.toSky) {
                height = cfg.down
                        ? Math.max(BeamConfig.MIN_HEIGHT, (float) (pos.getY() - level.getMinBuildHeight()) + cfg.offsetY)
                        : Math.max(BeamConfig.MIN_HEIGHT, level.getMaxBuildHeight() - pos.getY() - 1.0f + cfg.offsetY);
            } else {
                height = Math.max(BeamConfig.MIN_HEIGHT, cfg.height);
            }
            if (cfg.down) {
                dy = -1.0;
                originY = 0.0f + cfg.offsetY;
            }
        }

        float endRelX = originX + (float) dx * height;
        float endRelY = originY + (float) dy * height;
        float endRelZ = originZ + (float) dz * height;

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

        // ---- alpha & pulse (additive stacking: keep singles modest)
        float alpha = cfg.alpha / 255.0f;
        if (cfg.pulse > 0.001f) {
            alpha *= 0.55f + 0.45f * Mth.sin(time * cfg.pulse * 2.4f);
        }
        if (alpha < 0.005f) {
            alpha = 0.0f;
        }

        float rot = cfg.rotation > 0.001f ? time * cfg.rotation * 1.3f : 0.0f;

        // Core is whiter/hotter than the shell; shells stay faint because the
        // additive blending stacks every overlapping quad on top.
        float coreR = r * 0.6f + 0.4f;
        float coreG = g * 0.6f + 0.4f;
        float coreB = b * 0.6f + 0.4f;
        float coreAlpha = alpha * 0.55f;
        float glowAlpha = Math.min(1.0f, alpha * (0.13f + 0.10f * cfg.glow));

        float w = Math.max(BeamConfig.MIN_WIDTH, cfg.width);
        float endW = Math.max(BeamConfig.MIN_WIDTH, cfg.endWidth);
        float glowScale = 1.0f + 0.45f * cfg.glow;

        RenderType beamType = ModRenderTypes.beam(BEAM_TEXTURE);
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
                    squareColumn(vc, mat, nmat, w * 0.5f, height, rot, coreR, coreG, coreB, coreAlpha);
                    squareColumn(vc, mat, nmat, w * 0.5f * glowScale, height, rot, r, g, b, glowAlpha);
                }
                case CYLINDER -> { // constant-radius "laser"
                    cylinder(vc, mat, nmat, w * 0.4f, w * 0.4f, height, 12, rot, coreR, coreG, coreB, coreAlpha);
                    cylinder(vc, mat, nmat, w * 0.4f * glowScale, w * 0.4f * glowScale, height, 12, rot, r, g, b, glowAlpha);
                }
                case CONE -> { // spotlight: narrow at the source, wide at the target
                    float r0 = Math.max(0.02f, w * 0.15f);
                    float r1 = endW * 0.5f;
                    cylinder(vc, mat, nmat, r0, r1, height, 16, rot, coreR, coreG, coreB, coreAlpha);
                    cylinder(vc, mat, nmat, r0 * glowScale, r1 * glowScale, height, 16, rot, r, g, b, glowAlpha);
                }
                case HELIX -> {
                    helix(vc, mat, nmat, w * 0.5f, height, rot, r, g, b, alpha * 0.7f);
                    cylinder(vc, mat, nmat, w * 0.12f + 0.03f, w * 0.12f + 0.03f, height, 8, rot,
                            coreR, coreG, coreB, coreAlpha * 0.7f);
                }
                case SHEET -> {
                    sheet(vc, mat, nmat, w, height, rot, coreR, coreG, coreB, coreAlpha * 0.9f);
                    sheet(vc, mat, nmat, w * glowScale, height, rot + HALF_PI, r, g, b, glowAlpha);
                }
                case STAR -> {
                    for (int i = 0; i < 4; i++) {
                        sheet(vc, mat, nmat, w, height, rot + i * QUARTER_PI, coreR, coreG, coreB, coreAlpha * 0.7f);
                    }
                    cylinder(vc, mat, nmat, w * 0.14f + 0.04f, w * 0.14f + 0.04f, height, 8, rot,
                            coreR, coreG, coreB, coreAlpha * 0.6f);
                }
            }
            poseStack.popPose();

            // ---- glow dots at both ends (camera-facing billboards)
            Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
            float endRadius = cfg.shape == BeamShape.CONE
                    ? Math.max(0.12f, endW * 0.40f)
                    : Math.max(0.10f, w * 0.45f);

            glowDot(vc, poseStack, cam, originX, originY, originZ,
                    Math.max(0.08f, w * 0.30f), coreR, coreG, coreB, Math.min(1.0f, alpha * 0.5f));
            glowDot(vc, poseStack, cam, endRelX, endRelY, endRelZ,
                    endRadius, coreR, coreG, coreB, alpha * 0.30f);
        }

        // ---- creative ghost box + origin marker while holding a tool
        if (showGhost) {
            ghostCube(vc, poseStack, 0.55f, 0.85f, 1.0f, 0.10f);
            Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
            glowDot(vc, poseStack, cam, originX, originY, originZ,
                    0.10f, 0.6f, 1.0f, 1.0f, 0.6f);
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

    /** Four-sided square tube. */
    private static void squareColumn(VertexConsumer vc, Matrix4f m, Matrix3f n, float half, float height,
                                     float rot, float r, float g, float b, float a) {
        for (int i = 0; i < 4; i++) {
            float a0 = rot + i * HALF_PI;
            float a1 = a0 + HALF_PI;
            float x0 = half * Mth.cos(a0);
            float z0 = half * Mth.sin(a0);
            float x1 = half * Mth.cos(a1);
            float z1 = half * Mth.sin(a1);
            quad(vc, m, n,
                    x0, 0, z0, x1, 0, z1,
                    x1, height, z1, x0, height, z0,
                    r, g, b, a, 0.0f, 1.0f, 0.0f, height);
        }
    }

    /** Polygonal prism; different top/bottom radii turn it into a cone. */
    private static void cylinder(VertexConsumer vc, Matrix4f m, Matrix3f n, float rBottom, float rTop,
                                 float height, int segments, float rot,
                                 float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            float a0 = rot + TAU * i / segments;
            float a1 = rot + TAU * (i + 1) / segments;
            float cb0 = Mth.cos(a0), sb0 = Mth.sin(a0);
            float cb1 = Mth.cos(a1), sb1 = Mth.sin(a1);
            quad(vc, m, n,
                    rBottom * cb0, 0, rBottom * sb0,
                    rBottom * cb1, 0, rBottom * sb1,
                    rTop * cb1, height, rTop * sb1,
                    rTop * cb0, height, rTop * sb0,
                    r, g, b, a,
                    (float) i / segments, (float) (i + 1) / segments, 0.0f, height);
        }
    }

    /** Two ribbons spiralling around the core, 180 degrees apart. */
    private static void helix(VertexConsumer vc, Matrix4f m, Matrix3f n, float radius, float height,
                              float rot, float r, float g, float b, float a) {
        int slices = Mth.clamp(Mth.ceil(height / 0.3f), 8, 1500);
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
                quad(vc, m, n,
                        inner * Mth.cos(ang0), y0, inner * Mth.sin(ang0),
                        outer * Mth.cos(ang0), y0, outer * Mth.sin(ang0),
                        outer * Mth.cos(ang1), y1, outer * Mth.sin(ang1),
                        inner * Mth.cos(ang1), y1, inner * Mth.sin(ang1),
                        r, g, b, a, 0.0f, 1.0f, y0, y1);
            }
        }
    }

    /** Single vertical plane of given width, rotated by {@code angle} around the beam axis. */
    private static void sheet(VertexConsumer vc, Matrix4f m, Matrix3f n, float width, float height,
                              float angle, float r, float g, float b, float a) {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        float hw = width * 0.5f;
        quad(vc, m, n,
                -hw * c, 0, -hw * s,
                hw * c, 0, hw * s,
                hw * c, height, hw * s,
                -hw * c, height, -hw * s,
                r, g, b, a, 0.0f, 1.0f, 0.0f, height);
    }

    /** Camera-facing glowing dot. */
    private static void glowDot(VertexConsumer vc, PoseStack poseStack, Quaternionf cam,
                                double x, double y, double z, float radius,
                                float r, float g, float b, float a) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(cam);
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        quad(vc, m, n,
                -radius, -radius, 0.0f, radius, -radius, 0.0f,
                radius, radius, 0.0f, -radius, radius, 0.0f,
                r, g, b, a, 0.0f, 1.0f, 0.0f, 1.0f);
        poseStack.popPose();
    }

    /** Translucent unit cube drawn around the invisible creative emitter. */
    private static void ghostCube(VertexConsumer vc, PoseStack poseStack,
                                  float r, float g, float b, float a) {
        poseStack.pushPose();
        poseStack.scale(1.0f, 1.0f, 1.0f);
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();
        float o = -0.002f;
        float p = 1.002f;
        // bottom / top
        quad(vc, m, n, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, r, g, b, a, 0, 1, 0, 1);
        quad(vc, m, n, 0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, r, g, b, a, 0, 1, 0, 1);
        // sides
        quad(vc, m, n, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, r, g, b, a, 0, 1, 0, 1);
        quad(vc, m, n, 1, 0, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, r, g, b, a, 0, 1, 0, 1);
        quad(vc, m, n, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 1, 1, r, g, b, a, 0, 1, 0, 1);
        quad(vc, m, n, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, r, g, b, a, 0, 1, 0, 1);
        // thin edge frame so the box reads clearly
        frame(vc, m, n, o, p, r, g, b, Math.min(1.0f, a * 2.5f));
        poseStack.popPose();
    }

    /** Four upright corner strips acting as a bright frame of the ghost cube. */
    private static void frame(VertexConsumer vc, Matrix4f m, Matrix3f n,
                              float o, float p, float r, float g, float b, float a) {
        float t = 0.015f;
        for (int i = 0; i < 4; i++) {
            float x = (i == 0 || i == 3) ? o : p;
            float z = (i < 2) ? o : p;
            float x1 = (i == 0 || i == 3) ? t : -t;
            float z1 = (i < 2) ? t : -t;
            quad(vc, m, n, x, 0, z, x + x1, 0, z, x + x1, 1, z, x, 1, z, r, g, b, a, 0, 1, 0, 1);
            quad(vc, m, n, x, 0, z, x, 0, z + z1, x, 1, z + z1, x, 1, z, r, g, b, a, 0, 1, 0, 1);
        }
    }

    // ------------------------------------------------------------ plumbing

    /** Emits a single-winding quad; the render type disables culling. */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float a,
                             float u0, float u1, float v0, float v1) {
        vertex(vc, m, n, x1, y1, z1, r, g, b, a, u0, v0);
        vertex(vc, m, n, x2, y2, z2, r, g, b, a, u1, v0);
        vertex(vc, m, n, x3, y3, z3, r, g, b, a, u1, v1);
        vertex(vc, m, n, x4, y4, z4, r, g, b, a, u0, v1);
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
        // The beam can extend far away from the block itself.
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
