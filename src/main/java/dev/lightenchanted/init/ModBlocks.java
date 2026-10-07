package dev.lightenchanted.init;

import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, LightEnchanted.MOD_ID);

    public static final RegistryObject<LightEmitterBlock> LIGHT_EMITTER = BLOCKS.register("light_emitter",
            () -> new LightEmitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<MovingSpotlightBlock> MOVING_SPOTLIGHT = BLOCKS.register("moving_spotlight",
            () -> new MovingSpotlightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 15)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<CreativeLightEmitterBlock> LIGHT_EMITTER_CREATIVE =
            BLOCKS.register("light_emitter_creative",
                    () -> new CreativeLightEmitterBlock(BlockBehaviour.Properties.of()
                            .strength(0.3f)
                            .sound(SoundType.GLASS)
                            .lightLevel(state -> 15)
                            .noOcclusion()
                            .noCollission()
                            .noLootTable()));

    public static final RegistryObject<IndustrialFanBlock> INDUSTRIAL_FAN = BLOCKS.register("industrial_fan",
            () -> new IndustrialFanBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 10.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<IndustrialFanSlaveBlock> INDUSTRIAL_FAN_SLAVE = BLOCKS.register("industrial_fan_slave",
            () -> new IndustrialFanSlaveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 10.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .noLootTable()));

    public static final RegistryObject<WallFanBlock> WALL_FAN = BLOCKS.register("wall_fan",
            () -> new WallFanBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<LightTrussBlock> LIGHT_TRUSS = BLOCKS.register("light_truss",
            () -> new LightTrussBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.LANTERN)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<RecessedDownlightBlock> RECESSED_DOWNLIGHT = BLOCKS.register("recessed_downlight",
            () -> new RecessedDownlightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<OpticalMirrorBlock> OPTICAL_MIRROR = BLOCKS.register("optical_mirror",
            () -> new OpticalMirrorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<PhotoreceptorBlock> PHOTORECEPTOR = BLOCKS.register("photoreceptor",
            () -> new PhotoreceptorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<HazeMachineBlock> HAZE_MACHINE = BLOCKS.register("haze_machine",
            () -> new HazeMachineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 8.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<IndustrialFloodlightBlock> INDUSTRIAL_FLOODLIGHT = BLOCKS.register("industrial_floodlight",
            () -> new IndustrialFloodlightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 8.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 15)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<FresnelSpotlightBlock> FRESNEL_SPOTLIGHT = BLOCKS.register("fresnel_spotlight",
            () -> new FresnelSpotlightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.8f, 6.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 15)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<LaserProjectorBlock> LASER_PROJECTOR = BLOCKS.register("laser_projector",
            () -> new LaserProjectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 15)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<LightGrateBlock> LIGHT_GRATE = BLOCKS.register("light_grate",
            () -> new LightGrateBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 8.0f)
                    .sound(SoundType.LANTERN)
                    .lightLevel(state -> 15)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<FluorescentTubeBlock> FLUORESCENT_TUBE = BLOCKS.register("fluorescent_tube",
            () -> new FluorescentTubeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(1.0f, 3.0f)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 14)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
