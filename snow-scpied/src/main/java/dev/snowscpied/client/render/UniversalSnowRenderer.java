package dev.snowscpied.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.snowscpied.SnowSCPied;
import dev.snowscpied.config.SnowConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * True 3D Procedural Volumetric Snow Mantle:
 * - Conforms to the EXACT 3D sub-elements, stairs, fences, beams, fans, trusses and modded shapes.
 * - Adds 3D snow depth (1.5 - 2 pixels thick volumetric cushion) with soft overhanging snow skirts/rims.
 * - Never a flat square over air!
 */
@Mod.EventBusSubscriber(modid = "snow_scpied", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class UniversalSnowRenderer {
    private static final ResourceLocation SNOW_MANTLE_TEX =
            new ResourceLocation(SnowSCPied.MOD_ID, "textures/block/snow_crust_overlay.png");

    private static final Set<BlockPos> MANUAL_SNOW_BLOCKS = new HashSet<>();
    private static final RandomSource RANDOM = RandomSource.create(42L);

    public static void toggleSnowOnBlock(BlockPos pos) {
        if (MANUAL_SNOW_BLOCKS.contains(pos)) {
            MANUAL_SNOW_BLOCKS.remove(pos);
        } else {
            MANUAL_SNOW_BLOCKS.add(pos.immutable());
        }
    }

    public static boolean hasSnow(BlockPos pos) {
        return MANUAL_SNOW_BLOCKS.contains(pos);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!SnowConfig.CLIENT.enableUniversalSnowOverlay.get()) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        Level level = Minecraft.getInstance().level;
        if (level == null || MANUAL_SNOW_BLOCKS.isEmpty()) return;

        BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entitySolid(SNOW_MANTLE_TEX));

        for (BlockPos pos : MANUAL_SNOW_BLOCKS) {
            double rx = pos.getX() - camPos.x;
            double ry = pos.getY() - camPos.y;
            double rz = pos.getZ() - camPos.z;

            if (rx * rx + ry * ry + rz * rz > 1024.0) continue;

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            poseStack.pushPose();
            poseStack.translate(rx, ry, rz);
            Matrix4f m = poseStack.last().pose();
            Matrix3f n = poseStack.last().normal();

            int packedLight = brd.getBlockModel(state) != null ? level.getLightEngine().getRawBrightness(pos.above(), 0) : 15;
            int light = (packedLight << 20) | (packedLight << 4);
            if (light == 0) light = 0xF000F0;

            // 1. First attempt: Conforming to exact VoxelShape sub-boxes (stairs, fences, slabs, custom blocks)
            VoxelShape shape = state.getShape(level, pos);
            List<AABB> boxes = shape.toAabbs();

            if (!boxes.isEmpty()) {
                for (AABB box : boxes) {
                    renderVolumetricSnowCap(vc, m, n,
                            (float) box.minX, (float) box.minZ,
                            (float) box.maxX, (float) box.maxZ,
                            (float) box.maxY, 0.08f, light);
                }
            } else {
                // 2. Fallback: Detailed BakedQuad analysis for complex modded meshes
                BakedModel model = brd.getBlockModel(state);
                if (model != null) {
                    List<BakedQuad> upQuads = model.getQuads(state, Direction.UP, RANDOM, ModelData.EMPTY, null);
                    for (BakedQuad q : upQuads) {
                        int[] vData = q.getVertices();
                        if (vData.length >= 32) {
                            float x0 = Float.intBitsToFloat(vData[0]);
                            float y0 = Float.intBitsToFloat(vData[1]);
                            float z0 = Float.intBitsToFloat(vData[2]);

                            float x1 = Float.intBitsToFloat(vData[8]);
                            float y1 = Float.intBitsToFloat(vData[9]);
                            float z1 = Float.intBitsToFloat(vData[10]);

                            float x2 = Float.intBitsToFloat(vData[16]);
                            float y2 = Float.intBitsToFloat(vData[17]);
                            float z2 = Float.intBitsToFloat(vData[18]);

                            float x3 = Float.intBitsToFloat(vData[24]);
                            float y3 = Float.intBitsToFloat(vData[25]);
                            float z3 = Float.intBitsToFloat(vData[26]);

                            float minX = Math.min(Math.min(x0, x1), Math.min(x2, x3));
                            float maxX = Math.max(Math.max(x0, x1), Math.max(x2, x3));
                            float minZ = Math.min(Math.min(z0, z1), Math.min(z2, z3));
                            float maxZ = Math.max(Math.max(z0, z1), Math.max(z2, z3));
                            float maxY = Math.max(Math.max(y0, y1), Math.max(y2, y3));

                            renderVolumetricSnowCap(vc, m, n, minX, minZ, maxX, maxZ, maxY, 0.08f, light);
                        }
                    }
                }
            }

            poseStack.popPose();
        }
    }

    /**
     * Renders a true 3D volumetric snow pillow cap matching the exact dimensions [minX, minZ] -> [maxX, maxZ]
     * with a raised top deck (height h) and 4 overhanging soft snow side skirts!
     */
    private static void renderVolumetricSnowCap(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                                float minX, float minZ,
                                                float maxX, float maxZ,
                                                float baseTopY, float snowHeight, int light) {
        float topY = baseTopY + snowHeight;
        float skirtBottomY = baseTopY - 0.03f;

        // Slight soft outward bulge for natural puffy snow drift
        float expand = 0.02f;
        float sMinX = minX - expand;
        float sMaxX = maxX + expand;
        float sMinZ = minZ - expand;
        float sMaxZ = maxZ + expand;

        // 1. Top Volumetric Snow Surface
        quad(vc, m, n, sMinX, topY, sMinZ, sMaxX, topY, sMinZ, sMaxX, topY, sMaxZ, sMinX, topY, sMaxZ, 0, 1, 0, 1, light);

        // 2. Front Overhanging Snow Skirt
        quad(vc, m, n, sMinX, topY, sMaxZ, sMaxX, topY, sMaxZ, sMaxX, skirtBottomY, sMaxZ, sMinX, skirtBottomY, sMaxZ, 0, 1, 0.7f, 1, light);

        // 3. Back Overhanging Snow Skirt
        quad(vc, m, n, sMaxX, topY, sMinZ, sMinX, topY, sMinZ, sMinX, skirtBottomY, sMinZ, sMaxX, skirtBottomY, sMinZ, 0, 1, 0.7f, 1, light);

        // 4. Left Overhanging Snow Skirt
        quad(vc, m, n, sMinX, topY, sMinZ, sMinX, topY, sMaxZ, sMinX, skirtBottomY, sMaxZ, sMinX, skirtBottomY, sMinZ, 0, 1, 0.7f, 1, light);

        // 5. Right Overhanging Snow Skirt
        quad(vc, m, n, sMaxX, topY, sMaxZ, sMaxX, topY, sMinZ, sMaxX, skirtBottomY, sMinZ, sMaxX, skirtBottomY, sMaxZ, 0, 1, 0.7f, 1, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float u0, float u1, float v0, float v1, int light) {
        vc.vertex(m, x0, y0, z0).color(255, 255, 255, 255).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x1, y1, z1).color(255, 255, 255, 255).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x2, y2, z2).color(255, 255, 255, 255).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x3, y3, z3).color(255, 255, 255, 255).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }
}
