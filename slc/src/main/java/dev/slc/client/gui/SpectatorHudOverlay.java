package dev.slc.client.gui;

import dev.slc.client.ClientRoundCache;
import dev.slc.spectator.SpectatorTargetInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

public class SpectatorHudOverlay implements IGuiOverlay {
    public static final SpectatorHudOverlay INSTANCE = new SpectatorHudOverlay();

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Strict requirement: Only active if player is in Spectator mode AND Round is in progress
        if (!mc.player.isSpectator() || !ClientRoundCache.isRoundActive()) return;

        Font font = mc.font;

        // -------------------------------------------------------------
        // 1. TOP BAR: Dual Spawn Tokens / Influence Balance Bar
        // -------------------------------------------------------------
        int topBarWidth = 260;
        int topBarHeight = 12;
        int topBarX = (screenWidth - topBarWidth) / 2;
        int topBarY = 8;

        // Background
        graphics.fill(topBarX - 2, topBarY - 2, topBarX + topBarWidth + 2, topBarY + topBarHeight + 2, 0xCC0A0D12);

        float mtfRatio = ClientRoundCache.getMtfInfluence();
        float chaosRatio = ClientRoundCache.getChaosInfluence();
        float total = mtfRatio + chaosRatio;
        if (total <= 0) total = 1.0f;
        int mtfFillW = (int) ((mtfRatio / total) * topBarWidth);

        // MTF side (Blue)
        graphics.fill(topBarX, topBarY, topBarX + mtfFillW, topBarY + topBarHeight, 0xFF2980B9);
        // Chaos side (Green)
        graphics.fill(topBarX + mtfFillW, topBarY, topBarX + topBarWidth, topBarY + topBarHeight, 0xFF27AE60);

        // Labels
        graphics.drawString(font, "§9МОГ §f" + (int)(mtfRatio * 100) + "%", topBarX + 4, topBarY + 2, 0xFFFFFF, false);
        String chaosStr = (int)(chaosRatio * 100) + "% §aХАОС";
        graphics.drawString(font, chaosStr, topBarX + topBarWidth - font.width(chaosStr) - 4, topBarY + 2, 0xFFFFFF, false);

        // -------------------------------------------------------------
        // 2. LEFT PANEL: Vertical Alive Player List
        // -------------------------------------------------------------
        List<SpectatorTargetInfo> players = ClientRoundCache.getAlivePlayers();
        int leftPanelX = 10;
        int leftPanelY = 32;
        int itemHeight = 22;

        graphics.drawString(font, "§6§lЖИВЫЕ ИГРОКИ (" + players.size() + "):", leftPanelX, leftPanelY - 12, 0xF39C12, true);

        int currentIndex = ClientRoundCache.getSpectateIndex();

        for (int i = 0; i < Math.min(10, players.size()); i++) {
            SpectatorTargetInfo target = players.get(i);
            boolean isSpectatingThis = (i == currentIndex && !ClientRoundCache.isFreecamMode());

            int cardY = leftPanelY + i * (itemHeight + 2);
            int cardW = 150;

            // Card background
            int bgColor = isSpectatingThis ? 0xEE1A2536 : 0xAA0E131C;
            graphics.fill(leftPanelX, cardY, leftPanelX + cardW, cardY + itemHeight, bgColor);

            if (isSpectatingThis) {
                // Cyan border highlight
                graphics.fill(leftPanelX - 2, cardY, leftPanelX, cardY + itemHeight, 0xFF58D3F7);
            }

            // Role color badge and nickname
            int roleColor = target.role().getColorRgb();
            String namePrefix = isSpectatingThis ? "§b► " : "  ";
            graphics.drawString(font, namePrefix + "§f" + target.name(), leftPanelX + 4, cardY + 2, 0xFFFFFF, false);

            // Role subtitle
            graphics.drawString(font, target.role().getDisplayName(), leftPanelX + cardW - font.width(target.role().getDisplayName()) - 4, cardY + 2, roleColor, false);

            // HP Bar
            int barY = cardY + 13;
            int barW = cardW - 8;
            graphics.fill(leftPanelX + 4, barY, leftPanelX + 4 + barW, barY + 5, 0xFF2C3E50);

            float hpRatio = Math.max(0f, Math.min(1f, target.health() / Math.max(1f, target.maxHealth())));
            int hpFillW = (int) (barW * hpRatio);
            graphics.fill(leftPanelX + 4, barY, leftPanelX + 4 + hpFillW, barY + 5, 0xFF2ECC71);

            // Hume Shield Bar (if any)
            if (target.maxHumeShield() > 0) {
                float hsRatio = Math.max(0f, Math.min(1f, target.humeShield() / target.maxHumeShield()));
                int hsFillW = (int) (barW * hsRatio);
                graphics.fill(leftPanelX + 4, barY + 5, leftPanelX + 4 + hsFillW, barY + 7, 0xFF00D2D3);
            }
        }

        // -------------------------------------------------------------
        // 3. BOTTOM-RIGHT PANEL: Audio Controls & Keybind Hints
        // -------------------------------------------------------------
        int rightPanelW = 200;
        int rightPanelX = screenWidth - rightPanelW - 10;
        int rightPanelY = screenHeight - 65;

        graphics.fill(rightPanelX - 4, rightPanelY - 4, rightPanelX + rightPanelW + 4, screenHeight - 6, 0xCC0A0D12);

        String muteDead = ClientRoundCache.isMuteDeadChat() ? "§c[ВКЛ]" : "§7[ВЫКЛ]";
        String muteTarget = ClientRoundCache.isMuteSpectatedPlayer() ? "§c[ВКЛ]" : "§7[ВЫКЛ]";

        graphics.drawString(font, "§7Заглушить мертвых: " + muteDead, rightPanelX, rightPanelY, 0xAAAAAA, false);
        graphics.drawString(font, "§7Заглушить наблюдаемого: " + muteTarget, rightPanelX, rightPanelY + 11, 0xAAAAAA, false);

        String camMode = ClientRoundCache.isFreecamMode() ? "§e[Свободная камера]" : "§b[Фиксация от 1-го лица]";
        graphics.drawString(font, "Режим: " + camMode, rightPanelX, rightPanelY + 24, 0xFFFFFF, false);
        graphics.drawString(font, "§8[ЛКМ/ПКМ] Игрок | [Пробел] Камера | [Tab] Мем", rightPanelX, rightPanelY + 36, 0x888888, false);
    }
}
