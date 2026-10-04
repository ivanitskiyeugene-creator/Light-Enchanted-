package dev.lightenchanted.init;

import dev.lightenchanted.LightEnchanted;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LightEnchanted.MOD_ID);

    public static final RegistryObject<BlockItem> LIGHT_EMITTER = ITEMS.register("light_emitter",
            () -> new BlockItem(ModBlocks.LIGHT_EMITTER.get(), new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
