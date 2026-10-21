package com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage;

import com.blorbee.createaerophysicsgantry.compat.simulated.SimulatedHelper;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_shaft.PhysicsGantryShaftBlock;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_shaft.PhysicsGantryShaftBlockEntity;
import com.blorbee.createaerophysicsgantry.registry.CAPGBlocks;
import com.blorbee.createaerophysicsgantry.util.SubLevelBlockEntityCollector;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.Math;
import java.lang.reflect.Method;
import java.util.*;

public class PhysicsGantryCarriageBlockEntity extends KineticBlockEntity implements IDisplayAssemblyExceptions, BlockEntitySubLevelActor {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MOTOR_SIGN = 1;
    private static final int CONSTRAINT_GRACE_TICKS = 5;
    private static final int MISSING_SHAFT_GRACE_TICKS = 5;
    private static final int STALL_WAKE_TICKS = 5;
    private static final double EDGE_INSET = 0.02;

    private record ShaftRef(PhysicsGantryShaftBlockEntity be, UUID subLevelId, BlockPos pos) {}

    private boolean assembledToSubLevel;

    private BlockPos attachedShaftPos;
    private Direction attachedShaftDirection;
    private Direction attachedCarriageFacing;

    private UUID attachedSubLevelId;
    private UUID attachedShaftSubLevelId;

    private double attachedShaftProgress;

    private GenericConstraintHandle shaftConstraintHandle;
    private UUID constraintShaftSubLevelId;
    private BlockPos constraintShaftPos;
    private double constraintBaseProgress;
    private long constraintBuiltTick;
    private final Vector3d constraintBaseAnchor = new Vector3d();

    private boolean wasMoving;
    private int missingShaftTicks;
    private int stalledTicks;

    private List<BlockPos> leadingCache = List.of();
    private Direction leadingCacheDir;
    private long leadingCacheTick = Long.MIN_VALUE;

    private long lastAttachmentTickGameTime = Long.MIN_VALUE;
    private long lastToggleGameTime = Long.MIN_VALUE;

    protected AssemblyException lastException;

    public PhysicsGantryCarriageBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        registerAwardables(behaviours, AllAdvancements.CONTRAPTION_ACTORS);
    }

    public void forceToggleAssembly() {
        if (level == null || level.isClientSide)
            return;

        long now = level.getGameTime();
        if (lastToggleGameTime == now)
            return;
        lastToggleGameTime = now;

        SubLevel currentSubLevel = resolveAttachedSubLevel();
        if (isAttachedPayloadSubLevel(currentSubLevel)) {
            PhysicsGantryCarriageBlockEntity active = resolveActiveCarriageInstance(null, null, null);
            if (active.isShaftInSubLevel())
                return; // the carriage must stay assembled while the shaft is

            if (active != this) {
                active.lastToggleGameTime = this.lastToggleGameTime;
                active.doSubLevelDisassemble();
            } else {
                doSubLevelDisassemble();
            }
        } else {
            clearAttachmentTrackingState();
            doSubLevelAssemble();
        }
    }

    public boolean isSubLevelAssembled() {
        return assembledToSubLevel || attachedSubLevelId != null;
    }

    public boolean isShaftInSubLevel() {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel != null && resolveAttachedSubLevel() instanceof ServerSubLevel own) {
            ShaftRef ref = resolveShaft(serverLevel, own);
            return ref != null && ref.subLevelId() != null;
        }
        return attachedShaftSubLevelId != null;
    }

    private boolean hasTrackedAttachmentState() {
        return assembledToSubLevel
            || attachedSubLevelId != null
            || attachedShaftPos != null
            && attachedShaftDirection != null
            && attachedCarriageFacing != null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;

        if (!hasTrackedAttachmentState()) {
            tickUnassembledCarriage();
            return;
        }

        SubLevel attached = resolveAttachedSubLevel();
        if (attached == null) {
            // we think we are assembled but we are a plain world block, something else must have done it
            if (!SableCompanion.INSTANCE.isInPlotGrid(level, worldPosition)) {
                clearAttachmentTrackingStateAndRefreshShaft();
                setChanged();
                sendData();
            }
            return;
        }

        if (!assembledToSubLevel && attachedShaftPos != null && attachedShaftDirection != null && attachedCarriageFacing != null)
            assembledToSubLevel = true;
        runAttachmentTick(attached);
    }

    private void tickUnassembledCarriage() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof PhysicsGantryCarriageBlock))
            return;

        BlockPos shaftPos = worldPosition.relative(state.getValue(PhysicsGantryCarriageBlock.FACING).getOpposite());
        SubLevel containing = SimulatedHelper.getContainingSubLevel(this);

        if (containing != null) {
            // built into sublevel (inside of shaft's sublevel, gotta assemble)
            if (level.getBlockEntity(shaftPos) instanceof PhysicsGantryShaftBlockEntity) {
                missingShaftTicks = 0;
                assembleOrPop();
            } else if (++missingShaftTicks > MISSING_SHAFT_GRACE_TICKS) {
                level.destroyBlock(worldPosition, true);
            }
            return;
        }

        if (level.getBlockEntity(shaftPos) instanceof PhysicsGantryShaftBlockEntity) {
            missingShaftTicks = 0;
            return; // world carriage & world shaft: normal idle
        }

        PhysicsGantryShaftBlockEntity shaft = SimulatedHelper.findBlockEntityIncludingSubLevels(level, shaftPos, PhysicsGantryShaftBlockEntity.class);
        if (shaft == null) {
            // shaft gone everywhere, pop after short grace period
            if (++missingShaftTicks > MISSING_SHAFT_GRACE_TICKS)
                level.destroyBlock(worldPosition, true);
            return;
        }
        missingShaftTicks = 0;

        // shaft is in a sublevel and we are in the world, we must join with our own sublevel
        assembleOrPop();
    }

    private void assembleOrPop() {
        clearAttachmentTrackingState();
        doSubLevelAssemble();
        if (!assembledToSubLevel)
            level.destroyBlock(worldPosition, true); // improper state of carriage
    }

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        if (level != null && !level.isClientSide && subLevel != null) {
            if (hasTrackedAttachmentState()) {
                runAttachmentTick(subLevel);
            }
        }
    }

    private ConstraintJointAxis getConstraintAxis() {
        if (attachedShaftDirection == null)
            return ConstraintJointAxis.LINEAR_X;

        return switch (attachedShaftDirection.getAxis()) {
            case X -> ConstraintJointAxis.LINEAR_X;
            case Y -> ConstraintJointAxis.LINEAR_Y;
            case Z -> ConstraintJointAxis.LINEAR_Z;
        };
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }

    private void doSubLevelAssemble() {
        if (level == null || level.isClientSide)
            return;

        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof PhysicsGantryCarriageBlock)) {
            return;
        }

        Direction carriageFacing = blockState.getValue(PhysicsGantryCarriageBlock.FACING);
        BlockPos shaftPos = worldPosition.relative(carriageFacing.getOpposite());

        PhysicsGantryShaftBlockEntity shaftBe = level.getBlockEntity(shaftPos) instanceof PhysicsGantryShaftBlockEntity be
            ? be
            : SimulatedHelper.findBlockEntityIncludingSubLevels(level, shaftPos, PhysicsGantryShaftBlockEntity.class);
        if (shaftBe == null) {
            return;
        }

        BlockState shaftState = shaftBe.getBlockState();
        if (shaftState.getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return;
        }
        if (!shouldAssemble()) {
            return;
        }

        PhysicsGantryCarriageBlockEntity.CombinedAssemblyResult result;
        try {
            result = assembleCombinedMountedStructure();
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            LOGGER.warn("[PhysicsGantry] assemble failed at {}: {}", worldPosition, e.getMessage());
            return;
        } catch (Exception e) {
            LOGGER.warn("[PhysicsGantry] assemble invoke failed at {}: {}", worldPosition, e.toString());
            return;
        }

        if (result != null && result.subLevel() != null) {
            SubLevel assembledSubLevel = result.subLevel();
            PhysicsGantryCarriageBlockEntity active = resolveActiveCarriageInstance(result.movedCarriagePos(),
                result.offset(), assembledSubLevel);

            Direction shaftDirection = shaftState.getValue(PhysicsGantryShaftBlock.FACING);
            UUID attachedId = assembledSubLevel.getUniqueId();

            setAssembledAttachmentState(shaftPos, shaftDirection, carriageFacing, attachedId, null);
            setChanged();

            if (active != this) {
                active.setAssembledAttachmentState(shaftPos, shaftDirection, carriageFacing, attachedId, null);
                active.setChanged();
            }

            PhysicsGantryShaftBlockEntity shaftBeAfter = SimulatedHelper.findBlockEntityIncludingSubLevels(level, shaftPos,
                PhysicsGantryShaftBlockEntity.class);
            if (shaftBeAfter != null) {
                UUID shaftSubLevelId = SimulatedHelper.getContainingSubLevelId(shaftBeAfter);
                if (shaftSubLevelId != null) {
                    BlockPos shaftPlotPos = shaftBeAfter.getBlockPos();

                    setAssembledAttachmentState(shaftPlotPos, shaftDirection, carriageFacing, attachedId, shaftSubLevelId);
                    setChanged();

                    if (active != this) {
                        active.setAssembledAttachmentState(shaftPlotPos, shaftDirection, carriageFacing,
                            attachedId, shaftSubLevelId);
                        active.setChanged();
                    }
                }
            }

            sendData();
            if (active != this) {
                active.sendData();
            }

            AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);
        }
    }

    private PhysicsGantryCarriageBlockEntity.CombinedAssemblyResult assembleCombinedMountedStructure() throws AssemblyException {
        if (!(level instanceof ServerLevel serverLevel))
            return null;

        LinkedHashSet<BlockPos> assembledBlocks = new LinkedHashSet<>();
        LinkedHashSet<SuperGlueEntity> superGlues = new LinkedHashSet<>();
        LinkedHashSet<HoneyGlueEntity> honeyGlues = new LinkedHashSet<>();

        boolean carriageInsideGlueBox = isCarriageInsideGlueBox();
        if (!carriageInsideGlueBox) {
            SimAssemblyContraption carriageContraption = new SimAssemblyContraption(null, false);
            carriageContraption.searchMovedStructure(level, worldPosition);
            assembledBlocks.addAll(carriageContraption.getBlocks());
            superGlues.addAll(carriageContraption.getGlues());
            honeyGlues.addAll(carriageContraption.getHoneyGlues());
        } else {
            assembledBlocks.add(worldPosition);
        }

        BlockState carriageState = getBlockState();
        Direction mountedDirection = getMountedPayloadDirection(carriageState);
        BlockPos mountedPos = worldPosition.relative(mountedDirection);
        if (!level.getBlockState(mountedPos).isAir() && !assembledBlocks.contains(mountedPos)) {
            SimAssemblyContraption mountedContraption = new SimAssemblyContraption(null, false);
            mountedContraption.searchMovedStructure(level, mountedPos);
            assembledBlocks.addAll(mountedContraption.getBlocks());
            superGlues.addAll(mountedContraption.getGlues());
            honeyGlues.addAll(mountedContraption.getHoneyGlues());
        }

        if (assembledBlocks.isEmpty())
            return null;

        BoundingBox3i bounds = BoundingBox3i.from(assembledBlocks);
        ArrayList<AABB> collectedContraptionGlues = new ArrayList<>();
        invokeDisassembleAndAddCreateContraptions(level, bounds, assembledBlocks, collectedContraptionGlues);

        BlockPos anchor = assembledBlocks.contains(worldPosition) ? worldPosition : assembledBlocks.getFirst();
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(serverLevel, anchor, assembledBlocks, bounds);
        if (subLevel == null)
            return null;

        BlockPos offsetBlocks = subLevel.getPlot().getCenterBlock().subtract(anchor);
        Vec3 offset = Vec3.atLowerCornerOf(offsetBlocks);

        for (AABB aabb : collectedContraptionGlues) {
            level.addFreshEntity(new SuperGlueEntity(level, aabb.move(offset)));
        }

        for (SuperGlueEntity glueEntity : superGlues) {
            glueEntity.remove(Entity.RemovalReason.KILLED);
            level.addFreshEntity(new SuperGlueEntity(level, glueEntity.getBoundingBox().move(offset)));
        }

        for (HoneyGlueEntity glueEntity : honeyGlues) {
            glueEntity.remove(Entity.RemovalReason.KILLED);
            AABB newBounds = glueEntity.getBoundingBox().move(offset);
            HoneyGlueEntity entity = new HoneyGlueEntity(level, newBounds);
            level.addFreshEntity(entity);
            entity.setBoundsAndSync(newBounds);
        }

        BlockPos movedCarriagePos = worldPosition.offset(offsetBlocks);
        return new PhysicsGantryCarriageBlockEntity.CombinedAssemblyResult(
            new SimAssemblyHelper.AssemblyResult(subLevel, offsetBlocks), movedCarriagePos);
    }

    private boolean isCarriageInsideGlueBox() {
        if (level == null)
            return false;

        AABB searchBounds = new AABB(worldPosition).inflate(16);
        for (SuperGlueEntity glueEntity : level.getEntitiesOfClass(SuperGlueEntity.class, searchBounds)) {
            if (glueEntity.contains(worldPosition))
                return true;
        }
        for (HoneyGlueEntity glueEntity : level.getEntitiesOfClass(HoneyGlueEntity.class, searchBounds)) {
            if (glueEntity.contains(worldPosition))
                return true;
        }
        return false;
    }

    public static Direction getMountedPayloadDirection(BlockState carriageState) {
        if (carriageState.hasProperty(PhysicsGantryCarriageBlock.FACING) && carriageState.hasProperty(PhysicsGantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE)) {
            Direction facing = carriageState.getValue(PhysicsGantryCarriageBlock.FACING);
            boolean alongFirst = carriageState.getValue(PhysicsGantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE);

            Quaterniond orientation = new Quaterniond();
            orientation.rotateY(Math.toRadians(AngleHelper.horizontalAngle(facing)));
            orientation.rotateX(Math.toRadians(facing == Direction.UP ? 0.0 : (facing == Direction.DOWN ? 180.0 : 90.0)));
            orientation.rotateY(Math.toRadians(alongFirst ^ facing.getAxis() == Direction.Axis.X ? 0.0 : 90.0));

            Vector3d mountedNormal = orientation.transform(new Vector3d(0.0, 1.0, 0.0));
            return Direction.getNearest(mountedNormal.x, mountedNormal.y, mountedNormal.z);
        } else {
            return Direction.UP;
        }
    }

    private void invokeDisassembleAndAddCreateContraptions(Level level, BoundingBox3i bounds, Set<BlockPos> assembledBlocks, List<AABB> collectedContraptionGlues) throws IllegalStateException {
        try {
            Method method = SimAssemblyHelper.class
                .getDeclaredMethod(
                    "disassembleAndAddCreateContraptions",
                    Level.class,
                    BoundingBox3ic.class,
                    Collection.class,
                    boolean.class,
                    List.class
                );
            method.setAccessible(true);
            method.invoke(null, level, bounds, assembledBlocks, true, collectedContraptionGlues);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to invoke Simulated contraption disassembly helper", e);
        }
    }

    private void setAssembledAttachmentState(BlockPos shaftPos, Direction shaftDirection, Direction carriageFacing, UUID subLevelId, UUID shaftSubLevelId) {
        this.assembledToSubLevel = true;
        this.attachedShaftPos = shaftPos == null ? null : shaftPos.immutable();
        this.attachedShaftDirection = shaftDirection;
        this.attachedCarriageFacing = carriageFacing;
        this.attachedSubLevelId = subLevelId;
        this.attachedShaftSubLevelId = shaftSubLevelId;
        this.attachedShaftProgress = 0.0;
        this.wasMoving = false;
        this.lastException = null;
    }

    public boolean isAssembledToSubLevel() {
        return assembledToSubLevel;
    }

    private PhysicsGantryCarriageBlockEntity resolveActiveCarriageInstance(BlockPos movedCarriagePos, BlockPos assemblyOffset, @Nullable SubLevel assembledSubLevel) {
        if (movedCarriagePos != null && assembledSubLevel != null) {
            for (BlockEntity be : SubLevelBlockEntityCollector.getBlockEntities(assembledSubLevel)) {
                if (be != null
                    && !be.isRemoved()
                    && movedCarriagePos.equals(be.getBlockPos())
                ) {
                    if (be instanceof PhysicsGantryCarriageBlockEntity carriage)
                        return carriage;
                }
            }
        }

        Level lookup = resolveLookupLevel(level);
        if (lookup != null) {
            if (movedCarriagePos != null &&
                SimulatedHelper.findBlockEntityIncludingSubLevels(lookup, movedCarriagePos) instanceof PhysicsGantryCarriageBlockEntity carriage) {
                return carriage;
            }

            if (assemblyOffset != null) {
                BlockPos expected = worldPosition.offset(assemblyOffset);
                if (SimulatedHelper.findBlockEntityIncludingSubLevels(lookup, expected) instanceof PhysicsGantryCarriageBlockEntity carriage) {
                    return carriage;
                }
            }

            if (lookup.getBlockEntity(worldPosition) instanceof PhysicsGantryCarriageBlockEntity carriage) {
                return carriage;
            }

            if (SimulatedHelper.findBlockEntityIncludingSubLevels(lookup, worldPosition) instanceof PhysicsGantryCarriageBlockEntity carriage) {
                return carriage;
            }
        }

        return this;
    }

    private void doSubLevelDisassemble() {
        if (level == null || level.isClientSide)
            return;

        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null) {
            return;
        }

        BlockPos previousShaftPos = attachedShaftPos;
        clearShaftConstraint();

        SubLevel subLevel = resolveAttachedSubLevel();
        if (subLevel == null) {
            clearAttachmentTrackingStateAndRefreshShaft();
            sendData();
            return;
        } else if (!isAttachedPayloadSubLevel(subLevel)) {
            clearAttachmentTrackingStateAndRefreshShaft();
            sendData();
            return;
        }

        BlockPos disassemblyAnchor = worldPosition;
        BlockPos disassemblyGoal = resolveDisassemblyGoalBlockPos(subLevel);
        if (wouldDisassemblyOverwriteProtectedWorldBlock(subLevel, disassemblyAnchor, disassemblyGoal)) {
            return;
        }

        SimAssemblyHelper.disassembleSubLevel(serverLevel, subLevel, disassemblyAnchor, disassemblyGoal, Rotation.NONE, true);
        clearAttachmentTrackingStateAndRefreshShaft();
        clearDisassembledCarriageAttachmentState(disassemblyGoal, previousShaftPos);
        sendData();
    }

    private void clearDisassembledCarriageAttachmentState(BlockPos disassemblyGoal, BlockPos previousShaftPos) {
        if (level == null || disassemblyGoal == null)
            return;

        Level lookup = resolveLookupLevel(level);

        PhysicsGantryCarriageBlockEntity disassembledCarriage = SimulatedHelper.findBlockEntityIncludingSubLevels(lookup,
            disassemblyGoal, PhysicsGantryCarriageBlockEntity.class);
        if (disassembledCarriage != null) {
            disassembledCarriage.clearAttachmentTrackingState();
            disassembledCarriage.scrubKineticLinksAfterAttachmentTeardown(previousShaftPos);
            disassembledCarriage.setChanged();
            disassembledCarriage.sendData();
        }
    }

    private boolean isAttachedPayloadSubLevel(SubLevel subLevel) {
        if (subLevel == null)
            return false;

        if (!assembledToSubLevel &&
            attachedSubLevelId == null &&
            attachedShaftPos == null &&
            attachedShaftDirection == null &&
            attachedCarriageFacing == null) {
            return false;
        }

        return !subLevelContainsAttachedShaft(subLevel) && (assembledToSubLevel
            || attachedSubLevelId != null
            || attachedShaftPos != null
            && attachedShaftDirection != null
            && attachedCarriageFacing != null);
    }

    private boolean subLevelContainsAttachedShaft(SubLevel subLevel) {
        if (subLevel == null || attachedShaftPos == null)
            return false;

        UUID subLevelId = subLevel.getUniqueId();
        if (attachedShaftSubLevelId != null && attachedShaftSubLevelId.equals(subLevelId)) {
            PhysicsGantryShaftBlockEntity shaft = SimulatedHelper.findBlockEntityIncludingSubLevels(subLevel.getLevel(),
                attachedShaftPos, PhysicsGantryShaftBlockEntity.class);
            if (shaft == null)
                return false;

            if (attachedShaftDirection == null)
                return false;

            BlockState shaftState = shaft.getBlockState();
            return shaftState.getBlock() == CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()
                && shaftState.getValue(PhysicsGantryShaftBlock.FACING) == attachedShaftDirection;
        } else {
            return false;
        }
    }

    private void clearAttachmentTrackingState() {
        clearShaftConstraint();
        assembledToSubLevel = false;
        attachedShaftPos = null;
        attachedShaftDirection = null;
        attachedCarriageFacing = null;
        attachedSubLevelId = null;
        attachedShaftSubLevelId = null;
        attachedShaftProgress = 0.0;
        lastAttachmentTickGameTime = Long.MIN_VALUE;
        wasMoving = false;
        missingShaftTicks = 0;
    }

    private void clearAttachmentTrackingStateAndRefreshShaft() {
        BlockPos previousShaftPos = attachedShaftPos;
        clearAttachmentTrackingState();
        scrubKineticLinksAfterAttachmentTeardown(previousShaftPos);
    }

    private void scrubKineticLinksAfterAttachmentTeardown(BlockPos shaftPos) {
        if (level == null || level.isClientSide)
            return;

        detachKinetics();
        removeSource();
        attachKinetics();
        if (shaftPos != null) {
            Level lookup = resolveLookupLevel(level);
            if (lookup != null) {
                PhysicsGantryShaftBlockEntity shaft = SimulatedHelper.findBlockEntityIncludingSubLevels(lookup,
                    shaftPos, PhysicsGantryShaftBlockEntity.class);
                if (shaft != null) {
                    shaft.detachKinetics();
                    shaft.removeSource();
                    shaft.attachKinetics();
                }
            }
        }

        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        if (shaftPos != null) {
            BlockState shaftState = level.getBlockState(shaftPos);
            if (!shaftState.isAir()) {
                level.updateNeighborsAt(shaftPos, shaftState.getBlock());
            }
        }
    }

    public boolean hasActiveAttachmentAnchor() {
        return assembledToSubLevel || shaftConstraintHandle != null;
    }

    public BlockPos getAttachedShaftPos() {
        return attachedShaftPos;
    }
    public Direction getAttachedShaftDirection() {
        return attachedShaftDirection;
    }
    public Direction getAttachedCarriageFacing() {
        return attachedCarriageFacing;
    }
    public UUID getAttachedSubLevelId() {
        return attachedSubLevelId;
    }
    public double getAttachedShaftProgress() {
        return attachedShaftProgress;
    }

    private void runAttachmentTick(SubLevel subLevel) {
        Level lookupLevel = resolveLookupLevel(level);
        if (lookupLevel != null) {
            long gameTime = lookupLevel.getGameTime();
            if (lastAttachmentTickGameTime != gameTime) {
                lastAttachmentTickGameTime = gameTime;
                tickAttachedSubLevelMovement(subLevel);
            }
        }
    }

    private boolean isSubLevelDockedToExternal(SubLevel subLevel) {
        if (subLevel == null)
            return false;
        for (BlockEntity be : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
            if (be instanceof DockingConnectorBlockEntity connector && connector.isLocked())
                return true;
        }
        return false;
    }

    private int[] getSubLevelBlockBounds(SubLevel subLevel) {
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        int minX = bounds.minX(), minY = bounds.minY(), minZ = bounds.minZ();
        int maxX = bounds.maxX(), maxY = bounds.maxY(), maxZ = bounds.maxZ();
        return maxX >= minX && maxY >= minY && maxZ >= minZ ? new int[]{minX, minY, minZ, maxX, maxY, maxZ} : null;
    }

    private boolean wouldDisassemblyOverwriteProtectedWorldBlock(SubLevel subLevel, BlockPos anchor, BlockPos goal) {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null || subLevel == null || anchor == null || goal == null)
            return false;

        int[] bounds = getSubLevelBlockBounds(subLevel);
        if (bounds == null)
            return true;

        BlockPos offset = goal.subtract(anchor);
        for (int x = bounds[0]; x <= bounds[3]; x++) {
            for (int y = bounds[1]; y <= bounds[4]; y++) {
                for (int z = bounds[2]; z <= bounds[5]; z++) {
                    BlockPos localPos = new BlockPos(x, y, z);
                    BlockState payloadState = serverLevel.getBlockState(localPos);
                    if (payloadState.isAir())
                        continue;
                    if (isProtectedWorldBlock(serverLevel, localPos, payloadState))
                        return true;

                    BlockPos targetPos = localPos.offset(offset);
                    if (isProtectedWorldBlock(serverLevel, targetPos, serverLevel.getBlockState(targetPos)))
                        return true;
                }
            }
        }
        return false;
    }

    private void tickAttachedSubLevelMovement(SubLevel subLevel) {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null || !(subLevel instanceof ServerSubLevel carriageSubLevel) || !hasTrackedAttachmentState())
            return;

        ShaftRef shaft = resolveShaft(serverLevel, carriageSubLevel);
        if (shaft == null) {
            if (++missingShaftTicks > MISSING_SHAFT_GRACE_TICKS)
                detachFromShaft();
            return;
        }
        missingShaftTicks = 0;

        if (shaft.subLevelId() != null && shaft.subLevelId().equals(carriageSubLevel.getUniqueId()))
            return;

        double[] range = measureProgressRange(serverLevel);
        boolean constraintLive = shaftConstraintHandle != null && shaftConstraintHandle.isValid();

        double actual = constraintLive ? measureActualProgress(serverLevel, carriageSubLevel) : attachedShaftProgress;
        double before = attachedShaftProgress;

        float delta = 0.0f;
        if (!shaft.be().getBlockState().getValue(PhysicsGantryShaftBlock.POWERED) && !isSubLevelDockedToExternal(carriageSubLevel))
            delta = shaft.be().getPinionMovementSpeed();

        if (delta != 0.0f) {
            double next = Math.clamp(attachedShaftProgress + delta, range[0], range[1]);
            if (next != attachedShaftProgress && !wouldHitSolid(serverLevel, carriageSubLevel, shaft, actual, next))
                attachedShaftProgress = next;
        }

        // prevent shaft progress from escaping the position of the sublevel
        if (constraintLive) {
            double leash = Math.max(0.1, Math.abs(delta) * 2.0);
            double lag = attachedShaftProgress - actual;

            if (lag > leash)
                attachedShaftProgress = actual + leash;
            else if (lag < -leash)
                attachedShaftProgress = actual - leash;
            attachedShaftProgress = Math.clamp(attachedShaftProgress, range[0], range[1]);

            // use updated attachedShaftProgress
            stalledTicks = Math.abs(attachedShaftProgress - actual) > leash * 0.75 ? stalledTicks + 1 : 0;
        }

        boolean moving = attachedShaftProgress != before;
        if (moving)
            setChanged();

        boolean needsWaked = (moving && !wasMoving) || stalledTicks >= STALL_WAKE_TICKS;
        if (needsWaked) {
            stalledTicks = 0;
            if (constraintLive)
                attachedShaftProgress = actual;
            clearShaftConstraint();
        }

        driveConstraint(serverLevel, carriageSubLevel, shaft);
        if (needsWaked)
            wakeBody(serverLevel, carriageSubLevel);
        wasMoving = moving;
    }

    private ShaftRef resolveShaft(ServerLevel serverLevel, ServerSubLevel subLevel) {
        // where we last saw it
        if (attachedShaftPos != null) {
            PhysicsGantryShaftBlockEntity be = attachedShaftSubLevelId != null
                ? SimulatedHelper.findBlockEntity(serverLevel, attachedShaftSubLevelId, attachedShaftPos, PhysicsGantryShaftBlockEntity.class)
                : (serverLevel.getBlockEntity(attachedShaftPos) instanceof PhysicsGantryShaftBlockEntity s ? s : null);
            if (be != null && isMatchingShaft(be.getBlockState()))
                return new ShaftRef(be, attachedShaftSubLevelId, attachedShaftPos);
        }

        // shaft moved between world and sublevel? find again
        if (attachedCarriageFacing == null)
            return null;

        Pose3d pose = subLevel.logicalPose();
        Vec3 carriageCenter = pose.transformPosition(Vec3.atCenterOf(worldPosition));
        Vec3 facingWorld = pose.transformNormal(Vec3.atLowerCornerOf(attachedCarriageFacing.getNormal()));

        PhysicsGantryShaftBlockEntity be = SimulatedHelper.findBlockEntityAtWorldPoint(serverLevel, carriageCenter.subtract(facingWorld), PhysicsGantryShaftBlockEntity.class);
        if (be == null || be.getBlockState().getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get())
            return null;

        Direction newDir = be.getBlockState().getValue(PhysicsGantryShaftBlock.FACING);
        Vec3 shaftCenterWorld = SimulatedHelper.toContainingWorldPosition(be, Vec3.atCenterOf(be.getBlockPos()));
        Vec3 dirWorld = SimulatedHelper.toContainingWorldDirection(be, Vec3.atLowerCornerOf(newDir.getNormal()));

        attachedShaftSubLevelId = SimulatedHelper.getContainingSubLevelId(be);
        attachedShaftPos = be.getBlockPos().immutable();
        attachedShaftDirection = newDir;
        attachedShaftProgress = carriageCenter.subtract(shaftCenterWorld).dot(dirWorld);
        clearShaftConstraint();
        setChanged();
        return new ShaftRef(be, attachedShaftSubLevelId, attachedShaftPos);
    }

    private boolean isMatchingShaft(BlockState state) {
        return state.getBlock() == CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()
            && state.getValue(PhysicsGantryShaftBlock.FACING) == attachedShaftDirection;
    }

    private double[] measureProgressRange(ServerLevel serverLevel) {
        return new double[] {
            -measureShaftSpan(serverLevel, attachedShaftDirection.getOpposite()),
            measureShaftSpan(serverLevel, attachedShaftDirection)
        };
    }

    private void driveConstraint(ServerLevel serverLevel, ServerSubLevel subLevel, ShaftRef shaft) {
        if (shaftConstraintHandle != null) {
            boolean sameShaft = Objects.equals(constraintShaftSubLevelId, shaft.subLevelId())
                && Objects.equals(constraintShaftPos, shaft.pos());
            if (!sameShaft) {
                clearShaftConstraint();
            } else if (!shaftConstraintHandle.isValid()) {
                if (serverLevel.getGameTime() - constraintBuiltTick < CONSTRAINT_GRACE_TICKS)
                    return;
                clearShaftConstraint();
            }
        }

        if (shaftConstraintHandle == null && !buildConstraint(serverLevel, subLevel, shaft))
            return;
        shaftConstraintHandle.setMotor(getConstraintAxis(), motorTarget(), 1.0E6, 1.0E4, false, 0.0);
    }

    private boolean wouldHitSolid(ServerLevel serverLevel, ServerSubLevel subLevel, ShaftRef shaft, double fromProgress, double toProgress) {
        double step = toProgress - fromProgress;
        if (step == 0.0)
            return false;

        Vec3 dirWorld = SimulatedHelper.toContainingWorldDirection(shaft.be(), Vec3.atLowerCornerOf(attachedShaftDirection.getNormal()));
        if (dirWorld == null)
            return true;
        Vec3 travel = dirWorld.scale(Math.signum(step)); // unit world direction of travel
        Vec3 move = dirWorld.scale(step); // displacement to move

        Pose3d pose = subLevel.logicalPose();
        Vector3d localTravel = new Quaterniond(pose.orientation()).conjugate().transform(new Vector3d(travel.x, travel.y, travel.z));
        Direction localDir = Direction.getNearest(localTravel.x, localTravel.y, localTravel.z);

        Set<UUID> ignored = new HashSet<>();
        ignored.add(subLevel.getUniqueId());
        if (shaft.subLevelId() != null)
            ignored.add(shaft.subLevelId());

        for (BlockPos local : getLeadingBlocks(serverLevel, subLevel, localDir)) {
            Vec3 center = pose.transformPosition(Vec3.atCenterOf(local));
            Vec3 lead = center.add(travel.scale(0.5 - EDGE_INSET)).add(move);
            if (SimulatedHelper.isSolidAtWorldPoint(serverLevel, lead, ignored))
                return true;
        }
        return false;
    }

    private List<BlockPos> getLeadingBlocks(ServerLevel serverLevel, ServerSubLevel subLevel, Direction dir) {
        long now = serverLevel.getGameTime();
        if (dir == leadingCacheDir && now - leadingCacheTick < 20)
            return leadingCache;

        List<BlockPos> out = new ArrayList<>();
        int[] b = getSubLevelBlockBounds(subLevel);
        if (b != null) {
            for (int x = b[0]; x <= b[3]; x++)
                for (int y = b[1]; y <= b[4]; y++)
                    for (int z = b[2]; z <= b[5]; z++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (serverLevel.getBlockState(p).isAir() || !serverLevel.getBlockState(p.relative(dir)).isAir())
                            continue;
                        out.add(p);
                    }
        }

        leadingCache = out;
        leadingCacheDir = dir;
        leadingCacheTick = now;
        return out;
    }

    private boolean buildConstraint(ServerLevel serverLevel, ServerSubLevel subLevel, ShaftRef shaft) {
        ServerSubLevel shaftSubLevel = null;
        if (shaft.subLevelId() != null) {
            if (!(SubLevelBlockEntityCollector.getSubLevel(serverLevel, shaft.subLevelId()) instanceof ServerSubLevel s))
                return false;
            shaftSubLevel = s;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null)
            return false;

        Pose3d carriagePose = subLevel.logicalPose();
        Vector3d com = new Vector3d(carriagePose.position());
        Vector3d pos1 = new Vector3d(com);
        Quaterniond shaftQ = new Quaterniond();

        if (shaftSubLevel != null) {
            Vec3 local = SimulatedHelper.toContainingLocalPosition(shaftSubLevel, new Vec3(com.x, com.y, com.z));
            if (local == null)
                return false;
            pos1.set(local.x, local.y, local.z);
            shaftQ.set(shaftSubLevel.logicalPose().orientation());
        }
        Vector3d pos2 = new Vector3d(carriagePose.rotationPoint());

        if (!isFiniteAndSafe(pos1.x) || !isFiniteAndSafe(pos1.y) || !isFiniteAndSafe(pos1.z))
            return false;

        Quaterniond orientation1 = new Quaterniond();
        Quaterniond orientation2 = new Quaterniond(carriagePose.orientation()).conjugate().mul(shaftQ).normalize();

        Set<ConstraintJointAxis> locked = EnumSet.of(
            ConstraintJointAxis.LINEAR_X,
            ConstraintJointAxis.LINEAR_Y,
            ConstraintJointAxis.LINEAR_Z,
            ConstraintJointAxis.ANGULAR_X,
            ConstraintJointAxis.ANGULAR_Y,
            ConstraintJointAxis.ANGULAR_Z
        );
        locked.remove(getConstraintAxis());

        GenericConstraintConfiguration config = new GenericConstraintConfiguration(
            pos1, pos2,
            orientation1, orientation2,
            locked
        );
        GenericConstraintHandle handle = container.physicsSystem().getPipeline().addConstraint(shaftSubLevel, subLevel, config);
        if (handle == null)
            return false;

        handle.setContactsEnabled(false);

        shaftConstraintHandle = handle;
        constraintShaftSubLevelId = shaft.subLevelId();
        constraintShaftPos = shaft.pos();
        constraintBaseProgress = attachedShaftProgress;
        constraintBuiltTick = serverLevel.getGameTime();
        constraintBaseAnchor.set(pos1);
        return true;
    }

    private double motorTarget() {
        if (attachedShaftDirection == null)
            return 0.0;
        return (attachedShaftProgress - constraintBaseProgress) * attachedShaftDirection.getAxisDirection().getStep() * MOTOR_SIGN;
    }

    private void wakeBody(ServerLevel serverLevel, ServerSubLevel subLevel) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container != null) {
            Pose3d pose = subLevel.logicalPose();
            container.physicsSystem().getPipeline().teleport(subLevel, new Vector3d(pose.position()), new Quaterniond(pose.orientation()));
        }
    }

    private boolean isProtectedWorldBlock(Level chunkLevel, BlockPos pos, BlockState state) {
        return state.getBlock() == Blocks.BEDROCK || state.getDestroySpeed(chunkLevel, pos) == -1.0F;
    }

    public void detachFromShaft() {
        if (level != null && !level.isClientSide) {
            if (hasTrackedAttachmentState() || shaftConstraintHandle != null) {
                clearShaftConstraint();

                SubLevel carriageSubLevel = resolveAttachedSubLevel();
                if (carriageSubLevel == null)
                    carriageSubLevel = SimulatedHelper.getContainingSubLevel(this);

                if (carriageSubLevel != null) {
                    ServerLevel serverLevel = resolveServerLevel(level);
                    if (serverLevel != null) {
                        BlockPos disassemblyGoal = resolveDisassemblyGoalBlockPos(carriageSubLevel);
                        clearAttachmentTrackingStateAndRefreshShaft();
                        SimAssemblyHelper.disassembleSubLevel(serverLevel, carriageSubLevel, worldPosition, disassemblyGoal, Rotation.NONE, true);
                    }
                } else {
                    clearAttachmentTrackingStateAndRefreshShaft();
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.destroyBlock(getBlockPos(), true);
                    }
                }

                setChanged();
                sendData();
            }
        }
    }

    private void clearShaftConstraint() {
        if (shaftConstraintHandle != null)
            shaftConstraintHandle.remove();
        shaftConstraintHandle = null;
        constraintShaftSubLevelId = null;
        constraintShaftPos = null;
        constraintBaseProgress = 0.0;
        stalledTicks = 0;
    }

    private int measureShaftSpan(ServerLevel level, Direction direction) {
        BlockPos cursor = attachedShaftPos;
        int n = 0;
        while (n < 256) {
            cursor = cursor.relative(direction);
            if (!isMatchingShaft(level.getBlockState(cursor)))
                break;
            n++;
        }
        return n;
    }

    private double measureActualProgress(ServerLevel serverLevel, ServerSubLevel subLevel) {
        Vector3d inShaft = new Vector3d(subLevel.logicalPose().position());
        if (constraintShaftSubLevelId != null) {
            SubLevel shaftSub = SubLevelBlockEntityCollector.getSubLevel(serverLevel, constraintShaftSubLevelId);
            if (shaftSub == null)
                return attachedShaftProgress;
            Vec3 local = SimulatedHelper.toContainingLocalPosition(shaftSub, new Vec3(inShaft.x, inShaft.y, inShaft.z));
            if (local == null)
                return attachedShaftProgress;
            inShaft.set(local.x, local.y, local.z);
        }

        double along = (inShaft.x - constraintBaseAnchor.x) * attachedShaftDirection.getStepX()
            + (inShaft.y - constraintBaseAnchor.y) * attachedShaftDirection.getStepY()
            + (inShaft.z - constraintBaseAnchor.z) * attachedShaftDirection.getStepZ();
        return constraintBaseProgress + along;
    }

    private Level resolveLookupLevel(Level currentLevel) {
        if (currentLevel == null)
            return level;
        try {
            if (currentLevel.getClass().getMethod("getLevel").invoke(currentLevel) instanceof Level worldLevel) {
                return worldLevel;
            }
        } catch (Exception ignored) {}
        return currentLevel;
    }

    private ServerLevel resolveServerLevel(Level currentLevel) {
        Level lookup = resolveLookupLevel(currentLevel);
        if (lookup instanceof ServerLevel serverLevel)
            return serverLevel;
        if (lookup != null && lookup.getServer() != null) {
            ServerLevel byDimension = lookup.getServer().getLevel(lookup.dimension());
            return byDimension != null ? byDimension : lookup.getServer().overworld();
        }
        return null;
    }

    private BlockPos resolveDisassemblyGoalBlockPos(SubLevel subLevel) {
        Vec3 world = subLevel.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition));
        return BlockPos.containing(world);
    }

    private boolean isFiniteAndSafe(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 3.0E7;
    }

    private SubLevel resolveAttachedSubLevel() {
        if (level == null)
            return null;
        if (!hasTrackedAttachmentState())
            return null;

        Level lookup = resolveLookupLevel(level);

        if (attachedSubLevelId != null) {
            SubLevel byId = SubLevelBlockEntityCollector.getSubLevel(lookup, attachedSubLevelId);
            if (byId != null)
                return byId;
        }

        SubLevel containing = Sable.HELPER.getContaining(this);
        if (containing != null) {
            if (attachedSubLevelId == null)
                attachedSubLevelId = containing.getUniqueId();
            return containing;
        }

        if (attachedSubLevelId == null)
            return null;

        SubLevelContainer container = SubLevelContainer.getContainer(lookup);
        return container == null ? null : container.getSubLevel(attachedSubLevelId);
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide)
            clearShaftConstraint();
        super.remove();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        AssemblyException.write(tag, registries, lastException);
        tag.putBoolean("AssembledToSubLevel", assembledToSubLevel);
        if (this.attachedShaftPos != null) {
            tag.putLong("AttachedShaftPos", this.attachedShaftPos.asLong());
        }
        if (this.attachedShaftDirection != null) {
            tag.putInt("AttachedShaftDirection", this.attachedShaftDirection.get3DDataValue());
        }
        if (this.attachedCarriageFacing != null) {
            tag.putInt("AttachedCarriageFacing", this.attachedCarriageFacing.get3DDataValue());
        }
        if (this.attachedSubLevelId != null) {
            tag.putUUID("AttachedSubLevelId", this.attachedSubLevelId);
        }
        if (this.attachedShaftSubLevelId != null) {
            tag.putUUID("AttachedShaftSubLevelId", this.attachedShaftSubLevelId);
        }
        tag.putDouble("AttachedShaftProgress", this.attachedShaftProgress);

        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        lastException = AssemblyException.read(tag, registries);
        assembledToSubLevel = tag.getBoolean("AssembledToSubLevel");
        attachedShaftPos = tag.contains("AttachedShaftPos") ? BlockPos.of(tag.getLong("AttachedShaftPos")) : null;
        attachedShaftDirection = tag.contains("AttachedShaftDirection") ? Direction.from3DDataValue(tag.getInt("AttachedShaftDirection")) : null;
        attachedCarriageFacing = tag.contains("AttachedCarriageFacing") ? Direction.from3DDataValue(tag.getInt("AttachedCarriageFacing")) : null;
        attachedSubLevelId = tag.contains("AttachedSubLevelId") ? tag.getUUID("AttachedSubLevelId") : null;
        attachedShaftSubLevelId = tag.contains("AttachedShaftSubLevelId") ? tag.getUUID("AttachedShaftSubLevelId") : null;
        attachedShaftProgress = tag.getDouble("AttachedShaftProgress");
        super.read(tag, registries, clientPacket);
    }

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
        if (!hasActiveAttachmentAnchor()) {
            return 0.0F;
        } else {
            float defaultModifier = super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
            if (!connectedViaAxes && stateTo.getBlock() == CAPGBlocks.PHYSICS_GANTRY_SHAFT.get() && stateTo.getValue(PhysicsGantryShaftBlock.POWERED)) {
                Direction direction = Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
                return stateFrom.getValue(PhysicsGantryCarriageBlock.FACING) != direction.getOpposite()
                    ? defaultModifier
                    : getGantryPinionModifier(
                        stateTo.getValue(PhysicsGantryShaftBlock.FACING), stateFrom.getValue(PhysicsGantryCarriageBlock.FACING)
                );
            } else {
                return defaultModifier;
            }
        }
    }

    public static float getGantryPinionModifier(Direction shaft, Direction pinionDirection) {
        Direction.Axis shaftAxis = shaft.getAxis();
        float directionModifier = shaft.getAxisDirection().getStep();
        if (shaftAxis != Direction.Axis.Y || pinionDirection != Direction.NORTH && pinionDirection != Direction.EAST) {
            if (shaftAxis != Direction.Axis.X || pinionDirection != Direction.DOWN && pinionDirection != Direction.SOUTH) {
                return shaftAxis != Direction.Axis.Z || pinionDirection != Direction.UP && pinionDirection != Direction.WEST ? directionModifier : -directionModifier;
            } else {
                return -directionModifier;
            }
        } else {
            return -directionModifier;
        }
    }

    private boolean shouldAssemble() {
        if (level == null)
            return false;

        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof PhysicsGantryCarriageBlock))
            return false;

        Direction facing = blockState.getValue(PhysicsGantryCarriageBlock.FACING).getOpposite();
        BlockPos shaftPos = worldPosition.relative(facing);

        BlockState shaftState = level.getBlockState(shaftPos);
        if (shaftState.getBlock() instanceof PhysicsGantryShaftBlock) {
            return !shaftState.getValue(PhysicsGantryShaftBlock.POWERED);
        }

        PhysicsGantryShaftBlockEntity shaftBe = SimulatedHelper.findBlockEntityIncludingSubLevels(level, shaftPos,
            PhysicsGantryShaftBlockEntity.class);
        if (shaftBe != null) {
            BlockState sublevelShaftState = shaftBe.getBlockState();
            return sublevelShaftState.getBlock() instanceof PhysicsGantryShaftBlock
                && !sublevelShaftState.getValue(PhysicsGantryShaftBlock.POWERED);
        }

        return false;
    }

    private record CombinedAssemblyResult(SimAssemblyHelper.AssemblyResult assemblyResult, BlockPos movedCarriagePos) {
        private SubLevel subLevel() {
            return assemblyResult.subLevel();
        }
        private BlockPos offset() {
            return assemblyResult.offset();
        }
    }
}
