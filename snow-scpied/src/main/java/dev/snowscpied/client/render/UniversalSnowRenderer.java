package dev.snowscpied.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.snowscpied.config.SnowConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Universal Top-Surface 3D Snow Layer Engine:
 * - Generates physical 3D snow layers ONLY on topmost exposed upward surfaces (with roof/occlusion detection).
 * - Exact vanilla snow layer thickness (2 pixels / 0.125 blocks thick, matching SnowLayerBlock).
 * - Authentic 16x16 vanilla snow texture.
 * - Real world ambient & block lighting (LevelRenderer.getLightColor) with directional diffuse shading.
 */
@Mod.EventBusSubscriber(modid = "snow_scpied", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class UniversalSnowRenderer {
    private static final ResourceLocation SNOW_MANTLE_TEX =
            new ResourceLocation("minecraft", "textures/block/snow.png");

    private static final Set<BlockPos> SNOW_BLOCKS = new HashSet<>();
    private static final RandomSource RANDOM = RandomSource.create(42L);

    public static void toggleSnow(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        if (SNOW_BLOCKS.contains(immutablePos)) {
            SNOW_BLOCKS.remove(immutablePos);
        } else {
            SNOW_BLOCKS.add(immutablePos);
        }
    }

    public static void toggleSnowFace(BlockPos pos, Direction face) {
        toggleSnow(pos);
    }

    public static void toggleAllFaces(BlockPos pos) {
        toggleSnow(pos);
    }

    public static void toggleSnowOnBlock(BlockPos pos) {
        toggleSnow(pos);
    }

    public static boolean hasSnow(BlockPos pos) {
        return SNOW_BLOCKS.contains(pos);
    }

    public static boolean hasSnow(BlockPos pos, Direction face) {
        return face == Direction.UP && SNOW_BLOCKS.contains(pos);
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
        if (level == null || SNOW_BLOCKS.isEmpty()) return;

        BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entitySolid(SNOW_MANTLE_TEX));

        for (BlockPos pos : SNOW_BLOCKS) {
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

            // Real vanilla block + sky light for the block above
            BlockPos abovePos = pos.above();
            int light = LevelRenderer.getLightColor(level, state, abovePos);

            renderTopSnowLayers(vc, m, n, level, pos, state, brd, light);

            poseStack.popPose();
        }
    }

    private static void renderTopSnowLayers(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                            Level level, BlockPos pos, BlockState state,
                                            BlockRenderDispatcher brd, int light) {
        List<AABB> rawSurfaces = new ArrayList<>();
        BakedModel model = brd.getBlockModel(state);

        // 1. Extract upward-facing quads from BakedModel
        if (model != null) {
            List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, Direction.UP, RANDOM, ModelData.EMPTY, null));
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

                    // Only consider upward surfaces (flat or nearly flat horizontal top face)
                    if ((maxX - minX > 0.03f) && (maxZ - minZ > 0.03f) && (maxY - minY < 0.06f || q.getDirection() == Direction.UP)) {
                        rawSurfaces.add(new AABB(minX, maxY, minZ, maxX, maxY, maxZ));
                    }
                }
            }
        }

        // 2. Also check VoxelShape (for stairs, fences, slabs, custom blocks)
        VoxelShape shape = state.getShape(level, pos);
        List<AABB> shapeBoxes = shape.toAabbs();
        if (!shapeBoxes.isEmpty()) {
            for (AABB box : shapeBoxes) {
                rawSurfaces.add(new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ));
            }
        }

        if (rawSurfaces.isEmpty()) {
            rawSurfaces.add(new AABB(0, 1.0, 0, 1.0, 1.0, 1.0));
        }

        // 3. Occlusion & Topmost Filter:
        // Filter out any surface that has another surface directly above it (so lower hidden layers don't get snow!)
        List<AABB> visibleTopSurfaces = new ArrayList<>();
        for (int i = 0; i < rawSurfaces.size(); i++) {
            AABB surfA = rawSurfaces.get(i);
            boolean isOccluded = false;

            for (int j = 0; j < rawSurfaces.size(); j++) {
                if (i == j) continue;
                AABB surfB = rawSurfaces.get(j);

                // If surfB is strictly above surfA and overlaps horizontally:
                if (surfB.minY > surfA.maxY + 0.03) {
                    boolean overlapX = (surfB.minX <= surfA.minX + 0.03) && (surfB.maxX >= surfA.maxX - 0.03);
                    boolean overlapZ = (surfB.minZ <= surfA.minZ + 0.03) && (surfB.maxZ >= surfA.maxZ - 0.03);
                    if (overlapX && overlapZ) {
                        isOccluded = true;
                        break;
                    }
                }
                // Deduplicate identical duplicate quads
                else if (Math.abs(surfB.maxY - surfA.maxY) < 0.015 && j < i) {
                    if (Math.abs(surfB.minX - surfA.minX) < 0.03 && Math.abs(surfB.maxX - surfA.maxX) < 0.03 &&
                        Math.abs(surfB.minZ - surfA.minZ) < 0.03 && Math.abs(surfB.maxZ - surfA.maxZ) < 0.03) {
                        isOccluded = true;
                        break;
                    }
                }
            }

            if (!isOccluded) {
                visibleTopSurfaces.add(surfA);
            }
        }

        // 4. Render thick vanilla-sized snow layer (2 pixels = 0.125 blocks) on each exposed top surface
        for (AABB surf : visibleTopSurfaces) {
            renderThickSnowLayer(vc, m, n, surf, light);
        }
    }

    /**
     * Renders a 3D solid snow layer matching vanilla 1-layer snow block thickness (0.125 blocks = 2 pixels)
     */
    private static void renderThickSnowLayer(VertexConsumer vc, Matrix4f m, Matrix3f n, AABB surf, int light) {
        float snowThick = 0.125f; // Exact 2-pixel vanilla snow layer thickness
        float expand = 0.005f;

        float minX = (float) surf.minX - expand;
        float maxX = (float) surf.maxX + expand;
        float minZ = (float) surf.minZ - expand;
        float maxZ = (float) surf.maxZ + expand;

        float baseY = (float) surf.maxY;
        float topY = baseY + snowThick;

        // 1. Top snow face (1.0x light)
        shadedQuad(vc, m, n, minX, topY, minZ, maxX, topY, minZ, maxX, topY, maxZ, minX, topY, maxZ, 0, 1, 0, 1, Direction.UP, light);

        // 2. Bottom face against block (0.5x light)
        shadedQuad(vc, m, n, minX, baseY, minZ, minX, baseY, maxZ, maxX, baseY, maxZ, maxX, baseY, minZ, 0, 1, 0, 1, Direction.DOWN, light);

        // 3. North face (0.8x light)
        shadedQuad(vc, m, n, maxX, topY, minZ, minX, topY, minZ, minX, baseY, minZ, maxX, baseY, minZ, 0, 1, 0, 1, Direction.NORTH, light);

        // 4. South face (0.8x light)
        shadedQuad(vc, m, n, minX, topY, maxZ, maxX, topY, maxZ, maxX, baseY, maxZ, minX, baseY, maxZ, 0, 1, 0, 1, Direction.SOUTH, light);

        // 5. West face (0.6x light)
        shadedQuad(vc, m, n, minX, topY, minZ, minX, topY, maxZ, minX, baseY, maxZ, minX, baseY, minZ, 0, 1, 0, 1, Direction.WEST, light);

        // 6. East face (0.6x light)
        shadedQuad(vc, m, n, maxX, topY, maxZ, maxX, topY, minZ, maxX, baseY, minZ, maxX, baseY, maxZ, 0, 1, 0, 1, Direction.EAST, light);
    }

    private static void shadedQuad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   float x3, float y3, float z3,
                                   float u0, float u1, float v0, float v1,
                                   Direction quadDir, int light) {
        float shade = switch (quadDir) {
            case UP -> 1.0f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST -> 0.6f;
        };
        int r = (int) (shade * 255.0f);
        int g = (int) (shade * 255.0f);
        int b = (int) (shade * 255.0f);

        float nx = quadDir.getStepX();
        float ny = quadDir.getStepY();
        float nz = quadDir.getStepZ();

        // Front face
        vc.vertex(m, x0, y0, z0).color(r, g, b, 255).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x1, y1, z1).color(r, g, b, 255).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, 255).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, 255).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();

        // Back face (no culling)
        vc.vertex(m, x3, y3, z3).color(r, g, b, 255).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, -nx, -ny, -nz).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, 255).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, -nx, -ny, -nz).endVertex();
        vc.vertex(m, x1, y1, z1).color(r, g, b, 255).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, -nx, -ny, -nz).endVertex();
        vc.vertex(m, x0, y0, z0).color(r, g, b, 255).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, -nx, -ny, -nz).endVertex();
    }
}
