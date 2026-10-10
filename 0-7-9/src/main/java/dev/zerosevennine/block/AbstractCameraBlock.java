package dev.zerosevennine.block;

import dev.zerosevennine.blockentity.CameraBlockEntity;
import dev.zerosevennine.facility.FacilityZone;
import dev.zerosevennine.init.ModBlockEntities;
import dev.zerosevennine.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
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

public abstract class AbstractCameraBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final FacilityZone defaultZone;

    public AbstractCameraBlock(Properties properties, FacilityZone defaultZone) {
        super(properties);
        this.defaultZone = defaultZone;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public FacilityZone getDefaultZone() {
        return defaultZone;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        // Camera faces opposite of clicked wall so it looks into the room
        Direction facing = clickedFace.getAxis() == Direction.Axis.Y ? clickedFace : clickedFace;
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction dir = state.getValue(FACING);
        return switch (dir) {
            case UP -> Shapes.box(0.2, 0.0, 0.2, 0.8, 0.7, 0.8);
            case DOWN -> Shapes.box(0.2, 0.3, 0.2, 0.8, 1.0, 0.8);
            case NORTH -> Shapes.box(0.2, 0.2, 0.3, 0.8, 0.8, 1.0);
            case SOUTH -> Shapes.box(0.2, 0.2, 0.0, 0.8, 0.8, 0.7);
            case WEST -> Shapes.box(0.3, 0.2, 0.2, 1.0, 0.8, 0.8);
            case EAST -> Shapes.box(0.0, 0.2, 0.2, 0.7, 0.8, 0.8);
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        CameraBlockEntity be = new CameraBlockEntity(pos, state);
        be.setZone(defaultZone);
        be.setCameraName(defaultZone.getCode() + " - Security Camera");
        return be;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.CAMERA.get(), CameraBlockEntity::tick);
    }
}
