package dev.zerosevennine.client;

import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.client.render.CameraBlockEntityRenderer;
import dev.zerosevennine.init.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZeroSevenNine.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CAMERA.get(), CameraBlockEntityRenderer::new);
    }
}
