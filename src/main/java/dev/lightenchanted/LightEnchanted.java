package dev.lightenchanted;

import com.mojang.logging.LogUtils;
import dev.lightenchanted.init.ModBlockEntities;
import dev.lightenchanted.init.ModBlocks;
import dev.lightenchanted.init.ModCreativeTabs;
import dev.lightenchanted.init.ModItems;
import dev.lightenchanted.network.ModNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(LightEnchanted.MOD_ID)
public final class LightEnchanted {
    public static final String MOD_ID = "lightenchanted";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LightEnchanted() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);

        ModNetwork.register();

        LOGGER.info("Light Enchanted initialized");
    }
}
