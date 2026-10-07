package dev.snowscpied.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.snowscpied.SnowSCPied;
import dev.snowscpied.config.SnowConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/**
 * Universal 3D Snow Crust Renderer:
 * Dynamically covers the top faces of ANY custom blocks and 3D mod models (industrial fans,
 * fences, stairs, light trusses, machines) with a seamless procedural snow crust!
 */
@Mod.EventBusSubscriber(modid = "snow_scpied", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class UniversalSnowRenderer {
    private static final ResourceLocation SNOW_CRUST_TEX =
            new ResourceLocation(SnowSCPied.MOD_ID, "textures/block/snow_crust_overlay.png");

    private static final Set<BlockPos> MANUAL_SNOW_BLOCKS = new HashSet<>();

    public static void toggleSnowOnBlock(BlockPos pos) {
        if (MANUAL_SNOW_BLOCKS.contains(pos)) {
            MANUAL_SNOW_BLOCKS.remove(pos);
        } else {
            MANUAL_SNOW_BLOCKS.add(pos.immutable());
        }
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
        if (level == null) return;

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(SNOW_CRUST_TEX));

        for (BlockPos pos : MANUAL_SNOW_BLOCKS) {
            double rx = pos.getX() - camPos.x;
            double ry = pos.getY() - camPos.y;
            double rz = pos.getZ() - camPos.z;

            if (rx * rx + ry * ry + rz * rz > 1024.0) continue;

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            double topY = state.getShape(level, pos).isEmpty() ? 1.0 : state.getShape(level, pos).max(net.minecraft.core.Direction.Axis.Y);

            poseStack.pushPose();
            poseStack.translate(rx, ry + topY + 0.003, rz);
            Matrix4f m = poseStack.last().pose();
            Matrix3f n = poseStack.last().normal();

            int light = 0xF000F0;
            vc.vertex(m, 0.0f, 0.0f, 0.0f).color(255, 255, 255, 240).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
            vc.vertex(m, 1.0f, 0.0f, 0.0f).color(255, 255, 255, 240).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
            vc.vertex(m, 1.0f, 0.0f, 1.0f).color(255, 255, 255, 240).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
            vc.vertex(m, 0.0f, 0.0f, 1.0f).color(255, 255, 255, 240).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();

            poseStack.popPose();
        }
    }
}
