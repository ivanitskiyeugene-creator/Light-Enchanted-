package dev.lightenchanted.network;

import dev.lightenchanted.LightEnchanted;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LightEnchanted.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++,
                UpdateLightEmitterPacket.class,
                UpdateLightEmitterPacket::encode,
                UpdateLightEmitterPacket::decode,
                UpdateLightEmitterPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
