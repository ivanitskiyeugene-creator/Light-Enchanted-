package dev.lightenchanted.block;

import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Flush-mounted architectural ceiling spot downlight.
 */
public class RecessedDownlightBlock extends LightEmitterBlock {
    public RecessedDownlightBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LightEmitterBlockEntity emitter) {
            // Configure default preset for recessed downlight: warm white, downward cone
            BeamConfig cfg = emitter.getConfig();
            cfg.down = true;
            cfg.color = 0xFFF5E6; // Warm white
            cfg.width = 0.35f;
            cfg.endWidth = 1.4f;
            cfg.height = 12;
            cfg.alpha = 210;
            cfg.glow = 0.8f;
            emitter.applyConfig(cfg);
        }
    }
}
