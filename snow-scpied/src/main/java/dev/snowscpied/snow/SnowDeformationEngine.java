package dev.snowscpied.snow;

import dev.snowscpied.config.SnowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Physical Snow Deformation Engine:
 * - Real-time physical snow compression and layer reduction under heavy steps and impact.
 * - Dynamic crater formation on landing from falls.
 * - Snow particle bursts and crunching sound effects.
 */
public class SnowDeformationEngine {
    private static final Map<BlockPos, Long> DEFORMED_BLOCKS = new HashMap<>();

    public static void handleEntityStep(Entity entity) {
        if (!SnowConfig.CLIENT.enableDeformation.get()) return;

        Level level = entity.level();
        BlockPos feetPos = entity.blockPosition();
        BlockPos belowPos = feetPos.below();

        BlockState feetState = level.getBlockState(feetPos);
        BlockState belowState = level.getBlockState(belowPos);

        BlockPos targetPos = feetState.is(Blocks.SNOW) ? feetPos : (belowState.is(Blocks.SNOW) ? belowPos : null);

        if (targetPos != null) {
            BlockState snowState = level.getBlockState(targetPos);
            int currentLayers = snowState.getValue(SnowLayerBlock.LAYERS);

            float fallDist = entity.fallDistance;
            int layersToCompress = 0;

            if (fallDist > 2.5f) {
                // Hard fall impact: crush 2-3 layers!
                layersToCompress = Math.min(currentLayers - 1, (int) (fallDist * 0.8f));
                if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                            targetPos.getX() + 0.5, targetPos.getY() + (currentLayers * 0.125), targetPos.getZ() + 0.5,
                            16, 0.3, 0.1, 0.3, 0.08);
                    serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                            targetPos.getX() + 0.5, targetPos.getY() + (currentLayers * 0.125), targetPos.getZ() + 0.5,
                            8, 0.2, 0.1, 0.2, 0.05);
                }
                level.playSound(null, targetPos, SoundEvents.SNOW_FALL, SoundSource.BLOCKS, 1.2f, 0.8f);
            } else if (currentLayers > 2 && entity.tickCount % 4 == 0) {
                // Regular walking step: compress deep snow by 1 layer under feet
                layersToCompress = 1;
                level.playSound(null, targetPos, SoundEvents.SNOW_STEP, SoundSource.BLOCKS, 0.6f, 0.9f);
            }

            if (layersToCompress > 0) {
                int newLayers = Math.max(1, currentLayers - layersToCompress);
                if (newLayers != currentLayers) {
                    level.setBlock(targetPos, snowState.setValue(SnowLayerBlock.LAYERS, newLayers), 3);
                    DEFORMED_BLOCKS.put(targetPos.immutable(), level.getGameTime());
                }
            }
        }
    }

    public static double getAccurateSurfaceY(Level level, BlockPos feetPos) {
        BlockState feetState = level.getBlockState(feetPos);
        if (feetState.is(Blocks.SNOW)) {
            int layers = feetState.getValue(SnowLayerBlock.LAYERS);
            return feetPos.getY() + (layers * 0.125);
        }

        BlockPos belowPos = feetPos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (belowState.is(Blocks.SNOW)) {
            int layers = belowState.getValue(SnowLayerBlock.LAYERS);
            return belowPos.getY() + (layers * 0.125);
        }

        if (belowState.is(Blocks.SNOW_BLOCK) || belowState.is(Blocks.POWDER_SNOW)) {
            return belowPos.getY() + 1.0;
        }

        return belowPos.getY() + (belowState.getShape(level, belowPos).isEmpty() ? 1.0 : belowState.getShape(level, belowPos).max(net.minecraft.core.Direction.Axis.Y));
    }
}
