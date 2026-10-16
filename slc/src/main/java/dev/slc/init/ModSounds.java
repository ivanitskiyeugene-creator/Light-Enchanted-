package dev.slc.init;

import dev.slc.SecretLabCubed;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SecretLabCubed.MOD_ID);

    public static final RegistryObject<SoundEvent> ELEVATOR_MOVING = SOUNDS.register("elevator_moving",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SecretLabCubed.MOD_ID, "elevator_moving")));

    public static final RegistryObject<SoundEvent> CASSIE_MTF_WAVE = SOUNDS.register("cassie_announce_mtf",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SecretLabCubed.MOD_ID, "cassie_announce_mtf")));

    public static final RegistryObject<SoundEvent> CASSIE_CHAOS_WAVE = SOUNDS.register("cassie_announce_chaos",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SecretLabCubed.MOD_ID, "cassie_announce_chaos")));
}
