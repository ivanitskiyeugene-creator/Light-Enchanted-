package dev.lightenchanted.block;

import dev.lightenchanted.blockentity.IndustrialFanBlockEntity;
import dev.lightenchanted.init.ModBlockEntities;
import dev.lightenchanted.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * SCP:SL HCZ Industrial Ventilation Fan (Slave/Dummy part of 3x3 multiblock).
 */
public class IndustrialFanSlaveBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public IndustrialFanSlaveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof IndustrialFanBlockEntity slave) {
            BlockPos masterPos = slave.getMasterPos();
            if (masterPos != null && level.getBlockState(masterPos).is(ModBlocks.INDUSTRIAL_FAN.get())) {
                return level.getBlockState(masterPos).use(level, player, hand, hit.withPosition(masterPos));
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof IndustrialFanBlockEntity slave) {
                BlockPos masterPos = slave.getMasterPos();
                if (masterPos != null && level.getBlockState(masterPos).is(ModBlocks.INDUSTRIAL_FAN.get())) {
                    level.destroyBlock(masterPos, true);
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // Master block renders the entire 3x3 model
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // Pass-through for light raytracing
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IndustrialFanBlockEntity(pos, state);
    }
}
