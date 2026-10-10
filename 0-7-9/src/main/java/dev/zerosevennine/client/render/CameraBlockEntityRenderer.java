package dev.zerosevennine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.block.AbstractCameraBlock;
import dev.zerosevennine.block.EzCameraBlock;
import dev.zerosevennine.block.HczCameraBlock;
import dev.zerosevennine.block.LczCameraBlock;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
        float rTint = 1.0f, gTint = 1.0f, bTint = 1.0f;

        if (be.getBlockState().getBlock() instanceof LczCameraBlock) {
            texture = LCZ_TEX;
        } else if (be.getBlockState().getBlock() instanceof EzCameraBlock) {
            texture = EZ_TEX;
        }

        VertexConsumer builder = buffer.getBuffer(RenderType.entityCutout(texture));

        // ==========================================
        // 1. Static Wall Mount Assembly
        // ==========================================
        // Baseplate on Wall
        renderBox(poseStack, builder, -0.28f, -0.28f, 0.42f, 0.28f, 0.28f, 0.50f, packedLight, packedOverlay, 0.35f, 0.38f, 0.42f, 1.0f);
        // Corner Bolts
        renderBox(poseStack, builder, -0.24f, 0.20f, 0.40f, -0.20f, 0.24f, 0.42f, packedLight, packedOverlay, 0.2f, 0.2f, 0.2f, 1.0f);
        renderBox(poseStack, builder, 0.20f, 0.20f, 0.40f, 0.24f, 0.24f, 0.42f, packedLight, packedOverlay, 0.2f, 0.2f, 0.2f, 1.0f);
        renderBox(poseStack, builder, -0.24f, -0.24f, 0.40f, -0.20f, -0.20f, 0.42f, packedLight, packedOverlay, 0.2f, 0.2f, 0.2f, 1.0f);
        renderBox(poseStack, builder, 0.20f, -0.24f, 0.40f, 0.24f, -0.20f, 0.42f, packedLight, packedOverlay, 0.2f, 0.2f, 0.2f, 1.0f);

        // Heavy Cantilever Support Arm
        renderBox(poseStack, builder, -0.07f, -0.10f, 0.16f, 0.07f, 0.10f, 0.42f, packedLight, packedOverlay, 0.4f, 0.43f, 0.47f, 1.0f);
        // Under-arm Cable Conduit
        renderBox(poseStack, builder, -0.03f, -0.14f, 0.20f, 0.03f, -0.10f, 0.42f, packedLight, packedOverlay, 0.15f, 0.15f, 0.15f, 1.0f);

        // Gimbal Swivel Pivot Joint
        renderBox(poseStack, builder, -0.10f, -0.10f, 0.08f, 0.10f, 0.10f, 0.16f, packedLight, packedOverlay, 0.25f, 0.28f, 0.32f, 1.0f);

        // ==========================================
        // 2. Dynamic Swivel Camera Head (Yaw & Pitch)
        // ==========================================
        float yaw = be.currentYaw;
        float pitch = be.currentPitch;

        poseStack.pushPose();
        poseStack.translate(0.0, 0.0, 0.10);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        // Main Camera Body Housing
        renderBox(poseStack, builder, -0.20f, -0.16f, -0.28f, 0.20f, 0.16f, 0.05f, packedLight, packedOverlay, rTint, gTint, bTint, 1.0f);

        // Overhanging Sunshield / Protective Visor Hood
        renderBox(poseStack, builder, -0.22f, 0.15f, -0.38f, 0.22f, 0.19f, 0.06f, packedLight, packedOverlay, 0.3f, 0.33f, 0.37f, 1.0f);

        // Side Armor Plates / Heat Vents
        renderBox(poseStack, builder, -0.23f, -0.12f, -0.24f, -0.20f, 0.12f, 0.02f, packedLight, packedOverlay, 0.25f, 0.27f, 0.30f, 1.0f);
        renderBox(poseStack, builder, 0.20f, -0.12f, -0.24f, 0.23f, 0.12f, 0.02f, packedLight, packedOverlay, 0.25f, 0.27f, 0.30f, 1.0f);

        // Recessed Optical Lens Barrel Cylinder
        renderBox(poseStack, builder, -0.13f, -0.13f, -0.36f, 0.13f, 0.13f, -0.28f, packedLight, packedOverlay, 0.12f, 0.14f, 0.16f, 1.0f);

        // Antireflective Lens Glass Face
        renderBox(poseStack, builder, -0.09f, -0.09f, -0.37f, 0.09f, 0.09f, -0.36f, packedLight, packedOverlay, 0.15f, 0.25f, 0.35f, 1.0f);

        // Emissive LED Status Cluster: Red in Idle, Brilliant Cyan in Occupied (079)
        float ledR = be.isOccupied() ? 0.0f : 1.0f;
        float ledG = be.isOccupied() ? 0.9f : 0.1f;
        float ledB = be.isOccupied() ? 1.0f : 0.1f;
        int ledLight = LightTexture.FULL_BRIGHT;

        // Front Status LED Bar
        renderBox(poseStack, builder, -0.07f, 0.08f, -0.29f, 0.07f, 0.13f, -0.28f, ledLight, packedOverlay, ledR, ledG, ledB, 1.0f);

        poseStack.popPose();
        poseStack.popPose();
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
