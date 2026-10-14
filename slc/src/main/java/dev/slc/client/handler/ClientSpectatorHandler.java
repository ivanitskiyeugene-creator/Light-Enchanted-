package dev.slc.client.handler;

import dev.slc.client.ClientRoundCache;
import dev.slc.client.gui.RadialInventoryScreen;
import dev.slc.client.gui.SpectatorMemeScreen;
import dev.slc.spectator.SpectatorTargetInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientSpectatorHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // Strict requirement: Only active when round is in progress (Round = true)
        if (!ClientRoundCache.isRoundActive()) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.player.isSpectator()) {
                // SPECTATOR CONTROLS
                if (event.getKey() == GLFW.GLFW_KEY_SPACE) {
                    ClientRoundCache.toggleFreecam();
                    if (ClientRoundCache.isFreecamMode()) {
                        mc.setCameraEntity(mc.player);
                    }
                } else if (event.getKey() == GLFW.GLFW_KEY_TAB) {
                    mc.setScreen(new SpectatorMemeScreen());
                } else if (event.getKey() == GLFW.GLFW_KEY_M) {
                    ClientRoundCache.toggleMuteDeadChat();
                } else if (event.getKey() == GLFW.GLFW_KEY_K) {
                    ClientRoundCache.toggleMuteSpectatedPlayer();
                }
            } else {
                // ALIVE PLAYER CONTROLS
                if (event.getKey() == GLFW.GLFW_KEY_TAB) {
                    mc.setScreen(new RadialInventoryScreen());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // Strict requirement: Only active when in Spectator and Round is active
        if (!mc.player.isSpectator() || !ClientRoundCache.isRoundActive()) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                // LMB -> Next spectated player
                ClientRoundCache.nextSpectateTarget();
                event.setCanceled(true);
            } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                // RMB -> Previous spectated player
                ClientRoundCache.prevSpectateTarget();
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (!ClientRoundCache.isRoundActive() || !mc.player.isSpectator()) {
            if (mc.getCameraEntity() != mc.player) {
                mc.setCameraEntity(mc.player);
            }
            return;
        }

        // Handle First-person lock to spectated player
        if (!ClientRoundCache.isFreecamMode()) {
            SpectatorTargetInfo target = ClientRoundCache.getCurrentSpectateTarget();
            if (target != null) {
                Player targetEntity = mc.level.getPlayerByUUID(target.uuid());
                if (targetEntity != null && targetEntity.isAlive()) {
                    if (mc.getCameraEntity() != targetEntity) {
                        mc.setCameraEntity(targetEntity);
                    }
                    return;
                }
            }
        }

        // In freecam mode or target missing, lock back to own spectator entity
        if (mc.getCameraEntity() != mc.player) {
            mc.setCameraEntity(mc.player);
        }
    }
}
