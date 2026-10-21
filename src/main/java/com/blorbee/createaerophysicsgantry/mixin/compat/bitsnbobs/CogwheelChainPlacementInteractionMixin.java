package com.blorbee.createaerophysicsgantry.mixin.compat.bitsnbobs;

import com.blorbee.createaerophysicsgantry.registry.CAPGBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.kipti.bnb.content.kinetics.cogwheel_chain.placement.CogwheelChainPlacementInteraction", remap = false)
public abstract class CogwheelChainPlacementInteractionMixin {
    @Inject(method = "onClickInput", at = @At("HEAD"), cancellable = true, remap = false)
    private static void capg$skipChainInteraction(InputEvent.InteractionKeyMappingTriggered event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.level == null)
            return;
        if (event.getKeyMapping() != mc.options.keyUse)
            return;

        if (mc.hitResult instanceof BlockHitResult hit && mc.level.getBlockState(hit.getBlockPos()).is(CAPGBlocks.BELT_WHEEL.get())) {
            ci.cancel();
        }
    }
}
