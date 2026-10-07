package dev.snowscpied.snow;

import dev.snowscpied.config.SnowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles real-time snow surface physical deformation and depression under entities.
 */
public class SnowDeformationEngine {
    private static final Map<BlockPos, Float> DEPRESSION_MAP = new HashMap<>();

    public static void registerEntityStep(Entity entity) {
        if (!SnowConfig.CLIENT.enableDeformation.get()) return;

        Level level = entity.level();
        BlockPos feetPos = entity.blockPosition();
        BlockPos belowPos = feetPos.below();

        BlockState belowState = level.getBlockState(belowPos);
        BlockState feetState = level.getBlockState(feetPos);

        boolean onSnow = feetState.is(Blocks.SNOW) || belowState.is(Blocks.SNOW) || belowState.is(Blocks.SNOW_BLOCK) || belowState.is(Blocks.POWDER_SNOW);

        if (onSnow) {
            float weight = (entity instanceof LivingEntity living) ? living.getBbWidth() * living.getBbHeight() : 1.0f;
            float fallDist = entity.fallDistance;
            float depressionAmount = Math.min(0.25f, 0.05f + (fallDist * 0.04f) + (weight * 0.02f));

            BlockPos targetPos = feetState.is(Blocks.SNOW) ? feetPos : belowPos;
            DEPRESSION_MAP.put(targetPos, Math.min(0.35f, DEPRESSION_MAP.getOrDefault(targetPos, 0.0f) + depressionAmount));
        }
    }

    public static float getDepression(BlockPos pos) {
        return DEPRESSION_MAP.getOrDefault(pos, 0.0f);
    }

    public static void clear() {
        DEPRESSION_MAP.clear();
    }
}
