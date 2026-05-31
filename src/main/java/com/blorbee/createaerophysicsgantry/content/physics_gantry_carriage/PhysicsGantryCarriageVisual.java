package com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage;

import com.blorbee.createaerophysicsgantry.registry.CAPGPartialModels;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.content.kinetics.base.ShaftVisual;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

public class PhysicsGantryCarriageVisual extends ShaftVisual<PhysicsGantryCarriageBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance gantryCogs;

    final Direction facing;
    final boolean alongFirst;
    final Direction.Axis rotationAxis;
    final float rotationMult;
    final BlockPos visualPos;
    final Direction mountedDir;

    private float lastAngle = Float.NaN;

    public PhysicsGantryCarriageVisual(VisualizationContext context, PhysicsGantryCarriageBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        gantryCogs = instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, Models.partial(CAPGPartialModels.PHYSICS_GANTRY_COGS))
            .createInstance();
        facing = blockState.getValue(PhysicsGantryCarriageBlock.FACING);
        alongFirst = blockState.getValue(PhysicsGantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE);
        rotationAxis = KineticBlockEntityRenderer.getRotationAxisOf(blockEntity);
        rotationMult = getRotationMultiplier(getGantryAxis(), facing);
        visualPos = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
            ? blockEntity.getBlockPos()
            : blockEntity.getBlockPos().relative(facing.getOpposite());
        mountedDir = PhysicsGantryCarriageBlockEntity.getMountedPayloadDirection(blockState);
        animateCogs(getCogAngle());
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        float cogAngle = getCogAngle();
        if (!Mth.equal(cogAngle, lastAngle)) {
            animateCogs(cogAngle);
            lastAngle = cogAngle;
        }
    }

    private float getCogAngle() {
        return PhysicsGantryCarriageRenderer.getAngleForBE(blockEntity, visualPos, rotationAxis) * rotationMult;
    }

    private void animateCogs(float cogAngle) {
        gantryCogs.setIdentityTransform()
            .translate(getVisualPosition())
            .center()
            .rotateYDegrees(AngleHelper.horizontalAngle(facing))
            .rotateXDegrees(facing == Direction.UP ? 0.0F : (facing == Direction.DOWN ? 180.0F : 90.0F))
            .rotateYDegrees(alongFirst ^ facing.getAxis() == Direction.Axis.X ? 0.0F : 90.0F)
            .translate(0.0F, -0.5625F, 0.0F)
            .rotateXDegrees(-cogAngle)
            .translate(0.0F, 0.5625F, 0.0F)
            .uncenter()
            .setChanged();
    }

    static float getRotationMultiplier(Direction.Axis gantryAxis, Direction facing) {
        float multiplier = 1.0F;
        if (gantryAxis == Direction.Axis.X && facing == Direction.UP)
            multiplier *= -1.0F;
        if (gantryAxis == Direction.Axis.Y && (facing == Direction.NORTH || facing == Direction.EAST))
            multiplier *= -1.0F;
        return multiplier;
    }

    private Direction.Axis getGantryAxis() {
        Direction.Axis gantryAxis = Direction.Axis.X;
        for (Direction.Axis axis : Iterate.axes) {
            if (axis != rotationAxis && axis != facing.getAxis())
                gantryAxis = axis;
        }
        return gantryAxis;
    }

    @Override
    public void updateLight(float partialTick) {
        relight(gantryCogs, rotatingModel);
    }

    @Override
    protected void _delete() {
        super._delete();
        gantryCogs.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(gantryCogs);
    }
}
