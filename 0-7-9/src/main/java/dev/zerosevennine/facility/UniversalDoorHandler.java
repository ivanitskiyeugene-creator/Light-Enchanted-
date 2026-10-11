package dev.zerosevennine.facility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

public class UniversalDoorHandler {

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
                path.contains("arc_gate")) {
                return true;
            }
            if ((ns.contains("scp") || ns.contains("facility")) && path.contains("tesla")) {
                return true;
            }
        }
        String className = block.getClass().getSimpleName().toLowerCase();
        return className.contains("tesla");
    }

    public static boolean triggerTeslaGate(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null) return false;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) {
                        if (invokeMethod(be, "discharge")) return true;
                        if (invokeMethod(be, "shock")) return true;
                        if (invokeMethod(be, "overcharge")) return true;
                        if (invokeMethod(be, "trigger")) return true;
                        if (invokeMethod(be, "activate")) return true;
                        if (invokeMethod(be, "setPowered", boolean.class, true)) return true;
                    }
                }
            }
        }
        notifyNeighbors(level, pos);
        return true;
    }

    public static boolean toggleDoor(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);

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
    }

    public static void setAreaLockState(Level level, BlockPos centerPos, boolean lock, Player player) {
        if (level == null || centerPos == null) return;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
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

    private static boolean triggerModdedDoorToggle(Level level, BlockPos pos, Player player) {
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) {
                        if (invokeMethod(be, "toggle")) return true;
                        if (invokeMethod(be, "toggleDoor")) return true;
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
