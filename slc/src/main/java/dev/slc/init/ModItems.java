package dev.slc.init;

import dev.slc.SecretLabCubed;
import dev.slc.item.DisarmerItem;
import dev.slc.item.DummySpawnerItem;
import dev.slc.item.ElevatorFloorFlipperItem;
import dev.slc.item.ElevatorLinkerItem;
import dev.slc.item.ElevatorTimerTunerItem;
import dev.slc.item.SpatialMarkerWandItem;
import dev.slc.item.map.ElevatorCabinMarkerItem;
import dev.slc.item.map.ExitZoneMarkerItem;
import dev.slc.item.map.PocketDimensionMarkerItem;
import dev.slc.item.map.RoleSpawnMarkerItem;
import dev.slc.role.Role;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SecretLabCubed.MOD_ID);

    /** Dedicated tools: no generic marker whose role is hidden in NBT. */
    public static final RegistryObject<Item> EXIT_ZONE_MARKER = ITEMS.register("exit_zone_marker",
            () -> new ExitZoneMarkerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ELEVATOR_CABIN_MARKER = ITEMS.register("elevator_cabin_marker",
            () -> new ElevatorCabinMarkerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> POCKET_ARRIVAL_MARKER = ITEMS.register("pocket_arrival_marker",
            () -> new PocketDimensionMarkerItem(new Item.Properties().stacksTo(1), PocketDimensionMarkerItem.Type.ARRIVAL_ZONE));
    public static final RegistryObject<Item> POCKET_ESCAPE_MARKER = ITEMS.register("pocket_escape_marker",
            () -> new PocketDimensionMarkerItem(new Item.Properties().stacksTo(1), PocketDimensionMarkerItem.Type.ESCAPE_ZONE));
    public static final RegistryObject<Item> POCKET_RETURN_MARKER = ITEMS.register("pocket_return_marker",
            () -> new PocketDimensionMarkerItem(new Item.Properties().stacksTo(1), PocketDimensionMarkerItem.Type.RETURN_POINT));
    public static final Map<Role, RegistryObject<Item>> SPAWN_POINT_MARKERS = registerSpawnMarkers("spawn_point", RoleSpawnMarkerItem.Type.POINT);
    public static final Map<Role, RegistryObject<Item>> SPAWN_ZONE_MARKERS = registerSpawnMarkers("spawn_zone", RoleSpawnMarkerItem.Type.ZONE);

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

    public static RegistryObject<Item> spawnPointMarker(Role role) { return SPAWN_POINT_MARKERS.get(role); }
    public static RegistryObject<Item> spawnZoneMarker(Role role) { return SPAWN_ZONE_MARKERS.get(role); }

    private static Map<Role, RegistryObject<Item>> registerSpawnMarkers(String prefix, RoleSpawnMarkerItem.Type type) {
        EnumMap<Role, RegistryObject<Item>> markers = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            String roleId = role.name().toLowerCase(Locale.ROOT);
            markers.put(role, ITEMS.register(prefix + "_" + roleId,
                    () -> new RoleSpawnMarkerItem(new Item.Properties().stacksTo(1), role, type)));
        }
        return Collections.unmodifiableMap(markers);
    }
}
