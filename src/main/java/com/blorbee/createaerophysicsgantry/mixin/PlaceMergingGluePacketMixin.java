package com.blorbee.createaerophysicsgantry.mixin;

import com.blorbee.createaerophysicsgantry.compat.simulated.SimulatedHelper;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageBlock;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageBlockEntity;
import dev.simulated_team.simulated.network.packets.PlaceMergingGluePacket;
import mezz.jei.common.network.ServerPacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlaceMergingGluePacketMixin.class)
public class PlaceMergingGluePacketMixin {
    private static final Component CARRIAGE_TO_CARRIAGE_MESSAGE = Component.literal(
        "You cannot attach a Physics Gantry Carriage to a Physics Gantry Carriage, selection cleared"
    );

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void createaerophysicsgantry$blockPhysicsGantryCarriageGlue(ServerPacketContext context, CallbackInfo ci) {
        PlaceMergingGluePacket packet = (PlaceMergingGluePacket)(Object)this;
        ServerPlayer player = context.player();
        Level level = player.level();
        if (player != null && level != null) {
            if (isPhysicsGantryCarriageEndpoint(level, packet.parentPos()) || isPhysicsGantryCarriageEndpoint(level, packet.childPos())) {
                player.displayClientMessage(CARRIAGE_TO_CARRIAGE_MESSAGE, true);
                ci.cancel();
            }
        }
    }

    private static boolean isPhysicsGantryCarriageEndpoint(Level level, BlockPos pos) {
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, pos);
        if (blockEntity instanceof PhysicsGantryCarriageBlockEntity) {
            return true;
        }

        BlockState state = blockEntity == null ? level.getBlockState(pos) : blockEntity.getBlockState();
        return state.getBlock() instanceof PhysicsGantryCarriageBlock;
    }
}
