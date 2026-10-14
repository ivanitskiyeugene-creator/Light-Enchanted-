package dev.slc.init;

import dev.slc.SecretLabCubed;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SecretLabCubed.MOD_ID);

    public static final RegistryObject<CreativeModeTab> SLC_TAB = CREATIVE_MODE_TABS.register("slc_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.slc.slc_tab"))
                    .icon(() -> new ItemStack(ModItems.ELEVATOR_BUTTON.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.ELEVATOR_BUTTON.get());
                        output.accept(ModItems.SPATIAL_MARKER_WAND.get());
                        output.accept(ModItems.ELEVATOR_LINKER.get());
                    })
                    .build());
}
