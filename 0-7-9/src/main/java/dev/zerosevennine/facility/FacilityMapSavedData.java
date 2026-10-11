package dev.zerosevennine.facility;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public class FacilityMapSavedData extends SavedData {
    private static final String DATA_NAME = "zero_seven_nine_facility_map";

    public static FacilityMapSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                FacilityMapSavedData::load,
                FacilityMapSavedData::new,
                DATA_NAME
        );
    }

    public FacilityMapSavedData() {
    }

    public static FacilityMapSavedData load(CompoundTag tag) {
        FacilityMapSavedData data = new FacilityMapSavedData();
        FacilityNetworkManager.clearAllRooms();

        if (tag.contains("Rooms", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Rooms", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag rTag = list.getCompound(i);
                String name = rTag.getString("name");
                FacilityZone zone = FacilityZone.valueOf(rTag.getString("zone"));
                RoomShape shape = RoomShape.valueOf(rTag.getString("shape"));
                int rot = rTag.getInt("rot");
                int gx = rTag.getInt("gx");
                int gy = rTag.getInt("gy");
                boolean gen = rTag.getBoolean("gen");
                boolean booting = rTag.getBoolean("booting");
                FacilityZone targetZone = rTag.contains("targetZone") ? FacilityZone.valueOf(rTag.getString("targetZone")) : zone;

                BlockPos pos = rTag.contains("pos") ? BlockPos.of(rTag.getLong("pos")) : new BlockPos(gx * 16, 64, gy * 16);

                FacilityNetworkManager.RoomNode node = new FacilityNetworkManager.RoomNode(
                        pos, name, zone, null, shape, rot, targetZone, gx, gy
                );
                node.hasGenerator = gen;
                node.isGeneratorBooting = booting;

                if (rTag.contains("cameras", Tag.TAG_LIST)) {
                    ListTag camList = rTag.getList("cameras", Tag.TAG_COMPOUND);
                    for (int j = 0; j < camList.size(); j++) {
                        CompoundTag cTag = camList.getCompound(j);
                        node.linkedCameras.add(BlockPos.of(cTag.getLong("pos")));
                    }
                }

                FacilityNetworkManager.registerRoomNode(node);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (FacilityNetworkManager.RoomNode node : FacilityNetworkManager.getRooms().values()) {
            CompoundTag rTag = new CompoundTag();
            rTag.putString("name", node.roomName != null ? node.roomName : "");
            rTag.putString("zone", node.zone.name());
            rTag.putString("shape", node.roomShape.name());
            rTag.putInt("rot", node.rotation);
            rTag.putInt("gx", node.gridX);
            rTag.putInt("gy", node.gridY);
            rTag.putBoolean("gen", node.hasGenerator);
            rTag.putBoolean("booting", node.isGeneratorBooting);
            rTag.putString("targetZone", node.targetZone != null ? node.targetZone.name() : node.zone.name());
            if (node.pos != null) {
                rTag.putLong("pos", node.pos.asLong());
            }

            ListTag camList = new ListTag();
            for (BlockPos cp : node.linkedCameras) {
                CompoundTag cTag = new CompoundTag();
                cTag.putLong("pos", cp.asLong());
                camList.add(cTag);
            }
            rTag.put("cameras", camList);

            list.add(rTag);
        }
        tag.put("Rooms", list);
        return tag;
    }
}
