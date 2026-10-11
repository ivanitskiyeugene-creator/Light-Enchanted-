package dev.zerosevennine.client.handler;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.block.AbstractCameraBlock;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.client.gui.FacilityMapDesignerScreen;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.facility.RoomShape;
import dev.zerosevennine.init.ModSounds;
import dev.zerosevennine.network.C2SEditMapRoomPacket;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ZeroSevenNine.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientCameraLinker {
    private static final ResourceLocation SCANLINES = new ResourceLocation(ZeroSevenNine.MOD_ID, "textures/gui/scanlines.png");

    private static boolean linkingMode = false;
    private static FacilityZone targetZone = FacilityZone.HCZ;
    private static int targetGx = 0;
    private static int targetGy = 0;
    private static String roomName = "ROOM";
    private static RoomShape roomShape = RoomShape.SQUARE;
    private static int roomRotation = 0;
    private static boolean hasGenerator = false;
    private static final List<int[]> activePolygon = new ArrayList<>();
    private static final List<BlockPos> linkedCameras = new ArrayList<>();

    public static boolean isLinkingMode() {
        return linkingMode;
    }

    public static void enterLinkMode(FacilityZone zone, int gx, int gy, String name,
                                     RoomShape shape, int rot, boolean hasGen,
                                     List<int[]> polygon, List<BlockPos> cameras) {
        linkingMode = true;
        targetZone = zone;
        targetGx = gx;
        targetGy = gy;
        roomName = (name != null && !name.isEmpty()) ? name : ("ROOM " + gx + ":" + gy);
        roomShape = shape != null ? shape : RoomShape.SQUARE;
        roomRotation = rot;
        hasGenerator = hasGen;

        activePolygon.clear();
        if (polygon != null) {
            for (int[] pt : polygon) {
                activePolygon.add(new int[]{pt[0], pt[1]});
            }
        }

        linkedCameras.clear();
        if (cameras != null) {
            for (BlockPos p : cameras) {
                linkedCameras.add(p.immutable());
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            mc.level.playSound(mc.player, mc.player.blockPosition(), ModSounds.CAMERA_LINK.get(), SoundSource.PLAYERS, 1.0f, 1.2f);
            mc.player.displayClientMessage(Component.literal("§b[079 LINKER] §fРежим привязки камер к §e" + roomName + "§f! Нажимайте по камерам в мире. §7[Shift] для возврата."), true);
        }
    }

    public static void finishAndReturnToDesigner() {
        if (!linkingMode) return;
        linkingMode = false;

        Minecraft mc = Minecraft.getInstance();

        // 1. Send update to server
        ModNetwork.CHANNEL.sendToServer(new C2SEditMapRoomPacket(
                C2SEditMapRoomPacket.Action.SAVE_ROOM,
                targetZone,
                targetGx,
                targetGy,
                roomName,
                FacilityMapNodeBlockEntity.RoomType.STANDARD,
                roomShape,
                roomRotation,
                targetZone,
                hasGenerator,
                linkedCameras,
                activePolygon
        ));

        // 2. Play confirm sound & reopen screen
        if (mc.player != null && mc.level != null) {
            mc.level.playSound(mc.player, mc.player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.5f);
            mc.player.displayClientMessage(Component.literal("§a[079 LINKER] §fПривязка сохранена! Привязано камер: §b" + linkedCameras.size()), true);
        }

        FacilityMapDesignerScreen screen = new FacilityMapDesignerScreen();
        screen.restoreState(targetZone, targetGx, targetGy, roomName, roomShape, roomRotation, hasGenerator, activePolygon, linkedCameras);
        mc.setScreen(screen);
    }

    private static BlockPos getLookedCameraPos(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 lookVec = mc.player.getViewVector(1.0f);
        Vec3 end = eyePos.add(lookVec.scale(12.0));

        BlockHitResult hit = mc.level.clip(new ClipContext(eyePos, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            if (mc.level.getBlockState(pos).getBlock() instanceof AbstractCameraBlock ||
                mc.level.getBlockEntity(pos) instanceof CameraBlockEntity) {
                return pos;
            }
        }
        return null;
    }

    private static void toggleCameraLink(BlockPos camPos, Minecraft mc) {
        if (camPos == null || mc.player == null || mc.level == null) return;
        camPos = camPos.immutable();

        if (linkedCameras.contains(camPos)) {
            linkedCameras.remove(camPos);
            mc.level.playSound(mc.player, camPos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 0.8f, 0.9f);
            mc.player.displayClientMessage(Component.literal("§c[-] Камера [" + camPos.getX() + ", " + camPos.getY() + ", " + camPos.getZ() + "] отвязана от " + roomName + " (Осталось: " + linkedCameras.size() + ")"), true);
        } else {
            linkedCameras.add(camPos);
            mc.level.playSound(mc.player, camPos, ModSounds.CAMERA_LINK.get(), SoundSource.PLAYERS, 1.0f, 1.4f);
            mc.player.displayClientMessage(Component.literal("§a[+] ✓ Камера [" + camPos.getX() + ", " + camPos.getY() + ", " + camPos.getZ() + "] привязана к " + roomName + "! (Всего: " + linkedCameras.size() + ")"), true);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!linkingMode) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getKey() == GLFW.GLFW_KEY_LEFT_SHIFT || event.getKey() == GLFW.GLFW_KEY_RIGHT_SHIFT ||
                event.getKey() == GLFW.GLFW_KEY_ESCAPE || mc.options.keyShift.matches(event.getKey(), event.getScanCode())) {
                finishAndReturnToDesigner();
            }
        }
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (!linkingMode) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        if (event.getAction() == GLFW.GLFW_PRESS && (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            BlockPos camPos = getLookedCameraPos(mc);
            if (camPos != null) {
                toggleCameraLink(camPos, mc);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!linkingMode) return;
        if (event.getLevel().isClientSide) {
            BlockPos pos = event.getPos();
            if (event.getLevel().getBlockState(pos).getBlock() instanceof AbstractCameraBlock ||
                event.getLevel().getBlockEntity(pos) instanceof CameraBlockEntity) {
                toggleCameraLink(pos, Minecraft.getInstance());
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!linkingMode || event.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        Font font = mc.font;

        // Top SCP Technical Linker Banner
        int bannerW = 340;
        int bannerH = 42;
        int bannerX = (width - bannerW) / 2;
        int bannerY = 8;

        // Background & Technical cyan borders
        graphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, 0xDD020B18);
        graphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + 1, 0xFF00E5FF);
        graphics.fill(bannerX, bannerY + bannerH - 1, bannerX + bannerW, bannerY + bannerH, 0xFF00E5FF);
        graphics.fill(bannerX, bannerY, bannerX + 1, bannerY + bannerH, 0xFF00E5FF);
        graphics.fill(bannerX + bannerW - 1, bannerY, bannerX + bannerW, bannerY + bannerH, 0xFF00E5FF);

        // Corner technical brackets
        int cb = 6;
        graphics.fill(bannerX, bannerY, bannerX + cb, bannerY + 2, 0xFFFFFFFF);
        graphics.fill(bannerX, bannerY, bannerX + 2, bannerY + cb, 0xFFFFFFFF);
        graphics.fill(bannerX + bannerW - cb, bannerY, bannerX + bannerW, bannerY + 2, 0xFFFFFFFF);
        graphics.fill(bannerX + bannerW - 2, bannerY, bannerX + bannerW, bannerY + cb, 0xFFFFFFFF);

        // Title Row
        String title = "[ РЕЖИМ ПРИВЯЗКИ КАМЕР ] // " + targetZone.name() + " [" + targetGx + ", " + targetGy + "]";
        graphics.drawString(font, title, bannerX + 10, bannerY + 6, 0xFF00E5FF, true);

        // Room & Count Row
        String roomInfo = "КОМНАТА: " + roomName.toUpperCase() + "  |  ПРИВЯЗАНО: " + linkedCameras.size();
        graphics.drawString(font, roomInfo, bannerX + 10, bannerY + 18, 0xFFE0F7FA, false);

        // Instructions Row
        String help = "[КЛИК ПО КАМЕРЕ] — Привязать/Убрать  |  [SHIFT] — Сохранить и выйти";
        graphics.drawString(font, help, bannerX + 10, bannerY + 30, 0xFF81D4FA, false);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!linkingMode || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Font font = mc.font;

        BlockPos hoveredCam = getLookedCameraPos(mc);

        // Search cameras in radius around player
        BlockPos playerPos = mc.player.blockPosition();
        int radius = 32;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -16; y <= 16; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = playerPos.offset(x, y, z);
                    if (mc.level.getBlockState(p).getBlock() instanceof AbstractCameraBlock ||
                        mc.level.getBlockEntity(p) instanceof CameraBlockEntity) {

                        double dx = (p.getX() + 0.5) - camPos.x;
                        double dy = (p.getY() + 0.5) - camPos.y;
                        double dz = (p.getZ() + 0.5) - camPos.z;
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist > 48.0 || dist < 0.2) continue;

                        boolean isLinked = linkedCameras.contains(p);
                        boolean isHovered = (hoveredCam != null && hoveredCam.equals(p));

                        poseStack.pushPose();
                        poseStack.translate(dx, dy, dz);
                        poseStack.mulPose(camera.rotation());

                        float scale = (float) Math.max(0.015, dist * 0.0035);
                        poseStack.scale(-scale, -scale, scale);

                        render3DCameraLinkMarker(poseStack, bufferSource, font, p, isLinked, isHovered);

                        poseStack.popPose();
                    }
                }
            }
        }
    }

    private static void render3DCameraLinkMarker(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                                 BlockPos pos, boolean isLinked, boolean isHovered) {
        Matrix4f mat = poseStack.last().pose();

        int frameCol = isLinked ? 0xFF00FF88 : (isHovered ? 0xFFFFFFFF : 0xAA00E5FF);
        int bgCol = 0xAA000000;

        int boxW = 80;
        int boxH = 26;

        fillQuad(bufferSource, mat, -boxW / 2, -boxH / 2, boxW / 2, boxH / 2, bgCol);
        fillQuad(bufferSource, mat, -boxW / 2, -boxH / 2, boxW / 2, -boxH / 2 + 1, frameCol);
        fillQuad(bufferSource, mat, -boxW / 2, boxH / 2 - 1, boxW / 2, boxH / 2, frameCol);
        fillQuad(bufferSource, mat, -boxW / 2, -boxH / 2, -boxW / 2 + 1, boxH / 2, frameCol);
        fillQuad(bufferSource, mat, boxW / 2 - 1, -boxH / 2, boxW / 2, boxH / 2, frameCol);

        String title = isLinked ? "✓ ПРИВЯЗАНА" : "+ НАЖМИТЕ ДЛЯ СВЯЗИ";
        int titleCol = isLinked ? 0xFF00FF88 : (isHovered ? 0xFFFFFFFF : 0xFF81D4FA);
        float tw1 = font.width(title);
        font.drawInBatch(title, -tw1 / 2, -boxH / 2 + 4, titleCol, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);

        String coord = "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
        float tw2 = font.width(coord);
        font.drawInBatch(coord, -tw2 / 2, -boxH / 2 + 14, 0xFFE0F7FA, false, mat, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
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
}
