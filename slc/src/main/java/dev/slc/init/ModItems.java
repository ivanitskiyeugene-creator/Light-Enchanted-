package dev.slc.init;

import dev.slc.SecretLabCubed;
import dev.slc.item.AdminPanelItem;
import dev.slc.item.AdminZoneMarkerItem;
import dev.slc.item.DisarmerItem;
import dev.slc.item.DummySpawnerItem;
import dev.slc.item.ElevatorFloorFlipperItem;
import dev.slc.item.ElevatorLinkerItem;
import dev.slc.item.ElevatorTimerTunerItem;
import dev.slc.item.SpatialMarkerWandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SecretLabCubed.MOD_ID);

    public static final RegistryObject<Item> ADMIN_PANEL = ITEMS.register("admin_panel",
            () -> new AdminPanelItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ADMIN_ZONE_MARKER = ITEMS.register("admin_zone_marker",
            () -> new AdminZoneMarkerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> DUMMY_SPAWNER = ITEMS.register("dummy_spawner",
            () -> new DummySpawnerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ELEVATOR_BUTTON = ITEMS.register("elevator_button",
            () -> new BlockItem(ModBlocks.ELEVATOR_BUTTON.get(), new Item.Properties()));

    public static final RegistryObject<Item> SPATIAL_MARKER_WAND = ITEMS.register("spatial_marker_wand",
            () -> new SpatialMarkerWandItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ELEVATOR_LINKER = ITEMS.register("elevator_linker",
            () -> new ElevatorLinkerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ELEVATOR_TIMER_TUNER = ITEMS.register("elevator_timer_tuner",
            () -> new ElevatorTimerTunerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> DISARMER = ITEMS.register("disarmer",
            () -> new DisarmerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ELEVATOR_FLOOR_FLIPPER = ITEMS.register("elevator_floor_flipper",
            () -> new ElevatorFloorFlipperItem(new Item.Properties().stacksTo(1)));
}
