package dev.snowscpied;

import dev.snowscpied.config.SnowConfig;
import dev.snowscpied.event.SnowEventHandler;
import dev.snowscpied.init.ModBlocks;
import dev.snowscpied.init.ModCreativeTabs;
import dev.snowscpied.init.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Snow - SCPied:
 * Deep Physical Snow Deformation, Dynamic Footprints, Blood Decals & Universal Procedural Snow Layering.
 */
@Mod(SnowSCPied.MOD_ID)
public class SnowSCPied {
    public static final String MOD_ID = "snow_scpied";
    public static final Logger LOGGER = LogManager.getLogger("Snow-SCPied");

    public SnowSCPied() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SnowConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SnowConfig.COMMON_SPEC);

        MinecraftForge.EVENT_BUS.register(new SnowEventHandler());

        LOGGER.info("Snow - SCPied initialized with building decals and dynamic physics.");
    }
}
