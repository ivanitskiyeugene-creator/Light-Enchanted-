package dev.zerosevennine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.block.AbstractCameraBlock;
import dev.zerosevennine.block.EzCameraBlock;
import dev.zerosevennine.block.LczCameraBlock;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.client.handler.ClientCameraHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class CameraBlockEntityRenderer implements BlockEntityRenderer<CameraBlockEntity> {
    private static final ResourceLocation HCZ_TEX = new ResourceLocation(ZeroSevenNine.MOD_ID, "textures/block/hcz_camera.png");
    private static final ResourceLocation LCZ_TEX = new ResourceLocation(ZeroSevenNine.MOD_ID, "textures/block/lcz_camera.png");
    private static final ResourceLocation EZ_TEX = new ResourceLocation(ZeroSevenNine.MOD_ID, "textures/block/ez_camera.png");

    public CameraBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CameraBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();

        // When in 079 mode, make the active camera (and any camera within 2.5 blocks) invisible so it never obstructs the player's view
        if (ClientCameraHandler.in079Mode) {
            if (be.isOccupied() || be.getBlockPos().equals(ClientCameraHandler.activeCameraPos)) {
                return;
            }
            if (mc.cameraEntity != null) {
                double distSq = mc.cameraEntity.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5);
                if (distSq < 6.25) {
                    return;
                }
            }
        }

        poseStack.pushPose();

        Direction facing = Direction.NORTH;
        if (be.getBlockState().hasProperty(AbstractCameraBlock.FACING)) {
            facing = be.getBlockState().getValue(AbstractCameraBlock.FACING);
        }

        // Center origin
        poseStack.translate(0.5, 0.5, 0.5);

        // Orient base bracket towards wall facing
        float baseAngle = switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            case UP -> 0.0f;
            case DOWN -> 0.0f;
            default -> 0.0f;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(baseAngle));

        ResourceLocation texture = HCZ_TEX;
        float rTint = 0.28f, gTint = 0.30f, bTint = 0.33f; // HCZ: Dark Gunmetal
        float armorR = 0.18f, armorG = 0.20f, armorB = 0.22f;
        float hoodR = 0.22f, hoodG = 0.24f, hoodB = 0.26f;

        if (be.getBlockState().getBlock() instanceof LczCameraBlock) {
            texture = LCZ_TEX;
            rTint = 0.68f; gTint = 0.70f; bTint = 0.74f; // LCZ: Industrial Grey
            armorR = 0.45f; armorG = 0.48f; armorB = 0.52f;
            hoodR = 0.52f; hoodG = 0.55f; hoodB = 0.58f;
        } else if (be.getBlockState().getBlock() instanceof EzCameraBlock) {
            texture = EZ_TEX;
            rTint = 0.88f; gTint = 0.90f; bTint = 0.94f; // EZ: Clean Foundation White
            armorR = 0.72f; armorG = 0.75f; armorB = 0.80f;
            hoodR = 0.80f; hoodG = 0.82f; hoodB = 0.86f;
        }

        VertexConsumer builder = buffer.getBuffer(RenderType.entityCutout(texture));

        // ==========================================
        // 1. Static Wall Mount Assembly
        // ==========================================
        // Heavy Wall Mounting Baseplate (chamfered flush against wall)
        renderBox(poseStack, builder, -0.28f, -0.28f, 0.40f, 0.28f, 0.28f, 0.50f, packedLight, packedOverlay, armorR, armorG, armorB, 1.0f);
        renderBox(poseStack, builder, -0.24f, -0.24f, 0.38f, 0.24f, 0.24f, 0.40f, packedLight, packedOverlay, rTint, gTint, bTint, 1.0f);

        // 4 Corner Hex Bolts
        float boltCol = 0.12f;
        renderBox(poseStack, builder, -0.23f, 0.19f, 0.36f, -0.19f, 0.23f, 0.38f, packedLight, packedOverlay, boltCol, boltCol, boltCol, 1.0f);
        renderBox(poseStack, builder, 0.19f, 0.19f, 0.36f, 0.23f, 0.23f, 0.38f, packedLight, packedOverlay, boltCol, boltCol, boltCol, 1.0f);
        renderBox(poseStack, builder, -0.23f, -0.23f, 0.36f, -0.19f, -0.19f, 0.38f, packedLight, packedOverlay, boltCol, boltCol, boltCol, 1.0f);
        renderBox(poseStack, builder, 0.19f, -0.23f, 0.36f, 0.23f, -0.19f, 0.38f, packedLight, packedOverlay, boltCol, boltCol, boltCol, 1.0f);

        // Central Conduit Socket Hub
        renderBox(poseStack, builder, -0.10f, -0.10f, 0.32f, 0.10f, 0.10f, 0.38f, packedLight, packedOverlay, armorR, armorG, armorB, 1.0f);

        // Reinforced Cantilever Support Arm
        renderBox(poseStack, builder, -0.08f, -0.10f, 0.14f, 0.08f, 0.10f, 0.32f, packedLight, packedOverlay, rTint, gTint, bTint, 1.0f);

        // Under-arm Ribbed Cable Conduit
        renderBox(poseStack, builder, -0.04f, -0.14f, 0.16f, 0.04f, -0.10f, 0.36f, packedLight, packedOverlay, 0.10f, 0.10f, 0.12f, 1.0f);

        // Gimbal Turntable Pan Base
        renderBox(poseStack, builder, -0.11f, -0.11f, 0.08f, 0.11f, 0.11f, 0.14f, packedLight, packedOverlay, armorR, armorG, armorB, 1.0f);

        // Dual Fork Gimbal Arms (Motorized Swivel Yolk)
        renderBox(poseStack, builder, -0.21f, -0.10f, -0.04f, -0.17f, 0.10f, 0.10f, packedLight, packedOverlay, armorR, armorG, armorB, 1.0f);
        renderBox(poseStack, builder, 0.17f, -0.10f, -0.04f, 0.21f, 0.10f, 0.10f, packedLight, packedOverlay, armorR, armorG, armorB, 1.0f);

        // Side Pivot Motor Bearings
        renderBox(poseStack, builder, -0.22f, -0.04f, -0.02f, -0.16f, 0.04f, 0.06f, packedLight, packedOverlay, 0.15f, 0.16f, 0.18f, 1.0f);
        renderBox(poseStack, builder, 0.16f, -0.04f, -0.02f, 0.22f, 0.04f, 0.06f, packedLight, packedOverlay, 0.15f, 0.16f, 0.18f, 1.0f);

        // ==========================================
        // 2. Dynamic Swivel Camera Head (Yaw & Pitch)
        // ==========================================
        float baseYaw = switch (facing) {
            case NORTH -> 180.0f;
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };

        float relYaw = be.currentYaw - baseYaw;
        while (relYaw < -180.0f) relYaw += 360.0f;
        while (relYaw > 180.0f) relYaw -= 360.0f;

        float pitch = be.currentPitch;

        poseStack.pushPose();
        poseStack.translate(0.0, 0.0, 0.02);
        poseStack.mulPose(Axis.YP.rotationDegrees(-relYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        // Main Camera Body Housing
        renderBox(poseStack, builder, -0.18f, -0.15f, -0.26f, 0.18f, 0.15f, 0.06f, packedLight, packedOverlay, rTint, gTint, bTint, 1.0f);

        // Aerodynamic Protective Sunshield / Visor Hood
        renderBox(poseStack, builder, -0.20f, 0.14f, -0.38f, 0.20f, 0.18f, 0.08f, packedLight, packedOverlay, hoodR, hoodG, hoodB, 1.0f);

        // Dual Side Armor & Heat Dissipation Sinks
        renderBox(poseStack, builder, -0.20f, -0.12f, -0.22f, -0.18f, 0.12f, 0.02f, packedLight, packedOverlay, armorR, armorG, armorB, 1.0f);
        renderBox(poseStack, builder, 0.18f, -0.12f, -0.22f, 0.20f, 0.12f, 0.02f, packedLight, packedOverlay, armorR, armorG, armorB, 1.0f);

        // Rear Cable Gland & Exhaust Vent Grill
        renderBox(poseStack, builder, -0.13f, -0.12f, 0.06f, 0.13f, 0.12f, 0.09f, packedLight, packedOverlay, 0.14f, 0.15f, 0.17f, 1.0f);

        // ==========================================
        // 3. Front Optical Lens Assembly & LED Ring
        // ==========================================
        // Heavy 12-sided Tapered Lens Barrel Housing (from z = -0.26 to z = -0.365)
        renderCylinder(poseStack, builder, 0.0f, 0.0f, -0.26f, -0.365f, 0.145f, 12, 0.16f, 0.18f, 0.20f, 1.0f, packedLight, packedOverlay);

        // Recessed Dark Antireflective Optical Glass Face (z = -0.362)
        renderCircularDisk(poseStack, builder, 0.0f, 0.0f, -0.362f, 0.095f, 12, 0.04f, 0.06f, 0.09f, 1.0f, packedLight, packedOverlay);

        // Determine Mode: CASSIE (Idle) vs SCP-079 (Active/Watching)
        boolean is079 = be.isOccupied();
        int ledLight = LightTexture.FULL_BRIGHT;

        float time = (mc.level != null) ? (mc.level.getGameTime() + partialTick) : 0.0f;
        float pulse = (float) (0.85 + 0.15 * Math.sin(time * 0.15));

        // LED Ring Colors
        float ringR, ringG, ringB;
        float pupilR, pupilG, pupilB;

        if (is079) {
            // SCP-079 ACTIVE: Brilliant Radiant Ice-Cyan / Electric Blue
            ringR = 0.05f;
            ringG = 0.95f;
            ringB = 1.00f;

            // Glowing Optical Iris / Aperture Pupil Core
            pupilR = 0.30f;
            pupilG = 1.00f;
            pupilB = 1.00f;
        } else {
            // CASSIE / INACTIVE: Vibrant Crimson Red with Breathing Wave
            ringR = 1.00f * pulse;
            ringG = 0.06f * pulse;
            ringB = 0.06f * pulse;

            // Faint Red Core Glint
            pupilR = 0.40f * pulse;
            pupilG = 0.02f * pulse;
            pupilB = 0.02f * pulse;
        }

        // =========================================================
        // 4. THE ICONIC CIRCULAR GLOWING LED RING (КРУГЛАЯ КАЙМА)
        // =========================================================
        // Continuous 16-Segment Radial Halo Ring around the lens barrel face (z = -0.368)
        renderCircularLedRing(poseStack, builder, 0.0f, 0.0f, -0.368f, 0.095f, 0.138f, 16, ringR, ringG, ringB, 1.0f, ledLight, packedOverlay);

        // Outer Protective Metallic Bezel Rim
        renderCircularLedRing(poseStack, builder, 0.0f, 0.0f, -0.367f, 0.138f, 0.146f, 16, 0.22f, 0.24f, 0.27f, 1.0f, packedLight, packedOverlay);

        // Center Optical Iris / Aperture Pupil Disk (z = -0.364)
        renderCircularDisk(poseStack, builder, 0.0f, 0.0f, -0.364f, 0.045f, 12, pupilR, pupilG, pupilB, 1.0f, ledLight, packedOverlay);

        // ==========================================
        // 5. Side Auxiliary Status Indicators
        // ==========================================
        // Left Side Status Indicator Bar
        float leftR = is079 ? 0.05f : 1.0f;
        float leftG = is079 ? 0.92f : 0.45f;
        float leftB = is079 ? 1.00f : 0.0f;
        renderBox(poseStack, builder, -0.205f, 0.05f, -0.12f, -0.201f, 0.08f, -0.06f, ledLight, packedOverlay, leftR, leftG, leftB, 1.0f);

        // Right Side Data Transfer LED
        float rightR = is079 ? 0.15f : 0.0f;
        float rightG = is079 ? 0.85f : 0.80f;
        float rightB = is079 ? 1.00f : 0.20f;
        renderBox(poseStack, builder, 0.201f, 0.05f, -0.12f, 0.205f, 0.08f, -0.06f, ledLight, packedOverlay, rightR, rightG, rightB, 1.0f);

        poseStack.popPose();
        poseStack.popPose();
    }

    private void renderCircularLedRing(PoseStack poseStack, VertexConsumer builder,
                                       float centerX, float centerY, float z,
                                       float innerR, float outerR, int segments,
                                       float r, float g, float b, float a,
                                       int packedLight, int packedOverlay) {
        Matrix4f mat = poseStack.last().pose();
        Matrix3f nmat = poseStack.last().normal();

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * 2.0 * Math.PI / segments);
            float angle2 = (float) ((i + 1) * 2.0 * Math.PI / segments);

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            float inX1 = centerX + innerR * cos1;
            float inY1 = centerY + innerR * sin1;
            float outX1 = centerX + outerR * cos1;
            float outY1 = centerY + outerR * sin1;

            float inX2 = centerX + innerR * cos2;
            float inY2 = centerY + innerR * sin2;
            float outX2 = centerX + outerR * cos2;
            float outY2 = centerY + outerR * sin2;

            vertex(builder, mat, nmat, inX1, inY1, z, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);
            vertex(builder, mat, nmat, outX1, outY1, z, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);
            vertex(builder, mat, nmat, outX2, outY2, z, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);
            vertex(builder, mat, nmat, inX2, inY2, z, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);
        }
    }

    private void renderCircularDisk(PoseStack poseStack, VertexConsumer builder,
                                    float centerX, float centerY, float z,
                                    float radius, int segments,
                                    float r, float g, float b, float a,
                                    int packedLight, int packedOverlay) {
        Matrix4f mat = poseStack.last().pose();
        Matrix3f nmat = poseStack.last().normal();

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * 2.0 * Math.PI / segments);
            float angle2 = (float) ((i + 1) * 2.0 * Math.PI / segments);

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            float x1 = centerX + radius * cos1;
            float y1 = centerY + radius * sin1;
            float x2 = centerX + radius * cos2;
            float y2 = centerY + radius * sin2;

            vertex(builder, mat, nmat, centerX, centerY, z, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);
            vertex(builder, mat, nmat, x1, y1, z, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);
            vertex(builder, mat, nmat, x2, y2, z, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);
            vertex(builder, mat, nmat, centerX, centerY, z, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);
        }
    }

    private void renderCylinder(PoseStack poseStack, VertexConsumer builder,
                                float centerX, float centerY, float zStart, float zEnd,
                                float radius, int segments,
                                float r, float g, float b, float a,
                                int packedLight, int packedOverlay) {
        Matrix4f mat = poseStack.last().pose();
        Matrix3f nmat = poseStack.last().normal();

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * 2.0 * Math.PI / segments);
            float angle2 = (float) ((i + 1) * 2.0 * Math.PI / segments);

            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            float cos2 = (float) Math.cos(angle2);
            float sin2 = (float) Math.sin(angle2);

            float x1 = centerX + radius * cos1;
            float y1 = centerY + radius * sin1;
            float x2 = centerX + radius * cos2;
            float y2 = centerY + radius * sin2;

            float nx = (cos1 + cos2) * 0.5f;
            float ny = (sin1 + sin2) * 0.5f;

            vertex(builder, mat, nmat, x1, y1, zStart, r, g, b, a, packedLight, packedOverlay, nx, ny, 0);
            vertex(builder, mat, nmat, x2, y2, zStart, r, g, b, a, packedLight, packedOverlay, nx, ny, 0);
            vertex(builder, mat, nmat, x2, y2, zEnd, r, g, b, a, packedLight, packedOverlay, nx, ny, 0);
            vertex(builder, mat, nmat, x1, y1, zEnd, r, g, b, a, packedLight, packedOverlay, nx, ny, 0);
        }
    }

    private void renderBox(PoseStack poseStack, VertexConsumer builder,
                           float minX, float minY, float minZ,
                           float maxX, float maxY, float maxZ,
                           int packedLight, int packedOverlay,
                           float r, float g, float b, float a) {
        Matrix4f mat = poseStack.last().pose();
        Matrix3f nmat = poseStack.last().normal();

        // North face (-Z)
        vertex(builder, mat, nmat, minX, maxY, minZ, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);
        vertex(builder, mat, nmat, maxX, maxY, minZ, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);
        vertex(builder, mat, nmat, maxX, minY, minZ, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);
        vertex(builder, mat, nmat, minX, minY, minZ, r, g, b, a, packedLight, packedOverlay, 0, 0, -1);

        // South face (+Z)
        vertex(builder, mat, nmat, maxX, maxY, maxZ, r, g, b, a, packedLight, packedOverlay, 0, 0, 1);
        vertex(builder, mat, nmat, minX, maxY, maxZ, r, g, b, a, packedLight, packedOverlay, 0, 0, 1);
        vertex(builder, mat, nmat, minX, minY, maxZ, r, g, b, a, packedLight, packedOverlay, 0, 0, 1);
        vertex(builder, mat, nmat, maxX, minY, maxZ, r, g, b, a, packedLight, packedOverlay, 0, 0, 1);

        // West face (-X)
        vertex(builder, mat, nmat, minX, maxY, maxZ, r, g, b, a, packedLight, packedOverlay, -1, 0, 0);
        vertex(builder, mat, nmat, minX, maxY, minZ, r, g, b, a, packedLight, packedOverlay, -1, 0, 0);
        vertex(builder, mat, nmat, minX, minY, minZ, r, g, b, a, packedLight, packedOverlay, -1, 0, 0);
        vertex(builder, mat, nmat, minX, minY, maxZ, r, g, b, a, packedLight, packedOverlay, -1, 0, 0);

        // East face (+X)
        vertex(builder, mat, nmat, maxX, maxY, minZ, r, g, b, a, packedLight, packedOverlay, 1, 0, 0);
        vertex(builder, mat, nmat, maxX, maxY, maxZ, r, g, b, a, packedLight, packedOverlay, 1, 0, 0);
        vertex(builder, mat, nmat, maxX, minY, maxZ, r, g, b, a, packedLight, packedOverlay, 1, 0, 0);
        vertex(builder, mat, nmat, maxX, minY, minZ, r, g, b, a, packedLight, packedOverlay, 1, 0, 0);

        // Top face (+Y)
        vertex(builder, mat, nmat, minX, maxY, maxZ, r, g, b, a, packedLight, packedOverlay, 0, 1, 0);
        vertex(builder, mat, nmat, maxX, maxY, maxZ, r, g, b, a, packedLight, packedOverlay, 0, 1, 0);
        vertex(builder, mat, nmat, maxX, maxY, minZ, r, g, b, a, packedLight, packedOverlay, 0, 1, 0);
        vertex(builder, mat, nmat, minX, maxY, minZ, r, g, b, a, packedLight, packedOverlay, 0, 1, 0);

        // Bottom face (-Y)
        vertex(builder, mat, nmat, minX, minY, minZ, r, g, b, a, packedLight, packedOverlay, 0, -1, 0);
        vertex(builder, mat, nmat, maxX, minY, minZ, r, g, b, a, packedLight, packedOverlay, 0, -1, 0);
        vertex(builder, mat, nmat, maxX, minY, maxZ, r, g, b, a, packedLight, packedOverlay, 0, -1, 0);
        vertex(builder, mat, nmat, minX, minY, maxZ, r, g, b, a, packedLight, packedOverlay, 0, -1, 0);
    }

    private void vertex(VertexConsumer builder, Matrix4f mat, Matrix3f nmat, float x, float y, float z,
                        float r, float g, float b, float a, int light, int overlay,
                        float nx, float ny, float nz) {
        builder.vertex(mat, x, y, z)
                .color(r, g, b, a)
                .uv(0.0f, 0.0f)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(nmat, nx, ny, nz)
                .endVertex();
    }
}
