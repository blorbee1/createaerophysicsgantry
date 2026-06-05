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
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.fixed.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
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
import net.minecraft.util.Mth;
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

    private boolean assembledToSubLevel;

    private BlockPos attachedShaftPos;
    private Direction attachedShaftDirection;
    private Direction attachedCarriageFacing;

    private UUID attachedSubLevelId;
    private UUID attachedShaftSubLevelId;

    private double attachedShaftProgress;

    private PhysicsConstraintHandle shaftConstraintHandle;
    private Vec3 shaftConstraintWorldAnchor;

    private long lastAttachmentTickGameTime = Long.MIN_VALUE;
    private long lastToggleGameTime = Long.MIN_VALUE;

    private final Quaterniond lockedSubLevelOrientation = new Quaterniond();
    private boolean hasLockedSubLevelOrientation;

    private final Quaterniond lockedShaftFrameOrientation = new Quaterniond();
    private boolean hasLockedShaftFrameOrientation;

    private final Vector3d lockedLocalAttachmentAnchor = new Vector3d();
    private boolean hasLockedLocalAttachmentAnchor;

    private final Vector3d lockedRotationPoint = new Vector3d();
    private boolean hasLockedRotationPoint;

    protected AssemblyException lastException;

    public PhysicsGantryCarriageBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        registerAwardables(behaviours, AllAdvancements.CONTRAPTION_ACTORS);
    }

    public void checkValidGantryShaft() {
        if (assembledToSubLevel && !hasValidAttachedShaft(resolveLookupLevel(level))) {
            detachFromShaft("check-valid-shaft");
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        if (!getBlockState().canSurvive(level, worldPosition)) {
            level.destroyBlock(worldPosition, true);
        }
    }

    public void forceToggleAssembly() {
        if (level == null || level.isClientSide)
            return;

        long now = level.getGameTime();
        if (lastToggleGameTime == now)
            return;
        lastToggleGameTime = now;

        SubLevel currentSubLevel = resolveAttachedSubLevel();
//        LOGGER.info(
//            "[PhysicsGantry] toggle requested pos={} assembled={} hasSubLevel={}",
//            worldPosition, assembledToSubLevel, currentSubLevel != null
//        );

        if (isAttachedPayloadSubLevel(currentSubLevel)) {
            PhysicsGantryCarriageBlockEntity active = resolveActiveCarriageInstance(null, null, null);
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

        if (!assembledToSubLevel && attachedSubLevelId == null) {
            checkShaftAssemblyAndFollow();
        }

        SubLevel attachedSubLevel = resolveAttachedSubLevel();
        if (!assembledToSubLevel
            && attachedSubLevel != null
            && attachedShaftPos != null
            && attachedShaftDirection != null
            && attachedCarriageFacing != null
        ) {
            assembledToSubLevel = true;
        }

        if (attachedSubLevel != null
            && (assembledToSubLevel || attachedShaftPos != null && attachedShaftDirection != null && attachedCarriageFacing != null)
        ) {
            runAttachmentTick(attachedSubLevel);
        }
    }

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        if (level != null && !level.isClientSide && subLevel != null) {
            if (hasTrackedAttachmentState()) {
                runAttachmentTick(subLevel);
            }
        }
    }

    private void updateShaftConstraintFromGameTick(SubLevel subLevel, Vec3 worldAnchor) {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null || subLevel == null)
            return;

        SubLevel constrainedSubLevel = subLevel;
        if (attachedSubLevelId != null) {
            SubLevel preferredSubLevel = SubLevelBlockEntityCollector.getSubLevel(serverLevel, attachedSubLevelId);
            if (preferredSubLevel != null)
                constrainedSubLevel = preferredSubLevel;
        }

        SubLevel shaftSubLevel = null;
        if (attachedShaftSubLevelId != null) {
            SubLevel level = SubLevelBlockEntityCollector.getSubLevel(serverLevel, attachedShaftSubLevelId);
            if (level != null)
                shaftSubLevel = level;
        }

        Quaterniond orientation = resolveLockedOrientation(subLevel);
        Vector3d rotationPoint = subLevel.logicalPose().rotationPoint();
        if (rotationPoint == null)
            rotationPoint = new Vector3d();

        Vector3d targetPosition = computeLockedAttachmentTargetPosition(subLevel, worldAnchor, orientation);
        if (isFiniteAndSafe(targetPosition.x) && isFiniteAndSafe(targetPosition.y) && isFiniteAndSafe(targetPosition.z)) {
            boolean hasValidConstraint = shaftConstraintHandle != null && shaftConstraintHandle.isValid();
            boolean anchorMoved = shaftConstraintWorldAnchor == null || shaftConstraintWorldAnchor.distanceToSqr(worldAnchor) > 1.0E-6;

            if (!hasValidConstraint || anchorMoved) {
                if (shaftConstraintHandle != null)
                    clearShaftConstraint();

                Vector3d pos1 = targetPosition;
                if (shaftSubLevel != null) {
                    Vector3d shaftSubPos = shaftSubLevel.logicalPose().position();
                    Quaterniond shaftSubRotation = shaftSubLevel.logicalPose().orientation();
                    Vector3d shaftRotPoint = shaftSubLevel.logicalPose().rotationPoint();

                    if (shaftSubPos != null && shaftSubRotation != null && shaftRotPoint != null) {
                        Vector3d localInShaft = targetPosition.sub(shaftSubPos);
                        new Quaterniond(shaftSubRotation).conjugate().transform(localInShaft);
                        localInShaft.add(shaftRotPoint);
                        pos1 = localInShaft;
                    }
                }

                Quaterniond constraintOrientation = shaftSubLevel != null ? new Quaterniond() : orientation;

                FixedConstraintConfiguration fixedConstraint = new FixedConstraintConfiguration(pos1, rotationPoint, constraintOrientation);
                ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
                if (container == null)
                    return;

                SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
                PhysicsPipeline pipeline = physicsSystem.getPipeline();

                ServerSubLevel firstBody = shaftSubLevel instanceof ServerSubLevel ssl ? ssl : null;

                shaftConstraintHandle = pipeline.addConstraint(firstBody, (ServerSubLevel) constrainedSubLevel, fixedConstraint);
                shaftConstraintWorldAnchor = worldAnchor;
            }
        }
    }

    private void checkShaftAssemblyAndFollow() {
        if (level == null)
            return;
        if (assembledToSubLevel)
            return;
        if (SimulatedHelper.getContainingSubLevel(this) != null)
            return;

        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof PhysicsGantryCarriageBlock))
            return;

        Direction carriageFacing = blockState.getValue(PhysicsGantryShaftBlock.FACING);
        BlockPos shaftPos = worldPosition.relative(carriageFacing.getOpposite());

        if (level.getBlockEntity(shaftPos) instanceof PhysicsGantryShaftBlockEntity)
            return;

        PhysicsGantryShaftBlockEntity shaftBe = SimulatedHelper.findBlockEntityIncludingSubLevels(level, shaftPos,
            PhysicsGantryShaftBlockEntity.class);
        if (shaftBe == null)
            return;

        //LOGGER.info("[PhysicsGantry] shaft assembled into sublevel, auto-assembling carriage at {}", worldPosition);
        clearAttachmentTrackingState();
        doSubLevelAssemble();
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
            //LOGGER.warn("[PhysicsGantry] assemble aborted: not carriage block pos={}", worldPosition);
            return;
        }

        Direction carriageFacing = blockState.getValue(PhysicsGantryCarriageBlock.FACING);
        BlockPos shaftPos = worldPosition.relative(carriageFacing.getOpposite());

        PhysicsGantryShaftBlockEntity shaftBe = level.getBlockEntity(shaftPos) instanceof PhysicsGantryShaftBlockEntity be
            ? be
            : SimulatedHelper.findBlockEntityIncludingSubLevels(level, shaftPos, PhysicsGantryShaftBlockEntity.class);
        if (shaftBe == null) {
            //LOGGER.warn("[PhysicsGantry] assemble aborted: no shaft BE at {} from {}", shaftPos, worldPosition);
            return;
        }

        BlockState shaftState = shaftBe.getBlockState();
        if (shaftState.getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            //LOGGER.warn("[PhysicsGantry] assemble aborted: wrong shaft block at {}", shaftPos);
            return;
        }
        if (!shouldAssemble()) {
            //LOGGER.warn("[PhysicsGantry] assemble aborted: shouldAssemble=false, pos={}", worldPosition);
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

//            if (active.resolveAttachedSubLevel() == null && active != this) {
//                LOGGER.warn("[PhysicsGantry] active carriage unresolved after assemble origin={} active={}", worldPosition, active.worldPosition);
//            }

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

//            LOGGER.info("[PhysicsGantry] assembled pos={} active={} shaft={} dir={} face={}",
//                worldPosition, active.worldPosition, shaftPos, shaftDirection, carriageFacing);

            sendData();
            if (active != this) {
                active.sendData();
            }

            AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);
        } else {
            //LOGGER.warn("[PhysicsGantry] assemble returned null/no server sublevel at {}", worldPosition);
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
            //LOGGER.info("[PhysicsGantry] ignoring enclosing glue box at carriage {}", worldPosition);
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

//        LOGGER.info("[PhysicsGantry] assembledBlocks={} anchor={} plotCenter={} sublevelPos={}",
//            assembledBlocks, anchor, subLevel.getPlot().getCenterBlock(), subLevel.logicalPose().position());

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
        this.hasLockedSubLevelOrientation = false;
        this.lockedSubLevelOrientation.identity();
        this.hasLockedShaftFrameOrientation = false;
        this.lockedShaftFrameOrientation.identity();
        this.hasLockedLocalAttachmentAnchor = false;
        this.lockedLocalAttachmentAnchor.set(0.0, 0.0, 0.0);
        this.hasLockedRotationPoint = false;
        this.lockedRotationPoint.set(0.0, 0.0, 0.0);
        this.shaftConstraintWorldAnchor = null;
        this.lastException = null;
    }

    public void updateAttachmentShaftPosition(BlockPos newShaftPos) {
        if (assembledToSubLevel && newShaftPos != null) {
            attachedShaftPos = newShaftPos.immutable();
            setChanged();
        }
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
                    //LOGGER.info("[PhysicsGantry] found BE at movedCarriagePos class={} isCarriage={}", be.getClass().getName(), be instanceof PhysicsGantryCarriageBlockEntity);
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
            //LOGGER.warn("[PhysicsGantry] disassemble aborted: no server level for {}", worldPosition);
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
//            LOGGER.warn("[PhysicsGantry] disassemble skipped unsafe parent host pos={} shaft={} subLevelId={}",
//                worldPosition, attachedShaftPos, attachedSubLevelId);
            clearAttachmentTrackingStateAndRefreshShaft();
            sendData();
            return;
        }

        BlockPos disassemblyAnchor = worldPosition;
        BlockPos disassemblyGoal = resolveDisassemblyGoalBlockPos(subLevel);
        if (wouldDisassemblyOverwriteProtectedWorldBlock(subLevel, disassemblyAnchor, disassemblyGoal)) {
            //LOGGER.warn("[PhysicsGantry] disassembly blocked by protected block at {} goal={}", worldPosition, disassemblyGoal);
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
            disassembledCarriage.refreshShaftAnchorLookup(previousShaftPos);
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
        assembledToSubLevel = false;
        attachedShaftPos = null;
        attachedShaftDirection = null;
        attachedCarriageFacing = null;
        attachedSubLevelId = null;
        attachedShaftSubLevelId = null;
        attachedShaftProgress = 0.0;
        shaftConstraintWorldAnchor = null;
        hasLockedSubLevelOrientation = false;
        lockedSubLevelOrientation.identity();
        hasLockedShaftFrameOrientation = false;
        lockedShaftFrameOrientation.identity();
        hasLockedLocalAttachmentAnchor = false;
        lockedLocalAttachmentAnchor.set(0.0, 0.0, 0.0);
        hasLockedRotationPoint = false;
        lockedRotationPoint.set(0.0, 0.0, 0.0);
        lastAttachmentTickGameTime = Long.MIN_VALUE;
    }

    private void clearAttachmentTrackingStateAndRefreshShaft() {
        BlockPos previousShaftPos = attachedShaftPos;
        clearAttachmentTrackingState();
        scrubKineticLinksAfterAttachmentTeardown(previousShaftPos);
        refreshShaftAnchorLookup(previousShaftPos);
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

    private void refreshShaftAnchorLookup(BlockPos shaftPos) {
        if (shaftPos != null && level != null) {
            Level lookup = resolveLookupLevel(level);
            PhysicsGantryShaftBlockEntity shaft = SimulatedHelper.findBlockEntityIncludingSubLevels(lookup,
                shaftPos, PhysicsGantryShaftBlockEntity.class);
            if (shaft != null) {
                shaft.refreshCarriageAnchorLookup();
            }
        }
    }

    public boolean hasActiveAttachmentAnchor() {
        return assembledToSubLevel || shaftConstraintHandle != null || shaftConstraintWorldAnchor != null || hasLockedLocalAttachmentAnchor;
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

    private void tickAttachedSubLevelMovement(SubLevel subLevel) {
        ServerLevel lookupLevel = resolveServerLevel(level);
        if (lookupLevel == null)
            return;
        if (!hasTrackedAttachmentState())
            return;

        PhysicsGantryShaftBlockEntity shaftBe = findAttachedShaftEntity(attachedShaftPos, attachedShaftSubLevelId);
        UUID currentShaftSubLevelId = shaftBe == null ? null : SimulatedHelper.getContainingSubLevelId(shaftBe);

        if (!Objects.equals(currentShaftSubLevelId, attachedShaftSubLevelId)) {
            if (currentShaftSubLevelId == null && shaftBe == null && attachedShaftSubLevelId != null) {
                detachFromShaft("shaft-sublevel-destroyed");
                return;
            }

            attachedShaftSubLevelId = currentShaftSubLevelId;

            if (currentShaftSubLevelId == null) {
                // shaft returned to world space
                Vector3d subPos = subLevel.logicalPose().position();
                Quaterniond subRotation = subLevel.logicalPose().orientation();
                Vector3d rotPoint = subLevel.logicalPose().rotationPoint();

                if (subPos != null && subRotation != null && rotPoint != null && attachedCarriageFacing != null) {
                    Vector3d local = new Vector3d(
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5
                    ).sub(rotPoint);

                    subRotation.transform(local);
                    local.add(subPos);

                    Direction shaftDir = attachedCarriageFacing.getOpposite();
                    attachedShaftPos = BlockPos.containing(
                        local.x + shaftDir.getStepX(),
                        local.y + shaftDir.getStepY(),
                        local.z + shaftDir.getStepZ()
                    ).immutable();
                }
            } else {
                // shaft entered a sublevel
                attachedShaftPos = shaftBe.getBlockPos().immutable();
            }

            hasLockedLocalAttachmentAnchor = false;
            hasLockedShaftFrameOrientation = false;
            hasLockedSubLevelOrientation = false;
            clearShaftConstraint();
            setChanged();
        }

//        if (!hasValidAttachedShaft(lookupLevel)) {
//            detachFromShaft("tick-invalid-shaft");
//            return;
//        }
        if (measureValidProgressRange() == null) {
            detachFromShaft("tick-invalid-shaft");
            return;
        }

        if (shaftBe == null)
            return;

        BlockState shaftState = shaftBe.getBlockState();
        float delta = shaftBe.getPinionMovementSpeed();
        if (delta == 0.0F && Math.abs(shaftBe.getSpeed()) > 1.0E-4F) {
            delta = Mth.clamp(convertToLinear(-shaftBe.getSpeed()), -0.49F, 0.49F);
        }

        boolean tryMoving = !shaftState.getValue(PhysicsGantryShaftBlock.POWERED) && delta != 0.0F;
        if (tryMoving) {
            double[] range = measureValidProgressRange();
            if (range == null) {
                detachFromShaft("tick-invalid-shaft");
                return;
            }

//            int forwardSpan = measureShaftSpan(lookupLevel, attachedShaftPos, attachedShaftDirection);
//            int backwardSpan = measureShaftSpan(lookupLevel, attachedShaftPos, attachedShaftDirection.getOpposite());

            double nextProgress;
            if (delta > 0) {
                nextProgress = Math.min(attachedShaftProgress + delta, range[1]);
            } else {
                nextProgress = Math.max(attachedShaftProgress + delta, range[0]);
            }

            boolean allowMovement = !isSubLevelDockedToExternal(subLevel) &&
                !wouldAttachedSubLevelHitAWorldBlock(subLevel, nextProgress, Math.signum(delta));

            if (allowMovement) {
                attachedShaftProgress = nextProgress;
            } else {
                resetSubLevelVelocity(subLevel);
            }
        }

        applyAttachmentPose(subLevel);

//        if (tryMoving) {
//            updateTrackedShaftWhileMoving(subLevel);
//        }
    }

    private boolean isSubLevelDockedToExternal(SubLevel subLevel) {
        if (subLevel == null)
            return false;

        for (BlockEntity be : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
            if (be instanceof DockingConnectorBlockEntity connector) {
                if (connector.isLocked())
                    return true;
            }
        }

        return false;
    }

    private void updateTrackedShaftWhileMoving(SubLevel subLevel) {
        if (level == null || subLevel == null)
            return;
        if (attachedSubLevelId == null)
            return;

        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof PhysicsGantryCarriageBlock))
            return;

        Direction carriageFacing = blockState.getValue(PhysicsGantryCarriageBlock.FACING);
        BlockPos shaftPos = worldPosition.relative(carriageFacing.getOpposite());

        PhysicsGantryShaftBlockEntity shaftBe = level.getBlockEntity(shaftPos) instanceof PhysicsGantryShaftBlockEntity be
            ? be
            : SimulatedHelper.findBlockEntityIncludingSubLevels(level, shaftPos, PhysicsGantryShaftBlockEntity.class);
        if (shaftBe == null) {
            clearAttachmentTrackingStateAndRefreshShaft();
            setChanged();
            return;
        }

        shaftPos = shaftBe.getBlockPos();

        BlockState shaftState = shaftBe.getBlockState();
        if (shaftState.getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            clearAttachmentTrackingStateAndRefreshShaft();
            setChanged();
            return;
        }

        UUID shaftSubLevelId = SimulatedHelper.getContainingSubLevelId(shaftBe);
        if (shaftSubLevelId != attachedShaftSubLevelId) {
            clearAttachmentTrackingStateAndRefreshShaft();
            setChanged();
            return;
        }

        Direction shaftDirection = shaftState.getValue(PhysicsGantryShaftBlock.FACING);
        if (shaftDirection != attachedShaftDirection) {
            clearAttachmentTrackingStateAndRefreshShaft();
            setChanged();
            return;
        }

        if (shaftPos.equals(attachedShaftPos))
            return;

        BlockPos oldShaftPos = attachedShaftPos;
        BlockPos delta = shaftPos.subtract(oldShaftPos);

        double blockOffset = delta.getX() * attachedShaftDirection.getStepX() +
            delta.getY() * attachedShaftDirection.getStepY() +
            delta.getZ() * attachedShaftDirection.getStepZ();

        double oldProgress = attachedShaftProgress;
        double adjustedProgress = attachedShaftProgress - blockOffset;

        attachedShaftPos = shaftPos.immutable();
        attachedShaftProgress = adjustedProgress;

        setChanged();
        sendData();
//        LOGGER.debug("[PhysicsGantry] updated attachedShaftPos while moving to {} subLevel={} attachedShaftProgress={} oldprogress={}",
//            shaftPos, attachedShaftSubLevelId, attachedShaftProgress, oldProgress);
    }

    private void applyAttachmentPose(SubLevel subLevel) {
        if (subLevel != null && attachedShaftPos != null && attachedShaftDirection != null && attachedCarriageFacing != null) {
            PhysicsGantryShaftBlockEntity shaftBe = findAttachedShaftEntity(attachedShaftPos, attachedShaftSubLevelId);
            if (shaftBe == null)
                return;

            Vec3 shaftDirectionLocal = Vec3.atLowerCornerOf(attachedShaftDirection.getNormal());
            Vec3 carriageFacingLocal = Vec3.atLowerCornerOf(attachedCarriageFacing.getNormal());

            Vec3 shaftCenter = Vec3.atCenterOf(attachedShaftPos);

            SubLevel shaftSubLevel = SimulatedHelper.getContainingSubLevel(shaftBe);
            if (shaftSubLevel != null) {
                Vector3d subPos = shaftSubLevel.logicalPose().position();
                Quaterniond subRotation = shaftSubLevel.logicalPose().orientation();
                Vector3d rotPoint = shaftSubLevel.logicalPose().rotationPoint();
                if (subPos != null && subRotation != null && rotPoint != null) {
                    BlockPos shaftPlotPos = shaftBe.getBlockPos();

                    Vector3d local = new Vector3d(
                        shaftPlotPos.getX() + 0.5 + shaftDirectionLocal.x * attachedShaftProgress + carriageFacingLocal.x * 0.5,
                        shaftPlotPos.getY() + 0.5 + shaftDirectionLocal.y * attachedShaftProgress + carriageFacingLocal.y * 0.5,
                        shaftPlotPos.getZ() + 0.5 + shaftDirectionLocal.z * attachedShaftProgress + carriageFacingLocal.z * 0.5
                    ).sub(rotPoint);

                    subRotation.transform(local);
                    local.add(subPos);

                    Vec3 worldAnchor = new Vec3(local.x, local.y, local.z);
                    updateShaftConstraintFromGameTick(subLevel, worldAnchor);
                    return;
                }
            }

            Vec3 localAnchorWorld = shaftCenter
                .add(shaftDirectionLocal.x * attachedShaftProgress,
                    shaftDirectionLocal.y * attachedShaftProgress,
                    shaftDirectionLocal.z * attachedShaftProgress
                )
                .add(carriageFacingLocal.x * 0.5,
                    carriageFacingLocal.y * 0.5,
                    carriageFacingLocal.z * 0.5);

            Vec3 worldAnchor = SimulatedHelper.toContainingWorldPosition(shaftBe, localAnchorWorld);
            updateShaftConstraintFromGameTick(subLevel, worldAnchor);
        }
    }

    private boolean wouldAttachedSubLevelHitAWorldBlock(SubLevel subLevel, double progress, float movementSign) {
        if (subLevel != null && attachedShaftPos != null && attachedShaftDirection != null && attachedCarriageFacing != null) {
            PhysicsGantryShaftBlockEntity shaftBe = findAttachedShaftEntity(attachedShaftPos, attachedShaftSubLevelId);
            if (shaftBe == null)
                return true;

            double fromProgress = attachedShaftProgress;
            double toProgress = progress;
            double step = 0.25 * movementSign;

            if (step == 0) {
                step = toProgress > fromProgress ? 0.25 : -0.25;
            }

            Quaterniond orientation = resolveLockedOrientation(subLevel);

            double current = fromProgress;
            boolean done = false;
            while (!done) {
                if (movementSign > 0) {
                    current = Math.min(current + Math.abs(step), toProgress);
                    done = current >= toProgress;
                } else {
                    current = Math.max(current - Math.abs(step), toProgress);
                    done = current <= toProgress;
                }

                Vec3 worldAnchor = computeAttachmentWorldAnchor(shaftBe, current);
                if (worldAnchor == null)
                    return true;

                Vector3d targetPosition = computeLockedAttachmentTargetPosition(subLevel, worldAnchor, orientation);
                if (wouldSubLevelHitAWorldBlock(subLevel, targetPosition, orientation, movementSign))
                    return true;
            }
        }

        return false;
    }

    private Vec3 computeAttachmentWorldAnchor(PhysicsGantryShaftBlockEntity shaftBe, double progress) {
        Vec3 shaftDirectionLocal = Vec3.atLowerCornerOf(attachedShaftDirection.getNormal());
        Vec3 carriageFacingLocal = Vec3.atLowerCornerOf(attachedCarriageFacing.getNormal());
        Vec3 localAnchor = Vec3.atCenterOf(attachedShaftPos)
            .add(shaftDirectionLocal.x * progress, shaftDirectionLocal.y * progress, shaftDirectionLocal.z * progress)
            .add(carriageFacingLocal.x * 0.5, carriageFacingLocal.y * 0.5, carriageFacingLocal.z * 0.5);
        return SimulatedHelper.toContainingWorldPosition(shaftBe, localAnchor);
    }

    private boolean wouldSubLevelHitAWorldBlock(SubLevel subLevel, Vector3dc targetPosition, Quaterniond orientation, float movementSign) {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null || subLevel == null || targetPosition == null || orientation == null)
            return true;

        int[] bounds = getSubLevelBlockBounds(subLevel);
        if (bounds == null)
            return true;

        Vector3d rotationPoint = subLevel.logicalPose().rotationPoint();
        if (rotationPoint == null)
            rotationPoint = new Vector3d();

        double faceOffsetX = attachedShaftDirection.getStepX() * 0.5 * movementSign;
        double faceOffsetY = attachedShaftDirection.getStepY() * 0.5 * movementSign;
        double faceOffsetZ = attachedShaftDirection.getStepZ() * 0.5 * movementSign;

        for (int x = bounds[0]; x <= bounds[3]; x++) {
            for (int y = bounds[1]; y <= bounds[4]; y++) {
                for (int z = bounds[2]; z <= bounds[5]; z++) {
                    BlockPos localPos = new BlockPos(x, y, z);
                    BlockState payloadState = serverLevel.getBlockState(localPos);
                    if (payloadState.isAir())
                        continue;
                    if (isAWorldBlock(serverLevel, localPos, payloadState))
                        return true;

                    BlockPos targetCenter = transformSubLevelBlockToWorld(localPos, rotationPoint, targetPosition, orientation);
                    if (isAWorldBlock(serverLevel, targetCenter, serverLevel.getBlockState(targetCenter)))
                        return true;

                    Vector3d leadingFaceLocal = new Vector3d(
                        localPos.getX() + 0.5 + faceOffsetX,
                        localPos.getY() + 0.5 + faceOffsetY,
                        localPos.getZ() + 0.5 + faceOffsetZ
                    ).sub(rotationPoint);

                    new Quaterniond(orientation).transform(leadingFaceLocal);
                    leadingFaceLocal.add(targetPosition);

                    BlockPos leadingFaceWorld = BlockPos.containing(leadingFaceLocal.x, leadingFaceLocal.y, leadingFaceLocal.z);
                    if (isAWorldBlock(serverLevel, leadingFaceWorld, serverLevel.getBlockState(leadingFaceWorld)))
                        return true;
                }
            }
        }

        return false;
    }

    private boolean wouldDisassemblyOverwriteProtectedWorldBlock(SubLevel subLevel, BlockPos anchor, BlockPos goal) {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel != null && subLevel != null && anchor != null && goal != null) {
            int[] bounds = getSubLevelBlockBounds(subLevel);
            if (bounds == null)
                return true;

            BlockPos offset = goal.subtract(anchor);
            for (int x = bounds[0]; x <= bounds[3]; x++) {
                for (int y = bounds[1]; y <= bounds[4]; y++) {
                    for (int z = bounds[2]; z <= bounds[5]; z++) {
                        BlockPos localPos = new BlockPos(x, y, z);
                        BlockState payloadState = serverLevel.getBlockState(localPos);

                        if (!payloadState.isAir()) {
                            if (isProtectedWorldBlock(serverLevel, localPos, payloadState))
                                return true;

                            BlockPos targetPos = localPos.offset(offset);
                            BlockState targetState = serverLevel.getBlockState(targetPos);
                            if (isProtectedWorldBlock(serverLevel, targetPos, targetState))
                                return true;
                        }
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private BlockPos transformSubLevelBlockToWorld(BlockPos localPos, Vector3dc rotationPoint, Vector3dc targetPosition, Quaterniondc orientation) {
        Vector3d local = new Vector3d(
            localPos.getX() + 0.5 - rotationPoint.x(), localPos.getY() + 0.5 - rotationPoint.y(), localPos.getZ() + 0.5 - rotationPoint.z()
        );
        orientation.transform(local);
        local.add(targetPosition);
        return BlockPos.containing(local.x, local.y, local.z);
    }

    private int[] getSubLevelBlockBounds(SubLevel subLevel) {
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        int minX = bounds.minX();
        int minY = bounds.minY();
        int minZ = bounds.minZ();
        int maxX = bounds.maxX();
        int maxY = bounds.maxY();
        int maxZ = bounds.maxZ();
        return maxX >= minX && maxY >= minY && maxZ >= minZ ? new int[]{minX, minY, minZ, maxX, maxY, maxZ} : null;
    }

    private boolean isProtectedWorldBlock(Level chunkLevel, BlockPos pos, BlockState state) {
        return state.getBlock() == Blocks.BEDROCK || state.getDestroySpeed(chunkLevel, pos) == -1.0F;
    }

    private boolean isAWorldBlock(Level chunkLevel, BlockPos pos, BlockState state) {
        return state.getBlock() != Blocks.AIR && !SableCompanion.INSTANCE.isInPlotGrid(chunkLevel, pos);
    }

    public void detachFromShaft(String reason) {
        if (level != null && !level.isClientSide) {
            if (hasTrackedAttachmentState() || shaftConstraintHandle != null || shaftConstraintWorldAnchor != null) {
                clearShaftConstraint();

                SubLevel carriageSubLevel = resolveAttachedSubLevel();
                if (carriageSubLevel == null)
                    carriageSubLevel = SimulatedHelper.getContainingSubLevel(this);

                // TODO: try to fix shaft assembled unassembled carriage, break shaft
                // it works when using disassembleSubLevel, the block will fall
                // by using disassembleSubLevel will cause other paths not to make the block fall because the sublevel got disassembled
                // dont ask me why disassembling doesnt actually disassemble the block
                if (carriageSubLevel != null) {
//                    clearAttachmentTrackingStateAndRefreshShaft();
//                    if (level instanceof ServerLevel serverLevel) {
//                        serverLevel.destroyBlock(getBlockPos(), true);
//                    }
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

//                if (level instanceof ServerLevel serverLevel) {
//                    serverLevel.destroyBlock(getBlockPos(), true);
//                }

                setChanged();
                sendData();

//                LOGGER.debug(
//                    "[PhysicsGantry] detached from shaft pos={} reason={} sublevelid={}",
//                    this.worldPosition, reason, SimulatedHelper.getContainingSubLevelId(this));
            }
        }
    }

    private Vec3 toSubLevelLocalAnchor(SubLevel subLevel, Vec3 worldAnchor) {
        Vector3d position = subLevel.logicalPose().position();
        Quaterniond orientation = subLevel.logicalPose().orientation();
        if (position != null && orientation != null) {
            Vector3d local = new Vector3d(worldAnchor.x, worldAnchor.y, worldAnchor.z).sub(position);
            new Quaterniond(orientation).conjugate().transform(local);
            return new Vec3(local.x, local.y, local.z);
        } else {
            return Vec3.ZERO;
        }
    }

    private void clearShaftConstraint() {
        if (shaftConstraintHandle != null) {
            shaftConstraintHandle.remove();
        }
        shaftConstraintHandle = null;
        shaftConstraintWorldAnchor = null;
    }

    private int measureShaftSpan(Level lookupLevel, BlockPos origin, Direction direction) {
        int distance = 0;
        BlockPos cursor = origin;

        while (true) {
            cursor = cursor.relative(direction);
            BlockState state = findAttachedShaftState(cursor, attachedShaftSubLevelId);
            if (state == null)
                state = lookupLevel.getBlockState(cursor);

            if (state.getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get() || state.getValue(PhysicsGantryShaftBlock.FACING) != attachedShaftDirection)
                return distance;
            distance++;
        }
    }

    private double[] measureValidProgressRange() {
        if (attachedShaftPos == null || attachedShaftDirection == null || level == null)
            return new double[]{0, 0};

        BlockState anchorState = findAttachedShaftState(attachedShaftPos, attachedShaftSubLevelId);
        if (anchorState == null)
            anchorState = resolveLookupLevel(level).getBlockState(attachedShaftPos);

        if (anchorState.getBlock() != CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()
            || anchorState.getValue(PhysicsGantryShaftBlock.FACING) != attachedShaftDirection
        ) {
            reanchorToCurrentShaftBlock();
            if (attachedShaftPos == null) {
                detachFromShaft("invalid-shaft-pos");
                return null;
            }
            return measureValidProgressRange();
        }

        Level lookupLevel = resolveLookupLevel(level);
        int forward = measureShaftSpan(lookupLevel, attachedShaftPos, attachedShaftDirection);
        int backward = measureShaftSpan(lookupLevel, attachedShaftPos, attachedShaftDirection.getOpposite());
        return new double[]{-backward, forward};
    }

    private void reanchorToCurrentShaftBlock() {
        if (attachedShaftPos == null || attachedShaftDirection == null)
            return;

        Level lookupLevel = resolveLookupLevel(level);
        if (lookupLevel == null)
            return;

        int progressBlocks = (int) Math.round(attachedShaftProgress);

        for (int offset : new int[]{progressBlocks, progressBlocks + 1, progressBlocks - 1}) {
            BlockPos checkPos = attachedShaftPos.relative(attachedShaftDirection, offset);
            BlockState state = findAttachedShaftState(checkPos, attachedShaftSubLevelId);
            if (state == null)
                state = lookupLevel.getBlockState(checkPos);

            if (state.getBlock() == CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()
                && state.getValue(PhysicsGantryShaftBlock.FACING) == attachedShaftDirection
            ) {
                double blockDelta = (checkPos.getX() - attachedShaftPos.getX()) * attachedShaftDirection.getStepX()
                    + (checkPos.getY() - attachedShaftPos.getY()) * attachedShaftDirection.getStepY()
                    + (checkPos.getZ() - attachedShaftPos.getZ()) * attachedShaftDirection.getStepZ();

                double newProgress = attachedShaftProgress - blockDelta;

                if (Math.abs(newProgress) <= 0.5) {
                    attachedShaftProgress = newProgress;
                    attachedShaftPos = checkPos.immutable();

                    setChanged();
                    return;
                }
            }
        }

        clearAttachmentTrackingStateAndRefreshShaft();
    }

    private boolean hasValidAttachedShaft(Level lookupLevel) {
        if (assembledToSubLevel
            && lookupLevel != null
            && attachedShaftPos != null
            && attachedShaftDirection != null
            && attachedCarriageFacing != null
        ) {
            PhysicsGantryShaftBlockEntity shaft = findAttachedShaftEntity(attachedShaftPos, attachedShaftSubLevelId);
            if (shaft != null) {
                BlockState shaftState = shaft.getBlockState();
                return shaftState.getBlock() == CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()
                    && shaftState.getValue(PhysicsGantryShaftBlock.FACING) == attachedShaftDirection;
            }

            BlockState shaftState = lookupLevel.getBlockState(attachedShaftPos);
            return shaftState.getBlock() == CAPGBlocks.PHYSICS_GANTRY_SHAFT.get()
                && shaftState.getValue(PhysicsGantryShaftBlock.FACING) == attachedShaftDirection;
        } else {
            return false;
        }
    }

    private PhysicsGantryShaftBlockEntity findAttachedShaftEntity(BlockPos shaftPos, UUID preferredSubLevelId) {
        if (level == null || shaftPos == null)
            return null;

        PhysicsGantryShaftBlockEntity scoped = SimulatedHelper.findBlockEntity(level, preferredSubLevelId,
            shaftPos, PhysicsGantryShaftBlockEntity.class);
        if (scoped != null)
            return scoped;

        PhysicsGantryShaftBlockEntity includingSubLevels = SimulatedHelper.findBlockEntityIncludingSubLevels(level,
            shaftPos, PhysicsGantryShaftBlockEntity.class);
        if (includingSubLevels != null)
            return includingSubLevels;

        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null)
            return null;

        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container != null) {
            for (SubLevel subLevel : container.getAllSubLevels()) {
                for (BlockEntity be : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                    if (be instanceof PhysicsGantryShaftBlockEntity shaft && shaftPos.equals(shaft.getBlockPos())) {
                        return shaft;
                    }
                }
            }
        }

        Level lookupLevel = resolveLookupLevel(level);
        if (lookupLevel == null)
            return null;

        return lookupLevel.getBlockEntity(shaftPos) instanceof PhysicsGantryShaftBlockEntity shaft ? shaft : null;
    }

    private BlockState findAttachedShaftState(BlockPos pos, UUID preferredSubLevelId) {
        PhysicsGantryShaftBlockEntity shaft = findAttachedShaftEntity(pos, preferredSubLevelId);
        return shaft == null ? null : shaft.getBlockState();
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

    private BlockPos nearestGridFromSubLevel(SubLevel subLevel) {
        Vector3d position = subLevel.logicalPose().position();
        return position == null ? worldPosition : BlockPos.containing(position.x, position.y, position.z);
    }

    private BlockPos resolveDisassemblyGoalBlockPos(SubLevel subLevel) {
        if (attachedShaftPos != null && attachedShaftDirection != null && attachedCarriageFacing != null) {
            Vec3 baseCenter = Vec3.atCenterOf(attachedShaftPos);
            Vec3 targetCenter = baseCenter.add(
                attachedShaftDirection.getStepX() * attachedShaftProgress,
                attachedShaftDirection.getStepY() * attachedShaftProgress,
                attachedShaftDirection.getStepZ() * attachedShaftProgress
            ).add(attachedCarriageFacing.getStepX(), attachedCarriageFacing.getStepY(), attachedCarriageFacing.getStepZ());
            return BlockPos.containing(targetCenter.x, targetCenter.y, targetCenter.z);
        } else {
            return nearestGridFromSubLevel(subLevel);
        }
    }

    private Quaterniond resolveShaftFrameOrientation() {
        if (attachedShaftPos == null || attachedShaftDirection == null || attachedCarriageFacing == null)
            return null;

        PhysicsGantryShaftBlockEntity shaftBe = findAttachedShaftEntity(attachedShaftPos, attachedShaftSubLevelId);
        if (shaftBe == null)
            return null;

        Vec3 shaftLocal = Vec3.atLowerCornerOf(attachedShaftDirection.getNormal());
        Vec3 carriageLocal = Vec3.atLowerCornerOf(attachedCarriageFacing.getNormal());
        Vec3 shaftWorld = SimulatedHelper.toContainingWorldDirection(shaftBe, shaftLocal);
        Vec3 carriageWorld = SimulatedHelper.toContainingWorldDirection(shaftBe, carriageLocal);

        if (shaftWorld == null || carriageWorld == null)
            return null;

        Vector3d forward = new Vector3d(shaftWorld.x, shaftWorld.y, shaftWorld.z).normalize();
        Vector3d upHint = new Vector3d(carriageWorld.x, carriageWorld.y, carriageWorld.z).normalize();

        if (!(Double.isFinite(forward.x) && Double.isFinite(upHint.x)))
            return null;

        if (Math.abs(forward.dot(upHint)) > 0.999) {
            upHint = Math.abs(forward.y) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0 ,0);
        }

        Vector3d right = new Vector3d(forward).cross(upHint);
        if (right.lengthSquared() < 1.0E-12) {
            upHint = Math.abs(forward.y) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0 ,0);
            right = new Vector3d(forward).cross(upHint);
            if (right.lengthSquared() < 1.0E-12)
                return null;
        }

        right.normalize();
        Vector3d up = new Vector3d(right).cross(forward).normalize();

        Matrix3d basis = new Matrix3d().identity();
        basis.setColumn(0, forward);
        basis.setColumn(1, up);
        basis.setColumn(2, right);

        Quaterniond orientation = new Quaterniond().setFromNormalized(basis).normalize();
        if (!Double.isFinite(orientation.x) || !Double.isFinite(orientation.y) || !Double.isFinite(orientation.z))
            return null;

        return orientation;
    }

    private Quaterniond resolveLockedOrientation(SubLevel subLevel) {
        if (!hasLockedSubLevelOrientation) {
            Quaterniond current = subLevel.logicalPose().orientation();
            if (current != null && Double.isFinite(current.x) && Double.isFinite(current.y) && Double.isFinite(current.z) && Double.isFinite(current.w)) {
                lockedSubLevelOrientation.set(current);
            } else {
                lockedSubLevelOrientation.identity();
            }
            hasLockedSubLevelOrientation = true;
        }

        if (!hasLockedShaftFrameOrientation) {
            Quaterniond initialFrame = resolveShaftFrameOrientation();
            if (initialFrame != null) {
                lockedShaftFrameOrientation.set(initialFrame);
                hasLockedShaftFrameOrientation = true;
            }
        }

        if (hasLockedShaftFrameOrientation) {
            Quaterniond currentFrame = resolveShaftFrameOrientation();
            if (currentFrame != null) {
                Quaterniond delta = new Quaterniond(currentFrame).mul(new Quaterniond(lockedShaftFrameOrientation).conjugate()).normalize();
                return delta.mul(new Quaterniond(lockedSubLevelOrientation)).normalize();
            }
        }

        return new Quaterniond(lockedSubLevelOrientation);
    }

    private Vector3d computeLockedAttachmentTargetPosition(SubLevel subLevel, Vec3 worldAnchor, Quaterniond orientation) {
        if (!hasLockedLocalAttachmentAnchor) {
            Vec3 initialLocal = toSubLevelLocalAnchor(subLevel, worldAnchor);
            if (initialLocal != null && Double.isFinite(initialLocal.x) && Double.isFinite(initialLocal.y) && Double.isFinite(initialLocal.z)) {
                lockedLocalAttachmentAnchor.set(initialLocal.x, initialLocal.y, initialLocal.z);
            } else {
                lockedLocalAttachmentAnchor.set(
                    attachedCarriageFacing == null ? 0.0 : attachedCarriageFacing.getStepX() * 0.5,
                    attachedCarriageFacing == null ? 0.0 : attachedCarriageFacing.getStepY() * 0.5,
                    attachedCarriageFacing == null ? 0.0 : attachedCarriageFacing.getStepZ() * 0.5
                );
            }

            hasLockedLocalAttachmentAnchor = true;
            Vector3d initialRotPoint = subLevel.logicalPose().rotationPoint();
            if (initialRotPoint != null && Double.isFinite(initialRotPoint.x) && Double.isFinite(initialRotPoint.y) && Double.isFinite(initialRotPoint.z)) {
                lockedRotationPoint.set(initialRotPoint);
                hasLockedRotationPoint = true;
            }
        }

        Vector3d effectiveLocalAnchor = new Vector3d(lockedLocalAttachmentAnchor);
        if (hasLockedRotationPoint) {
            Vector3d currentRotPoint = subLevel.logicalPose().rotationPoint();
            if (currentRotPoint != null && Double.isFinite(currentRotPoint.x) && Double.isFinite(currentRotPoint.y) && Double.isFinite(currentRotPoint.z)) {
                effectiveLocalAnchor.add(
                    lockedRotationPoint.x - currentRotPoint.x,
                    lockedRotationPoint.y - currentRotPoint.y,
                    lockedRotationPoint.z - currentRotPoint.z
                );
            }
        }

        Vector3d rotatedLocal = new Vector3d(effectiveLocalAnchor);
        new Quaterniond(orientation).transform(rotatedLocal);
        return new Vector3d(worldAnchor.x - rotatedLocal.x, worldAnchor.y - rotatedLocal.y, worldAnchor.z - rotatedLocal.z);
    }

    private void resetSubLevelVelocity(SubLevel subLevel) {
        if (subLevel != null && level != null && level instanceof ServerLevel serverLevel) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
            if (container == null)
                return;

            SubLevelPhysicsSystem physics = container.physicsSystem();
            PhysicsPipeline pipeline = physics.getPipeline();

            pipeline.resetVelocity((PhysicsPipelineBody) subLevel);
        }
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
        if (level != null && !level.isClientSide) {
            //if (hasTrackedAttachmentState() || shaftConstraintHandle != null || shaftConstraintWorldAnchor != null) {
                clearShaftConstraint();
                clearAttachmentTrackingStateAndRefreshShaft();
                setChanged();
                sendData();
            //}
        }
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
