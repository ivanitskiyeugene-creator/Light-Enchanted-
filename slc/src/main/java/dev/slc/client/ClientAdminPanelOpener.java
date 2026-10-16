package dev.slc.client;

import dev.slc.client.gui.AdminPanelScreen;
import net.minecraft.client.Minecraft;

public final class ClientAdminPanelOpener {
    private ClientAdminPanelOpener() {}
    public static void open() { Minecraft.getInstance().setScreen(new AdminPanelScreen()); }
}
