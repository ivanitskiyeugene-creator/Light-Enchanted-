package dev.zerosevennine.client.handler;

import dev.zerosevennine.block.AbstractCameraBlock;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public static boolean breachScannerActive = false;
    public static List<Scp079Session.LogEntry> logs = new ArrayList<>();

    public static boolean isZoomed = false;
    public static float zoomFovFactor = 0.82f;

    private static float initialYaw = 0.0f;
    private static float initialPitch = 15.0f;
    private static boolean hasInitialAngles = false;

    public static void handleSyncState(boolean active, BlockPos cameraPos, int t, int e, int ne,
                                      float currAp, float mAp, float regen, boolean breachScanner, List<Scp079Session.LogEntry> l) {
        boolean wasIn079 = in079Mode;
        boolean cameraChanged = !cameraPos.equals(activeCameraPos);

        in079Mode = active;
        activeCameraPos = cameraPos;
        tier = t;
        exp = e;
        nextExp = ne;
        ap = currAp;
        maxAp = mAp;
        apRegen = regen;
        breachScannerActive = breachScanner;
        logs = l;

        if (active && (!hasInitialAngles || cameraChanged || !wasIn079)) {
            updateInitialCameraAngles();
        }
    }

    public static void updateInitialCameraAngles() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && activeCameraPos != null) {
            BlockState state = mc.level.getBlockState(activeCameraPos);
            Direction facing = Direction.NORTH;
            if (state.hasProperty(AbstractCameraBlock.FACING)) {
                facing = state.getValue(AbstractCameraBlock.FACING);
            }

            initialYaw = switch (facing) {
                case NORTH -> 180.0f;
                case SOUTH -> 0.0f;
                case WEST -> 90.0f;
                case EAST -> -90.0f;
                default -> 0.0f;
            };
            initialPitch = 15.0f;
            hasInitialAngles = true;

            if (mc.player != null) {
                mc.player.setYRot(initialYaw);
                mc.player.setXRot(initialPitch);
                mc.player.yRotO = initialYaw;
                mc.player.xRotO = initialPitch;
            }
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (in079Mode) {
            // Completely block physical player movement on WASD and jumping
            event.getInput().forwardImpulse = 0.0f;
            event.getInput().leftImpulse = 0.0f;
            event.getInput().up = false;
            event.getInput().down = false;
            event.getInput().left = false;
            event.getInput().right = false;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !in079Mode) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (!hasInitialAngles) {
                updateInitialCameraAngles();
            }

            // Target smooth FOV (Narrower security camera FOV 0.82x base, 0.30x zoom)
            float targetFov = isZoomed ? 0.30f : 0.82f;
            zoomFovFactor += (targetFov - zoomFovFactor) * 0.25f;

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

        // Clamp rotation angles relative to camera mount facing into the room
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float diffYaw = currentYaw - initialYaw;
        while (diffYaw < -180.0f) diffYaw += 360.0f;
        while (diffYaw > 180.0f) diffYaw -= 360.0f;

        float clampedDiffYaw = Math.max(-80.0f, Math.min(80.0f, diffYaw));
        float clampedPitch = Math.max(-35.0f, Math.min(45.0f, currentPitch));

        float targetYaw = initialYaw + clampedDiffYaw;
        if (diffYaw != clampedDiffYaw || currentPitch != clampedPitch) {
            mc.player.setYRot(targetYaw);
            mc.player.setXRot(clampedPitch);
        }

        event.setYaw(targetYaw);
        event.setPitch(clampedPitch);
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

        // LMB (Button 0): Primary Device Interaction / Camera Jump / Toggle Door / Elevator / Tesla
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            if (handlePrimaryInteraction(mc)) {
                event.setCanceled(true);
                return;
            }
        }

        // RMB (Button 1): Secondary Action / Door Lockdown (locks single door, spends AP)
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
            if (handleSecondaryInteraction(mc)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private static boolean handlePrimaryInteraction(Minecraft mc) {
        if (mc.level == null || mc.player == null || activeCameraPos == null) return false;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int cx = width / 2;
        int cy = height / 2;

        CameraBlockEntity cam = FacilityNetworkManager.getCamera(activeCameraPos);
        if (cam == null && mc.level.getBlockEntity(activeCameraPos) instanceof CameraBlockEntity cbe) {
            cam = cbe;
        }

        if (cam != null) {
            double camX = activeCameraPos.getX() + 0.5;
            double camY = activeCameraPos.getY() + 0.5;
            double camZ = activeCameraPos.getZ() + 0.5;

            double yawRad = Math.toRadians(mc.player.getYRot());
            double pitchRad = Math.toRadians(mc.player.getXRot());

            double cosY = Math.cos(yawRad);
            double sinY = Math.sin(yawRad);
            double cosP = Math.cos(pitchRad);
            double sinP = Math.sin(pitchRad);

            double fovYDeg = mc.options.fov().get() * zoomFovFactor;
            double fovYRad = Math.toRadians(Math.max(10.0, fovYDeg));
            double scale = (height / 2.0) / Math.tan(fovYRad / 2.0);

            // 1. Check if aiming at linked device (Door, Tesla, Elevator)
            for (CameraBlockEntity.LinkedDevice dev : cam.getLinkedDevices()) {
                double dx = (dev.pos.getX() + 0.5) - camX;
                double dy = (dev.pos.getY() + 0.5) - camY;
                double dz = (dev.pos.getZ() + 0.5) - camZ;

                double viewX = -dx * cosY - dz * sinY;
                double viewForwardXZ = -dx * sinY + dz * cosY;
                double viewY = dy * cosP - viewForwardXZ * sinP;
                double viewZ = dy * sinP + viewForwardXZ * cosP;

                if (viewZ > 0.3) {
                    int sx = (int) (cx + (viewX / viewZ) * scale);
                    int sy = (int) (cy - (viewY / viewZ) * scale);

                    if (Math.abs(sx - cx) < 32 && Math.abs(sy - cy) < 32) {
                        if (dev.type == DeviceType.DOOR || dev.type == DeviceType.ELEVATOR) {
                            ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(dev.pos, C2SInteractDevicePacket.Action.TOGGLE_DOOR));
                            return true;
                        } else if (dev.type == DeviceType.TESLA) {
                            ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(dev.pos, C2SInteractDevicePacket.Action.PING));
                            return true;
                        } else if (dev.type == DeviceType.LIGHT) {
                            ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(dev.pos, C2SInteractDevicePacket.Action.BLACKOUT));
                            return true;
                        }
                    }
                }
            }

            // 2. Check if aiming at neighbor camera doorway
            for (Map.Entry<Direction, BlockPos> neighbor : cam.getWasdNeighbors().entrySet()) {
                BlockPos nPos = neighbor.getValue();
                double dx = (nPos.getX() + 0.5) - camX;
                double dy = (nPos.getY() + 0.5) - camY;
                double dz = (nPos.getZ() + 0.5) - camZ;

                double viewX = -dx * cosY - dz * sinY;
                double viewForwardXZ = -dx * sinY + dz * cosY;
                double viewY = dy * cosP - viewForwardXZ * sinP;
                double viewZ = dy * sinP + viewForwardXZ * cosP;

                if (viewZ > 0.3) {
                    int sx = (int) (cx + (viewX / viewZ) * scale);
                    int sy = (int) (cy - (viewY / viewZ) * scale);

                    if (Math.abs(sx - cx) < 32 && Math.abs(sy - cy) < 32) {
                        ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(nPos));
                        return true;
                    }
                }
            }
        }

        // 3. Fallback: direct block raycast
        if (mc.hitResult instanceof BlockHitResult blockHit && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(blockHit.getBlockPos(), C2SInteractDevicePacket.Action.TOGGLE_DOOR));
            return true;
        }

        return false;
    }

    private static boolean handleSecondaryInteraction(Minecraft mc) {
        if (mc.level == null || mc.player == null || activeCameraPos == null) return false;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int cx = width / 2;
        int cy = height / 2;

        CameraBlockEntity cam = FacilityNetworkManager.getCamera(activeCameraPos);
        if (cam == null && mc.level.getBlockEntity(activeCameraPos) instanceof CameraBlockEntity cbe) {
            cam = cbe;
        }

        if (cam != null) {
            double camX = activeCameraPos.getX() + 0.5;
            double camY = activeCameraPos.getY() + 0.5;
            double camZ = activeCameraPos.getZ() + 0.5;

            double yawRad = Math.toRadians(mc.player.getYRot());
            double pitchRad = Math.toRadians(mc.player.getXRot());

            double cosY = Math.cos(yawRad);
            double sinY = Math.sin(yawRad);
            double cosP = Math.cos(pitchRad);
            double sinP = Math.sin(pitchRad);

            double fovYDeg = mc.options.fov().get() * zoomFovFactor;
            double fovYRad = Math.toRadians(Math.max(10.0, fovYDeg));
            double scale = (height / 2.0) / Math.tan(fovYRad / 2.0);

            for (CameraBlockEntity.LinkedDevice dev : cam.getLinkedDevices()) {
                double dx = (dev.pos.getX() + 0.5) - camX;
                double dy = (dev.pos.getY() + 0.5) - camY;
                double dz = (dev.pos.getZ() + 0.5) - camZ;

                double viewX = -dx * cosY - dz * sinY;
                double viewForwardXZ = -dx * sinY + dz * cosY;
                double viewY = dy * cosP - viewForwardXZ * sinP;
                double viewZ = dy * sinP + viewForwardXZ * cosP;

                if (viewZ > 0.3) {
                    int sx = (int) (cx + (viewX / viewZ) * scale);
                    int sy = (int) (cy - (viewY / viewZ) * scale);

                    if (Math.abs(sx - cx) < 32 && Math.abs(sy - cy) < 32) {
                        ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(dev.pos, C2SInteractDevicePacket.Action.LOCK_DOOR));
                        return true;
                    }
                }
            }
        }

        if (mc.hitResult instanceof BlockHitResult blockHit && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(blockHit.getBlockPos(), C2SInteractDevicePacket.Action.LOCK_DOOR));
            return true;
        }

        return false;
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
        if (in079Mode && event.getOverlay() == VanillaGuiOverlay.CHAT_PANEL.type()) {
            Scp079CameraOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        }
    }
}
