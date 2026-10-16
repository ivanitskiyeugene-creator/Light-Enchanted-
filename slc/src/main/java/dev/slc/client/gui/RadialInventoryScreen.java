package dev.slc.client.gui;

import dev.slc.client.ClientSlcArmorCache;
import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.network.C2SSelectInventorySlotPacket;
import dev.slc.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The only in-round inventory interface. It reads the operative's real backing cells but never
 * exposes the Minecraft inventory screen. Eight sectors are a page, not an eight-item limit.
 */
public class RadialInventoryScreen extends Screen {
    private static final int PAGE_SIZE = 8;
    private static final int COLOR_GOLD_LINE = 0xFFD8B01C;
    private static final int COLOR_HOVER_FILL = 0xAAD8B01C;
    private static final int COLOR_BG_SECTOR = 0x660A0D12;
    private static final int COLOR_GOLD_TEXT = 0xFFF1C40F;

    private int hoveredIndex = -1;
    private int page;

    public RadialInventoryScreen() {
        super(Component.literal("SLC Tactical Inventory"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private List<Integer> occupiedCells(Player player) {
        List<Integer> cells = new ArrayList<>();
        for (int slot = 0; slot < SlcLoadoutSavedData.AMMO_SLOT; slot++) {
            if (!player.getInventory().getItem(slot).isEmpty()) cells.add(slot);
        }
        return cells;
    }

    private int pageCount(List<Integer> cells) {
        return Math.max(1, (cells.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int sourceCellAt(Player player, int index) {
        List<Integer> cells = occupiedCells(player);
        int source = page * PAGE_SIZE + index;
        return source >= 0 && source < cells.size() ? cells.get(source) : -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        List<Integer> cells = occupiedCells(player);
        page = Math.min(page, pageCount(cells) - 1);

        Font font = this.font;
        int cx = width / 2;
        int cy = height / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double innerRadius = 48.0;
        double outerRadius = 102.0;

        if (distance >= innerRadius && distance <= outerRadius + 15) {
            double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0 + 22.5;
            if (angle < 0) angle += 360.0;
            hoveredIndex = ((int) (angle / 45.0)) % PAGE_SIZE;
        } else {
            hoveredIndex = -1;
        }

        for (int i = 0; i < PAGE_SIZE; i++) {
            boolean hovered = i == hoveredIndex;
            double middle = Math.toRadians((i * 45.0) - 90.0);
            double iconRadius = (innerRadius + outerRadius) / 2.0;
            int iconX = (int) (cx + Math.cos(middle) * iconRadius) - 8;
            int iconY = (int) (cy + Math.sin(middle) * iconRadius) - 8;
            graphics.fill(iconX - 8, iconY - 8, iconX + 24, iconY + 24, hovered ? COLOR_HOVER_FILL : COLOR_BG_SECTOR);

            int sourceCell = sourceCellAt(player, i);
            if (sourceCell >= 0) {
                ItemStack stack = player.getInventory().getItem(sourceCell);
                graphics.renderItem(stack, iconX, iconY);
                graphics.renderItemDecorations(font, stack, iconX, iconY);
                if (sourceCell == player.getInventory().selected) {
                    graphics.fill(iconX - 9, iconY - 9, iconX + 25, iconY - 8, 0xFF62D87B);
                }
            }
        }

        renderCircleOutline(graphics, cx, cy, (int) innerRadius, COLOR_GOLD_LINE);
        renderCircleOutline(graphics, cx, cy, (int) outerRadius, COLOR_GOLD_LINE);
        for (int i = 0; i < PAGE_SIZE; i++) {
            double lineAngle = Math.toRadians((i * 45.0) - 112.5);
            int x1 = (int) (cx + Math.cos(lineAngle) * innerRadius);
            int y1 = (int) (cy + Math.sin(lineAngle) * innerRadius);
            int x2 = (int) (cx + Math.cos(lineAngle) * outerRadius);
            int y2 = (int) (cy + Math.sin(lineAngle) * outerRadius);
            graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, COLOR_GOLD_LINE);
        }

        graphics.fill(cx - 46, cy - 46, cx + 46, cy + 46, 0xEE080B10);
        int sourceCell = hoveredIndex < 0 ? -1 : sourceCellAt(player, hoveredIndex);
        if (sourceCell >= 0) {
            ItemStack stack = player.getInventory().getItem(sourceCell);
            graphics.drawCenteredString(font, "§l" + stack.getHoverName().getString().toUpperCase(), cx, cy - 28, COLOR_GOLD_TEXT);
            graphics.drawCenteredString(font, "§eКоличество: §f" + stack.getCount(), cx, cy - 6, 0xFFFFFFFF);
            graphics.drawCenteredString(font, "§8ЛКМ выбрать • ПКМ выбросить", cx, cy + 18, 0x888888);
        } else {
            graphics.drawCenteredString(font, "§7SLC ИНВЕНТАРЬ", cx, cy - 12, 0xAAAAAA);
            graphics.drawCenteredString(font, "§8Страница " + (page + 1) + "/" + pageCount(cells) + " • " + cells.size() + " предметов", cx, cy + 2, 0x888888);
        }
        graphics.drawCenteredString(font, "Колесо мыши: страницы", cx, cy + 36, 0xAAAAAA);

        renderSpecialCell(graphics, font, cx + 118, cy - 12, ClientSlcArmorCache.getArmor(), "БРОНЯ", COLOR_GOLD_TEXT);
        renderSpecialCell(graphics, font, cx + 118, cy + 46,
                player.getInventory().getItem(SlcLoadoutSavedData.AMMO_SLOT), "AMMO", 0xFFE67E22);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSpecialCell(GuiGraphics graphics, Font font, int x, int y, ItemStack stack, String label, int color) {
        graphics.fill(x - 3, y - 3, x + 21, y + 21, 0xCC080B10);
        graphics.fill(x - 4, y - 4, x + 22, y - 3, color);
        graphics.fill(x - 4, y + 21, x + 22, y + 22, color);
        graphics.fill(x - 4, y - 4, x - 3, y + 22, color);
        graphics.fill(x + 21, y - 4, x + 22, y + 22, color);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
        }
        graphics.drawCenteredString(font, label, x + 8, y + 25, color);
    }

    private void renderCircleOutline(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int i = 0; i < 32; i++) {
            double a1 = Math.toRadians(i * 11.25);
            double a2 = Math.toRadians((i + 1) * 11.25);
            int x1 = (int) (cx + Math.cos(a1) * radius);
            int y1 = (int) (cy + Math.sin(a1) * radius);
            int x2 = (int) (cx + Math.cos(a2) * radius);
            int y2 = (int) (cy + Math.sin(a2) * radius);
            graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return true;
        int pages = pageCount(occupiedCells(player));
        page = Math.floorMod(page + (delta < 0 ? 1 : -1), pages);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Player player = Minecraft.getInstance().player;
        int source = player == null || hoveredIndex < 0 ? -1 : sourceCellAt(player, hoveredIndex);
        if (source >= 0 && button == 0) {
            selectAndClose(source);
            return true;
        }
        if (source >= 0 && button == 1 && player != null) {
            ModNetwork.CHANNEL.sendToServer(new C2SSelectInventorySlotPacket(source, true));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Player player = Minecraft.getInstance().player;
            int source = player == null || hoveredIndex < 0 ? -1 : sourceCellAt(player, hoveredIndex);
            if (source >= 0) {
                selectAndClose(source);
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258 || keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectAndClose(int sourceCell) {
        ModNetwork.CHANNEL.sendToServer(new C2SSelectInventorySlotPacket(sourceCell, false));
        this.onClose();
    }
}
