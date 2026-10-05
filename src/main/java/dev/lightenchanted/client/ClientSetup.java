package dev.lightenchanted.client;

import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.client.render.IndustrialFanRenderer;
import dev.lightenchanted.client.render.LightEmitterRenderer;
import dev.lightenchanted.init.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LightEnchanted.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LIGHT_EMITTER.get(), LightEmitterRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.INDUSTRIAL_FAN.get(), IndustrialFanRenderer::new);
    }
}
