package dev.slc.client.gui;

import dev.slc.network.C2SSelectInventorySlotPacket;
import dev.slc.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class RadialInventoryScreen extends Screen {
    public static final int SLOTS = 8;
    private int hoveredSlot = -1;

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

        int cx = width / 2;
        int cy = height / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        // Calculate hovered slot angle
        if (dist > 25 && dist < 120) {
            double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0 + (360.0 / SLOTS / 2.0);
            if (angle < 0) angle += 360.0;
            hoveredSlot = ((int) (angle / (360.0 / SLOTS))) % SLOTS;
        } else {
            hoveredSlot = -1;
        }

        // Draw radial wheel background circle
        graphics.fillGradient(cx - 85, cy - 85, cx + 85, cy + 85, 0xAA0A0D14, 0xDD0E1420);

        // Draw sectors & item icons
        double radius = 55.0;
        for (int i = 0; i < SLOTS; i++) {
            double angleRad = Math.toRadians((i * (360.0 / SLOTS)) - 90.0);
            int slotX = (int) (cx + Math.cos(angleRad) * radius) - 8;
            int slotY = (int) (cy + Math.sin(angleRad) * radius) - 8;

            boolean isHovered = (i == hoveredSlot);
            boolean isSelected = (i == player.getInventory().selected);

            // Slot backdrop
            int slotBg = isHovered ? 0xEE3498DB : (isSelected ? 0xDD2ECC71 : 0x88151D28);
            graphics.fill(slotX - 4, slotY - 4, slotX + 20, slotY + 20, slotBg);

            // Item Icon
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, slotX, slotY);
                graphics.renderItemDecorations(this.font, stack, slotX, slotY);
            } else {
                // Empty slot number
                graphics.drawCenteredString(this.font, String.valueOf(i + 1), slotX + 8, slotY + 4, 0x556677);
            }
        }

        // Center hub display
        graphics.fill(cx - 24, cy - 24, cx + 24, cy + 24, 0xEE080C12);

        if (hoveredSlot >= 0 && hoveredSlot < SLOTS) {
            ItemStack stack = player.getInventory().getItem(hoveredSlot);
            if (!stack.isEmpty()) {
                String itemName = stack.getHoverName().getString();
                graphics.drawCenteredString(this.font, "§e" + itemName, cx, cy + 95, 0xF1C40F);
            } else {
                graphics.drawCenteredString(this.font, "§7[Пустой слот " + (hoveredSlot + 1) + "]", cx, cy + 95, 0x888888);
            }
        } else {
            graphics.drawCenteredString(this.font, "§8[Колесо выбора (8 слотов)]", cx, cy + 95, 0x666666);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredSlot >= 0 && hoveredSlot < SLOTS) {
            selectSlotAndClose(hoveredSlot);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (hoveredSlot >= 0 && hoveredSlot < SLOTS) {
            selectSlotAndClose(hoveredSlot);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Tab (258) or Escape (256) or 'E'
        if (keyCode == 258 || keyCode == 256 || keyCode == 69) {
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
