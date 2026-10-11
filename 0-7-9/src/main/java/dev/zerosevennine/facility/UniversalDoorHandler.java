package dev.zerosevennine.facility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.List;

public class UniversalDoorHandler {

    public static ItemStack getO5KeycardStack() {
        ResourceLocation[] candidates = new ResourceLocation[]{
            new ResourceLocation("scpfr", "o_5_keycard"),
            new ResourceLocation("scpfr", "keycard_o5"),
            new ResourceLocation("scpfr", "keycard_level_5"),
            new ResourceLocation("scpfr", "o5_keycard"),
            new ResourceLocation("scpfr", "omni_keycard"),
            new ResourceLocation("scpfr", "keycard_omni"),
            new ResourceLocation("scpthefacility", "o5_keycard"),
            new ResourceLocation("scpthefacility", "keycard_o5"),
            new ResourceLocation("scpthefacility", "omni_keycard"),
            new ResourceLocation("thefacility", "o5_keycard")
        };

        for (ResourceLocation loc : candidates) {
            if (ForgeRegistries.ITEMS.containsKey(loc)) {
                Item item = ForgeRegistries.ITEMS.getValue(loc);
                if (item != null) {
                    return new ItemStack(item);
                }
            }
        }

        for (var entry : ForgeRegistries.ITEMS.getEntries()) {
            String path = entry.getKey().location().getPath().toLowerCase();
            String ns = entry.getKey().location().getNamespace().toLowerCase();
            if ((ns.contains("scp") || ns.contains("facility")) &&
                (path.contains("o_5") || path.contains("o5") || path.contains("omni") || path.contains("level_5") || path.contains("level5"))) {
                return new ItemStack(entry.getValue());
            }
        }

        return ItemStack.EMPTY;
    }

