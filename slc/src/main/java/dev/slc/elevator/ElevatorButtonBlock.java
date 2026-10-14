package dev.slc.elevator;

import dev.slc.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ElevatorButtonBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // Voxel shapes attached to walls (3 to 13 on X/Z, 2 to 14 on Y, 2 pixels thick)
    private static final VoxelShape SHAPE_NORTH = Block.box(3.0, 2.0, 14.0, 13.0, 14.0, 16.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(3.0, 2.0, 0.0, 13.0, 14.0, 2.0);
    private static final VoxelShape SHAPE_WEST  = Block.box(14.0, 2.0, 3.0, 16.0, 14.0, 13.0);
    private static final VoxelShape SHAPE_EAST  = Block.box(0.0, 2.0, 3.0, 2.0, 14.0, 13.0);

    public ElevatorButtonBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        if (clicked.getAxis().isHorizontal()) {
            return this.defaultBlockState().setValue(FACING, clicked.getOpposite());
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST  -> SHAPE_WEST;
            case EAST  -> SHAPE_EAST;
            default    -> SHAPE_NORTH;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ElevatorButtonBlockEntity elevator) {
            if (elevator.getLinkedPos() == null) {
                player.displayClientMessage(Component.literal("§c[Лифт] Эта кнопка не привязана к целевому лифту! Используйте Elevator Linker."), true);
                return InteractionResult.CONSUME;
            }

            boolean triggered = elevator.triggerElevator();
            if (triggered) {
                player.displayClientMessage(Component.literal("§a[Лифт] Лифт вызван! Двери закрываются..."), true);
            } else {
                player.displayClientMessage(Component.literal("§e[Лифт] Лифт уже в пути или занят."), true);
            }
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ElevatorButtonBlockEntity elevator) {
            return elevator.isEmittingRedstone() ? 15 : 0;
        }
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElevatorButtonBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, p, st, be) -> {
            if (be instanceof ElevatorButtonBlockEntity elevator) {
                ElevatorButtonBlockEntity.serverTick(lvl, p, st, elevator);
            }
        };
    }
}
