package dev.snowscpied.snow;

import dev.snowscpied.SnowSCPied;
import net.minecraft.resources.ResourceLocation;

public enum FootprintType {
    BOOTS("textures/entity/snow/footprint_boots.png", 0.35f, 0.45f),
    BARE_FEET("textures/entity/snow/footprint_boots.png", 0.30f, 0.40f),
    SCP_CLAWS("textures/entity/snow/footprint_claws.png", 0.45f, 0.45f),
    HOOVES("textures/entity/snow/footprint_boots.png", 0.28f, 0.28f);

    private final ResourceLocation texture;
    private final float width;
    private final float height;

    FootprintType(String texturePath, float width, float height) {
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
