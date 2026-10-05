package dev.lightenchanted.blockentity;

import dev.lightenchanted.beam.BeamConfig;
import dev.lightenchanted.block.CreativeLightEmitterBlock;
import dev.lightenchanted.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores the beam configuration of one emitter block.
 * Syncs to tracking clients through the vanilla block-entity update packet.
 */
public class LightEmitterBlockEntity extends BlockEntity {
    private final BeamConfig config = new BeamConfig();

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
     * Authoritative config replacement (call on the server). Saves and pushes
     * an update to every client tracking this chunk.
     */
    public void applyConfig(BeamConfig newConfig) {
        config.copyFrom(newConfig);
        config.sanitize();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Beam", config.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Beam")) {
            config.copyFrom(BeamConfig.load(tag.getCompound("Beam")));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
