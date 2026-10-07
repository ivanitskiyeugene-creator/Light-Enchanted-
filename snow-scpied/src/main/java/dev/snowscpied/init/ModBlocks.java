package dev.snowscpied.init;

import dev.snowscpied.SnowSCPied;
import dev.snowscpied.block.BloodDecalBlock;
import dev.snowscpied.block.FootprintDecalBlock;
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
            DeferredRegister.create(ForgeRegistries.BLOCKS, SnowSCPied.MOD_ID);

    // Decorative Building Footprints (16-way rotation)
    public static final RegistryObject<FootprintDecalBlock> DECAL_BOOTS = BLOCKS.register("decal_boots",
            () -> new FootprintDecalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .noOcclusion()
                    .noCollission()
                    .strength(0.1f)
                    .sound(SoundType.SNOW)));

    public static final RegistryObject<FootprintDecalBlock> DECAL_CLAWS = BLOCKS.register("decal_claws",
            () -> new FootprintDecalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .noOcclusion()
                    .noCollission()
                    .strength(0.1f)
                    .sound(SoundType.SNOW)));

    public static final RegistryObject<FootprintDecalBlock> DECAL_BARE_FEET = BLOCKS.register("decal_bare_feet",
            () -> new FootprintDecalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .noOcclusion()
                    .noCollission()
                    .strength(0.1f)
                    .sound(SoundType.SNOW)));

    // Decorative Building Blood & Fluid Splatters (Walls & Floors)
    public static final RegistryObject<BloodDecalBlock> DECAL_BLOOD_HUMAN = BLOCKS.register("decal_blood_human",
            () -> new BloodDecalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .noOcclusion()
                    .noCollission()
                    .strength(0.1f)
                    .sound(SoundType.SLIME_BLOCK)));

    public static final RegistryObject<BloodDecalBlock> DECAL_BLOOD_ACID = BLOCKS.register("decal_blood_acid",
            () -> new BloodDecalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .noOcclusion()
                    .noCollission()
                    .strength(0.1f)
                    .sound(SoundType.SLIME_BLOCK)));

    public static final RegistryObject<BloodDecalBlock> DECAL_BLOOD_ANOMALOUS = BLOCKS.register("decal_blood_anomalous",
            () -> new BloodDecalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .noOcclusion()
                    .noCollission()
                    .strength(0.1f)
                    .sound(SoundType.SLIME_BLOCK)));

    public static final RegistryObject<BloodDecalBlock> DECAL_BLOOD_ENDER = BLOCKS.register("decal_blood_ender",
            () -> new BloodDecalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noOcclusion()
                    .noCollission()
                    .strength(0.1f)
                    .sound(SoundType.SLIME_BLOCK)));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
