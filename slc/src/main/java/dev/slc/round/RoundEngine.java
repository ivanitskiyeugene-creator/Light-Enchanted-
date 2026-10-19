package dev.slc.round;

import dev.slc.cassie.CassieVoiceEngine;
import dev.slc.config.SlcConfig;
import dev.slc.decontam.DecontaminationManager;
import dev.slc.loadout.SlcArmorManager;
import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.role.Role;
import dev.slc.role.Team;
import dev.slc.warhead.AlphaWarheadManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.phys.Vec3;
import dev.slc.zone.SpatialZoneManager;

import java.util.*;

/**
 * Core round lifecycle manager implementing canonical 1:1 SCP:SL (14.2+)
 * victory conditions, team tracking, and civilian escape calculations.
 */
public class RoundEngine {
    private static MinecraftServer server = null;
    private static RoundState state = RoundState.WAITING_FOR_PLAYERS;

    private static int countdownTicks = 0;
    private static int roundElapsedTicks = 0;
    /** OP switch: keeps the round alive by bypassing only automatic victory evaluation. */
    private static boolean automaticRoundEndLocked = false;
    private static final Map<UUID, Role> playerRoles = new HashMap<>();

    // Escape counters strictly following SL 14.2 canon (only untied civilians score points)
    private static int freeScientistEscapes = 0;
    private static int freeClassDEscapes = 0;
    private static int tiedScientistEscapes = 0;
    private static int tiedClassDEscapes = 0;

    public static void init(MinecraftServer s) {
        server = s;
        state = RoundState.WAITING_FOR_PLAYERS;
        countdownTicks = 0;
        roundElapsedTicks = 0;
        automaticRoundEndLocked = false;
        playerRoles.clear();
        freeScientistEscapes = 0;
        freeClassDEscapes = 0;
        tiedScientistEscapes = 0;
        tiedClassDEscapes = 0;
        DecontaminationManager.reset();
        AlphaWarheadManager.reset();
        StaminaManager.reset();
        dev.slc.scp.ScpAbilityManager.clear();
        RespawnWaveManager.init(s);
        dev.slc.disarm.DisarmerManager.clearAll();
    }

    public static void cleanup() {
        RoundInventoryManager.forgetAll();
        StaminaManager.reset();
        dev.slc.scp.ScpAbilityManager.clear();
        server = null;
        playerRoles.clear();
        RespawnWaveManager.cleanup();
        dev.slc.disarm.DisarmerManager.clearAll();
    }

    public static RoundState getState() {
        return state;
    }

    public static int getRoundElapsedTicks() {
        return roundElapsedTicks;
    }

    public static boolean isAutomaticRoundEndLocked() {
        return automaticRoundEndLocked;
    }

    /** @return the new enabled state of the "do not stop round" lock. */
    public static boolean toggleAutomaticRoundEndLock() {
        automaticRoundEndLocked = !automaticRoundEndLocked;
        syncClientState();
        return automaticRoundEndLocked;
    }

    public static Role getPlayerRole(UUID uuid) {
        return playerRoles.getOrDefault(uuid, Role.SPECTATOR);
    }

    /** Applies role state and its saved equipment template, but leaves the player in place. */
    public static void setPlayerRole(ServerPlayer player, Role role) {
        if (player == null || role == null) return;
        Role previous = getPlayerRole(player.getUUID());
        if (previous == Role.SCP_079 && role != Role.SCP_079) {
            dev.slc.integration.Scp079Bridge.end(player);
        }
        playerRoles.put(player.getUUID(), role);
        applyRoleToPlayer(player, role);
    }

    /** Administration/spawn-wave path: full role application followed by a saved point or zone spawn. */
    public static void forceRoleAndSpawn(ServerPlayer player, Role role) {
        setPlayerRole(player, role);
        // SCP-079 is positioned by its 0-7-9 camera session, not by a physical role spawn.
        if (role != Role.SCP_079) teleportToRoleSpawn(player, role);
        // Do not wait for the ten-tick spectator sync: the role card must appear with the spawn.
        syncClientState();
    }

