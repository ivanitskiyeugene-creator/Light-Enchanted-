package dev.snowscpied.item;

import dev.snowscpied.client.render.UniversalSnowRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * Right-click ANY custom block or 3D mod model (industrial fan, light truss, keypads, machinery)
 * on any face (UP, NORTH, SOUTH, WEST, EAST, DOWN) to apply or remove a physical 3D snow mantle!
 * Shift + Right-click toggles snow across all 6 faces of the block at once.
 */
public class SnowSprayerItem extends Item {
    public SnowSprayerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        Player player = context.getPlayer();

        if (level.isClientSide) {
            if (player != null && player.isShiftKeyDown()) {
                // Shift + Right Click: Toggle snow on all 6 faces
                UniversalSnowRenderer.toggleAllFaces(pos);
            } else {
                // Right Click: Toggle snow on the specific clicked face
                UniversalSnowRenderer.toggleSnowFace(pos, face);
            }

            // Spawn dynamic snowflake particles across the clicked face
            Vec3 hit = context.getClickLocation();
            for (int i = 0; i < 14; i++) {
                double rx = hit.x + (Math.random() - 0.5) * 0.35;
                double ry = hit.y + (Math.random() - 0.5) * 0.35;
                double rz = hit.z + (Math.random() - 0.5) * 0.35;
                level.addParticle(ParticleTypes.SNOWFLAKE, rx, ry, rz,
                        face.getStepX() * 0.03 + (Math.random() - 0.5) * 0.02,
                        face.getStepY() * 0.03 + (Math.random() - 0.5) * 0.02,
                        face.getStepZ() * 0.03 + (Math.random() - 0.5) * 0.02);
            }
        }

        level.playSound(player, pos, SoundEvents.SNOW_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.snow_scpied.snow_sprayer.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.snow_scpied.snow_sprayer.desc2").withStyle(ChatFormatting.DARK_AQUA));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
