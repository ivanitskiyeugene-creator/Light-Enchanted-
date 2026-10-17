package dev.slc.mimic;

import dev.slc.role.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class MimicryEngine {
    public static class StolenVoiceClip {
        public final UUID victimUuid;
        public final String victimName;
        public final Role victimRole;
        public final long timestampMs;
        public final String phrasePreview;

        public StolenVoiceClip(UUID victimUuid, String victimName, Role victimRole, String phrasePreview) {
            this.victimUuid = victimUuid;
            this.victimName = victimName;
            this.victimRole = victimRole;
            this.timestampMs = System.currentTimeMillis();
            this.phrasePreview = phrasePreview != null ? phrasePreview : "ПОМОГИТЕ! ЗДЕСЬ КТО-ТО ЕСТЬ!";
        }
    }

    public enum TacticalSoundEffect {
        DOOR_KEYCARD_BEEP("Писк считывателя дверей", SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.8f),
        GUN_RELOAD_RATTLE("Перезарядка оружия (TACZ)", SoundEvents.IRON_GOLEM_REPAIR, 0.8f, 1.4f),
        MEDKIT_BANDAGE("Использование аптечки", SoundEvents.WOOL_PLACE, 1.0f, 1.1f),
        RUNNING_FOOTSTEPS("Шаги бегущего D-Класса", SoundEvents.STONE_STEP, 1.2f, 1.0f),
        COIN_DROP("Звук падения предмета", SoundEvents.ITEM_PICKUP, 0.9f, 0.7f);

        public final String title;
        public final net.minecraft.sounds.SoundEvent soundEvent;
        public final float volume;
        public final float pitch;

        TacticalSoundEffect(String title, net.minecraft.sounds.SoundEvent soundEvent, float volume, float pitch) {
            this.title = title;
            this.soundEvent = soundEvent;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    // Voice Clips Stored per SCP-939 Player
    private static final Map<UUID, List<StolenVoiceClip>> stolenVoices = new HashMap<>();

    public static void onVictimKilledBy939(ServerPlayer scp939, ServerPlayer victim, Role victimRole) {
        if (scp939 == null || victim == null) return;

        List<StolenVoiceClip> clips = stolenVoices.computeIfAbsent(scp939.getUUID(), k -> new ArrayList<>());
        if (clips.size() >= 8) {
            clips.remove(0); // Circular buffer max 8 clips
        }

        String phrase = victimRole == Role.SCIENTIST ? "НЕ СТРЕЛЯЙТЕ, Я СВОЙ!" : "ОТКРОЙТЕ ДВЕРЬ!";
        StolenVoiceClip clip = new StolenVoiceClip(victim.getUUID(), victim.getScoreboardName(), victimRole, phrase);
        clips.add(clip);

    }

    public static List<StolenVoiceClip> getClipsFor(UUID scp939Uuid) {
        return stolenVoices.getOrDefault(scp939Uuid, Collections.emptyList());
    }

    /** Captured voices are strictly round-scoped. */
    public static void clear() {
        stolenVoices.clear();
    }

    public static void playTacticalMimicSound(ServerPlayer scp939, TacticalSoundEffect effect, Vec3 targetPos) {
        if (scp939 == null || effect == null || scp939.level() == null) return;

        Vec3 playAt = targetPos != null ? targetPos : scp939.position();
        scp939.level().playSound(null, BlockPos.containing(playAt), effect.soundEvent, SoundSource.PLAYERS, effect.volume, effect.pitch);
    }
}
