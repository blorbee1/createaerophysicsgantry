package com.blorbee.createaerophysicsgantry.event;

import com.blorbee.createaerophysicsgantry.compat.simulated.SimulatedHelper;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageBlock;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageBlockEntity;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_shaft.PhysicsGantryShaftBlock;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_shaft.PhysicsGantryShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@EventBusSubscriber
public final class CAPGEvents {
    private static final Map<UUID, PendingGantryRelink> PENDING_GANTRY_RELINK = new HashMap<>();

    private CAPGEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide)
            return;
        if (tryHandleGantrySlimeRelink(event))
            return;
    }

    private static boolean tryHandleGantrySlimeRelink(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!held.is(Items.SLIME_BALL))
            return false;

        UUID playerId = event.getEntity().getUUID();
        BlockPos clickedPos = event.getPos();
        BlockEntity clickedBe = SimulatedHelper.findBlockEntityIncludingSubLevels(event.getLevel(), clickedPos);
        BlockState clickedState = clickedBe == null ? event.getLevel().getBlockState(clickedPos) : clickedBe.getBlockState();

        if (clickedBe instanceof PhysicsGantryCarriageBlockEntity) {
            UUID clickedSubLevelId = SimulatedHelper.getContainingSubLevelId(clickedBe);
            PendingGantryRelink selected = PENDING_GANTRY_RELINK.get(playerId);
            if (selected != null && !selected.matches(clickedBe.getBlockPos(), clickedSubLevelId)) {
                PENDING_GANTRY_RELINK.remove(playerId);
                event.getEntity()
                    .displayClientMessage(Component.literal("You cannot attach a Physics Gantry Carriage to a Physics Gantry Carriage, selected cleared"), true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return true;
            } else {
                PENDING_GANTRY_RELINK.put(playerId, new PendingGantryRelink(clickedBe.getBlockPos().immutable(), clickedSubLevelId));
                event.getEntity()
                    .displayClientMessage(Component.literal("Selected gantry carriage base face for relink"), true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return true;
            }
        } else {
            if (!(clickedState.getBlock() instanceof PhysicsGantryShaftBlock))
                return false;

            Direction clickedFace = event.getFace();
            if (clickedFace == null) {
                event.getEntity()
                    .displayClientMessage(Component.literal("Could not resolve shaft side for relink"), true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return true;
            }

            PendingGantryRelink selected = PENDING_GANTRY_RELINK.remove(playerId);
            if (selected == null) {
                event.getEntity()
                    .displayClientMessage(Component.literal("Select a gantry carriage first"), true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return true;
            }

            BlockEntity selectedBe = SimulatedHelper.findBlockEntity(event.getLevel(), selected.subLevelId(), selected.pos());
            if (selectedBe == null)
                selectedBe = SimulatedHelper.findBlockEntityIncludingSubLevels(event.getLevel(), selected.pos());

            if (selectedBe instanceof PhysicsGantryCarriageBlockEntity carriage) {
                PhysicsGantryShaftBlockEntity shaft = clickedBe instanceof PhysicsGantryShaftBlockEntity shaftBe ? shaftBe : null;
                boolean started = carriage.beginManualShaftRelink(clickedPos, clickedFace, event.getEntity(), shaft);
                if (started) {
                    event.getEntity()
                        .displayClientMessage(Component.literal("Created new gantry attachment point"), true);
                } else {
                    event.getEntity()
                        .displayClientMessage(Component.literal("Could not relink to that shaft side, selected cleared"), true);
                }

                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return true;
            } else {
                event.getEntity()
                    .displayClientMessage(Component.literal("Selected carriage is no longer valid"), true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return true;
            }
        }
    }

    private record PendingGantryRelink(BlockPos pos, UUID subLevelId) {
        private boolean matches(BlockPos otherPos, UUID otherSubLevelId) {
            return this.pos.equals(otherPos) && Objects.equals(this.subLevelId, otherSubLevelId);
        }
    }
}