    /** Role conversion at an explicit world location, used by SCP-049 reanimation rather than a spawn marker. */
    public static void forceRoleAt(ServerPlayer player, Role role, Vec3 position) {
        if (player == null || role == null || position == null) return;
        setPlayerRole(player, role);
        player.connection.teleport(position.x, position.y, position.z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        syncClientState();
    }

    public static void teleportToRoleSpawn(ServerPlayer player, Role role) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        SpatialZoneManager.SpawnTarget target = SpatialZoneManager.get(level)
                .getRandomRoleSpawn(level, role, player.getRandom());
        if (target == null) {
            net.minecraft.core.BlockPos fallback = level.getSharedSpawnPos();
            target = new SpatialZoneManager.SpawnTarget(fallback.getX() + 0.5D, fallback.getY(), fallback.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
        }
        player.connection.teleport(target.x(), target.y(), target.z(), target.yaw(), target.pitch());
        player.resetFallDistance();
    }

    public static int getFreeScientistEscapes() {
        return freeScientistEscapes;
    }

    public static int getFreeClassDEscapes() {
        return freeClassDEscapes;
    }

    public static int getTiedScientistEscapes() {
        return tiedScientistEscapes;
    }

    public static int getTiedClassDEscapes() {
        return tiedClassDEscapes;
    }

    /**
     * Triggered when a player enters the surface escape zone.
     * Tied civilians change team and grant tickets, but do NOT count as civilian points for their original team.
     */
    public static void handlePlayerEscape(ServerPlayer player, boolean isTied) {
        if (player == null) return;
        Role currentRole = getPlayerRole(player.getUUID());

        if (currentRole == Role.CLASS_D) {
            if (isTied) {
                tiedClassDEscapes++;
                setPlayerRole(player, Role.MTF_PRIVATE);
                // Captor's faction gains the converted escape: MTF tickets
                RespawnWaveManager.addMtfTickets(SlcConfig.COMMON.mtfEscapeTicketBonus.get());
            } else {
                freeClassDEscapes++;
                setPlayerRole(player, Role.CHAOS_CONSCRIPT);
                RespawnWaveManager.addChaosTickets(SlcConfig.COMMON.chaosEscapeTicketBonus.get());
            }
        } else if (currentRole == Role.SCIENTIST) {
            if (isTied) {
                tiedScientistEscapes++;
                setPlayerRole(player, Role.CHAOS_CONSCRIPT);
                // Captor's faction gains the converted escape: Chaos tickets
                RespawnWaveManager.addChaosTickets(SlcConfig.COMMON.chaosEscapeTicketBonus.get());
            } else {
                freeScientistEscapes++;
                setPlayerRole(player, Role.MTF_SPECIALIST);
                RespawnWaveManager.addMtfTickets(SlcConfig.COMMON.mtfEscapeTicketBonus.get());
            }
        }
        // Bonds never survive an escape
        dev.slc.disarm.DisarmerManager.untie(player);
        syncClientState();
    }

    public static void startRoundCountdown(int seconds) {
        state = RoundState.LOBBY_COUNTDOWN;
        countdownTicks = seconds * 20;
        CassieVoiceEngine.broadcast("FACILITY BREACH SEQUENCE INITIALIZED IN " + seconds + " SECONDS", 0x3498DB);
    }

    public static void forceStartRound() {
        state = RoundState.IN_PROGRESS;
        roundElapsedTicks = 0;
        freeScientistEscapes = 0;
        freeClassDEscapes = 0;
        tiedScientistEscapes = 0;
        tiedClassDEscapes = 0;
        RespawnWaveManager.reset();
        assignInitialRoles();
        DecontaminationManager.startTimer();
        CassieVoiceEngine.broadcast("FACILITY OPERATING NORMALLY . ALL STATIONS AT FULL BREACH READINESS", 0x2ECC71);
        syncClientState();
    }

    public static void endRound(Team winningTeam) {
        state = RoundState.ROUND_ENDED;
        endScp079Sessions();
        dev.slc.scp.ScpAbilityManager.clear();
        RoundInventoryManager.finishRound(server);
        String winMsg = winningTeam != null ?
                "РАУНД ОКОНЧЕН! ПОБЕДА: " + winningTeam.getDisplayName().toUpperCase() :
                "РАУНД ОКОНЧЕН! НИЧЬЯ (STALEMATE)";

        CassieVoiceEngine.broadcast(winMsg, winningTeam != null ? winningTeam.getColorRgb() : 0xFFFFFF);
        syncClientState();
    }

    /** Clean round reset used by the OP-4 control panel. Map zones/loadouts are SavedData and remain intact. */
    public static void restartToLobby() {
        endScp079Sessions();
        dev.slc.scp.ScpAbilityManager.clear();
        RoundInventoryManager.finishRound(server);
        state = RoundState.WAITING_FOR_PLAYERS;
        countdownTicks = 0;
        roundElapsedTicks = 0;
        automaticRoundEndLocked = false;
        freeScientistEscapes = 0;
        freeClassDEscapes = 0;
        tiedScientistEscapes = 0;
        tiedClassDEscapes = 0;
        DecontaminationManager.reset();
        AlphaWarheadManager.reset();
        RespawnWaveManager.reset();
        dev.slc.disarm.DisarmerManager.clearAll();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                playerRoles.put(player.getUUID(), Role.SPECTATOR);
                player.setGameMode(GameType.SPECTATOR);
            }
        }
        syncClientState();
    }

    public static void tick() {
        if (server == null) return;

        dev.slc.disarm.DisarmerManager.tick(server);

        switch (state) {
            case LOBBY_COUNTDOWN -> {
                if (countdownTicks > 0) {
                    countdownTicks--;
                    if (countdownTicks % 20 == 0) {
                        int sec = countdownTicks / 20;
                        if (sec <= 5 && sec > 0) {
                            CassieVoiceEngine.broadcast("T MINUS " + sec + " SECONDS", 0xF39C12);
                        }
                    }
                    if (countdownTicks == 0) {
                        forceStartRound();
                    }
                }
            }
            case IN_PROGRESS -> {
                roundElapsedTicks++;
                StaminaManager.tick(server);
                if (roundElapsedTicks % 20 == 0) enforceScpInventoryEmpty();
                DecontaminationManager.tick(roundElapsedTicks);
                AlphaWarheadManager.tick();
                RespawnWaveManager.tick();
                if (roundElapsedTicks % 10 == 0) {
                    syncClientState();
                }
                checkWinConditions();
            }
            case WARHEAD_DETONATING -> {
                roundElapsedTicks++;
                StaminaManager.tick(server);
                if (roundElapsedTicks % 20 == 0) enforceScpInventoryEmpty();
                AlphaWarheadManager.tick();
                RespawnWaveManager.tick();
                if (roundElapsedTicks % 10 == 0) {
                    syncClientState();
                }
                checkWinConditions();
            }
            default -> {}
        }
    }

    public static void syncClientState() {
        if (server == null) return;
        List<dev.slc.spectator.SpectatorTargetInfo> targets = new ArrayList<>();
        int aliveMtf = 0;
        int aliveChaos = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Role role = getPlayerRole(player.getUUID());
            if (player.isAlive() && (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR || role == Role.SCP_079)) {
                if (role != Role.SPECTATOR) {
                    float hp = player.getHealth();
                    float maxHp = (float) role.getMaxHealth();
                    float maxHs = (float) role.getMaxHumeShield();
                    float hs = maxHs > 0 ? maxHs * (hp / maxHp) : 0f;

                    targets.add(new dev.slc.spectator.SpectatorTargetInfo(
                            player.getUUID(),
                            player.getScoreboardName(),
                            role,
                            hp,
                            maxHp,
                            hs,
                            maxHs
                    ));

                    if (role.getTeam() == Team.FOUNDATION_FORCES || role.getTeam() == Team.SCIENTISTS) {
                        aliveMtf++;
                    } else if (role.getTeam() == Team.CHAOS_INSURGENCY || role.getTeam() == Team.CLASS_D_PERSONNEL) {
                        aliveChaos++;
                    }
                }
            }
        }

        // Ticket bars show the REAL respawn tickets (SL influence tokens)
        float mtfTokens = Math.max(0.1f, RespawnWaveManager.getMtfTickets());
        float chaosTokens = Math.max(0.1f, RespawnWaveManager.getChaosTickets());
        float total = mtfTokens + chaosTokens;
        float mtfRatio = mtfTokens / total;
        float chaosRatio = chaosTokens / total;

        java.util.Set<UUID> tiedSet = dev.slc.disarm.DisarmerManager.getTiedPlayers();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Role r = getPlayerRole(player.getUUID());
            dev.slc.network.ModNetwork.sendToPlayer(new dev.slc.network.S2CSyncRoundStatePacket(
                    state, roundElapsedTicks, mtfRatio, chaosRatio, r,
                    tiedSet.contains(player.getUUID()), tiedSet, automaticRoundEndLocked, StaminaManager.get(player)), player);
        }
        dev.slc.network.ModNetwork.sendToAll(new dev.slc.network.S2CSyncSpectatorDataPacket(targets));
    }

    private static void assignInitialRoles() {
        if (server == null) return;
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (players.isEmpty()) return;

        Collections.shuffle(players);
        int total = players.size();

        // 1. Assign SCPs
        int scpCount = Math.max(1, total / 5);
        int assigned = 0;

        Role[] scpPool = {Role.SCP_173, Role.SCP_079, Role.SCP_939, Role.SCP_049, Role.SCP_096, Role.SCP_106};
        for (int i = 0; i < scpCount && assigned < total; i++) {
            forceRoleAndSpawn(players.get(assigned++), scpPool[i % scpPool.length]);
        }

        // 2. Assign Guards
        int guardCount = Math.max(1, (total - assigned) / 4);
        for (int i = 0; i < guardCount && assigned < total; i++) {
            forceRoleAndSpawn(players.get(assigned++), Role.FACILITY_GUARD);
        }

        // 3. Assign Scientists
        int remaining = total - assigned;
        int sciCount = Math.max(1, remaining / 3);
        for (int i = 0; i < sciCount && assigned < total; i++) {
            forceRoleAndSpawn(players.get(assigned++), Role.SCIENTIST);
        }

        // 4. Remaining are Class-D
        while (assigned < total) {
            forceRoleAndSpawn(players.get(assigned++), Role.CLASS_D);
        }
    }

    private static void applyRoleToPlayer(ServerPlayer player, Role role) {
        if (player == null || role == null) return;

        if (role != Role.SPECTATOR && (state == RoundState.IN_PROGRESS || state == RoundState.WARHEAD_DETONATING)) {
            // The player's ordinary inventory is retained out of sight for the round. The live cells
            // then become SLC's technical backing, which keeps normal TACZ scanning functional.
            RoundInventoryManager.hideForRound(player);
        }
        if (role.isScp()) {
            // SCPs possess abilities, not player equipment. Clear all visible/technical cells and
            // the dedicated invisible armor cell on every SCP role transition.
            player.getInventory().clearContent();
            player.getInventory().armor.clear();
            player.getInventory().offhand.clear();
            SlcArmorManager.setArmor(player, net.minecraft.world.item.ItemStack.EMPTY);
            player.inventoryMenu.broadcastChanges();
        } else if (player.level() instanceof ServerLevel level) {
            SlcArmorManager.applyRoleLoadout(player, SlcLoadoutSavedData.get(level).getLoadout(role));
        }
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        // Minecraft's MAX_HEALTH attribute has a hard upper bound of 1024. The displayed role
        // profile retains its canonical value while the physical combat attribute stays valid.
        if (maxHealth != null) maxHealth.setBaseValue(Math.max(1.0D, Math.min(1024.0D, role.getMaxHealth())));
        // Do not change health during LivingDeathEvent. The dead player must reach the spectator
        // state normally; living role assignments and reanimations receive their full role HP.
        if (role != Role.SPECTATOR && player.isAlive()) player.setHealth((float) player.getMaxHealth());
        if (role == Role.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        } else {
            player.setGameMode(GameType.SURVIVAL);
            StaminaManager.refill(player);
            if (role == Role.SCP_079) dev.slc.integration.Scp079Bridge.begin(player);
        }
    }

    /** Hard server invariant: every SCP role, including SCP-079, has no player equipment cells. */
    private static void enforceScpInventoryEmpty() {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!getPlayerRole(player.getUUID()).isScp()) continue;
            boolean hasItems = !player.getInventory().items.stream().allMatch(net.minecraft.world.item.ItemStack::isEmpty)
                    || !player.getInventory().armor.stream().allMatch(net.minecraft.world.item.ItemStack::isEmpty)
                    || !player.getInventory().offhand.stream().allMatch(net.minecraft.world.item.ItemStack::isEmpty)
                    || !SlcArmorManager.getArmor(player).isEmpty();
            if (!hasItems) continue;
            player.getInventory().clearContent();
            player.getInventory().armor.clear();
            player.getInventory().offhand.clear();
            SlcArmorManager.setArmor(player, net.minecraft.world.item.ItemStack.EMPTY);
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void endScp079Sessions() {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (getPlayerRole(player.getUUID()) == Role.SCP_079) dev.slc.integration.Scp079Bridge.end(player);
        }
    }

    private static void checkWinConditions() {
        if (automaticRoundEndLocked || server == null || roundElapsedTicks < 200) return;

        int aliveClassD = 0;
        int aliveScientists = 0;
        int aliveFoundation = 0;
        int aliveChaos = 0;
        int aliveScps = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Role r = getPlayerRole(player.getUUID());
            if (player.isAlive() && (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR || r == Role.SCP_079)) {
                switch (r.getTeam()) {
                    case CLASS_D_PERSONNEL -> aliveClassD++;
                    case SCIENTISTS -> aliveScientists++;
                    case FOUNDATION_FORCES -> aliveFoundation++;
                    case CHAOS_INSURGENCY -> aliveChaos++;
                    case SCP_OBJECTS -> aliveScps++;
                    default -> {}
                }
            }
        }

        int aliveFoundationFaction = aliveFoundation + aliveScientists;
        int aliveChaosFaction = aliveChaos + aliveClassD;
        int totalAliveHumans = aliveFoundationFaction + aliveChaosFaction;

        boolean roundShouldEnd = false;

        if (totalAliveHumans == 0) {
            roundShouldEnd = true;
        } else if (aliveFoundationFaction > 0 && aliveChaosFaction == 0 && aliveScps == 0) {
            roundShouldEnd = true;
        } else if (aliveChaosFaction > 0 && aliveFoundationFaction == 0 && aliveScps == 0) {
            roundShouldEnd = true;
        } else if (aliveFoundationFaction == 0 && aliveChaosFaction == 0) {
            roundShouldEnd = true;
        }

        if (!roundShouldEnd) return;

        if (aliveScps > (freeScientistEscapes + freeClassDEscapes) && aliveScps > 0) {
            endRound(Team.SCP_OBJECTS);
        } else if (freeScientistEscapes > freeClassDEscapes) {
            endRound(Team.FOUNDATION_FORCES);
        } else if (freeClassDEscapes > freeScientistEscapes) {
            endRound(Team.CHAOS_INSURGENCY);
        } else {
            endRound(null);
        }
    }
}
