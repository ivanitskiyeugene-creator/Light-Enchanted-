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

import java.util.ArrayList;
import java.util.List;

public class FacilityMapNodeBlockEntity extends BlockEntity {
    public enum RoomType {
        STANDARD("Standard Room"),
        ELEVATOR("Elevator"),
        CHECKPOINT("Checkpoint");

        private final String label;

        RoomType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private String roomName = "Heavy Hallway";
    private FacilityZone zone = FacilityZone.HCZ;
    private RoomType roomType = RoomType.STANDARD;
    private FacilityZone targetZone = null;
    private int gridX = 0;
    private int gridY = 0;
    private boolean hasGenerator = false;
    private boolean isGeneratorBooting = false;
    private int generatorBootTimer = 0;

    private final List<BlockPos> boundaryPoints = new ArrayList<>();
    private BlockPos minBound = null;
    private BlockPos maxBound = null;

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

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType roomType) {
        this.roomType = roomType;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public FacilityZone getTargetZone() {
        return targetZone;
    }

    public void setTargetZone(FacilityZone targetZone) {
        this.targetZone = targetZone;
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

    public List<BlockPos> getBoundaryPoints() {
        return boundaryPoints;
    }

    public BlockPos getMinBound() {
        return minBound;
    }

    public BlockPos getMaxBound() {
        return maxBound;
    }

    public void setBoundaryPoints(List<BlockPos> points) {
        this.boundaryPoints.clear();
        this.boundaryPoints.addAll(points);
        recalcBounds();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void recalcBounds() {
        if (boundaryPoints.isEmpty()) {
            minBound = null;
            maxBound = null;
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : boundaryPoints) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        minBound = new BlockPos(minX, minY, minZ);
        maxBound = new BlockPos(maxX, maxY, maxZ);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("roomName", roomName);
        tag.putString("zone", zone.name());
        tag.putString("roomType", roomType.name());
        if (targetZone != null) {
            tag.putString("targetZone", targetZone.name());
        }
        tag.putInt("gridX", gridX);
        tag.putInt("gridY", gridY);
        tag.putBoolean("hasGen", hasGenerator);
        tag.putBoolean("booting", isGeneratorBooting);
        tag.putInt("timer", generatorBootTimer);

        long[] pts = new long[boundaryPoints.size()];
        for (int i = 0; i < boundaryPoints.size(); i++) {
            pts[i] = boundaryPoints.get(i).asLong();
        }
        tag.putLongArray("boundaryPoints", pts);
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
        if (tag.contains("roomType")) {
            try {
                roomType = RoomType.valueOf(tag.getString("roomType"));
            } catch (Exception ignored) {}
        }
        if (tag.contains("targetZone")) {
            try {
                targetZone = FacilityZone.valueOf(tag.getString("targetZone"));
            } catch (Exception ignored) {}
        } else {
            targetZone = null;
        }
        gridX = tag.getInt("gridX");
        gridY = tag.getInt("gridY");
        hasGenerator = tag.getBoolean("hasGen");
        isGeneratorBooting = tag.getBoolean("booting");
        generatorBootTimer = tag.getInt("timer");

        boundaryPoints.clear();
        if (tag.contains("boundaryPoints")) {
            long[] pts = tag.getLongArray("boundaryPoints");
            for (long p : pts) {
                boundaryPoints.add(BlockPos.of(p));
            }
        }
        recalcBounds();
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
