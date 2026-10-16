package dev.slc.network;

import dev.slc.SecretLabCubed;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1.0.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SecretLabCubed.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(S2CSyncRoundStatePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSyncRoundStatePacket::encode)
                .decoder(S2CSyncRoundStatePacket::decode)
                .consumerMainThread(S2CSyncRoundStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CSyncSpectatorDataPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSyncSpectatorDataPacket::encode)
                .decoder(S2CSyncSpectatorDataPacket::decode)
                .consumerMainThread(S2CSyncSpectatorDataPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SSelectInventorySlotPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SSelectInventorySlotPacket::encode)
                .decoder(C2SSelectInventorySlotPacket::decode)
                .consumerMainThread(C2SSelectInventorySlotPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CCassiePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCassiePacket::encode)
                .decoder(S2CCassiePacket::decode)
                .consumerMainThread(S2CCassiePacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CSyncSlcArmorPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSyncSlcArmorPacket::encode)
                .decoder(S2CSyncSlcArmorPacket::decode)
                .consumerMainThread(S2CSyncSlcArmorPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2COpenAdminPanelPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2COpenAdminPanelPacket::encode)
                .decoder(S2COpenAdminPanelPacket::decode)
                .consumerMainThread(S2COpenAdminPanelPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CAdminKitPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CAdminKitPacket::encode)
                .decoder(S2CAdminKitPacket::decode)
                .consumerMainThread(S2CAdminKitPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CAdminStatusPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CAdminStatusPacket::encode)
                .decoder(S2CAdminStatusPacket::decode)
                .consumerMainThread(S2CAdminStatusPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SAdminActionPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SAdminActionPacket::encode)
                .decoder(C2SAdminActionPacket::decode)
                .consumerMainThread(C2SAdminActionPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SRequestAdminPanelPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SRequestAdminPanelPacket::encode)
                .decoder(C2SRequestAdminPanelPacket::decode)
                .consumerMainThread(C2SRequestAdminPanelPacket::handle)
                .add();
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}
