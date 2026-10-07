package dev.snowscpied.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Permanent decorative footprint decal for builders and adventure map makers.
 * Supports 16-way rotation (0-15) for precise trail building in SCP facilities and snowscapes.
 */
public class FootprintDecalBlock extends Block {
    public static final IntegerProperty ROTATION_16 = BlockStateProperties.ROTATION_16;
    private static final VoxelShape FLAT_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 0.5, 16.0);

    public FootprintDecalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROTATION_16, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION_16);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        int rot = (int) Math.floor((context.getRotation() * 16.0F / 360.0F) + 0.5D) & 15;
        return defaultBlockState().setValue(ROTATION_16, rot);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FLAT_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // Walk-through decal
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        return facing == Direction.DOWN && !state.canSurvive(level, currentPos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }
}
