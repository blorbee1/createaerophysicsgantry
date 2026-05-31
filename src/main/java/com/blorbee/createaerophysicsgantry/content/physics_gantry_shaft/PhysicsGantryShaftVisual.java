package com.blorbee.createaerophysicsgantry.content.physics_gantry_shaft;

import com.blorbee.createaerophysicsgantry.registry.CAPGPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class PhysicsGantryShaftVisual extends OrientedRotatingVisual<PhysicsGantryShaftBlockEntity> {
    private PhysicsGantryShaftVisual(VisualizationContext context, PhysicsGantryShaftBlockEntity blockEntity, float partialTick, Direction facing, Model model) {
        super(context, blockEntity, partialTick, Direction.UP, facing, model);
    }

    public static BlockEntityVisual<? super PhysicsGantryShaftBlockEntity> create(
        VisualizationContext context, PhysicsGantryShaftBlockEntity blockEntity, float particalTick
    ) {
        BlockState state = blockEntity.getBlockState();
        PhysicsGantryShaftBlock.Part part = state.getValue(PhysicsGantryShaftBlock.PART);
        boolean powered = state.getValue(PhysicsGantryShaftBlock.POWERED);
        Direction facing = state.getValue(PhysicsGantryShaftBlock.FACING);
        boolean flipped = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE;

        Model model = Models.partial(getPartial(part, powered, flipped));
        return new PhysicsGantryShaftVisual(context, blockEntity, particalTick, facing, model);
    }

    private static PartialModel getPartial(PhysicsGantryShaftBlock.Part part, boolean powered, boolean flipped) {
        return switch (part) {
            case START -> powered
                ? (flipped ? CAPGPartialModels.GANTRY_SHAFT_START_POWERED_FLIPPED : CAPGPartialModels.GANTRY_SHAFT_START_POWERED)
                : (flipped ? CAPGPartialModels.GANTRY_SHAFT_START_FLIPPED : CAPGPartialModels.GANTRY_SHAFT_START);
            case MIDDLE -> powered
                ? (flipped ? CAPGPartialModels.GANTRY_SHAFT_MIDDLE_POWERED_FLIPPED : CAPGPartialModels.GANTRY_SHAFT_MIDDLE_POWERED)
                : (flipped ? CAPGPartialModels.GANTRY_SHAFT_MIDDLE_FLIPPED : CAPGPartialModels.GANTRY_SHAFT_MIDDLE);
            case END -> powered
                ? (flipped ? CAPGPartialModels.GANTRY_SHAFT_END_POWERED_FLIPPED : CAPGPartialModels.GANTRY_SHAFT_END_POWERED)
                : (flipped ? CAPGPartialModels.GANTRY_SHAFT_END_FLIPPED : CAPGPartialModels.GANTRY_SHAFT_END);
            case SINGLE -> powered
                ? (flipped ? CAPGPartialModels.GANTRY_SHAFT_SINGLE_POWERED_FLIPPED : CAPGPartialModels.GANTRY_SHAFT_SINGLE_POWERED)
                : (flipped ? CAPGPartialModels.GANTRY_SHAFT_SINGLE_FLIPPED : CAPGPartialModels.GANTRY_SHAFT_SINGLE);
        };
    }
}
