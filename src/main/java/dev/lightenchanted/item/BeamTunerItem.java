package dev.lightenchanted.item;

import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
 * Beam Tuner. Right-click an emitter to bind it, then right-click anywhere
 * else to place the beam's target point at the clicked location.
 */
public class BeamTunerItem extends Item {
    public static final String TAG_EMITTER = "BoundEmitter";
    public static final int MAX_RANGE = 128;

    public BeamTunerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        BlockPos clicked = ctx.getClickedPos();

        // Step 1: click an emitter -> bind it to the tuner
        if (level.getBlockEntity(clicked) instanceof LightEmitterBlockEntity) {
            if (!level.isClientSide && player != null) {
                stack.getOrCreateTag().putLong(TAG_EMITTER, clicked.asLong());
                player.displayClientMessage(Component.translatable(
                        "message.lightenchanted.tuner_linked",
                        clicked.getX(), clicked.getY(), clicked.getZ()), true);
                level.playSound(null, clicked, SoundEvents.UI_BUTTON_CLICK.get(),
                        SoundSource.BLOCKS, 0.7f, 1.6f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Step 2: click anywhere -> that precise point becomes the beam target
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_EMITTER)) {
            if (!level.isClientSide && player != null) {
                BlockPos emitterPos = BlockPos.of(tag.getLong(TAG_EMITTER));
                if (level.getBlockEntity(emitterPos) instanceof LightEmitterBlockEntity emitter) {
                    if (emitterPos.distSqr(clicked) > (long) MAX_RANGE * MAX_RANGE) {
                        player.displayClientMessage(Component.translatable(
                                "message.lightenchanted.tuner_too_far", MAX_RANGE), true);
                    } else {
                        Vec3 target = ctx.getClickLocation();
                        BeamConfig cfg = new BeamConfig(emitter.getConfig());
                        cfg.hasTarget = true;
                        cfg.targetX = target.x;
                        cfg.targetY = target.y;
                        cfg.targetZ = target.z;
                        emitter.applyConfig(cfg);
                        player.displayClientMessage(Component.translatable(
                                "message.lightenchanted.tuner_target_set"), true);
                        level.playSound(null, clicked, SoundEvents.UI_BUTTON_CLICK.get(),
                                SoundSource.BLOCKS, 0.7f, 1.2f);
                    }
                } else {
                    player.displayClientMessage(Component.translatable(
                            "message.lightenchanted.tuner_missing"), true);
                    stack.removeTagKey(TAG_EMITTER);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_EMITTER);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lightenchanted.beam_tuner.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
