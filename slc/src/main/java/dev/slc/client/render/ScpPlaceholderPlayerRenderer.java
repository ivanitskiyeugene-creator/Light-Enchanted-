package dev.slc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.slc.SecretLabCubed;
import dev.slc.client.ClientRoundCache;
import dev.slc.role.Role;
import dev.slc.spectator.SpectatorTargetInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Deliberately simple geometric silhouettes. These are placeholders for future approved SCP models. */
@Mod.EventBusSubscriber(modid = SecretLabCubed.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ScpPlaceholderPlayerRenderer {
    private ScpPlaceholderPlayerRenderer() {}

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        Role role = roleOf(player);
        if (role == null || !role.isScp() || role == Role.SCP_079) return;
        PoseStack pose = event.getPoseStack();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        pose.pushPose();
        switch (role) {
            case SCP_173 -> render173(pose, lines);
            case SCP_939 -> render939(pose, lines);
            case SCP_049 -> render049(pose, lines, 0.57F, 0.16F, 0.13F);
            case SCP_049_2 -> render049(pose, lines, 0.34F, 0.40F, 0.45F);
            case SCP_106 -> render106(pose, lines);
            case SCP_096 -> render096(pose, lines);
            default -> { }
        }
        pose.popPose();
        event.setCanceled(true);
    }

    private static Role roleOf(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && player.getUUID().equals(mc.player.getUUID())) return ClientRoundCache.getLocalRole();
        for (SpectatorTargetInfo info : ClientRoundCache.getAlivePlayers()) {
            if (info.uuid().equals(player.getUUID())) return info.role();
        }
        return null;
    }

    private static void render173(PoseStack pose, VertexConsumer lines) {
        box(pose, lines, -.42, 0, -.30, .42, 1.85, .30, .72F, .70F, .61F);
        box(pose, lines, -.28, 1.85, -.24, .28, 2.35, .24, .72F, .70F, .61F);
    }

    private static void render939(PoseStack pose, VertexConsumer lines) {
        box(pose, lines, -.75, .15, -.82, .75, .88, .55, .66F, .18F, .15F);
        box(pose, lines, -.37, .45, -.30, .37, 1.05, .82, .76F, .24F, .18F);
        for (double x : new double[]{-.58, .38}) for (double z : new double[]{-.63, .29}) {
            box(pose, lines, x, 0, z, x + .20, .45, z + .20, .66F, .18F, .15F);
        }
    }

    private static void render049(PoseStack pose, VertexConsumer lines, float red, float green, float blue) {
        box(pose, lines, -.36, 0, -.25, .36, 1.90, .25, red, green, blue);
        box(pose, lines, -.48, 1.15, -.36, .48, 2.10, .36, red, green, blue);
        box(pose, lines, -.26, 1.90, -.26, .26, 2.36, .26, red, green, blue);
    }

    private static void render106(PoseStack pose, VertexConsumer lines) {
        box(pose, lines, -.42, 0, -.30, .42, 1.72, .30, .27F, .08F, .34F);
        box(pose, lines, -.29, 1.70, -.27, .29, 2.19, .27, .36F, .10F, .47F);
        box(pose, lines, -.72, .74, -.15, -.42, 1.64, .15, .27F, .08F, .34F);
        box(pose, lines, .42, .74, -.15, .72, 1.64, .15, .27F, .08F, .34F);
    }

    private static void render096(PoseStack pose, VertexConsumer lines) {
        box(pose, lines, -.30, 0, -.20, .30, 2.35, .20, .82F, .82F, .78F);
        box(pose, lines, -.24, 2.35, -.22, .24, 2.85, .22, .90F, .90F, .86F);
        box(pose, lines, -.78, 1.20, -.14, -.30, 2.15, .14, .82F, .82F, .78F);
        box(pose, lines, .30, 1.20, -.14, .78, 2.15, .14, .82F, .82F, .78F);
    }

    private static void box(PoseStack pose, VertexConsumer lines, double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ, float red, float green, float blue) {
        LevelRenderer.renderLineBox(pose, lines, new AABB(minX, minY, minZ, maxX, maxY, maxZ), red, green, blue, 1.0F);
    }
}
