package dev.lightenchanted.init;

import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.blockentity.HazeMachineBlockEntity;
import dev.lightenchanted.blockentity.IndustrialFanBlockEntity;
import dev.lightenchanted.blockentity.LightEmitterBlockEntity;
import dev.lightenchanted.blockentity.PhotoreceptorBlockEntity;
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
                                    ModBlocks.MOVING_SPOTLIGHT.get(),
                                    ModBlocks.LIGHT_EMITTER_CREATIVE.get(),
                                    ModBlocks.RECESSED_DOWNLIGHT.get(),
                                    ModBlocks.INDUSTRIAL_FLOODLIGHT.get(),
                                    ModBlocks.FRESNEL_SPOTLIGHT.get(),
                                    ModBlocks.LASER_PROJECTOR.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<IndustrialFanBlockEntity>> INDUSTRIAL_FAN =
            BLOCK_ENTITIES.register("industrial_fan",
                    () -> BlockEntityType.Builder.of(IndustrialFanBlockEntity::new,
                                    ModBlocks.INDUSTRIAL_FAN.get(),
                                    ModBlocks.INDUSTRIAL_FAN_SLAVE.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<IndustrialFanBlockEntity>> WALL_FAN =
            BLOCK_ENTITIES.register("wall_fan",
                    () -> BlockEntityType.Builder.of(IndustrialFanBlockEntity::new,
                                    ModBlocks.WALL_FAN.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<PhotoreceptorBlockEntity>> PHOTORECEPTOR =
            BLOCK_ENTITIES.register("photoreceptor",
                    () -> BlockEntityType.Builder.of(PhotoreceptorBlockEntity::new,
                                    ModBlocks.PHOTORECEPTOR.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<HazeMachineBlockEntity>> HAZE_MACHINE =
            BLOCK_ENTITIES.register("haze_machine",
                    () -> BlockEntityType.Builder.of(HazeMachineBlockEntity::new,
                                    ModBlocks.HAZE_MACHINE.get())
                            .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
