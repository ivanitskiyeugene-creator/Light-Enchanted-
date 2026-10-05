package dev.lightenchanted.block;

import dev.lightenchanted.blockentity.IndustrialFanBlockEntity;
import dev.lightenchanted.init.ModBlockEntities;
import dev.lightenchanted.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * SCP:SL Heavy Containment Zone (HCZ) 3x3 Industrial Ventilation Fan (Master Block).
 */
public class IndustrialFanBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public IndustrialFanBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();

        // Check if 3x3 area is clear
        if (!canPlace3x3(level, center, facing)) {
            return null;
        }

        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            placeSlaves(level, pos, state.getValue(FACING));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                removeSlaves(level, pos, state.getValue(FACING));
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    public static boolean canPlace3x3(Level level, BlockPos center, Direction facing) {
        Direction.Axis axis = facing.getAxis();
        Direction right = (axis == Direction.Axis.Y) ? Direction.EAST : facing.getClockWise();
        Direction up = (axis == Direction.Axis.Y) ? Direction.NORTH : Direction.UP;

        for (int r = -1; r <= 1; r++) {
            for (int u = -1; u <= 1; u++) {
                if (r == 0 && u == 0) continue;
                BlockPos p = center.relative(right, r).relative(up, u);
                if (!level.getBlockState(p).canBeReplaced()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void placeSlaves(Level level, BlockPos center, Direction facing) {
        Direction.Axis axis = facing.getAxis();
        Direction right = (axis == Direction.Axis.Y) ? Direction.EAST : facing.getClockWise();
        Direction up = (axis == Direction.Axis.Y) ? Direction.NORTH : Direction.UP;

        for (int r = -1; r <= 1; r++) {
            for (int u = -1; u <= 1; u++) {
                if (r == 0 && u == 0) continue;
                BlockPos p = center.relative(right, r).relative(up, u);
                level.setBlock(p, ModBlocks.INDUSTRIAL_FAN_SLAVE.get().defaultBlockState()
                        .setValue(IndustrialFanSlaveBlock.FACING, facing), Block.UPDATE_ALL);
                if (level.getBlockEntity(p) instanceof IndustrialFanBlockEntity slaveBe) {
                    slaveBe.setMasterPos(center);
                }
            }
        }
    }

    public static void removeSlaves(Level level, BlockPos center, Direction facing) {
        Direction.Axis axis = facing.getAxis();
        Direction right = (axis == Direction.Axis.Y) ? Direction.EAST : facing.getClockWise();
        Direction up = (axis == Direction.Axis.Y) ? Direction.NORTH : Direction.UP;

        for (int r = -1; r <= 1; r++) {
            for (int u = -1; u <= 1; u++) {
                if (r == 0 && u == 0) continue;
                BlockPos p = center.relative(right, r).relative(up, u);
                if (level.getBlockState(p).is(ModBlocks.INDUSTRIAL_FAN_SLAVE.get())) {
                    level.removeBlock(p, false);
                }
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof IndustrialFanBlockEntity fan) {
            if (!level.isClientSide) {
                fan.cycleSpeedMode();
                float pitch = switch (fan.getSpeedMode()) {
                    case 1 -> 0.8f;
                    case 2 -> 1.0f;
                    case 3 -> 1.3f;
                    default -> 0.6f;
                };
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, pitch);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof IndustrialFanBlockEntity fan) {
            boolean powered = level.hasNeighborSignal(pos);
            fan.setRedstonePowered(powered);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // Pass-through visual shape for god rays!
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IndustrialFanBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.INDUSTRIAL_FAN.get(),
                level.isClientSide ? IndustrialFanBlockEntity::clientTick : IndustrialFanBlockEntity::serverTick);
    }
}
