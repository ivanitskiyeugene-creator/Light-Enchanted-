package dev.slc.client.handler;

import com.mojang.blaze3d.platform.InputConstants;
import dev.slc.client.ClientRoundCache;
import dev.slc.client.ClientRoundControlCache;
import dev.slc.client.gui.RadialInventoryScreen;
import dev.slc.controls.RoundControl;
import dev.slc.loadout.QuickAction;
import dev.slc.network.C2SActivateLoadoutActionPacket;
import dev.slc.network.C2SScpAbilityPacket;
import dev.slc.network.ModNetwork;
import dev.slc.scp.ScpAbilityAction;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Server-configured canonical controls that supersede vanilla actions only during SLC rounds. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientRoundControls {
    private static int delayedUseTicks;

    private ClientRoundControls() {}

    private static boolean active(Minecraft mc) {
        return mc.player != null && !mc.player.isSpectator() && ClientRoundCache.isRoundActive();
    }

    private static boolean matches(RoundControl control, int keyCode) {
        return ClientRoundControlCache.key(control) == keyCode;
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc) || mc.screen != null || event.getAction() != GLFW.GLFW_PRESS) return;
        int key = event.getKey();
        if (matches(RoundControl.RADIAL_INVENTORY, key)) {
            mc.setScreen(new RadialInventoryScreen());
        } else if (matches(RoundControl.ACCESS_CARD, key)) {
            ModNetwork.CHANNEL.sendToServer(new C2SActivateLoadoutActionPacket(QuickAction.CARD));
        } else if (matches(RoundControl.INTERACT, key)) {
            KeyMapping.click(mc.options.keyUse.getKey());
        } else if (matches(RoundControl.GRENADE, key)) {
            ModNetwork.CHANNEL.sendToServer(new C2SActivateLoadoutActionPacket(QuickAction.GRENADE));
        } else if (matches(RoundControl.MEDKIT, key)) {
            ModNetwork.CHANNEL.sendToServer(new C2SActivateLoadoutActionPacket(QuickAction.MEDKIT));
        } else if (matches(RoundControl.SCP_ABILITY, key)) {
            ModNetwork.CHANNEL.sendToServer(new C2SScpAbilityPacket(ScpAbilityAction.REANIMATE_049));
        } else if (matches(RoundControl.SCP_MIMIC, key)) {
            ModNetwork.CHANNEL.sendToServer(new C2SScpAbilityPacket(ScpAbilityAction.MIMIC_939));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) {
            delayedUseTicks = 0;
            return;
        }

        // Vanilla Ctrl sprint and Shift sneak are overridden only while the SLC round is live.
        mc.options.keySprint.setDown(false);
        mc.options.keyShift.setDown(false);
        if (isHeld(mc, RoundControl.SPRINT) && ClientRoundCache.getStamina() > 0.0F) {
            mc.player.setSprinting(true);
        }

        if (delayedUseTicks > 0 && --delayedUseTicks == 0 && mc.screen == null) {
            KeyMapping.click(mc.options.keyUse.getKey());
        }
    }

    private static boolean isHeld(Minecraft mc, RoundControl control) {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), ClientRoundControlCache.key(control));
    }

    /** Called after server validation selected the globally classified grenade/medkit stack. */
    public static void useActiveItemAfterSync() {
        delayedUseTicks = 2;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (active(mc) && event.getNewScreen() instanceof InventoryScreen) {
            event.setNewScreen(null);
        }
    }
}
