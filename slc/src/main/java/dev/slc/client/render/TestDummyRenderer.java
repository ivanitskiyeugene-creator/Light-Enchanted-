package dev.slc.client.render;

import dev.slc.entity.TestDummyEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Reuses Minecraft's neutral humanoid visual: test dummies need no invented facility texture. */
public final class TestDummyRenderer extends HumanoidMobRenderer<TestDummyEntity, HumanoidModel<TestDummyEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/entity/zombie/zombie.png");

    public TestDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), .35F);
    }

    @Override
    public ResourceLocation getTextureLocation(TestDummyEntity entity) {
        return TEXTURE;
    }
}
