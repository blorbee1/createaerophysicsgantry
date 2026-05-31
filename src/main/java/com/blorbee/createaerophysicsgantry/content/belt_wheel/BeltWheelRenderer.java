package com.blorbee.createaerophysicsgantry.content.belt_wheel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.render.RenderTypes;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class BeltWheelRenderer extends KineticBlockEntityRenderer<BeltWheelBlockEntity> {
    private static final float BELT_LOOP_RADIUS_MIN = 0.08F;
    private static final float BELT_LOOP_RADIUS_MAX = 0.28F;

    private static final float BELT_HALF_WIDTH_NEAR = 0.125F;
    private static final float BELT_HALF_WIDTH_FAR = 0.09375F;

    private static final ResourceLocation BELT_TEXTURE = ResourceLocation.fromNamespaceAndPath("create", "textures/block/belt.png");

    public BeltWheelRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(BeltWheelBlockEntity be, BlockState state) {
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        return CachedBuffers.partialFacingVertical(AllPartialModels.SHAFTLESS_COGWHEEL, state,
            Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
    }

    @Override
    protected void renderSafe(BeltWheelBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        BlockState state = be.getBlockState();
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        Direction shaftFacing = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);

        SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, shaftFacing);
        float angle = getAngleForBE(be, be.getBlockPos(), axis) / 180.0F * (float) Math.PI;
        kineticRotationTransform(shaft, be, axis, angle, light).renderInto(ms, buffer.getBuffer(RenderType.solid()));

        if (be.shouldRenderLinkFromThisEndpoint()) {
            BeltWheelBlockEntity linkedWheel = be.resolveLinkedWheel();
            if (linkedWheel != null) {
                Vec3 startWorld = be.getWorldAnchorPosition();
                Vec3 endWorld = linkedWheel.getWorldAnchorPosition();
                double distance = startWorld.distanceTo(endWorld);

                if (!(distance < 0.01)) {
                    Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos());
                    Vec3 startFrame = be.getAnchorPositionInRenderFrameOf(be);
                    Vec3 endFrame = linkedWheel.getAnchorPositionInRenderFrameOf(be);
                    Vec3 start = startFrame.subtract(origin);
                    Vec3 end = endFrame.subtract(origin);

                    if (isRenderableLocalEndpoint(start) && isRenderableLocalEndpoint(end) && !(start.distanceToSqr(end) > 9216.0)) {
                        renderLoopBelt(be, ms, buffer, start, end, be.getBlockState().getValue(BlockStateProperties.AXIS), startWorld, endWorld, overlay);
                    }
                }
            }
        }
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull BeltWheelBlockEntity be) {
        if (!be.shouldRenderLinkFromThisEndpoint())
            return super.getRenderBoundingBox(be);

        Vec3 start = be.getWorldAnchorPosition();
        Vec3 end = be.getLinkedWorldAnchorPosition();
        if (start == null || end == null)
            return super.getRenderBoundingBox(be);

        return new AABB(
            Math.min(start.x, end.x) - 1, Math.min(start.y, end.y) - 1, Math.min(start.z, end.z) - 1,
            Math.max(start.x, end.x) + 1, Math.max(start.y, end.y) + 1, Math.max(start.z, end.z) + 1
        );
    }

    @Override
    public boolean shouldRenderOffScreen(BeltWheelBlockEntity blockEntity) {
        return true;
    }

    private static float getAngleForBE(KineticBlockEntity be, BlockPos pos, Direction.Axis axis) {
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, pos, axis);
        return (time * be.getSpeed() * 3.0F / 20.0F + offset) % 360.0F;
    }

    private static boolean isRenderableLocalEndpoint(Vec3 point) {
        return point != null
            && Double.isFinite(point.x)
            && Double.isFinite(point.y)
            && Double.isFinite(point.z)
            && Math.abs(point.x) < 128.0
            && Math.abs(point.y) < 128.0
            && Math.abs(point.z) < 128.0;
    }

    private static void renderLoopBelt(
        BeltWheelBlockEntity be,
        PoseStack ms,
        MultiBufferSource buffer,
        Vec3 start, Vec3 end,
        Direction.Axis axis,
        Vec3 startWorld, Vec3 endWorld,
        int overlay
    ) {
        Vec3 axisVec = switch (axis) {
            case X -> new Vec3(1.0, 0.0, 0.0);
            case Y -> new Vec3(0.0, 1.0, 0.0);
            case Z -> new Vec3(0.0, 0.0, 1.0);
        };

        Vec3 centerDelta = end.subtract(start);
        Vec3 projected = centerDelta.subtract(axisVec.scale(centerDelta.dot(axisVec)));

        if (!(projected.lengthSqr() <= 1.0E-6)) {
            Vec3 line = projected.normalize();
            Vec3 side = axisVec.cross(line).normalize();

            if (!(side.lengthSqr() <= 1.0E-6)) {
                float distance = (float) projected.length();
                float radius = Math.clamp(distance * 0.45F, BELT_LOOP_RADIUS_MIN, BELT_LOOP_RADIUS_MAX);

                Vec3 aTop = start.add(side.scale(radius));
                Vec3 bTop = end.add(side.scale(radius));
                Vec3 bBottom = end.subtract(side.scale(radius));
                Vec3 aBottom = start.subtract(side.scale(radius));

                List<Vec3> loopPoints = new ArrayList<>();

                loopPoints.add(aTop);
                loopPoints.add(bTop);
                appendArc(loopPoints, end, side, line, radius, 0.0F, (float) Math.PI,
                    Mth.clamp((int)(radius * 56.0F), 12, 32));

                loopPoints.add(bBottom);
                loopPoints.add(aBottom);
                appendArc(loopPoints, start, side, line, radius, (float) Math.PI, (float) (Math.PI * 2),
                    Mth.clamp((int)(radius * 56.0F), 12, 32));

                float animation = 0.0F;
                float speed = be.getSpeed();
                float absSpeed = Math.abs(speed);
                float directionSign = speed >= 0.0F ? 1.0F : -1.0F;
                if (absSpeed > 1.0E-4F) {
                    float time = AnimationTickHolder.getRenderTime(be.getLevel()) / (360.0F / absSpeed);
                    time %= 1.0F;
                    if (time < 0.0F)
                        time++;
                    animation = (time - 0.5F) * directionSign;
                }

                Minecraft minecraft = Minecraft.getInstance();
                Vec3 mid = startWorld.lerp(endWorld, 0.5);
                boolean far = minecraft.level == be.getLevel() &&
                    !minecraft.getBlockEntityRenderDispatcher().camera.getPosition().closerThan(mid, 48.0);

                VertexConsumer beltConsumer = buffer.getBuffer(RenderTypes.chain(BELT_TEXTURE));
                Vec3 worldOrigin = Vec3.atLowerCornerOf(be.getBlockPos());
                float uvCursor = animation;

                for (int i = 1; i < loopPoints.size(); i++) {
                    Vec3 from = loopPoints.get(i - 1);
                    Vec3 to = loopPoints.get(i);
                    Vec3 tangent = to.subtract(from);

                    if (!(tangent.lengthSqr() <= 1.0E-8)) {
                        float segmentLength = (float) tangent.length();

                        Vec3 fromWorld = from.add(worldOrigin);
                        Vec3 toWorld = to.add(worldOrigin);

                        int lightFrom = LevelRenderer.getLightColor(be.getLevel(), BlockPos.containing(fromWorld));
                        int lightTo = LevelRenderer.getLightColor(be.getLevel(), BlockPos.containing(toWorld));

                        renderBeltSegment(ms, beltConsumer, from, to, axisVec, uvCursor, uvCursor + segmentLength,
                            lightFrom, lightTo, far, overlay);
                        uvCursor += segmentLength * directionSign;
                    }
                }
            }
        }
    }

    private static void appendArc(List<Vec3> points, Vec3 center, Vec3 side, Vec3 line, float radius, float startAngle, float endAngle, int segments) {
        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            float angle = Mth.lerp(t, startAngle, endAngle);
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            Vec3 point = center.add(side.scale(radius * c)).add(line.scale(radius * s));
            points.add(point);
        }
    }

    private static void renderBeltSegment(
        PoseStack ms,
        VertexConsumer consumer,
        Vec3 from, Vec3 to, Vec3 axisVec,
        float minV, float maxV,
        int lightFrom, int lightTo,
        boolean far,
        int overlay
    ) {
        Vec3 delta = to.subtract(from);
        if (!(delta.lengthSqr() <= 1.0E-10)) {
            Vec3 direction = delta.normalize();
            Vec3 side = axisVec.cross(direction);

            if (side.lengthSqr() <= 1.0E-10) {
                side = direction.cross(new Vec3(0.0, 1.0, 0.0));
            }
            if (side.lengthSqr() <= 1.0E-10) {
                side = direction.cross(new Vec3(1.0, 0.0, 0.0));
            }
            side = side.normalize();
            Vec3 up = direction.cross(side).normalize();

            float halfWidth = far ? BELT_HALF_WIDTH_FAR : BELT_HALF_WIDTH_NEAR;
            float halfThickness = halfWidth * 0.75F;

            Vec3 center = from.add(delta.scale(0.5));
            Vec3 along = direction.scale(delta.length() * 0.5);
            Vec3 across = side.scale(halfWidth);
            Vec3 vertical = up.scale(halfThickness);
            Vec3 p000 = center.subtract(along).subtract(across).subtract(vertical);
            Vec3 p001 = center.subtract(along).subtract(across).add(vertical);
            Vec3 p010 = center.subtract(along).add(across).subtract(vertical);
            Vec3 p011 = center.subtract(along).add(across).add(vertical);
            Vec3 p100 = center.add(along).subtract(across).subtract(vertical);
            Vec3 p101 = center.add(along).subtract(across).add(vertical);
            Vec3 p110 = center.add(along).add(across).subtract(vertical);
            Vec3 p111 = center.add(along).add(across).add(vertical);

            float uvStart = minV;
            float uvEnd = maxV;
            float widthU = 0.5F;

            renderFace(ms, consumer, p001, p101, p111, p011, uvStart, uvEnd, 0.0F, widthU, lightFrom, lightTo, overlay);
            renderFace(ms, consumer, p000, p100, p110, p010, uvStart, uvEnd, 0.0F, widthU, lightFrom, lightTo, overlay);
            renderFace(ms, consumer, p000, p100, p101, p001, uvStart, uvEnd, 0.0F, widthU, lightFrom, lightTo, overlay);
            renderFace(ms, consumer, p010, p110, p111, p011, uvStart, uvEnd, 0.0F, widthU, lightFrom, lightTo, overlay);
            renderFace(ms, consumer, p000, p001, p011, p010, 0.0F, widthU, 0.0F, widthU, lightFrom, lightTo, overlay);
            renderFace(ms, consumer, p100, p110, p111, p101, 0.0F, widthU, 0.0F, widthU, lightFrom, lightTo, overlay);
        }
    }

    private static void renderFace(
        PoseStack ms,
        VertexConsumer consumer,
        Vec3 a, Vec3 b, Vec3 c, Vec3 d,
        float minU, float maxU,
        float minV, float maxV,
        int lightFrom, int lightTo,
        int overlay
    ) {
        Matrix4f pose = ms.last().pose();
        PoseStack.Pose normalPose = ms.last();

        Vec3 edge1 = b.subtract(a);
        Vec3 edge2 = d.subtract(a);
        Vec3 normal = edge1.cross(edge2).normalize();
        if (normal.lengthSqr() <= 1.0E-10) {
            normal = new Vec3(0.0, 1.0, 0.0);
        }

        Vec3 backNormal = new Vec3(-normal.x, -normal.y, -normal.z);
        addVertex(pose, normalPose, consumer, a, minU, minV, normal, lightFrom, overlay);
        addVertex(pose, normalPose, consumer, b, maxU, minV, normal, lightFrom, overlay);
        addVertex(pose, normalPose, consumer, c, maxU, maxV, normal, lightTo, overlay);
        addVertex(pose, normalPose, consumer, d, minU, maxV, normal, lightTo, overlay);
        addVertex(pose, normalPose, consumer, d, minU, maxV, backNormal, lightTo, overlay);
        addVertex(pose, normalPose, consumer, c, maxU, maxV, backNormal, lightTo, overlay);
        addVertex(pose, normalPose, consumer, b, maxU, minV, backNormal, lightFrom, overlay);
        addVertex(pose, normalPose, consumer, a, minU, minV, backNormal, lightFrom, overlay);
    }

    private static void addVertex(
        Matrix4f pose,
        PoseStack.Pose normalPose,
        VertexConsumer consumer,
        Vec3 pos,
        float u, float v,
        Vec3 normal,
        int light, int overlay
    ) {
        consumer.addVertex(pose, (float)pos.x, (float)pos.y, (float)pos.z)
            .setColor(1.0F, 1.0F, 1.0F, 1.0F)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(normalPose, (float)normal.x, (float)normal.y, (float)normal.z);
    }
}
