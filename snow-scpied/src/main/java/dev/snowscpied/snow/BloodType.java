package dev.snowscpied.snow;

import dev.snowscpied.SnowSCPied;
import net.minecraft.resources.ResourceLocation;

public enum BloodType {
    HUMAN("textures/entity/snow/blood_human.png", 0.5f, 0.5f),
    ACID("textures/entity/snow/blood_acid.png", 0.55f, 0.55f),
    ANOMALOUS("textures/entity/snow/blood_anomalous.png", 0.6f, 0.6f),
    ENDER("textures/entity/snow/blood_ender.png", 0.5f, 0.5f);

    private final ResourceLocation texture;
    private final float width;
    private final float height;

    BloodType(String texturePath, float width, float height) {
        this.texture = new ResourceLocation(SnowSCPied.MOD_ID, texturePath);
        this.width = width;
        this.height = height;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }
}
