package com.blorbee.createaerophysicsgantry.mixin;

import com.blorbee.createaerophysicsgantry.compat.simulated.SimulatedHelper;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageBlock;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MergingGlueItemHandlerMixin.class)
public class MergingGlueItemHandlerMixin {
    private static final Component CARRIAGE_TO_CARRIAGE_MESSAGE = Component.literal(
        "You cannot attach a Physics Gantry Carriage to a Physics Gantry Carriage, selection cleared"
    );

    @Shadow
    public BlockPos firstPos;
    @Shadow
    public Direction firstDirection;

    @Inject(method = "onItemUseBlock", at = @At("HEAD"), cancellable = true)
    private void createaerophysicsgantry$skipPhysicsGantryCarriageGlue(
        Level level, Player player, ItemStack itemStack, InteractionHand hand, CallbackInfoReturnable<Boolean> cir
    ) {
        if (!itemStack.isEmpty() && itemStack.is(Items.SLIME_BALL)) {
            if (Minecraft.getInstance().hitResult instanceof BlockHitResult blockHit && blockHit.getType() != HitResult.Type.MISS) {
                BlockPos clickedPos = blockHit.getBlockPos();
                Direction clickedDirection = blockHit.getDirection();
                boolean clickedCarriage = isPhysicsGantryCarriageEndpoint(level, clickedPos);
                boolean selectedCarriage = this.firstPos != null && isPhysicsGantryCarriageEndpoint(level, this.firstPos);
                if (clickedCarriage || selectedCarriage) {
                    if (this.firstPos == null && clickedCarriage) {
                        this.firstPos = clickedPos.immutable();
                        this.firstDirection = clickedDirection;
                        cir.setReturnValue(true);
                    } else if (!selectedCarriage) {
                        cir.setReturnValue(false);
                    } else {
                        boolean clickedDifferentCarriage = clickedCarriage && !this.firstPos.equals(clickedPos);
                        this.firstPos = null;
                        this.firstDirection = null;
                        if (clickedDifferentCarriage) {
                            player.displayClientMessage(CARRIAGE_TO_CARRIAGE_MESSAGE, true);
                        }

                        cir.setReturnValue(clickedDifferentCarriage);
                    }
                }
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
