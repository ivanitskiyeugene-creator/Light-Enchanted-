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

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
