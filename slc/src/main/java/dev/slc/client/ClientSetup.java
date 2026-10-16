package dev.slc.client;

import dev.slc.SecretLabCubed;
import dev.slc.client.gui.CassieHudOverlay;
import dev.slc.client.gui.DisarmedHudOverlay;
import dev.slc.client.gui.RoleIntroOverlay;
import dev.slc.client.gui.MapSetupToolOverlay;
import dev.slc.client.gui.SpectatorHudOverlay;
import dev.slc.client.render.ElevatorButtonRenderer;
import dev.slc.client.render.TestDummyRenderer;
import dev.slc.init.ModEntities;
import dev.slc.init.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = SecretLabCubed.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    /** Appears in Minecraft's Controls menu and is rebindable like every other key. */
    public static final KeyMapping ADMIN_PANEL_KEY = new KeyMapping(
            "key.slc.admin_panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.slc");

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SecretLabCubed.LOGGER.info("[SLC] Client setup initialized.");
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ADMIN_PANEL_KEY);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ELEVATOR_BUTTON_BE.get(), ElevatorButtonRenderer::new);
        event.registerEntityRenderer(ModEntities.STATIC_DUMMY.get(), TestDummyRenderer::new);
        event.registerEntityRenderer(ModEntities.RESPAWN_DUMMY.get(), TestDummyRenderer::new);
        event.registerEntityRenderer(ModEntities.AI_DUMMY.get(), TestDummyRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("spectator_hud", SpectatorHudOverlay.INSTANCE);
        event.registerAboveAll("role_intro", RoleIntroOverlay.INSTANCE);
        event.registerAboveAll("cassie_text", CassieHudOverlay.INSTANCE);
        event.registerAboveAll("disarmed_hud", DisarmedHudOverlay.INSTANCE);
        event.registerAboveAll("map_setup_tool", MapSetupToolOverlay.INSTANCE);
    }
}
