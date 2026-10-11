package dev.zerosevennine.client.handler;

import dev.zerosevennine.ai.Scp079PlayerData;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.client.gui.Scp079HudOverlay;
import dev.zerosevennine.client.gui.Scp079MapScreen;
import dev.zerosevennine.facility.UniversalDoorHandler;
import dev.zerosevennine.network.C2SBlackoutPacket;
import dev.zerosevennine.network.C2SInteractDevicePacket;
import dev.zerosevennine.network.C2SLockdownPacket;
import dev.zerosevennine.network.C2SSyncCameraAnglePacket;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "zero_seven_nine", value = Dist.CLIENT)
public class ClientCameraHandler {

    private static boolean active = false;
    private static BlockPos currentCameraPos = null;
    private static Direction cameraFacing = Direction.NORTH;
    private static Scp079PlayerData clientData = new Scp079PlayerData();

    private static float minYaw = -80.0f;
    private static float maxYaw = 80.0f;
    private static float minPitch = -35.0f;
    private static float maxPitch = 35.0f;

    private static double targetFov = 70.0;
    private static double currentFov = 70.0;
    private static boolean isZoomed = false;

    private static int lastSyncTicks = 0;
    private static BlockPos hoveredDevicePos = null;
    private static String hoveredActionText = null;
    private static long lastInteractTimeMs = 0;

