package dev.lightenchanted.init;

import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LightEnchanted.MOD_ID);

    public static final RegistryObject<BlockEntityType<LightEmitterBlockEntity>> LIGHT_EMITTER =
            BLOCK_ENTITIES.register("light_emitter",
                    () -> BlockEntityType.Builder.of(LightEmitterBlockEntity::new,
                                    ModBlocks.LIGHT_EMITTER.get(),
                                    ModBlocks.LIGHT_EMITTER_CREATIVE.get())
                            .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
