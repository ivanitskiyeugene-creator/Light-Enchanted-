package dev.lightenchanted.blockentity;

import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.block.CreativeLightEmitterBlock;
import dev.lightenchanted.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores the beam configuration of one emitter block and manages real
 * Minecraft block lighting at the beam's impact / target point.
 *
 * Syncs to tracking clients through the vanilla block-entity update packet.
 */
public class LightEmitterBlockEntity extends BlockEntity {
    private final BeamConfig config = new BeamConfig();
    private BlockPos activeLightPos = null;
    private int tickCount = 0;

    public LightEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIGHT_EMITTER.get(), pos, state);
    }

    public BeamConfig getConfig() {
        return config;
    }

    /** True for the invisible creative-only variant (enables offset editing). */
    public boolean isCreative() {
        return getBlockState().getBlock() instanceof CreativeLightEmitterBlock;
    }

    /**
     * Authoritative config replacement (call on the server or locally on client).
     * Saves, broadcasts updates to tracking clients, and immediately updates impact lighting.
     */
    public void applyConfig(BeamConfig newConfig) {
        config.copyFrom(newConfig);
        config.sanitize();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            updateImpactLight(level, worldPosition);
        }
    }

    /** Server-side tick: maintains real block light at the beam destination. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, LightEmitterBlockEntity be) {
        be.tickCount++;
        // Refresh light position periodically (every 4 ticks = 5 times/sec)
        if (be.tickCount % 4 == 0) {
            be.updateImpactLight(level, pos);
        }
    }

    public void updateImpactLight(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return;
        }

        BlockPos desiredLightPos = null;

        if (config.enabled) {
            if (config.hasTarget) {
                // Aimed at a point in the world: locate closest air/light space
                BlockPos targetBlock = BlockPos.containing(config.targetX, config.targetY, config.targetZ);
                BlockState targetState = level.getBlockState(targetBlock);
                if (targetState.isAir() || targetState.canBeReplaced() || targetState.is(Blocks.LIGHT)) {
                    desiredLightPos = targetBlock;
                } else {
                    BlockPos above = targetBlock.above();
                    BlockState aboveState = level.getBlockState(above);
                    if (aboveState.isAir() || aboveState.canBeReplaced() || aboveState.is(Blocks.LIGHT)) {
                        desiredLightPos = above;
                    }
                }
            } else if (config.down) {
                // Pointing down: drop down until we hit a solid block, place light right above floor
                int maxDrop = config.toSky
                        ? Math.max(1, pos.getY() - level.getMinBuildHeight())
                        : Math.min(128, Math.max(1, (int) config.height));
                for (int dy = 1; dy <= maxDrop; dy++) {
                    BlockPos check = pos.below(dy);
                    BlockState bs = level.getBlockState(check);
                    if (!bs.isAir() && !bs.canBeReplaced() && !bs.is(Blocks.LIGHT)) {
                        desiredLightPos = check.above();
                        break;
                    }
                }
            }
        }

        // Never place the light block on the emitter itself
        if (desiredLightPos != null && (desiredLightPos.equals(pos) || desiredLightPos.equals(worldPosition))) {
            desiredLightPos = null;
        }

        // Clean up previous light block if position shifted or beam disabled
        if (activeLightPos != null && !activeLightPos.equals(desiredLightPos)) {
            cleanUpLight(level, activeLightPos);
            activeLightPos = null;
        }

        // Place new light block
        if (desiredLightPos != null && !desiredLightPos.equals(activeLightPos)) {
            BlockState current = level.getBlockState(desiredLightPos);
            if (current.isAir() || current.canBeReplaced() || current.is(Blocks.LIGHT)) {
                level.setBlock(desiredLightPos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
                activeLightPos = desiredLightPos;
            }
        }
    }

    private static void cleanUpLight(Level level, BlockPos pos) {
        if (level != null && pos != null) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.LIGHT)) {
                level.removeBlock(pos, false);
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && activeLightPos != null) {
            cleanUpLight(level, activeLightPos);
            activeLightPos = null;
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Beam", config.save(new CompoundTag()));
        if (activeLightPos != null) {
            tag.putLong("ImpactLight", activeLightPos.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Beam")) {
            config.copyFrom(BeamConfig.load(tag.getCompound("Beam")));
        }
        if (tag.contains("ImpactLight")) {
            activeLightPos = BlockPos.of(tag.getLong("ImpactLight"));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
    }
}
