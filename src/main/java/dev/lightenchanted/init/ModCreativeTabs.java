package dev.lightenchanted.init;

import dev.lightenchanted.LightEnchanted;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LightEnchanted.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.lightenchanted"))
                    .icon(() -> new ItemStack(ModItems.LIGHT_EMITTER.get()))
                    .displayItems((parameters, output) -> {
                        // Projectors & Fixtures
                        output.accept(ModItems.LIGHT_EMITTER.get());
                        output.accept(ModItems.INDUSTRIAL_FLOODLIGHT.get());
                        output.accept(ModItems.FRESNEL_SPOTLIGHT.get());
                        output.accept(ModItems.LASER_PROJECTOR.get());
                        output.accept(ModItems.RECESSED_DOWNLIGHT.get());
                        output.accept(ModItems.FLUORESCENT_TUBE.get());
                        output.accept(ModItems.LIGHT_GRATE.get());
                        output.accept(ModItems.LIGHT_TRUSS.get());

                        // Optics & Interactive Blocks
                        output.accept(ModItems.OPTICAL_MIRROR.get());
                        output.accept(ModItems.PHOTORECEPTOR.get());
                        output.accept(ModItems.HAZE_MACHINE.get());
                        output.accept(ModItems.INDUSTRIAL_FAN.get());

                        // Tools & Utilities
                        output.accept(ModItems.BEAM_TUNER.get());
                        output.accept(ModItems.LIGHT_EMITTER_CREATIVE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
