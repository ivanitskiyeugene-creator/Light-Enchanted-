package dev.lightenchanted.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.block.WallFanBlock;
import dev.lightenchanted.blockentity.IndustrialFanBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * High-detail 3D Renderer for Industrial Ventilation Fans:
 * - 3x3 SCP:SL Heavy Containment Fan
 * - 1x1 Compact Wall Fan
 * - Pure matte containment steel casing (no bright stripes)
 * - Rotating aerodynamically-pitched impeller blades
 */
public class IndustrialFanRenderer implements BlockEntityRenderer<IndustrialFanBlockEntity> {
    private static final ResourceLocation CASING_TEX =
            new ResourceLocation(LightEnchanted.MOD_ID, "textures/block/industrial_fan_casing.png");
    private static final ResourceLocation BLADE_TEX =
            new ResourceLocation(LightEnchanted.MOD_ID, "textures/block/industrial_fan_blade.png");
    private static final ResourceLocation GRATE_TEX =
            new ResourceLocation(LightEnchanted.MOD_ID, "textures/block/industrial_fan_grate.png");

    public IndustrialFanRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(IndustrialFanBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        boolean isWallFan = be.getBlockState().getBlock() instanceof WallFanBlock;
        if (!isWallFan && !be.isMaster()) {
            return;
        }

        Direction facing = be.getFacing();
        float spinAngle = be.getSpinAngle(partialTick);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        applyFacingRotation(poseStack, facing);

        VertexConsumer vcCasing = buffers.getBuffer(RenderType.entitySolid(CASING_TEX));
        VertexConsumer vcGrate = buffers.getBuffer(RenderType.entityCutout(GRATE_TEX));
        VertexConsumer vcBlade = buffers.getBuffer(RenderType.entitySolid(BLADE_TEX));

        Matrix4f mat = poseStack.last().pose();
        Matrix3f nmat = poseStack.last().normal();

        if (isWallFan) {
            // Render 1x1 Compact Wall Fan
            renderWallFanCasing(vcCasing, mat, nmat, packedLight);
            renderWallFanGrates(vcGrate, mat, nmat, packedLight);
            renderWallFanMotorHub(vcCasing, mat, nmat, packedLight);

            poseStack.pushPose();
            poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(spinAngle)));
            Matrix4f rotMat = poseStack.last().pose();
            Matrix3f rotNmat = poseStack.last().normal();

