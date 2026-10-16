package dev.slc.client.gui;

import dev.slc.client.ClientRoundCache;
import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.role.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.registries.ForgeRegistries;

/** Compact replacement for the vanilla survival HUD during a living SLC round. */
public final class SlcHudOverlay implements IGuiOverlay {
    public static final SlcHudOverlay INSTANCE = new SlcHudOverlay();
    private static final int PANEL = 0xD90A0D12;
    private static final int BORDER = 0xFFF1C40F;

    private SlcHudOverlay() {}

    public static boolean shouldRender(Minecraft mc) {
        return mc.player != null && !mc.player.isSpectator() && ClientRoundCache.isRoundActive()
                && ClientRoundCache.getLocalRole() != Role.SPECTATOR && ClientRoundCache.getLocalRole() != Role.SCP_079;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!shouldRender(mc)) return;
        Player player = mc.player;
        Font font = mc.font;

        renderVitalPanel(graphics, font, player, 18, height - 58);
        renderAccessCard(graphics, font, player, width / 2 - 70, height - 46);
        renderWeaponPanel(graphics, font, player, width - 190, height - 58);
        renderCrosshair(graphics, width / 2, height / 2);
    }

    private static void renderVitalPanel(GuiGraphics graphics, Font font, Player player, int x, int y) {
        panel(graphics, x, y, 156, 43, BORDER);
        float healthRatio = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
        bar(graphics, font, x + 8, y + 8, 140, healthRatio, 0xFFE04B4B,
                "HP " + Math.ceil(player.getHealth()) + " / " + Math.ceil(player.getMaxHealth()));
        bar(graphics, font, x + 8, y + 25, 140, ClientRoundCache.getStamina() / 100.0F, 0xFFF1C40F,
                "STAMINA " + Math.round(ClientRoundCache.getStamina()) + "%");
    }

    private static void renderAccessCard(GuiGraphics graphics, Font font, Player player, int x, int y) {
        ItemStack card = findByPath(player, "keycard", "key_card", "access_card");
        panel(graphics, x, y, 140, 31, 0xFF5FB7E9);
        graphics.drawString(font, "КАРТА  [CTRL]", x + 6, y + 5, 0xFFB9E7FF, false);
        if (!card.isEmpty()) {
            graphics.renderItem(card, x + 6, y + 14);
            graphics.drawString(font, shorten(card.getHoverName().getString(), 16), x + 27, y + 18, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, "НЕТ КАРТЫ", x + 6, y + 18, 0xFF9AA5AF, false);
        }
    }

    private static void renderWeaponPanel(GuiGraphics graphics, Font font, Player player, int x, int y) {
        panel(graphics, x, y, 172, 43, 0xFFE67E22);
        ItemStack held = player.getMainHandItem();
        graphics.drawString(font, "ОРУЖИЕ", x + 7, y + 5, 0xFFFFC894, false);
        if (!held.isEmpty()) {
            graphics.renderItem(held, x + 7, y + 17);
            graphics.drawString(font, shorten(held.getHoverName().getString(), 18), x + 28, y + 20, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, "БЕЗ ОРУЖИЯ", x + 7, y + 20, 0xFF9AA5AF, false);
        }
        ItemStack ammo = player.getInventory().getItem(SlcLoadoutSavedData.AMMO_SLOT);
        if (!ammo.isEmpty()) {
            graphics.renderItem(ammo, x + 7, y + 29);
            graphics.drawString(font, "AMMO x" + ammo.getCount(), x + 28, y + 33, 0xFFE67E22, false);
        }
    }

    private static ItemStack findByPath(Player player, String... names) {
        for (int i = 0; i < SlcLoadoutSavedData.AMMO_SLOT; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null) continue;
            String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
            for (String name : names) if (path.contains(name)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 1, accent);
        graphics.fill(x, y + height - 1, x + width, y + height, accent);
        graphics.fill(x, y, x + 1, y + height, accent);
        graphics.fill(x + width - 1, y, x + width, y + height, accent);
    }

    private static void bar(GuiGraphics graphics, Font font, int x, int y, int width, float ratio, int color, String label) {
        ratio = Math.max(0.0F, Math.min(1.0F, ratio));
        graphics.fill(x, y, x + width, y + 10, 0xFF1D232C);
        graphics.fill(x, y, x + Math.round(width * ratio), y + 10, color);
        graphics.drawCenteredString(font, label, x + width / 2, y + 1, 0xFF101318);
    }

    private static void renderCrosshair(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 7, y, x - 2, y + 1, BORDER);
        graphics.fill(x + 3, y, x + 8, y + 1, BORDER);
        graphics.fill(x, y - 7, x + 1, y - 2, BORDER);
        graphics.fill(x, y + 3, x + 1, y + 8, BORDER);
    }

    private static String shorten(String text, int maximum) {
        return text.length() <= maximum ? text : text.substring(0, Math.max(1, maximum - 1)) + "…";
    }
}