    public static void setCameraActive(boolean isActive, BlockPos pos, Direction facing) {
        active = isActive;
        currentCameraPos = pos;
        cameraFacing = facing != null ? facing : Direction.NORTH;

        Minecraft mc = Minecraft.getInstance();
        if (isActive) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (pos != null && mc.player != null) {
                float baseYaw = cameraFacing.toYRot();
                mc.player.setYRot(baseYaw);
                mc.player.setXRot(0.0f);
            }
        } else {
            isZoomed = false;
            targetFov = 70.0;
            currentFov = 70.0;
            hoveredDevicePos = null;
            hoveredActionText = null;
        }
    }

    public static void updateClientData(Scp079PlayerData data) {
        clientData = data;
    }

    public static boolean isActive() { return active; }
    public static BlockPos getCurrentCameraPos() { return currentCameraPos; }
    public static Scp079PlayerData getClientData() { return clientData; }
    public static BlockPos getHoveredDevicePos() { return hoveredDevicePos; }
    public static String getHoveredActionText() { return hoveredActionText; }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        clampRotation(player);

        lastSyncTicks++;
        if (lastSyncTicks >= 2 && currentCameraPos != null) {
            lastSyncTicks = 0;
            float relYaw = player.getYRot() - cameraFacing.toYRot();
            float relPitch = player.getXRot();
            ModNetwork.sendToServer(new C2SSyncCameraAnglePacket(currentCameraPos, relYaw, relPitch));
        }

        currentFov += (targetFov - currentFov) * 0.2;

        updateTargetHover(mc, player);
    }

    private static void updateTargetHover(Minecraft mc, LocalPlayer player) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 reach = eye.add(look.scale(32.0));

        BlockHitResult hit = mc.level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);

            if (UniversalDoorHandler.isDoorLikeBlock(state)) {
                hoveredDevicePos = pos;
                boolean isLocked = isDoorCurrentlyLocked(mc.level, pos);
                hoveredActionText = isLocked ? "§c[DOOR LOCKED]§f [RMB] Unlock (10 AP)" : "§a[DOOR]§f [LMB] Toggle (2 AP) | [RMB] Lock (10 AP)";
                return;
            }

            if (UniversalDoorHandler.isTeslaGateBlock(state)) {
                hoveredDevicePos = pos;
                hoveredActionText = "§e[TESLA GATE]§f [LMB] Overcharge Shock (20 AP)";
                return;
            }
        }

        hoveredDevicePos = null;
        hoveredActionText = null;
    }

    private static boolean isDoorCurrentlyLocked(Level level, BlockPos pos) {
        if (currentCameraPos != null) {
            BlockEntity be = level.getBlockEntity(currentCameraPos);
            if (be instanceof CameraBlockEntity cbe) {
                return cbe.isDoorLocked(pos);
            }
        }
        return false;
    }

    private static void clampRotation(LocalPlayer player) {
        float baseYaw = cameraFacing.toYRot();
        float currentYaw = player.getYRot();
        float diffYaw = ((currentYaw - baseYaw + 180.0f) % 360.0f + 360.0f) % 360.0f - 180.0f;

        if (diffYaw < minYaw) {
            player.setYRot(baseYaw + minYaw);
        } else if (diffYaw > maxYaw) {
            player.setYRot(baseYaw + maxYaw);
        }

        float currentPitch = player.getXRot();
        if (currentPitch < minPitch) {
            player.setXRot(minPitch);
        } else if (currentPitch > maxPitch) {
            player.setXRot(maxPitch);
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (active) {
            event.setFOV(currentFov);
        }
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        if (event.getButton() == 2 && event.getAction() == 1) { // MMB Pressed
            isZoomed = !isZoomed;
            targetFov = isZoomed ? 30.0 : 70.0;
            event.setCanceled(true);
            return;
        }

        if (event.getButton() == 0 && event.getAction() == 1) { // LMB
            if (handlePrimaryInteraction()) {
                event.setCanceled(true);
            }
        } else if (event.getButton() == 1 && event.getAction() == 1) { // RMB
            if (handleSecondaryInteraction()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        if (event.getAction() == 1) { // Key Pressed
            int key = event.getKey();

            if (key == 258) { // TAB
                mc.setScreen(new Scp079MapScreen());
                return;
            }

            if (key == 70) { // F key -> Blackout
                if (currentCameraPos != null && clientData.getTier() >= 2) {
                    ModNetwork.sendToServer(new C2SBlackoutPacket(currentCameraPos));
                }
            } else if (key == 71) { // G key -> Lockdown
                if (currentCameraPos != null && clientData.getTier() >= 3) {
                    ModNetwork.sendToServer(new C2SLockdownPacket(currentCameraPos));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        if (event.isAttack()) {
            if (handlePrimaryInteraction()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        } else if (event.isUseItem()) {
            if (handleSecondaryInteraction()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }
    }

    private static boolean handlePrimaryInteraction() {
        long now = System.currentTimeMillis();
        if (now - lastInteractTimeMs < 250) return true;
        lastInteractTimeMs = now;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return false;

        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 reach = eye.add(look.scale(32.0));

        BlockHitResult hit = mc.level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos targetPos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(targetPos);

            if (UniversalDoorHandler.isDoorLikeBlock(state)) {
                if (currentCameraPos != null) {
                    ModNetwork.sendToServer(new C2SInteractDevicePacket(currentCameraPos, targetPos, C2SInteractDevicePacket.ActionType.DOOR_TOGGLE));
                    return true;
                }
            }

            if (UniversalDoorHandler.isTeslaGateBlock(state)) {
                if (currentCameraPos != null) {
                    ModNetwork.sendToServer(new C2SInteractDevicePacket(currentCameraPos, targetPos, C2SInteractDevicePacket.ActionType.TESLA_TRIGGER));
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean handleSecondaryInteraction() {
        long now = System.currentTimeMillis();
        if (now - lastInteractTimeMs < 250) return true;
        lastInteractTimeMs = now;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return false;

        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 reach = eye.add(look.scale(32.0));

        BlockHitResult hit = mc.level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos targetPos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(targetPos);

            if (UniversalDoorHandler.isDoorLikeBlock(state)) {
                if (currentCameraPos != null) {
                    ModNetwork.sendToServer(new C2SInteractDevicePacket(currentCameraPos, targetPos, C2SInteractDevicePacket.ActionType.DOOR_LOCK_TOGGLE));
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (active) {
            if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type() ||
                event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type() ||
                event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type() ||
                event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type() ||
                event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type() ||
                event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type() ||
                event.getOverlay() == VanillaGuiOverlay.AIR_LEVEL.type()) {
                event.setCanceled(true);
            }
        }
    }
}
