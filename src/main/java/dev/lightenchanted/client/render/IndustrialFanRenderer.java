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
 * High-Detail 3D Industrial Ventilation Fan Renderer:
 * - 3x3 SCP:SL Heavy Containment Fan & 1x1 Compact Wall Fan
 * - True 3D Cylindrical Duct Tunnel with Corner Gussets
 * - 3D Octagonal Motor Housing with 4 Heavy Structural Support Arms
 * - Aerodynamically-Pitched Curved Impeller Blades with Root Cuffs
 * - Double-Sided Fine Protective Grilles (visible from both inside and outside)
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
            // Render 3x3 SCP:SL Heavy Containment Fan
            renderCasingAndDuctTunnel(vcCasing, mat, nmat, packedLight);
            renderGrates(vcGrate, mat, nmat, packedLight);
            renderMotorAndSupportStruts(vcCasing, mat, nmat, packedLight);

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

    // =========================================================================
    // 3x3 SCP:SL HEAVY CONTAINMENT FAN
    // =========================================================================

    private static void renderCasingAndDuctTunnel(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float r = 1.48f;
        float d = 0.48f;
        float innerR = 1.25f;

        // 1. Front Outer Wall Plates (z = -d)
        quad(vc, m, n, -r, r, -d, r, r, -d, r, innerR, -d, -r, innerR, -d, 0, 1, 0, 0.15f, light);
        quad(vc, m, n, -r, -innerR, -d, r, -innerR, -d, r, -r, -d, -r, -r, -d, 0, 1, 0.85f, 1, light);
        quad(vc, m, n, -r, innerR, -d, -innerR, innerR, -d, -innerR, -innerR, -d, -r, -innerR, -d, 0, 0.15f, 0.15f, 0.85f, light);
        quad(vc, m, n, innerR, innerR, -d, r, innerR, -d, r, -innerR, -d, innerR, -innerR, -d, 0.85f, 1, 0.15f, 0.85f, light);

        // 2. Rear Outer Wall Plates (z = +d)
        quad(vc, m, n, r, r, d, -r, r, d, -r, innerR, d, r, innerR, d, 0, 1, 0, 0.15f, light);
        quad(vc, m, n, r, -innerR, d, -r, -innerR, d, -r, -r, d, r, -r, d, 0, 1, 0.85f, 1, light);
        quad(vc, m, n, r, innerR, d, innerR, innerR, d, innerR, -innerR, d, r, -innerR, d, 0, 0.15f, 0.15f, 0.85f, light);
        quad(vc, m, n, -innerR, innerR, d, -r, innerR, d, -r, -innerR, d, -innerR, -innerR, d, 0.85f, 1, 0.15f, 0.85f, light);

        // 3. 4 Corner Gusset Reinforcement Plates (diagonal corner fillets)
        float cOuter = 1.42f;
        float cInner = 0.95f;
        // Top-Left Gusset
        quad(vc, m, n, -cOuter, innerR, -d, -innerR, cOuter, -d, -cInner, cInner, -d, -cInner, cInner, -d, 0, 0.2f, 0, 0.2f, light);
        // Top-Right Gusset
        quad(vc, m, n, innerR, cOuter, -d, cOuter, innerR, -d, cInner, cInner, -d, cInner, cInner, -d, 0.8f, 1, 0, 0.2f, light);
        // Bottom-Left Gusset
        quad(vc, m, n, -innerR, -cOuter, -d, -cOuter, -innerR, -d, -cInner, -cInner, -d, -cInner, -cInner, -d, 0, 0.2f, 0.8f, 1, light);
        // Bottom-Right Gusset
        quad(vc, m, n, cOuter, -innerR, -d, innerR, -cOuter, -d, cInner, -cInner, -d, cInner, -cInner, -d, 0.8f, 1, 0.8f, 1, light);

        // 4. 4 Outer Perimeter Casing Walls (Top, Bottom, Left, Right)
        quad(vc, m, n, -r, r, d, r, r, d, r, r, -d, -r, r, -d, 0, 1, 0, 1, light);
        quad(vc, m, n, -r, -r, -d, r, -r, -d, r, -r, d, -r, -r, d, 0, 1, 0, 1, light);
        quad(vc, m, n, -r, -r, -d, -r, -r, d, -r, r, d, -r, r, -d, 0, 1, 0, 1, light);
        quad(vc, m, n, r, -r, d, r, -r, -d, r, r, -d, r, r, d, 0, 1, 0, 1, light);

        // 5. True 3D Cylindrical Internal Duct Tunnel (12-segment steel cylinder)
        int segments = 12;
        float tau = (float) (Math.PI * 2.0);
        for (int i = 0; i < segments; i++) {
            float a0 = (i * tau) / segments;
            float a1 = ((i + 1) * tau) / segments;

            float x0 = Mth.cos(a0) * innerR;
            float y0 = Mth.sin(a0) * innerR;
            float x1 = Mth.cos(a1) * innerR;
            float y1 = Mth.sin(a1) * innerR;

            float u0 = (float) i / segments;
            float u1 = (float) (i + 1) / segments;

            // Inward-facing tunnel quads
            quad(vc, m, n, x0, y0, -d, x1, y1, -d, x1, y1, d, x0, y0, d, u0, u1, 0, 1, light);
        }
    }

    private static void renderMotorAndSupportStruts(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float hr = 0.34f;
        float hz = 0.24f;
        float innerR = 1.25f;
        float strutW = 0.07f;

        // 1. 4 Heavy Diagonal Structural Mounting Struts (45°, 135°, 225°, 315°)
        for (int s = 0; s < 4; s++) {
            float angle = (float) ((s * 0.5 + 0.25) * Math.PI);
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);

            float nx = -sin * strutW;
            float ny = cos * strutW;

            float xIn0 = cos * hr - nx;
            float yIn0 = sin * hr - ny;
            float xIn1 = cos * hr + nx;
            float yIn1 = sin * hr + ny;

            float xOut0 = cos * innerR - nx;
            float yOut0 = sin * innerR - ny;
            float xOut1 = cos * innerR + nx;
            float yOut1 = sin * innerR + ny;

            // Front face of strut
            doubleQuad(vc, m, n, xIn0, yIn0, -0.06f, xOut0, yOut0, -0.06f, xOut1, yOut1, -0.06f, xIn1, yIn1, -0.06f, 0, 1, 0.4f, 0.6f, light);
            // Rear face of strut
            doubleQuad(vc, m, n, xIn1, yIn1, 0.06f, xOut1, yOut1, 0.06f, xOut0, yOut0, 0.06f, xIn0, yIn0, 0.06f, 0, 1, 0.4f, 0.6f, light);
            // Top/Side faces of strut
            doubleQuad(vc, m, n, xIn0, yIn0, -0.06f, xIn0, yIn0, 0.06f, xOut0, yOut0, 0.06f, xOut0, yOut0, -0.06f, 0, 1, 0, 0.2f, light);
            doubleQuad(vc, m, n, xIn1, yIn1, 0.06f, xIn1, yIn1, -0.06f, xOut1, yOut1, -0.06f, xOut1, yOut1, 0.06f, 0, 1, 0, 0.2f, light);
        }

        // 2. 3D Octagonal Center Motor Housing (Stationary Core)
        int hubSegments = 8;
        float tau = (float) (Math.PI * 2.0);

        for (int i = 0; i < hubSegments; i++) {
            float a0 = (i * tau) / hubSegments;
            float a1 = ((i + 1) * tau) / hubSegments;

            float x0 = Mth.cos(a0) * hr;
            float y0 = Mth.sin(a0) * hr;
            float x1 = Mth.cos(a1) * hr;
            float y1 = Mth.sin(a1) * hr;

            // Front octagonal cap pie-slice (z = -hz)
            quad(vc, m, n, 0, 0, -hz, x0, y0, -hz, x1, y1, -hz, 0, 0, -hz, 0.5f, 0.5f + x0 * 0.5f, 0.5f, 0.5f + y0 * 0.5f, light);
            // Rear octagonal cap pie-slice (z = +hz)
            quad(vc, m, n, 0, 0, hz, x1, y1, hz, x0, y0, hz, 0, 0, hz, 0.5f, 0.5f + x1 * 0.5f, 0.5f, 0.5f + y1 * 0.5f, light);

            // Side cylinder wall panel
            quad(vc, m, n, x0, y0, -hz, x0, y0, hz, x1, y1, hz, x1, y1, -hz, (float) i / hubSegments, (float) (i + 1) / hubSegments, 0.2f, 0.8f, light);
        }
    }

    private static void renderGrates(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float r = 1.25f;
        float d = 0.44f;

        // Front Grate (Double-sided: visible from outside and inside)
        doubleQuad(vc, m, n, -r, -r, -d, r, -r, -d, r, r, -d, -r, r, -d, 0, 1, 0, 1, light);

        // Rear Grate (Double-sided: visible from outside and inside)
        doubleQuad(vc, m, n, -r, -r, d, r, -r, d, r, r, d, -r, r, d, 0, 1, 0, 1, light);
    }

    private static void renderBlades(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        int bladeCount = 5;
        float rHub = 0.34f;
        float rBlade = 1.22f;
        float rootW = 0.38f;
        float tipW = 0.65f;
        float pitchRoot = 0.20f;
        float pitchTip = 0.10f;

        // Front Center Rotating Bullet Spinner
        float spinR = 0.22f;
        float spinZ = -0.32f;
        int spinSeg = 8;
        float tau = (float) (Math.PI * 2.0);
        for (int i = 0; i < spinSeg; i++) {
            float a0 = (i * tau) / spinSeg;
            float a1 = ((i + 1) * tau) / spinSeg;
            float x0 = Mth.cos(a0) * spinR, y0 = Mth.sin(a0) * spinR;
            float x1 = Mth.cos(a1) * spinR, y1 = Mth.sin(a1) * spinR;
            quad(vc, m, n, 0, 0, spinZ, x0, y0, -0.24f, x1, y1, -0.24f, 0, 0, spinZ, 0.5f, 0.5f + x0, 0.5f, 0.5f + y0, light);
        }

        // 5 Aerodynamic Impeller Blades
        for (int i = 0; i < bladeCount; i++) {
            float angle = (float) (i * (Math.PI * 2.0 / bladeCount));
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);

            float nx = -sin, ny = cos;

            // Root section
            float x0 = cos * rHub - nx * (rootW * 0.5f);
            float y0 = sin * rHub - ny * (rootW * 0.5f);
            float z0 = -pitchRoot * 0.5f;

            float x1 = cos * rHub + nx * (rootW * 0.5f);
            float y1 = sin * rHub + ny * (rootW * 0.5f);
            float z1 = pitchRoot * 0.5f;

            // Tip section
            float x2 = cos * rBlade + nx * (tipW * 0.5f);
            float y2 = sin * rBlade + ny * (tipW * 0.5f);
            float z2 = pitchTip;

            float x3 = cos * rBlade - nx * (tipW * 0.5f);
            float y3 = sin * rBlade - ny * (tipW * 0.5f);
            float z3 = -pitchTip;

            // Double-sided aerodynamic blade faces
            doubleQuad(vc, m, n, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, 0, 1, 0, 1, light);

            // Solid leading edge & trailing edge thickness
            doubleQuad(vc, m, n, x0, y0, z0, x3, y3, z3, x3, y3, z3 - 0.02f, x0, y0, z0 - 0.02f, 0, 1, 0, 0.2f, light);
            doubleQuad(vc, m, n, x1, y1, z1, x2, y2, z2, x2, y2, z2 + 0.02f, x1, y1, z1 + 0.02f, 0, 1, 0.8f, 1, light);
        }
    }

    // =========================================================================
    // 1x1 COMPACT WALL FAN
    // =========================================================================

    private static void renderWallFanCasing(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float r = 0.48f;
        float d = 0.48f;
        float innerR = 0.40f;

        // Front Face Plates
        quad(vc, m, n, -r, r, -d, r, r, -d, r, innerR, -d, -r, innerR, -d, 0, 1, 0, 0.15f, light);
        quad(vc, m, n, -r, -innerR, -d, r, -innerR, -d, r, -r, -d, -r, -r, -d, 0, 1, 0.85f, 1, light);
        quad(vc, m, n, -r, innerR, -d, -innerR, innerR, -d, -innerR, -innerR, -d, -r, -innerR, -d, 0, 0.15f, 0.15f, 0.85f, light);
        quad(vc, m, n, innerR, innerR, -d, r, innerR, -d, r, -innerR, -d, innerR, -innerR, -d, 0.85f, 1, 0.15f, 0.85f, light);

        // Rear Face Plates
        quad(vc, m, n, r, r, d, -r, r, d, -r, innerR, d, r, innerR, d, 0, 1, 0, 0.15f, light);
        quad(vc, m, n, r, -innerR, d, -r, -innerR, d, -r, -r, d, r, -r, d, 0, 1, 0.85f, 1, light);
        quad(vc, m, n, r, innerR, d, innerR, innerR, d, innerR, -innerR, d, r, -innerR, d, 0, 0.15f, 0.15f, 0.85f, light);
        quad(vc, m, n, -innerR, innerR, d, -r, innerR, d, -r, -innerR, d, -innerR, -innerR, d, 0.85f, 1, 0.15f, 0.85f, light);

        // Internal cylindrical duct tunnel (8 segments)
        int segs = 8;
        float tau = (float) (Math.PI * 2.0);
        for (int i = 0; i < segs; i++) {
            float a0 = (i * tau) / segs;
            float a1 = ((i + 1) * tau) / segs;
            float x0 = Mth.cos(a0) * innerR, y0 = Mth.sin(a0) * innerR;
            float x1 = Mth.cos(a1) * innerR, y1 = Mth.sin(a1) * innerR;
            quad(vc, m, n, x0, y0, -d, x1, y1, -d, x1, y1, d, x0, y0, d, (float) i / segs, (float) (i + 1) / segs, 0, 1, light);
        }
    }

    private static void renderWallFanGrates(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float r = 0.40f;
        float d = 0.44f;

        // Front Grate (Double-sided)
        doubleQuad(vc, m, n, -r, -r, -d, r, -r, -d, r, r, -d, -r, r, -d, 0, 1, 0, 1, light);
        // Rear Grate (Double-sided)
        doubleQuad(vc, m, n, -r, -r, d, r, -r, d, r, r, d, -r, r, d, 0, 1, 0, 1, light);
    }

    private static void renderWallFanMotorHub(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        float hr = 0.14f;
        float hz = 0.18f;
        float innerR = 0.40f;
        float strutW = 0.03f;

        // 4 Support struts
        for (int s = 0; s < 4; s++) {
            float angle = (float) (s * 0.5 * Math.PI);
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            float nx = -sin * strutW, ny = cos * strutW;

            doubleQuad(vc, m, n,
                    cos * hr - nx, sin * hr - ny, 0.0f,
                    cos * innerR - nx, sin * innerR - ny, 0.0f,
                    cos * innerR + nx, sin * innerR + ny, 0.0f,
                    cos * hr + nx, sin * hr + ny, 0.0f,
                    0, 1, 0.4f, 0.6f, light);
        }

        // 3D Octagonal center motor hub
        int segs = 8;
        float tau = (float) (Math.PI * 2.0);
        for (int i = 0; i < segs; i++) {
            float a0 = (i * tau) / segs, a1 = ((i + 1) * tau) / segs;
            float x0 = Mth.cos(a0) * hr, y0 = Mth.sin(a0) * hr;
            float x1 = Mth.cos(a1) * hr, y1 = Mth.sin(a1) * hr;

            quad(vc, m, n, 0, 0, -hz, x0, y0, -hz, x1, y1, -hz, 0, 0, -hz, 0.5f, 0.5f + x0, 0.5f, 0.5f + y0, light);
            quad(vc, m, n, 0, 0, hz, x1, y1, hz, x0, y0, hz, 0, 0, hz, 0.5f, 0.5f + x1, 0.5f, 0.5f + y1, light);
            quad(vc, m, n, x0, y0, -hz, x0, y0, hz, x1, y1, hz, x1, y1, -hz, (float) i / segs, (float) (i + 1) / segs, 0.2f, 0.8f, light);
        }
    }

    private static void renderWallFanBlades(VertexConsumer vc, Matrix4f m, Matrix3f n, int light) {
        int bladeCount = 4;
        float rHub = 0.14f;
        float rBlade = 0.38f;
        float rootW = 0.14f;
        float tipW = 0.22f;
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

            doubleQuad(vc, m, n, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, 0, 1, 0, 1, light);
        }
    }

    // =========================================================================
    // RENDERING UTILITIES
    // =========================================================================

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

    private static void doubleQuad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   float x3, float y3, float z3,
                                   float u0, float u1, float v0, float v1, int light) {
        // Front face (CCW)
        quad(vc, m, n, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, u1, v0, v1, light);
        // Back face (CW)
        quad(vc, m, n, x3, y3, z3, x2, y2, z2, x1, y1, z1, x0, y0, z0, u0, u1, v0, v1, light);
    }
}
