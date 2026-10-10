package dev.zerosevennine.system;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.init.ModBlocks;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "zero_seven_nine", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Scp079PlayerManager {
    private static final Map<UUID, Scp079Session> SESSIONS = new HashMap<>();

    public static boolean is079(UUID uuid) {
        return SESSIONS.containsKey(uuid);
    }

    public static Scp079Session getSession(UUID uuid) {
        return SESSIONS.get(uuid);
    }

    public static boolean startSession(ServerPlayer player, BlockPos startingCameraPos) {
        CameraBlockEntity camBe = FacilityNetworkManager.getCamera(startingCameraPos);

        // 1. Check all registered cameras
        if (camBe == null) {
            Map<BlockPos, CameraBlockEntity> allCams = FacilityNetworkManager.getCameras();
            if (!allCams.isEmpty()) {
                startingCameraPos = allCams.keySet().iterator().next();
                camBe = allCams.get(startingCameraPos);
            }
        }

        // 2. Scan in radius for any placed camera block entity that wasn't registered yet
        if (camBe == null && player.level() != null) {
            BlockPos pPos = player.blockPosition();
            for (int dx = -32; dx <= 32 && camBe == null; dx += 2) {
                for (int dy = -16; dy <= 16 && camBe == null; dy += 2) {
                    for (int dz = -32; dz <= 32 && camBe == null; dz += 2) {
                        BlockPos checkPos = pPos.offset(dx, dy, dz);
                        BlockEntity be = player.level().getBlockEntity(checkPos);
                        if (be instanceof CameraBlockEntity foundCam) {
                            startingCameraPos = checkPos;
                            camBe = foundCam;
                            FacilityNetworkManager.registerCamera(checkPos, foundCam);
                            break;
                        }
                    }
                }
            }
        }

        // 3. Fallback: If still no camera exists in the facility, auto-place an HCZ camera at player's location to allow immediate instant play
        if (camBe == null && player.level() != null) {
            BlockPos autoPos = player.blockPosition().above();
            player.level().setBlock(autoPos, ModBlocks.HCZ_CAMERA.get().defaultBlockState(), 3);
            BlockEntity autoBe = player.level().getBlockEntity(autoPos);
            if (autoBe instanceof CameraBlockEntity createdCam) {
                startingCameraPos = autoPos;
                camBe = createdCam;
                createdCam.setCameraName("HCZ - Primary Camera");
                FacilityNetworkManager.registerCamera(autoPos, createdCam);
                player.sendSystemMessage(Component.literal("§a[0-7-9] Auto-deployed Surveillance Camera at [" + autoPos.getX() + ", " + autoPos.getY() + ", " + autoPos.getZ() + "]"));
            }
        }

        if (camBe == null) {
            player.sendSystemMessage(Component.literal("§c[0-7-9] Камеры видеонаблюдения не найдены! Поставьте блок камеры (HCZ, LCZ или EZ Camera) на стену и введите /079 join."));
            return false;
        }

        Scp079Session session = new Scp079Session(player.getUUID(), startingCameraPos);
        SESSIONS.put(player.getUUID(), session);

        camBe.setOccupied(true, player.getUUID());
        player.setGameMode(GameType.SPECTATOR);
        player.setDeltaMovement(0, 0, 0);

        // Position player so their eye height matches exactly the camera center (Y + 0.5)
        double eyeOffset = player.getEyeHeight();
        player.teleportTo(startingCameraPos.getX() + 0.5, (startingCameraPos.getY() + 0.5) - eyeOffset, startingCameraPos.getZ() + 0.5);

        ModNetwork.send079StateToClient(player, session);
        return true;
    }

    public static void endSession(ServerPlayer player) {
        Scp079Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            CameraBlockEntity camBe = FacilityNetworkManager.getCamera(session.getCurrentCameraPos());
            if (camBe != null) {
                camBe.setOccupied(false, null);
            }
            player.setGameMode(GameType.SURVIVAL);
            ModNetwork.sendExit079ToClient(player);
        }
    }

    public static void switchCamera(ServerPlayer player, BlockPos newCamPos) {
        Scp079Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;

        CameraBlockEntity oldCam = FacilityNetworkManager.getCamera(session.getCurrentCameraPos());
        if (oldCam != null) {
            oldCam.setOccupied(false, null);
        }

        CameraBlockEntity newCam = FacilityNetworkManager.getCamera(newCamPos);
        if (newCam != null) {
            newCam.setOccupied(true, player.getUUID());
            session.setCurrentCameraPos(newCamPos);
            player.setDeltaMovement(0, 0, 0);
            double eyeOffset = player.getEyeHeight();
            player.teleportTo(newCamPos.getX() + 0.5, (newCamPos.getY() + 0.5) - eyeOffset, newCamPos.getZ() + 0.5);
            ModNetwork.send079StateToClient(player, session);
        }
    }

    public static void broadcastSystemAlert(String message, int colorRgb) {
        for (Scp079Session session : SESSIONS.values()) {
            session.addLog(message, colorRgb, 0);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (Map.Entry<UUID, Scp079Session> entry : SESSIONS.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                BlockPos cp = entry.getValue().getCurrentCameraPos();
                player.setDeltaMovement(0, 0, 0);
                player.fallDistance = 0;
                double eyeOffset = player.getEyeHeight();
                player.teleportTo(cp.getX() + 0.5, (cp.getY() + 0.5) - eyeOffset, cp.getZ() + 0.5);
                entry.getValue().tick(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            endSession(serverPlayer);
        }
    }
}
