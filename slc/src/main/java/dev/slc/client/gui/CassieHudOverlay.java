package dev.slc.client.gui;

import dev.slc.client.ClientRoundCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * C.A.S.S.I.E. text feed — canon style:
 * positioned on the LEFT side slightly below the top, pure clean text with no
 * decorative frames or watermarks, words appear one by one synchronized with the audio.
 */
public class CassieHudOverlay implements IGuiOverlay {
    public static final CassieHudOverlay INSTANCE = new CassieHudOverlay();

    private static final int WORD_MS = 320;          // per-word cadence
    private static final int PUNCT_EXTRA_MS = 260;   // extra pause after , . ; :
    private static final int HOLD_AFTER_MS = 4000;   // linger once fully revealed
    private static final int FADE_OUT_MS = 600;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientRoundCache.hasCassieMessage()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        String text = ClientRoundCache.getCassieText();
        long elapsed = System.currentTimeMillis() - ClientRoundCache.getCassieStartMillis();

        // --- Word-by-word reveal synced to the voice cadence ---
        String[] words = text.split(" ");
        List<long[]> wordCues = new ArrayList<>();   // [startMs, endMs] per word
        long t = 0;
        for (String word : words) {
            long start = t;
            t += WORD_MS;
            if (word.endsWith(",") || word.endsWith(".") || word.endsWith(";") || word.endsWith(":")) {
                t += PUNCT_EXTRA_MS;
            }
            wordCues.add(new long[]{start, t});
        }
        long totalRevealMs = t;

        if (elapsed > totalRevealMs + HOLD_AFTER_MS + FADE_OUT_MS) {
            return; // expired; cache cleared only by next push
        }

        StringBuilder visible = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (elapsed >= wordCues.get(i)[0]) {
                visible.append(words[i]);
                if (i < words.length - 1) visible.append(' ');
            } else {
                break;
            }
        }

        // Fade in/out alpha
        int alpha = 255;
        if (elapsed < 300) {
            alpha = (int) (255 * (elapsed / 300.0));
        } else if (elapsed > totalRevealMs + HOLD_AFTER_MS) {
            long fadeStart = elapsed - (totalRevealMs + HOLD_AFTER_MS);
            alpha = (int) (255 * (1.0 - Math.min(1.0, fadeStart / (double) FADE_OUT_MS)));
        }
        if (alpha <= 4) return;

        int baseColor = ClientRoundCache.getCassieColor() & 0xFFFFFF;
        int color = (alpha << 24) | baseColor;

        Font font = mc.font;
        int x = 12;
        int y = (int) (screenHeight * 0.18); // left side, slightly below the top

        // Projected layout: word-wrap at ~38% of the screen width
        List<String> lines = wrapWords(font, visible.toString(), (int) (screenWidth * 0.38));
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * (font.lineHeight + 2), color, true);
        }
    }

    private static List<String> wrapWords(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
