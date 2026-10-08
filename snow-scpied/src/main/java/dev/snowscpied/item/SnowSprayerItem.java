package dev.snowscpied.item;

import dev.snowscpied.client.render.UniversalSnowRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Snow Sprayer Tool:
 * Right-click ANY custom block or 3D mod model (industrial fan, light truss, stairs, machinery)
 * to apply or remove a physical vanilla-thick 3D snow layer strictly on its topmost exposed surfaces!
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
            UniversalSnowRenderer.toggleSnow(pos);

            // Spawn dynamic snowflake particles across the top surface
            Vec3 hit = context.getClickLocation();
            for (int i = 0; i < 12; i++) {
                double rx = hit.x + (Math.random() - 0.5) * 0.4;
                double ry = hit.y + 0.1 + Math.random() * 0.2;
                double rz = hit.z + (Math.random() - 0.5) * 0.4;
                level.addParticle(ParticleTypes.SNOWFLAKE, rx, ry, rz,
                        (Math.random() - 0.5) * 0.02,
                        0.02 + Math.random() * 0.02,
                        (Math.random() - 0.5) * 0.02);
            }
        }

        level.playSound(context.getPlayer(), pos, SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.snow_scpied.snow_sprayer.desc").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
