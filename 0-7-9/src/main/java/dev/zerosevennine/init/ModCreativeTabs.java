package dev.zerosevennine.init;

import dev.zerosevennine.ZeroSevenNine;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZeroSevenNine.MOD_ID);

    public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register("tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.zero_seven_nine"))
                    .icon(() -> new ItemStack(ModItems.HCZ_CAMERA.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.HCZ_CAMERA.get());
                        output.accept(ModItems.LCZ_CAMERA.get());
                        output.accept(ModItems.EZ_CAMERA.get());
                        output.accept(ModItems.FACILITY_MAP_NODE.get());
                        output.accept(ModItems.SCREWDRIVER.get());
                    })
                    .build());
}
