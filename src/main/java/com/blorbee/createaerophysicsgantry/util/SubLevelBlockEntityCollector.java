package com.blorbee.createaerophysicsgantry.util;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;
import java.util.*;

public final class SubLevelBlockEntityCollector {
    @Nullable
    public static SubLevel getSubLevel(Level level, @Nullable UUID subLevelId) {
        if (level == null || level.isClientSide)
            return null;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null)
            return null;
        return container.getSubLevel(subLevelId);
    }

    public static List<BlockEntity> getBlockEntities(@Nullable SubLevel subLevel) {
        Map<BlockPos, BlockEntity> blockEntities = new LinkedHashMap<>();
        if (subLevel == null)
            return List.of();

        try {
            LevelPlot plot = subLevel.getPlot();
            collectActorBlockEntities(plot, blockEntities);

            Collection<PlotChunkHolder> loadedChunks = plot.getLoadedChunks();
            if (loadedChunks == null)
                return new ArrayList<>(blockEntities.values());

            for (PlotChunkHolder chunkHolder : loadedChunks) {
                LevelChunk chunk = chunkHolder.getChunk();
                Map<BlockPos, BlockEntity> blockEntityMap = chunk.getBlockEntities();
                for (BlockEntity blockEntity : blockEntityMap.values()) {
                    blockEntities.putIfAbsent(blockEntity.getBlockPos().immutable(), blockEntity);
                }
            }
        } catch (Exception ignored) {
            return new ArrayList<>(blockEntities.values());
        }
        return new ArrayList<>(blockEntities.values());
    }

    private static void collectActorBlockEntities(LevelPlot plot, Map<BlockPos, BlockEntity> blockEntities) {
        Iterable<BlockEntitySubLevelActor> actors = plot.getBlockEntityActors();

        for (BlockEntitySubLevelActor actor : actors) {
            if (actor instanceof BlockEntity blockEntity) {
                blockEntities.put(blockEntity.getBlockPos().immutable(), blockEntity);
            }
        }
    }

    public static List<BlockEntity> getLoadedWorldBlockEntities(@Nullable Level level, BlockPos center, int chunkRadius) {
        List<BlockEntity> blockEntities = new ArrayList<>();
        if (level != null && center != null) {
            int centerChunkX = center.getX() >> 4;
            int centerChunkZ = center.getZ() >> 4;

            for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
                for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                    if (level.hasChunk(chunkX, chunkZ)) {
                        blockEntities.addAll(level.getChunk(chunkX, chunkZ).getBlockEntities().values());
                    }
                }
            }

            return blockEntities;
        } else {
            return blockEntities;
        }
    }
}
