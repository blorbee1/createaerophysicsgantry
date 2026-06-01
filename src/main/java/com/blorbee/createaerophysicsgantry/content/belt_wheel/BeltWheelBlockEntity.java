package com.blorbee.createaerophysicsgantry.content.belt_wheel;

import com.blorbee.createaerophysicsgantry.compat.simulated.SimulatedHelper;
import com.blorbee.createaerophysicsgantry.util.SubLevelBlockEntityCollector;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

public class BeltWheelBlockEntity extends GeneratingKineticBlockEntity {
    private static final Map<Level, Set<BeltWheelBlockEntity>> INDEXED_WHEELS = new WeakHashMap<>();

    private static void indexWheel(BeltWheelBlockEntity be) {
        INDEXED_WHEELS.computeIfAbsent(be.level, b -> Collections.newSetFromMap(new WeakHashMap<>()))
            .add(be);
    }

    private static void unindexWheel(BeltWheelBlockEntity be) {
        Set<BeltWheelBlockEntity> set = INDEXED_WHEELS.get(be.level);
        if (set != null)
            set.remove(be);
    }

    @Nullable
    private BlockPos linkedPos;
    @Nullable
    private UUID linkedSubLevelId;
    private boolean linkValidated = false;

    private boolean receivesFromLinkedWheel;
    private float generatedLinkSpeed;

    private float reportedLinkCapacity;
    private float reportedLinkStress;

    private boolean lastKnownSourceState;

    private float reportedReceiverStress = 0.0F;
    private float reportedDriverCapacity = 0.0F;
    private boolean sharedOverstressed = false;

    private Long lastKnownNetwork;

    public BeltWheelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
        super.updateFromNetwork(maxStress, currentStress, networkSize);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;

        boolean currentSourceState = isSource();
        boolean sourceChanged = currentSourceState != lastKnownSourceState;
        lastKnownSourceState = currentSourceState;

        boolean kineticNetworkChanged = false;
        Long currentNetwork = hasNetwork() ? network : null;
        if (!Objects.equals(currentNetwork, lastKnownNetwork)) {
            lastKnownNetwork = currentNetwork;
            kineticNetworkChanged = true;
        }

        if (sourceChanged) {
            updateGeneratedRotation();
        }

        if (!linkValidated) {
            validateLink();
            return;
        }

        if (hasLinkedTarget()) {
            syncLinkReferences();

            BeltWheelBlockEntity linked = resolveLinkedWheel();
            if (linked != null) {
                if (kineticNetworkChanged && wouldCreateKineticLoop(linked)) {
                    breakLink(true);
                    return;
                }

                if (!receivesFromLinkedWheel) {
                    boolean newState = overStressed || linked.overStressed;
                    if (sharedOverstressed != newState) {
                        sharedOverstressed = newState;

                        overStressed = sharedOverstressed;
                        linked.overStressed = sharedOverstressed;

                        setChanged();
                        linked.setChanged();
                    }
                }

                if (receivesFromLinkedWheel) {
                    float ourStress = Math.max(0, stress);
                    if (Math.abs(ourStress - linked.reportedReceiverStress) > 0.01F) {
                        linked.reportedReceiverStress = ourStress;
                        linked.setChanged();
                    }
                } else {
                    float speed = Math.abs(getTheoreticalSpeed());
                    float ourStress = stress - (lastStressApplied * speed);
                    float netCapacity = Math.max(0, capacity - ourStress);
                    if (Math.abs(netCapacity - linked.reportedDriverCapacity) > 0.01F) {
                        linked.reportedDriverCapacity = netCapacity;
                        linked.setChanged();
                    }
                }
            }

            validateLinkDistanceAndBreakIfInvalid();
        }

        boolean nextReceiveFromLink = false;
        float nextSpeed = 0.0F;
        if (hasLinkedTarget()) {
            BeltWheelBlockEntity linkedWheel = resolveLinkedWheel();
            if (linkedWheel != null && !linkedWheel.isRemoved()) {
                UUID thisSubLevel = SimulatedHelper.getContainingSubLevelId(this);
                if (thisSubLevel != null && !linkedWheel.references(worldPosition, thisSubLevel)) {
                    nextReceiveFromLink = receivesFromLinkedWheel;
                    nextSpeed = receivesFromLinkedWheel ? generatedLinkSpeed : 0.0F;
                } else {
                    TransferDecision transferDecision = resolveTransferDecision(linkedWheel);
                    nextReceiveFromLink = transferDecision.receiveFromLinkedWheel;
                    if (nextReceiveFromLink) {
                        nextSpeed = linkedWheel.getSpeed();
                    }
                }
            } else {
                nextReceiveFromLink = receivesFromLinkedWheel;
                nextSpeed = receivesFromLinkedWheel ? generatedLinkSpeed : 0.0F;
            }
        }

