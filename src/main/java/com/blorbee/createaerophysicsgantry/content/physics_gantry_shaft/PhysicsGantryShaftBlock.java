package com.blorbee.createaerophysicsgantry.content.physics_gantry_shaft;

import com.blorbee.createaerophysicsgantry.registry.CAPGBlockEntityTypes;
import com.blorbee.createaerophysicsgantry.registry.CAPGBlocks;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.placement.PoleHelper;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.function.Predicate;

public class PhysicsGantryShaftBlock extends DirectionalKineticBlock implements IBE<PhysicsGantryShaftBlockEntity> {
    public static final Property<PhysicsGantryShaftBlock.Part> PART = EnumProperty.create("part", PhysicsGantryShaftBlock.Part.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new PlacementHelper());

    public PhysicsGantryShaftBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false).setValue(PART, Part.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(PART, POWERED));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        IPlacementHelper placementHelper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        return !placementHelper.matchesItem(stack)
            ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            : placementHelper.getOffset(player, level, state, pos, hitResult).placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hitResult);
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.EIGHT_VOXEL_POLE.get(state.getValue(FACING).getAxis());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        Direction.Axis axis = facing.getAxis();
        if (direction.getAxis() != axis)
            return state;

        boolean connect = CAPGBlocks.PHYSICS_GANTRY_SHAFT.get() == neighborState.getBlock() && neighborState.getValue(FACING) == facing;
        PhysicsGantryShaftBlock.Part part = state.getValue(PART);
        if (direction.getAxisDirection() == facing.getAxisDirection()) {
            if (connect) {
                if (part == Part.END)
                    part = Part.MIDDLE;
                if (part == Part.SINGLE)
                    part = Part.START;
            } else {
                if (part == Part.MIDDLE)
                    part = Part.END;
                if (part == Part.START)
                    part = Part.SINGLE;
            }
        } else if (connect) {
            if (part == Part.START)
                part = Part.MIDDLE;
            if (part == Part.SINGLE)
                part = Part.END;
        } else {
            if (part == Part.MIDDLE)
                part = Part.START;
            if (part == Part.END)
                part = Part.SINGLE;
        }

        return state.setValue(PART, part);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null)
            return null;

        BlockPos pos = context.getClickedPos();
        Level world = context.getLevel();
        Direction face = context.getClickedFace();
        BlockState neighbor = world.getBlockState(pos.relative(state.getValue(FACING).getOpposite()));
        BlockState clickedState = CAPGBlocks.PHYSICS_GANTRY_SHAFT.get() == neighbor.getBlock() ? neighbor : world.getBlockState(pos.relative(face.getOpposite()));

        if (CAPGBlocks.PHYSICS_GANTRY_SHAFT.get() == clickedState.getBlock()
            && (clickedState.getValue(FACING).getAxis() == state.getValue(FACING).getAxis())) {
            Direction facing = clickedState.getValue(FACING);
            state = state.setValue(FACING, context.getPlayer() != null && context.getPlayer().isShiftKeyDown() ? facing.getOpposite() : facing);
        }

        return state.setValue(POWERED, shouldBePowered(state, world, pos));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        InteractionResult onWrenched = super.onWrenched(state, context);
        if (onWrenched.consumesAction()) {
            BlockPos pos = context.getClickedPos();
            Level world = context.getLevel();
            neighborChanged(world.getBlockState(pos), world, pos, state.getBlock(), pos, false);
        }
        return onWrenched;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && oldState.getBlock() == CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            PhysicsGantryShaftBlock.Part oldPart = oldState.getValue(PART);
            PhysicsGantryShaftBlock.Part part = state.getValue(PART);
            if ((
                oldPart != Part.MIDDLE && part == Part.MIDDLE
                || oldPart != Part.SINGLE && part != Part.SINGLE
            ) && level.getBlockEntity(pos) instanceof PhysicsGantryShaftBlockEntity be) {
                be.checkAttachedCarriageBlocks();
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide)
            return;

        boolean previouslyPowered = state.getValue(POWERED);
        boolean shouldPower = level.hasNeighborSignal(pos);
        if (!previouslyPowered && !shouldPower && shouldBePowered(state, level, pos)) {
            level.setBlock(pos, state.setValue(POWERED, true), 3);
        } else if (previouslyPowered != shouldPower) {
            ArrayList<BlockPos> toUpdate = new ArrayList<>();
            Direction facing = state.getValue(FACING);
            Direction.Axis axis = facing.getAxis();

            for (Direction d : Iterate.directionsInAxis(axis)) {
                for (BlockPos currentPos = pos.relative(d); level.isLoaded(currentPos); currentPos = currentPos.relative(d)) {
                    BlockState currentState = level.getBlockState(currentPos);
                    if (!(currentState.getBlock() instanceof PhysicsGantryShaftBlock) || currentState.getValue(FACING) != facing) {
                        break;
                    }
                    if (!shouldPower && currentState.getValue(POWERED) && level.hasNeighborSignal(currentPos)) {
                        return;
                    }
                    if (currentState.getValue(POWERED) == shouldPower) {
                        break;
                    }
                    toUpdate.add(currentPos);
                }
            }
            toUpdate.add(pos);

            for (BlockPos blockPos : toUpdate) {
                BlockState blockState = level.getBlockState(blockPos);
                if (level.getBlockEntity(blockPos) instanceof KineticBlockEntity kineticBlockEntity) {
                    kineticBlockEntity.detachKinetics();
                }
                if (blockState.getBlock() instanceof PhysicsGantryShaftBlock) {
                    level.setBlock(blockPos, blockState.setValue(POWERED, shouldPower), 2);
                }
            }
        }
    }

    protected boolean shouldBePowered(BlockState state, Level level, BlockPos pos) {
        boolean shouldPower = level.hasNeighborSignal(pos);
        Direction facing = state.getValue(FACING);

        for (Direction d : Iterate.directionsInAxis(facing.getAxis())) {
            BlockPos neighborPos = pos.relative(d);
            if (level.isLoaded(neighborPos)) {
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.getBlock() instanceof PhysicsGantryShaftBlock && neighborState.getValue(FACING) == facing) {
                    shouldPower |= neighborState.getValue(POWERED);
                }
            }
        }

        return shouldPower;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        return super.areStatesKineticallyEquivalent(oldState, newState) && oldState.getValue(POWERED) == newState.getValue(POWERED);
    }

    @Override
    public float getParticleTargetRadius() {
        return 0.35F;
    }

    @Override
    public float getParticleInitialRadius() {
        return 0.25F;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public Class<PhysicsGantryShaftBlockEntity> getBlockEntityClass() {
        return PhysicsGantryShaftBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PhysicsGantryShaftBlockEntity> getBlockEntityType() {
        return CAPGBlockEntityTypes.PHYSICS_GANTRY_SHAFT.get();
    }

    public enum Part implements StringRepresentable {
        START,
        MIDDLE,
        END,
        SINGLE;

        public String getSerializedName() {
            return Lang.asId(name());
        }
    }

    public static class PlacementHelper extends PoleHelper<Direction> {
        public PlacementHelper() {
            super(s -> (s.getBlock() == CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()),
                s -> (s.getValue(DirectionalKineticBlock.FACING)).getAxis(),
                DirectionalKineticBlock.FACING);
        }

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.is((CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()).asItem());
        }

        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
            PlacementOffset offset = super.getOffset(player, world, state, pos, ray);
            offset.withTransform(offset.getTransform().andThen(
                s -> s.setValue(PhysicsGantryShaftBlock.POWERED,
                    state.getValue(PhysicsGantryShaftBlock.POWERED))));
            return offset;
        }
    }
}
