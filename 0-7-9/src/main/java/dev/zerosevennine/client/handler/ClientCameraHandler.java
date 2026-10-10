package dev.zerosevennine.client.handler;

import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.world.level.ClipContext;
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

    public static class AimTarget {
        public enum Type { DEVICE_MARKER, NEIGHBOR_MARKER, WORLD_BLOCK }
        public final Type type;
        public final BlockPos pos;
        public final CameraBlockEntity.LinkedDevice device;
        public final Direction neighborDir;
        public final double distance;

        public AimTarget(Type type, BlockPos pos, CameraBlockEntity.LinkedDevice device, Direction neighborDir, double distance) {
            this.type = type;
            this.pos = pos;
            this.device = device;
            this.neighborDir = neighborDir;
            this.distance = distance;
        }
    }

    public static AimTarget findAimTarget(Minecraft mc) {
        if (mc.level == null || mc.player == null || activeCameraPos == null) return null;

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 lookVec = mc.player.getViewVector(1.0f);
        double maxDist = 60.0;

        CameraBlockEntity cam = FacilityNetworkManager.getCamera(activeCameraPos);
        if (cam == null && mc.level.getBlockEntity(activeCameraPos) instanceof CameraBlockEntity cbe) {
            cam = cbe;
        }

        AimTarget bestMarkerTarget = null;
        double bestMarkerDistToRay = Double.MAX_VALUE;

        if (cam != null) {
            // 1. Check 3D Floating Device Markers up to 60m
            for (CameraBlockEntity.LinkedDevice dev : cam.getLinkedDevices()) {
                Vec3 target = new Vec3(dev.pos.getX() + 0.5, dev.pos.getY() + 1.2, dev.pos.getZ() + 0.5);
                Vec3 toTarget = target.subtract(eyePos);
                double dist = toTarget.length();
                if (dist > maxDist) continue;

                double proj = toTarget.dot(lookVec);
                if (proj > 0.0) {
                    Vec3 closest = eyePos.add(lookVec.scale(proj));
                    double distToRay = closest.distanceTo(target);
                    double hitRadius = Math.max(1.2, proj * 0.08);
                    if (distToRay <= hitRadius && distToRay < bestMarkerDistToRay) {
                        bestMarkerDistToRay = distToRay;
                        bestMarkerTarget = new AimTarget(AimTarget.Type.DEVICE_MARKER, dev.pos, dev, null, dist);
                    }
                }
            }

            // 2. Check 3D Floating Neighbor Camera Doorway Jump Markers up to 60m
            for (Map.Entry<Direction, BlockPos> neighbor : cam.getWasdNeighbors().entrySet()) {
                BlockPos nPos = neighbor.getValue();
                Vec3 target = new Vec3(nPos.getX() + 0.5, nPos.getY() + 1.2, nPos.getZ() + 0.5);
                Vec3 toTarget = target.subtract(eyePos);
                double dist = toTarget.length();
                if (dist > maxDist) continue;

                double proj = toTarget.dot(lookVec);
                if (proj > 0.0) {
                    Vec3 closest = eyePos.add(lookVec.scale(proj));
                    double distToRay = closest.distanceTo(target);
                    double hitRadius = Math.max(1.5, proj * 0.09);
                    if (distToRay <= hitRadius && distToRay < bestMarkerDistToRay) {
                        bestMarkerDistToRay = distToRay;
                        bestMarkerTarget = new AimTarget(AimTarget.Type.NEIGHBOR_MARKER, nPos, null, neighbor.getKey(), dist);
                    }
                }
            }
        }

        if (bestMarkerTarget != null) {
            return bestMarkerTarget;
        }

        // 3. 60-meter raycast for world blocks (offset rayStart past camera bounding box)
        Vec3 rayStart = eyePos.add(lookVec.scale(0.7));
        Vec3 traceEnd = eyePos.add(lookVec.scale(maxDist));
        BlockHitResult hit = mc.level.clip(new ClipContext(
            rayStart,
            traceEnd,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            mc.player
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hit.getBlockPos();
            if (!hitPos.equals(activeCameraPos)) {
                double dist = eyePos.distanceTo(new Vec3(hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5));
                return new AimTarget(AimTarget.Type.WORLD_BLOCK, hitPos, null, null, dist);
            }
        }

        return null;
    }

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

        AimTarget currentAim = findAimTarget(mc);

        // Disable depth testing so holographic UI markers render 100% on top of all blocks and walls
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 1. Real 3D In-World Device Hologram Markers (up to 60m)
        for (CameraBlockEntity.LinkedDevice dev : cam.getLinkedDevices()) {
            BlockPos p = dev.pos;
            double targetX = p.getX() + 0.5;
            double targetY = p.getY() + 1.2;
            double targetZ = p.getZ() + 0.5;

            double dx = targetX - camPos.x;
            double dy = targetY - camPos.y;
            double dz = targetZ - camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 60.0) continue;

            boolean isHovered = (currentAim != null && currentAim.pos.equals(dev.pos));

            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            poseStack.mulPose(camera.rotation());

            float scale = (float) Math.max(0.012f, 0.015f * (dist * 0.12f));
            poseStack.scale(-scale, -scale, scale);

            render3DDeviceMarker(poseStack, bufferSource, font, dev, dist, isHovered);

            poseStack.popPose();
        }

        // 2. Real 3D In-World Neighbor Camera Doorway Jump Markers (up to 60m)
        for (Map.Entry<Direction, BlockPos> neighbor : cam.getWasdNeighbors().entrySet()) {
            BlockPos nPos = neighbor.getValue();
            double targetX = nPos.getX() + 0.5;
            double targetY = nPos.getY() + 1.2;
            double targetZ = nPos.getZ() + 0.5;

            double dx = targetX - camPos.x;
            double dy = targetY - camPos.y;
            double dz = targetZ - camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 60.0) continue;

            boolean isHovered = (currentAim != null && currentAim.pos.equals(nPos));

            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            poseStack.mulPose(camera.rotation());

            float scale = (float) Math.max(0.012f, 0.015f * (dist * 0.12f));
            poseStack.scale(-scale, -scale, scale);

            render3DNeighborMarker(poseStack, bufferSource, font, neighbor.getKey(), dist, isHovered);

            poseStack.popPose();
        }

        bufferSource.endBatch();

        // Restore depth testing for subsequent render stages
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void render3DDeviceMarker(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                             CameraBlockEntity.LinkedDevice dev, double dist, boolean isHovered) {
        Matrix4f mat = poseStack.last().pose();
        int bracketCol = isHovered ? 0xFFFFFFFF : (dev.isLocked ? 0xFFFF3333 : 0xFF00E5FF);
        int textCol = isHovered ? 0xFFFFFFFF : 0xFFE0F7FA;

        int bw = isHovered ? 24 : 20;
        int bh = isHovered ? 24 : 20;

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

        String statusSuffix = dev.isLocked ? " [LOCKED]" : "";
        String typeLabel = dev.type.getLabel().toUpperCase() + statusSuffix + " // " + ((int) dist) + "m";
        String actionPrompt = switch (dev.type) {
            case DOOR -> dev.isLocked ? "[LMB: UNLOCK (-5 AP)]  [RMB: UNLOCK]" : "[LMB] TOGGLE (-5 AP)  [RMB] LOCK (-15 AP)";
            case TESLA -> "[LMB] OVERCHARGE (-35 AP)";
            case ELEVATOR -> "[LMB] CALL / SEND";
            case SPEAKER -> "[V] INTERCOM BROADCAST";
            case LIGHT -> "[F] BLACKOUT LIGHTS (-40 AP)";
        };

        float tw1 = font.width(typeLabel);
        float tw2 = font.width(actionPrompt);
        float maxW = Math.max(tw1, tw2) + 8;

        int bgCol = isHovered ? 0xDD0D1B2A : 0xCC061018;
        fillQuad(bufferSource, mat, -maxW / 2, bh + 5, maxW / 2, bh + 27, bgCol);
        fillQuad(bufferSource, mat, -maxW / 2, bh + 5, maxW / 2, bh + 6, bracketCol);

        font.drawInBatch(typeLabel, -tw1 / 2, bh + 7, textCol, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(actionPrompt, -tw2 / 2, bh + 17, isHovered ? 0xFF00E5FF : 0xAA80DEEA, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
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

        int bgCol = isHovered ? 0xDD0D1B2A : 0xCC061018;
        fillQuad(bufferSource, mat, -14, -14, 14, 14, bgCol);
        fillQuad(bufferSource, mat, -14, -14, 14, -12, col);
        fillQuad(bufferSource, mat, -14, 12, 14, 14, col);

        float kw = font.width(dirKey);
        font.drawInBatch(dirKey, -kw / 2, -4, 0xFFE0F7FA, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);

        String label = "[LMB] JUMP CAMERA (2 AP)";
        float lw = font.width(label);
        fillQuad(bufferSource, mat, -lw / 2 - 4, 18, lw / 2 + 4, 30, bgCol);
        font.drawInBatch(label, -lw / 2, 20, isHovered ? 0xFF00E5FF : 0xAA80DEEA, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
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
            handlePrimaryInteraction(mc);
            event.setCanceled(true);
            return;
        }

        // RMB (Button 1): Secondary Action / Door Lockdown (locks single door, spends AP)
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
            handleSecondaryInteraction(mc);
            event.setCanceled(true);
            return;
        }
    }

    private static void handlePrimaryInteraction(Minecraft mc) {
        AimTarget target = findAimTarget(mc);
        if (target == null) return;

        if (target.type == AimTarget.Type.NEIGHBOR_MARKER) {
            ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(target.pos));
        } else if (target.type == AimTarget.Type.DEVICE_MARKER && target.device != null) {
            if (target.device.type == DeviceType.DOOR || target.device.type == DeviceType.ELEVATOR) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(target.pos, C2SInteractDevicePacket.Action.TOGGLE_DOOR));
            } else if (target.device.type == DeviceType.TESLA) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(target.pos, C2SInteractDevicePacket.Action.TRIGGER_TESLA));
            } else if (target.device.type == DeviceType.LIGHT) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(target.pos, C2SInteractDevicePacket.Action.BLACKOUT));
            } else if (target.device.type == DeviceType.SPEAKER) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(target.pos, C2SInteractDevicePacket.Action.SPEAKER));
            }
        } else if (target.type == AimTarget.Type.WORLD_BLOCK) {
            ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(target.pos, C2SInteractDevicePacket.Action.TOGGLE_DOOR));
        }
    }

    private static void handleSecondaryInteraction(Minecraft mc) {
        AimTarget target = findAimTarget(mc);
        if (target == null) return;

        if (target.type == AimTarget.Type.DEVICE_MARKER || target.type == AimTarget.Type.WORLD_BLOCK) {
            ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(target.pos, C2SInteractDevicePacket.Action.LOCK_DOOR));
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

            // Space: Door Lock on aimed target
            if (event.getKey() == GLFW.GLFW_KEY_SPACE) {
                AimTarget target = findAimTarget(mc);
                if (target != null) {
                    ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(target.pos, C2SInteractDevicePacket.Action.LOCK_DOOR));
                }
            }

            // E: Tactical Ping on aimed target
            if (event.getKey() == GLFW.GLFW_KEY_E) {
                AimTarget target = findAimTarget(mc);
                if (target != null) {
                    ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(target.pos, C2SInteractDevicePacket.Action.PING));
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
