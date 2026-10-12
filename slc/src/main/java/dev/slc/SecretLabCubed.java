package dev.slc;

import dev.slc.command.SlcCommands;
import dev.slc.config.SlcConfig;
import dev.slc.round.RoundEngine;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SecretLabCubed.MOD_ID)
public class SecretLabCubed {
    public static final String MOD_ID = "slc";
    public static final Logger LOGGER = LogManager.getLogger("SecretLabCubed");

    public SecretLabCubed() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SlcConfig.COMMON_SPEC);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[SLC] SECRET LAB: CUBED Round Orchestrator initialized.");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        RoundEngine.init(event.getServer());
        LOGGER.info("[SLC] Server starting - Round Engine attached.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        RoundEngine.cleanup();
        LOGGER.info("[SLC] Server stopping - Round Engine cleaned up.");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            RoundEngine.tick();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SlcCommands.register(event.getDispatcher());
    }
}
