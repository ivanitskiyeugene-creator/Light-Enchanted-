package dev.slc.scp;

import dev.slc.SecretLabCubed;
import dev.slc.mimic.MimicryEngine;
import dev.slc.role.Role;
import dev.slc.round.RoundEngine;
import dev.slc.round.RoundState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative gameplay for the non-079 SCP roles. */
@Mod.EventBusSubscriber(modid = SecretLabCubed.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ScpAbilityManager {
    private static final long CORPSE_LIFETIME = 20L * 90L;
    private static final long REANIMATION_CHANNEL = 20L * 3L;
    private static final long RAGE_DURATION = 20L * 25L;
    private static final Map<UUID, Corpse> CORPSES = new HashMap<>();
    private static final Map<UUID, Reanimation> REANIMATIONS = new HashMap<>();
    private static final Map<UUID, Rage> RAGES = new HashMap<>();
    private static final Map<UUID, Integer> MIMIC_INDEX = new HashMap<>();

    private ScpAbilityManager() {}

    public static void clear() {
        CORPSES.clear();
        REANIMATIONS.clear();
        RAGES.clear();
        MIMIC_INDEX.clear();
        MimicryEngine.clear();
    }

    public static void activate(ServerPlayer player, ScpAbilityAction action) {
        if (!roundActive() || player == null || player.isSpectator()) return;
        Role role = RoundEngine.getPlayerRole(player.getUUID());
        if (action == ScpAbilityAction.REANIMATE_049 && role == Role.SCP_049) beginReanimation(player);
        if (action == ScpAbilityAction.MIMIC_939 && role == Role.SCP_939) mimic(player);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // A three-second 049 channel is interrupted by any incoming damage, even if the attacker
        // is not a player (fire, projectile, mob, or environment).
        if (event.getEntity() instanceof ServerPlayer damaged) REANIMATIONS.remove(damaged.getUUID());
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || !(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!roundActive() || !human(victim)) return;
        Role role = RoundEngine.getPlayerRole(attacker.getUUID());
        switch (role) {
            case SCP_173 -> {
                if (isWatched(attacker)) event.setCanceled(true);
                else event.setAmount(10_000.0F); // neck snap
            }
            case SCP_939 -> event.setAmount(60.0F);
            case SCP_049 -> event.setAmount(10_000.0F); // "cure" touch
            case SCP_096 -> event.setAmount(RAGES.containsKey(attacker.getUUID()) ? 10_000.0F : 35.0F);
            case SCP_049_2 -> event.setAmount(32.0F);
            default -> { }
        }
        Reanimation channel = REANIMATIONS.get(attacker.getUUID());
        if (channel != null) REANIMATIONS.remove(attacker.getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !roundActive() || !human(victim)) return;
        Role victimRole = RoundEngine.getPlayerRole(victim.getUUID());
        long now = victim.serverLevel().getGameTime();
        CORPSES.put(victim.getUUID(), new Corpse(victim.getUUID(), victim.position(), now + CORPSE_LIFETIME));
        victim.serverLevel().sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 0.8D, victim.getZ(), 14, .25D, .3D, .25D, .01D);

        if (event.getSource().getEntity() instanceof ServerPlayer killer
                && RoundEngine.getPlayerRole(killer.getUUID()) == Role.SCP_939) {
            MimicryEngine.onVictimKilledBy939(killer, victim, victimRole);
        }
        // A dead human enters the normal spectator/wave pool. A nearby 049 may later turn that
        // individual into an independent SCP-049-2 after the player has respawned as spectator.
        RoundEngine.setPlayerRole(victim, Role.SPECTATOR);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !roundActive()) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        tickCorpses(now);
        tickReanimations(server, now);
        for (ServerPlayer scp : server.getPlayerList().getPlayers()) {
            Role role = RoundEngine.getPlayerRole(scp.getUUID());
            if (role == Role.SCP_173) tick173(scp);
            else if (role == Role.SCP_939) tick939Sense(scp);
            else if (role == Role.SCP_096) tick096(scp, now);
        }
    }

    private static void tick173(ServerPlayer scp) {
        if (!isWatched(scp)) return;
        scp.setDeltaMovement(0.0D, scp.getDeltaMovement().y, 0.0D);
        scp.setSprinting(false);
        scp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 10, true, false, false));
    }

    /** Noisy humans receive a short glow pulse; the signal updates continually and works through walls. */
    private static void tick939Sense(ServerPlayer scp) {
        for (ServerPlayer human : scp.server.getPlayerList().getPlayers()) {
            if (!human(human) || human.distanceToSqr(scp) > 48.0D * 48.0D) continue;
            if (human.isSprinting() || human.getDeltaMovement().horizontalDistanceSqr() > 0.018D) {
                human.addEffect(new MobEffectInstance(MobEffects.GLOWING, 8, 0, true, false, false));
            }
        }
    }

    private static void tick096(ServerPlayer scp, long now) {
        Rage rage = RAGES.get(scp.getUUID());
        if (rage != null && rage.until <= now) RAGES.remove(scp.getUUID());
        if (!RAGES.containsKey(scp.getUUID())) {
            for (ServerPlayer human : scp.server.getPlayerList().getPlayers()) {
                if (human(human) && human.distanceToSqr(scp) <= 40.0D * 40.0D && isLookingAt(human, scp, 0.978D)) {
                    RAGES.put(scp.getUUID(), new Rage(human.getUUID(), now + RAGE_DURATION));
                    break;
                }
            }
        }
        if (RAGES.containsKey(scp.getUUID())) {
            scp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 8, 2, true, false, false));
            scp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 8, 2, true, false, false));
        }
    }

    private static void beginReanimation(ServerPlayer doctor) {
        if (REANIMATIONS.containsKey(doctor.getUUID())) return;
        long now = doctor.serverLevel().getGameTime();
        Corpse nearest = null;
        double nearestDistance = 3.6D * 3.6D;
        for (Corpse corpse : CORPSES.values()) {
            double distance = doctor.position().distanceToSqr(corpse.position);
            if (corpse.until > now && distance <= nearestDistance) {
                nearest = corpse;
                nearestDistance = distance;
            }
        }
        if (nearest != null) REANIMATIONS.put(doctor.getUUID(), new Reanimation(nearest.playerUuid, nearest.position, now + REANIMATION_CHANNEL));
    }

    private static void tickReanimations(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, Reanimation>> it = REANIMATIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Reanimation> entry = it.next();
            ServerPlayer doctor = server.getPlayerList().getPlayer(entry.getKey());
            Reanimation action = entry.getValue();
            if (doctor == null || RoundEngine.getPlayerRole(doctor.getUUID()) != Role.SCP_049
                    || doctor.position().distanceToSqr(action.position) > 4.0D * 4.0D) {
                it.remove();
                continue;
            }
            doctor.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, action.position.x, action.position.y + .4D, action.position.z, 2, .2D, .3D, .2D, .01D);
            if (now >= action.finishAt) {
                ServerPlayer revived = server.getPlayerList().getPlayer(action.playerUuid);
                if (revived != null && revived.isAlive() && RoundEngine.getPlayerRole(revived.getUUID()) == Role.SPECTATOR) {
                    RoundEngine.forceRoleAt(revived, Role.SCP_049_2, action.position);
                    CORPSES.remove(action.playerUuid);
                }
                it.remove();
            }
        }
    }

    private static void tickCorpses(long now) {
        CORPSES.values().removeIf(corpse -> corpse.until <= now);
    }

    private static void mimic(ServerPlayer scp939) {
        var clips = MimicryEngine.getClipsFor(scp939.getUUID());
        if (clips.isEmpty()) return;
        int index = Math.floorMod(MIMIC_INDEX.getOrDefault(scp939.getUUID(), -1) + 1, clips.size());
        MIMIC_INDEX.put(scp939.getUUID(), index);
        MimicryEngine.playTacticalMimicSound(scp939, MimicryEngine.TacticalSoundEffect.RUNNING_FOOTSTEPS, scp939.position());
    }

    private static boolean roundActive() {
        return RoundEngine.getState() == RoundState.IN_PROGRESS || RoundEngine.getState() == RoundState.WARHEAD_DETONATING;
    }

    private static boolean human(ServerPlayer player) {
        return player != null && !player.isSpectator() && RoundEngine.getPlayerRole(player.getUUID()).isHuman();
    }

    private static boolean isWatched(ServerPlayer scp) {
        for (ServerPlayer watcher : scp.server.getPlayerList().getPlayers()) {
            if (human(watcher) && watcher.distanceToSqr(scp) <= 36.0D * 36.0D && isLookingAt(watcher, scp, 0.965D)) return true;
        }
        return false;
    }

    private static boolean isLookingAt(ServerPlayer viewer, LivingEntity target, double threshold) {
        Vec3 eyes = viewer.getEyePosition();
        Vec3 targetEyes = target.getEyePosition();
        Vec3 delta = targetEyes.subtract(eyes);
        double distance = delta.length();
        if (distance < .01D) return true;
        if (viewer.getViewVector(1.0F).dot(delta.scale(1.0D / distance)) < threshold) return false;
        HitResult hit = viewer.level().clip(new ClipContext(eyes, targetEyes, ClipContext.Block.COLLIDER, Fluids.NONE, viewer));
        return hit.getType() == HitResult.Type.MISS;
    }

    private record Corpse(UUID playerUuid, Vec3 position, long until) { }
    private record Reanimation(UUID playerUuid, Vec3 position, long finishAt) { }
    private record Rage(UUID targetUuid, long until) { }
}
