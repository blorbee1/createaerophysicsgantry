package com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage;

import com.blorbee.createaerophysicsgantry.registry.CAPGPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class PhysicsGantryCarriageRenderer extends KineticBlockEntityRenderer<PhysicsGantryCarriageBlockEntity> {
    public PhysicsGantryCarriageRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(PhysicsGantryCarriageBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            BlockState state = be.getBlockState();
            Direction facing = state.getValue(PhysicsGantryCarriageBlock.FACING);
            boolean alongFirst = state.getValue(PhysicsGantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE);

            Direction.Axis rotationAxis = getRotationAxisOf(be);
            BlockPos visualPos = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? be.getBlockPos() : be.getBlockPos().relative(facing.getOpposite());
            float angleForBe = getAngleForBE(be, visualPos, rotationAxis);
            Direction.Axis gantryAxis = Direction.Axis.X;

            for (Direction.Axis axis : Iterate.axes) {
                if (axis != rotationAxis && axis != facing.getAxis())
                    gantryAxis = axis;
            }

            if (gantryAxis == Direction.Axis.X && facing == Direction.UP)
                angleForBe *= -1.0F;
            if (gantryAxis == Direction.Axis.Y && (facing == Direction.NORTH || facing == Direction.EAST))
                angleForBe *= -1.0F;

            SuperByteBuffer cogs = CachedBuffers.partial(CAPGPartialModels.PHYSICS_GANTRY_COGS, state);
            cogs.center()
                .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                .rotateXDegrees(facing == Direction.UP ? 0.0F : (facing == Direction.DOWN ? 180.0F : 90.0F))
                .rotateYDegrees(alongFirst ^ facing.getAxis() == Direction.Axis.X ? 0.0F : 90.0F)
                .translate(0.0F, -0.5625F, 0.0F)
                .rotateXDegrees(-angleForBe)
                .translate(0.0F, 0.5625F, 0.0F)
                .uncenter();
            cogs.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));

            Direction mountedDir = PhysicsGantryCarriageBlockEntity.getMountedPayloadDirection(state);
            Direction.Axis mountedAxis = mountedDir.getAxis();

            SuperByteBuffer outputShaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, mountedDir);
            outputShaft.translate(mountedDir.getStepX() * 0.0625F, mountedDir.getStepY() * 0.0625F, mountedDir.getStepZ() * 0.0625F);

            float shaftAngle = getAngleForBE(be, be.getBlockPos(), mountedAxis) / 180.0F * (float) Math.PI;
            kineticRotationTransform(outputShaft, be, mountedAxis, shaftAngle, light);
            outputShaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }

    public static float getAngleForBE(KineticBlockEntity be, BlockPos pos, Direction.Axis axis) {
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, pos, axis);
        return (time * be.getSpeed() * 3.0F / 20.0F + offset) % 360.0F;
    }

    @Override
    protected BlockState getRenderedBlockState(PhysicsGantryCarriageBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }
}
