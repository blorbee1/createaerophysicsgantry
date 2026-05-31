package com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage;

import com.blorbee.createaerophysicsgantry.CreateAeroPhysicsGantry;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_shaft.PhysicsGantryShaftBlock;
import com.blorbee.createaerophysicsgantry.registry.CAPGBlockEntityTypes;
import com.blorbee.createaerophysicsgantry.registry.CAPGBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class PhysicsGantryCarriageBlock extends DirectionalAxisKineticBlock implements IBE<PhysicsGantryCarriageBlockEntity> {
    public PhysicsGantryCarriageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction direction = state.getValue(FACING);
        BlockState shaft = world.getBlockState(pos.relative(direction.getOpposite()));
        if (shaft.getBlock() == CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()
            && shaft.getValue(PhysicsGantryShaftBlock.FACING).getAxis() != direction.getAxis()) {
            return true;
        } else {
            return world instanceof Level level && level.getBlockEntity(pos) instanceof PhysicsGantryCarriageBlockEntity carriageBe
                ? carriageBe.isSubLevelAssembled()
                : false;
        }
    }

    @Override
    public void updateIndirectNeighbourShapes(BlockState stateIn, LevelAccessor worldIn, BlockPos pos, int flags, int count) {
        super.updateIndirectNeighbourShapes(stateIn, worldIn, pos, flags, count);
        withBlockEntityDo(worldIn, pos, PhysicsGantryCarriageBlockEntity::checkValidGantryShaft);
    }

    @Override
    protected Direction getFacingForPlacement(BlockPlaceContext context) {
        return context.getClickedFace();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.mayBuild() || player.isShiftKeyDown())
            return InteractionResult.PASS;

        if (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            if (!level.isClientSide) {
                withBlockEntityDo(level, pos, PhysicsGantryCarriageBlockEntity::forceToggleAssembly);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState stateForPlacement = super.getStateForPlacement(context);
        if (stateForPlacement == null)
            return null;

        Direction opposite = stateForPlacement.getValue(FACING).getOpposite();
        return cycleAxisIfNecessary(stateForPlacement, opposite, context.getLevel().getBlockState(context.getClickedPos().relative(opposite)));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (neighborPos.equals(pos.relative(state.getValue(FACING).getOpposite())) && !canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.getValue(FACING) != direction.getOpposite() ? state : cycleAxisIfNecessary(state, direction, neighborState);
    }

    protected BlockState cycleAxisIfNecessary(BlockState state, Direction direction, BlockState otherState) {
        if (otherState.getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return state;
        } else if (otherState.getValue(PhysicsGantryShaftBlock.FACING).getAxis() == direction.getAxis()) {
            return state;
        } else {
            return isValidGantryShaftAxis(state, otherState) ? state : state.cycle(AXIS_ALONG_FIRST_COORDINATE);
        }
    }

    public static boolean isValidGantryShaftAxis(BlockState pinionState, BlockState gantryState) {
        return getValidGantryShaftAxis(pinionState) == gantryState.getValue(PhysicsGantryShaftBlock.FACING).getAxis();
    }

    public static Direction.Axis getValidGantryShaftAxis(BlockState state) {
        if (state.getBlock() instanceof PhysicsGantryCarriageBlock block) {
            Direction.Axis rotationAxis = block.getRotationAxis(state);
            Direction.Axis facingAxis = state.getValue(FACING).getAxis();

            for (Direction.Axis axis : Iterate.axes) {
                if (axis != rotationAxis && axis != facingAxis) {
                    return axis;
                }
            }
        }

        return Direction.Axis.Y;
    }

    @Override
    public Class<PhysicsGantryCarriageBlockEntity> getBlockEntityClass() {
        return PhysicsGantryCarriageBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PhysicsGantryCarriageBlockEntity> getBlockEntityType() {
        return CAPGBlockEntityTypes.PHYSICS_GANTRY_CARRIAGE.get();
    }
}
