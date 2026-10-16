package dev.slc.init;

import dev.slc.entity.TestDummyEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import dev.slc.SecretLabCubed;

@Mod.EventBusSubscriber(modid = SecretLabCubed.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributes {
    private ModEntityAttributes() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.STATIC_DUMMY.get(), TestDummyEntity.createAttributes().build());
        event.put(ModEntities.RESPAWN_DUMMY.get(), TestDummyEntity.createAttributes().build());
        event.put(ModEntities.AI_DUMMY.get(), TestDummyEntity.createAttributes().build());
    }
}