    public static boolean isReaderOrScannerBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key != null) {
            String path = key.getPath().toLowerCase();
            String ns = key.getNamespace().toLowerCase();
            if (path.contains("reader") || path.contains("scanner") || path.contains("keycard") ||
                path.contains("keypad") || path.contains("swipe") || path.contains("card_reader")) {
                return true;
            }
            if ((ns.contains("scp") || ns.contains("facility")) && (path.contains("keycard_reader") || path.contains("reader"))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDoorLikeBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        var block = state.getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock ||
            block instanceof ButtonBlock || block instanceof LeverBlock) {
            return true;
        }

        var key = ForgeRegistries.BLOCKS.getKey(block);
        if (key != null) {
            String path = key.getPath().toLowerCase();
            String ns = key.getNamespace().toLowerCase();
            if (path.contains("door") || path.contains("gate") || path.contains("blast") ||
                path.contains("airlock") || path.contains("checkpoint") || path.contains("cell") ||
                path.contains("shutter") || path.contains("hatch") || path.contains("panel") ||
                path.contains("reader") || path.contains("button") || path.contains("keycard") ||
                path.contains("keypad") || path.contains("scanner") || path.contains("sliding") ||
                path.contains("bulkhead") || path.contains("containment") || path.contains("decontamination") ||
                path.contains("iris") || path.contains("vault")) {
                return true;
            }
            if (ns.contains("scp") || ns.contains("facility") || ns.contains("foundation") ||
                ns.contains("scpthefacility") || ns.contains("thefacility")) {
                if (path.contains("elevator") || path.contains("transit") || path.contains("intercom") ||
                    path.contains("speaker") || path.contains("lock") || path.contains("terminal")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isTeslaGateBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        var block = state.getBlock();
        var key = ForgeRegistries.BLOCKS.getKey(block);
        if (key != null) {
            String path = key.getPath().toLowerCase();
            String ns = key.getNamespace().toLowerCase();
            if (path.contains("tesla") || path.contains("shock_gate") || path.contains("electric_gate") ||
                path.contains("shock_barrier") || path.contains("tesla_gate") || path.contains("tesla_coil")) {
                return true;
            }
            if ((ns.contains("scp") || ns.contains("facility")) && path.contains("tesla")) {
                return true;
            }
        }
        String className = block.getClass().getSimpleName().toLowerCase();
        return className.contains("tesla");
    }

    public static void triggerTeslaGate(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null) return;

        BlockPos centerPos = pos;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(p);
                    if (isTeslaGateBlock(st)) {
                        centerPos = p;
                        break;
                    }
                }
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            // Emulate player presence using invisible ArmorStand to trigger SCP: The Facility / SCPFR detection hitbox
            ArmorStand dummy = new ArmorStand(serverLevel, centerPos.getX() + 0.5, centerPos.getY() + 0.1, centerPos.getZ() + 0.5);
            dummy.setInvisible(true);
            dummy.setNoGravity(true);
            dummy.setInvulnerable(true);
            dummy.setSilent(true);
            dummy.setSmall(false);
            dummy.addTag("scp079_tesla_dummy");
            serverLevel.addFreshEntity(dummy);

            // Also invoke direct BlockEntity methods if available
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos p = centerPos.offset(dx, dy, dz);
                        BlockEntity be = serverLevel.getBlockEntity(p);
                        if (be != null) {
                            invokeMethod(be, "trigger");
                            invokeMethod(be, "overcharge");
                            invokeMethod(be, "discharge");
                            invokeMethod(be, "activate");
                            invokeMethod(be, "shock");
                            invokeMethod(be, "onEntityInside", Entity.class, dummy);
                            invokeMethod(be, "onEntityCollision", Entity.class, dummy);
                        }
                        BlockState bs = serverLevel.getBlockState(p);
                        BlockState toggled = setBooleanProperty(bs, "active", true);
                        if (toggled != bs) serverLevel.setBlock(p, toggled, 3);
                    }
                }
            }

            // Audio & Particle effects
            serverLevel.playSound(null, centerPos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.2f, 1.4f);
            serverLevel.playSound(null, centerPos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 0.5f);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, centerPos.getX() + 0.5, centerPos.getY() + 1.2, centerPos.getZ() + 0.5, 45, 1.2, 1.2, 1.2, 0.2);
            serverLevel.sendParticles(ParticleTypes.FLASH, centerPos.getX() + 0.5, centerPos.getY() + 1.2, centerPos.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0.0);

            // Area shock damage to living entities inside Tesla Gate
            AABB shockBox = new AABB(centerPos).inflate(3.5, 2.5, 3.5);
            List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, shockBox, e -> e != player && e != dummy && !e.isSpectator());
            for (LivingEntity target : targets) {
                target.hurt(serverLevel.damageSources().lightningBolt(), 100.0f);
            }

            dummy.discard();
        }
    }

    public static boolean toggleDoorState(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);

        ItemStack prevHand = (player != null) ? player.getItemInHand(InteractionHand.MAIN_HAND) : ItemStack.EMPTY;
        ItemStack o5Card = getO5KeycardStack();
        if (player != null && !o5Card.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, o5Card);
        }

        try {
            boolean scannerTriggered = false;
            for (int dy = -1; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos p = pos.offset(dx, dy, dz);
                        BlockState s = level.getBlockState(p);
                        if (isReaderOrScannerBlock(s) || (dx == 0 && dy == 0 && dz == 0)) {
                            if (player != null) {
                                BlockHitResult bhr = new BlockHitResult(new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), Direction.UP, p, false);
                                InteractionResult res = s.use(level, player, InteractionHand.MAIN_HAND, bhr);
                                if (res.consumesAction()) {
                                    scannerTriggered = true;
                                    notifyNeighbors(level, p);
                                }
                            }
                            BlockEntity be = level.getBlockEntity(p);
                            if (be != null) {
                                if (invokeMethod(be, "scanCard", Player.class, player)) scannerTriggered = true;
                                if (invokeMethod(be, "onKeycardUsed", Player.class, player)) scannerTriggered = true;
                                if (invokeMethod(be, "grantAccess", Player.class, player)) scannerTriggered = true;
                                if (invokeMethod(be, "grantAccess")) scannerTriggered = true;
                                if (invokeMethod(be, "processCard", ItemStack.class, o5Card)) scannerTriggered = true;
                                if (invokeMethod(be, "validateKeycard", ItemStack.class, o5Card)) scannerTriggered = true;
                                if (invokeMethod(be, "trigger")) scannerTriggered = true;
                                if (invokeMethod(be, "activate")) scannerTriggered = true;
                            }
                        }
                    }
                }
            }

            if (scannerTriggered) {
                notifyNeighbors(level, pos);
                return true;
            }

            // 1. Vanilla & Standard DoorBlock
            if (state.getBlock() instanceof DoorBlock) {
                BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
                BlockPos upperPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
                BlockState lowerState = level.getBlockState(lowerPos);
                BlockState upperState = level.getBlockState(upperPos);

                boolean wasOpen = lowerState.hasProperty(DoorBlock.OPEN) && lowerState.getValue(DoorBlock.OPEN);
                boolean newState = !wasOpen;

                if (lowerState.getBlock() instanceof DoorBlock) {
                    level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, newState), 3);
                }
                if (upperState.getBlock() instanceof DoorBlock) {
                    level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, newState), 3);
                }
                level.playSound(null, pos, newState ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
                notifyNeighbors(level, lowerPos);
                notifyNeighbors(level, upperPos);
                return true;
            }

            // 2. Vanilla TrapDoor
            if (state.getBlock() instanceof TrapDoorBlock) {
                boolean wasOpen = state.getValue(TrapDoorBlock.OPEN);
                boolean newState = !wasOpen;
                level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, newState), 3);
                level.playSound(null, pos, newState ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
                notifyNeighbors(level, pos);
                return true;
            }

            // 3. FenceGate
            if (state.getBlock() instanceof FenceGateBlock) {
                boolean wasOpen = state.getValue(FenceGateBlock.OPEN);
                boolean newState = !wasOpen;
                level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, newState), 3);
                level.playSound(null, pos, newState ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
                notifyNeighbors(level, pos);
                return true;
            }

            // 4. Button / Lever
            if (state.getBlock() instanceof ButtonBlock button) {
                button.press(state, level, pos);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0f, 1.0f);
                notifyNeighbors(level, pos);
                return true;
            }
            if (state.getBlock() instanceof LeverBlock) {
                boolean wasPowered = state.getValue(LeverBlock.POWERED);
                level.setBlock(pos, state.setValue(LeverBlock.POWERED, !wasPowered), 3);
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0f, wasPowered ? 0.5f : 0.6f);
                notifyNeighbors(level, pos);
                return true;
            }

            // 5. SCP: The Facility / Custom Mod Doors / Multi-Block Entities
            boolean handled = triggerModdedDoorToggle(level, pos, player);
            if (handled) {
                notifyNeighbors(level, pos);
                return true;
            }

            // 6. Universal Simulated Right-Click (BlockState.use)
            if (player != null) {
                BlockHitResult bhr = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, false);
                InteractionResult res = state.use(level, player, InteractionHand.MAIN_HAND, bhr);
                if (res.consumesAction()) {
                    notifyNeighbors(level, pos);
                    return true;
                }
            }

            // 7. Universal BlockState Property Manipulation
            BlockState toggled = toggleBooleanProperty(state, "open");
            if (toggled != state) {
                level.setBlock(pos, toggled, 3);
                notifyNeighbors(level, pos);
                return true;
            }

            notifyNeighbors(level, pos);
            return true;
        } finally {
            if (player != null) {
                player.setItemInHand(InteractionHand.MAIN_HAND, prevHand);
            }
        }
    }

    public static void setAreaLockState(Level level, BlockPos centerPos, boolean lock, Player player) {
        if (level == null || centerPos == null) return;

        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = centerPos.offset(dx, dy, dz);
                    swapButtonLockState(level, p, lock);
                }
            }
        }

        if (lock) {
            closeDoor(level, centerPos, player);
        }
        notifyNeighbors(level, centerPos);
    }

    private static void swapButtonLockState(Level level, BlockPos pos, boolean lock) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        BlockState withLockedProp = setBooleanProperty(state, "locked", lock);
        if (withLockedProp != state) {
            level.setBlock(pos, withLockedProp, 3);
            notifyNeighbors(level, pos);
            return;
        }

        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null) return;

        String ns = key.getNamespace();
        String path = key.getPath();

        ResourceLocation targetKey = null;
        if (lock) {
            if (!path.contains("locked")) {
                ResourceLocation try1 = new ResourceLocation(ns, path + "_locked");
                if (ForgeRegistries.BLOCKS.containsKey(try1)) {
                    targetKey = try1;
                } else {
                    ResourceLocation try2 = new ResourceLocation(ns, "locked_" + path);
                    if (ForgeRegistries.BLOCKS.containsKey(try2)) {
                        targetKey = try2;
                    }
                }
            }
        } else {
            if (path.endsWith("_locked")) {
                String base = path.substring(0, path.length() - "_locked".length());
                ResourceLocation try1 = new ResourceLocation(ns, base);
                if (ForgeRegistries.BLOCKS.containsKey(try1)) {
                    targetKey = try1;
                }
            } else if (path.startsWith("locked_")) {
                String base = path.substring("locked_".length());
                ResourceLocation try1 = new ResourceLocation(ns, base);
                if (ForgeRegistries.BLOCKS.containsKey(try1)) {
                    targetKey = try1;
                }
            }
        }

        if (targetKey != null) {
            Block newBlock = ForgeRegistries.BLOCKS.getValue(targetKey);
            if (newBlock != null) {
                BlockState newState = newBlock.defaultBlockState();
                for (Property<?> prop : state.getProperties()) {
                    newState = copyProperty(state, newState, prop);
                }
                level.setBlock(pos, newState, 3);
                notifyNeighbors(level, pos);
            }
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            if (lock) {
                invokeMethod(be, "lock");
                invokeMethod(be, "setLocked", boolean.class, true);
                invokeMethod(be, "setIsLocked", boolean.class, true);
            } else {
                invokeMethod(be, "unlock");
                invokeMethod(be, "setLocked", boolean.class, false);
                invokeMethod(be, "setIsLocked", boolean.class, false);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> prop) {
        if (to.hasProperty(prop)) {
            return to.setValue(prop, from.getValue(prop));
        }
        return to;
    }

    public static boolean closeDoor(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof DoorBlock) {
            BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            BlockPos upperPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
            BlockState lowerState = level.getBlockState(lowerPos);
            BlockState upperState = level.getBlockState(upperPos);

            if (lowerState.getBlock() instanceof DoorBlock) {
                level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, false), 3);
            }
            if (upperState.getBlock() instanceof DoorBlock) {
                level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, false), 3);
            }
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, lowerPos);
            notifyNeighbors(level, upperPos);
            return true;
        }

        if (state.getBlock() instanceof TrapDoorBlock) {
            level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, false), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        if (state.getBlock() instanceof FenceGateBlock) {
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, false), 3);
            level.playSound(null, pos, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        triggerModdedDoorState(level, pos, player, false);
        BlockState modified = setBooleanProperty(state, "open", false);
        if (modified != state) {
            level.setBlock(pos, modified, 3);
        }
        notifyNeighbors(level, pos);
        return true;
    }

    public static boolean openDoor(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof DoorBlock) {
            BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            BlockPos upperPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
            BlockState lowerState = level.getBlockState(lowerPos);
            BlockState upperState = level.getBlockState(upperPos);

            if (lowerState.getBlock() instanceof DoorBlock) {
                level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, true), 3);
            }
            if (upperState.getBlock() instanceof DoorBlock) {
                level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, true), 3);
            }
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, lowerPos);
            notifyNeighbors(level, upperPos);
            return true;
        }

        if (state.getBlock() instanceof TrapDoorBlock) {
            level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        if (state.getBlock() instanceof FenceGateBlock) {
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 3);
            level.playSound(null, pos, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        triggerModdedDoorState(level, pos, player, true);
        BlockState modified = setBooleanProperty(state, "open", true);
        if (modified != state) {
            level.setBlock(pos, modified, 3);
        }
        notifyNeighbors(level, pos);
        return true;
    }

    private static boolean triggerModdedDoorToggle(Level level, BlockPos pos, Player player) {
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) {
                        if (invokeMethod(be, "toggle")) return true;
                        if (invokeMethod(be, "toggleDoor")) return true;
                        if (invokeMethod(be, "openDoor")) return true;
                        if (invokeMethod(be, "interact", Player.class, player)) return true;
                        if (invokeMethod(be, "onInteract", Player.class, player)) return true;
                        if (invokeMethod(be, "trigger")) return true;
                        if (invokeMethod(be, "activate")) return true;
                        if (invokeMethod(be, "use", Player.class, player)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean triggerModdedDoorState(Level level, BlockPos pos, Player player, boolean open) {
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) {
                        if (invokeMethod(be, open ? "open" : "close")) return true;
                        if (invokeMethod(be, open ? "openDoor" : "closeDoor")) return true;
                        if (invokeMethod(be, "setOpen", boolean.class, open)) return true;
                        if (invokeMethod(be, "setOpened", boolean.class, open)) return true;
                        if (invokeMethod(be, "setPowered", boolean.class, open)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean invokeMethod(Object target, String name) {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equalsIgnoreCase(name) && m.getParameterCount() == 0) {
                    m.invoke(target);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean invokeMethod(Object target, String name, Class<?> paramType, Object arg) {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equalsIgnoreCase(name) && m.getParameterCount() == 1) {
                    if (m.getParameterTypes()[0].isAssignableFrom(paramType) ||
                       (paramType == boolean.class && m.getParameterTypes()[0] == Boolean.TYPE)) {
                        m.invoke(target, arg);
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static BlockState setBooleanProperty(BlockState state, String propertyNamePrefix, boolean value) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof BooleanProperty bp) {
                if (bp.getName().toLowerCase().startsWith(propertyNamePrefix.toLowerCase()) ||
                    bp.getName().toLowerCase().contains(propertyNamePrefix.toLowerCase())) {
                    try {
                        return state.setValue(bp, value);
                    } catch (Exception ignored) {}
                }
            }
        }
        return state;
    }

    private static BlockState toggleBooleanProperty(BlockState state, String propertyNamePrefix) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof BooleanProperty bp) {
                if (bp.getName().toLowerCase().startsWith(propertyNamePrefix.toLowerCase()) ||
                    bp.getName().toLowerCase().contains(propertyNamePrefix.toLowerCase())) {
                    try {
                        return state.cycle(bp);
                    } catch (Exception ignored) {}
                }
            }
        }
        return state;
    }

    public static void notifyNeighbors(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        BlockState state = level.getBlockState(pos);
        level.updateNeighborsAt(pos, state.getBlock());
        for (Direction dir : Direction.values()) {
            level.updateNeighborsAt(pos.relative(dir), state.getBlock());
        }
    }
}
