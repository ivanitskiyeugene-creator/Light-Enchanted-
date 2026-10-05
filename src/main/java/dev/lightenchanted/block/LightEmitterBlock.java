package dev.lightenchanted.block;

import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import dev.lightenchanted.client.ClientHooks;
import dev.lightenchanted.item.BeamTunerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * The emitter block. Right click opens the beam editor GUI,
 * Shift+right click toggles the beam without opening the GUI.
 */
public class LightEmitterBlock extends BaseEntityBlock {
    public LightEmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LightEmitterBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LightEmitterBlockEntity emitter)) {
            return InteractionResult.PASS;
        }

        // With a tuner in hand, the item must get the interaction first
        // (binding / target setting). Otherwise the GUI would swallow the click.
        if (player.getItemInHand(hand).getItem() instanceof BeamTunerItem) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            // Quick toggle, done authoritatively on the server.
            if (!level.isClientSide) {
                BeamConfig cfg = new BeamConfig(emitter.getConfig());
                cfg.enabled = !cfg.enabled;
                emitter.applyConfig(cfg);
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.BLOCKS,
                        0.6f, cfg.enabled ? 1.4f : 0.7f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (hand == InteractionHand.MAIN_HAND && level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openEmitterScreen(emitter));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
