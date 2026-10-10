package dev.zerosevennine;

import dev.zerosevennine.command.Scp079Commands;
import dev.zerosevennine.init.ModBlockEntities;
import dev.zerosevennine.init.ModBlocks;
import dev.zerosevennine.init.ModCreativeTabs;
import dev.zerosevennine.init.ModItems;
import dev.zerosevennine.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ZeroSevenNine.MOD_ID)
public class ZeroSevenNine {
    public static final String MOD_ID = "zero_seven_nine";

    public ZeroSevenNine() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        Scp079Commands.register(event.getDispatcher());
    }
}
