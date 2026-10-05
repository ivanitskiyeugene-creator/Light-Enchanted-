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

    /** Invisible creative-only emitter; drops nothing, walk-through. */
    public static final RegistryObject<CreativeLightEmitterBlock> LIGHT_EMITTER_CREATIVE =
            BLOCKS.register("light_emitter_creative",
                    () -> new CreativeLightEmitterBlock(BlockBehaviour.Properties.of()
                            .strength(0.3f)
                            .sound(SoundType.GLASS)
                            .lightLevel(state -> 15)
                            .noOcclusion()
                            .noCollission()
                            .noLootTable()));

    /** SCP:SL Heavy Containment Zone 3x3 Industrial Ventilation Fan */
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

    /** Modular Stage / Industrial Light Truss */
    public static final RegistryObject<LightTrussBlock> LIGHT_TRUSS = BLOCKS.register("light_truss",
            () -> new LightTrussBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.LANTERN)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    /** Flush-mounted Recessed Downlight */
    public static final RegistryObject<RecessedDownlightBlock> RECESSED_DOWNLIGHT = BLOCKS.register("recessed_downlight",
            () -> new RecessedDownlightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
