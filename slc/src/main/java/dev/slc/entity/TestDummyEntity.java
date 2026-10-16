package dev.slc.entity;

import dev.slc.admin.AdminPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Static, restoring and AI test dummies share the same clear test-only behavior class but distinct entity types. */
public final class TestDummyEntity extends PathfinderMob {
    public enum Kind { STATIC, RESPAWN, AI }
    public enum AiMode { PATROL, COMBAT }

    private final Kind kind;
    private AiMode aiMode = AiMode.PATROL;
    private BlockPos home = BlockPos.ZERO;
    private int restoreTicks;
    private int actionTicks;

    public TestDummyEntity(EntityType<? extends PathfinderMob> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.xpReward = 0;
        if (kind != Kind.AI) setNoAi(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    public void setAiMode(AiMode mode) { this.aiMode = mode; }

    @Override
    public void tick() {
        super.tick();
        if (home.equals(BlockPos.ZERO)) home = blockPosition();
        if (kind == Kind.RESPAWN && restoreTicks > 0) {
            restoreTicks--;
            setInvulnerable(restoreTicks > 0);
            return;
        }
        if (kind != Kind.AI || level().isClientSide) return;
        actionTicks++;
        if (aiMode == AiMode.PATROL) {
            if (actionTicks % 100 == 0 || getNavigation().isDone()) {
                double x = home.getX() + 0.5D + (getRandom().nextDouble() - .5D) * 8D;
                double z = home.getZ() + 0.5D + (getRandom().nextDouble() - .5D) * 8D;
                getNavigation().moveTo(x, home.getY(), z, 0.8D);
            }
        } else {
            Player target = level().getNearestPlayer(this, 12D);
            if (target != null && !target.isSpectator()) {
                double distance = distanceToSqr(target);
                if (distance > 3D) getNavigation().moveTo(target, 1.05D);
                else if (actionTicks % 20 == 0) target.hurt(damageSources().mobAttack(this), (float)getAttributeValue(Attributes.ATTACK_DAMAGE));
            }
        }
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (kind == Kind.RESPAWN) {
            setHealth(getMaxHealth());
            setPos(home.getX() + .5D, home.getY(), home.getZ() + .5D);
            setDeltaMovement(Vec3.ZERO);
            restoreTicks = 100;
            setInvulnerable(true);
            return;
        }
        super.die(source);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && kind == Kind.AI && player instanceof ServerPlayer serverPlayer
                && AdminPermission.isAdmin(serverPlayer) && player.isShiftKeyDown()) {
            aiMode = aiMode == AiMode.PATROL ? AiMode.COMBAT : AiMode.PATROL;
            level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.NEUTRAL, .6F,
                    aiMode == AiMode.COMBAT ? .7F : 1.35F);
            return InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("AiMode", aiMode.name());
        tag.putLong("Home", home.asLong());
        tag.putInt("RestoreTicks", restoreTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try { aiMode = AiMode.valueOf(tag.getString("AiMode")); } catch (IllegalArgumentException ignored) { }
        if (tag.contains("Home")) home = BlockPos.of(tag.getLong("Home"));
        restoreTicks = tag.getInt("RestoreTicks");
    }
}
