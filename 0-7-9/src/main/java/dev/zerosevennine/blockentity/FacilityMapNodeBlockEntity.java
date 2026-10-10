package dev.zerosevennine.blockentity;

import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FacilityMapNodeBlockEntity extends BlockEntity {
    private String roomName = "Heavy Hallway";
    private FacilityZone zone = FacilityZone.HCZ;
    private int gridX = 0;
    private int gridY = 0;
    private boolean hasGenerator = false;
    private boolean isGeneratorBooting = false;
    private int generatorBootTimer = 0;

    public FacilityMapNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FACILITY_MAP_NODE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FacilityMapNodeBlockEntity be) {
        if (be.isGeneratorBooting) {
            be.generatorBootTimer++;
            // 60-second generator bootup sequence
            if (be.generatorBootTimer > 1200) {
                be.isGeneratorBooting = false;
                be.generatorBootTimer = 0;
                FacilityNetworkManager.onGeneratorOvercharge(level);
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            FacilityNetworkManager.registerMapNode(worldPosition, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            FacilityNetworkManager.unregisterMapNode(worldPosition);
        }
        super.setRemoved();
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public FacilityZone getZone() {
        return zone;
    }

    public void setZone(FacilityZone zone) {
        this.zone = zone;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getGridX() {
        return gridX;
    }

    public void setGridX(int gridX) {
        this.gridX = gridX;
        setChanged();
    }

    public int getGridY() {
        return gridY;
    }

    public void setGridY(int gridY) {
        this.gridY = gridY;
        setChanged();
    }

    public boolean hasGenerator() {
        return hasGenerator;
    }

    public void setHasGenerator(boolean hasGen) {
        this.hasGenerator = hasGen;
        setChanged();
    }

    public boolean isGeneratorBooting() {
        return isGeneratorBooting;
    }

    public void startGeneratorBooting() {
        this.isGeneratorBooting = true;
        this.generatorBootTimer = 0;
        setChanged();
        FacilityNetworkManager.broadcastGeneratorWarning(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("roomName", roomName);
        tag.putString("zone", zone.name());
        tag.putInt("gridX", gridX);
        tag.putInt("gridY", gridY);
        tag.putBoolean("hasGen", hasGenerator);
        tag.putBoolean("booting", isGeneratorBooting);
        tag.putInt("timer", generatorBootTimer);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        roomName = tag.getString("roomName");
        if (tag.contains("zone")) {
            try {
                zone = FacilityZone.valueOf(tag.getString("zone"));
            } catch (Exception ignored) {}
        }
        gridX = tag.getInt("gridX");
        gridY = tag.getInt("gridY");
        hasGenerator = tag.getBoolean("hasGen");
        isGeneratorBooting = tag.getBoolean("booting");
        generatorBootTimer = tag.getInt("timer");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
