package dev.lightenchanted.init;

import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.item.BeamTunerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LightEnchanted.MOD_ID);

    public static final RegistryObject<BlockItem> LIGHT_EMITTER = ITEMS.register("light_emitter",
            () -> new BlockItem(ModBlocks.LIGHT_EMITTER.get(), new Item.Properties()));

    public static final RegistryObject<BeamTunerItem> BEAM_TUNER = ITEMS.register("beam_tuner",
            () -> new BeamTunerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockItem> LIGHT_EMITTER_CREATIVE = ITEMS.register("light_emitter_creative",
            () -> new BlockItem(ModBlocks.LIGHT_EMITTER_CREATIVE.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> INDUSTRIAL_FAN = ITEMS.register("industrial_fan",
            () -> new BlockItem(ModBlocks.INDUSTRIAL_FAN.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> LIGHT_TRUSS = ITEMS.register("light_truss",
            () -> new BlockItem(ModBlocks.LIGHT_TRUSS.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> RECESSED_DOWNLIGHT = ITEMS.register("recessed_downlight",
            () -> new BlockItem(ModBlocks.RECESSED_DOWNLIGHT.get(), new Item.Properties()));

    // ---- v2.3.0 Optics & Lamp Items ----

    public static final RegistryObject<BlockItem> OPTICAL_MIRROR = ITEMS.register("optical_mirror",
            () -> new BlockItem(ModBlocks.OPTICAL_MIRROR.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> PHOTORECEPTOR = ITEMS.register("photoreceptor",
            () -> new BlockItem(ModBlocks.PHOTORECEPTOR.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> HAZE_MACHINE = ITEMS.register("haze_machine",
            () -> new BlockItem(ModBlocks.HAZE_MACHINE.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> INDUSTRIAL_FLOODLIGHT = ITEMS.register("industrial_floodlight",
            () -> new BlockItem(ModBlocks.INDUSTRIAL_FLOODLIGHT.get(), new Item.Properties()));

    public static final RegistryObject<FresnelSpotlightBlockItem> FRESNEL_SPOTLIGHT = ITEMS.register("fresnel_spotlight",
            () -> new FresnelSpotlightBlockItem(ModBlocks.FRESNEL_SPOTLIGHT.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> LASER_PROJECTOR = ITEMS.register("laser_projector",
            () -> new BlockItem(ModBlocks.LASER_PROJECTOR.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> LIGHT_GRATE = ITEMS.register("light_grate",
            () -> new BlockItem(ModBlocks.LIGHT_GRATE.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> FLUORESCENT_TUBE = ITEMS.register("fluorescent_tube",
            () -> new BlockItem(ModBlocks.FLUORESCENT_TUBE.get(), new Item.Properties()));

    public static class FresnelSpotlightBlockItem extends BlockItem {
        public FresnelSpotlightBlockItem(net.minecraft.world.level.block.Block block, Properties properties) {
            super(block, properties);
        }
    }

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
