package com.blorbee.createaerophysicsgantry.content.belt_wheel;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class BeltWheelVisual extends KineticBlockEntityVisual<BeltWheelBlockEntity> {
    private final RotatingInstance rotatingModel;
    private final RotatingInstance rotatingTopShaft;
    private final RotatingInstance rotatingBottomShaft;

    public BeltWheelVisual(VisualizationContext context, BeltWheelBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        BlockState state = blockEntity.getBlockState();
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);

        rotatingModel = instancerProvider()
            .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFTLESS_COGWHEEL))
            .createInstance();
        rotatingModel.setup(blockEntity)
            .setPosition(getVisualPosition())
            .rotateToFace(axis)
            .setChanged();

        RotatingInstance topShaft = null;
        RotatingInstance bottomShaft = null;
        for (Direction direction : Iterate.directionsInAxis(axis)) {
            boolean hasShaft = blockEntity.getBlockState()
                .getValue(direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? BeltWheelBlock.TOP_SHAFT : BeltWheelBlock.BOTTOM_SHAFT);

            if (hasShaft) {
                RotatingInstance shaft = instancerProvider()
                    .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                    .createInstance();
                shaft.setup(blockEntity)
                    .setPosition(getVisualPosition())
                    .rotateToFace(Direction.SOUTH, direction)
                    .setChanged();

                if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                    topShaft = shaft;
                } else {
                    bottomShaft = shaft;
                }
            }
        }

        rotatingTopShaft = topShaft;
        rotatingBottomShaft = bottomShaft;
    }

    @Override
    public void update(float partialTick) {
        rotatingModel.setup(blockEntity).setChanged();
        if (rotatingTopShaft != null) {
            rotatingTopShaft.setup(blockEntity).setChanged();
        }
        if (rotatingBottomShaft != null) {
            rotatingBottomShaft.setup(blockEntity).setChanged();
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(rotatingModel);
        if (rotatingTopShaft != null) {
            consumer.accept(rotatingTopShaft);
        }
        if (rotatingBottomShaft != null) {
            consumer.accept(rotatingBottomShaft);
        }
    }

    @Override
    public void updateLight(float partialTick) {
        relight(rotatingModel, rotatingTopShaft, rotatingBottomShaft);
    }

    @Override
    protected void _delete() {
        rotatingModel.delete();
        if (rotatingTopShaft != null) {
            rotatingTopShaft.delete();
        }
        if (rotatingBottomShaft != null) {
            rotatingBottomShaft.delete();
        }
    }
}
