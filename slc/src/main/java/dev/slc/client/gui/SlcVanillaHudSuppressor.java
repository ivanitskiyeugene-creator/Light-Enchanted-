package dev.slc.client.gui;

import dev.slc.SecretLabCubed;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Removes the vanilla survival HUD only when the replacement SLC round HUD is active. */
@Mod.EventBusSubscriber(modid = SecretLabCubed.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlcVanillaHudSuppressor {
    private SlcVanillaHudSuppressor() {}

    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!SlcHudOverlay.shouldRender(Minecraft.getInstance())) return;
        if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()
                || event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()
                || event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()
                || event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()
                || event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || event.getOverlay() == VanillaGuiOverlay.PLAYER_LIST.type()
                || event.getOverlay() == VanillaGuiOverlay.ITEM_NAME.type()) {
            event.setCanceled(true);
        }
    }
}
