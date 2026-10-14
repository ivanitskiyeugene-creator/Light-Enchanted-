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
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}
