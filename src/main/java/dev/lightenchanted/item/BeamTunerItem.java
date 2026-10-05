package dev.lightenchanted.item;

import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Beam Tuner.
 * - Right-click an emitter to link it.
 * - Shift + Right-click an emitter to clear its target.
 * - Right-click any block or click into the air to set the beam's target point.
 * - Shift + Right-click air to clear the tuner's link.
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

        // Step 1: click an emitter -> bind it (or clear target if shift-clicking)
        if (level.getBlockEntity(clicked) instanceof LightEmitterBlockEntity emitter) {
            if (player != null && player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    BeamConfig cfg = new BeamConfig(emitter.getConfig());
                    cfg.hasTarget = false;
                    emitter.applyConfig(cfg);
                    player.displayClientMessage(Component.translatable(
                            "message.lightenchanted.target_cleared"), false);
                    level.playSound(null, clicked, SoundEvents.BEACON_DEACTIVATE,
                            SoundSource.BLOCKS, 0.7f, 1.2f);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            stack.getOrCreateTag().putLong(TAG_EMITTER, clicked.asLong());
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "message.lightenchanted.tuner_linked",
                        clicked.getX(), clicked.getY(), clicked.getZ()), false);
                level.playSound(null, clicked, SoundEvents.BEACON_ACTIVATE,
                        SoundSource.BLOCKS, 0.8f, 1.5f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Step 2: click any block -> that point becomes the beam target
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_EMITTER)) {
            BlockPos emitterPos = BlockPos.of(tag.getLong(TAG_EMITTER));
            if (level.getBlockEntity(emitterPos) instanceof LightEmitterBlockEntity emitter) {
                Vec3 target = ctx.getClickLocation();
                if (emitterPos.distToCenterSqr(target.x, target.y, target.z) > (double) MAX_RANGE * MAX_RANGE) {
                    if (player != null && !level.isClientSide) {
                        player.displayClientMessage(Component.translatable(
                                "message.lightenchanted.tuner_too_far", MAX_RANGE), false);
                    }
                } else {
                    if (!level.isClientSide) {
                        BeamConfig cfg = new BeamConfig(emitter.getConfig());
                        cfg.hasTarget = true;
                        cfg.targetX = target.x;
                        cfg.targetY = target.y;
                        cfg.targetZ = target.z;
                        emitter.applyConfig(cfg);
                        if (player != null) {
                            player.displayClientMessage(Component.translatable(
                                    "message.lightenchanted.tuner_target_set"), false);
                            level.playSound(null, clicked, SoundEvents.BEACON_POWER_SELECT,
                                    SoundSource.BLOCKS, 0.8f, 1.4f);
                        }
                    }
                }
            } else if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable(
                        "message.lightenchanted.tuner_missing"), false);
                stack.removeTagKey(TAG_EMITTER);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag tag = stack.getTag();

        if (tag != null && tag.contains(TAG_EMITTER)) {
            // Shift + right click air: clear tuner binding
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    stack.removeTagKey(TAG_EMITTER);
                    player.displayClientMessage(Component.translatable(
                            "message.lightenchanted.tuner_cleared"), false);
                    level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                            SoundSource.PLAYERS, 0.6f, 1.0f);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }

            // Raycast what player is looking at in the distance
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0f);
            Vec3 reach = eye.add(look.scale(MAX_RANGE));
            BlockHitResult hit = level.clip(new ClipContext(
                    eye, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            Vec3 target = (hit.getType() != HitResult.Type.MISS) ? hit.getLocation() : reach;

            BlockPos emitterPos = BlockPos.of(tag.getLong(TAG_EMITTER));
            if (level.getBlockEntity(emitterPos) instanceof LightEmitterBlockEntity emitter) {
                if (emitterPos.distToCenterSqr(target.x, target.y, target.z) > (double) MAX_RANGE * MAX_RANGE) {
                    if (!level.isClientSide) {
                        player.displayClientMessage(Component.translatable(
                                "message.lightenchanted.tuner_too_far", MAX_RANGE), false);
                    }
                } else {
                    if (!level.isClientSide) {
                        BeamConfig cfg = new BeamConfig(emitter.getConfig());
                        cfg.hasTarget = true;
                        cfg.targetX = target.x;
                        cfg.targetY = target.y;
                        cfg.targetZ = target.z;
                        emitter.applyConfig(cfg);
                        player.displayClientMessage(Component.translatable(
                                "message.lightenchanted.tuner_target_set"), false);
                        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                                SoundSource.PLAYERS, 0.8f, 1.4f);
                    }
                }
            } else if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "message.lightenchanted.tuner_missing"), false);
                stack.removeTagKey(TAG_EMITTER);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_EMITTER);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_EMITTER)) {
            BlockPos pos = BlockPos.of(tag.getLong(TAG_EMITTER));
            tooltip.add(Component.translatable("message.lightenchanted.tuner_linked",
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("item.lightenchanted.beam_tuner.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
