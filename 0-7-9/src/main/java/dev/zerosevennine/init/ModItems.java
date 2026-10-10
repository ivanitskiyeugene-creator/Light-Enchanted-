package dev.zerosevennine.init;

import dev.zerosevennine.ZeroSevenNine;
import dev.zerosevennine.item.MapTabletItem;
import dev.zerosevennine.item.RoomSelectorItem;
import dev.zerosevennine.item.ScrewdriverItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ZeroSevenNine.MOD_ID);

    public static final RegistryObject<Item> HCZ_CAMERA = ITEMS.register("hcz_camera",
            () -> new BlockItem(ModBlocks.HCZ_CAMERA.get(), new Item.Properties()));

    public static final RegistryObject<Item> LCZ_CAMERA = ITEMS.register("lcz_camera",
            () -> new BlockItem(ModBlocks.LCZ_CAMERA.get(), new Item.Properties()));

    public static final RegistryObject<Item> EZ_CAMERA = ITEMS.register("ez_camera",
            () -> new BlockItem(ModBlocks.EZ_CAMERA.get(), new Item.Properties()));

    public static final RegistryObject<Item> FACILITY_MAP_NODE = ITEMS.register("facility_map_node",
            () -> new BlockItem(ModBlocks.FACILITY_MAP_NODE.get(), new Item.Properties()));

    public static final RegistryObject<Item> SCREWDRIVER = ITEMS.register("screwdriver",
            () -> new ScrewdriverItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ROOM_SELECTOR = ITEMS.register("room_selector",
            () -> new RoomSelectorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> MAP_TABLET = ITEMS.register("map_tablet",
            () -> new MapTabletItem(new Item.Properties().stacksTo(1)));
}
