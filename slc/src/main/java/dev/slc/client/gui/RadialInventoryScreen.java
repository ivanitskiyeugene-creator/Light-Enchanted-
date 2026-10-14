package dev.slc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.slc.network.C2SSelectInventorySlotPacket;
import dev.slc.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class RadialInventoryScreen extends Screen {
    public static final int SLOTS = 8;
    private int hoveredSlot = -1;

    // Authentic SCP:SL Gold/Yellow Palette from reference
    private static final int COLOR_GOLD_LINE = 0xFFD8B01C;
    private static final int COLOR_HOVER_FILL = 0xAAD8B01C;
    private static final int COLOR_BG_SECTOR = 0x660A0D12;
    private static final int COLOR_GOLD_TEXT = 0xFFF1C40F;

    public RadialInventoryScreen() {
        super(Component.literal("Tactical Radial Inventory"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        Font font = this.font;
        int cx = width / 2;
        int cy = height / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        double innerR = 48.0;
        double outerR = 102.0;

        // Calculate hovered sector
        if (dist >= innerR && dist <= outerR + 15) {
            double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0 + (360.0 / SLOTS / 2.0);
            if (angle < 0) angle += 360.0;
            hoveredSlot = ((int) (angle / (360.0 / SLOTS))) % SLOTS;
        } else {
            hoveredSlot = -1;
        }

        // 1. Render Sectors (Outlines, Backgrounds, and Highlights)
        double sectorAngle = 360.0 / SLOTS;
        for (int i = 0; i < SLOTS; i++) {
            boolean isHovered = (i == hoveredSlot);
            double midAngle = Math.toRadians((i * sectorAngle) - 90.0);
            double iconR = (innerR + outerR) / 2.0;

            int iconX = (int) (cx + Math.cos(midAngle) * iconR) - 8;
            int iconY = (int) (cy + Math.sin(midAngle) * iconR) - 8;

            // Sector fill
            int fillColor = isHovered ? COLOR_HOVER_FILL : COLOR_BG_SECTOR;
            graphics.fill(iconX - 8, iconY - 8, iconX + 24, iconY + 24, fillColor);

            // Item Icon
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, iconX, iconY);
                graphics.renderItemDecorations(font, stack, iconX, iconY);
            }
        }

        // Draw radial sector dividing lines and rings
        renderCircleOutline(graphics, cx, cy, (int) innerR, COLOR_GOLD_LINE);
        renderCircleOutline(graphics, cx, cy, (int) outerR, COLOR_GOLD_LINE);

        for (int i = 0; i < SLOTS; i++) {
            double lineAngle = Math.toRadians((i * sectorAngle) - 90.0 - (sectorAngle / 2.0));
            int x1 = (int) (cx + Math.cos(lineAngle) * innerR);
            int y1 = (int) (cy + Math.sin(lineAngle) * innerR);
            int x2 = (int) (cx + Math.cos(lineAngle) * outerR);
            int y2 = (int) (cy + Math.sin(lineAngle) * outerR);
            graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, COLOR_GOLD_LINE);
        }

        // 2. Center Info Circle (1:1 SL Center Layout)
        graphics.fill(cx - 46, cy - 46, cx + 46, cy + 46, 0xEE080B10);

        if (hoveredSlot >= 0 && hoveredSlot < SLOTS) {
            ItemStack stack = player.getInventory().getItem(hoveredSlot);
            if (!stack.isEmpty()) {
                String itemName = stack.getHoverName().getString().toUpperCase();
                // Item Header
                graphics.drawCenteredString(font, "§l" + itemName, cx, cy - 32, COLOR_GOLD_TEXT);

                // Stats Section
                graphics.fill(cx - 38, cy - 20, cx + 38, cy - 19, COLOR_GOLD_LINE);

                String itemId = stack.getItem().toString().toLowerCase();
                if (itemId.contains("gun") || itemId.contains("rifle") || itemId.contains("pistol") || itemId.contains("card")) {
                    graphics.drawString(font, "§7Type:", cx - 36, cy - 14, 0xAAAAAA, false);
                    graphics.drawString(font, "§eTACZ / FR", cx + 2, cy - 14, COLOR_GOLD_TEXT, false);

                    graphics.drawString(font, "§7Slot:", cx - 36, cy - 4, 0xAAAAAA, false);
                    graphics.drawString(font, "§e#" + (hoveredSlot + 1), cx + 18, cy - 4, COLOR_GOLD_TEXT, false);
                } else {
                    graphics.drawCenteredString(font, "§eКоличество: §f" + stack.getCount(), cx, cy - 10, 0xFFFFFF);
                }

                // Attachments / Details
                graphics.drawString(font, "§8[ПКМ - Выбросить]", cx - 36, cy + 22, 0x888888, false);
            } else {
                graphics.drawCenteredString(font, "§8[ПУСТОЙ СЛОТ " + (hoveredSlot + 1) + "]", cx, cy - 4, 0x666666);
            }
        } else {
            graphics.drawCenteredString(font, "§7ИНВЕНТАРЬ", cx, cy - 12, 0xAAAAAA);
            graphics.drawCenteredString(font, "§88 СЛОТОВ", cx, cy + 2, 0x666666);
        }

        // 3. Bottom-Left Stamina Meter (1:1 SL Yellow Box)
        int gaugeX = 14;
        int gaugeY = height - 24;
        int gaugeW = 70;
        int gaugeH = 12;

        graphics.fill(gaugeX - 1, gaugeY - 1, gaugeX + gaugeW + 1, gaugeY + gaugeH + 1, COLOR_GOLD_LINE);
        graphics.fill(gaugeX, gaugeY, gaugeX + gaugeW, gaugeY + gaugeH, 0xFFC29B12);
        graphics.drawCenteredString(font, "§0§l100%", gaugeX + gaugeW / 2, gaugeY + 2, 0x000000);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCircleOutline(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians((i * (360.0 / segments)));
            double a2 = Math.toRadians(((i + 1) * (360.0 / segments)));
            int x1 = (int) (cx + Math.cos(a1) * radius);
            int y1 = (int) (cy + Math.sin(a1) * radius);
            int x2 = (int) (cx + Math.cos(a2) * radius);
            int y2 = (int) (cy + Math.sin(a2) * radius);
            graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hoveredSlot >= 0 && hoveredSlot < SLOTS) {
            if (button == 0) {
                selectSlotAndClose(hoveredSlot);
                return true;
            } else if (button == 1) {
                // RMB drops item
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    player.drop(false);
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredSlot >= 0 && hoveredSlot < SLOTS) {
            selectSlotAndClose(hoveredSlot);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Tab (258) or Escape (256)
        if (keyCode == 258 || keyCode == 256) {
            if (hoveredSlot >= 0 && hoveredSlot < SLOTS) {
                selectSlotAndClose(hoveredSlot);
            } else {
                this.onClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectSlotAndClose(int slot) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.getInventory().selected = slot;
            ModNetwork.CHANNEL.sendToServer(new C2SSelectInventorySlotPacket(slot));
        }
        this.onClose();
    }
}
