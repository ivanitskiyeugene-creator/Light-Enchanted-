package dev.slc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.slc.SecretLabCubed;
import dev.slc.client.ClientRoleSkinCache;
import dev.slc.client.ClientRoundCache;
import dev.slc.role.Role;
import dev.slc.spectator.SpectatorTargetInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Renders an approved role PNG over the normal player model. SCP roles deliberately bypass this
 * path and are represented by their separate placeholder-model renderer.
 */
@Mod.EventBusSubscriber(modid = SecretLabCubed.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanRoleSkinRenderer {
    private HumanRoleSkinRenderer() {}

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        AbstractClientPlayer player = event.getEntity();
        Role role = roleOf(player);
        if (role == null || !role.isHuman()) return;
        ResourceLocation skin = ClientRoleSkinCache.texture(role);
        if (skin == null) return; // vanilla skin is used until the approved URL is ready.

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float partial = event.getPartialTick();
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        float pitch = Mth.lerp(partial, player.xRotO, player.getXRot());
        // The dispatcher has already translated to the entity. This is the stable player-model
        // portion of LivingEntityRenderer's transform sequence.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);

        PlayerModel model = (PlayerModel) event.getRenderer().getModel();
        model.attackTime = player.getAttackAnim(partial);
        model.riding = player.isPassenger();
        model.young = false; // player entities are never ageable
        float age = player.tickCount + partial;
        model.prepareMobModel(player, 0.0F, 0.0F, partial);
        model.setupAnim(player, 0.0F, 0.0F, age, headYaw - bodyYaw, pitch);
        if (player.getPose() == Pose.SLEEPING) pose.translate(0.0F, 0.2F, 0.0F);
        VertexConsumer vertices = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(skin));
        model.renderToBuffer(pose, vertices, event.getPackedLight(), OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
        event.setCanceled(true);
    }

    private static Role roleOf(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && player.getUUID().equals(mc.player.getUUID())) return ClientRoundCache.getLocalRole();
        for (SpectatorTargetInfo info : ClientRoundCache.getAlivePlayers()) {
            if (info.uuid().equals(player.getUUID())) return info.role();
        }
        return null;
    }
}
