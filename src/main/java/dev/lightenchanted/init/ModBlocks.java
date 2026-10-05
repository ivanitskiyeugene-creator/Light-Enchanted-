package dev.lightenchanted.init;

import dev.lightenchanted.LightEnchanted;
import dev.lightenchanted.block.CreativeLightEmitterBlock;
import dev.lightenchanted.block.LightEmitterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, LightEnchanted.MOD_ID);

    public static final RegistryObject<LightEmitterBlock> LIGHT_EMITTER = BLOCKS.register("light_emitter",
            () -> new LightEmitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    /** Invisible creative-only emitter; drops nothing, walk-through. */
    public static final RegistryObject<CreativeLightEmitterBlock> LIGHT_EMITTER_CREATIVE =
            BLOCKS.register("light_emitter_creative",
                    () -> new CreativeLightEmitterBlock(BlockBehaviour.Properties.of()
                            .strength(0.3f)
                            .sound(SoundType.GLASS)
                            .lightLevel(state -> 15)
                            .noOcclusion()
                            .noCollission()
                            .noLootTable()));

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
