package com.blorbee.createaerophysicsgantry.content.belt_wheel;

import com.blorbee.createaerophysicsgantry.compat.simulated.SimulatedHelper;
import com.blorbee.createaerophysicsgantry.config.ServerConfig;
import com.blorbee.createaerophysicsgantry.registry.CAPGBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
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
import java.util.Objects;
import java.util.UUID;

public class BeltWheelBlock extends RotatedPillarKineticBlock implements IBE<BeltWheelBlockEntity>, ICogWheel, IWrenchable {
    public static final String TRANSLATE_KEY_PREFIX = "createaerophysicsgantry.belt_wheel";
    private static final String SELECTION_TAG = "BeltWheelFirst";

    public static final BooleanProperty TOP_SHAFT = BooleanProperty.create("top_shaft");
    public static final BooleanProperty BOTTOM_SHAFT = BooleanProperty.create("bottom_shaft");

    public record Selection(BlockPos pos, @Nullable UUID subLevel, long expiresAt) {}

    public record LinkError(String key, Object... args) {
        public Component message() {
            return Component.translatable(key, args).withStyle(ChatFormatting.RED);
        }

        public static LinkError basic(String key) {
            return new LinkError(TRANSLATE_KEY_PREFIX + "." + key);
        }
    }

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

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.getBlockEntity(context.getClickedPos()) instanceof BeltWheelBlockEntity be && be.hasLinkedTarget()) {
            if (!level.isClientSide) {
                be.breakLink(true, false);

                Player player = context.getPlayer();
                if (player != null) {
                    player.getInventory().placeItemBackInInventory(AllItems.BELT_CONNECTOR.asStack());
                    notify(player, TRANSLATE_KEY_PREFIX + ".link_broken", ChatFormatting.YELLOW);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.onWrenched(state, context);
    }

    @Nullable
    public static LinkError checkNewLink(BeltWheelBlockEntity first, BeltWheelBlockEntity second) {
        Vec3 a = first.getWorldAnchorPosition();
        Vec3 b = second.getWorldAnchorPosition();
        if (a == null || b == null)
            return LinkError.basic("link_missing");

        Vec3 delta = b.subtract(a);
        double distanceSqr = delta.lengthSqr();
        int max = getConfiguredMaxDistance();
        if (distanceSqr <= 0.01 || distanceSqr > max * max)
            return new LinkError(TRANSLATE_KEY_PREFIX + ".distance_invalid", max);

        BeltWheelBlockEntity.Geometry geometry = BeltWheelBlockEntity.evalulateGeometry(first, second, -1);
        if (geometry == BeltWheelBlockEntity.Geometry.AXES)
            return LinkError.basic("axes_not_parallel");
        if (geometry == BeltWheelBlockEntity.Geometry.PLANE)
            return LinkError.basic("not_coplanar");
        if (geometry != BeltWheelBlockEntity.Geometry.OK)
            return LinkError.basic("link_missing");

        Vec3 localA = SimulatedHelper.toContainingLocalDirection(first, delta);
        Vec3 localB = SimulatedHelper.toContainingLocalDirection(second, delta);
        boolean alignedA = isAxisAligned(localA);
        boolean alignedB = isAxisAligned(localB);
        if (!alignedA && !alignedB)
            return LinkError.basic("not_straight");

        boolean okA = alignedA && getConnectionAxis(localA) != first.getBlockState().getValue(AXIS);
        boolean okB = alignedB && getConnectionAxis(localB) != second.getBlockState().getValue(AXIS);
        if (!okA && !okB)
            return LinkError.basic("axis_mismatch");

        return null;
    }

    private static ItemInteractionResult handleBeltConnectorUse(Level level, Player player, ItemStack stack, BeltWheelBlockEntity clickedWheel) {
        long now = level.getGameTime();
        UUID clickedSubLevel = SimulatedHelper.getContainingSubLevelId(clickedWheel);

        Selection pending = readSelection(stack);
        if (pending != null && pending.expiresAt() < now) {
            writeSelection(stack, null);
            pending = null;
        }

        if (pending == null) {
            writeSelection(stack, new Selection(clickedWheel.getBlockPos().immutable(), clickedSubLevel, now + 600L));
            notify(player, TRANSLATE_KEY_PREFIX + ".link_first", ChatFormatting.GRAY);
            return ItemInteractionResult.SUCCESS;
        }

        if (pending.pos().equals(clickedWheel.getBlockPos()) && Objects.equals(pending.subLevel(), clickedSubLevel)) {
            writeSelection(stack, null);
            notify(player, TRANSLATE_KEY_PREFIX + ".link_cleared", ChatFormatting.YELLOW);
            return ItemInteractionResult.SUCCESS;
        }

        BeltWheelBlockEntity firstWheel = SimulatedHelper.findBlockEntity(level, pending.subLevel(),
            pending.pos(), BeltWheelBlockEntity.class);
        if (firstWheel == null || firstWheel.isRemoved()) {
            writeSelection(stack, null);
            notify(player, TRANSLATE_KEY_PREFIX + ".link_missing", ChatFormatting.RED);
            return ItemInteractionResult.SUCCESS;
        }

        LinkError error = checkNewLink(firstWheel, clickedWheel);
        if (error != null) {
            writeSelection(stack, null);
            notify(player, error.message());
            return ItemInteractionResult.FAIL;
        }

        UUID firstSubLevel = SimulatedHelper.getContainingSubLevelId(firstWheel);

        firstWheel.breakLink(true);
        clickedWheel.breakLink(true);

        firstWheel.setLinkedTarget(clickedWheel.getBlockPos(), clickedSubLevel);
        clickedWheel.setLinkedTarget(firstWheel.getBlockPos(), firstSubLevel);

        double rest = firstWheel.getWorldAnchorPosition().distanceTo(clickedWheel.getWorldAnchorPosition());
        firstWheel.setRestLength(rest);
        clickedWheel.setRestLength(rest);

        firstWheel.bindPartnerId(clickedWheel.ensureWheelId());
        clickedWheel.bindPartnerId(firstWheel.ensureWheelId());

        level.playSound(null, clickedWheel.getBlockPos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 1f);
        level.playSound(null, firstWheel.getBlockPos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 1f);

        writeSelection(stack, null);
        if (!player.getAbilities().instabuild)
            stack.shrink(1);

        notify(player, TRANSLATE_KEY_PREFIX + ".link_success", ChatFormatting.GREEN);
        return ItemInteractionResult.SUCCESS;
    }

    public static void dropBelt(Level level, Vec3 at) {
        ItemEntity item = new ItemEntity(level, at.x, at.y + 1, at.z, AllItems.BELT_CONNECTOR.asStack());
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    @Nullable
    public static Selection readSelection(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null)
            return null;
        CompoundTag root = data.copyTag();
        if (!root.contains(SELECTION_TAG, CompoundTag.TAG_COMPOUND))
            return null;
        CompoundTag tag = root.getCompound(SELECTION_TAG);
        return new Selection(BlockPos.of(tag.getLong("Pos")), tag.hasUUID("Sub") ? tag.getUUID("Sub") : null, tag.getLong("Expires"));
    }

    public static void writeSelection(ItemStack stack, @Nullable Selection selection) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (selection == null) {
            root.remove(SELECTION_TAG);
        } else {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", selection.pos().asLong());
            if (selection.subLevel() != null)
                tag.putUUID("Sub", selection.subLevel());
            tag.putLong("Expires", selection.expiresAt());
            root.put(SELECTION_TAG, tag);
        }

        if (root.isEmpty())
            stack.remove(DataComponents.CUSTOM_DATA);
        else
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
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

    private static void notify(Player player, Component msg) {
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
            withBlockEntityDo(level, pos, BeltWheelBlockEntity::onBlockRemoved);
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