            renderWallFanBlades(vcBlade, rotMat, rotNmat, packedLight);
            poseStack.popPose();
        } else {
            // Render 3x3 Heavy Containment Fan
            renderCasingBox(vcCasing, mat, nmat, packedLight);
            renderGrates(vcGrate, mat, nmat, packedLight);
            renderMotorHub(vcCasing, mat, nmat, packedLight);

            poseStack.pushPose();
            poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(spinAngle)));
            Matrix4f rotMat = poseStack.last().pose();
            Matrix3f rotNmat = poseStack.last().normal();

            renderBlades(vcBlade, rotMat, rotNmat, packedLight);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void applyFacingRotation(PoseStack poseStack, Direction facing) {
        switch (facing) {
            case NORTH -> {}
            case SOUTH -> poseStack.mulPose(new Quaternionf().rotationY((float) Math.PI));
            case WEST -> poseStack.mulPose(new Quaternionf().rotationY((float) (Math.PI / 2.0)));
            case EAST -> poseStack.mulPose(new Quaternionf().rotationY((float) (-Math.PI / 2.0)));
            case UP -> poseStack.mulPose(new Quaternionf().rotationX((float) (Math.PI / 2.0)));
            case DOWN -> poseStack.mulPose(new Quaternionf().rotationX((float) (-Math.PI / 2.0)));
        }
    }

    private static void renderCasingBox(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float r = 1.48f;
        float d = 0.48f;
        float innerR = 1.25f;

        // Front Face Frame Bevels (z = -d)
        quad(vc, m, n, -r, r, -d, r, r, -d, r, innerR, -d, -r, innerR, -d, 0, 1, 0, 0.2f, light);
        quad(vc, m, n, -r, -innerR, -d, r, -innerR, -d, r, -r, -d, -r, -r, -d, 0, 1, 0.8f, 1, light);
        quad(vc, m, n, -r, innerR, -d, -innerR, innerR, -d, -innerR, -innerR, -d, -r, -innerR, -d, 0, 0.2f, 0.2f, 0.8f, light);
        quad(vc, m, n, innerR, innerR, -d, r, innerR, -d, r, -innerR, -d, innerR, -innerR, -d, 0.8f, 1, 0.2f, 0.8f, light);

        // Rear Face Frame Bevels (z = +d)
        quad(vc, m, n, r, r, d, -r, r, d, -r, innerR, d, r, innerR, d, 0, 1, 0, 0.2f, light);
        quad(vc, m, n, r, -innerR, d, -r, -innerR, d, -r, -r, d, r, -r, d, 0, 1, 0.8f, 1, light);
        quad(vc, m, n, r, innerR, d, innerR, innerR, d, innerR, -innerR, d, r, -innerR, d, 0, 0.2f, 0.2f, 0.8f, light);
        quad(vc, m, n, -innerR, innerR, d, -r, innerR, d, -r, -innerR, d, -innerR, -innerR, d, 0.8f, 1, 0.2f, 0.8f, light);

        // Outer Top, Bottom, Left, Right Plates
        quad(vc, m, n, -r, r, d, r, r, d, r, r, -d, -r, r, -d, 0, 1, 0, 1, light);
        quad(vc, m, n, -r, -r, -d, r, -r, -d, r, -r, d, -r, -r, d, 0, 1, 0, 1, light);
        quad(vc, m, n, -r, -r, -d, -r, -r, d, -r, r, d, -r, r, -d, 0, 1, 0, 1, light);
        quad(vc, m, n, r, -r, d, r, -r, -d, r, r, -d, r, r, d, 0, 1, 0, 1, light);
    }

    private static void renderWallFanCasing(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float r = 0.48f;
        float d = 0.48f;
        float innerR = 0.40f;

        quad(vc, m, n, -r, r, -d, r, r, -d, r, innerR, -d, -r, innerR, -d, 0, 1, 0, 0.2f, light);
        quad(vc, m, n, -r, -innerR, -d, r, -innerR, -d, r, -r, -d, -r, -r, -d, 0, 1, 0.8f, 1, light);
        quad(vc, m, n, -r, innerR, -d, -innerR, innerR, -d, -innerR, -innerR, -d, -r, -innerR, -d, 0, 0.2f, 0.2f, 0.8f, light);
        quad(vc, m, n, innerR, innerR, -d, r, innerR, -d, r, -innerR, -d, innerR, -innerR, -d, 0.8f, 1, 0.2f, 0.8f, light);

        quad(vc, m, n, r, r, d, -r, r, d, -r, innerR, d, r, innerR, d, 0, 1, 0, 0.2f, light);
        quad(vc, m, n, r, -innerR, d, -r, -innerR, d, -r, -r, d, r, -r, d, 0, 1, 0.8f, 1, light);
        quad(vc, m, n, r, innerR, d, innerR, innerR, d, innerR, -innerR, d, r, -innerR, d, 0, 0.2f, 0.2f, 0.8f, light);
        quad(vc, m, n, -innerR, innerR, d, -r, innerR, d, -r, -innerR, d, -innerR, -innerR, d, 0.8f, 1, 0.2f, 0.8f, light);
    }

    private static void renderGrates(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float r = 1.25f;
        float d = 0.42f;

        quad(vc, m, n, -r, -r, -d, r, -r, -d, r, r, -d, -r, r, -d, 0, 1, 0, 1, light);
        quad(vc, m, n, -r, r, d, r, r, d, r, -r, d, -r, -r, d, 0, 1, 0, 1, light);
    }

    private static void renderWallFanGrates(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float r = 0.40f;
        float d = 0.42f;

        quad(vc, m, n, -r, -r, -d, r, -r, -d, r, r, -d, -r, r, -d, 0, 1, 0, 1, light);
        quad(vc, m, n, -r, r, d, r, r, d, r, -r, d, -r, -r, d, 0, 1, 0, 1, light);
    }

    private static void renderMotorHub(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float hr = 0.35f;
        float hz = 0.25f;
        quad(vc, m, n, -hr, hr, -hz, hr, hr, -hz, hr, -hr, -hz, -hr, -hr, -hz, 0.3f, 0.7f, 0.3f, 0.7f, light);
        quad(vc, m, n, -hr, -hr, hz, hr, -hr, hz, hr, hr, hz, -hr, hr, hz, 0.3f, 0.7f, 0.3f, 0.7f, light);
    }

    private static void renderWallFanMotorHub(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float hr = 0.12f;
        float hz = 0.20f;
        quad(vc, m, n, -hr, hr, -hz, hr, hr, -hz, hr, -hr, -hz, -hr, -hr, -hz, 0.3f, 0.7f, 0.3f, 0.7f, light);
        quad(vc, m, n, -hr, -hr, hz, hr, -hr, hz, hr, hr, hz, -hr, hr, hz, 0.3f, 0.7f, 0.3f, 0.7f, light);
    }

    private static void renderBlades(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        int bladeCount = 5;
        float rHub = 0.35f;
        float rBlade = 1.22f;
        float rootW = 0.35f;
        float tipW = 0.58f;
        float pitch = 0.18f;

        for (int i = 0; i < bladeCount; i++) {
            float angle = (float) (i * (Math.PI * 2.0 / bladeCount));
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);

            float nx = -sin, ny = cos;

            float x0 = cos * rHub - nx * (rootW * 0.5f);
            float y0 = sin * rHub - ny * (rootW * 0.5f);
            float z0 = -pitch * 0.5f;

            float x1 = cos * rHub + nx * (rootW * 0.5f);
            float y1 = sin * rHub + ny * (rootW * 0.5f);
            float z1 = pitch * 0.5f;

            float x2 = cos * rBlade + nx * (tipW * 0.5f);
            float y2 = sin * rBlade + ny * (tipW * 0.5f);
            float z2 = pitch;

            float x3 = cos * rBlade - nx * (tipW * 0.5f);
            float y3 = sin * rBlade - ny * (tipW * 0.5f);
            float z3 = -pitch;

            quad(vc, m, n, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, 0, 1, 0, 1, light);
            quad(vc, m, n, x3, y3, z3, x2, y2, z2, x1, y1, z1, x0, y0, z0, 0, 1, 0, 1, light);
        }
    }

    private static void renderWallFanBlades(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        int bladeCount = 4;
        float rHub = 0.12f;
        float rBlade = 0.38f;
        float rootW = 0.12f;
        float tipW = 0.20f;
        float pitch = 0.08f;

        for (int i = 0; i < bladeCount; i++) {
            float angle = (float) (i * (Math.PI * 2.0 / bladeCount));
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);

            float nx = -sin, ny = cos;

            float x0 = cos * rHub - nx * (rootW * 0.5f);
            float y0 = sin * rHub - ny * (rootW * 0.5f);
            float z0 = -pitch * 0.5f;

            float x1 = cos * rHub + nx * (rootW * 0.5f);
            float y1 = sin * rHub + ny * (rootW * 0.5f);
            float z1 = pitch * 0.5f;

            float x2 = cos * rBlade + nx * (tipW * 0.5f);
            float y2 = sin * rBlade + ny * (tipW * 0.5f);
            float z2 = pitch;

            float x3 = cos * rBlade - nx * (tipW * 0.5f);
            float y3 = sin * rBlade - ny * (tipW * 0.5f);
            float z3 = -pitch;

            quad(vc, m, n, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, 0, 1, 0, 1, light);
            quad(vc, m, n, x3, y3, z3, x2, y2, z2, x1, y1, z1, x0, y0, z0, 0, 1, 0, 1, light);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float u0, float u1, float v0, float v1, int light) {
        vc.vertex(m, x0, y0, z0).color(255, 255, 255, 255).uv(u0, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, x1, y1, z1).color(255, 255, 255, 255).uv(u1, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, x2, y2, z2).color(255, 255, 255, 255).uv(u1, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, x3, y3, z3).color(255, 255, 255, 255).uv(u0, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
    }
}
