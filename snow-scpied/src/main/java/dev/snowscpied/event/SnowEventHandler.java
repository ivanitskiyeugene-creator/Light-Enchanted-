package dev.snowscpied.event;

import dev.snowscpied.snow.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SnowEventHandler {

    @SubscribeEvent
    public void onEntityStep(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (!level.isClientSide) return;

        Vec3 motion = entity.getDeltaMovement();
        double speedSq = motion.x * motion.x + motion.z * motion.z;

        // Trigger step every 6-8 ticks when moving
        if (speedSq > 0.002 && entity.tickCount % 6 == 0 && entity.onGround()) {
            BlockPos pos = entity.blockPosition();
            BlockPos below = pos.below();

            BlockState s0 = level.getBlockState(pos);
            BlockState s1 = level.getBlockState(below);

            boolean onSnow = s0.is(Blocks.SNOW) || s1.is(Blocks.SNOW) || s1.is(Blocks.SNOW_BLOCK) || s1.is(Blocks.POWDER_SNOW);
            if (onSnow) {
                // Register physical deformation
                SnowDeformationEngine.registerEntityStep(entity);

                // Add footprint decal
                FootprintType type = FootprintType.BOOTS;
                if (entity instanceof Player player && player.getInventory().armor.get(0).isEmpty()) {
                    type = FootprintType.BARE_FEET;
                } else if (!(entity instanceof Player)) {
                    type = FootprintType.SCP_CLAWS;
                }

                boolean isLeft = (entity.tickCount / 6) % 2 == 0;
                float offsetSide = isLeft ? -0.15f : 0.15f;
                float rad = (float) Math.toRadians(entity.getYRot() + 90.0);
                Vec3 printPos = entity.position().add(Math.cos(rad) * offsetSide, 0.0, Math.sin(rad) * offsetSide);

                FootprintManager.addFootprint(printPos, entity.getYRot(), type, isLeft);
            }
        }
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();

        BlockPos pos = entity.blockPosition();
        BlockPos below = pos.below();
        boolean onSnow = level.getBlockState(pos).is(Blocks.SNOW) || level.getBlockState(below).is(Blocks.SNOW) || level.getBlockState(below).is(Blocks.SNOW_BLOCK);

        if (onSnow) {
            BloodType bType = BloodType.HUMAN;
            if (entity instanceof EnderMan) {
                bType = BloodType.ENDER;
            } else if (entity.getName().getString().toLowerCase().contains("scp") || entity.getName().getString().toLowerCase().contains("slime")) {
                bType = BloodType.ACID;
            }

            float damage = event.getAmount();
            int droplets = Math.min(5, Math.max(1, (int) (damage * 0.5f)));

            for (int i = 0; i < droplets; i++) {
                double rx = (Math.random() - 0.5) * 1.2;
                double rz = (Math.random() - 0.5) * 1.2;
                Vec3 stainPos = entity.position().add(rx, 0.0, rz);
                BloodStainManager.addBloodStain(stainPos, bType, 0.35f + (float) Math.random() * 0.3f);
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            boolean isSnowing = false;
            // Decay footprints and blood stains
            FootprintManager.tick(isSnowing);
            BloodStainManager.tick(isSnowing);
        }
    }
}
