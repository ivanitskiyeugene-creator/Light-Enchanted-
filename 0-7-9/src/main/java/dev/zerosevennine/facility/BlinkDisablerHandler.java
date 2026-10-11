package dev.zerosevennine.facility;

import dev.zerosevennine.config.Scp079Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zero_seven_nine", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlinkDisablerHandler {

    private static final Map<Class<?>, List<Field>> CACHED_BLINK_FIELDS = new ConcurrentHashMap<>();

    public static boolean isBlinkDisabled() {
        if (Scp079Config.COMMON != null && Scp079Config.COMMON.disableScpBlink != null) {
            return Scp079Config.COMMON.disableScpBlink.get();
        }
        return true;
    }

    public static boolean isBlinkResource(ResourceLocation id) {
        if (id == null) return false;
        String path = id.getPath().toLowerCase();
        String ns = id.getNamespace().toLowerCase();

        if (path.contains("blink") || path.contains("blinking") || path.contains("eyelid") ||
            path.contains("eyes_closed") || path.contains("eye_close") || path.contains("close_eye")) {
            return true;
        }

        if (ns.contains("scp") || ns.contains("modern") || ns.contains("lockdown") || ns.contains("facility")) {
            if (path.contains("eye") || path.contains("173") || path.contains("vision_obscure") || path.contains("blackout")) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!isBlinkDisabled()) return;
        MobEffectInstance effectInst = event.getEffectInstance();
        if (effectInst != null && effectInst.getEffect() != null) {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effectInst.getEffect());
            if (isBlinkResource(id)) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    @SubscribeEvent
    public static void onMobEffectAdded(MobEffectEvent.Added event) {
        if (!isBlinkDisabled()) return;
        MobEffectInstance effectInst = event.getEffectInstance();
        if (effectInst != null && effectInst.getEffect() != null && event.getEntity() instanceof Player player) {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effectInst.getEffect());
            if (isBlinkResource(id)) {
                player.removeEffect(effectInst.getEffect());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!isBlinkDisabled()) return;
        Player player = event.player;
        if (player == null) return;

        // 1. Remove active blink effects
        for (MobEffectInstance inst : new ArrayList<>(player.getActiveEffects())) {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(inst.getEffect());
            if (isBlinkResource(id)) {
                player.removeEffect(inst.getEffect());
            }
        }

        // 2. Clear / Reset persistent NBT blink data
        CompoundTag tag = player.getPersistentData();
        if (tag != null) {
            for (String key : new ArrayList<>(tag.getAllKeys())) {
                String lKey = key.toLowerCase();
                if (lKey.contains("blink") || lKey.contains("eyelid") || lKey.contains("eyes_closed") || lKey.contains("eye_state")) {
                    if (tag.contains(key, Tag.TAG_INT)) {
                        tag.putInt(key, 999999);
                    } else if (tag.contains(key, Tag.TAG_FLOAT)) {
                        tag.putFloat(key, 999999.0f);
                    } else if (tag.contains(key, Tag.TAG_BYTE)) {
                        tag.putBoolean(key, false);
                    }
                }
            }
        }

        // 3. Keep capabilities/reflection fields from SCP mods at max timer or not blinking
        suppressPlayerCapabilityBlink(player);
    }

    private static void suppressPlayerCapabilityBlink(Player player) {
        try {
            // Check attached capabilities or fields on player / mod extensions
            Class<?> pClass = player.getClass();
            inspectAndSuppressObject(player, pClass);
        } catch (Exception ignored) {}
    }

    private static void inspectAndSuppressObject(Object obj, Class<?> clazz) {
        if (obj == null || clazz == null || clazz.getName().startsWith("java.") || clazz.getName().startsWith("jdk.")) return;

        List<Field> fields = CACHED_BLINK_FIELDS.computeIfAbsent(clazz, c -> {
            List<Field> matched = new ArrayList<>();
            Class<?> current = c;
            while (current != null && current != Object.class) {
                for (Field f : current.getDeclaredFields()) {
                    String name = f.getName().toLowerCase();
                    if (name.contains("blink") || name.contains("eyelid") || name.contains("eyeclosed") || name.contains("eyesclosed")) {
                        f.setAccessible(true);
                        matched.add(f);
                    }
                }
                current = current.getSuperclass();
            }
            return matched;
        });

        for (Field f : fields) {
            try {
                Class<?> type = f.getType();
                if (type == boolean.class || type == Boolean.class) {
                    f.setBoolean(obj, false);
                } else if (type == int.class || type == Integer.class) {
                    f.setInt(obj, 999999);
                } else if (type == float.class || type == Float.class) {
                    f.setFloat(obj, 999999.0f);
                }
            } catch (Exception ignored) {}
        }
    }
}
