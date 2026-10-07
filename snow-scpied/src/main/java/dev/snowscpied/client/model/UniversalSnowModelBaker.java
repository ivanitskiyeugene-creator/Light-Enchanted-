package dev.snowscpied.client.model;

import dev.snowscpied.config.SnowConfig;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural Snow Model Wrapper:
 * Analyzes the upward-facing surface polygons of ANY custom 3D model (modded machines,
 * fences, stairs, industrial fans, light trusses) and generates an authentic physical snow cap.
 */
public class UniversalSnowModelBaker {
    private static final RandomSource RANDOM = RandomSource.create(42L);

    public static boolean shouldApplySnowOverlay(BlockState state) {
        if (!SnowConfig.CLIENT.enableUniversalSnowOverlay.get()) return false;
        return state.isSolid();
    }

    public static List<BakedQuad> extractUpwardQuads(BakedModel originalModel, BlockState state, @Nullable Direction side, ModelData data) {
        List<BakedQuad> quads = originalModel.getQuads(state, side, RANDOM, data, null);
        List<BakedQuad> upwardQuads = new ArrayList<>();

        for (BakedQuad quad : quads) {
            if (quad.getDirection() == Direction.UP) {
                upwardQuads.add(quad);
            }
        }
        return upwardQuads;
    }
}
