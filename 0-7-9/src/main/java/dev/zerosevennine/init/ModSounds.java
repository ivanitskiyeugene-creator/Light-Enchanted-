package dev.zerosevennine.init;

import dev.zerosevennine.ZeroSevenNine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ZeroSevenNine.MOD_ID);

    public static final RegistryObject<SoundEvent> CAMERA_SWITCH =
            SOUND_EVENTS.register("camera_switch", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ZeroSevenNine.MOD_ID, "camera_switch")));

    public static final RegistryObject<SoundEvent> ZONE_DIAL_TONE =
            SOUND_EVENTS.register("zone_dial_tone", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ZeroSevenNine.MOD_ID, "zone_dial_tone")));

    public static final RegistryObject<SoundEvent> CAMERA_LINK =
            SOUND_EVENTS.register("camera_link", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ZeroSevenNine.MOD_ID, "camera_link")));
}
