package dev.snowscpied.init;

import dev.snowscpied.SnowSCPied;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SnowSCPied.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.snow_scpied"))
                    .icon(() -> new ItemStack(ModItems.SNOW_SPRAYER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SNOW_SPRAYER.get());
                        output.accept(ModItems.DECAL_BOOTS.get());
                        output.accept(ModItems.DECAL_CLAWS.get());
                        output.accept(ModItems.DECAL_BARE_FEET.get());
                        output.accept(ModItems.DECAL_BLOOD_HUMAN.get());
                        output.accept(ModItems.DECAL_BLOOD_ACID.get());
                        output.accept(ModItems.DECAL_BLOOD_ANOMALOUS.get());
                        output.accept(ModItems.DECAL_BLOOD_ENDER.get());
                    })
                    .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
