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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Universal True 3D Procedural Volumetric Snow Mantle Engine:
 * - 6-Axis directional support: applies snow accurately to ANY clicked face (TOP, NORTH, SOUTH, WEST, EAST, BOTTOM).
 * - Multi-layer geometry extraction: perfectly conforms to individual sub-elements (stairs, fences, keypads, grates, fan housings).
 * - Full 3D volumetric depth with closed double-sided geometry (never hollow or see-through from any angle).
 * - Zero UV distortion with seamless isotropic snow crystals.
 */
@Mod.EventBusSubscriber(modid = "snow_scpied", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class UniversalSnowRenderer {
    private static final ResourceLocation SNOW_MANTLE_TEX =
            new ResourceLocation(SnowSCPied.MOD_ID, "textures/block/snow_crust_overlay.png");

    private static final Map<BlockPos, Set<Direction>> SNOW_DATA = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create(42L);

    public static void toggleSnowFace(BlockPos pos, Direction face) {
        BlockPos immutablePos = pos.immutable();
        if (SNOW_DATA.containsKey(immutablePos)) {
            Set<Direction> set = SNOW_DATA.get(immutablePos);
            if (set.contains(face)) {
                set.remove(face);
                if (set.isEmpty()) {
                    SNOW_DATA.remove(immutablePos);
                }
            } else {
                set.add(face);
            }
        } else {
            Set<Direction> set = EnumSet.of(face);
            SNOW_DATA.put(immutablePos, set);
        }
    }

    public static void toggleAllFaces(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        if (SNOW_DATA.containsKey(immutablePos)) {
            SNOW_DATA.remove(immutablePos);
        } else {
            SNOW_DATA.put(immutablePos, EnumSet.allOf(Direction.class));
        }
    }

    public static void toggleSnowOnBlock(BlockPos pos) {
        toggleSnowFace(pos, Direction.UP);
    }

    public static boolean hasSnow(BlockPos pos) {
        return SNOW_DATA.containsKey(pos);
    }

    public static boolean hasSnow(BlockPos pos, Direction face) {
        Set<Direction> set = SNOW_DATA.get(pos);
        return set != null && set.contains(face);
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
        if (level == null || SNOW_DATA.isEmpty()) return;

        BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entitySolid(SNOW_MANTLE_TEX));

        for (Map.Entry<BlockPos, Set<Direction>> entry : SNOW_DATA.entrySet()) {
            BlockPos pos = entry.getKey();
            Set<Direction> faces = entry.getValue();
            if (faces == null || faces.isEmpty()) continue;

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

            for (Direction face : faces) {
                renderFaceSnow(vc, m, n, level, pos, state, brd, face, light);
            }

            poseStack.popPose();
        }
    }

    private static void renderFaceSnow(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                       Level level, BlockPos pos, BlockState state,
                                       BlockRenderDispatcher brd, Direction face, int light) {
        List<AABB> targetBoxes = new ArrayList<>();
        BakedModel model = brd.getBlockModel(state);

        // 1. Try to extract directional model quads for custom modded sub-blocks (keypads, fans, trusses)
        if (model != null) {
            List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, face, RANDOM, ModelData.EMPTY, null));
            quads.addAll(model.getQuads(state, null, RANDOM, ModelData.EMPTY, null));

            for (BakedQuad q : quads) {
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
                    float minY = Math.min(Math.min(y0, y1), Math.min(y2, y3));
                    float maxY = Math.max(Math.max(y0, y1), Math.max(y2, y3));
                    float minZ = Math.min(Math.min(z0, z1), Math.min(z2, z3));
                    float maxZ = Math.max(Math.max(z0, z1), Math.max(z2, z3));

                    // Quad matches if it spans a non-zero area and belongs to this face quadrant
                    boolean matches = false;
                    switch (face) {
                        case UP -> matches = (maxY >= 0.5f || (maxY - minY < 0.05f));
                        case DOWN -> matches = (minY <= 0.5f || (maxY - minY < 0.05f));
                        case NORTH -> matches = (minZ <= 0.5f || (maxZ - minZ < 0.05f));
                        case SOUTH -> matches = (maxZ >= 0.5f || (maxZ - minZ < 0.05f));
                        case WEST -> matches = (minX <= 0.5f || (maxX - minX < 0.05f));
                        case EAST -> matches = (maxX >= 0.5f || (maxX - minX < 0.05f));
                    }

                    if (matches && (maxX > minX + 0.02f || maxZ > minZ + 0.02f || maxY > minY + 0.02f)) {
                        targetBoxes.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
                    }
                }
            }
        }

        // 2. Fallback to exact VoxelShape sub-boxes (stairs, fences, walls, slabs)
        if (targetBoxes.isEmpty()) {
            VoxelShape shape = state.getShape(level, pos);
            List<AABB> shapeBoxes = shape.toAabbs();
            if (!shapeBoxes.isEmpty()) {
                targetBoxes.addAll(shapeBoxes);
            } else {
                targetBoxes.add(new AABB(0, 0, 0, 1, 1, 1));
            }
        }

        // 3. Render a solid, closed, double-sided 3D snow cushion for each target box
        for (AABB box : targetBoxes) {
            renderBoxFaceSnow(vc, m, n, box, face, light);
        }
    }

    private static void renderBoxFaceSnow(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                          AABB box, Direction face, int light) {
        float snowThick = 0.05f;
        float skirtDepth = 0.02f;
        float expand = 0.01f;

        float minX = (float) box.minX;
        float maxX = (float) box.maxX;
        float minY = (float) box.minY;
        float maxY = (float) box.maxY;
        float minZ = (float) box.minZ;
        float maxZ = (float) box.maxZ;

        switch (face) {
            case UP -> {
                float topY = maxY + snowThick;
                float baseY = maxY - skirtDepth;
                float sx0 = minX - expand, sx1 = maxX + expand;
                float sz0 = minZ - expand, sz1 = maxZ + expand;

                // 1. Top snow surface
                doubleQuad(vc, m, n, sx0, topY, sz0, sx1, topY, sz0, sx1, topY, sz1, sx0, topY, sz1, 0, 1, 0, 1, 0, 1, 0, light);
                // 2. Base cap (against block surface - eliminates hollow bottom)
                doubleQuad(vc, m, n, sx0, baseY, sz0, sx0, baseY, sz1, sx1, baseY, sz1, sx1, baseY, sz0, 0, 1, 0, 1, 0, -1, 0, light);
                // 3. 4 Overhanging side skirts
                doubleQuad(vc, m, n, sx0, topY, sz1, sx1, topY, sz1, sx1, baseY, sz1, sx0, baseY, sz1, 0, 1, 0, 1, 0, 0, 1, light);
                doubleQuad(vc, m, n, sx1, topY, sz0, sx0, topY, sz0, sx0, baseY, sz0, sx1, baseY, sz0, 0, 1, 0, 1, 0, 0, -1, light);
                doubleQuad(vc, m, n, sx0, topY, sz0, sx0, topY, sz1, sx0, baseY, sz1, sx0, baseY, sz0, 0, 1, 0, 1, -1, 0, 0, light);
                doubleQuad(vc, m, n, sx1, topY, sz1, sx1, topY, sz0, sx1, baseY, sz0, sx1, baseY, sz1, 0, 1, 0, 1, 1, 0, 0, light);
            }
            case DOWN -> {
                float botY = minY - snowThick;
                float baseY = minY + skirtDepth;
                float sx0 = minX - expand, sx1 = maxX + expand;
                float sz0 = minZ - expand, sz1 = maxZ + expand;

                doubleQuad(vc, m, n, sx0, botY, sz0, sx0, botY, sz1, sx1, botY, sz1, sx1, botY, sz0, 0, 1, 0, 1, 0, -1, 0, light);
                doubleQuad(vc, m, n, sx0, baseY, sz0, sx1, baseY, sz0, sx1, baseY, sz1, sx0, baseY, sz1, 0, 1, 0, 1, 0, 1, 0, light);
                doubleQuad(vc, m, n, sx0, botY, sz1, sx1, botY, sz1, sx1, baseY, sz1, sx0, baseY, sz1, 0, 1, 0, 1, 0, 0, 1, light);
                doubleQuad(vc, m, n, sx1, botY, sz0, sx0, botY, sz0, sx0, baseY, sz0, sx1, baseY, sz0, 0, 1, 0, 1, 0, 0, -1, light);
                doubleQuad(vc, m, n, sx0, botY, sz0, sx0, botY, sz1, sx0, baseY, sz1, sx0, baseY, sz0, 0, 1, 0, 1, -1, 0, 0, light);
                doubleQuad(vc, m, n, sx1, botY, sz1, sx1, botY, sz0, sx1, baseY, sz0, sx1, baseY, sz1, 0, 1, 0, 1, 1, 0, 0, light);
            }
            case NORTH -> {
                float outerZ = minZ - snowThick;
                float baseZ = minZ + skirtDepth;
                float sx0 = minX - expand, sx1 = maxX + expand;
                float sy0 = minY - expand, sy1 = maxY + expand;

                // North front face
                doubleQuad(vc, m, n, sx1, sy1, outerZ, sx0, sy1, outerZ, sx0, sy0, outerZ, sx1, sy0, outerZ, 0, 1, 0, 1, 0, 0, -1, light);
                // Back cap
                doubleQuad(vc, m, n, sx0, sy1, baseZ, sx1, sy1, baseZ, sx1, sy0, baseZ, sx0, sy0, baseZ, 0, 1, 0, 1, 0, 0, 1, light);
                // 4 Side skirts (Top, Bottom, Left, Right)
                doubleQuad(vc, m, n, sx0, sy1, outerZ, sx1, sy1, outerZ, sx1, sy1, baseZ, sx0, sy1, baseZ, 0, 1, 0, 1, 0, 1, 0, light);
                doubleQuad(vc, m, n, sx0, sy0, baseZ, sx1, sy0, baseZ, sx1, sy0, outerZ, sx0, sy0, outerZ, 0, 1, 0, 1, 0, -1, 0, light);
                doubleQuad(vc, m, n, sx0, sy1, baseZ, sx0, sy1, outerZ, sx0, sy0, outerZ, sx0, sy0, baseZ, 0, 1, 0, 1, -1, 0, 0, light);
                doubleQuad(vc, m, n, sx1, sy1, outerZ, sx1, sy1, baseZ, sx1, sy0, baseZ, sx1, sy0, outerZ, 0, 1, 0, 1, 1, 0, 0, light);
            }
            case SOUTH -> {
                float outerZ = maxZ + snowThick;
                float baseZ = maxZ - skirtDepth;
                float sx0 = minX - expand, sx1 = maxX + expand;
                float sy0 = minY - expand, sy1 = maxY + expand;

                // South front face
                doubleQuad(vc, m, n, sx0, sy1, outerZ, sx1, sy1, outerZ, sx1, sy0, outerZ, sx0, sy0, outerZ, 0, 1, 0, 1, 0, 0, 1, light);
                // Back cap
                doubleQuad(vc, m, n, sx1, sy1, baseZ, sx0, sy1, baseZ, sx0, sy0, baseZ, sx1, sy0, baseZ, 0, 1, 0, 1, 0, 0, -1, light);
                // 4 Side skirts
                doubleQuad(vc, m, n, sx0, sy1, baseZ, sx1, sy1, baseZ, sx1, sy1, outerZ, sx0, sy1, outerZ, 0, 1, 0, 1, 0, 1, 0, light);
                doubleQuad(vc, m, n, sx0, sy0, outerZ, sx1, sy0, outerZ, sx1, sy0, baseZ, sx0, sy0, baseZ, 0, 1, 0, 1, 0, -1, 0, light);
                doubleQuad(vc, m, n, sx0, sy1, outerZ, sx0, sy1, baseZ, sx0, sy0, baseZ, sx0, sy0, outerZ, 0, 1, 0, 1, -1, 0, 0, light);
                doubleQuad(vc, m, n, sx1, sy1, baseZ, sx1, sy1, outerZ, sx1, sy0, outerZ, sx1, sy0, baseZ, 0, 1, 0, 1, 1, 0, 0, light);
            }
            case WEST -> {
                float outerX = minX - snowThick;
                float baseX = minX + skirtDepth;
                float sz0 = minZ - expand, sz1 = maxZ + expand;
                float sy0 = minY - expand, sy1 = maxY + expand;

                // West front face
                doubleQuad(vc, m, n, outerX, sy1, sz0, outerX, sy1, sz1, outerX, sy0, sz1, outerX, sy0, sz0, 0, 1, 0, 1, -1, 0, 0, light);
                // Back cap
                doubleQuad(vc, m, n, baseX, sy1, sz1, baseX, sy1, sz0, baseX, sy0, sz0, baseX, sy0, sz1, 0, 1, 0, 1, 1, 0, 0, light);
                // 4 Side skirts
                doubleQuad(vc, m, n, outerX, sy1, sz0, baseX, sy1, sz0, baseX, sy1, sz1, outerX, sy1, sz1, 0, 1, 0, 1, 0, 1, 0, light);
                doubleQuad(vc, m, n, outerX, sy0, sz1, baseX, sy0, sz1, baseX, sy0, sz0, outerX, sy0, sz0, 0, 1, 0, 1, 0, -1, 0, light);
                doubleQuad(vc, m, n, outerX, sy1, sz1, baseX, sy1, sz1, baseX, sy0, sz1, outerX, sy0, sz1, 0, 1, 0, 1, 0, 0, 1, light);
                doubleQuad(vc, m, n, baseX, sy1, sz0, outerX, sy1, sz0, outerX, sy0, sz0, baseX, sy0, sz0, 0, 1, 0, 1, 0, 0, -1, light);
            }
            case EAST -> {
                float outerX = maxX + snowThick;
                float baseX = maxX - skirtDepth;
                float sz0 = minZ - expand, sz1 = maxZ + expand;
                float sy0 = minY - expand, sy1 = maxY + expand;

                // East front face
                doubleQuad(vc, m, n, outerX, sy1, sz1, outerX, sy1, sz0, outerX, sy0, sz0, outerX, sy0, sz1, 0, 1, 0, 1, 1, 0, 0, light);
                // Back cap
                doubleQuad(vc, m, n, baseX, sy1, sz0, baseX, sy1, sz1, baseX, sy0, sz1, baseX, sy0, sz0, 0, 1, 0, 1, -1, 0, 0, light);
                // 4 Side skirts
                doubleQuad(vc, m, n, baseX, sy1, sz0, outerX, sy1, sz0, outerX, sy1, sz1, baseX, sy1, sz1, 0, 1, 0, 1, 0, 1, 0, light);
                doubleQuad(vc, m, n, baseX, sy0, sz1, outerX, sy0, sz1, outerX, sy0, sz0, baseX, sy0, sz0, 0, 1, 0, 1, 0, -1, 0, light);
                doubleQuad(vc, m, n, baseX, sy1, sz1, outerX, sy1, sz1, outerX, sy0, sz1, baseX, sy0, sz1, 0, 1, 0, 1, 0, 0, 1, light);
                doubleQuad(vc, m, n, outerX, sy1, sz0, baseX, sy1, sz0, baseX, sy0, sz0, outerX, sy0, sz0, 0, 1, 0, 1, 0, 0, -1, light);
            }
        }
    }

    private static void doubleQuad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   float x3, float y3, float z3,
                                   float u0, float u1, float v0, float v1,
                                   float nx, float ny, float nz, int light) {
        // Front face
        vc.vertex(m, x0, y0, z0).color(255, 255, 255, 255).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x1, y1, z1).color(255, 255, 255, 255).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x2, y2, z2).color(255, 255, 255, 255).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x3, y3, z3).color(255, 255, 255, 255).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();

        // Back face (counter-winding to prevent cull issues)
        vc.vertex(m, x3, y3, z3).color(255, 255, 255, 255).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, -nx, -ny, -nz).endVertex();
        vc.vertex(m, x2, y2, z2).color(255, 255, 255, 255).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, -nx, -ny, -nz).endVertex();
        vc.vertex(m, x1, y1, z1).color(255, 255, 255, 255).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, -nx, -ny, -nz).endVertex();
        vc.vertex(m, x0, y0, z0).color(255, 255, 255, 255).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, -nx, -ny, -nz).endVertex();
    }
}
