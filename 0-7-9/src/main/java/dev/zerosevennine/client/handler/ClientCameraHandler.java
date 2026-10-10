package dev.zerosevennine.client.handler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
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

            float targetFov = isZoomed ? 0.30f : 0.82f;
            zoomFovFactor += (targetFov - zoomFovFactor) * 0.25f;

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
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!in079Mode || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || activeCameraPos == null) return;

        CameraBlockEntity cam = FacilityNetworkManager.getCamera(activeCameraPos);
        if (cam == null && mc.level.getBlockEntity(activeCameraPos) instanceof CameraBlockEntity cbe) {
            cam = cbe;
        }
        if (cam == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Font font = mc.font;

        Vec3 lookVec = mc.player.getViewVector(1.0f);
        Vec3 eyePos = mc.player.getEyePosition(1.0f);

        // 1. Real 3D In-World Device Hologram Markers
        for (CameraBlockEntity.LinkedDevice dev : cam.getLinkedDevices()) {
            BlockPos p = dev.pos;
            double targetX = p.getX() + 0.5;
            double targetY = p.getY() + 1.2;
            double targetZ = p.getZ() + 0.5;

            double dx = targetX - camPos.x;
            double dy = targetY - camPos.y;
            double dz = targetZ - camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            Vec3 toTarget = new Vec3(targetX - eyePos.x, targetY - eyePos.y, targetZ - eyePos.z);
            double toTargetDist = toTarget.length();
            double dot = lookVec.dot(toTarget.normalize());
            boolean isHovered = (dot > 0.985 || (toTargetDist < 6.0 && dot > 0.97));

            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            poseStack.mulPose(camera.rotation());

            float scale = (float) Math.max(0.012f, 0.015f * (dist * 0.12f));
            poseStack.scale(-scale, -scale, scale);

            render3DDeviceMarker(poseStack, bufferSource, font, dev, dist, isHovered);

            poseStack.popPose();
        }

        // 2. Real 3D In-World Neighbor Camera Doorway Jump Markers
        for (Map.Entry<Direction, BlockPos> neighbor : cam.getWasdNeighbors().entrySet()) {
            BlockPos nPos = neighbor.getValue();
            double targetX = nPos.getX() + 0.5;
            double targetY = nPos.getY() + 1.2;
            double targetZ = nPos.getZ() + 0.5;

            double dx = targetX - camPos.x;
            double dy = targetY - camPos.y;
            double dz = targetZ - camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            Vec3 toTarget = new Vec3(targetX - eyePos.x, targetY - eyePos.y, targetZ - eyePos.z);
            double toTargetDist = toTarget.length();
            double dot = lookVec.dot(toTarget.normalize());
            boolean isHovered = (dot > 0.985 || (toTargetDist < 6.0 && dot > 0.97));

            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            poseStack.mulPose(camera.rotation());

            float scale = (float) Math.max(0.012f, 0.015f * (dist * 0.12f));
            poseStack.scale(-scale, -scale, scale);

            render3DNeighborMarker(poseStack, bufferSource, font, neighbor.getKey(), dist, isHovered);

            poseStack.popPose();
        }

        bufferSource.endBatch();
    }

    private static void render3DDeviceMarker(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                             CameraBlockEntity.LinkedDevice dev, double dist, boolean isHovered) {
        Matrix4f mat = poseStack.last().pose();
        int bracketCol = isHovered ? 0xFFFFFFFF : 0xFF00E5FF;
        int textCol = 0xFFE0F7FA;

        int bw = 20;
        int bh = 20;

        // Draw 3D holographic brackets
        fillQuad(bufferSource, mat, -bw, -bh, -bw + 8, -bh + 2, bracketCol);
        fillQuad(bufferSource, mat, -bw, -bh, -bw + 2, -bh + 8, bracketCol);

        fillQuad(bufferSource, mat, bw - 8, -bh, bw, -bh + 2, bracketCol);
        fillQuad(bufferSource, mat, bw - 2, -bh, bw, -bh + 8, bracketCol);

        fillQuad(bufferSource, mat, -bw, bh - 2, -bw + 8, bh, bracketCol);
        fillQuad(bufferSource, mat, -bw, bh - 8, -bw + 2, bh, bracketCol);

        fillQuad(bufferSource, mat, bw - 8, bh - 2, bw, bh, bracketCol);
        fillQuad(bufferSource, mat, bw - 2, bh - 8, bw, bh, bracketCol);

        // Center dot
        fillQuad(bufferSource, mat, -2, -2, 2, 2, bracketCol);

        String typeLabel = dev.type.getLabel().toUpperCase() + " // " + ((int) dist) + "m";
        String actionPrompt = switch (dev.type) {
            case DOOR -> dev.isLocked ? "[LOCKED // RMB UNLOCK]" : "[LMB] TOGGLE  [RMB] LOCK";
            case TESLA -> "[LMB] OVERCHARGE (Tesla)";
            case ELEVATOR -> "[LMB] CALL / SEND";
            case SPEAKER -> "[V] INTERCOM BROADCAST";
            case LIGHT -> "[F] BLACKOUT LIGHTS";
        };

        float tw1 = font.width(typeLabel);
        float tw2 = font.width(actionPrompt);
        float maxW = Math.max(tw1, tw2) + 8;

        fillQuad(bufferSource, mat, -maxW / 2, bh + 5, maxW / 2, bh + 27, 0xCC061018);
        fillQuad(bufferSource, mat, -maxW / 2, bh + 5, maxW / 2, bh + 6, bracketCol);

        font.drawInBatch(typeLabel, -tw1 / 2, bh + 7, textCol, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(actionPrompt, -tw2 / 2, bh + 17, 0xAA80DEEA, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
    }

    private static void render3DNeighborMarker(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                               Direction dir, double dist, boolean isHovered) {
        Matrix4f mat = poseStack.last().pose();
        int col = isHovered ? 0xFFFFFFFF : 0xFF00E5FF;
        String dirKey = switch (dir) {
            case NORTH -> "[W]";
            case SOUTH -> "[S]";
            case WEST -> "[A]";
            case EAST -> "[D]";
            default -> "[JUMP]";
        };

        fillQuad(bufferSource, mat, -14, -14, 14, 14, 0xCC061018);
        fillQuad(bufferSource, mat, -14, -14, 14, -12, col);
        fillQuad(bufferSource, mat, -14, 12, 14, 14, col);

        float kw = font.width(dirKey);
        font.drawInBatch(dirKey, -kw / 2, -4, 0xFFE0F7FA, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);

        String label = "[LMB] JUMP CAMERA (2 AP)";
        float lw = font.width(label);
        fillQuad(bufferSource, mat, -lw / 2 - 4, 18, lw / 2 + 4, 30, 0xCC061018);
        font.drawInBatch(label, -lw / 2, 20, 0xAA80DEEA, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
    }

    private static void fillQuad(MultiBufferSource bufferSource, Matrix4f mat, float x1, float y1, float x2, float y2, int color) {
        VertexConsumer builder = bufferSource.getBuffer(RenderType.gui());
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        builder.vertex(mat, x1, y2, 0.0f).color(r, g, b, a).endVertex();
        builder.vertex(mat, x2, y2, 0.0f).color(r, g, b, a).endVertex();
        builder.vertex(mat, x2, y1, 0.0f).color(r, g, b, a).endVertex();
        builder.vertex(mat, x1, y1, 0.0f).color(r, g, b, a).endVertex();
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

        CameraBlockEntity cam = FacilityNetworkManager.getCamera(activeCameraPos);
        if (cam == null && mc.level.getBlockEntity(activeCameraPos) instanceof CameraBlockEntity cbe) {
            cam = cbe;
        }

        Vec3 lookVec = mc.player.getViewVector(1.0f);
        Vec3 eyePos = mc.player.getEyePosition(1.0f);

        if (cam != null) {
            // 1. Check if aiming at linked 3D device marker
            for (CameraBlockEntity.LinkedDevice dev : cam.getLinkedDevices()) {
                Vec3 target = new Vec3(dev.pos.getX() + 0.5, dev.pos.getY() + 1.2, dev.pos.getZ() + 0.5);
                Vec3 toTarget = target.subtract(eyePos);
                double dist = toTarget.length();
                double dot = lookVec.dot(toTarget.normalize());

                if (dot > 0.985 || (dist < 6.0 && dot > 0.97)) {
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

            // 2. Check if aiming at neighbor camera doorway
            for (Map.Entry<Direction, BlockPos> neighbor : cam.getWasdNeighbors().entrySet()) {
                BlockPos nPos = neighbor.getValue();
                Vec3 target = new Vec3(nPos.getX() + 0.5, nPos.getY() + 1.2, nPos.getZ() + 0.5);
                Vec3 toTarget = target.subtract(eyePos);
                double dist = toTarget.length();
                double dot = lookVec.dot(toTarget.normalize());

                if (dot > 0.985 || (dist < 6.0 && dot > 0.97)) {
                    ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(nPos));
                    return true;
                }
            }
        }

        // 3. Fallback: direct block hit
        if (mc.hitResult instanceof BlockHitResult blockHit && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(blockHit.getBlockPos(), C2SInteractDevicePacket.Action.TOGGLE_DOOR));
            return true;
        }

        return false;
    }

    private static boolean handleSecondaryInteraction(Minecraft mc) {
        if (mc.level == null || mc.player == null || activeCameraPos == null) return false;

        CameraBlockEntity cam = FacilityNetworkManager.getCamera(activeCameraPos);
        if (cam == null && mc.level.getBlockEntity(activeCameraPos) instanceof CameraBlockEntity cbe) {
            cam = cbe;
        }

        Vec3 lookVec = mc.player.getViewVector(1.0f);
        Vec3 eyePos = mc.player.getEyePosition(1.0f);

        if (cam != null) {
            for (CameraBlockEntity.LinkedDevice dev : cam.getLinkedDevices()) {
                Vec3 target = new Vec3(dev.pos.getX() + 0.5, dev.pos.getY() + 1.2, dev.pos.getZ() + 0.5);
                Vec3 toTarget = target.subtract(eyePos);
                double dist = toTarget.length();
                double dot = lookVec.dot(toTarget.normalize());

                if (dot > 0.985 || (dist < 6.0 && dot > 0.97)) {
                    ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(dev.pos, C2SInteractDevicePacket.Action.LOCK_DOOR));
                    return true;
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
