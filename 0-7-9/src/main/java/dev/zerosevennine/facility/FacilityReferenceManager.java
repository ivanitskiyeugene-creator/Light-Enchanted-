package dev.zerosevennine.facility;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class FacilityReferenceManager {
    private static final String REFERENCE_FOLDER = "079_references";
    private static ResourceLocation activeTextureLocation = null;
    private static DynamicTexture activeTexture = null;
    private static BufferedImage activeBufferedImage = null;
    private static String activeFileName = "none";

    private static boolean visible = false;
    private static float alpha = 0.45f;
    private static float scale = 1.0f;
    private static int offsetX = 0;
    private static int offsetY = 0;

    public static File getReferencesDirectory() {
        File dir = new File(Minecraft.getInstance().gameDirectory, REFERENCE_FOLDER);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static List<File> listReferenceFiles() {
        File dir = getReferencesDirectory();
        List<File> list = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        });
        if (files != null) {
            for (File f : files) list.add(f);
        }
        return list;
    }

    public static boolean loadReferenceImage(File file) {
        if (file == null || !file.exists()) return false;
        try (InputStream in = new FileInputStream(file)) {
            BufferedImage bImg = ImageIO.read(file);
            if (bImg == null) return false;

            NativeImage nativeImage = NativeImage.read(new FileInputStream(file));
            if (activeTexture != null) {
                activeTexture.close();
            }

            activeTexture = new DynamicTexture(nativeImage);
            activeTextureLocation = Minecraft.getInstance().getTextureManager().register("079_ref_" + System.currentTimeMillis(), activeTexture);
            activeBufferedImage = bImg;
            activeFileName = file.getName();
            visible = true;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean loadFirstAvailable() {
        List<File> list = listReferenceFiles();
        if (!list.isEmpty()) {
            return loadReferenceImage(list.get(0));
        }
        return false;
    }

    public static ResourceLocation getActiveTextureLocation() {
        return activeTextureLocation;
    }

    public static BufferedImage getActiveBufferedImage() {
        return activeBufferedImage;
    }

    public static String getActiveFileName() {
        return activeFileName;
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void setVisible(boolean v) {
        visible = v;
    }

    public static void toggleVisible() {
        visible = !visible;
    }

    public static float getAlpha() {
        return alpha;
    }

    public static void cycleAlpha() {
        if (alpha <= 0.25f) alpha = 0.5f;
        else if (alpha <= 0.55f) alpha = 0.8f;
        else if (alpha <= 0.85f) alpha = 1.0f;
        else alpha = 0.25f;
    }

    public static float getScale() {
        return scale;
    }

    public static void setScale(float s) {
        scale = Math.max(0.2f, Math.min(5.0f, s));
    }

    public static int getOffsetX() {
        return offsetX;
    }

    public static void setOffsetX(int ox) {
        offsetX = ox;
    }

    public static int getOffsetY() {
        return offsetY;
    }

    public static void setOffsetY(int oy) {
        offsetY = oy;
    }
}
