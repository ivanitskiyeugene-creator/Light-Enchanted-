package dev.slc.client.handler;

import dev.slc.client.ClientRoundCache;
import dev.slc.client.ClientSetup;
import dev.slc.client.gui.RadialInventoryScreen;
import dev.slc.loadout.QuickAction;
import dev.slc.network.C2SActivateLoadoutActionPacket;
import dev.slc.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Canonical SCP:SL controls while a living player is in an SLC round. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientRoundControls {
    private static int delayedUseTicks;

    private ClientRoundControls() {}

    private static boolean active(Minecraft mc) {
        return mc.player != null && !mc.player.isSpectator() && ClientRoundCache.isRoundActive();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) {
            delayedUseTicks = 0;
            return;
        }

        // The old Minecraft inventory is intentionally unavailable during a round. Tab opens the
        // SLC wheel; the exact bindings remain reassignable in Controls.
        if (mc.screen == null && ClientSetup.RADIAL_INVENTORY_KEY.consumeClick()) {
            mc.setScreen(new RadialInventoryScreen());
        }
        activateWhenPressed(ClientSetup.CARD_ACTION_KEY, QuickAction.CARD);
        activateWhenPressed(ClientSetup.INTERACT_ACTION_KEY, QuickAction.INTERACT);
        activateWhenPressed(ClientSetup.GRENADE_ACTION_KEY, QuickAction.GRENADE);
        activateWhenPressed(ClientSetup.MEDKIT_ACTION_KEY, QuickAction.MEDKIT);

        // SCP:SL sprint is Shift. Suppress vanilla Ctrl-sprint and Shift-sneak only while in round.
        mc.options.keySprint.setDown(false);
        mc.options.keyShift.setDown(false);
        if (ClientSetup.SPRINT_ACTION_KEY.isDown()) mc.player.setSprinting(true);

        if (delayedUseTicks > 0 && --delayedUseTicks == 0 && mc.screen == null) {
            mc.startUseItem();
        }
    }

    private static void activateWhenPressed(net.minecraft.client.KeyMapping key, QuickAction action) {
        while (key.consumeClick()) ModNetwork.CHANNEL.sendToServer(new C2SActivateLoadoutActionPacket(action));
    }

    /** Called only after the server has validated and selected the mapped physical stack. */
    public static void useActiveItemAfterSync() {
        delayedUseTicks = 2;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (active(mc) && event.getNewScreen() instanceof InventoryScreen) {
            // E never exposes vanilla storage during a match; its SLC action is processed above.
            event.setNewScreen(null);
        }
    }
}
