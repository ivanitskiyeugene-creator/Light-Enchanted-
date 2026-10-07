package dev.snowscpied.init;

import dev.snowscpied.SnowSCPied;
import dev.snowscpied.item.SnowSprayerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SnowSCPied.MOD_ID);

    // Tools
    public static final RegistryObject<SnowSprayerItem> SNOW_SPRAYER = ITEMS.register("snow_sprayer",
            () -> new SnowSprayerItem(new Item.Properties().stacksTo(1)));

    // Decals
    public static final RegistryObject<BlockItem> DECAL_BOOTS = ITEMS.register("decal_boots",
            () -> new BlockItem(ModBlocks.DECAL_BOOTS.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> DECAL_CLAWS = ITEMS.register("decal_claws",
            () -> new BlockItem(ModBlocks.DECAL_CLAWS.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> DECAL_BARE_FEET = ITEMS.register("decal_bare_feet",
            () -> new BlockItem(ModBlocks.DECAL_BARE_FEET.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> DECAL_BLOOD_HUMAN = ITEMS.register("decal_blood_human",
            () -> new BlockItem(ModBlocks.DECAL_BLOOD_HUMAN.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> DECAL_BLOOD_ACID = ITEMS.register("decal_blood_acid",
            () -> new BlockItem(ModBlocks.DECAL_BLOOD_ACID.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> DECAL_BLOOD_ANOMALOUS = ITEMS.register("decal_blood_anomalous",
            () -> new BlockItem(ModBlocks.DECAL_BLOOD_ANOMALOUS.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> DECAL_BLOOD_ENDER = ITEMS.register("decal_blood_ender",
            () -> new BlockItem(ModBlocks.DECAL_BLOOD_ENDER.get(), new Item.Properties()));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
