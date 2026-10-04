package dev.lightenchanted.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders the fully customizable beam.
 *
 * Uses the vanilla beacon-beam render type (translucent, emissive, no depth
 * write) with our own gradient texture, tinted per-vertex with the configured
 * color. Every quad is emitted twice with opposite winding so shapes stay
 * visible from any side even with face culling enabled.
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
        if (!cfg.enabled) {
            return;
        }
        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        float time = (float) (level.getGameTime() % 720000L) + partialTick;

        float height = cfg.toSky
                ? Math.max(BeamConfig.MIN_HEIGHT, level.getMaxBuildHeight() - be.getBlockPos().getY())
                : Math.max(BeamConfig.MIN_HEIGHT, cfg.height);

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

        // ---- alpha & pulse
        float alpha = cfg.alpha / 255.0f;
        if (cfg.pulse > 0.001f) {
            alpha *= 0.6f + 0.4f * Mth.sin(time * cfg.pulse * 2.4f);
        }
        if (alpha < 0.01f) {
            return;
        }

        float rot = cfg.rotation > 0.001f ? time * cfg.rotation * 1.3f : 0.0f;

        // Core is a bit whiter/brighter than the shell.
        float coreR = r * 0.7f + 0.3f;
        float coreG = g * 0.7f + 0.3f;
        float coreB = b * 0.7f + 0.3f;
        float glowAlpha = Math.min(1.0f, alpha * (0.35f + 0.25f * cfg.glow));

        float w = Math.max(BeamConfig.MIN_WIDTH, cfg.width);
        float glowScale = 1.0f + 0.4f * cfg.glow;

        VertexConsumer vc = buffers.getBuffer(RenderType.beaconBeam(BEAM_TEXTURE, false));
        poseStack.pushPose();
        poseStack.translate(0.5D, 1.0D, 0.5D);
        Matrix4f mat = poseStack.last().pose();
        Matrix3f nmat = poseStack.last().normal();

        switch (cfg.shape) {
            case CLASSIC -> {
                squareColumn(vc, mat, nmat, w * 0.5f, height, rot, coreR, coreG, coreB, alpha);
                squareColumn(vc, mat, nmat, w * 0.5f * glowScale, height, rot, r, g, b, glowAlpha);
            }
            case CYLINDER -> {
                cylinder(vc, mat, nmat, w * 0.5f, w * 0.5f, height, 14, rot, coreR, coreG, coreB, alpha);
                cylinder(vc, mat, nmat, w * 0.5f * glowScale, w * 0.5f * glowScale, height, 14, rot, r, g, b, glowAlpha);
            }
            case CONE -> {
                cylinder(vc, mat, nmat, w * 0.5f, w * 0.06f, height, 14, rot, coreR, coreG, coreB, alpha);
                cylinder(vc, mat, nmat, w * 0.5f * glowScale, w * 0.06f * glowScale, height, 14, rot, r, g, b, glowAlpha);
            }
            case HELIX -> {
                helix(vc, mat, nmat, w * 0.5f, height, rot, r, g, b, alpha * 0.9f);
                cylinder(vc, mat, nmat, w * 0.12f + 0.03f, w * 0.12f + 0.03f, height, 8, rot,
                        coreR, coreG, coreB, alpha * 0.55f);
            }
            case SHEET -> {
                sheet(vc, mat, nmat, w, height, rot, coreR, coreG, coreB, alpha);
                sheet(vc, mat, nmat, w * glowScale, height, rot + HALF_PI, r, g, b, alpha * 0.5f);
            }
            case STAR -> {
                for (int i = 0; i < 4; i++) {
                    sheet(vc, mat, nmat, w, height, rot + i * QUARTER_PI, coreR, coreG, coreB, alpha * 0.8f);
                }
                cylinder(vc, mat, nmat, w * 0.14f + 0.04f, w * 0.14f + 0.04f, height, 8, rot,
                        coreR, coreG, coreB, alpha * 0.6f);
            }
        }

        poseStack.popPose();
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

    /** Single vertical plane of given width, rotated by {@code angle} around Y. */
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

    // ------------------------------------------------------------ plumbing

    /** Emits a quad twice (both windings) so it renders from either side. */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
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
        // The beam can extend far above the block itself.
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
