package dev.slc.disarm;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical SCP:SL disarmer state machine (server-side).
 * - 2 s cast with tape-rip sound; broken by damage to the captor, item switch,
 *   range > 4, victim death or victim already tied.
 * - The captor can NEVER release their own tie. Only a THIRD party (any other free
 *   player) can untie by holding the use key ("E") at close range (~3 s).
 * - Tied victims can walk/look/talk but cannot fight, use items, interact or sprint.
 */
public class DisarmerManager {
    public static final int CAST_TICKS = 40;      // 2.0 s
    public static final double CAST_RANGE = 4.0;
    public static final double TIE_RANGE = 3.0;
    public static final int UNTIE_EVENTS = 6;     // ~3 s of held "E" (event repeats every ~10 ticks)
    public static final double UNTIE_RANGE = 2.5;

    private static final Map<UUID, UUID> tiedPlayers = new HashMap<>(); // victim -> captor
    private static final Map<UUID, CastState> activeCasts = new HashMap<>(); // captor -> cast
    private static final Map<UUID, UntieState> untieProgress = new HashMap<>(); // untier -> progress

    private record CastState(UUID victim, int ticksLeft, int startTickItem) {}
    private record UntieState(UUID victim, int progress, int lastEventTick, int eventSeq) {}

    public static void clearAll() {
        tiedPlayers.clear();
        activeCasts.clear();
        untieProgress.clear();
    }

    public static boolean isTied(UUID uuid) {
        return tiedPlayers.containsKey(uuid);
    }

    public static Set<UUID> getTiedPlayers() {
        return new HashSet<>(tiedPlayers.keySet());
    }

    public static UUID getCaptor(UUID victim) {
        return tiedPlayers.get(victim);
    }

    // ------------------------------------------------------------------
    // Tie cast (started by DisarmerItem.onLeftClickEntity)
    // ------------------------------------------------------------------

    public static boolean startCast(ServerPlayer captor, ServerPlayer victim) {
        if (captor == null || victim == null || captor == victim) return false;
        if (isTied(victim.getUUID())) return false;
        if (activeCasts.containsKey(captor.getUUID())) return false;
        if (captor.distanceTo(victim) > TIE_RANGE) {
            captor.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7f, 0.5f);
            return false;
        }
        activeCasts.put(captor.getUUID(), new CastState(victim.getUUID(), CAST_TICKS, captor.getInventory().selected));
        captor.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.2f);
        return true;
    }

    public static void cancelCast(UUID captor, boolean silent) {
        if (captor == null) return;
        CastState cast = activeCasts.remove(captor);
        if (cast == null || silent) return;
    }

    /** Damage to the CAPTOR breaks the cast (called from LivingHurtEvent). */
    public static void onCaptorHurt(ServerPlayer captor) {
        if (captor != null) cancelCast(captor.getUUID(), true);
    }

    public static void tick(MinecraftServer server) {
        // ---- Tie casts ----
        if (!activeCasts.isEmpty()) {
            activeCasts.entrySet().removeIf(entry -> {
                UUID captorId = entry.getKey();
                CastState cast = entry.getValue();
                ServerPlayer captor = server.getPlayerList().getPlayer(captorId);
                ServerPlayer victim = server.getPlayerList().getPlayer(cast.victim());

                if (captor == null || victim == null || !victim.isAlive()
                        || isTied(cast.victim())
                        || captor.distanceTo(victim) > CAST_RANGE
                        || !(captor.getMainHandItem().getItem() instanceof dev.slc.item.DisarmerItem)
                        || captor.getInventory().selected != cast.startTickItem()) {
                    return true; // silently broken
                }

                int left = cast.ticksLeft() - 1;
                if (left <= 0) {
                    tie(victim, captor);
                    return true;
                }
                entry.setValue(new CastState(cast.victim(), left, cast.startTickItem()));
                return false;
            });
        }

        // ---- Untie progress decay (gaps in the "E" hold) ----
        long gameTick = server.getTickCount();
        untieProgress.entrySet().removeIf(e -> gameTick - e.getValue().lastEventTick() > 12);
    }

    private static void tie(ServerPlayer victim, ServerPlayer captor) {
        tiedPlayers.put(victim.getUUID(), captor.getUUID());
        // Tape rip for everyone nearby
        victim.level().playSound(null, victim.blockPosition(), SoundEvents.LEASH_KNOT_PLACE,
                SoundSource.PLAYERS, 1.2f, 1.0f);
    }

    // ------------------------------------------------------------------
    // Untie via held "E" (entity interact repeats while the key is held)
    // ------------------------------------------------------------------

    public static void onUntieEvent(ServerPlayer untier, ServerPlayer victim) {
        if (untier == null || victim == null) return;
        UUID victimId = victim.getUUID();
        if (!isTied(victimId)) return;
        if (isTied(untier.getUUID())) return;

        UUID captor = tiedPlayers.get(victimId);
        if (untier.getUUID().equals(captor)) {
            // The captor can never release their own tie
            untier.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7f, 0.5f);
            return;
        }
        if (untier.distanceTo(victim) > UNTIE_RANGE) return;

        long gameTick = untier.server.getTickCount();
        UntieState st = untieProgress.get(untier.getUUID());
        int progress;
        int seq;
        if (st == null || !st.victim().equals(victimId)) {
            progress = 1;
            seq = 1;
        } else {
            progress = st.progress() + 1;
            seq = st.eventSeq() + 1;
        }
        untieProgress.put(untier.getUUID(), new UntieState(victimId, progress, (int) gameTick, seq));

        if (progress >= UNTIE_EVENTS) {
            untieProgress.remove(untier.getUUID());
            untie(victim);
            untier.level().playSound(null, victim.blockPosition(), SoundEvents.LEASH_KNOT_BREAK,
                    SoundSource.PLAYERS, 1.2f, 1.1f);
        } else {
            untier.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 1.5f);
        }
    }

    public static void untie(ServerPlayer victim) {
        if (victim == null) return;
        tiedPlayers.remove(victim.getUUID());
    }

    public static void onPlayerGone(UUID uuid) {
        tiedPlayers.remove(uuid);
        activeCasts.remove(uuid);
        untieProgress.remove(uuid);
        // Broken captor ties also persist for the victim: released
        tiedPlayers.values().removeIf(captor -> captor.equals(uuid));
    }
}
