package com.blorbee.createaerophysicsgantry.compat.simulated;

import com.blorbee.createaerophysicsgantry.util.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public final class SimulatedHelper {
    @Nullable
    public static BlockEntity findBlockEntity(Level level, @Nullable UUID subLevelId, BlockPos pos) {
        if (level != null && pos != null) {
            if (subLevelId != null) {
                SubLevel subLevel = SubLevelBlockEntityCollector.getSubLevel(level, subLevelId);
                if (subLevel == null)
                    return level.getBlockEntity(pos);

                for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                    if (blockEntity != null && !blockEntity.isRemoved() && pos.equals(blockEntity.getBlockPos()))
                        return blockEntity;
                }

                return level.getBlockEntity(pos);
            } else {
                return level.getBlockEntity(pos);
            }
        } else {
            return null;
        }
    }

    @Nullable
    public static <T extends BlockEntity> T findBlockEntity(Level level, @Nullable UUID subLevelId, BlockPos pos, Class<T> type) {
        BlockEntity blockEntity = findBlockEntity(level, subLevelId, pos);
        return type.isInstance(blockEntity) ? type.cast(blockEntity) : null;
    }

    @Nullable
    public static BlockEntity findBlockEntityIncludingSubLevels(Level level, BlockPos pos) {
        SubLevel containingSubLevel = Sable.HELPER.getContaining(level, pos);
        return Sable.HELPER.runIncludingSubLevels(level, pos.getCenter(), Boolean.TRUE, containingSubLevel,
            (subLevel, internalPos) -> findBlockEntityInAccess(level, subLevel, internalPos));
    }

    @Nullable
    public static <T extends BlockEntity> T findBlockEntityIncludingSubLevels(Level level, BlockPos pos, Class<T> type) {
        BlockEntity blockEntity = findBlockEntityIncludingSubLevels(level, pos);
        return type.isInstance(blockEntity) ? type.cast(blockEntity) : null;
    }

    @Nullable
    public static BlockEntity findBlockEntityInAccess(Level level, @Nullable SubLevel subLevel, BlockPos internalPos) {
        if (subLevel == null)
            return level.getBlockEntity(internalPos);
        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
            if (blockEntity != null && !blockEntity.isRemoved() && internalPos.equals(blockEntity.getBlockPos()))
                return blockEntity;
        }
        return null;
    }

    public static Vec3 toContainingWorldDirection(BlockEntity blockEntity, Vec3 localDirection) {
        if (localDirection == null)
            return null;
        if (localDirection.lengthSqr() < 1.0E-6)
            return Vec3.ZERO;

        SubLevel subLevel = Sable.HELPER.getContaining(blockEntity);
        if (subLevel == null)
            return localDirection.normalize();

        Pose3d pose = subLevel.logicalPose();
        Vec3 vec = pose.transformNormal(localDirection);
        return vec.normalize();
    }

    public static Vec3 toContainingWorldPosition(BlockEntity blockEntity, Vec3 localPosition) {
        if (localPosition == null) {
            return null;
        }

        SubLevel subLevel = Sable.HELPER.getContaining(blockEntity);
        if (subLevel == null)
            return localPosition;

        Pose3d pose = subLevel.logicalPose();
        return pose.transformPosition(localPosition);
    }

    @Nullable
    public static Vec3 toContainingLocalPosition(SubLevel subLevel, Vec3 worldPosition) {
        if (subLevel != null && worldPosition != null) {
            return subLevel.logicalPose().transformPositionInverse(worldPosition);
        } else {
            return null;
        }
    }

    @Nullable
    public static UUID getContainingSubLevelId(BlockEntity blockEntity) {
        SubLevel subLevel = Sable.HELPER.getContaining(blockEntity);
        if (subLevel == null)
            return null;
        return subLevel.getUniqueId();
    }

    @Nullable
    public static SubLevel getContainingSubLevel(BlockEntity blockEntity) {
        return blockEntity == null ? null : Sable.HELPER.getContaining(blockEntity);
    }

    public static Vec3 projectOutOfSubLevel(Level level, Vec3 position) {
        return position == null ? null : Sable.HELPER.projectOutOfSubLevel(level, position);
    }

    public static Vec3 projectOutOfSubLevels(Level level, Vec3 position) {
        if (level != null && position != null) {
            Vec3 projected = position;

            for (int i = 0; i < 8; i++) {
                Vec3 next = projectOutOfSubLevel(level, projected);
                if (next == null || next.distanceToSqr(projected) <= 1.0E-12) {
                    return projected;
                }

                projected = next;
            }

            return projected;
        } else {
            return position;
        }
    }

    public static Vec3 toGlobalWorldPosition(BlockEntity blockEntity, Vec3 localPosition) {
        if (blockEntity != null && localPosition != null) {
            Vec3 projected = toContainingWorldPosition(blockEntity, localPosition);
            return projectOutOfSubLevels(blockEntity.getLevel(), projected);
        } else {
            return localPosition;
        }
    }

    public static Vec3 toRenderFramePosition(BlockEntity blockEntity, Vec3 localPosition, BlockEntity renderOrigin) {
        if (blockEntity != null && localPosition != null && renderOrigin != null) {
            Vec3 global = toGlobalWorldPosition(blockEntity, localPosition);
            SubLevel renderSubLevel = getContainingSubLevel(renderOrigin);
            if (renderSubLevel == null) {
                return global;
            }

            Vec3 local = toContainingLocalPosition(renderSubLevel, global);
            return local == null ? global : local;
        } else {
            return localPosition;
        }
    }
}
