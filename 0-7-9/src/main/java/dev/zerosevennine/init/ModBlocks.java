package dev.zerosevennine.init;

import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ZeroSevenNine.MOD_ID);

    public static final RegistryObject<Block> HCZ_CAMERA = BLOCKS.register("hcz_camera",
            () -> new HczCameraBlock(BlockBehaviour.Properties.of()
                    .strength(2.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final RegistryObject<Block> LCZ_CAMERA = BLOCKS.register("lcz_camera",
            () -> new LczCameraBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 5.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final RegistryObject<Block> EZ_CAMERA = BLOCKS.register("ez_camera",
            () -> new EzCameraBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 5.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final RegistryObject<Block> FACILITY_MAP_NODE = BLOCKS.register("facility_map_node",
            () -> new FacilityMapNodeBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f, 8.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
}
