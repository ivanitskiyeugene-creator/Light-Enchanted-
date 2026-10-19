package dev.slc.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.slc.client.ClientRoundCache;
import dev.slc.role.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class RoleIntroOverlay implements IGuiOverlay {
    public static final RoleIntroOverlay INSTANCE = new RoleIntroOverlay();

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int ticksRemaining = ClientRoundCache.getRoleIntroTicks();
        if (ticksRemaining <= 0) return;

        Role role = ClientRoundCache.getLocalRole();
        if (role == null || role == Role.SPECTATOR) return;

        Font font = mc.font;
        int cx = screenWidth / 2;
        int cy = screenHeight / 2 - 20;

        // Calculate opacity for smooth fade in / out
        float alpha = 1.0f;
        if (ticksRemaining > 125) {
            alpha = (140 - ticksRemaining) / 15.0f; // Fade in
        } else if (ticksRemaining < 20) {
            alpha = ticksRemaining / 20.0f; // Fade out
        }
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        int alphaBits = ((int) (alpha * 255.0f)) << 24;

        // 1. "ТЕПЕРЬ ВЫ" (Subheader)
        String subHeader = "ТЕПЕРЬ ВЫ";
        int subHeaderColor = 0xDDDDDD | alphaBits;
        graphics.drawCenteredString(font, subHeader, cx, cy - 35, subHeaderColor);

        // 2. [BIG BOLD ROLE TITLE] (Scaled 2.2x)
        String roleTitle = role.getDisplayName().toUpperCase();
        int roleRgb = (role.getTeam().getColorRgb() & 0x00FFFFFF) | alphaBits;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        float scale = 2.2f;
        pose.translate(cx, cy - 18, 0);
        pose.scale(scale, scale, 1.0f);
        int titleWidth = font.width(roleTitle);
        graphics.drawString(font, roleTitle, -titleWidth / 2, 0, roleRgb, false);
        pose.popPose();

        // 3. Three bullet objectives
        String[] objectives = getObjectivesForRole(role);
        int objY = cy + 24;
        for (int i = 0; i < objectives.length; i++) {
            int textColor = (role.isScp() && i == 2 ? 0xFF5555 : 0xFFFFFF) | alphaBits;
            graphics.drawCenteredString(font, objectives[i], cx, objY + (i * 12), textColor);
        }

        // 4. "Нажмите F1, чтобы узнать больше"
        String f1Text = "Нажмите F1, чтобы узнать больше";
        int f1Color = 0xFFFFFF | alphaBits;
        graphics.drawCenteredString(font, f1Text, cx, cy + 85, f1Color);
    }

    private String[] getObjectivesForRole(Role role) {
        if (role.isScp()) {
            return new String[] {
                    "Убейте всех людей.",
                    "Не дайте никому сбежать.",
                    "Объединитесь с другими SCP объектами."
            };
        }

        return switch (role) {
            case CLASS_D -> new String[] {
                    "Сбегите из подземного комплекса.",
                    "Сотрудничайте с Повстанцами Хаоса.",
                    "Избегайте SCP объектов и охраны МОГ."
            };
            case SCIENTIST -> new String[] {
                    "Сбегите из подземного комплекса.",
                    "Сотрудничайте с охраной Фонда.",
                    "Улучшайте ключ-карты в SCP-914."
            };
            case FACILITY_GUARD -> new String[] {
                    "Спасите ученых и эвакуируйте их на Поверхность.",
                    "Сдерживайте распространение SCP объектов.",
                    "Нейтрализуйте агрессивный класс D."
            };
            case MTF_PRIVATE, MTF_SERGEANT, MTF_SPECIALIST, MTF_CAPTAIN -> new String[] {
                    "Уничтожьте все враждебные SCP объекты.",
                    "Эвакуируйте оставшихся ученых.",
                    "Ликвидируйте силы Повстанцев Хаоса."
            };
            case CHAOS_CONSCRIPT, CHAOS_MARAUDER, CHAOS_REPRESSOR -> new String[] {
                    "Помогите сбежать персоналу класса D.",
                    "Уничтожьте силы охраны МОГ и ученых.",
                    "Захватите контроль над комплексом."
            };
            default -> new String[] {
                    "Выполняйте задачи вашей фракции.",
                    "Используйте доступное снаряжение.",
                    "Оставайтесь в живых."
            };
        };
    }
}
