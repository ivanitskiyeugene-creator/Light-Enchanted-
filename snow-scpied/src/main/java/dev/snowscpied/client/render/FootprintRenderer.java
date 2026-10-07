package dev.snowscpied.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.snowscpied.snow.BloodStainManager;
import dev.snowscpied.snow.FootprintManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

@Mod.EventBusSubscriber(modid = "snow_scpied", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class FootprintRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        // 1. Render Dynamic Footprints
        List<FootprintManager.FootprintInstance> prints = FootprintManager.getActivePrints();
        for (FootprintManager.FootprintInstance fp : prints) {
            double rx = fp.x - camPos.x;
            double ry = fp.y - camPos.y;
            double rz = fp.z - camPos.z;

            if (rx * rx + ry * ry + rz * rz > 1024.0) continue; // 32 block distance culling

            poseStack.pushPose();
            poseStack.translate(rx, ry, rz);
            poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(-fp.yaw + 180.0)));

            VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(fp.type.getTexture()));
            Matrix4f mat = poseStack.last().pose();
            Matrix3f nmat = poseStack.last().normal();

            float hw = fp.type.getWidth() * 0.5f;
            float hh = fp.type.getHeight() * 0.5f;
            int alpha = (int) (fp.opacity * 255);

            renderDecalQuad(vc, mat, nmat, -hw, hh, hw, -hh, alpha);
            poseStack.popPose();
        }

        // 2. Render Blood & Fluid Stains
        List<BloodStainManager.BloodStainInstance> stains = BloodStainManager.getActiveStains();
        for (BloodStainManager.BloodStainInstance stain : stains) {
            double rx = stain.x - camPos.x;
            double ry = stain.y - camPos.y;
            double rz = stain.z - camPos.z;

            if (rx * rx + ry * ry + rz * rz > 1024.0) continue;

            poseStack.pushPose();
            poseStack.translate(rx, ry, rz);
            poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(stain.rotation)));

            VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(stain.type.getTexture()));
            Matrix4f mat = poseStack.last().pose();
            Matrix3f nmat = poseStack.last().normal();

            float hs = stain.scale * 0.5f;
            int alpha = (int) (stain.opacity * 255);

            renderDecalQuad(vc, mat, nmat, -hs, hs, hs, -hs, alpha);
            poseStack.popPose();
        }
    }

    private static void renderDecalQuad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                        float x0, float z0, float x1, float z1, int alpha) {
        int light = 0xF000F0;
        vc.vertex(m, x0, 0.0f, z0).color(255, 255, 255, alpha).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x1, 0.0f, z0).color(255, 255, 255, alpha).uv(1, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x1, 0.0f, z1).color(255, 255, 255, alpha).uv(1, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x0, 0.0f, z1).color(255, 255, 255, alpha).uv(0, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }
}
