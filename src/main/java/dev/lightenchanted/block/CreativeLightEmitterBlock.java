package dev.lightenchanted.block;

import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Invisible, collision-free emitter variant for creative builds and maps.
 * The beam's origin can additionally be shifted with per-axis offsets
 * (see BeamConfig#offsetX/Y/Z), so the light can start "from a lamp",
 * "from behind a wall", etc. while the block itself is hidden anywhere.
 */
public class CreativeLightEmitterBlock extends LightEmitterBlock {
    public CreativeLightEmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
