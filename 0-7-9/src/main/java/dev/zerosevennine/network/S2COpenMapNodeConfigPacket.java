package dev.zerosevennine.network;

import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import dev.zerosevennine.client.gui.MapNodeConfigScreen;
import dev.zerosevennine.facility.FacilityZone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2COpenMapNodeConfigPacket {
    private final BlockPos nodePos;
    private final String roomName;
    private final FacilityZone zone;
    private final FacilityMapNodeBlockEntity.RoomType roomType;
    private final FacilityZone targetZone;
    private final int gridX;
    private final int gridY;
    private final boolean hasGenerator;

    public S2COpenMapNodeConfigPacket(BlockPos nodePos, String roomName, FacilityZone zone,
                                     FacilityMapNodeBlockEntity.RoomType roomType, FacilityZone targetZone,
                                     int gridX, int gridY, boolean hasGenerator) {
        this.nodePos = nodePos;
        this.roomName = roomName;
        this.zone = zone;
        this.roomType = roomType;
        this.targetZone = targetZone;
        this.gridX = gridX;
        this.gridY = gridY;
        this.hasGenerator = hasGenerator;
    }

    public static void encode(S2COpenMapNodeConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.nodePos);
        buf.writeUtf(msg.roomName);
        buf.writeEnum(msg.zone);
        buf.writeEnum(msg.roomType);
        buf.writeBoolean(msg.targetZone != null);
        if (msg.targetZone != null) {
            buf.writeEnum(msg.targetZone);
        }
        buf.writeInt(msg.gridX);
        buf.writeInt(msg.gridY);
        buf.writeBoolean(msg.hasGenerator);
    }

    public static S2COpenMapNodeConfigPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf();
        FacilityZone z = buf.readEnum(FacilityZone.class);
        FacilityMapNodeBlockEntity.RoomType rt = buf.readEnum(FacilityMapNodeBlockEntity.RoomType.class);
        FacilityZone tz = buf.readBoolean() ? buf.readEnum(FacilityZone.class) : null;
        int gx = buf.readInt();
        int gy = buf.readInt();
        boolean gen = buf.readBoolean();
        return new S2COpenMapNodeConfigPacket(pos, name, z, rt, tz, gx, gy, gen);
    }

    public static void handle(S2COpenMapNodeConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft.getInstance().setScreen(new MapNodeConfigScreen(msg.nodePos, msg.roomName, msg.zone,
                        msg.roomType, msg.targetZone, msg.gridX, msg.gridY, msg.hasGenerator));
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
