package dev.lightenchanted.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lightenchanted.LightEnchanted;
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
 * High-detail 3D Renderer for SCP:SL Heavy Containment Zone (HCZ) 3x3 Industrial Fan.
 * - 3x3 Dark heavy containment shroud with hazard stripes
 * - Front and rear safety rebar grates
 * - Central heavy motor hub
 * - 5 curved rotating steel fan blades with live rotation animation
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
        if (!be.isMaster()) {
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

        // 1. Render 3x3 Outer Heavy Shroud with Hazard Rim (Front and Back)
        renderCasingBox(vcCasing, mat, nmat, packedLight);

        // 2. Render Front and Rear Safety Grates
        renderGrates(vcGrate, mat, nmat, packedLight);

        // 3. Render Central Motor Dome
        renderMotorHub(vcCasing, mat, nmat, packedLight);

        // 4. Render Rotating 5-Blade Impeller Assembly
        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(spinAngle)));
        Matrix4f rotMat = poseStack.last().pose();
        Matrix3f rotNmat = poseStack.last().normal();

        renderBlades(vcBlade, rotMat, rotNmat, packedLight);

        poseStack.popPose();

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

    private static void renderGrates(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float r = 1.25f;
        float d = 0.42f;

        // Front Safety Grate (z = -d)
        quad(vc, m, n, -r, -r, -d, r, -r, -d, r, r, -d, -r, r, -d, 0, 3, 0, 3, light);
        quad(vc, m, n, -r, r, -d, r, r, -d, r, -r, -d, -r, -r, -d, 0, 3, 0, 3, light);

        // Rear Safety Grate (z = +d)
        quad(vc, m, n, -r, -r, d, r, -r, d, r, r, d, -r, r, d, 0, 3, 0, 3, light);
        quad(vc, m, n, -r, r, d, r, r, d, r, -r, d, -r, -r, d, 0, 3, 0, 3, light);
    }

    private static void renderMotorHub(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float hr = 0.38f;
        float hd = 0.28f;
        int segs = 8;
        float tau = (float) (Math.PI * 2.0);

        for (int i = 0; i < segs; i++) {
            float a0 = tau * i / segs;
            float a1 = tau * (i + 1) / segs;
            float x0 = hr * Mth.cos(a0), y0 = hr * Mth.sin(a0);
            float x1 = hr * Mth.cos(a1), y1 = hr * Mth.sin(a1);

            // Hub cylinder sides
            quad(vc, m, n, x0, y0, -hd, x1, y1, -hd, x1, y1, hd, x0, y0, hd, 0, 1, 0, 1, light);

            // Hub front cap
            quad(vc, m, n, 0, 0, -hd, x0, y0, -hd, x1, y1, -hd, 0, 0, -hd, 0.4f, 0.6f, 0.4f, 0.6f, light);
            // Hub back cap
            quad(vc, m, n, 0, 0, hd, x1, y1, hd, x0, y0, hd, 0, 0, hd, 0.4f, 0.6f, 0.4f, 0.6f, light);
        }
    }

    private static void renderBlades(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        int bladeCount = 5;
        float tau = (float) (Math.PI * 2.0);
        float hubRadius = 0.32f;
        float bladeLength = 0.90f;
        float rootWidth = 0.22f;
        float tipWidth = 0.42f;
        float pitchAngle = 0.38f;

        for (int i = 0; i < bladeCount; i++) {
            float baseAngle = tau * i / bladeCount;
            float cos = Mth.cos(baseAngle);
            float sin = Mth.sin(baseAngle);
            float pCos = -sin;
            float pSin = cos;

            float zPitch = Mth.sin(pitchAngle) * tipWidth * 0.5f;

            float rx0 = cos * hubRadius - pCos * (rootWidth * 0.5f);
            float ry0 = sin * hubRadius - pSin * (rootWidth * 0.5f);
            float rz0 = -zPitch * 0.5f;

            float rx1 = cos * hubRadius + pCos * (rootWidth * 0.5f);
            float ry1 = sin * hubRadius + pSin * (rootWidth * 0.5f);
            float rz1 = zPitch * 0.5f;

            float tx0 = cos * (hubRadius + bladeLength) - pCos * (tipWidth * 0.5f);
            float ty0 = sin * (hubRadius + bladeLength) - pSin * (tipWidth * 0.5f);
            float tz0 = -zPitch;

            float tx1 = cos * (hubRadius + bladeLength) + pCos * (tipWidth * 0.5f);
            float ty1 = sin * (hubRadius + bladeLength) + pSin * (tipWidth * 0.5f);
            float tz1 = zPitch;

            quad(vc, m, n, rx0, ry0, rz0, rx1, ry1, rz1, tx1, ty1, tz1, tx0, ty0, tz0, 0, 1, 0, 1, light);
            quad(vc, m, n, tx0, ty0, tz0, tx1, ty1, tz1, rx1, ry1, rz1, rx0, ry0, rz0, 0, 1, 0, 1, light);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float x3, float y3, float z3,
                            float x4, float y4, float z4,
                            float u0, float u1, float v0, float v1, int light) {
        vc.vertex(m, x1, y1, z1).color(255, 255, 255, 255).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x2, y2, z2).color(255, 255, 255, 255).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x3, y3, z3).color(255, 255, 255, 255).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x4, y4, z4).color(255, 255, 255, 255).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(IndustrialFanBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
