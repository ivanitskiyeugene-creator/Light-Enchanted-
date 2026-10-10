package dev.zerosevennine.client.handler;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.client.gui.Scp079CameraOverlay;
import dev.zerosevennine.client.gui.Scp079MapScreen;
import dev.zerosevennine.facility.DeviceType;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.network.C2SInteractDevicePacket;
import dev.zerosevennine.network.C2SSwitchCameraPacket;
import dev.zerosevennine.network.C2SUpdateCameraOrientationPacket;
import dev.zerosevennine.network.ModNetwork;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "zero_seven_nine", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientCameraHandler {
    public static boolean in079Mode = false;
    public static BlockPos activeCameraPos = BlockPos.ZERO;
    public static int tier = 1;
    public static int exp = 0;
    public static int nextExp = 100;
    public static float ap = 100.0f;
    public static float maxAp = 100.0f;
    public static float apRegen = 3.2f;
    public static List<Scp079Session.LogEntry> logs = new ArrayList<>();

    public static boolean isZoomed = false;
    public static float zoomFovFactor = 1.0f;

    private static float initialYaw = 0.0f;
    private static float initialPitch = 0.0f;
    private static boolean hasInitialAngles = false;

    public static void handleSyncState(boolean active, BlockPos cameraPos, int t, int e, int ne,
                                      float currAp, float mAp, float regen, List<Scp079Session.LogEntry> l) {
        boolean wasIn079 = in079Mode;
        in079Mode = active;
        activeCameraPos = cameraPos;
        tier = t;
        exp = e;
        nextExp = ne;
        ap = currAp;
        maxAp = mAp;
        apRegen = regen;
        logs = l;

        if (active && !wasIn079) {
            hasInitialAngles = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !in079Mode) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (!hasInitialAngles) {
                initialYaw = mc.player.getYRot();
                initialPitch = mc.player.getXRot();
                hasInitialAngles = true;
            }

            // Target smooth FOV
            float targetFov = isZoomed ? 0.35f : 1.0f;
            zoomFovFactor += (targetFov - zoomFovFactor) * 0.3f;

            // Send look angles to server to swivel physical camera model
            ModNetwork.CHANNEL.sendToServer(new C2SUpdateCameraOrientationPacket(mc.player.getYRot(), mc.player.getXRot()));
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (in079Mode) {
            event.setNewFovModifier(event.getFovModifier() * zoomFovFactor);
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!in079Mode) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !hasInitialAngles) return;

        // Clamp rotation angles relative to camera mount
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float diffYaw = currentYaw - initialYaw;
        while (diffYaw < -180.0f) diffYaw += 360.0f;
        while (diffYaw > 180.0f) diffYaw -= 360.0f;

        float clampedDiffYaw = Math.max(-80.0f, Math.min(80.0f, diffYaw));
        float clampedPitch = Math.max(-40.0f, Math.min(30.0f, currentPitch));

        if (diffYaw != clampedDiffYaw || currentPitch != clampedPitch) {
            mc.player.setYRot(initialYaw + clampedDiffYaw);
            mc.player.setXRot(clampedPitch);
        }
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (!in079Mode) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        // MMB (Button 2): Optical Zoom toggle
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && event.getAction() == GLFW.GLFW_PRESS) {
            isZoomed = !isZoomed;
            event.setCanceled(true);
            return;
        }

        // RMB (Button 1): Interact with hovered door/device
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.hitResult instanceof BlockHitResult blockHit && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = blockHit.getBlockPos();
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(hitPos, C2SInteractDevicePacket.Action.TOGGLE_DOOR));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!in079Mode) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            // Tab: Open Tactical Facility Map Screen
            if (event.getKey() == GLFW.GLFW_KEY_TAB) {
                mc.setScreen(new Scp079MapScreen());
                return;
            }

            // WASD Camera Jumps (Costs 2 AP)
            if (event.getKey() == GLFW.GLFW_KEY_W) {
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(Direction.NORTH));
            } else if (event.getKey() == GLFW.GLFW_KEY_S) {
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(Direction.SOUTH));
            } else if (event.getKey() == GLFW.GLFW_KEY_A) {
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(Direction.WEST));
            } else if (event.getKey() == GLFW.GLFW_KEY_D) {
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(Direction.EAST));
            }

            // F: Blackout (Tier 2+)
            if (event.getKey() == GLFW.GLFW_KEY_F) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(activeCameraPos, C2SInteractDevicePacket.Action.BLACKOUT));
            }

            // G: Room Lockdown (Tier 3+)
            if (event.getKey() == GLFW.GLFW_KEY_G) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(activeCameraPos, C2SInteractDevicePacket.Action.LOCKDOWN));
            }

            // V: Intercom Speaker
            if (event.getKey() == GLFW.GLFW_KEY_V) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(activeCameraPos, C2SInteractDevicePacket.Action.SPEAKER));
            }

            // Space: Door Lock
            if (event.getKey() == GLFW.GLFW_KEY_SPACE) {
                if (mc.hitResult instanceof BlockHitResult blockHit) {
                    ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(blockHit.getBlockPos(), C2SInteractDevicePacket.Action.LOCK_DOOR));
                }
            }

            // E: Tactical Ping
            if (event.getKey() == GLFW.GLFW_KEY_E) {
                if (mc.hitResult instanceof BlockHitResult blockHit) {
                    ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(blockHit.getBlockPos(), C2SInteractDevicePacket.Action.PING));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (in079Mode) {
            // Hide normal vanilla crosshair, hotbar, health, hunger, etc.
            if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type() ||
                event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type() ||
                event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type() ||
                event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type() ||
                event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type() ||
                event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (in079Mode && event.getOverlay() == VanillaGuiOverlay.ALL.type()) {
            Scp079CameraOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        }
    }
}
