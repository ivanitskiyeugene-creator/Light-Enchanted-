package dev.slc.init;

import dev.slc.SecretLabCubed;
import dev.slc.entity.TestDummyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SecretLabCubed.MOD_ID);

    public static final RegistryObject<EntityType<TestDummyEntity>> STATIC_DUMMY = ENTITIES.register("static_dummy", () ->
            EntityType.Builder.<TestDummyEntity>of((type, level) -> new TestDummyEntity(type, level, TestDummyEntity.Kind.STATIC), MobCategory.MISC)
                    .sized(.6F, 1.8F).clientTrackingRange(8).build("static_dummy"));
    public static final RegistryObject<EntityType<TestDummyEntity>> RESPAWN_DUMMY = ENTITIES.register("respawn_dummy", () ->
            EntityType.Builder.<TestDummyEntity>of((type, level) -> new TestDummyEntity(type, level, TestDummyEntity.Kind.RESPAWN), MobCategory.MISC)
                    .sized(.6F, 1.8F).clientTrackingRange(8).build("respawn_dummy"));
    public static final RegistryObject<EntityType<TestDummyEntity>> AI_DUMMY = ENTITIES.register("ai_dummy", () ->
            EntityType.Builder.<TestDummyEntity>of((type, level) -> new TestDummyEntity(type, level, TestDummyEntity.Kind.AI), MobCategory.MISC)
                    .sized(.6F, 1.8F).clientTrackingRange(8).build("ai_dummy"));

    private ModEntities() {}
}
