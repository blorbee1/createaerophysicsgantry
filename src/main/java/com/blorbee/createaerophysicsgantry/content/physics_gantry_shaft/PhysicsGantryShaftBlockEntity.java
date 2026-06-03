package com.blorbee.createaerophysicsgantry.content.physics_gantry_shaft;

import com.blorbee.createaerophysicsgantry.compat.simulated.SimulatedHelper;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageBlock;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageBlockEntity;
import com.blorbee.createaerophysicsgantry.registry.CAPGBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PhysicsGantryShaftBlockEntity extends KineticBlockEntity {
    private Level previousLevelRef;
    private BlockPos previousShaftPos;

    public PhysicsGantryShaftBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    protected boolean syncSequenceContext() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;
        if (previousLevelRef != null && previousShaftPos != null && (previousLevelRef != level || !previousShaftPos.equals(worldPosition))) {
            moveAdjacentDisassembledCarriagesAcrossLevels(previousLevelRef, previousShaftPos, level, worldPosition);
        }
        previousLevelRef = level;
        previousShaftPos = worldPosition.immutable();
        pullAdjacentDisassembledCarriagesIntoCurrentLevel();
    }

    public void checkAttachedCarriageBlocks() {
        if (canAssembleOn())
            refreshCarriageAnchorLookup();
    }

    public void refreshCarriageAnchorLookup() {
        if (level == null)
            return;

        for (Direction d : Iterate.directions) {
            if (d.getAxis() != (getBlockState().getValue(PhysicsGantryShaftBlock.FACING)).getAxis()) {
                BlockPos offset = worldPosition.relative(d);
                BlockState pinionState = level.getBlockState(offset);
                if (pinionState.getBlock() == CAPGBlocks.PHYSICS_GANTRY_CARRIAGE.get() && pinionState.getValue(PhysicsGantryCarriageBlock.FACING) == d) {
                    PhysicsGantryCarriageBlockEntity carriageBlockEntity = null;
                    if (level.getBlockEntity(offset) instanceof PhysicsGantryCarriageBlockEntity carriage) {
                        carriageBlockEntity = carriage;
                    } else {
                        carriageBlockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, offset,
                            PhysicsGantryCarriageBlockEntity.class);
                    }

                    if (carriageBlockEntity != null && carriageBlockEntity.hasActiveAttachmentAnchor()) {
                        carriageBlockEntity.checkValidGantryShaft();
                    }
                }
            }
        }
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        checkAttachedCarriageBlocks();
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
        float defaultModifier = super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
        if (!connectedViaAxes && stateFrom.getValue(PhysicsGantryShaftBlock.POWERED) && stateTo.getBlock() == CAPGBlocks.PHYSICS_GANTRY_CARRIAGE.get()) {
            PhysicsGantryCarriageBlockEntity carriageBlockEntity = null;
            if (target instanceof PhysicsGantryCarriageBlockEntity carriage) {
                carriageBlockEntity = carriage;
            } else {
                carriageBlockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, target.getBlockPos(),
                    PhysicsGantryCarriageBlockEntity.class);
            }

            if (carriageBlockEntity == null || !carriageBlockEntity.hasActiveAttachmentAnchor())
                return 0.0F;

            Direction direction = Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
            if (stateTo.getValue(PhysicsGantryCarriageBlock.FACING) != direction)
                return defaultModifier;

            return PhysicsGantryCarriageBlockEntity.getGantryPinionModifier(
                stateFrom.getValue(PhysicsGantryShaftBlock.FACING),
                stateTo.getValue(PhysicsGantryCarriageBlock.FACING));
        } else {
            return defaultModifier;
        }
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        if (otherState.getBlock() != CAPGBlocks.PHYSICS_GANTRY_CARRIAGE.get())
            return false;

        PhysicsGantryCarriageBlockEntity carriageBlockEntity = null;
        if (other instanceof PhysicsGantryCarriageBlockEntity carriage) {
            carriageBlockEntity = carriage;
        } else {
            carriageBlockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, other.getBlockPos(),
                PhysicsGantryCarriageBlockEntity.class);
        }

        if (carriageBlockEntity == null || !carriageBlockEntity.hasActiveAttachmentAnchor())
            return false;

        BlockPos diff = other.getBlockPos().subtract(worldPosition);
        Direction direction = Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
        return otherState.getValue(PhysicsGantryCarriageBlock.FACING) == direction;
    }

    public boolean canAssembleOn() throws MatchException {
        BlockState blockState = getBlockState();
        if (blockState.getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get())
            return false;
        if (blockState.getValue(PhysicsGantryShaftBlock.POWERED))
            return false;

        float speed = getPinionMovementSpeed();
        return switch (blockState.getValue(PhysicsGantryShaftBlock.PART)) {
            case END -> speed < 0.0F;
            case MIDDLE -> speed != 0.0F;
            case START -> speed > 0.0F;
            case SINGLE -> false;
        };
    }

    public float getPinionMovementSpeed() {
        BlockState blockState = getBlockState();
        if (blockState.getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get())
            return 0.0F;
        return Mth.clamp(convertToLinear(-getSpeed()), -0.49F, 0.49F);
    }

    @Override
    protected boolean isNoisy() {
        return false;
    }

    private void pullAdjacentDisassembledCarriagesIntoCurrentLevel() {
        if (level == null)
            return;

        BlockState shaftState = getBlockState();
        if (shaftState.getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get())
            return;

        Direction shaftFacing = shaftState.getValue(PhysicsGantryShaftBlock.FACING);
        for (Direction d : Iterate.directions) {
            if (d.getAxis() != shaftFacing.getAxis()) {
                BlockPos carriagePos = worldPosition.relative(d);
                BlockEntity levelBe = level.getBlockEntity(carriagePos);

                if (!(levelBe instanceof PhysicsGantryCarriageBlockEntity)) {
                    PhysicsGantryCarriageBlockEntity carriage = SimulatedHelper.findBlockEntityIncludingSubLevels(level,
                        carriagePos, PhysicsGantryCarriageBlockEntity.class);

                    if (carriage != null && !carriage.isRemoved() && !carriage.isAssembledToSubLevel()) {
                        Level sourceLevel = carriage.getLevel();
                        if (sourceLevel != null && sourceLevel != level) {
                            BlockState sourceState = sourceLevel.getBlockState(carriagePos);

                            if (sourceState.getBlock() == CAPGBlocks.PHYSICS_GANTRY_CARRIAGE.get() &&
                                sourceState.getValue(PhysicsGantryCarriageBlock.FACING) == d) {
                                BlockState targetState = level.getBlockState(carriagePos);
                                if (targetState.isAir()) {
                                    sourceLevel.removeBlock(carriagePos, false);
                                    level.setBlock(carriagePos, sourceState, 3);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void moveAdjacentDisassembledCarriagesAcrossLevels(Level sourceLevel, BlockPos sourceShaftPos, Level targetLevel, BlockPos targetShaftPos) {
        if (sourceLevel == null || targetLevel == null || sourceShaftPos == null || targetShaftPos == null)
            return;

        BlockState sourceShaftState = sourceLevel.getBlockState(sourceShaftPos);
        BlockState targetShaftState = targetLevel.getBlockState(targetShaftPos);
        if (sourceShaftState.getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get() || targetShaftState
            .getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get())
            return;

        Direction sourceFacing = sourceShaftState.getValue(PhysicsGantryShaftBlock.FACING);
        Direction targetFacing = targetShaftState.getValue(PhysicsGantryShaftBlock.FACING);
        if (sourceFacing.getAxis() != targetFacing.getAxis())
            return;

        for (Direction d : Iterate.directions) {
            if (d.getAxis() != sourceFacing.getAxis()) {
                BlockPos sourceCarriagePos = sourceShaftPos.relative(d);
                BlockPos targetCarriagePos = targetShaftPos.relative(d);
                BlockState sourceCarriageState = sourceLevel.getBlockState(sourceCarriagePos);

                if (sourceCarriageState.getBlock() == CAPGBlocks.PHYSICS_GANTRY_CARRIAGE.get()
                    && sourceCarriageState.getValue(PhysicsGantryCarriageBlock.FACING) == d
                    && sourceLevel.getBlockEntity(sourceCarriagePos) instanceof PhysicsGantryCarriageBlockEntity sourceCarriage
                    && !sourceCarriage.isAssembledToSubLevel()
                    && targetLevel.getBlockState(targetCarriagePos).isAir()
                ) {
                    sourceLevel.removeBlock(sourceCarriagePos, false);
                    targetLevel.setBlock(targetCarriagePos, sourceCarriageState, 3);
                }
            }
        }
    }
}
