package dev.snowscpied.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.snowscpied.SnowSCPied;
import dev.snowscpied.event.SnowEventHandler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders a frosted, dynamic snowy crust on the boots and lower legs of players and mobs
 * when walking through snow or carrying snow residue on their footwear.
 */
@Mod.EventBusSubscriber(modid = "snow_scpied", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SnowyLegsRenderer {
    private static final ResourceLocation SNOWY_LEGS_TEX =
            new ResourceLocation(SnowSCPied.MOD_ID, "textures/entity/snow/snowy_legs_overlay.png");

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        int charges = SnowEventHandler.getSnowCharge(entity.getUUID());
        if (charges <= 0) return;

        float alphaFrac = charges / 12.0f;
        int alpha = (int) (alphaFrac * 220);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();

        poseStack.pushPose();
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(SNOWY_LEGS_TEX));
        Matrix4f m = poseStack.last().pose();
        Matrix3f n = poseStack.last().normal();

        float hw = entity.getBbWidth() * 0.52f;
        float hBoot = Math.min(0.35f, entity.getBbHeight() * 0.25f);

        // Lower leg snow mantle box
        renderBox(vc, m, n, -hw, 0.0f, -hw, hw, hBoot, hw, alpha, event.getPackedLight());
        poseStack.popPose();
    }

    private static void renderBox(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                  float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ,
                                  int alpha, int light) {
        // Front
        quad(vc, m, n, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0, 1, 0, 1, alpha, light);
        // Back
        quad(vc, m, n, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, 0, 1, 0, 1, alpha, light);
        // Left
        quad(vc, m, n, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, 0, 1, 0, 1, alpha, light);
        // Right
        quad(vc, m, n, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, 0, 1, 0, 1, alpha, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float u0, float u1, float v0, float v1, int alpha, int light) {
        vc.vertex(m, x0, y0, z0).color(255, 255, 255, alpha).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x1, y1, z1).color(255, 255, 255, alpha).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x2, y2, z2).color(255, 255, 255, alpha).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x3, y3, z3).color(255, 255, 255, alpha).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }
}
