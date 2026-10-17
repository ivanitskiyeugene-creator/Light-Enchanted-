package dev.slc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.slc.SecretLabCubed;
import dev.slc.client.ClientMapZoneCache;
import dev.slc.item.map.ElevatorCabinMarkerItem;
import dev.slc.item.map.ExitZoneMarkerItem;
import dev.slc.item.map.PocketDimensionMarkerItem;
import dev.slc.item.map.RoleSpawnMarkerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Wireframe map volumes are visible only while their dedicated editor tool is held. */
@Mod.EventBusSubscriber(modid = SecretLabCubed.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MapZoneRenderer {
    private MapZoneRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        boolean drew = false;

        if (held.getItem() instanceof RoleSpawnMarkerItem marker) {
            if (marker.type() == RoleSpawnMarkerItem.Type.ZONE) {
                for (AABB box : ClientMapZoneCache.spawnZones(marker.role())) {
                    drawBox(pose, lines, box, camera, 0.25F, 0.85F, 1.0F);
                    drew = true;
                }
            } else {
                for (net.minecraft.core.BlockPos point : ClientMapZoneCache.spawnPoints(marker.role())) {
                    drawBox(pose, lines, new AABB(point).inflate(0.12D), camera, 0.25F, 1.0F, 0.5F);
                    drew = true;
                }
            }
        } else if (held.getItem() instanceof ExitZoneMarkerItem) {
            AABB escape = ClientMapZoneCache.escapeZone();
            if (escape != null) {
                drawBox(pose, lines, escape, camera, 1.0F, 0.78F, 0.15F);
                drew = true;
            }
        } else if (held.getItem() instanceof PocketDimensionMarkerItem marker) {
            if (marker.type() == PocketDimensionMarkerItem.Type.ARRIVAL_ZONE) {
                AABB arrival = ClientMapZoneCache.pocketArrivalZone();
                if (arrival != null) { drawBox(pose, lines, arrival, camera, 0.55F, 0.10F, 0.75F); drew = true; }
            } else if (marker.type() == PocketDimensionMarkerItem.Type.ESCAPE_ZONE) {
                AABB exit = ClientMapZoneCache.pocketEscapeZone();
                if (exit != null) { drawBox(pose, lines, exit, camera, 0.20F, 0.95F, 0.72F); drew = true; }
            } else {
                net.minecraft.core.BlockPos point = ClientMapZoneCache.pocketReturnPoint();
                if (point != null) { drawBox(pose, lines, new AABB(point).inflate(0.14D), camera, 0.95F, 0.45F, 0.95F); drew = true; }
            }
        } else if (held.getItem() instanceof ElevatorCabinMarkerItem) {
            for (AABB cabin : ClientMapZoneCache.elevatorCabins()) {
                drawBox(pose, lines, cabin, camera, 0.8F, 0.4F, 1.0F);
                drew = true;
            }
        }

        // The first selected corner is intentionally visible too, so volume tools never need chat feedback.
        net.minecraft.core.BlockPos first = null;
        if (held.getItem() instanceof RoleSpawnMarkerItem marker && marker.type() == RoleSpawnMarkerItem.Type.ZONE) first = marker.firstPoint(held);
        else if (held.getItem() instanceof ExitZoneMarkerItem marker) first = marker.firstPoint(held);
        else if (held.getItem() instanceof ElevatorCabinMarkerItem marker) first = marker.firstPoint(held);
        else if (held.getItem() instanceof PocketDimensionMarkerItem marker && marker.type() != PocketDimensionMarkerItem.Type.RETURN_POINT) first = marker.firstPoint(held);
        if (first != null) {
            drawBox(pose, lines, new AABB(first).inflate(0.18D), camera, 1.0F, 1.0F, 1.0F);
            drew = true;
        }
        if (drew) buffers.endBatch(RenderType.lines());
    }

    private static void drawBox(PoseStack pose, VertexConsumer lines, AABB worldBox, Vec3 camera,
                                float red, float green, float blue) {
        AABB box = worldBox.move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(pose, lines, box, red, green, blue, 1.0F);
    }
}
