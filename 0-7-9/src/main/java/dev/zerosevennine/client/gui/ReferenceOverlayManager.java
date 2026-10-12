package dev.zerosevennine.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ReferenceOverlayManager {
    private static File refDir = null;
    private static final List<File> availableImageFiles = new ArrayList<>();
    private static int selectedFileIndex = 0;

    private static ResourceLocation currentTextureLocation = null;
    private static DynamicTexture currentTexture = null;
    private static BufferedImage currentBufferedImage = null;
    private static int imageWidth = 0;
    private static int imageHeight = 0;

    public static boolean enabled = false;
    public static float alpha = 0.5f;
    public static float scale = 1.0f;
    public static int panX = 0;
    public static int panY = 0;

    public static File getReferencesDirectory() {
        if (refDir == null) {
            Minecraft mc = Minecraft.getInstance();
            File gameDir = mc.gameDirectory;
            refDir = new File(gameDir, "079_references");
            if (!refDir.exists()) {
                refDir.mkdirs();
                try {
                    File readme = new File(refDir, "README.txt");
                    if (!readme.exists()) {
                        FileWriter writer = new FileWriter(readme);
                        writer.write("Поместите сюда скриншоты карт зон (.png / .jpg) для использования в качестве подложки и автоматической сборки в Редакторе карты 079.\n");
                        writer.write("Place zone map screenshots (.png / .jpg) here for CAD reference overlays and automatic zone reconstruction in the 079 Map Tablet.\n");
                        writer.close();
                    }
                } catch (Exception ignored) {}
            }
        }
        return refDir;
    }

    public static void refreshFileList() {
        availableImageFiles.clear();
        File dir = getReferencesDirectory();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            });
            if (files != null) {
                for (File f : files) {
                    availableImageFiles.add(f);
                }
            }
        }

        if (availableImageFiles.isEmpty()) {
            unloadCurrentTexture();
        } else {
            if (selectedFileIndex >= availableImageFiles.size()) {
                selectedFileIndex = 0;
            }
            loadSelectedImage();
        }
    }

    public static List<File> getAvailableFiles() {
        return availableImageFiles;
    }

    public static int getSelectedFileIndex() {
        return selectedFileIndex;
    }

    public static void cycleNextFile() {
        if (availableImageFiles.isEmpty()) return;
        selectedFileIndex = (selectedFileIndex + 1) % availableImageFiles.size();
        loadSelectedImage();
    }

    public static void cyclePreviousFile() {
        if (availableImageFiles.isEmpty()) return;
        selectedFileIndex = (selectedFileIndex - 1 + availableImageFiles.size()) % availableImageFiles.size();
        loadSelectedImage();
    }

    public static String getActiveFileName() {
        if (availableImageFiles.isEmpty() || selectedFileIndex >= availableImageFiles.size()) {
            return "НЕТ ФАЙЛОВ";
        }
        return availableImageFiles.get(selectedFileIndex).getName();
    }

    public static void loadSelectedImage() {
        if (availableImageFiles.isEmpty() || selectedFileIndex >= availableImageFiles.size()) return;
        File file = availableImageFiles.get(selectedFileIndex);
        try (InputStream is = new FileInputStream(file)) {
            NativeImage nativeImage = NativeImage.read(is);
            imageWidth = nativeImage.getWidth();
            imageHeight = nativeImage.getHeight();

            Minecraft mc = Minecraft.getInstance();
            if (currentTexture != null) {
                currentTexture.close();
            }

            currentTexture = new DynamicTexture(nativeImage);
            currentTextureLocation = mc.getTextureManager().register("ref_overlay_" + System.currentTimeMillis(), currentTexture);
            currentBufferedImage = ImageIO.read(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void unloadCurrentTexture() {
        if (currentTexture != null) {
            currentTexture.close();
            currentTexture = null;
        }
        currentTextureLocation = null;
        currentBufferedImage = null;
        imageWidth = 0;
        imageHeight = 0;
    }

    public static ResourceLocation getTextureLocation() {
        return currentTextureLocation;
    }

    public static BufferedImage getBufferedImage() {
        return currentBufferedImage;
    }

    public static int getImageWidth() {
        return imageWidth;
    }

    public static int getImageHeight() {
        return imageHeight;
    }
}
