package dev.slc.client.gui;

import dev.slc.client.ClientRoundCache;
import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.role.Role;
import dev.slc.spectator.SpectatorTargetInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Minimal in-round SCP:SL HUD. Every operative UI element takes its accent from the role's team:
 * Class-D orange, Scientist yellow, Foundation/MTF blue, Chaos green and SCP red.
 */
public final class SlcHudOverlay implements IGuiOverlay {
    public static final SlcHudOverlay INSTANCE = new SlcHudOverlay();
    private static final int PANEL = 0xD90A0D12;
    private static final int PANEL_TARGET = 0xD810141A;
    private static final int MUTED_TEXT = 0xFF9AA5AF;

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
        Role role = ClientRoundCache.getLocalRole();
        Font font = mc.font;
        int accent = teamColor(role);

        renderVitalPanel(graphics, font, player, role, 18, height - 58, accent);
        if (role.isScp()) {
            // SCPs are intentionally not given an item/card/ammo interface.
            renderScpStatusPanel(graphics, font, role, width - 190, height - 58, accent);
        } else {
            renderAccessCard(graphics, font, player, width / 2 - 70, height - 46, accent);
            renderWeaponPanel(graphics, font, player, width - 190, height - 58, accent);
        }
        renderCrosshair(graphics, width / 2, height / 2, accent);
        renderTargetIdentity(graphics, font, mc, width / 2, height / 2);
    }

    private static void renderVitalPanel(GuiGraphics graphics, Font font, Player player, Role role, int x, int y, int accent) {
        int panelHeight = role.isScp() ? 28 : 43;
        panel(graphics, x, y, 156, panelHeight, accent);
        float healthRatio = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
        bar(graphics, font, x + 8, y + 8, 140, healthRatio, accent,
                "HP " + Math.ceil(player.getHealth()) + " / " + Math.ceil(player.getMaxHealth()));
        if (!role.isScp()) {
            bar(graphics, font, x + 8, y + 25, 140, ClientRoundCache.getStamina() / 100.0F, accent,
                    "ВЫНОСЛИВОСТЬ " + Math.round(ClientRoundCache.getStamina()) + "%");
        }
    }

    private static void renderAccessCard(GuiGraphics graphics, Font font, Player player, int x, int y, int accent) {
        ItemStack card = findByPath(player, "keycard", "key_card", "access_card");
        panel(graphics, x, y, 140, 31, accent);
        graphics.drawString(font, "КАРТА  [CTRL]", x + 6, y + 5, bright(accent), false);
        if (!card.isEmpty()) {
            graphics.renderItem(card, x + 6, y + 14);
            graphics.drawString(font, shorten(card.getHoverName().getString(), 16), x + 27, y + 18, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, "НЕТ КАРТЫ", x + 6, y + 18, MUTED_TEXT, false);
        }
    }

    private static void renderWeaponPanel(GuiGraphics graphics, Font font, Player player, int x, int y, int accent) {
        panel(graphics, x, y, 172, 43, accent);
        ItemStack held = player.getMainHandItem();
        graphics.drawString(font, "СНАРЯЖЕНИЕ", x + 7, y + 5, bright(accent), false);
        if (!held.isEmpty()) {
            graphics.renderItem(held, x + 7, y + 17);
            graphics.drawString(font, shorten(held.getHoverName().getString(), 18), x + 28, y + 20, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, "БЕЗ СНАРЯЖЕНИЯ", x + 7, y + 20, MUTED_TEXT, false);
        }
        ItemStack ammo = player.getInventory().getItem(SlcLoadoutSavedData.AMMO_SLOT);
        if (!ammo.isEmpty()) {
            graphics.renderItem(ammo, x + 7, y + 29);
            graphics.drawString(font, "AMMO x" + ammo.getCount(), x + 28, y + 33, accent, false);
        }
    }

    private static void renderScpStatusPanel(GuiGraphics graphics, Font font, Role role, int x, int y, int accent) {
        panel(graphics, x, y, 172, 43, accent);
        graphics.drawString(font, "ОБЪЕКТ SCP", x + 7, y + 6, bright(accent), false);
        graphics.drawString(font, shorten(role.getDisplayName(), 23), x + 7, y + 19, 0xFFFFFFFF, false);
        graphics.drawString(font, "СНАРЯЖЕНИЕ НЕДОСТУПНО", x + 7, y + 31, dim(accent, 0.72F), false);
    }

    /** Shows exact nickname and role only for a real SLC participant currently under the crosshair. */
    private static void renderTargetIdentity(GuiGraphics graphics, Font font, Minecraft mc, int x, int y) {
        if (!(mc.crosshairPickEntity instanceof Player target) || !target.isAlive()) return;
        SpectatorTargetInfo info = ClientRoundCache.findPlayer(target.getUUID());
        if (info == null || info.role() == null || info.role() == Role.SPECTATOR) return;

        int targetAccent = teamColor(info.role());
        String name = shorten(info.name(), 24);
        String role = info.role().getDisplayName();
        int contentWidth = Math.max(font.width(name), font.width(role)) + 12;
        int top = y + 24;
        graphics.fill(x - contentWidth / 2, top - 2, x + contentWidth / 2, top + 21, PANEL_TARGET);
        graphics.fill(x - contentWidth / 2, top - 2, x + contentWidth / 2, top - 1, targetAccent);
        graphics.drawCenteredString(font, name, x, top + 2, targetAccent);
        graphics.drawCenteredString(font, role, x, top + 12, dim(targetAccent, 0.75F));
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

    private static void renderCrosshair(GuiGraphics graphics, int x, int y, int accent) {
        graphics.fill(x - 7, y, x - 2, y + 1, accent);
        graphics.fill(x + 3, y, x + 8, y + 1, accent);
        graphics.fill(x, y - 7, x + 1, y - 2, accent);
        graphics.fill(x, y + 3, x + 1, y + 8, accent);
    }

    private static int teamColor(Role role) {
        return role == null ? 0xFFFFFFFF : role.getTeam().getColorRgb();
    }

    private static int dim(int color, float strength) {
        int r = (int) (((color >> 16) & 0xFF) * strength);
        int g = (int) (((color >> 8) & 0xFF) * strength);
        int b = (int) ((color & 0xFF) * strength);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int bright(int color) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * 1.18F + 12));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * 1.18F + 12));
        int b = Math.min(255, (int) ((color & 0xFF) * 1.18F + 12));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static String shorten(String text, int maximum) {
        return text.length() <= maximum ? text : text.substring(0, Math.max(1, maximum - 1)) + "…";
    }
}
