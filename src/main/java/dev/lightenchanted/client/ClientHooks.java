package dev.lightenchanted.client;

import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import dev.lightenchanted.client.gui.LightEmitterScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-only entry points invoked from common code through DistExecutor.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openEmitterScreen(LightEmitterBlockEntity emitter) {
        Minecraft.getInstance().setScreen(new LightEmitterScreen(emitter));
    }
}