        if (receivesFromLinkedWheel != nextReceiveFromLink || Math.abs(nextSpeed - generatedLinkSpeed) > 0.01F) {
            receivesFromLinkedWheel = nextReceiveFromLink;
            generatedLinkSpeed = nextSpeed;

            updateGeneratedRotation();
            setChanged();
            sendData();
        }

        refreshLinkedStressNetworkValues();
    }

    private void syncLinkReferences() {
        if (level == null || linkedPos == null || level.isClientSide)
            return;

        BeltWheelBlockEntity linked = findLinkedWheelAnywhere();
        if (linked == null)
            return;

        BlockPos linkedCurrentPos = linked.getBlockPos();
        UUID linkedCurrentSubLevel = SimulatedHelper.getContainingSubLevelId(linked);
        if (!linkedCurrentPos.equals(linkedPos) || !Objects.equals(linkedCurrentSubLevel, linkedSubLevelId)) {
            linkedPos = linkedCurrentPos.immutable();
            linkedSubLevelId = linkedCurrentSubLevel;
            setChanged();
            sendData();
        }

        BlockPos ourCurrentPos = worldPosition;
        UUID ourCurrentSubLevel = SimulatedHelper.getContainingSubLevelId(this);
        if (!linked.references(ourCurrentPos, ourCurrentSubLevel)) {
            linked.linkedPos = ourCurrentPos.immutable();
            linked.linkedSubLevelId = ourCurrentSubLevel;
            linked.setChanged();
            linked.sendData();
        }
    }

    private void validateLinkDistanceAndBreakIfInvalid() {
        BeltWheelBlockEntity other = resolveLinkedWheel();
        if (other == null || other.isRemoved()) {
            return;
        }

        Vec3 a = getWorldAnchorPosition();
        Vec3 b = other.getWorldAnchorPosition();

        if (a == null || b == null) {
            return;
        }

        Vec3 delta = b.subtract(a);
        double distance = delta.length();
        int maxDistance = BeltWheelBlock.getConfiguredMaxDistance();

        if (distance > maxDistance + 0.01 || distance < 0.01)  {
            breakLink(true);
        }
    }

    @Override
    public float getGeneratedSpeed() {
        return receivesFromLinkedWheel ? generatedLinkSpeed : 0.0F;
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (!receivesFromLinkedWheel) {
            lastCapacityProvided = 0.0F;
            return 0.0F;
        }

        float generatedSpeed = Math.abs(getGeneratedSpeed());
        if (generatedSpeed <= 1.0E-4F) {
            lastCapacityProvided = 0.0F;
            return 0.0F;
        }

        float available = Math.max(0.0F, reportedDriverCapacity);
        lastCapacityProvided = available / generatedSpeed;
        return lastCapacityProvided;
    }

    @Override
    public float calculateStressApplied() {
        if (receivesFromLinkedWheel) {
            lastStressApplied = 0.0F;
            return 0.0F;
        }

        float speed = Math.abs(getSpeed());
        if (speed <= 1.0E-4F) {
            lastStressApplied = 0.0F;
            return 0.0F;
        }

        lastStressApplied = reportedReceiverStress / speed;
        return lastStressApplied;
    }

    @Override
    public float getSpeed() {
        return receivesFromLinkedWheel ? generatedLinkSpeed : getTheoreticalSpeed();
    }

    public boolean hasLinkedTarget() {
        return linkedPos != null;
    }

    public boolean shouldRenderLinkFromThisEndpoint() {
        return hasLinkedTarget() && !receivesFromLinkedWheel;
    }

    public void setLinkedTarget(BlockPos targetPos, @Nullable UUID targetSubLevelId) {
        linkedPos = targetPos.immutable();
        linkedSubLevelId = targetSubLevelId;
        receivesFromLinkedWheel = false;
        generatedLinkSpeed = 0.0F;
        linkValidated = false;

        updateGeneratedRotation();
        setChanged();
        sendData();
    }

    public boolean references(BlockPos pos, @Nullable UUID subLevelId) {
        return linkedPos != null && linkedPos.equals(pos) && Objects.equals(linkedSubLevelId, subLevelId);
    }

    public void breakLink(boolean notifyOther) {
        if (!hasLinkedTarget())
            return;

        Vec3 start = getWorldAnchorPosition();
        Vec3 end = getLinkedWorldAnchorPosition();

        if (notifyOther && level != null && linkedPos != null) {
            BeltWheelBlockEntity other = resolveLinkedWheel();
            if (other != null && !other.isRemoved()) {
                other.breakLink(false);
            }
        }

        if (level instanceof ServerLevel serverLevel && start != null && end != null) {
            playBeltBreakEffects(serverLevel, start, end);
        }

        linkedPos = null;
        linkedSubLevelId = null;
        receivesFromLinkedWheel = false;
        generatedLinkSpeed = 0.0F;

        updateGeneratedRotation();
        setChanged();
        sendData();
    }

    private void validateLink() {
        if (level == null || level.isClientSide)
            return;

        BeltWheelBlockEntity other = resolveLinkedWheel();
        if (other == null)
            return;

        if (!other.hasLinkedTarget())
            return;

        if (wouldCreateKineticLoop(other)) {
            breakLink(true);
            return;
        }

        linkValidated = true;
        other.linkValidated = true;
    }

    private boolean wouldCreateKineticLoop(BeltWheelBlockEntity other) {
        if (other == null)
            return false;

        if (isInSameKineticNetwork(other))
            return true;

        // check if other's network contains any wheel that links back to our network
        Set<Long> visitedNetworks = new HashSet<>();
        Set<BeltWheelBlockEntity> visitedWheels = new HashSet<>();
        Queue<BeltWheelBlockEntity> queue = new LinkedList<>();

        visitedWheels.add(this);
        if (hasNetwork())
            visitedNetworks.add(network);

        queue.add(other);
        visitedWheels.add(other);

        while (!queue.isEmpty()) {
            BeltWheelBlockEntity current = queue.poll();

            if (isInSameKineticNetwork(current))
                return true;

            if (current.hasNetwork() && visitedNetworks.add(current.network)) {
                for (BeltWheelBlockEntity wheel : getAllKnownBeltWheels()) {
                    if (visitedWheels.contains(wheel))
                        continue;
                    if (wheel.hasNetwork() && Objects.equals(wheel.network, current.network)) {
                        visitedWheels.add(wheel);
                        queue.add(wheel);
                    }
                }
            }

            BeltWheelBlockEntity linked = current.resolveLinkedWheel();
            if (linked != null && visitedWheels.add(linked)) {
                if (isInSameKineticNetwork(linked))
                    return true;
                queue.add(linked);
            }
        }

        return false;
    }

    private boolean isInSameKineticNetwork(BeltWheelBlockEntity other) {
        if (other == null || !hasNetwork() || !other.hasNetwork())
            return false;
        return Objects.equals(network, other.network);
    }

    private Iterable<BeltWheelBlockEntity> getAllKnownBeltWheels() {
        List<BeltWheelBlockEntity> wheels = new ArrayList<>();
        if (level == null)
            return wheels;

        Set<BeltWheelBlockEntity> indexedWheels = INDEXED_WHEELS.get(level);
        if (indexedWheels != null)
            wheels.addAll(indexedWheels);

        return wheels;
    }

    @Nullable
    public BeltWheelBlockEntity resolveLinkedWheel() {
        return findLinkedWheelAnywhere();
    }

    @Nullable
    private BeltWheelBlockEntity findLinkedWheelAnywhere() {
        if (level == null || linkedPos == null)
            return null;

        BeltWheelBlockEntity found = SimulatedHelper.findBlockEntity(level, linkedSubLevelId,
            linkedPos, BeltWheelBlockEntity.class);
        if (found != null)
            return found;

        found = SimulatedHelper.findBlockEntityIncludingSubLevels(level, linkedPos, BeltWheelBlockEntity.class);
        if (found != null)
            return found;

        UUID ourSubLevel = SimulatedHelper.getContainingSubLevelId(this);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (SubLevel subLevel : container.getAllSubLevels()) {
                for (BlockEntity be : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                    if (be instanceof BeltWheelBlockEntity wheel && wheel.references(worldPosition, ourSubLevel)) {
                        return wheel;
                    }
                }
            }
        }

        return null;
    }

    public Vec3 getWorldAnchorPosition() {
        Vec3 local = Vec3.atCenterOf(worldPosition);
        Vec3 transformed = SimulatedHelper.toGlobalWorldPosition(this, local);
        return transformed == null ? local : transformed;
    }

    public Vec3 getAnchorPositionInRenderFrameOf(BeltWheelBlockEntity renderOrigin) {
        Vec3 local = Vec3.atCenterOf(worldPosition);
        Vec3 transformed = SimulatedHelper.toRenderFramePosition(this, local, renderOrigin);
        if (transformed != null && Double.isFinite(transformed.x) && Double.isFinite(transformed.y) && Double.isFinite(transformed.z))
            return transformed;
        Vec3 worldAnchor = getWorldAnchorPosition();
        Vec3 origin = Vec3.atLowerCornerOf(renderOrigin.getBlockPos());
        return worldAnchor.subtract(origin);
    }

    @Nullable
    public Vec3 getLinkedWorldAnchorPosition() {
        BeltWheelBlockEntity linkedWheel = resolveLinkedWheel();
        return linkedWheel == null ? null : linkedWheel.getWorldAnchorPosition();
    }

    private TransferDecision resolveTransferDecision(BeltWheelBlockEntity linkedWheel) {
        boolean thisHasIndependentSource = hasIndependentKineticSource(this);
        boolean linkedHasIndependentSource = hasIndependentKineticSource(linkedWheel);

        if (!thisHasIndependentSource && !linkedHasIndependentSource)
            return TransferDecision.NONE;

        if (!thisHasIndependentSource)
            return TransferDecision.RECEIVE;

        if (!linkedHasIndependentSource)
            return TransferDecision.DRIVE;

        double thisSpeed = Math.abs(getSpeed());
        double linkedSpeed = Math.abs(linkedWheel.getSpeed());
        final double EPSILON = 1.0E-4;

        if (linkedSpeed > thisSpeed + EPSILON)
            return TransferDecision.RECEIVE;
        if (thisSpeed > linkedSpeed + EPSILON)
            return TransferDecision.DRIVE;

        String thisKey = endpointKey(this);
        String linkedKey = endpointKey(linkedWheel);
        return thisKey.compareTo(linkedKey) > 0
            ? TransferDecision.RECEIVE
            : TransferDecision.DRIVE;
    }

    private void refreshLinkedStressNetworkValues() {
        if (level != null && !level.isClientSide && hasNetwork()) {
            float nextCapacity = calculateAddedStressCapacity();
            float nextStress = calculateStressApplied();

            if (!(Math.abs(nextCapacity - reportedLinkCapacity) <= 0.01F) || !(Math.abs(nextStress - reportedLinkStress) <= 0.01F)) {
                reportedLinkCapacity = nextCapacity;
                reportedLinkStress = nextStress;
                if (isSource())
                    notifyStressCapacityChange(nextCapacity);

                getOrCreateNetwork().updateStressFor(this, nextStress);
                getOrCreateNetwork().updateNetwork();
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide)
            indexWheel(this);
    }

    @Override
    public void remove() {
        super.remove();
        if (level != null && !level.isClientSide)
            unindexWheel(this);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return false;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);

        if (linkedPos != null)
            tag.putLong("LinkedPos", linkedPos.asLong());
        if (linkedSubLevelId != null)
            tag.putUUID("LinkedSubLevelId", linkedSubLevelId);

        tag.putBoolean("ReceivesFromLinkedWheel", receivesFromLinkedWheel);
        tag.putFloat("GeneratedLinkSpeed", generatedLinkSpeed);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        linkedPos = tag.contains("LinkedPos") ? BlockPos.of(tag.getLong("LinkedPos")) : null;
        linkedSubLevelId = tag.contains("LinkedSubLevelId") ? tag.getUUID("LinkedSubLevelId") : null;
        receivesFromLinkedWheel = tag.getBoolean("ReceivesFromLinkedWheel");
        generatedLinkSpeed = tag.contains("GeneratedLinkSpeed") ? tag.getFloat("GeneratedLinkSpeed") : 0.0F;

        linkValidated = false;
    }

    private static boolean hasIndependentKineticSource(BeltWheelBlockEntity be) {
        return be.hasSource() && !be.receivesFromLinkedWheel;
    }

    private static String endpointKey(BeltWheelBlockEntity be) {
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(be);
        String subLevel = subLevelId == null ? "world" : subLevelId.toString();
        BlockPos pos = be.getBlockPos();
        return subLevel + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private void playBeltBreakEffects(ServerLevel level, Vec3 start, Vec3 end) {
        BlockState particleState = AllBlocks.BELT.getDefaultState();
        for (int i = 0; i < 40; i++) {
            double t = i / 39.0;
            Vec3 pos = start.lerp(end, t);

            level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, particleState),
                pos.x, pos.y, pos.z,
                1,
                0.5, 0.5, 0.5,
                0.0
            );
        }

        level.playSound(null, BlockPos.containing(start), SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.playSound(null, BlockPos.containing(end), SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private enum TransferDecision {
        NONE(false),
        DRIVE(false),
        RECEIVE(true);

        private final boolean receiveFromLinkedWheel;
        TransferDecision(boolean receiveFromLinkedWheel) {
            this.receiveFromLinkedWheel = receiveFromLinkedWheel;
        }
    }
}
