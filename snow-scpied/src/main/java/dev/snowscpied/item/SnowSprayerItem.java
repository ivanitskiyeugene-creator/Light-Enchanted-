package dev.snowscpied.item;

import dev.snowscpied.client.render.UniversalSnowRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Snow Sprayer Tool:
 * Right-click ANY custom block or 3D mod model (industrial fan, light truss, fences, machinery)
 * to instantly apply or remove a physical 3D snow mantle on its upward surfaces!
 */
public class SnowSprayerItem extends Item {
    public SnowSprayerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (level.isClientSide) {
            UniversalSnowRenderer.toggleSnowOnBlock(pos);
            for (int i = 0; i < 12; i++) {
                double rx = pos.getX() + Math.random();
                double ry = pos.getY() + 1.05 + Math.random() * 0.2;
                double rz = pos.getZ() + Math.random();
                level.addParticle(ParticleTypes.SNOWFLAKE, rx, ry, rz, 0, 0.02, 0);
            }
        }

        level.playSound(context.getPlayer(), pos, SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
