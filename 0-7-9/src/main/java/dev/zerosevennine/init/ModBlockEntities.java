package dev.zerosevennine.init;

import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.blockentity.FacilityMapNodeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZeroSevenNine.MOD_ID);

    public static final RegistryObject<BlockEntityType<CameraBlockEntity>> CAMERA =
            BLOCK_ENTITIES.register("camera",
                    () -> BlockEntityType.Builder.of(CameraBlockEntity::new,
                            ModBlocks.HCZ_CAMERA.get(),
                            ModBlocks.LCZ_CAMERA.get(),
                            ModBlocks.EZ_CAMERA.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<FacilityMapNodeBlockEntity>> FACILITY_MAP_NODE =
            BLOCK_ENTITIES.register("facility_map_node",
                    () -> BlockEntityType.Builder.of(FacilityMapNodeBlockEntity::new,
                            ModBlocks.FACILITY_MAP_NODE.get()
                    ).build(null));
}
