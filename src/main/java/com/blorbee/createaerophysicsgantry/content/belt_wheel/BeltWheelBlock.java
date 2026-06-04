package com.blorbee.createaerophysicsgantry.content.belt_wheel;

import com.blorbee.createaerophysicsgantry.compat.simulated.SimulatedHelper;
import com.blorbee.createaerophysicsgantry.config.ServerConfig;
import com.blorbee.createaerophysicsgantry.registry.CAPGBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BeltWheelBlock extends RotatedPillarKineticBlock implements IBE<BeltWheelBlockEntity>, ICogWheel {
    private static final String TRANSLATE_KEY_PREFIX = "createaerophysicsgantry.belt_wheel";

    public static final BooleanProperty TOP_SHAFT = BooleanProperty.create("top_shaft");
    public static final BooleanProperty BOTTOM_SHAFT = BooleanProperty.create("bottom_shaft");

    private static final Map<UUID, BlockPos> PENDING_LINK_POSITIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PENDING_LINK_SUBLEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PENDING_LINK_EXPIRY = new ConcurrentHashMap<>();

    public BeltWheelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TOP_SHAFT, true).setValue(BOTTOM_SHAFT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(TOP_SHAFT, BOTTOM_SHAFT));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof BeltWheelBlockEntity local) {
            if (!level.isClientSide && isShears(stack)) {
                if (local.hasLinkedTarget()) {
                    local.breakLink(true);

                    if (player instanceof ServerPlayer serverPlayer) {
                        stack.hurtAndBreak(1, serverPlayer, hand == InteractionHand.MAIN_HAND
                            ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                    }

                    notify(player, TRANSLATE_KEY_PREFIX + ".link_broken", ChatFormatting.YELLOW);
                    return ItemInteractionResult.SUCCESS;
                } else {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            } else if (!(stack.getItem() instanceof BeltConnectorItem)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            } else {
                return level.isClientSide ? ItemInteractionResult.SUCCESS : handleBeltConnectorUse(level, player, stack, local);
            }
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
    }

    private static ItemInteractionResult handleBeltConnectorUse(Level level, Player player, ItemStack stack, BeltWheelBlockEntity clickedWheel) {
        UUID playerId = player.getUUID();
        long now = level.getGameTime();

        Long expiresAt = PENDING_LINK_EXPIRY.get(playerId);
        if (expiresAt != null && expiresAt < now) {
            clearPendingSelection(playerId);
        }

        BlockPos pendingPos = PENDING_LINK_POSITIONS.get(playerId);
        UUID pendingSubLevel = PENDING_LINK_SUBLEVELS.get(playerId);
        UUID clickedSubLevel = SimulatedHelper.getContainingSubLevelId(clickedWheel);

        if (pendingPos == null) {
            setPendingSelection(playerId, clickedWheel.getBlockPos(), clickedSubLevel, now + 600L);
            notify(player, TRANSLATE_KEY_PREFIX + ".link_first", ChatFormatting.GRAY);
            return ItemInteractionResult.SUCCESS;
        }

        if (pendingPos.equals(clickedWheel.getBlockPos()) && Objects.equals(pendingSubLevel, clickedSubLevel)) {
            clearPendingSelection(playerId);
            notify(player, TRANSLATE_KEY_PREFIX + ".link_cleared", ChatFormatting.YELLOW);
            return ItemInteractionResult.SUCCESS;
        }

        BeltWheelBlockEntity firstWheel = SimulatedHelper.findBlockEntity(level, pendingSubLevel,
            pendingPos, BeltWheelBlockEntity.class);
        if (firstWheel == null || firstWheel.isRemoved()) {
            clearPendingSelection(playerId);
            notify(player, TRANSLATE_KEY_PREFIX + ".link_missing", ChatFormatting.RED);
            return ItemInteractionResult.SUCCESS;
        }

        if (firstWheel.getBlockPos().equals(clickedWheel.getBlockPos())
            && Objects.equals(SimulatedHelper.getContainingSubLevelId(firstWheel), clickedSubLevel)) {
            clearPendingSelection(playerId);
            notify(player, TRANSLATE_KEY_PREFIX + ".link_self", ChatFormatting.RED);
            return ItemInteractionResult.FAIL;
        }

        Direction.Axis shaftAxis = firstWheel.getBlockState().getValue(AXIS);
        if (shaftAxis != clickedWheel.getBlockState().getValue(AXIS)) {
            clearPendingSelection(playerId);
            notify(player, TRANSLATE_KEY_PREFIX + ".axis_mismatch", ChatFormatting.RED);
            return ItemInteractionResult.FAIL;
        }

        Vec3 firstAnchor = firstWheel.getWorldAnchorPosition();
        Vec3 secondAnchor = clickedWheel.getWorldAnchorPosition();
        if (firstAnchor != null && secondAnchor != null) {
            Vec3 delta = secondAnchor.subtract(firstAnchor);
            double distance = delta.length();
            int maxDistance = getConfiguredMaxDistance();

            if (distance <= 0.01 || distance > maxDistance) {
                clearPendingSelection(playerId);
                notify(player, TRANSLATE_KEY_PREFIX + ".distance_invalid", ChatFormatting.RED);
                return ItemInteractionResult.FAIL;
            }

            if (!isAxisAligned(delta)) {
                clearPendingSelection(playerId);
                notify(player, TRANSLATE_KEY_PREFIX + ".not_straight", ChatFormatting.RED);
                return ItemInteractionResult.FAIL;
            }

            Direction.Axis connectionAxis = getConnectionAxis(delta);
            if (connectionAxis == shaftAxis) {
                clearPendingSelection(playerId);
                notify(player, TRANSLATE_KEY_PREFIX + ".axis_mismatch", ChatFormatting.RED);
                return ItemInteractionResult.FAIL;
            }

            UUID firstSubLevel = SimulatedHelper.getContainingSubLevelId(firstWheel);

            firstWheel.breakLink(true);
            clickedWheel.breakLink(true);

            firstWheel.setLinkedTarget(clickedWheel.getBlockPos(), clickedSubLevel);
            clickedWheel.setLinkedTarget(firstWheel.getBlockPos(), firstSubLevel);

            level.playSound(null, clickedWheel.getBlockPos(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.playSound(null, firstWheel.getBlockPos(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            clearPendingSelection(playerId);
            notify(player, TRANSLATE_KEY_PREFIX + ".link_success", ChatFormatting.GREEN);
            return ItemInteractionResult.SUCCESS;
        } else {
            notify(player, TRANSLATE_KEY_PREFIX + ".link_missing", ChatFormatting.RED);
            return ItemInteractionResult.FAIL;
        }
    }

    private static void setPendingSelection(UUID playerId, BlockPos pos, @Nullable UUID subLevelId, long expiresAtTick) {
        PENDING_LINK_POSITIONS.put(playerId, pos.immutable());
        if (subLevelId == null)
            PENDING_LINK_SUBLEVELS.remove(playerId);
        else
            PENDING_LINK_SUBLEVELS.put(playerId, subLevelId);
        PENDING_LINK_EXPIRY.put(playerId, expiresAtTick);
    }

    private static void clearPendingSelection(UUID playerId) {
        PENDING_LINK_POSITIONS.remove(playerId);
        PENDING_LINK_SUBLEVELS.remove(playerId);
        PENDING_LINK_EXPIRY.remove(playerId);
    }

    private static boolean isAxisAligned(Vec3 delta) {
        double length = delta.length();
        if (length <= 1.0E-6)
            return false;

        Vec3 unit = delta.scale(1.0 / length);
        double x = Math.abs(unit.x);
        double y = Math.abs(unit.y);
        double z = Math.abs(unit.z);
        return x >= 0.985 || y >= 0.985 || z >= 0.985;
    }

    private static void notify(Player player, String key, ChatFormatting formatting) {
        Component msg = Component.translatable(key).withStyle(formatting);
        player.displayClientMessage(msg, true);
    }

    public static int getConfiguredMaxDistance() {
        int configured = ServerConfig.BELT_WHEEL_MAX_DISTANCE.get();
        return Math.clamp(configured, 1, 64);
    }

    private static boolean isShears(ItemStack stack) {
        return stack.getItem() instanceof ShearsItem || stack.is(Items.SHEARS);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving) {
            withBlockEntityDo(level, pos, be -> be.breakLink(true));
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    public static Direction.Axis getConnectionAxis(Vec3 delta) {
        double ax = Math.abs(delta.x);
        double ay = Math.abs(delta.y);
        double az = Math.abs(delta.z);

        if (ax >= ay && ax >= az)
            return Direction.Axis.X;
        if (ay >= az)
            return Direction.Axis.Y;
        return Direction.Axis.Z;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean isSmallCog() {
        return true;
    }

    @Override
    public boolean isLargeCog() {
        return false;
    }

    @Override
    public boolean isDedicatedCogWheel() {
        return true;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public Class<BeltWheelBlockEntity> getBlockEntityClass() {
        return BeltWheelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BeltWheelBlockEntity> getBlockEntityType() {
        return CAPGBlockEntityTypes.BELT_WHEEL.get();
    }
}
