package dev.zerosevennine.network;

import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ZeroSevenNine.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(S2CSync079StatePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSync079StatePacket::encode)
                .decoder(S2CSync079StatePacket::decode)
                .consumerMainThread(S2CSync079StatePacket::handle)
                .add();

        CHANNEL.messageBuilder(S2COpenCameraConfigPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2COpenCameraConfigPacket::encode)
                .decoder(S2COpenCameraConfigPacket::decode)
                .consumerMainThread(S2COpenCameraConfigPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2COpenMapNodeConfigPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2COpenMapNodeConfigPacket::encode)
                .decoder(S2COpenMapNodeConfigPacket::decode)
                .consumerMainThread(S2COpenMapNodeConfigPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SInteractDevicePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SInteractDevicePacket::encode)
                .decoder(C2SInteractDevicePacket::decode)
                .consumerMainThread(C2SInteractDevicePacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SSwitchCameraPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SSwitchCameraPacket::encode)
                .decoder(C2SSwitchCameraPacket::decode)
                .consumerMainThread(C2SSwitchCameraPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SConfigureCameraPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SConfigureCameraPacket::encode)
                .decoder(C2SConfigureCameraPacket::decode)
                .consumerMainThread(C2SConfigureCameraPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SConfigureMapNodePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SConfigureMapNodePacket::encode)
                .decoder(C2SConfigureMapNodePacket::decode)
                .consumerMainThread(C2SConfigureMapNodePacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SUpdateCameraOrientationPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SUpdateCameraOrientationPacket::encode)
                .decoder(C2SUpdateCameraOrientationPacket::decode)
                .consumerMainThread(C2SUpdateCameraOrientationPacket::handle)
                .add();
    }

    public static void send079StateToClient(ServerPlayer player, Scp079Session session) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSync079StatePacket(true, session.getCurrentCameraPos(),
                        session.getTier(), session.getExp(), session.getExpForNextTier(),
                        session.getAp(), session.getMaxAp(), session.getApRegen(),
                        session.isBreachScannerActive(), session.getSystemLog()));
    }

    public static void sendExit079ToClient(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSync079StatePacket(false, BlockPos.ZERO, 1, 0, 100, 0, 100, 0, false, new ArrayList<>()));
    }

    public static void sendOpenCameraConfig(ServerPlayer player, BlockPos camPos, String roomName, FacilityZone zone) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenCameraConfigPacket(camPos, roomName, zone));
    }

    public static void sendOpenMapNodeConfig(ServerPlayer player, BlockPos nodePos, String roomName, FacilityZone zone,
                                            FacilityMapNodeBlockEntity.RoomType type, FacilityZone targetZone,
                                            int gridX, int gridY, boolean hasGen) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2COpenMapNodeConfigPacket(nodePos, roomName, zone, type, targetZone, gridX, gridY, hasGen));
    }
}
