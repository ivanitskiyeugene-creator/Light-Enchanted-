package dev.lightenchanted.client;

import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.client.render.IndustrialFanRenderer;
import dev.lightenchanted.client.render.LightEmitterRenderer;
import dev.lightenchanted.init.ModBlockEntities;
import dev.lightenchanted.init.ModBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = LightEnchanted.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LIGHT_EMITTER.get(), LightEmitterRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.INDUSTRIAL_FAN.get(), IndustrialFanRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.LIGHT_TRUSS.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.LIGHT_GRATE.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.OPTICAL_MIRROR.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.FLUORESCENT_TUBE.get(), RenderType.translucent());
        });
    }
}
