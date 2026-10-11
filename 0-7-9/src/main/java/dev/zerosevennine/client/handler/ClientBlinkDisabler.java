package dev.zerosevennine.client.handler;

import dev.zerosevennine.facility.BlinkDisablerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "zero_seven_nine", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientBlinkDisabler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!BlinkDisablerHandler.isBlinkDisabled()) return;
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Player player = mc.player;
            BlinkDisablerHandler.suppressPlayerCapabilityBlink(player);
            // If player was afflicted with blink blindness
            if (player.hasEffect(MobEffects.BLINDNESS) && player.getPersistentData().contains("blink_blindness")) {
                player.removeEffect(MobEffects.BLINDNESS);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!BlinkDisablerHandler.isBlinkDisabled()) return;

        NamedGuiOverlay overlay = event.getOverlay();
        if (overlay != null) {
            ResourceLocation id = overlay.id();
            if (id != null) {
                if (BlinkDisablerHandler.isBlinkResource(id)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (!BlinkDisablerHandler.isBlinkDisabled()) return;

        if (event.getSound() != null && event.getSound().getLocation() != null) {
            ResourceLocation loc = event.getSound().getLocation();
            if (BlinkDisablerHandler.isBlinkResource(loc)) {
                event.setSound(null);
            }
        }
    }
}
