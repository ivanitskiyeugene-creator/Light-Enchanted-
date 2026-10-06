package dev.lightenchanted.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class OpticalMirrorBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty ANGLE = IntegerProperty.create("angle", 0, 7); // 8 steps of 45 deg

    protected static final VoxelShape SHAPE_DOWN = Block.box(1, 0, 1, 15, 6, 15);
    protected static final VoxelShape SHAPE_UP = Block.box(1, 10, 1, 15, 16, 15);
    protected static final VoxelShape SHAPE_NORTH = Block.box(1, 1, 0, 15, 15, 6);
    protected static final VoxelShape SHAPE_SOUTH = Block.box(1, 1, 10, 15, 15, 16);
    protected static final VoxelShape SHAPE_WEST = Block.box(0, 1, 1, 6, 15, 15);
    protected static final VoxelShape SHAPE_EAST = Block.box(10, 1, 1, 16, 15, 15);

    public OpticalMirrorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(ANGLE, 1)); // 45 deg default
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        return defaultBlockState().setValue(FACING, facing).setValue(ANGLE, 1);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            int nextAngle = (state.getValue(ANGLE) + 1) % 8;
            BlockState newState = state.setValue(ANGLE, nextAngle);
            level.setBlock(pos, newState, 3);
            level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.6f, 1.4f + nextAngle * 0.05f);
            player.displayClientMessage(Component.translatable("message.lightenchanted.mirror_angle", nextAngle * 45), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> SHAPE_DOWN;
            case UP -> SHAPE_UP;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
        };
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    public static Vec3 getMirrorNormal(BlockState state) {
        Direction facing = state.getValue(FACING);
        int angle = state.getValue(ANGLE);
        double rad = Math.toRadians(angle * 45.0);

        // Calculate 3D surface normal vector for reflection
        Vec3 base = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        if (facing.getAxis() == Direction.Axis.Y) {
            return new Vec3(Math.cos(rad) * 0.7071, facing == Direction.UP ? 0.7071 : -0.7071, Math.sin(rad) * 0.7071).normalize();
        } else if (facing.getAxis() == Direction.Axis.Z) {
            return new Vec3(Math.cos(rad) * 0.7071, Math.sin(rad) * 0.7071, facing == Direction.SOUTH ? 0.7071 : -0.7071).normalize();
        } else {
            return new Vec3(facing == Direction.EAST ? 0.7071 : -0.7071, Math.sin(rad) * 0.7071, Math.cos(rad) * 0.7071).normalize();
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ANGLE);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
