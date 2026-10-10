package dev.zerosevennine.system;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.facility.FacilityNetworkManager;
import dev.zerosevennine.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Scp079Session {
    public static class LogEntry {
        public String message;
        public int colorRgb;
        public long tickCreated;

        public LogEntry(String message, int colorRgb, long tickCreated) {
            this.message = message;
            this.colorRgb = colorRgb;
            this.tickCreated = tickCreated;
        }
    }

    private final UUID playerUuid;
    private BlockPos currentCameraPos;

    private int tier = 1;
    private int exp = 0;
    private float ap = 100.0f;
    private float maxAp = 100.0f;
    private float apRegen = 3.2f;
    private boolean breachScannerActive = false;

    private final List<LogEntry> systemLog = new ArrayList<>();
    private int syncCooldown = 0;

    public Scp079Session(UUID playerUuid, BlockPos startingCameraPos) {
        this.playerUuid = playerUuid;
        this.currentCameraPos = startingCameraPos;
        recalcTierStats();
        addLog("0-7-9 SYSTEM INITIALIZED", 0x00E5FF, 0);
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public BlockPos getCurrentCameraPos() {
        return currentCameraPos;
    }

    public void setCurrentCameraPos(BlockPos pos) {
        this.currentCameraPos = pos;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = Math.max(1, Math.min(5, tier));
        recalcTierStats();
    }

    public int getExp() {
        return exp;
    }

    public int getExpForNextTier() {
        return switch (tier) {
            case 1 -> 100;
            case 2 -> 250;
            case 3 -> 500;
            case 4 -> 1000;
            default -> 9999;
        };
    }

    public float getAp() {
        return ap;
    }

    public void setAp(float ap) {
        this.ap = Math.max(0.0f, Math.min(maxAp, ap));
    }

    public float getMaxAp() {
        return maxAp;
    }

    public float getApRegen() {
        return breachScannerActive ? (apRegen * 0.5f) : apRegen;
    }

    public float getBaseApRegen() {
        return apRegen;
    }

    public boolean isBreachScannerActive() {
        return breachScannerActive;
    }

    public void toggleBreachScanner(ServerPlayer player) {
        if (tier < 4) return;
        this.breachScannerActive = !this.breachScannerActive;
        if (breachScannerActive) {
            addLog("BREACH SCANNER ONLINE (-50% AP REGEN)", 0xF39C12, player.level().getGameTime());
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.8f);
        } else {
            addLog("BREACH SCANNER OFFLINE (AP REGEN RESTORED)", 0x2ECC71, player.level().getGameTime());
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        }
    }

    public List<LogEntry> getSystemLog() {
        return systemLog;
    }

    public void addLog(String msg, int colorRgb, long gameTime) {
        systemLog.add(new LogEntry(msg, colorRgb, gameTime));
        if (systemLog.size() > 8) {
            systemLog.remove(0);
        }
    }

    public boolean spendAp(float amount) {
        if (ap >= amount) {
            ap -= amount;
            return true;
        }
        return false;
    }

    public void addExp(int amount, ServerPlayer player) {
        this.exp += amount;
        addLog("+" + amount + " EXP", 0x2ECC71, player.level().getGameTime());

        int req = getExpForNextTier();
        if (this.exp >= req && this.tier < 5) {
            this.exp -= req;
            this.tier++;
            recalcTierStats();
            addLog("ACCESS TIER UPGRADED TO LEVEL " + this.tier, 0x00E5FF, player.level().getGameTime());
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }

    private void recalcTierStats() {
        switch (tier) {
            case 1 -> { maxAp = 100.0f; apRegen = 3.2f; }
            case 2 -> { maxAp = 125.0f; apRegen = 4.0f; }
            case 3 -> { maxAp = 150.0f; apRegen = 4.8f; }
            case 4 -> { maxAp = 175.0f; apRegen = 5.6f; }
            case 5 -> { maxAp = 200.0f; apRegen = 6.8f; }
        }
    }

    public void tick(ServerPlayer player) {
        // Regenerate AP (20 ticks per second, -50% if breach scanner is active)
        float currentRegen = getApRegen();
        ap = Math.min(maxAp, ap + (currentRegen / 20.0f));

        syncCooldown++;
        if (syncCooldown >= 4) {
            syncCooldown = 0;
            ModNetwork.send079StateToClient(player, this);
        }
    }
}
