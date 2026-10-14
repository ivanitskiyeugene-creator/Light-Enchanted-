package dev.slc.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.slc.SecretLabCubed;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class SpectatorMemeScreen extends Screen {
    private static final ResourceLocation MEME_TEXTURE =
            new ResourceLocation(SecretLabCubed.MOD_ID, "textures/gui/meme_for_tab.png");

    public SpectatorMemeScreen() {
        super(Component.literal("Weapon Customization // MEME"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int imgW = Math.min(380, width - 40);
        int imgH = (int) (imgW * (9.0 / 16.0));
        int x = (width - imgW) / 2;
        int y = (height - imgH) / 2;

        // Dark matte backdrop frame
        graphics.fill(x - 4, y - 24, x + imgW + 4, y + imgH + 24, 0xDD0A0D12);
        graphics.fill(x - 2, y - 22, x + imgW + 2, y - 2, 0xFF151A22);

        // Header
        graphics.drawString(this.font, "§b[WEAPON ATTACHMENT BENCH // ACCESS DENIED]", x + 6, y - 16, 0x58D3F7, false);
        graphics.drawString(this.font, "§7[ESC / TAB чтобы закрыть]", x + imgW - 130, y - 16, 0x8899AA, false);

        // Render Capybara Easter Egg Image
        RenderSystem.setShaderTexture(0, MEME_TEXTURE);
        graphics.blit(MEME_TEXTURE, x, y, 0, 0, imgW, imgH, imgW, imgH);

        // Footer hint
        graphics.drawCenteredString(this.font, "§eОружие недоступно в режиме наблюдения. Наслаждайтесь капибарой.", width / 2, y + imgH + 8, 0xF1C40F);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Tab (258) or Escape (256) closes the meme screen
        if (keyCode == 258 || keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
