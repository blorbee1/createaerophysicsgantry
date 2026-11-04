package com.blorbee.createaerophysicsgantry.mixin;

import com.blorbee.createaerophysicsgantry.util.ConstraintGraph;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class RapierPhysicsPipelineMixin {
    @Inject(method = "addConstraint", at = @At("RETURN"), remap = false)
    private void capg$recordConstraint(PhysicsPipelineBody bodyA, PhysicsPipelineBody bodyB,
                                       PhysicsConstraintConfiguration<?> configuration,
                                       CallbackInfoReturnable<PhysicsConstraintHandle> cir) {
        PhysicsConstraintHandle handle = cir.getReturnValue();
        if (handle == null)
            return;
        if ((bodyA != null && !(bodyA instanceof SubLevel)) || (bodyB != null && !(bodyB instanceof SubLevel)))
            return;

        UUID a = bodyA instanceof SubLevel sa ? sa.getUniqueId() : null;
        UUID b = bodyB instanceof SubLevel sb ? sb.getUniqueId() : null;
        ConstraintGraph.add(a, b, handle);
    }
}
