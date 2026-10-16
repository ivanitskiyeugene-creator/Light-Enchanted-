package dev.slc.client.gui;

import dev.slc.item.map.ElevatorCabinMarkerItem;
import dev.slc.item.map.ExitZoneMarkerItem;
import dev.slc.item.map.RoleSpawnMarkerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** On-screen editor instructions for a held map tool. This replaces chat spam, not useful editor text. */
public final class MapSetupToolOverlay {
    public static final IGuiOverlay INSTANCE = MapSetupToolOverlay::render;

    private MapSetupToolOverlay() {}

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (Minecraft.getInstance().player == null) return;
        ItemStack stack = Minecraft.getInstance().player.getMainHandItem();
        String title;
        String step;
        if (stack.getItem() instanceof RoleSpawnMarkerItem marker) {
            title = marker.type() == RoleSpawnMarkerItem.Type.POINT
                    ? "ТОЧКА СПАВНА: " + marker.role().getDisplayName()
                    : "ЗОНА СПАВНА: " + marker.role().getDisplayName();
            step = marker.type() == RoleSpawnMarkerItem.Type.POINT
                    ? "ПКМ по полу — добавить точку и направление"
                    : (marker.hasFirstPoint(stack)
                    ? "Shift + ПКМ — сохранить зону  •  ПКМ — перенести угол A"
                    : "ЛКМ или ПКМ — установить угол A");
        } else if (stack.getItem() instanceof ExitZoneMarkerItem marker) {
            title = "ЗОНА ВЫХОДА";
            step = marker.hasFirstPoint(stack) ? "Shift + ПКМ — сохранить объём выхода" : "ЛКМ или ПКМ — установить угол A";
        } else if (stack.getItem() instanceof ElevatorCabinMarkerItem marker) {
            title = "КАБИНА ЛИФТА";
            step = marker.hasFirstPoint(stack)
                    ? "Shift + ПКМ по кнопке лифта — привязать кабину"
                    : "ЛКМ или ПКМ — установить угол A кабины";
        } else return;

        int x = 14;
        int y = height - 68;
        graphics.fill(x - 4, y - 4, x + 332, y + 30, 0xD9080B10);
        graphics.drawString(Minecraft.getInstance().font, title, x, y, 0xFFF1C40F, false);
        graphics.drawString(Minecraft.getInstance().font, step, x, y + 13, 0xFFFFFFFF, false);
    }
}
