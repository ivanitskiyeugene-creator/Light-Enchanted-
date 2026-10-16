package dev.slc.client;

import dev.slc.SecretLabCubed;
import dev.slc.network.C2SRequestAdminPanelPacket;
import dev.slc.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opens the OP panel from the normal Minecraft key-binding system (M by default). */
@Mod.EventBusSubscriber(modid = SecretLabCubed.MOD_ID, value = Dist.CLIENT)
public final class ClientAdminKeyHandler {
    private ClientAdminKeyHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;
        if (ClientSetup.ADMIN_PANEL_KEY.consumeClick()) {
            ModNetwork.CHANNEL.sendToServer(new C2SRequestAdminPanelPacket());
        }
    }
}
