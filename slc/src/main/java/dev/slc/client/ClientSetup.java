package dev.slc.client;

import dev.slc.SecretLabCubed;
import dev.slc.client.gui.RoleIntroOverlay;
import dev.slc.client.gui.SpectatorHudOverlay;
import dev.slc.client.render.ElevatorButtonRenderer;
import dev.slc.init.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = SecretLabCubed.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SecretLabCubed.LOGGER.info("[SLC] Client setup initialized.");
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ELEVATOR_BUTTON_BE.get(), ElevatorButtonRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("spectator_hud", SpectatorHudOverlay.INSTANCE);
        event.registerAboveAll("role_intro", RoleIntroOverlay.INSTANCE);
    }
}
