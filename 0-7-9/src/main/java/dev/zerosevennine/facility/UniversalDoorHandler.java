package dev.zerosevennine.facility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
                path.contains("reader") || path.contains("button") || path.contains("keycard")) {
                return true;
            }
            if (ns.contains("scp") || ns.contains("facility")) {
                if (path.contains("elevator") || path.contains("transit")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean openDoor(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);

        // 1. Vanilla & Standard DoorBlock
        if (state.getBlock() instanceof DoorBlock) {
            BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            BlockPos upperPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
            BlockState lowerState = level.getBlockState(lowerPos);
            BlockState upperState = level.getBlockState(upperPos);

            if (lowerState.getBlock() instanceof DoorBlock) {
                level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, true).setValue(DoorBlock.POWERED, true), 3);
            }
            if (upperState.getBlock() instanceof DoorBlock) {
                level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, true).setValue(DoorBlock.POWERED, true), 3);
            }
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, lowerPos);
            notifyNeighbors(level, upperPos);
            return true;
        }

        // 2. Vanilla TrapDoor
        if (state.getBlock() instanceof TrapDoorBlock) {
            level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.POWERED, true), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        // 3. FenceGate
        if (state.getBlock() instanceof FenceGateBlock) {
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true).setValue(FenceGateBlock.POWERED, true), 3);
            level.playSound(null, pos, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        // 4. Button / Lever
        if (state.getBlock() instanceof ButtonBlock button) {
            button.press(state, level, pos);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0f, 1.0f);
            return true;
        }
        if (state.getBlock() instanceof LeverBlock) {
            level.setBlock(pos, state.setValue(LeverBlock.POWERED, true), 3);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0f, 0.6f);
            notifyNeighbors(level, pos);
            return true;
        }

        // 5. SCP:FR and Modded Custom DoorBlock / Multi-Block Entities
        boolean handled = triggerModdedDoor(level, pos, player, true);
        if (handled) {
            notifyNeighbors(level, pos);
            return true;
        }

        // 6. Universal BlockState Property Manipulation
        BlockState modified = setBooleanProperty(state, "open", true);
        modified = setBooleanProperty(modified, "powered", true);
        if (modified != state) {
            level.setBlock(pos, modified, 3);
            notifyNeighbors(level, pos);
            return true;
        }

        // 7. Universal Simulated Right-Click (BlockState.use)
        if (player != null) {
            BlockHitResult bhr = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, false);
            InteractionResult res = state.use(level, player, InteractionHand.MAIN_HAND, bhr);
            if (res.consumesAction()) {
                notifyNeighbors(level, pos);
                return true;
            }
        }

        notifyNeighbors(level, pos);
        return true;
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
                level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, false).setValue(DoorBlock.POWERED, false), 3);
            }
            if (upperState.getBlock() instanceof DoorBlock) {
                level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, false).setValue(DoorBlock.POWERED, false), 3);
            }
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, lowerPos);
            notifyNeighbors(level, upperPos);
            return true;
        }

        if (state.getBlock() instanceof TrapDoorBlock) {
            level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, false).setValue(TrapDoorBlock.POWERED, false), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        if (state.getBlock() instanceof FenceGateBlock) {
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, false).setValue(FenceGateBlock.POWERED, false), 3);
            level.playSound(null, pos, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        if (state.getBlock() instanceof LeverBlock) {
            level.setBlock(pos, state.setValue(LeverBlock.POWERED, false), 3);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0f, 0.5f);
            notifyNeighbors(level, pos);
            return true;
        }

        // SCP:FR and Modded Custom DoorBlock
        boolean handled = triggerModdedDoor(level, pos, player, false);
        if (handled) {
            notifyNeighbors(level, pos);
            return true;
        }

        BlockState modified = setBooleanProperty(state, "open", false);
        modified = setBooleanProperty(modified, "powered", false);
        if (modified != state) {
            level.setBlock(pos, modified, 3);
            notifyNeighbors(level, pos);
            return true;
        }

        notifyNeighbors(level, pos);
        return true;
    }

    private static boolean triggerModdedDoor(Level level, BlockPos pos, Player player, boolean open) {
        // Search center and surrounding multi-block parts (for 2x2, 3x3 doors)
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
                        if (invokeMethod(be, "toggle")) return true;
                        if (invokeMethod(be, "toggleDoor")) return true;
                        if (invokeMethod(be, "trigger")) return true;
                        if (invokeMethod(be, "interact", Player.class, player)) return true;
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

    public static void notifyNeighbors(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        BlockState state = level.getBlockState(pos);
        level.updateNeighborsAt(pos, state.getBlock());
        for (Direction dir : Direction.values()) {
            level.updateNeighborsAt(pos.relative(dir), state.getBlock());
        }
    }
}
