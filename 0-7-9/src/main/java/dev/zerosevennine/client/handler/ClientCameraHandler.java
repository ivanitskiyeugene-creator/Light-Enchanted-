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
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.UniversalDoorHandler;
import dev.zerosevennine.init.ModSounds;
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
import net.minecraft.sounds.SoundSource;
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

    // Camera Switch Noise & Zone Transition Delay
    public static int switchStaticTicks = 0;
    public static int zoneTransitionTicks = 0;
    public static final int MAX_ZONE_TRANSITION_TICKS = 35;
    public static FacilityZone sourceZone = FacilityZone.HCZ;
    public static FacilityZone targetZone = FacilityZone.HCZ;
    public static String transitionTargetRoom = "CONNECTED FEED";

    private static float initialYaw = 0.0f;
    private static float initialPitch = 15.0f;
    private static boolean hasInitialAngles = false;

    private static long lastInteractTimeMs = 0;

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

    public static void startZoneTransition(FacilityZone from, FacilityZone to, BlockPos targetPos) {
        sourceZone = from != null ? from : FacilityZone.HCZ;
        targetZone = to != null ? to : FacilityZone.HCZ;
        zoneTransitionTicks = MAX_ZONE_TRANSITION_TICKS;

        FacilityNetworkManager.RoomNode room = FacilityNetworkManager.findRoomForCamera(targetPos);
        if (room != null && room.roomName != null && !room.roomName.isEmpty()) {
            transitionTargetRoom = room.roomName;
        } else {
            transitionTargetRoom = targetZone.name() + " SECTOR";
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            mc.level.playSound(mc.player, mc.player.blockPosition(), ModSounds.ZONE_DIAL_TONE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
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

            Map<Direction, BlockPos> dynamicNeighbors = FacilityNetworkManager.getDynamicWasdMap(activeCameraPos);
            for (Map.Entry<Direction, BlockPos> neighbor : dynamicNeighbors.entrySet()) {
                BlockPos nPos = neighbor.getValue();
                if (nPos == null) continue;
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
                if (cam != null && cam.hasDevice(hitPos)) {
                    CameraBlockEntity.LinkedDevice dev = cam.getDevice(hitPos);
                    double dist = eyePos.distanceTo(new Vec3(hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5));
                    return new AimTarget(AimTarget.Type.DEVICE_MARKER, hitPos, dev, null, dist);
                }
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

        FacilityZone oldZone = null;
        if (activeCameraPos != null) {
            CameraBlockEntity oldCam = FacilityNetworkManager.getCamera(activeCameraPos);
            oldZone = oldCam != null ? oldCam.getZone() : null;
        }

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

            if (wasIn079 && cameraChanged) {
                CameraBlockEntity newCam = FacilityNetworkManager.getCamera(cameraPos);
                FacilityZone newZone = newCam != null ? newCam.getZone() : null;

                if (oldZone != null && newZone != null && oldZone != newZone) {
                    startZoneTransition(oldZone, newZone, cameraPos);
                } else {
                    switchStaticTicks = 6;
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && mc.level != null) {
                        mc.level.playSound(mc.player, mc.player.blockPosition(), ModSounds.CAMERA_SWITCH.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
                    }
                }
            }
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
        if (mc.player == null) return;

        // Transition timers
        if (zoneTransitionTicks > 0) {
            zoneTransitionTicks--;
            if (zoneTransitionTicks == 0) {
                switchStaticTicks = 6;
                if (mc.player != null && mc.level != null) {
                    mc.level.playSound(mc.player, mc.player.blockPosition(), ModSounds.CAMERA_SWITCH.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
                }
            }
        }

        if (switchStaticTicks > 0) {
            switchStaticTicks--;
        }

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        if (hasInitialAngles) {
            float deltaYaw = yaw - initialYaw;
            while (deltaYaw < -180.0f) deltaYaw += 360.0f;
            while (deltaYaw > 180.0f) deltaYaw -= 360.0f;

            if (deltaYaw > 85.0f) yaw = initialYaw + 85.0f;
            if (deltaYaw < -85.0f) yaw = initialYaw - 85.0f;

            float deltaPitch = pitch - initialPitch;
            if (deltaPitch > 40.0f) pitch = initialPitch + 40.0f;
            if (deltaPitch < -40.0f) pitch = initialPitch - 40.0f;

            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }

        if (mc.player.tickCount % 2 == 0) {
            ModNetwork.CHANNEL.sendToServer(new C2SUpdateCameraOrientationPacket(yaw, pitch));
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (in079Mode && isZoomed) {
            event.setNewFovModifier(event.getFovModifier() * zoomFovFactor);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!in079Mode || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        CameraBlockEntity cam = FacilityNetworkManager.getCamera(activeCameraPos);
        if (cam == null && mc.level.getBlockEntity(activeCameraPos) instanceof CameraBlockEntity cbe) {
            cam = cbe;
        }
        if (cam == null) return;

        AimTarget hoveredTarget = findAimTarget(mc);

        for (CameraBlockEntity.LinkedDevice dev : cam.getLinkedDevices()) {
            double dx = (dev.pos.getX() + 0.5) - camPos.x;
            double dy = (dev.pos.getY() + 1.2) - camPos.y;
            double dz = (dev.pos.getZ() + 0.5) - camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 60.0 || dist < 0.5) continue;

            boolean isHovered = (hoveredTarget != null && hoveredTarget.type == AimTarget.Type.DEVICE_MARKER && hoveredTarget.pos.equals(dev.pos));

            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            poseStack.mulPose(camera.rotation());

            float scale = (float) Math.max(0.015, dist * 0.0035);
            poseStack.scale(-scale, -scale, scale);

            render3DDeviceMarker(poseStack, bufferSource, mc.font, dev, dist, isHovered);

            poseStack.popPose();
        }

        Map<Direction, BlockPos> dynamicNeighbors = FacilityNetworkManager.getDynamicWasdMap(activeCameraPos);
        for (Map.Entry<Direction, BlockPos> neighbor : dynamicNeighbors.entrySet()) {
            BlockPos nPos = neighbor.getValue();
            if (nPos == null) continue;
            double dx = (nPos.getX() + 0.5) - camPos.x;
            double dy = (nPos.getY() + 1.2) - camPos.y;
            double dz = (nPos.getZ() + 0.5) - camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 60.0 || dist < 0.5) continue;

            boolean isHovered = (hoveredTarget != null && hoveredTarget.type == AimTarget.Type.NEIGHBOR_MARKER && hoveredTarget.pos.equals(nPos));

            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            poseStack.mulPose(camera.rotation());

            float scale = (float) Math.max(0.015, dist * 0.0035);
            poseStack.scale(-scale, -scale, scale);

            render3DNeighborMarker(poseStack, bufferSource, mc.font, neighbor.getKey(), dist, isHovered);

            poseStack.popPose();
        }
    }

    private static void render3DDeviceMarker(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                             CameraBlockEntity.LinkedDevice dev, double dist, boolean isHovered) {
        Matrix4f mat = poseStack.last().pose();

        int bracketCol = isHovered ? 0xFFFFFFFF : 0xCCE0F7FA;
        int textCol = isHovered ? 0xFFFFFFFF : 0xFFE0F7FA;

        int bw = 8;
        int bh = 8;

        // Subtle 4 Corner Brackets
        fillQuad(bufferSource, mat, -bw, -bh, -bw + 3, -bh + 1, bracketCol);
        fillQuad(bufferSource, mat, -bw, -bh, -bw + 1, -bh + 3, bracketCol);

        fillQuad(bufferSource, mat, bw - 3, -bh, bw, -bh + 1, bracketCol);
        fillQuad(bufferSource, mat, bw - 1, -bh, bw, -bh + 3, bracketCol);

        fillQuad(bufferSource, mat, -bw, bh - 1, -bw + 3, bh, bracketCol);
        fillQuad(bufferSource, mat, -bw, bh - 3, -bw + 1, bh, bracketCol);

        fillQuad(bufferSource, mat, bw - 3, bh - 1, bw, bh, bracketCol);
        fillQuad(bufferSource, mat, bw - 1, bh - 3, bw, bh, bracketCol);

        if (!isHovered) {
            return;
        }

        String typeLabel = dev.type.getLabel().toUpperCase();
        String actionPrompt = switch (dev.type) {
            case DOOR, ELEVATOR -> "[LMB] TOGGLE DOOR  |  [RMB/SPACE] LOCK";
            case TESLA -> "[LMB] OVERCHARGE (35 AP)";
            case LIGHT -> "[LMB] BLACKOUT (40 AP)";
            case SPEAKER -> "[LMB] INTERCOM TRANSMIT";
            case GENERATOR -> "[GENERATOR TELEMETRY]";
        };

        float tw1 = font.width(typeLabel);
        float tw2 = font.width(actionPrompt);
        float maxW = Math.max(tw1, tw2) + 8;

        int bgCol = 0xAA000000;
        fillQuad(bufferSource, mat, -maxW / 2, bh + 4, maxW / 2, bh + 26, bgCol);
        fillQuad(bufferSource, mat, -maxW / 2, bh + 4, maxW / 2, bh + 5, bracketCol);

        font.drawInBatch(typeLabel, -tw1 / 2, bh + 6, textCol, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch(actionPrompt, -tw2 / 2, bh + 16, 0xFFE0F7FA, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
    }

    private static void render3DNeighborMarker(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                               Direction dir, double dist, boolean isHovered) {
        Matrix4f mat = poseStack.last().pose();
        int col = isHovered ? 0xFFFFFFFF : 0xCCE0F7FA;
        String dirKey = switch (dir) {
            case NORTH -> "[W]";
            case SOUTH -> "[S]";
            case WEST -> "[A]";
            case EAST -> "[D]";
            default -> "[JUMP]";
        };

        int bgCol = 0xAA000000;
        fillQuad(bufferSource, mat, -10, -10, 10, 10, bgCol);
        fillQuad(bufferSource, mat, -10, -10, 10, -9, col);
        fillQuad(bufferSource, mat, -10, 9, 10, 10, col);

        float kw = font.width(dirKey);
        font.drawInBatch(dirKey, -kw / 2, -3, 0xFFE0F7FA, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);

        if (isHovered) {
            String label = "[LMB] JUMP CAMERA (2 AP)";
            float lw = font.width(label);
            fillQuad(bufferSource, mat, -lw / 2 - 4, 14, lw / 2 + 4, 25, 0xAA000000);
            fillQuad(bufferSource, mat, -lw / 2 - 4, 14, lw / 2 + 4, 15, col);
            font.drawInBatch(label, -lw / 2, 16, 0xFFE0F7FA, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        }
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

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && event.getAction() == GLFW.GLFW_PRESS) {
            isZoomed = !isZoomed;
            event.setCanceled(true);
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            handlePrimaryInteraction(mc);
            event.setCanceled(true);
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
            handleSecondaryInteraction(mc);
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public static void onKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!in079Mode) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        if (event.isAttack()) {
            handlePrimaryInteraction(mc);
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (event.isUseItem()) {
            handleSecondaryInteraction(mc);
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void handlePrimaryInteraction(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastInteractTimeMs < 250) return;
        lastInteractTimeMs = now;

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
            if (mc.level != null && UniversalDoorHandler.isDoorLikeBlock(mc.level.getBlockState(target.pos))) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(target.pos, C2SInteractDevicePacket.Action.TOGGLE_DOOR));
            }
        }
    }

    private static void handleSecondaryInteraction(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastInteractTimeMs < 250) return;
        lastInteractTimeMs = now;

        AimTarget target = findAimTarget(mc);
        if (target == null) return;

        if (target.type == AimTarget.Type.DEVICE_MARKER || target.type == AimTarget.Type.WORLD_BLOCK) {
            if (mc.level != null && (target.type == AimTarget.Type.DEVICE_MARKER || UniversalDoorHandler.isDoorLikeBlock(mc.level.getBlockState(target.pos)))) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(target.pos, C2SInteractDevicePacket.Action.LOCK_DOOR));
            }
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!in079Mode) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getKey() == GLFW.GLFW_KEY_TAB || mc.options.keyPlayerList.matches(event.getKey(), event.getScanCode())) {
                mc.setScreen(new Scp079MapScreen());
                return;
            }

            if (event.getKey() == GLFW.GLFW_KEY_W) {
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(Direction.NORTH));
            } else if (event.getKey() == GLFW.GLFW_KEY_S) {
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(Direction.SOUTH));
            } else if (event.getKey() == GLFW.GLFW_KEY_A) {
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(Direction.WEST));
            } else if (event.getKey() == GLFW.GLFW_KEY_D) {
                ModNetwork.CHANNEL.sendToServer(new C2SSwitchCameraPacket(Direction.EAST));
            }

            if (event.getKey() == GLFW.GLFW_KEY_F) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(activeCameraPos, C2SInteractDevicePacket.Action.BLACKOUT));
            }

            if (event.getKey() == GLFW.GLFW_KEY_G) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(activeCameraPos, C2SInteractDevicePacket.Action.LOCKDOWN));
            }

            if (event.getKey() == GLFW.GLFW_KEY_V) {
                ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(activeCameraPos, C2SInteractDevicePacket.Action.SPEAKER));
            }

            if (event.getKey() == GLFW.GLFW_KEY_SPACE) {
                AimTarget target = findAimTarget(mc);
                if (target != null && mc.level != null && (target.type == AimTarget.Type.DEVICE_MARKER || UniversalDoorHandler.isDoorLikeBlock(mc.level.getBlockState(target.pos)))) {
                    ModNetwork.CHANNEL.sendToServer(new C2SInteractDevicePacket(target.pos, C2SInteractDevicePacket.Action.LOCK_DOOR));
                }
            }

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
                event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type() ||
                event.getOverlay() == VanillaGuiOverlay.PLAYER_LIST.type()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLiving(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.decoration.ArmorStand stand) {
            if (stand.getTags().contains("scp079_tesla_dummy") || stand.isMarker()) {
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
